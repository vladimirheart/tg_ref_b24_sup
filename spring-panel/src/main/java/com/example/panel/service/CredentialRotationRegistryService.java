package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.CredentialRotationRegistryEntry;
import com.example.panel.entity.IikoApiMonitor;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.CredentialRotationRegistryRepository;
import com.example.panel.repository.IikoApiMonitorRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.security.PanelSecurityProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class CredentialRotationRegistryService {

    public static final String MONITOR_KIND = "credential_rotation";
    public static final String CHECK_KIND = "rotation_registry";

    public static final String STATUS_HEALTHY = "healthy";
    public static final String STATUS_TRACKING_MISSING = "tracking_missing";
    public static final String STATUS_EXPIRES_SOON = "expires_soon";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_ROTATION_DUE_SOON = "rotation_due_soon";
    public static final String STATUS_ROTATION_OVERDUE = "rotation_overdue";
    public static final String STATUS_MISSING_SECRET = "missing_secret";
    public static final String STATUS_SOURCE_REMOVED = "source_removed";

    public static final String LEVEL_OK = "ok";
    public static final String LEVEL_WARNING = "warning";
    public static final String LEVEL_CRITICAL = "critical";
    public static final String INCIDENT_SIGNAL_TYPE = "credential_rotation";

    private static final int MAX_SUMMARY_LENGTH = 320;
    private static final int MAX_DETAILS_LENGTH = 1_500;
    private static final int[] WARNING_HORIZONS_DAYS = {30, 14, 7};
    private static final String DEFAULT_INTERNAL_BOT_API_TOKEN = "iguana-internal-bot-token";
    private static final String DEFAULT_REMEMBER_ME_KEY = "iguana-panel-remember-me";
    private static final String AUTOMATION_PARAM_TYPE = "employee_discount_automation_credentials.v1";
    private static final String INCIDENT_SOURCE = "credential_rotation_registry";
    private static final String INCIDENT_ACTOR = "system";

    private final CredentialRotationRegistryRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final SharedConfigService sharedConfigService;
    private final ChannelRepository channelRepository;
    private final IikoApiMonitorRepository iikoApiMonitorRepository;
    private final LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;
    private final NetBoxSyncSettingsService netBoxSyncSettingsService;
    private final JdbcTemplate jdbcTemplate;
    private final PanelSecurityProperties panelSecurityProperties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final IncidentService incidentService;
    private final Clock clock;

    public CredentialRotationRegistryService(CredentialRotationRegistryRepository repository,
                                             MonitoringCheckHistoryRepository historyRepository,
                                             SharedConfigService sharedConfigService,
                                             ChannelRepository channelRepository,
                                             IikoApiMonitorRepository iikoApiMonitorRepository,
                                             LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                             NetBoxSyncSettingsService netBoxSyncSettingsService,
                                             JdbcTemplate jdbcTemplate,
                                             PanelSecurityProperties panelSecurityProperties,
                                             ObjectMapper objectMapper,
                                             IncidentService incidentService,
                                             Environment environment) {
        this(
            repository,
            historyRepository,
            sharedConfigService,
            channelRepository,
            iikoApiMonitorRepository,
            locationsIikoServerSourceSettingsService,
            netBoxSyncSettingsService,
            jdbcTemplate,
            panelSecurityProperties,
            objectMapper,
            incidentService,
            environment,
            Clock.systemUTC()
        );
    }

    CredentialRotationRegistryService(CredentialRotationRegistryRepository repository,
                                      MonitoringCheckHistoryRepository historyRepository,
                                      SharedConfigService sharedConfigService,
                                      ChannelRepository channelRepository,
                                      IikoApiMonitorRepository iikoApiMonitorRepository,
                                      LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                      NetBoxSyncSettingsService netBoxSyncSettingsService,
                                      JdbcTemplate jdbcTemplate,
                                      PanelSecurityProperties panelSecurityProperties,
                                      ObjectMapper objectMapper,
                                      IncidentService incidentService,
                                      Environment environment,
                                      Clock clock) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.sharedConfigService = sharedConfigService;
        this.channelRepository = channelRepository;
        this.iikoApiMonitorRepository = iikoApiMonitorRepository;
        this.locationsIikoServerSourceSettingsService = locationsIikoServerSourceSettingsService;
        this.netBoxSyncSettingsService = netBoxSyncSettingsService;
        this.jdbcTemplate = jdbcTemplate;
        this.panelSecurityProperties = panelSecurityProperties;
        this.objectMapper = objectMapper;
        this.incidentService = incidentService;
        this.environment = environment;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public RegistrySnapshot buildSnapshot() {
        return syncRegistry(false);
    }

    public RegistrySnapshot refreshAll() {
        return syncRegistry(true);
    }

    public CredentialRotationRegistryEntry updateMetadata(long entryId, MetadataPatch patch) {
        CredentialRotationRegistryEntry existing = repository.findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Registry entry not found"));
        AlertState previousAlertState = captureAlertState(existing);
        OffsetDateTime now = OffsetDateTime.now(clock);
        long startedAtNs = System.nanoTime();

        existing.setOwnerName(normalizeOptional(patch.ownerName()));
        existing.setNote(normalizeOptional(patch.note()));
        existing.setExpiresAt(parseTimestamp(patch.expiresAt(), "expires_at"));
        existing.setRotatedAt(parseTimestamp(patch.rotatedAt(), "rotated_at"));
        existing.setRotationIntervalDays(normalizeRotationIntervalDays(patch.rotationIntervalDays()));
        existing.setLastCheckedAt(now);
        existing.setUpdatedAt(now);

        applyStatus(existing, now);
        repository.save(existing);
        recordHistory(existing, elapsedMillis(startedAtNs), now);
        syncAlertIncidents(List.of(existing), previousStatesFor(existing, previousAlertState));
        return existing;
    }

    public List<MonitoringCheckHistoryRepository.HistoryEntry> loadHistory(long entryId, int limit) {
        if (!repository.existsById(entryId)) {
            throw new IllegalArgumentException("Registry entry not found");
        }
        return historyRepository.findRecent(MONITOR_KIND, entryId, limit);
    }

    private RegistrySnapshot syncRegistry(boolean recordHistory) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<CredentialRotationRegistryEntry> existing = repository.findAllByOrderByDisplayNameAscIdAsc();
        Map<String, CredentialRotationRegistryEntry> existingByKey = new LinkedHashMap<>();
        Map<String, AlertState> previousAlertStates = new LinkedHashMap<>();
        for (CredentialRotationRegistryEntry item : existing) {
            if (StringUtils.hasText(item.getEntryKey())) {
                existingByKey.put(item.getEntryKey(), item);
                previousAlertStates.put(item.getEntryKey(), captureAlertState(item));
            }
        }

        Map<String, DiscoveredCredential> discoveredByKey = discoverCredentials();
        List<CredentialRotationRegistryEntry> refreshed = new ArrayList<>();

        for (DiscoveredCredential discovered : discoveredByKey.values()) {
            long startedAtNs = System.nanoTime();
            CredentialRotationRegistryEntry item = existingByKey.remove(discovered.entryKey());
            if (item == null) {
                item = new CredentialRotationRegistryEntry();
                item.setCreatedAt(now);
                item.setOwnerName(null);
                item.setNote(null);
                item.setExpiresAt(null);
                item.setRotatedAt(null);
                item.setRotationIntervalDays(null);
            }
            applyDiscovery(item, discovered, now);
            applyStatus(item, now);
            repository.save(item);
            if (recordHistory) {
                recordHistory(item, elapsedMillis(startedAtNs), now);
            }
            refreshed.add(item);
        }

        for (CredentialRotationRegistryEntry stale : existingByKey.values()) {
            long startedAtNs = System.nanoTime();
            stale.setSourcePresent(false);
            stale.setSecretPresent(false);
            stale.setLastSeenAt(stale.getLastSeenAt() != null ? stale.getLastSeenAt() : now);
            stale.setLastCheckedAt(now);
            stale.setUpdatedAt(now);
            applyStatus(stale, now);
            repository.save(stale);
            if (recordHistory) {
                recordHistory(stale, elapsedMillis(startedAtNs), now);
            }
            refreshed.add(stale);
        }

        List<CredentialRotationRegistryEntry> sorted = sortEntries(refreshed);
        syncAlertIncidents(sorted, previousAlertStates);
        return new RegistrySnapshot(now, buildOverview(sorted), sorted);
    }

    private void applyDiscovery(CredentialRotationRegistryEntry item,
                                DiscoveredCredential discovered,
                                OffsetDateTime now) {
        item.setEntryKey(discovered.entryKey());
        item.setIntegrationKind(discovered.integrationKind());
        item.setCredentialKind(discovered.credentialKind());
        item.setDisplayName(discovered.displayName());
        item.setSourceType(discovered.sourceType());
        item.setSourceRef(discovered.sourceRef());
        item.setSourcePresent(true);
        item.setSecretPresent(discovered.secretPresent());
        item.setLastSeenAt(now);
        item.setLastCheckedAt(now);
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(now);
        }
        item.setUpdatedAt(now);
    }

    private Map<String, DiscoveredCredential> discoverCredentials() {
        Map<String, DiscoveredCredential> discovered = new LinkedHashMap<>();
        Map<String, Object> settings = sharedConfigService.loadSettings();
        List<Channel> channels = channelRepository.findAll();

        addSharedBotCredentials(discovered, sharedConfigService.loadBotCredentials());
        addLegacyChannelTokens(discovered, channels);
        addChannelWebhookSecrets(discovered, channels);
        addSettingsSecrets(discovered, settings);
        addLocationsIikoServerSources(discovered, settings);
        addIikoApiMonitorSecrets(discovered);
        addAutomationCredentials(discovered);
        addRuntimeSecrets(discovered);

        return discovered;
    }

    private void addSharedBotCredentials(Map<String, DiscoveredCredential> target, List<BotCredential> credentials) {
        if (credentials == null) {
            return;
        }
        for (BotCredential credential : credentials) {
            if (credential == null) {
                continue;
            }
            String platform = normalizeKey(credential.platform(), "telegram");
            String name = normalizeOptional(credential.name());
            String keyPart = credential.id() != null
                ? String.valueOf(credential.id())
                : slug(name + "-" + platform);
            if (!StringUtils.hasText(keyPart)) {
                continue;
            }
            register(target, new DiscoveredCredential(
                "shared.bot-credential." + keyPart,
                platform,
                "bot_token",
                "Bot credential · " + defaultIfBlank(name, platform.toUpperCase(Locale.ROOT)),
                "shared_config",
                "bot_credentials.json#" + keyPart,
                StringUtils.hasText(credential.token())
            ));
        }
    }

    private void addLegacyChannelTokens(Map<String, DiscoveredCredential> target, List<Channel> channels) {
        if (channels == null) {
            return;
        }
        for (Channel channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            boolean shouldTrack = StringUtils.hasText(channel.getToken()) || channel.getCredentialId() == null;
            if (!shouldTrack) {
                continue;
            }
            register(target, new DiscoveredCredential(
                "channel." + channel.getId() + ".token",
                normalizePlatform(channel),
                "bot_token",
                "Channel token · " + defaultIfBlank(channel.getChannelName(), "channel #" + channel.getId()),
                "channel",
                "channels#" + channel.getId() + ".token",
                StringUtils.hasText(channel.getToken())
            ));
        }
    }

    private void addChannelWebhookSecrets(Map<String, DiscoveredCredential> target, List<Channel> channels) {
        if (channels == null) {
            return;
        }
        for (Channel channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            String platform = normalizePlatform(channel);
            Map<String, Object> platformConfig = parseJsonMap(channel.getPlatformConfig());
            String displayName = defaultIfBlank(channel.getChannelName(), "channel #" + channel.getId());
            if ("vk".equals(platform)) {
                Integer groupId = readInteger(platformConfig, "group_id", "groupId");
                boolean webhookExpected = groupId != null && groupId > 0;
                String confirmationToken = readString(platformConfig, "confirmation_token", "confirmationToken");
                String secret = readString(platformConfig, "secret", "callback_secret", "callbackSecret");
                if (webhookExpected || StringUtils.hasText(confirmationToken)) {
                    register(target, new DiscoveredCredential(
                        "channel." + channel.getId() + ".vk.confirmation_token",
                        "vk",
                        "webhook_confirmation_token",
                        "VK confirmation token · " + displayName,
                        "channel_config",
                        "channels#" + channel.getId() + ".platformConfig.confirmation_token",
                        StringUtils.hasText(confirmationToken)
                    ));
                }
                if (webhookExpected || StringUtils.hasText(secret)) {
                    register(target, new DiscoveredCredential(
                        "channel." + channel.getId() + ".vk.webhook_secret",
                        "vk",
                        "webhook_secret",
                        "VK webhook secret · " + displayName,
                        "channel_config",
                        "channels#" + channel.getId() + ".platformConfig.secret",
                        StringUtils.hasText(secret)
                    ));
                }
            }
            if ("max".equals(platform)) {
                String secret = readString(platformConfig, "secret", "webhook_secret", "webhookSecret");
                if (StringUtils.hasText(secret)) {
                    register(target, new DiscoveredCredential(
                        "channel." + channel.getId() + ".max.webhook_secret",
                        "max",
                        "webhook_secret",
                        "MAX webhook secret · " + displayName,
                        "channel_config",
                        "channels#" + channel.getId() + ".platformConfig.webhook_secret",
                        true
                    ));
                }
            }
        }
    }

    private void addSettingsSecrets(Map<String, DiscoveredCredential> target, Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return;
        }

        NetBoxSyncSettingsService.NetBoxSyncSettings netBox = netBoxSyncSettingsService.load(settings);
        if (netBox.enabled() || StringUtils.hasText(netBox.baseUrl()) || StringUtils.hasText(netBox.apiToken())) {
            register(target, new DiscoveredCredential(
                "settings.netbox.api_token",
                "netbox",
                "api_token",
                "NetBox sync API token",
                "settings_json",
                "settings.json#netbox_sync.api_token",
                StringUtils.hasText(netBox.apiToken())
            ));
        }

        Map<String, Object> notion = mapValue(settings.get("knowledge_base_config"));
        String notionToken = readString(notion, "token");
        boolean notionConfigured = parseBoolean(notion.get("enabled"))
            || StringUtils.hasText(readString(notion, "source_url", "sourceUrl"))
            || StringUtils.hasText(notionToken);
        if (notionConfigured) {
            register(target, new DiscoveredCredential(
                "settings.notion.token",
                "notion",
                "integration_token",
                "Notion knowledge base token",
                "settings_json",
                "settings.json#knowledge_base_config.token",
                StringUtils.hasText(notionToken)
            ));
        }

        Map<String, Object> dialogConfig = mapValue(settings.get("dialog_config"));
        String macroExternalUrl = readString(dialogConfig, "macro_variable_catalog_external_url");
        String macroExternalToken = readString(dialogConfig, "macro_variable_catalog_external_auth_token");
        if (StringUtils.hasText(macroExternalUrl) || StringUtils.hasText(macroExternalToken)) {
            register(target, new DiscoveredCredential(
                "settings.dialog.macro_external_auth_token",
                "dialog",
                "external_auth_token",
                "Dialog macro external catalog auth token",
                "settings_json",
                "settings.json#dialog_config.macro_variable_catalog_external_auth_token",
                StringUtils.hasText(macroExternalToken)
            ));
        }

        String workspaceExternalUrl = readString(dialogConfig, "workspace_client_external_profile_url");
        String workspaceExternalToken = readString(dialogConfig, "workspace_client_external_profile_auth_token");
        if (StringUtils.hasText(workspaceExternalUrl) || StringUtils.hasText(workspaceExternalToken)) {
            register(target, new DiscoveredCredential(
                "settings.dialog.workspace_external_profile_auth_token",
                "dialog",
                "external_auth_token",
                "Workspace external profile auth token",
                "settings_json",
                "settings.json#dialog_config.workspace_client_external_profile_auth_token",
                StringUtils.hasText(workspaceExternalToken)
            ));
        }
    }

    private void addLocationsIikoServerSources(Map<String, DiscoveredCredential> target, Map<String, Object> settings) {
        List<LocationsIikoServerSourceSettingsService.LocationIikoServerSource> sources =
            locationsIikoServerSourceSettingsService.loadForRuntime(settings);
        for (LocationsIikoServerSourceSettingsService.LocationIikoServerSource source : sources) {
            if (source == null || !StringUtils.hasText(source.id())) {
                continue;
            }
            register(target, new DiscoveredCredential(
                "settings.locations_iiko_server_sources." + source.id(),
                "iiko_server",
                "api_secret",
                "iikoServer source secret · " + defaultIfBlank(source.name(), source.baseUrl()),
                "settings_json",
                "settings.json#locations_iiko_server_sources[" + source.id() + "].api_secret",
                StringUtils.hasText(source.apiSecret())
            ));
        }
    }

    private void addIikoApiMonitorSecrets(Map<String, DiscoveredCredential> target) {
        List<IikoApiMonitor> monitors = iikoApiMonitorRepository.findAllByOrderByMonitorNameAscIdAsc();
        for (IikoApiMonitor monitor : monitors) {
            if (monitor == null || monitor.getId() == null) {
                continue;
            }
            register(target, new DiscoveredCredential(
                "monitoring.iiko_api_monitors." + monitor.getId(),
                "iiko",
                "api_login",
                "iiko API monitor key · " + defaultIfBlank(monitor.getMonitorName(), "monitor #" + monitor.getId()),
                "monitoring_db",
                "iiko_api_monitors#" + monitor.getId() + ".api_login",
                StringUtils.hasText(monitor.getApiLogin())
            ));
        }
    }

    private void addAutomationCredentials(Map<String, DiscoveredCredential> target) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT value, extra_json
              FROM settings_parameters
             WHERE param_type = ?
               AND is_deleted = FALSE
            """,
            AUTOMATION_PARAM_TYPE
        );
        for (Map<String, Object> row : rows) {
            String username = normalizeOptional(row.get("value"));
            Map<String, Object> payload = parseJsonMap(Objects.toString(row.get("extra_json"), ""));
            if (!StringUtils.hasText(username) || payload.isEmpty()) {
                continue;
            }

            Map<String, Object> bitrix24 = mapValue(payload.get("bitrix24"));
            String portalUrl = readString(bitrix24, "portal_url", "portalUrl");
            String webhookUrl = readString(bitrix24, "webhook_url", "webhookUrl");
            if (StringUtils.hasText(portalUrl) || StringUtils.hasText(webhookUrl)) {
                register(target, new DiscoveredCredential(
                    "employee-discount." + slug(username) + ".bitrix24.webhook_url",
                    "bitrix24",
                    "webhook_url",
                    "Bitrix24 webhook · " + username,
                    "settings_parameters",
                    "settings_parameters.employee_discount_automation_credentials.v1[" + username + "].bitrix24.webhook_url",
                    StringUtils.hasText(webhookUrl)
                ));
            }

            Map<String, Object> iikoProfiles = mapValue(payload.get("iiko_profiles"));
            for (Map.Entry<String, Object> profileEntry : iikoProfiles.entrySet()) {
                Map<String, Object> profile = mapValue(profileEntry.getValue());
                String baseUrl = readString(profile, "base_url", "baseUrl");
                String apiSecret = readString(profile, "api_secret", "apiSecret", "password");
                String profileKey = slug(defaultIfBlank(baseUrl, profileEntry.getKey()));
                if (!StringUtils.hasText(baseUrl) && !StringUtils.hasText(apiSecret)) {
                    continue;
                }
                register(target, new DiscoveredCredential(
                    "employee-discount." + slug(username) + ".iiko." + profileKey + ".api_secret",
                    "iiko",
                    "api_secret",
                    "iiko profile secret · " + username + " · " + defaultIfBlank(baseUrl, profileEntry.getKey()),
                    "settings_parameters",
                    "settings_parameters.employee_discount_automation_credentials.v1[" + username + "].iiko_profiles[" + profileEntry.getKey() + "].api_secret",
                    StringUtils.hasText(apiSecret)
                ));
            }
        }
    }

    private void addRuntimeSecrets(Map<String, DiscoveredCredential> target) {
        String internalApiToken = environment.getProperty("app.bots.internal-api.token", DEFAULT_INTERNAL_BOT_API_TOKEN);
        register(target, new DiscoveredCredential(
            "env.app_internal_bot_api_token",
            "internal_api",
            "shared_secret",
            "Panel internal bot API token",
            "environment",
            "app.bots.internal-api.token",
            StringUtils.hasText(internalApiToken)
        ));

        String signatureSecret = environment.getProperty("app.bots.internal-api.signature-secret", "");
        if (StringUtils.hasText(signatureSecret)) {
            register(target, new DiscoveredCredential(
                "env.app_internal_bot_api_signature_secret",
                "internal_api",
                "signature_secret",
                "Panel internal bot API signature secret",
                "environment",
                "app.bots.internal-api.signature-secret",
                true
            ));
        }

        String rememberMeKey = panelSecurityProperties.getRememberMeKey();
        if (!StringUtils.hasText(rememberMeKey)) {
            rememberMeKey = DEFAULT_REMEMBER_ME_KEY;
        }
        register(target, new DiscoveredCredential(
            "env.app_security_remember_me_key",
            "panel_security",
            "remember_me_key",
            "Panel remember-me key",
            "environment",
            "app.security.remember-me-key",
            StringUtils.hasText(rememberMeKey)
        ));
    }

    private void applyStatus(CredentialRotationRegistryEntry item, OffsetDateTime now) {
        StatusAssessment assessment = assessStatus(item, now);
        item.setLastStatus(assessment.statusCode());
        item.setStatusLevel(assessment.statusLevel());
        item.setStatusReason(assessment.reason());
        item.setNextRotationDueAt(assessment.nextRotationDueAt());
    }

    private StatusAssessment assessStatus(CredentialRotationRegistryEntry item, OffsetDateTime now) {
        if (!Boolean.TRUE.equals(item.getSourcePresent())) {
            return new StatusAssessment(
                STATUS_SOURCE_REMOVED,
                LEVEL_CRITICAL,
                "Источник секрета больше не обнаружен. Проверьте cleanup или перенастройку интеграции.",
                computeNextRotationDueAt(item.getRotatedAt(), item.getRotationIntervalDays())
            );
        }
        if (!Boolean.TRUE.equals(item.getSecretPresent())) {
            return new StatusAssessment(
                STATUS_MISSING_SECRET,
                LEVEL_CRITICAL,
                "Источник найден, но значение секрета сейчас пустое или не сохранено.",
                computeNextRotationDueAt(item.getRotatedAt(), item.getRotationIntervalDays())
            );
        }

        OffsetDateTime nextRotationDueAt = computeNextRotationDueAt(item.getRotatedAt(), item.getRotationIntervalDays());
        List<StatusCandidate> candidates = new ArrayList<>();

        addExpiryCandidate(candidates, item.getExpiresAt(), now);
        addRotationCandidate(candidates, nextRotationDueAt, now);

        if (candidates.isEmpty()) {
            return new StatusAssessment(
                STATUS_TRACKING_MISSING,
                LEVEL_WARNING,
                "Заполните expires_at или rotated_at + rotation_interval_days, чтобы отслеживать expiry/rotation окна.",
                nextRotationDueAt
            );
        }

        candidates.sort(Comparator
            .comparingInt((StatusCandidate itemCandidate) -> severityRank(itemCandidate.statusLevel()))
            .thenComparing(itemCandidate -> itemCandidate.dueAt() == null ? OffsetDateTime.MAX : itemCandidate.dueAt()));

        StatusCandidate top = candidates.get(0);
        if (severityRank(top.statusLevel()) > severityRank(LEVEL_WARNING)) {
            return new StatusAssessment(STATUS_HEALTHY, LEVEL_OK, "Rotation metadata заполнена; ближайшие окна вне warning horizon.", nextRotationDueAt);
        }
        return new StatusAssessment(top.statusCode(), top.statusLevel(), top.reason(), nextRotationDueAt);
    }

    private void addExpiryCandidate(List<StatusCandidate> candidates, OffsetDateTime expiresAt, OffsetDateTime now) {
        if (expiresAt == null) {
            return;
        }
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate());
        if (expiresAt.isBefore(now)) {
            candidates.add(new StatusCandidate(
                STATUS_EXPIRED,
                LEVEL_CRITICAL,
                "Секрет истёк " + formatWhen(expiresAt) + ".",
                expiresAt
            ));
            return;
        }
        if (days <= 7) {
            candidates.add(new StatusCandidate(
                STATUS_EXPIRES_SOON,
                LEVEL_CRITICAL,
                "Секрет истекает " + formatWhen(expiresAt) + " (через " + Math.max(days, 0) + " дн.).",
                expiresAt
            ));
            return;
        }
        if (days <= 14 || days <= 30) {
            candidates.add(new StatusCandidate(
                STATUS_EXPIRES_SOON,
                LEVEL_WARNING,
                "Секрет истекает " + formatWhen(expiresAt) + " (через " + Math.max(days, 0) + " дн.).",
                expiresAt
            ));
            return;
        }
        candidates.add(new StatusCandidate(
            STATUS_HEALTHY,
            LEVEL_OK,
            "Expiry за пределами warning horizon: " + formatWhen(expiresAt) + ".",
            expiresAt
        ));
    }

    private void addRotationCandidate(List<StatusCandidate> candidates, OffsetDateTime nextRotationDueAt, OffsetDateTime now) {
        if (nextRotationDueAt == null) {
            return;
        }
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), nextRotationDueAt.toLocalDate());
        if (nextRotationDueAt.isBefore(now)) {
            candidates.add(new StatusCandidate(
                STATUS_ROTATION_OVERDUE,
                LEVEL_CRITICAL,
                "Плановая ротация просрочена с " + formatWhen(nextRotationDueAt) + ".",
                nextRotationDueAt
            ));
            return;
        }
        if (days <= 7) {
            candidates.add(new StatusCandidate(
                STATUS_ROTATION_DUE_SOON,
                LEVEL_CRITICAL,
                "Плановая ротация требуется до " + formatWhen(nextRotationDueAt) + " (через " + Math.max(days, 0) + " дн.).",
                nextRotationDueAt
            ));
            return;
        }
        if (days <= 14 || days <= 30) {
            candidates.add(new StatusCandidate(
                STATUS_ROTATION_DUE_SOON,
                LEVEL_WARNING,
                "Плановая ротация требуется до " + formatWhen(nextRotationDueAt) + " (через " + Math.max(days, 0) + " дн.).",
                nextRotationDueAt
            ));
            return;
        }
        candidates.add(new StatusCandidate(
            STATUS_HEALTHY,
            LEVEL_OK,
            "Следующее окно ротации: " + formatWhen(nextRotationDueAt) + ".",
            nextRotationDueAt
        ));
    }

    private OffsetDateTime computeNextRotationDueAt(OffsetDateTime rotatedAt, Integer rotationIntervalDays) {
        if (rotatedAt == null || rotationIntervalDays == null || rotationIntervalDays <= 0) {
            return null;
        }
        return rotatedAt.plusDays(rotationIntervalDays.longValue());
    }

    private RegistryOverview buildOverview(List<CredentialRotationRegistryEntry> items) {
        int total = 0;
        int ok = 0;
        int warning = 0;
        int critical = 0;
        int trackingMissing = 0;
        int missingSecret = 0;
        int sourceRemoved = 0;
        for (CredentialRotationRegistryEntry item : items) {
            total++;
            String level = normalizeKey(item.getStatusLevel(), LEVEL_CRITICAL);
            if (LEVEL_OK.equals(level)) {
                ok++;
            } else if (LEVEL_WARNING.equals(level)) {
                warning++;
            } else {
                critical++;
            }
            String status = normalizeKey(item.getLastStatus(), "");
            if (STATUS_TRACKING_MISSING.equals(status)) {
                trackingMissing++;
            } else if (STATUS_MISSING_SECRET.equals(status)) {
                missingSecret++;
            } else if (STATUS_SOURCE_REMOVED.equals(status)) {
                sourceRemoved++;
            }
        }
        return new RegistryOverview(total, ok, warning, critical, trackingMissing, missingSecret, sourceRemoved);
    }

    private void recordHistory(CredentialRotationRegistryEntry item, long durationMs, OffsetDateTime createdAt) {
        if (item.getId() == null) {
            return;
        }
        historyRepository.record(
            MONITOR_KIND,
            item.getId(),
            CHECK_KIND,
            item.getLastStatus(),
            trim(buildSummary(item), MAX_SUMMARY_LENGTH),
            trim(buildDetails(item), MAX_DETAILS_LENGTH),
            null,
            durationMs,
            createdAt
        );
    }

    private void syncAlertIncidents(List<CredentialRotationRegistryEntry> items,
                                    Map<String, AlertState> previousAlertStates) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, Boolean> activeIncidentByKey = loadActiveIncidentStates();
        for (CredentialRotationRegistryEntry item : items) {
            syncAlertIncident(item, previousAlertStates, activeIncidentByKey);
        }
    }

    private void syncAlertIncident(CredentialRotationRegistryEntry item,
                                   Map<String, AlertState> previousAlertStates,
                                   Map<String, Boolean> activeIncidentByKey) {
        String entryKey = normalizeOptional(item.getEntryKey());
        if (!StringUtils.hasText(entryKey)) {
            return;
        }
        AlertState current = captureAlertState(item);
        AlertState previous = previousAlertStates.get(entryKey);
        boolean hasActiveIncident = Boolean.TRUE.equals(activeIncidentByKey.get(entryKey));
        if (current.critical()) {
            boolean changed = previous == null
                || !previous.critical()
                || !Objects.equals(previous.fingerprint(), current.fingerprint());
            if (changed || !hasActiveIncident) {
                incidentService.openOrRefreshSignalIncident(
                    INCIDENT_SIGNAL_TYPE,
                    entryKey,
                    buildIncidentTitle(item),
                    buildIncidentSummary(item),
                    buildIncidentDescription(item),
                    LEVEL_CRITICAL,
                    INCIDENT_SOURCE,
                    buildIncidentPayload(item),
                    INCIDENT_ACTOR
                );
            }
            return;
        }
        if ((previous != null && previous.critical()) || hasActiveIncident) {
            incidentService.resolveSignalIncident(
                INCIDENT_SIGNAL_TYPE,
                entryKey,
                buildIncidentResolvedText(item),
                buildIncidentPayload(item),
                INCIDENT_ACTOR
            );
        }
    }

    private Map<String, Boolean> loadActiveIncidentStates() {
        Map<String, Boolean> activeByKey = new LinkedHashMap<>();
        for (Map<String, Object> incident : incidentService.listIncidentSummariesForSignalType(INCIDENT_SIGNAL_TYPE)) {
            String signalKey = normalizeOptional(incident.get("signal_key"));
            if (!StringUtils.hasText(signalKey)) {
                continue;
            }
            if (isIncidentActive(incident)) {
                activeByKey.put(signalKey, true);
            } else {
                activeByKey.putIfAbsent(signalKey, false);
            }
        }
        return activeByKey;
    }

    private boolean isIncidentActive(Map<String, Object> incident) {
        String status = normalizeKey(incident.get("status"), "");
        return !"resolved".equals(status) && !"closed".equals(status);
    }

    private AlertState captureAlertState(CredentialRotationRegistryEntry item) {
        if (item == null) {
            return new AlertState(false, "");
        }
        boolean critical = LEVEL_CRITICAL.equals(normalizeKey(item.getStatusLevel(), LEVEL_CRITICAL));
        return new AlertState(critical, buildAlertFingerprint(item));
    }

    private String buildAlertFingerprint(CredentialRotationRegistryEntry item) {
        List<String> parts = new ArrayList<>();
        parts.add(defaultIfBlank(item.getDisplayName(), ""));
        parts.add(defaultIfBlank(item.getIntegrationKind(), ""));
        parts.add(defaultIfBlank(item.getCredentialKind(), ""));
        parts.add(defaultIfBlank(item.getSourceRef(), ""));
        parts.add(defaultIfBlank(item.getLastStatus(), ""));
        parts.add(defaultIfBlank(item.getStatusLevel(), ""));
        parts.add(defaultIfBlank(item.getStatusReason(), ""));
        parts.add(String.valueOf(Boolean.TRUE.equals(item.getSourcePresent())));
        parts.add(String.valueOf(Boolean.TRUE.equals(item.getSecretPresent())));
        parts.add(stringifyTimestamp(item.getExpiresAt()));
        parts.add(stringifyTimestamp(item.getRotatedAt()));
        parts.add(stringifyTimestamp(item.getNextRotationDueAt()));
        parts.add(item.getRotationIntervalDays() == null ? "" : String.valueOf(item.getRotationIntervalDays()));
        parts.add(defaultIfBlank(item.getOwnerName(), ""));
        parts.add(defaultIfBlank(item.getNote(), ""));
        return String.join("|", parts);
    }

    private String buildIncidentTitle(CredentialRotationRegistryEntry item) {
        return "Credential rotation critical: " + defaultIfBlank(item.getDisplayName(), defaultIfBlank(item.getEntryKey(), "credential"));
    }

    private String buildIncidentSummary(CredentialRotationRegistryEntry item) {
        return defaultIfBlank(item.getStatusReason(), defaultIfBlank(item.getLastStatus(), "Credential rotation alert"));
    }

    private String buildIncidentDescription(CredentialRotationRegistryEntry item) {
        return """
            Credential rotation registry detected a critical secret hygiene condition.
            Review owner/rotation metadata, verify the integration source and resolve the secret lifecycle risk.
            Use the credential rotation analytics page for registry history and related monitoring context.
            """.trim();
    }

    private String buildIncidentResolvedText(CredentialRotationRegistryEntry item) {
        return "Credential rotation entry returned to non-critical state: "
            + defaultIfBlank(item.getDisplayName(), defaultIfBlank(item.getEntryKey(), "credential"));
    }

    private Map<String, Object> buildIncidentPayload(CredentialRotationRegistryEntry item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entry_id", item.getId());
        payload.put("entry_key", defaultIfBlank(item.getEntryKey(), ""));
        payload.put("display_name", defaultIfBlank(item.getDisplayName(), ""));
        payload.put("integration_kind", defaultIfBlank(item.getIntegrationKind(), ""));
        payload.put("credential_kind", defaultIfBlank(item.getCredentialKind(), ""));
        payload.put("source_type", defaultIfBlank(item.getSourceType(), ""));
        payload.put("source_ref", defaultIfBlank(item.getSourceRef(), ""));
        payload.put("source_present", Boolean.TRUE.equals(item.getSourcePresent()));
        payload.put("secret_present", Boolean.TRUE.equals(item.getSecretPresent()));
        payload.put("status_level", defaultIfBlank(item.getStatusLevel(), ""));
        payload.put("last_status", defaultIfBlank(item.getLastStatus(), ""));
        payload.put("status_reason", defaultIfBlank(item.getStatusReason(), ""));
        payload.put("expires_at", stringifyTimestamp(item.getExpiresAt()));
        payload.put("rotated_at", stringifyTimestamp(item.getRotatedAt()));
        payload.put("next_rotation_due_at", stringifyTimestamp(item.getNextRotationDueAt()));
        payload.put("rotation_interval_days", item.getRotationIntervalDays());
        payload.put("owner_name", defaultIfBlank(item.getOwnerName(), ""));
        payload.put("note", defaultIfBlank(item.getNote(), ""));
        payload.put("last_checked_at", stringifyTimestamp(item.getLastCheckedAt()));
        payload.put("last_seen_at", stringifyTimestamp(item.getLastSeenAt()));
        return payload;
    }

    private String stringifyTimestamp(OffsetDateTime value) {
        return value == null ? "" : value.toString();
    }

    private Map<String, AlertState> previousStatesFor(CredentialRotationRegistryEntry item, AlertState state) {
        Map<String, AlertState> states = new LinkedHashMap<>();
        String entryKey = normalizeOptional(item.getEntryKey());
        if (StringUtils.hasText(entryKey)) {
            states.put(entryKey, state);
        }
        return states;
    }

    private String buildSummary(CredentialRotationRegistryEntry item) {
        return item.getDisplayName() + ": " + defaultIfBlank(item.getStatusReason(), defaultIfBlank(item.getLastStatus(), "status updated"));
    }

    private String buildDetails(CredentialRotationRegistryEntry item) {
        List<String> parts = new ArrayList<>();
        parts.add("integration=" + defaultIfBlank(item.getIntegrationKind(), "unknown"));
        parts.add("credential=" + defaultIfBlank(item.getCredentialKind(), "unknown"));
        parts.add("source=" + defaultIfBlank(item.getSourceRef(), "unknown"));
        parts.add("source_present=" + Boolean.TRUE.equals(item.getSourcePresent()));
        parts.add("secret_present=" + Boolean.TRUE.equals(item.getSecretPresent()));
        if (item.getExpiresAt() != null) {
            parts.add("expires_at=" + item.getExpiresAt());
        }
        if (item.getRotatedAt() != null) {
            parts.add("rotated_at=" + item.getRotatedAt());
        }
        if (item.getRotationIntervalDays() != null) {
            parts.add("rotation_interval_days=" + item.getRotationIntervalDays());
        }
        if (item.getNextRotationDueAt() != null) {
            parts.add("next_rotation_due_at=" + item.getNextRotationDueAt());
        }
        if (StringUtils.hasText(item.getOwnerName())) {
            parts.add("owner=" + item.getOwnerName().trim());
        }
        if (StringUtils.hasText(item.getNote())) {
            parts.add("note=" + item.getNote().trim());
        }
        if (StringUtils.hasText(item.getStatusReason())) {
            parts.add("reason=" + item.getStatusReason().trim());
        }
        return String.join("; ", parts);
    }

    private List<CredentialRotationRegistryEntry> sortEntries(List<CredentialRotationRegistryEntry> items) {
        return items.stream()
            .sorted(Comparator
                .comparingInt((CredentialRotationRegistryEntry item) -> severityRank(item.getStatusLevel()))
                .thenComparing(item -> normalizeOptional(item.getDisplayName()))
                .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
            .toList();
    }

    private int severityRank(String statusLevel) {
        String normalized = normalizeKey(statusLevel, LEVEL_CRITICAL);
        if (LEVEL_CRITICAL.equals(normalized)) {
            return 0;
        }
        if (LEVEL_WARNING.equals(normalized)) {
            return 1;
        }
        return 2;
    }

    private long elapsedMillis(long startedAtNs) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - startedAtNs).toMillis());
    }

    private void register(Map<String, DiscoveredCredential> target, DiscoveredCredential discovered) {
        if (discovered == null || !StringUtils.hasText(discovered.entryKey())) {
            return;
        }
        target.putIfAbsent(discovered.entryKey(), discovered);
    }

    private String normalizePlatform(Channel channel) {
        return normalizeKey(channel != null ? channel.getPlatform() : null, "telegram");
    }

    private String normalizeKey(String raw, String fallback) {
        String normalized = normalizeOptional(raw);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : fallback;
    }

    private String normalizeOptional(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Integer normalizeRotationIntervalDays(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 1 || value > 3_650) {
            throw new IllegalArgumentException("rotation_interval_days must be between 1 and 3650");
        }
        return value;
    }

    private OffsetDateTime parseTimestamp(String raw, String fieldName) {
        String normalized = normalizeOptional(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(normalized).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid ISO-8601 timestamp");
        }
    }

    private String trim(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return value == null ? null : value.trim();
        }
        String normalized = value.trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit);
    }

    private String formatWhen(OffsetDateTime value) {
        return value == null ? "—" : value.truncatedTo(ChronoUnit.MINUTES).toString();
    }

    private String slug(String raw) {
        String value = normalizeOptional(raw);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        String normalized = normalizeOptional(rawJson);
        if (!StringUtils.hasText(normalized)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(normalized, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(Objects.toString(entry.getKey(), ""), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private String readString(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty() || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (map.containsKey(key) && StringUtils.hasText(Objects.toString(map.get(key), "").trim())) {
                return Objects.toString(map.get(key), "").trim();
            }
        }
        return "";
    }

    private Integer readInteger(Map<String, Object> map, String... keys) {
        String value = readString(map, keys);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = normalizeOptional(raw);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "on".equalsIgnoreCase(text);
    }

    private record DiscoveredCredential(String entryKey,
                                        String integrationKind,
                                        String credentialKind,
                                        String displayName,
                                        String sourceType,
                                        String sourceRef,
                                        boolean secretPresent) {
    }

    private record StatusAssessment(String statusCode,
                                    String statusLevel,
                                    String reason,
                                    OffsetDateTime nextRotationDueAt) {
    }

    private record StatusCandidate(String statusCode,
                                   String statusLevel,
                                   String reason,
                                   OffsetDateTime dueAt) {
    }

    private record AlertState(boolean critical, String fingerprint) {
    }

    public record RegistrySnapshot(OffsetDateTime generatedAt,
                                   RegistryOverview overview,
                                   List<CredentialRotationRegistryEntry> items) {
    }

    public record RegistryOverview(int total,
                                   int ok,
                                   int warning,
                                   int critical,
                                   int trackingMissing,
                                   int missingSecret,
                                   int sourceRemoved) {
    }

    public record MetadataPatch(String ownerName,
                                String note,
                                String expiresAt,
                                String rotatedAt,
                                Integer rotationIntervalDays) {
    }
}
