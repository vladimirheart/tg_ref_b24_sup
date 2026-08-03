package com.example.panel.service;

import com.example.panel.service.NetBoxApiService.DownloadedFile;
import com.example.panel.service.NetBoxSyncSettingsService.NetBoxSyncSettings;
import com.example.panel.storage.ObjectPassportPhotoStorageService;
import com.example.panel.storage.ObjectPassportPhotoStorageService.StoredPhoto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NetBoxObjectPassportSyncService {

    private static final Logger log = LoggerFactory.getLogger(NetBoxObjectPassportSyncService.class);
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);
    private static final List<String> SCHEDULE_DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final Pattern WORK_TIME_PATTERN = Pattern.compile("(?<from>\\d{1,2}:\\d{2})\\s*[-–]\\s*(?<to>\\d{1,2}:\\d{2})");
    private static final Pattern CITY_PATTERN = Pattern.compile("^(?:г\\.?|город)\\s*([^,]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String NETBOX_PHOTO_SOURCE = "netbox";
    private static final String STATUS_ACTIVE = "Активен";

    private final SharedConfigService sharedConfigService;
    private final NetBoxSyncSettingsService settingsService;
    private final NetBoxApiService netBoxApiService;
    private final ObjectPassportService objectPassportService;
    private final ObjectPassportPhotoStorageService photoStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsCatalogService settingsCatalogService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "netbox-object-passport-sync");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SyncStatusSnapshot status = SyncStatusSnapshot.idle();
    private volatile Instant lastFinishedAt;

    public NetBoxObjectPassportSyncService(SharedConfigService sharedConfigService,
                                           NetBoxSyncSettingsService settingsService,
                                           NetBoxApiService netBoxApiService,
                                           ObjectPassportService objectPassportService,
                                           ObjectPassportPhotoStorageService photoStorageService,
                                           JdbcTemplate jdbcTemplate,
                                           ObjectMapper objectMapper,
                                           SettingsCatalogService settingsCatalogService) {
        this.sharedConfigService = sharedConfigService;
        this.settingsService = settingsService;
        this.netBoxApiService = netBoxApiService;
        this.objectPassportService = objectPassportService;
        this.photoStorageService = photoStorageService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.settingsCatalogService = settingsCatalogService;
    }

    public SyncTriggerResponse triggerManualSync() {
        return triggerAsync("manual");
    }

    public void runScheduledSyncIfDue() {
        NetBoxSyncSettings settings = settingsService.load(sharedConfigService.loadSettings());
        if (!settings.enabled() || running.get()) {
            return;
        }
        Instant now = Instant.now();
        if (lastFinishedAt != null && lastFinishedAt.plusSeconds(settings.intervalMinutes() * 60L).isAfter(now)) {
            return;
        }
        triggerAsync("schedule");
    }

    public SyncStatusSnapshot getStatus() {
        Map<String, Object> rawSettings = sharedConfigService.loadSettings();
        NetBoxSyncSettings settings = settingsService.load(rawSettings);
        String nextRunAtUtc = null;
        if (settings.enabled() && lastFinishedAt != null) {
            nextRunAtUtc = formatUtc(lastFinishedAt.plusSeconds(settings.intervalMinutes() * 60L));
        }
        if (settings.enabled() && lastFinishedAt == null && !"running".equals(status.state())) {
            nextRunAtUtc = "after_startup_tick";
        }
        return status.withSettings(settings, nextRunAtUtc);
    }

    @PreDestroy
    void shutdownExecutor() {
        executorService.shutdownNow();
    }

    SyncStatusSnapshot syncNow(String trigger) {
        Instant startedAt = Instant.now();
        updateStatus(new SyncStatusSnapshot(
                "running",
                5,
                "Подготавливаем синхронизацию NetBox",
                trigger,
                formatUtc(startedAt),
                null,
                List.of(),
                status.result(),
                status.lastSuccessAtUtc(),
                true,
                false,
                false,
                0,
                null
        ));
        try {
            Map<String, Object> sharedSettings = new LinkedHashMap<>(sharedConfigService.loadSettings());
            NetBoxSyncSettings settings = settingsService.load(sharedSettings);
            validateSettings(settings);

            updateProgress(15, "Запрашиваем сайты из NetBox");
            List<Map<String, Object>> sites = netBoxApiService.fetchSites(settings);
            updateProgress(30, "Готовим паспорта объектов и оборудование");

            SyncAccumulator accumulator = new SyncAccumulator();
            if (settings.fullOverwritePending()) {
                FullOverwritePayload overwritePayload = buildFullOverwritePayload(settings, sites, accumulator);
                updateProgress(80, "Полностью переписываем тестовые паспорта объектов");
                try {
                    objectPassportService.replaceAllPassports(overwritePayload.passports());
                    syncItConnectionParameters(accumulator.itParameters());
                    settingsService.markFullOverwriteComplete(sharedSettings);
                    sharedConfigService.saveSettings(sharedSettings);
                } catch (RuntimeException ex) {
                    overwritePayload.newStoredFiles().forEach(photoStorageService::deleteQuietly);
                    throw ex;
                }
            } else {
                upsertPassports(settings, sites, accumulator);
                syncItConnectionParameters(accumulator.itParameters());
            }

            updateProgress(95, "Финализируем результат синхронизации");
            SyncStatusSnapshot result = finish(
                    "success",
                    trigger,
                    startedAt,
                    accumulator.toSummary(),
                    List.of(),
                    accumulator.changed(),
                    settings.fullOverwritePending(),
                    settings.enabled(),
                    settings.intervalMinutes(),
                    "Синхронизация NetBox завершена"
            );
            log.info("netbox object passports sync completed: trigger={}, sites={}, created={}, updated={}, overwrite={}",
                    trigger,
                    accumulator.totalSites(),
                    accumulator.created(),
                    accumulator.updated(),
                    settings.fullOverwritePending());
            return result;
        } catch (Exception ex) {
            log.warn("netbox object passports sync failed: trigger={}", trigger, ex);
            return finish(
                    "error",
                    trigger,
                    startedAt,
                    null,
                    List.of(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                    false,
                    false,
                    settingsService.load(sharedConfigService.loadSettings()).enabled(),
                    settingsService.load(sharedConfigService.loadSettings()).intervalMinutes(),
                    "Синхронизация NetBox завершилась ошибкой"
            );
        } finally {
            lastFinishedAt = Instant.now();
            running.set(false);
        }
    }

    private FullOverwritePayload buildFullOverwritePayload(NetBoxSyncSettings settings,
                                                           List<Map<String, Object>> sites,
                                                           SyncAccumulator accumulator) {
        List<Map<String, Object>> passports = new ArrayList<>();
        List<String> newStoredFiles = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> site : sites) {
            index += 1;
            updateProgress(30 + Math.min(40, (index * 40) / Math.max(1, sites.size())),
                    "Готовим сайт " + index + " из " + sites.size());
            PassportBuildResult buildResult = buildPassportPayload(settings, site, null);
            passports.add(buildResult.payload());
            newStoredFiles.addAll(buildResult.newStoredFiles());
            accumulator.registerSite(buildResult.payload());
            accumulator.registerCreated();
        }
        return new FullOverwritePayload(passports, newStoredFiles);
    }

    private void upsertPassports(NetBoxSyncSettings settings,
                                 List<Map<String, Object>> sites,
                                 SyncAccumulator accumulator) {
        int index = 0;
        for (Map<String, Object> site : sites) {
            index += 1;
            updateProgress(35 + Math.min(45, (index * 45) / Math.max(1, sites.size())),
                    "Синхронизируем сайт " + index + " из " + sites.size());
            String siteId = stringValue(site.get("id"));
            Map<String, Object> existing = objectPassportService.findPassportByNetBoxSiteId(siteId);
            PassportBuildResult buildResult = buildPassportPayload(settings, site, existing);
            boolean created = existing == null || existing.isEmpty();
            try {
                objectPassportService.upsertPassportByNetBoxSiteId(siteId, buildResult.payload());
                buildResult.obsoleteStoredFiles().forEach(photoStorageService::deleteQuietly);
                accumulator.registerSite(buildResult.payload());
                if (created) {
                    accumulator.registerCreated();
                } else {
                    accumulator.registerUpdated();
                }
            } catch (RuntimeException ex) {
                buildResult.newStoredFiles().forEach(photoStorageService::deleteQuietly);
                throw ex;
            }
        }
    }

    private PassportBuildResult buildPassportPayload(NetBoxSyncSettings settings,
                                                     Map<String, Object> site,
                                                     Map<String, Object> existingPassport) {
        String siteId = stringValue(site.get("id"));
        List<Map<String, Object>> devices = netBoxApiService.fetchDevices(settings, siteId);
        List<Map<String, Object>> circuits = netBoxApiService.fetchCircuits(settings, siteId);
        List<Map<String, Object>> images = netBoxApiService.fetchSiteImages(settings, siteId);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("netbox_site_id", siteId);
        payload.put("department", firstNonBlank(site.get("name"), site.get("display"), siteId));
        payload.put("business", nestedString(site, "tenant", "name"));
        payload.put("city", resolveCity(site));
        payload.put("location_address", stringValue(site.get("physical_address")));
        payload.put("status", resolveLabeledValue(site.get("status")));
        payload.put("it_object_phone", stringValue(site.get("contact_phone")));
        payload.put("schedule", buildSchedule(resolveCustomField(site, "WorkTime")));
        payload.put("equipment", buildEquipment(devices));

        CircuitSnapshot mainCircuit = selectMainCircuit(circuits);
        payload.put("network_provider", mainCircuit == null ? "" : mainCircuit.provider());
        payload.put("network_contract_number", mainCircuit == null ? "" : mainCircuit.contractNumber());
        payload.put("network_speed", mainCircuit == null ? "" : mainCircuit.speedDisplay());
        payload.put("network_tunnel", mainCircuit == null ? "" : mainCircuit.tunnelType());
        payload.put("network_connection_params", buildNetworkConnectionParams(site, circuits));

        PhotoMergeResult photoMergeResult = mergeSitePhotos(settings, images, existingPassport);
        payload.put("photos", photoMergeResult.photos());
        return new PassportBuildResult(payload, photoMergeResult.newStoredFiles(), photoMergeResult.obsoleteStoredFiles());
    }

    private List<Map<String, Object>> buildEquipment(List<Map<String, Object>> devices) {
        List<Map<String, Object>> equipment = new ArrayList<>();
        for (Map<String, Object> device : devices) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("equipment_type", nestedString(device, "device_role", "name"));
            item.put("vendor", nestedString(device, "device_type", "manufacturer", "name"));
            item.put("name", firstNonBlank(device.get("name"), device.get("display")));
            item.put("model", nestedString(device, "device_type", "model"));
            item.put("serial_number", stringValue(device.get("serial")));
            item.put("status", resolveLabeledValue(device.get("status")));
            item.put("ip_address", nestedString(device, "primary_ip4", "address"));
            item.put("description", buildDeviceDescription(device));
            equipment.add(item);
        }
        return equipment;
    }

    private String buildDeviceDescription(Map<String, Object> device) {
        List<String> lines = new ArrayList<>();
        appendNamed(lines, "Описание", stringValue(device.get("comments")));
        appendNamed(lines, "Площадка", nestedString(device, "site", "name"));
        Map<String, Object> customFields = toMap(device.get("custom_fields"));
        for (Map.Entry<String, Object> entry : customFields.entrySet()) {
            String value = stringValue(entry.getValue());
            if (StringUtils.hasText(value)) {
                appendNamed(lines, entry.getKey(), value);
            }
        }
        return String.join("\n", lines);
    }

    private PhotoMergeResult mergeSitePhotos(NetBoxSyncSettings settings,
                                             List<Map<String, Object>> imageAttachments,
                                             Map<String, Object> existingPassport) {
        List<Map<String, Object>> existingPhotos = normalizePhotoList(existingPassport == null ? null : existingPassport.get("photos"));
        LinkedHashMap<String, Map<String, Object>> existingNetBoxByExternalId = new LinkedHashMap<>();
        List<Map<String, Object>> preservedPhotos = new ArrayList<>();
        for (Map<String, Object> photo : existingPhotos) {
            String source = stringValue(photo.get("source"));
            String externalId = stringValue(photo.get("external_id"));
            if (NETBOX_PHOTO_SOURCE.equalsIgnoreCase(source) && StringUtils.hasText(externalId)) {
                existingNetBoxByExternalId.put(externalId, photo);
            } else {
                preservedPhotos.add(new LinkedHashMap<>(photo));
            }
        }

        boolean hasExistingTitle = existingPhotos.stream()
                .anyMatch(photo -> "title".equalsIgnoreCase(stringValue(photo.get("category"))));
        List<Map<String, Object>> resultPhotos = new ArrayList<>(preservedPhotos);
        List<String> newStoredFiles = new ArrayList<>();
        Set<String> seenExternalIds = new LinkedHashSet<>();
        boolean titleAssignedToImported = false;

        for (Map<String, Object> attachment : imageAttachments) {
            String attachmentId = stringValue(attachment.get("id"));
            if (!StringUtils.hasText(attachmentId)) {
                continue;
            }
            String externalId = "netbox-image-" + attachmentId;
            seenExternalIds.add(externalId);
            Map<String, Object> existingNetBoxPhoto = existingNetBoxByExternalId.get(externalId);
            if (existingNetBoxPhoto != null) {
                LinkedHashMap<String, Object> merged = new LinkedHashMap<>(existingNetBoxPhoto);
                merged.put("source", NETBOX_PHOTO_SOURCE);
                merged.put("external_id", externalId);
                merged.put("source_url", firstNonBlank(attachment.get("image"), attachment.get("url")));
                if (StringUtils.hasText(stringValue(attachment.get("name")))) {
                    merged.put("caption", stringValue(attachment.get("name")));
                }
                resultPhotos.add(merged);
                continue;
            }
            DownloadedFile downloadedFile = netBoxApiService.downloadFile(
                    settings,
                    firstNonBlank(attachment.get("image"), attachment.get("url")),
                    stringValue(attachment.get("name"))
            );
            try (InputStream inputStream = downloadedFile.inputStream()) {
                StoredPhoto storedPhoto = photoStorageService.store(inputStream, downloadedFile.fileName(), downloadedFile.contentType());
                newStoredFiles.add(storedPhoto.storedName());
                LinkedHashMap<String, Object> photo = new LinkedHashMap<>();
                photo.put("id", "netbox-photo-" + attachmentId);
                photo.put("category", !hasExistingTitle && !titleAssignedToImported && isTitleAttachment(attachment) ? "title" : "archive");
                photo.put("caption", stringValue(attachment.get("name")));
                photo.put("url", storedPhoto.url());
                photo.put("stored_name", storedPhoto.storedName());
                photo.put("original_name", storedPhoto.originalName());
                photo.put("mime_type", storedPhoto.mimeType());
                photo.put("size", storedPhoto.size());
                photo.put("created_at", storedPhoto.uploadedAt());
                photo.put("source", NETBOX_PHOTO_SOURCE);
                photo.put("external_id", externalId);
                photo.put("source_url", firstNonBlank(attachment.get("image"), attachment.get("url")));
                resultPhotos.add(photo);
                if ("title".equals(photo.get("category"))) {
                    titleAssignedToImported = true;
                }
            } catch (IOException ex) {
                newStoredFiles.forEach(photoStorageService::deleteQuietly);
                throw new IllegalStateException("Не удалось сохранить фото сайта NetBox #" + attachmentId, ex);
            }
        }

        List<String> obsoleteStoredFiles = existingNetBoxByExternalId.entrySet().stream()
                .filter(entry -> !seenExternalIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(photo -> stringValue(photo.get("stored_name")))
                .filter(StringUtils::hasText)
                .toList();

        return new PhotoMergeResult(resultPhotos, newStoredFiles, obsoleteStoredFiles);
    }

    private boolean isTitleAttachment(Map<String, Object> attachment) {
        String name = stringValue(attachment.get("name")).toUpperCase(Locale.ROOT);
        return "ЗО".equals(name);
    }

    private List<Map<String, Object>> buildSchedule(String workTime) {
        if (!StringUtils.hasText(workTime)) {
            return List.of();
        }
        String normalized = workTime.trim();
        if ("24/7".equalsIgnoreCase(normalized) || normalized.toLowerCase(Locale.ROOT).contains("круглосуточ")) {
            return SCHEDULE_DAYS.stream()
                    .map(day -> Map.<String, Object>of("day", day, "from", "", "to", "", "is_24", true))
                    .toList();
        }
        Matcher matcher = WORK_TIME_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return List.of();
        }
        String from = matcher.group("from");
        String to = matcher.group("to");
        List<Map<String, Object>> schedule = new ArrayList<>();
        for (String day : SCHEDULE_DAYS) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("day", day);
            item.put("from", from);
            item.put("to", to);
            item.put("is_24", false);
            schedule.add(item);
        }
        return schedule;
    }

    private String buildNetworkConnectionParams(Map<String, Object> site, List<Map<String, Object>> circuits) {
        List<String> lines = new ArrayList<>();
        appendNamed(lines, "Site", firstNonBlank(site.get("name"), site.get("display")));
        appendNamed(lines, "Facility", stringValue(site.get("facility")));
        appendNamed(lines, "Описание", stringValue(site.get("description")));
        appendNamed(lines, "AD Account", resolveCustomField(site, "AD Account"));
        appendNamed(lines, "WorkTime", resolveCustomField(site, "WorkTime"));
        appendNamed(lines, "Комментарии", stringValue(site.get("comments")));
        for (Map<String, Object> circuit : circuits) {
            lines.add("");
            appendNamed(lines, "Circuit provider", nestedString(circuit, "provider", "name"));
            appendNamed(lines, "Circuit type", nestedString(circuit, "type", "name"));
            appendNamed(lines, "Circuit cid", stringValue(circuit.get("cid")));
            appendNamed(lines, "Circuit status", resolveLabeledValue(circuit.get("status")));
            appendNamed(lines, "Circuit speed", formatCircuitSpeed(circuit));
            appendNamed(lines, "Circuit description", stringValue(circuit.get("description")));
            appendNamed(lines, "ProviderTypeConnection", resolveCustomField(circuit, "ProviderTypeConnection"));
            appendNamed(lines, "ConnLogin", resolveCustomField(circuit, "ConnLogin"));
            appendNamed(lines, "ConnPassword", resolveCustomField(circuit, "ConnPassword"));
        }
        return lines.stream()
                .filter(Objects::nonNull)
                .filter(line -> !line.isBlank() || !lines.isEmpty())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private CircuitSnapshot selectMainCircuit(List<Map<String, Object>> circuits) {
        return circuits.stream()
                .map(this::toCircuitSnapshot)
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int activeCompare = Boolean.compare(right.active(), left.active());
                    if (activeCompare != 0) {
                        return activeCompare;
                    }
                    return Long.compare(right.speedKbps(), left.speedKbps());
                })
                .findFirst()
                .orElse(null);
    }

    private CircuitSnapshot toCircuitSnapshot(Map<String, Object> circuit) {
        if (circuit == null || circuit.isEmpty()) {
            return null;
        }
        String provider = nestedString(circuit, "provider", "name");
        String contractNumber = firstNonBlank(nestedString(circuit, "type", "name"), circuit.get("cid"));
        long speedKbps = resolveCircuitSpeedKbps(circuit);
        return new CircuitSnapshot(
                provider,
                contractNumber,
                formatSpeedKbps(speedKbps),
                resolveCustomField(circuit, "ProviderTypeConnection"),
                resolveActive(circuit.get("status")),
                speedKbps
        );
    }

    private long resolveCircuitSpeedKbps(Map<String, Object> circuit) {
        List<Object> candidates = new ArrayList<>();
        candidates.add(nestedValue(circuit, "termination_a", "port_speed"));
        candidates.add(nestedValue(circuit, "termination_z", "port_speed"));
        candidates.add(circuit.get("commit_rate"));
        for (Object candidate : candidates) {
            Long value = asLong(candidate);
            if (value != null && value > 0) {
                return value;
            }
        }
        return 0;
    }

    private String formatCircuitSpeed(Map<String, Object> circuit) {
        return formatSpeedKbps(resolveCircuitSpeedKbps(circuit));
    }

    private String formatSpeedKbps(long speedKbps) {
        if (speedKbps <= 0) {
            return "";
        }
        if (speedKbps % 1_000_000L == 0) {
            return (speedKbps / 1_000_000L) + " Gbps";
        }
        if (speedKbps >= 1_000_000L) {
            return trimFraction((double) speedKbps / 1_000_000D) + " Gbps";
        }
        if (speedKbps % 1_000L == 0) {
            return (speedKbps / 1_000L) + " Mbps";
        }
        if (speedKbps >= 1_000L) {
            return trimFraction((double) speedKbps / 1_000D) + " Mbps";
        }
        return speedKbps + " Kbps";
    }

    private String trimFraction(double value) {
        String formatted = String.format(Locale.US, "%.2f", value);
        return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String resolveCity(Map<String, Object> site) {
        String address = stringValue(site.get("physical_address"));
        if (StringUtils.hasText(address)) {
            Matcher matcher = CITY_PATTERN.matcher(address);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            int commaIndex = address.indexOf(',');
            if (commaIndex > 0) {
                String firstChunk = address.substring(0, commaIndex).trim();
                if (StringUtils.hasText(firstChunk)) {
                    return firstChunk.replaceFirst("^(г\\.?|город)\\s*", "").trim();
                }
            }
        }
        return firstNonBlank(nestedString(site, "region", "name"), nestedString(site, "region", "display"));
    }

    private String resolveCustomField(Map<String, Object> source, String key) {
        Map<String, Object> customFields = toMap(source.get("custom_fields"));
        for (Map.Entry<String, Object> entry : customFields.entrySet()) {
            if (key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                return stringValue(entry.getValue());
            }
        }
        return "";
    }

    private String resolveLabeledValue(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object label = map.get("label");
            if (label != null && StringUtils.hasText(String.valueOf(label))) {
                return String.valueOf(label).trim();
            }
            Object value = map.get("value");
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return stringValue(raw);
    }

    private boolean resolveActive(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            String label = stringValue(map.get("label"));
            String value = stringValue(map.get("value"));
            return "active".equalsIgnoreCase(label) || "active".equalsIgnoreCase(value) || "активен".equalsIgnoreCase(label);
        }
        String value = stringValue(raw);
        return "active".equalsIgnoreCase(value) || "активен".equalsIgnoreCase(value);
    }

    private void syncItConnectionParameters(Set<DesiredItConnectionParameter> desiredParameters) {
        if (desiredParameters.isEmpty()) {
            return;
        }
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
                "SELECT id, value, state, is_deleted, extra_json FROM settings_parameters WHERE param_type = ?",
                "it_connection"
        );
        Map<String, ExistingItConnectionParameter> existingByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : existingRows) {
            Map<String, Object> extra = parseExtraJson(row.get("extra_json"));
            String category = stringValue(extra.get("category"));
            String value = stringValue(row.get("value"));
            if (!StringUtils.hasText(category) || !StringUtils.hasText(value)) {
                continue;
            }
            existingByKey.put(buildItParameterKey(category, value), new ExistingItConnectionParameter(asLong(row.get("id")), category, value));
        }

        Map<String, String> categoryLabels = settingsCatalogService.getDefaultItConnectionCategories();
        for (DesiredItConnectionParameter desired : desiredParameters) {
            String key = buildItParameterKey(desired.category(), desired.value());
            Map<String, Object> extra = buildItConnectionExtra(desired, categoryLabels.getOrDefault(desired.category(), desired.category()));
            String extraJson = writeJson(extra);
            ExistingItConnectionParameter existing = existingByKey.get(key);
            if (existing != null && existing.id() != null) {
                jdbcTemplate.update(
                        "UPDATE settings_parameters SET value = ?, state = ?, is_deleted = 0, deleted_at = NULL, extra_json = ? WHERE id = ?",
                        desired.value(),
                        STATUS_ACTIVE,
                        extraJson,
                        existing.id()
                );
            } else {
                jdbcTemplate.update(
                        "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, ?, 0, ?)",
                        "it_connection",
                        desired.value(),
                        STATUS_ACTIVE,
                        extraJson
                );
            }
        }
    }

    private Map<String, Object> buildItConnectionExtra(DesiredItConnectionParameter desired, String categoryLabel) {
        LinkedHashMap<String, Object> extra = new LinkedHashMap<>();
        extra.put("category", desired.category());
        extra.put("category_label", categoryLabel);
        extra.put("equipment_type", "equipment_type".equals(desired.category()) ? desired.value() : "");
        extra.put("equipment_vendor", "equipment_vendor".equals(desired.category()) ? desired.value() : "");
        extra.put("equipment_model", "equipment_model".equals(desired.category()) ? desired.value() : "");
        extra.put("equipment_status", "equipment_status".equals(desired.category()) ? desired.value() : "");
        return extra;
    }

    private SyncTriggerResponse triggerAsync(String trigger) {
        if (!running.compareAndSet(false, true)) {
            return new SyncTriggerResponse(false, getStatus());
        }
        Instant startedAt = Instant.now();
        updateStatus(new SyncStatusSnapshot(
                "running",
                2,
                "Ставим синхронизацию NetBox в очередь",
                trigger,
                formatUtc(startedAt),
                null,
                List.of(),
                status.result(),
                status.lastSuccessAtUtc(),
                true,
                false,
                false,
                0,
                null
        ));
        executorService.submit(() -> syncNow(trigger));
        return new SyncTriggerResponse(true, getStatus());
    }

    private void validateSettings(NetBoxSyncSettings settings) {
        if (settings == null) {
            throw new IllegalStateException("Настройки NetBox отсутствуют");
        }
        if (!StringUtils.hasText(settings.baseUrl())) {
            throw new IllegalStateException("Укажите base URL NetBox");
        }
        if (!StringUtils.hasText(settings.apiToken())) {
            throw new IllegalStateException("Укажите API token NetBox");
        }
    }

    private void updateProgress(int progressPercent, String message) {
        SyncStatusSnapshot current = status;
        updateStatus(new SyncStatusSnapshot(
                "running",
                progressPercent,
                message,
                current.trigger(),
                current.startedAtUtc(),
                null,
                current.warnings(),
                current.result(),
                current.lastSuccessAtUtc(),
                true,
                current.fullOverwritePending(),
                current.enabled(),
                current.intervalMinutes(),
                current.nextRunAtUtc()
        ));
    }

    private SyncStatusSnapshot finish(String state,
                                      String trigger,
                                      Instant startedAt,
                                      SyncResultSummary result,
                                      List<String> warnings,
                                      boolean changed,
                                      boolean overwriteTriggered,
                                      boolean enabled,
                                      int intervalMinutes,
                                      String message) {
        Instant finishedAt = Instant.now();
        SyncStatusSnapshot snapshot = new SyncStatusSnapshot(
                state,
                100,
                message,
                trigger,
                formatUtc(startedAt),
                formatUtc(finishedAt),
                warnings == null ? List.of() : List.copyOf(warnings),
                result,
                "success".equals(state) ? formatUtc(finishedAt) : status.lastSuccessAtUtc(),
                false,
                overwriteTriggered,
                enabled,
                intervalMinutes,
                null
        );
        updateStatus(snapshot);
        return snapshot;
    }

    private void updateStatus(SyncStatusSnapshot snapshot) {
        status = snapshot;
    }

    private String formatUtc(Instant instant) {
        return instant == null ? null : UTC_FORMATTER.format(instant);
    }

    private String buildItParameterKey(String category, String value) {
        return category.trim().toLowerCase(Locale.ROOT) + "::" + value.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> parseExtraJson(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private void appendNamed(List<String> lines, String label, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(label + ": " + value);
        }
    }

    private List<Map<String, Object>> normalizePhotoList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> photos = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                photos.add(copy);
            }
        }
        return photos;
    }

    private String nestedString(Map<String, Object> source, String... path) {
        Object value = nestedValue(source, path);
        return stringValue(value);
    }

    private Object nestedValue(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return "";
        }
        return Arrays.stream(values)
                .map(this::stringValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private Long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private record PassportBuildResult(Map<String, Object> payload,
                                       List<String> newStoredFiles,
                                       List<String> obsoleteStoredFiles) {
    }

    private record PhotoMergeResult(List<Map<String, Object>> photos,
                                    List<String> newStoredFiles,
                                    List<String> obsoleteStoredFiles) {
    }

    private record FullOverwritePayload(List<Map<String, Object>> passports,
                                        List<String> newStoredFiles) {
    }

    private record CircuitSnapshot(String provider,
                                   String contractNumber,
                                   String speedDisplay,
                                   String tunnelType,
                                   boolean active,
                                   long speedKbps) {
    }

    private record DesiredItConnectionParameter(String category, String value) {
    }

    private record ExistingItConnectionParameter(Long id, String category, String value) {
    }

    private static final class SyncAccumulator {
        private int totalSites;
        private int created;
        private int updated;
        private int equipmentItems;
        private int photos;
        private final Set<DesiredItConnectionParameter> itParameters = new LinkedHashSet<>();

        void registerSite(Map<String, Object> payload) {
            totalSites += 1;
            equipmentItems += countItems(payload.get("equipment"));
            photos += countItems(payload.get("photos"));
            collectItParameters(payload.get("equipment"));
        }

        void registerCreated() {
            created += 1;
        }

        void registerUpdated() {
            updated += 1;
        }

        boolean changed() {
            return created > 0 || updated > 0;
        }

        int totalSites() {
            return totalSites;
        }

        int created() {
            return created;
        }

        int updated() {
            return updated;
        }

        Set<DesiredItConnectionParameter> itParameters() {
            return itParameters;
        }

        SyncResultSummary toSummary() {
            return new SyncResultSummary(totalSites, created, updated, equipmentItems, photos);
        }

        private void collectItParameters(Object rawEquipment) {
            if (!(rawEquipment instanceof Collection<?> collection)) {
                return;
            }
            for (Object item : collection) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                registerItParameter("equipment_type", localStringValue(map.get("equipment_type")));
                registerItParameter("equipment_vendor", localStringValue(map.get("vendor")));
                registerItParameter("equipment_model", localStringValue(map.get("model")));
                registerItParameter("equipment_status", localStringValue(map.get("status")));
            }
        }

        private void registerItParameter(String category, String value) {
            if (StringUtils.hasText(value)) {
                itParameters.add(new DesiredItConnectionParameter(category, value));
            }
        }

        private int countItems(Object raw) {
            return raw instanceof Collection<?> collection ? collection.size() : 0;
        }

        private String localStringValue(Object raw) {
            return raw == null ? "" : String.valueOf(raw).trim();
        }
    }

    public record SyncResultSummary(int totalSites,
                                    int createdPassports,
                                    int updatedPassports,
                                    int importedEquipmentItems,
                                    int importedPhotos) {
    }

    public record SyncStatusSnapshot(String state,
                                     int progressPercent,
                                     String message,
                                     String trigger,
                                     String startedAtUtc,
                                     String finishedAtUtc,
                                     List<String> warnings,
                                     SyncResultSummary result,
                                     String lastSuccessAtUtc,
                                     boolean running,
                                     boolean fullOverwritePending,
                                     boolean enabled,
                                     int intervalMinutes,
                                     String nextRunAtUtc) {

        static SyncStatusSnapshot idle() {
            return new SyncStatusSnapshot(
                    "idle",
                    0,
                    "Синхронизация NetBox ещё не запускалась.",
                    "",
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    false,
                    true,
                    false,
                    0,
                    null
            );
        }

        SyncStatusSnapshot withSettings(NetBoxSyncSettings settings, String nextRunAtUtc) {
            return new SyncStatusSnapshot(
                    state,
                    progressPercent,
                    message,
                    trigger,
                    startedAtUtc,
                    finishedAtUtc,
                    warnings,
                    result,
                    lastSuccessAtUtc,
                    running,
                    settings.fullOverwritePending(),
                    settings.enabled(),
                    settings.intervalMinutes(),
                    nextRunAtUtc
            );
        }
    }

    public record SyncTriggerResponse(boolean started, SyncStatusSnapshot status) {
    }
}
