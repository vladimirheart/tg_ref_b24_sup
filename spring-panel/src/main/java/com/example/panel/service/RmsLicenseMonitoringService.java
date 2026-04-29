package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.panel.entity.RmsLicenseMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.RmsLicenseMonitorRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RmsLicenseMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(RmsLicenseMonitoringService.class);

    public static final String LICENSE_STATUS_OK = "ok";
    public static final String LICENSE_STATUS_WARNING = "warning";
    public static final String LICENSE_STATUS_CRITICAL = "critical";
    public static final String LICENSE_STATUS_EXPIRED = "expired";
    public static final String LICENSE_STATUS_ERROR = "error";
    public static final String LICENSE_STATUS_DISABLED = "disabled";
    public static final String RMS_STATUS_UP = "up";
    public static final String RMS_STATUS_DOWN = "down";
    public static final String RMS_STATUS_DISABLED = "disabled";
    public static final String RMS_STATUS_UNKNOWN = "unknown";
    private static final String RMS_TARGET_LICENSE_ID = "100";
    private static final String RMS_TARGET_LICENSE_TITLE = "RMS (Front Fast Food)";
    private static final String CHAIN_TARGET_LICENSE_ID = "1300";
    private static final String CHAIN_TARGET_LICENSE_TITLE = "Chain (Store Connector (1 RMS))";
    private static final String KIOSK_CONNECTOR_LICENSE_ID = "36073118";
    private static final String KIOSK_CONNECTOR_LICENSE_TITLE = "iikoConnector for Get Kiosk (iikoFront)";
    private static final Charset WINDOWS_OEM_CHARSET = Charset.forName("IBM866");
    private static final Charset WINDOWS_ANSI_CHARSET = Charset.forName("windows-1251");

    private static final int DEFAULT_RMS_PORT = 443;
    private static final int LICENSE_WARNING_DAYS = 7;
    private static final int LICENSE_CRITICAL_DAYS = 3;
    private static final long QUEUE_GAP_MS = 20_000L;
    private static final long HTTP_TIMEOUT_MS = 15_000L;
    private static final long COMMAND_TIMEOUT_MS = 90_000L;
    private static final String MONITORING_PAGE_URL = "/analytics/rms-control";

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[]{
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    };

    private final RmsLicenseMonitorRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService licenseRefreshExecutor;
    private final ExecutorService networkRefreshExecutor;
    private final AtomicBoolean licenseRefreshRunning = new AtomicBoolean(false);
    private final AtomicBoolean licenseRefreshPending = new AtomicBoolean(false);
    private final AtomicBoolean networkRefreshRunning = new AtomicBoolean(false);
    private final AtomicBoolean networkRefreshPending = new AtomicBoolean(false);
    private final AtomicReference<LicenseRefreshTask> pendingLicenseTask = new AtomicReference<>();
    private final AtomicReference<NetworkRefreshTask> pendingNetworkTask = new AtomicReference<>();
    private final AtomicReference<OffsetDateTime> lastLicenseRefreshRequestedAt = new AtomicReference<>();
    private final AtomicReference<OffsetDateTime> lastLicenseRefreshCompletedAt = new AtomicReference<>();
    private final AtomicReference<OffsetDateTime> lastNetworkRefreshRequestedAt = new AtomicReference<>();
    private final AtomicReference<OffsetDateTime> lastNetworkRefreshCompletedAt = new AtomicReference<>();
    private final QueueProgressTracker licenseQueueTracker = new QueueProgressTracker();
    private final QueueProgressTracker networkQueueTracker = new QueueProgressTracker();

    public RmsLicenseMonitoringService(RmsLicenseMonitorRepository repository,
                                       MonitoringCheckHistoryRepository historyRepository,
                                       NotificationService notificationService,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.httpClient = buildUnsafeHttpClient();
        this.licenseRefreshExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("rms-license-refresh"));
        this.networkRefreshExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("rms-network-refresh"));
    }

    @PreDestroy
    void shutdownExecutors() {
        licenseRefreshExecutor.shutdownNow();
        networkRefreshExecutor.shutdownNow();
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public List<RmsLicenseMonitor> findAll() {
        return repository.findAllByOrderByRmsAddressAscIdAsc();
    }

    public RmsLicenseMonitor createMonitor(String rmsAddress,
                                           String authLogin,
                                           String authPassword,
                                           Boolean enabled,
                                           Boolean licenseMonitoringEnabled,
                                           Boolean networkMonitoringEnabled) {
        EndpointTarget target = parseEndpointSafe(rmsAddress);
        if (repository.findByRmsAddress(target.normalizedAddress()).isPresent()) {
            throw new IllegalArgumentException("Этот RMS уже добавлен в мониторинг");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RmsLicenseMonitor monitor = new RmsLicenseMonitor();
        monitor.setRmsAddress(target.normalizedAddress());
        monitor.setScheme(target.scheme());
        monitor.setHost(target.host());
        monitor.setPort(target.port());
        monitor.setAuthLogin(requireLogin(authLogin));
        monitor.setAuthPassword(requirePassword(authPassword, true));
        monitor.setEnabled(enabled == null || enabled);
        monitor.setLicenseMonitoringEnabled(licenseMonitoringEnabled == null || licenseMonitoringEnabled);
        monitor.setNetworkMonitoringEnabled(networkMonitoringEnabled == null || networkMonitoringEnabled);
        monitor.setLicenseStatus(LICENSE_STATUS_DISABLED);
        monitor.setRmsStatus(RMS_STATUS_DISABLED);
        monitor.setCreatedAt(now);
        monitor.setUpdatedAt(now);
        monitor = repository.save(monitor);

        synchronizeFeatureStates(monitor, false);
        return repository.findById(monitor.getId()).orElse(monitor);
    }

    public RmsLicenseMonitor updateMonitor(long id,
                                           String rmsAddress,
                                           String authLogin,
                                           String authPassword,
                                           Boolean enabled,
                                           Boolean licenseMonitoringEnabled,
                                           Boolean networkMonitoringEnabled) {
        RmsLicenseMonitor monitor = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("RMS-запись не найдена"));

        EndpointTarget target = parseEndpointSafe(rmsAddress);
        repository.findByRmsAddress(target.normalizedAddress()).ifPresent(existing -> {
            if (!existing.getId().equals(monitor.getId())) {
                throw new IllegalArgumentException("Этот RMS уже добавлен в мониторинг");
            }
        });

        monitor.setRmsAddress(target.normalizedAddress());
        monitor.setScheme(target.scheme());
        monitor.setHost(target.host());
        monitor.setPort(target.port());
        monitor.setAuthLogin(requireLogin(authLogin));
        monitor.setAuthPassword(requirePassword(authPassword, false, monitor.getAuthPassword()));
        monitor.setEnabled(enabled == null || enabled);
        monitor.setLicenseMonitoringEnabled(licenseMonitoringEnabled == null || licenseMonitoringEnabled);
        monitor.setNetworkMonitoringEnabled(networkMonitoringEnabled == null || networkMonitoringEnabled);
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(monitor);

        synchronizeFeatureStates(monitor, false);
        return repository.findById(monitor.getId()).orElse(monitor);
    }

    public void deleteMonitor(long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("RMS-запись не найдена");
        }
        repository.deleteById(id);
    }

    public RefreshRequestResult requestLicenseRefresh(boolean withNotifications) {
        return queueLicenseRefresh(null, withNotifications);
    }

    public RefreshRequestResult requestLicenseRefreshForMonitor(long id, boolean withNotifications) {
        requireMonitor(id);
        return queueLicenseRefresh(id, withNotifications);
    }

    public RefreshRequestResult requestNetworkRefresh() {
        return queueNetworkRefresh(null);
    }

    public RefreshRequestResult requestNetworkRefreshForMonitor(long id) {
        requireMonitor(id);
        return queueNetworkRefresh(id);
    }

    public void setLicenseMonitoringEnabledForAll(boolean enabled) {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (RmsLicenseMonitor monitor : monitors) {
            monitor.setLicenseMonitoringEnabled(enabled);
            monitor.setUpdatedAt(now);
            if (!enabled || !Boolean.TRUE.equals(monitor.getEnabled())) {
                applyLicenseDisabledState(monitor);
            } else if (LICENSE_STATUS_DISABLED.equals(normalizeStatus(monitor.getLicenseStatus()))) {
                monitor.setLicenseStatus(LICENSE_STATUS_ERROR);
                monitor.setLicenseErrorMessage("Ожидает обновления лицензии");
                repository.save(monitor);
            } else {
                repository.save(monitor);
            }
        }
    }

    public void setNetworkMonitoringEnabledForAll(boolean enabled) {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (RmsLicenseMonitor monitor : monitors) {
            monitor.setNetworkMonitoringEnabled(enabled);
            monitor.setUpdatedAt(now);
            if (!enabled || !Boolean.TRUE.equals(monitor.getEnabled())) {
                applyNetworkDisabledState(monitor);
            } else if (RMS_STATUS_DISABLED.equals(normalizeStatus(monitor.getRmsStatus()))) {
                monitor.setRmsStatus(RMS_STATUS_UNKNOWN);
                monitor.setRmsStatusMessage("Ожидает проверки доступности");
                repository.save(monitor);
            } else {
                repository.save(monitor);
            }
        }
    }

    private RefreshRequestResult queueLicenseRefresh(Long monitorId, boolean withNotifications) {
        lastLicenseRefreshRequestedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        if (!licenseRefreshPending.compareAndSet(false, true)) {
            return new RefreshRequestResult("already_queued", currentRefreshState());
        }
        pendingLicenseTask.set(new LicenseRefreshTask(monitorId, withNotifications));
        licenseRefreshExecutor.submit(this::runQueuedLicenseRefresh);
        return new RefreshRequestResult("queued", currentRefreshState());
    }

    private RefreshRequestResult queueNetworkRefresh(Long monitorId) {
        lastNetworkRefreshRequestedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        if (!networkRefreshPending.compareAndSet(false, true)) {
            return new RefreshRequestResult("already_queued", currentRefreshState());
        }
        pendingNetworkTask.set(new NetworkRefreshTask(monitorId));
        networkRefreshExecutor.submit(this::runQueuedNetworkRefresh);
        return new RefreshRequestResult("queued", currentRefreshState());
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public String loadTracerouteReport(long id) {
        RmsLicenseMonitor monitor = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("RMS-запись не найдена"));
        if (!StringUtils.hasText(monitor.getTracerouteReport())) {
            throw new IllegalArgumentException("Для этой записи ещё нет сохранённой трассировки");
        }
        return monitor.getTracerouteReport();
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public LicenseDetailsView loadLicenseDetails(long id) {
        RmsLicenseMonitor monitor = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("RMS-запись не найдена"));
        if (!StringUtils.hasText(monitor.getLicenseDetailsJson())) {
            throw new IllegalArgumentException("Для этой записи ещё нет сохранённого состава лицензий");
        }
        try {
            List<LinkedHashMap<String, String>> storedItems = objectMapper.readValue(
                monitor.getLicenseDetailsJson(),
                new TypeReference<List<LinkedHashMap<String, String>>>() {
                }
            );
            List<Map<String, String>> items = new java.util.ArrayList<>(storedItems);
            return new LicenseDetailsView(
                monitor.getId(),
                monitor.getRmsAddress(),
                resolveDisplayServerNameForView(monitor),
                monitor.getLicenseLastCheckedAt(),
                items
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось прочитать сохранённый состав лицензий");
        }
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public DiagnosticsView loadDiagnostics(long id) {
        RmsLicenseMonitor monitor = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("RMS-запись не найдена"));
        return new DiagnosticsView(
            monitor.getId(),
            monitor.getRmsAddress(),
            resolveDisplayServerNameForView(monitor),
            monitor.getLicenseLastCheckedAt(),
            monitor.getRmsLastCheckedAt(),
            monitor.getLicenseStatus(),
            monitor.getLicenseErrorMessage(),
            monitor.getLicenseDebugExcerpt(),
            monitor.getRmsStatus(),
            monitor.getRmsStatusMessage(),
            monitor.getPingOutput(),
            monitor.getTracerouteSummary(),
            monitor.getTracerouteReport(),
            loadTimelineForMonitor(monitor.getId())
        );
    }

    public String resolveDisplayServerNameForView(RmsLicenseMonitor monitor) {
        if (monitor == null) {
            return "";
        }
        return resolveDisplayServerNameInternal(
            null,
            monitor.getServerName(),
            monitor.getHost(),
            monitor.getLicenseDetailsJson()
        );
    }

    public String resolveLicenseErrorMessageForView(RmsLicenseMonitor monitor) {
        if (monitor == null) {
            return "";
        }
        String message = monitor.getLicenseErrorMessage();
        if (!StringUtils.hasText(message)) {
            return "";
        }
        LicenseDescriptor currentDescriptor = resolvePrimaryLicenseDescriptor(monitor);
        if (message.contains(currentDescriptor.title()) || message.contains("id=" + currentDescriptor.id())) {
            return message;
        }
        if (message.contains(RMS_TARGET_LICENSE_TITLE)
            || message.contains("id=" + RMS_TARGET_LICENSE_ID)
            || message.contains("[" + RMS_TARGET_LICENSE_ID + "]")) {
            return "Данные лицензии устарели для текущего типа сервера. Запустите обновление лицензий.";
        }
        return message;
    }

    public RefreshState currentRefreshState() {
        return new RefreshState(
            new QueueState(
                licenseRefreshRunning.get(),
                licenseRefreshPending.get(),
                lastLicenseRefreshRequestedAt.get(),
                lastLicenseRefreshCompletedAt.get(),
                licenseQueueTracker.currentMonitorId(),
                licenseQueueTracker.totalCount(),
                licenseQueueTracker.completedCount()
            ),
            new QueueState(
                networkRefreshRunning.get(),
                networkRefreshPending.get(),
                lastNetworkRefreshRequestedAt.get(),
                lastNetworkRefreshCompletedAt.get(),
                networkQueueTracker.currentMonitorId(),
                networkQueueTracker.totalCount(),
                networkQueueTracker.completedCount()
            )
        );
    }

    public String resolveLicenseSeverity(RmsLicenseMonitor monitor) {
        if (monitor == null) {
            return LICENSE_STATUS_ERROR;
        }
        if (!Boolean.TRUE.equals(monitor.getEnabled()) || !Boolean.TRUE.equals(monitor.getLicenseMonitoringEnabled())) {
            return LICENSE_STATUS_DISABLED;
        }
        String status = normalizeStatus(monitor.getLicenseStatus());
        if (StringUtils.hasText(status)) {
            return status;
        }
        Integer daysLeft = monitor.getLicenseDaysLeft();
        if (daysLeft == null) {
            return LICENSE_STATUS_ERROR;
        }
        return deriveLicenseStatus(daysLeft);
    }

    public String resolveRmsAvailability(RmsLicenseMonitor monitor) {
        if (monitor == null) {
            return RMS_STATUS_UNKNOWN;
        }
        if (!Boolean.TRUE.equals(monitor.getEnabled()) || !Boolean.TRUE.equals(monitor.getNetworkMonitoringEnabled())) {
            return RMS_STATUS_DISABLED;
        }
        String status = normalizeStatus(monitor.getRmsStatus());
        return StringUtils.hasText(status) ? status : RMS_STATUS_UNKNOWN;
    }

    public String resolveLicenseRefreshState(RmsLicenseMonitor monitor) {
        return monitor == null || monitor.getId() == null ? "idle" : licenseQueueTracker.resolveState(monitor.getId());
    }

    public String resolveNetworkRefreshState(RmsLicenseMonitor monitor) {
        return monitor == null || monitor.getId() == null ? "idle" : networkQueueTracker.resolveState(monitor.getId());
    }

    public Integer resolveTargetLicenseQuantity(RmsLicenseMonitor monitor) {
        return resolveLicenseQuantity(monitor, resolvePrimaryLicenseDescriptor(monitor));
    }

    public Integer resolveKioskConnectorLicenseQuantity(RmsLicenseMonitor monitor) {
        if (monitor == null || !isIikoRmsServer(monitor)) {
            return null;
        }
        return resolveLicenseQuantity(monitor, kioskConnectorLicenseDescriptor());
    }

    public String resolveServerTypeKey(RmsLicenseMonitor monitor) {
        return normalizeServerTypeKey(monitor == null ? null : monitor.getServerType());
    }

    public String resolveServerTypeDisplay(RmsLicenseMonitor monitor) {
        String normalized = resolveServerTypeKey(monitor);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (monitor == null || !StringUtils.hasText(monitor.getServerType())) {
            return "";
        }
        return monitor.getServerType().trim();
    }

    public String resolveTargetLicenseLabel(RmsLicenseMonitor monitor) {
        return resolvePrimaryLicenseDescriptor(monitor).title();
    }

    public String resolveTargetLicenseId(RmsLicenseMonitor monitor) {
        return resolvePrimaryLicenseDescriptor(monitor).id();
    }

    private Integer resolveLicenseQuantity(RmsLicenseMonitor monitor, LicenseDescriptor descriptor) {
        if (monitor == null || !StringUtils.hasText(monitor.getLicenseDetailsJson())) {
            return null;
        }
        try {
            List<LinkedHashMap<String, String>> storedItems = objectMapper.readValue(
                monitor.getLicenseDetailsJson(),
                new TypeReference<List<LinkedHashMap<String, String>>>() {
                }
            );
            Map<String, String> targetLicense = findTargetLicense(new ArrayList<>(storedItems), descriptor);
            if (targetLicense == null) {
                return null;
            }
            String quantity = firstNonBlank(
                targetLicense.get("quantity"),
                targetLicense.get("connections_count"),
                targetLicense.get("amount"),
                targetLicense.get("count"),
                targetLicense.get("connections"),
                targetLicense.get("limit")
            );
            if (!StringUtils.hasText(quantity)) {
                return null;
            }
            return Integer.valueOf(quantity.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    public AvailabilityOverview buildAvailabilityOverview(List<RmsLicenseMonitor> monitors) {
        int total = 0;
        int up = 0;
        int down = 0;
        int unknown = 0;
        int disabled = 0;
        if (monitors != null) {
            for (RmsLicenseMonitor monitor : monitors) {
                total++;
                String availability = resolveRmsAvailability(monitor);
                if (RMS_STATUS_UP.equals(availability)) {
                    up++;
                } else if (RMS_STATUS_DOWN.equals(availability)) {
                    down++;
                } else if (RMS_STATUS_DISABLED.equals(availability)) {
                    disabled++;
                } else {
                    unknown++;
                }
            }
        }
        int active = Math.max(0, total - disabled);
        double availabilityPercent = active == 0 ? 0d : (up * 100.0d / active);
        return new AvailabilityOverview(total, up, down, unknown, disabled, Math.round(availabilityPercent * 10.0d) / 10.0d);
    }

    private List<TimelineEntryView> loadTimelineForMonitor(Long monitorId) {
        if (monitorId == null) {
            return List.of();
        }
        return historyRepository.findRecent("rms", monitorId, 20).stream()
            .map(entry -> new TimelineEntryView(
                entry.checkKind(),
                entry.status(),
                entry.summary(),
                entry.detailsExcerpt(),
                entry.httpStatus(),
                entry.durationMs(),
                entry.createdAt()
            ))
            .toList();
    }

    private void recordTimelineEntry(RmsLicenseMonitor monitor,
                                     String checkKind,
                                     String status,
                                     String summary,
                                     String detailsExcerpt,
                                     OffsetDateTime createdAt) {
        if (monitor == null || monitor.getId() == null) {
            return;
        }
        historyRepository.record(
            "rms",
            monitor.getId(),
            checkKind,
            trimText(status, 40, ""),
            trimText(summary, 600, ""),
            trimText(detailsExcerpt, 4_000, ""),
            null,
            null,
            createdAt != null ? createdAt : OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private String buildLicenseTimelineSummary(RmsLicenseMonitor monitor) {
        if (monitor == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        if (monitor.getLicenseExpiresAt() != null) {
            summary.append(resolvePrimaryLicenseDescriptor(monitor).title())
                .append(" до ")
                .append(monitor.getLicenseExpiresAt().toLocalDate());
        }
        if (monitor.getLicenseDaysLeft() != null) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append("осталось ").append(monitor.getLicenseDaysLeft()).append(" дн.");
        }
        if (summary.length() == 0 && StringUtils.hasText(monitor.getLicenseErrorMessage())) {
            summary.append(monitor.getLicenseErrorMessage());
        }
        return trimText(summary.toString(), 600, "");
    }

    private void runQueuedLicenseRefresh() {
        LicenseRefreshTask task = pendingLicenseTask.getAndSet(null);
        licenseRefreshPending.set(false);
        licenseRefreshRunning.set(true);
        try {
            if (task == null || task.monitorId() == null) {
                refreshAllLicensesInternal(task == null || task.withNotifications());
            } else {
                RmsLicenseMonitor monitor = requireMonitor(task.monitorId());
                licenseQueueTracker.start(List.of(monitor.getId()));
                licenseQueueTracker.markRunning(monitor.getId());
                refreshLicenseState(monitor, task.withNotifications());
                licenseQueueTracker.markCompleted(monitor.getId());
            }
            lastLicenseRefreshCompletedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (Exception ex) {
            log.warn("RMS license refresh queue failed", ex);
        } finally {
            licenseQueueTracker.finish();
            licenseRefreshRunning.set(false);
        }
    }

    private void runQueuedNetworkRefresh() {
        NetworkRefreshTask task = pendingNetworkTask.getAndSet(null);
        networkRefreshPending.set(false);
        networkRefreshRunning.set(true);
        try {
            if (task == null || task.monitorId() == null) {
                refreshAllNetworkStatesInternal();
            } else {
                RmsLicenseMonitor monitor = requireMonitor(task.monitorId());
                networkQueueTracker.start(List.of(monitor.getId()));
                networkQueueTracker.markRunning(monitor.getId());
                refreshNetworkState(monitor);
                networkQueueTracker.markCompleted(monitor.getId());
            }
            lastNetworkRefreshCompletedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (Exception ex) {
            log.warn("RMS network refresh queue failed", ex);
        } finally {
            networkQueueTracker.finish();
            networkRefreshRunning.set(false);
        }
    }

    private void refreshAllLicensesInternal(boolean withNotifications) {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        licenseQueueTracker.start(monitors.stream().map(RmsLicenseMonitor::getId).toList());
        for (int index = 0; index < monitors.size(); index++) {
            RmsLicenseMonitor monitor = monitors.get(index);
            licenseQueueTracker.markRunning(monitor.getId());
            refreshLicenseState(monitor, withNotifications);
            licenseQueueTracker.markCompleted(monitor.getId());
            sleepBetweenQueueItems(index, monitors.size());
        }
    }

    private void refreshAllNetworkStatesInternal() {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        networkQueueTracker.start(monitors.stream().map(RmsLicenseMonitor::getId).toList());
        for (int index = 0; index < monitors.size(); index++) {
            RmsLicenseMonitor monitor = monitors.get(index);
            networkQueueTracker.markRunning(monitor.getId());
            refreshNetworkState(monitor);
            networkQueueTracker.markCompleted(monitor.getId());
            sleepBetweenQueueItems(index, monitors.size());
        }
    }

    private void refreshLicenseState(RmsLicenseMonitor monitor, boolean withNotifications) {
        if (monitor == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        monitor.setLicenseLastCheckedAt(now);
        monitor.setUpdatedAt(now);

        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            applyMasterDisabledState(monitor);
            return;
        }
        if (!Boolean.TRUE.equals(monitor.getLicenseMonitoringEnabled())) {
            applyLicenseDisabledState(monitor);
            return;
        }

        try {
            ServerMetadata metadata = fetchServerMetadata(monitor);
            monitor.setServerType(metadata.serverType());
            monitor.setServerVersion(metadata.serverVersion());

            LicenseSnapshot license = fetchLicenseSnapshot(monitor, metadata.serverEdition(), metadata.serverVersion());
            monitor.setServerName(resolveDisplayServerNameInternal(metadata.serverName(), monitor.getServerName(), monitor.getHost(), license.detailsJson()));
            monitor.setLicenseExpiresAt(license.expiresAt());
            monitor.setLicenseDaysLeft(license.daysLeft());
            monitor.setLicenseDetailsJson(license.detailsJson());
            monitor.setLicenseDebugExcerpt(license.debugExcerpt());
            monitor.setLicenseStatus(deriveLicenseStatus(license.daysLeft()));
            monitor.setLicenseErrorMessage(null);

            if (withNotifications && shouldNotifyLicense(monitor, now)) {
                sendLicenseNotification(monitor);
                monitor.setLicenseLastNotifiedAt(now);
            }
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "license",
                monitor.getLicenseStatus(),
                buildLicenseTimelineSummary(monitor),
                monitor.getLicenseDebugExcerpt(),
                now
            );
        } catch (Exception ex) {
            String errorMessage = resolveExceptionMessage(ex, "Не удалось обновить лицензию RMS");
            if (ex instanceof LicenseSnapshotException snapshotException) {
                if (StringUtils.hasText(snapshotException.detailsJson())) {
                    monitor.setLicenseDetailsJson(snapshotException.detailsJson());
                }
                if (StringUtils.hasText(snapshotException.debugExcerpt())) {
                    monitor.setLicenseDebugExcerpt(snapshotException.debugExcerpt());
                }
            }
            monitor.setLicenseStatus(LICENSE_STATUS_ERROR);
            monitor.setLicenseErrorMessage(trimText(errorMessage, 600, "Не удалось обновить лицензию RMS"));
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "license",
                monitor.getLicenseStatus(),
                monitor.getLicenseErrorMessage(),
                monitor.getLicenseDebugExcerpt(),
                now
            );
            log.warn("RMS license refresh failed for {}: {}", monitor.getRmsAddress(), errorMessage);
        }
    }

    private void refreshNetworkState(RmsLicenseMonitor monitor) {
        if (monitor == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        monitor.setRmsLastCheckedAt(now);
        monitor.setUpdatedAt(now);

        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            applyMasterDisabledState(monitor);
            return;
        }
        if (!Boolean.TRUE.equals(monitor.getNetworkMonitoringEnabled())) {
            applyNetworkDisabledState(monitor);
            return;
        }

        try {
            CommandResult ping = runCommand(buildPingCommand(monitor.getHost()), COMMAND_TIMEOUT_MS);
            monitor.setPingOutput(trimText(ping.output(), 20_000, ""));

            if (ping.success()) {
                monitor.setRmsStatus(RMS_STATUS_UP);
                monitor.setRmsStatusMessage(buildPingSuccessMessage(monitor.getHost(), ping.output()));
                monitor.setTracerouteSummary(null);
                monitor.setTracerouteReport(null);
                monitor.setTracerouteCheckedAt(null);
            } else {
                CommandResult traceroute = runCommand(buildTracerouteCommand(monitor.getHost()), COMMAND_TIMEOUT_MS);
                String tracerouteReport = trimText(traceroute.output(), 40_000, "");
                String tracerouteSummary = summarizeTraceroute(tracerouteReport);

                monitor.setRmsStatus(RMS_STATUS_DOWN);
                monitor.setRmsStatusMessage(StringUtils.hasText(tracerouteSummary)
                    ? tracerouteSummary
                    : "Ping не прошёл, маршрут недоступен");
                monitor.setTracerouteSummary(tracerouteSummary);
                monitor.setTracerouteReport(tracerouteReport);
                monitor.setTracerouteCheckedAt(now);
            }
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "network",
                monitor.getRmsStatus(),
                monitor.getRmsStatusMessage(),
                StringUtils.hasText(monitor.getTracerouteReport()) ? monitor.getTracerouteReport() : monitor.getPingOutput(),
                now
            );
        } catch (Exception ex) {
            String errorMessage = resolveExceptionMessage(ex, "Не удалось проверить доступность RMS");
            monitor.setRmsStatus(RMS_STATUS_DOWN);
            monitor.setRmsStatusMessage(trimText(errorMessage, 600, "Не удалось проверить доступность RMS"));
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "network",
                monitor.getRmsStatus(),
                monitor.getRmsStatusMessage(),
                monitor.getPingOutput(),
                now
            );
            log.warn("RMS network refresh failed for {}: {}", monitor.getRmsAddress(), errorMessage);
        }
    }

    private void applyMasterDisabledState(RmsLicenseMonitor monitor) {
        monitor.setLicenseStatus(LICENSE_STATUS_DISABLED);
        monitor.setLicenseErrorMessage("Запись мониторинга отключена");
        monitor.setRmsStatus(RMS_STATUS_DISABLED);
        monitor.setRmsStatusMessage("Запись мониторинга отключена");
        repository.save(monitor);
    }

    private void applyLicenseDisabledState(RmsLicenseMonitor monitor) {
        monitor.setLicenseStatus(LICENSE_STATUS_DISABLED);
        monitor.setLicenseErrorMessage("Мониторинг лицензии отключён");
        repository.save(monitor);
    }

    private void applyNetworkDisabledState(RmsLicenseMonitor monitor) {
        monitor.setRmsStatus(RMS_STATUS_DISABLED);
        monitor.setRmsStatusMessage("Мониторинг доступности отключён");
        repository.save(monitor);
    }

    private void synchronizeFeatureStates(RmsLicenseMonitor monitor, boolean withNotifications) {
        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            applyMasterDisabledState(monitor);
            return;
        }
        if (Boolean.TRUE.equals(monitor.getLicenseMonitoringEnabled())) {
            refreshLicenseState(monitor, withNotifications);
        } else {
            applyLicenseDisabledState(monitor);
        }
        if (Boolean.TRUE.equals(monitor.getNetworkMonitoringEnabled())) {
            refreshNetworkState(monitor);
        } else {
            applyNetworkDisabledState(monitor);
        }
    }

    private boolean shouldNotifyLicense(RmsLicenseMonitor monitor, OffsetDateTime now) {
        Integer daysLeft = monitor.getLicenseDaysLeft();
        if (daysLeft == null || daysLeft > LICENSE_WARNING_DAYS) {
            return false;
        }
        OffsetDateTime lastNotifiedAt = monitor.getLicenseLastNotifiedAt();
        return lastNotifiedAt == null || lastNotifiedAt.isBefore(now.minusHours(23));
    }

    private void sendLicenseNotification(RmsLicenseMonitor monitor) {
        String serverName = StringUtils.hasText(monitor.getServerName()) ? monitor.getServerName().trim() : monitor.getHost();
        String expiresAt = monitor.getLicenseExpiresAt() != null
            ? monitor.getLicenseExpiresAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"))
            : "не определена";
        int daysLeft = monitor.getLicenseDaysLeft() != null ? monitor.getLicenseDaysLeft() : LICENSE_WARNING_DAYS;

        String message = daysLeft < 0
            ? String.format("Лицензия RMS \"%s\" (%s) уже истекла. Дата окончания: %s.", serverName, monitor.getRmsAddress(), expiresAt)
            : String.format("Лицензия RMS \"%s\" (%s) истекает через %d дн. Дата окончания: %s.", serverName, monitor.getRmsAddress(), daysLeft, expiresAt);
        notificationService.notifyAllOperators(message, MONITORING_PAGE_URL, null);
    }

    private ServerMetadata fetchServerMetadata(RmsLicenseMonitor monitor) throws Exception {
        String baseUrl = buildBaseUrl(monitor);
        String serverInfoResponse = getText(baseUrl + "/resto/get_server_info.jsp?encoding=UTF-8");
        Document serverInfoXml = parseXml(serverInfoResponse);
        String serverName = sanitizeServerName(extractFirstText(serverInfoXml, "serverName"), monitor.getServerName(), monitor.getHost());
        String serverVersion = extractFirstText(serverInfoXml, "version");
        String edition = normalizeEdition(extractFirstText(serverInfoXml, "edition"));

        String loginHtml = getText(baseUrl + "/resto/login.jsp");
        String brand = loginHtml.toLowerCase(Locale.ROOT).contains("syrve") ? "syrve" : "iiko";

        return new ServerMetadata(
            trimText(serverName, 255, monitor.getHost()),
            trimText(composeServerType(brand, edition), 128, "unknown"),
            trimText(serverVersion, 128, ""),
            edition
        );
    }

    private LicenseSnapshot fetchLicenseSnapshot(RmsLicenseMonitor monitor,
                                                 String serverEdition,
                                                 String serverVersion) throws Exception {
        String baseUrl = buildBaseUrl(monitor);
        String safeVersion = StringUtils.hasText(serverVersion) ? serverVersion.trim() : "8.0";
        String safeEdition = StringUtils.hasText(serverEdition) ? serverEdition.trim() : "default";
        String body = """
            <args>
                <entities-version>1</entities-version>
                <client-type>BACK</client-type>
                <enable-warnings>false</enable-warnings>
                <client-call-id>%s</client-call-id>
                <license-hash>-1938788177</license-hash>
                <restrictions-state-hash>5761</restrictions-state-hash>
                <obtained-license-connections-ids />
                <request-watchdog-check-results>true</request-watchdog-check-results>
                <use-raw-entities>true</use-raw-entities>
            </args>
            """.formatted(UUID.randomUUID());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/xml");
        headers.put("X-Resto-LoginName", monitor.getAuthLogin());
        headers.put("X-Resto-PasswordHash", sha1Hex(monitor.getAuthPassword()));
        headers.put("X-Resto-BackVersion", safeVersion);
        headers.put("X-Resto-AuthType", "BACK");
        headers.put("X-Resto-ServerEdition", safeEdition);

        String response = postXml(
            baseUrl + "/resto/services/licensing?methodName=getForceDeveloperSandboxModeInfo&",
            body,
            headers
        );
        Document xml = parseXml(response);
        List<Map<String, String>> details = parseLicenseDetails(xml);
        String diagnosticExcerpt = buildLicenseDiagnosticExcerpt(response, xml);
        LicenseDescriptor targetDescriptor = resolvePrimaryLicenseDescriptor(monitor);
        Map<String, String> targetLicense = findTargetLicense(details, targetDescriptor);
        if (targetLicense == null) {
            throw new LicenseSnapshotException(
                "Не найдена лицензия " + targetDescriptor.title() + " с id=" + targetDescriptor.id(),
                writeLicenseDetails(details),
                diagnosticExcerpt
            );
        }
        OffsetDateTime targetExpiration = resolveLicenseExpiration(targetLicense);
        if (targetExpiration == null) {
            throw new LicenseSnapshotException(
                "Не удалось определить дату окончания лицензии " + targetDescriptor.title() + " [id=" + targetDescriptor.id() + "]",
                writeLicenseDetails(details),
                diagnosticExcerpt
            );
        }
        int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), targetExpiration.toLocalDate());
        return new LicenseSnapshot(targetExpiration, daysLeft, writeLicenseDetails(details), diagnosticExcerpt);
    }

    private String buildPingSuccessMessage(String host, String pingOutput) {
        String resolvedIp = extractPingResolvedAddress(pingOutput);
        if (StringUtils.hasText(resolvedIp)) {
            return "Ping успешен, IP: " + resolvedIp;
        }
        return "Ping успешен" + (StringUtils.hasText(host) ? ", host: " + host : "");
    }

    private String extractPingResolvedAddress(String pingOutput) {
        if (!StringUtils.hasText(pingOutput)) {
            return null;
        }
        for (String rawLine : pingOutput.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int openBracket = line.indexOf('[');
            int closeBracket = line.indexOf(']');
            if (openBracket >= 0 && closeBracket > openBracket) {
                String candidate = line.substring(openBracket + 1, closeBracket).trim();
                if (looksLikeIpAddress(candidate)) {
                    return candidate;
                }
            }
            String[] tokens = line.split("\\s+");
            for (String token : tokens) {
                String candidate = token.replace("(", "").replace(")", "").replace(",", "").trim();
                if (looksLikeIpAddress(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean looksLikeIpAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String candidate = value.trim();
        return candidate.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")
            || candidate.matches("^[0-9a-fA-F:]+$");
    }

    private String buildBaseUrl(RmsLicenseMonitor monitor) {
        String scheme = StringUtils.hasText(monitor.getScheme()) ? monitor.getScheme().trim() : "https";
        int port = monitor.getPort() != null && monitor.getPort() > 0 ? monitor.getPort() : DEFAULT_RMS_PORT;
        String host = monitor.getHost();
        if (host != null && host.contains(":") && !host.startsWith("[")) {
            return scheme + "://[" + host + "]:" + port;
        }
        return scheme + "://" + host + ":" + port;
    }

    private HttpClient buildUnsafeHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                .sslContext(context)
                .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        } catch (Exception ex) {
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        }
    }

    private String getText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " для " + url);
        }
        return decodeHttpResponse(response);
    }

    private String postXml(String url, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofMillis(HTTP_TIMEOUT_MS));
        headers.forEach(builder::header);
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " для запроса лицензии");
        }
        return decodeHttpResponse(response);
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String extractFirstText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : "";
    }

    private String extractChildText(Node node, String tagName) {
        if (node == null || !node.hasChildNodes()) {
            return "";
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child != null && child.getNodeType() == Node.ELEMENT_NODE && tagName.equalsIgnoreCase(child.getNodeName())) {
                return child.getTextContent();
            }
        }
        return "";
    }

    private String normalizeEdition(String rawEdition) {
        String edition = StringUtils.hasText(rawEdition) ? rawEdition.trim().toLowerCase(Locale.ROOT) : "default";
        if ("default".equals(edition)) {
            return "IIKO_RMS";
        }
        if ("chain".equals(edition)) {
            return "IIKO_CHAIN";
        }
        return edition.toUpperCase(Locale.ROOT);
    }

    private String composeServerType(String brand, String edition) {
        String safeBrand = StringUtils.hasText(brand) ? brand.trim().toLowerCase(Locale.ROOT) : "unknown";
        String safeEdition = StringUtils.hasText(edition) ? edition.trim() : "UNKNOWN";
        return safeBrand + " / " + safeEdition;
    }

    private String deriveLicenseStatus(int daysLeft) {
        if (daysLeft < 0) {
            return LICENSE_STATUS_EXPIRED;
        }
        if (daysLeft <= LICENSE_CRITICAL_DAYS) {
            return LICENSE_STATUS_CRITICAL;
        }
        if (daysLeft <= LICENSE_WARNING_DAYS) {
            return LICENSE_STATUS_WARNING;
        }
        return LICENSE_STATUS_OK;
    }

    private CommandResult runCommand(List<String> command, long timeoutMs) throws Exception {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Команда превысила лимит ожидания");
        }
        String output = decodeProcessOutput(process.getInputStream().readAllBytes());
        return new CommandResult(process.exitValue() == 0, output);
    }

    private List<String> buildPingCommand(String host) {
        if (isWindows()) {
            return List.of("ping", "-n", "4", "-w", "2000", host);
        }
        return List.of("ping", "-c", "4", "-W", "2", host);
    }

    private List<String> buildTracerouteCommand(String host) {
        if (isWindows()) {
            return List.of("tracert", "-d", "-w", "2000", host);
        }
        return List.of("traceroute", "-n", "-w", "2", host);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String summarizeTraceroute(String report) {
        if (!StringUtils.hasText(report)) {
            return "Ping не прошёл, трассировка не вернула данные";
        }
        String[] lines = report.split("\\R");
        String lastHopLine = null;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.matches("^\\d+\\s+.*")) {
                continue;
            }
            if (line.contains("* * *") || line.endsWith("*") || line.contains("Превышен интервал ожидания")) {
                if (lastHopLine != null) {
                    return "Маршрут обрывается после " + lastHopLine;
                }
                return "Нет ответов по трассировке";
            }
            lastHopLine = line;
        }
        return lastHopLine != null ? "Последний отвечающий хоп: " + lastHopLine : "Трассировка завершена без хопов";
    }

    private OffsetDateTime parseFlexibleDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("^\\d+$")) {
            long epoch = Long.parseLong(value);
            if (value.length() <= 10) {
                return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
            }
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return OffsetDateTime.parse(value, formatter);
                }
                LocalDateTime parsed = LocalDateTime.parse(value, formatter);
                return parsed.atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private EndpointTarget parseEndpointSafe(String rawEndpoint) {
        if (!StringUtils.hasText(rawEndpoint)) {
            throw new IllegalArgumentException("Укажите адрес RMS");
        }
        String candidate = rawEndpoint.trim();
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            candidate = "https://" + candidate;
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный адрес RMS");
        }

        String scheme = Optional.ofNullable(uri.getScheme()).orElse("https").trim().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Поддерживаются только адреса http/https");
        }

        HostPort hostPort = extractHostPort(uri);
        if (!StringUtils.hasText(hostPort.host())) {
            throw new IllegalArgumentException("Не удалось определить host RMS");
        }
        String host;
        try {
            host = IDN.toASCII(hostPort.host().trim(), IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Некорректный host RMS");
        }
        int port = hostPort.port() != null ? hostPort.port() : ("http".equals(scheme) ? 80 : DEFAULT_RMS_PORT);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Порт RMS должен быть в диапазоне 1-65535");
        }
        String normalizedAddress = scheme + "://" + host + (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80) ? "" : ":" + port);
        return new EndpointTarget(normalizedAddress, scheme, host, port);
    }

    private HostPort extractHostPort(URI uri) {
        String host = uri.getHost();
        Integer port = uri.getPort() > 0 ? uri.getPort() : null;
        if (StringUtils.hasText(host)) {
            return new HostPort(host, port);
        }

        String authority = uri.getRawAuthority();
        if (!StringUtils.hasText(authority)) {
            return new HostPort(null, port);
        }
        String value = authority.trim();
        int at = value.lastIndexOf('@');
        if (at >= 0 && at < value.length() - 1) {
            value = value.substring(at + 1);
        }
        value = URLDecoder.decode(value, StandardCharsets.UTF_8);

        if (value.startsWith("[")) {
            int endBracket = value.indexOf(']');
            if (endBracket > 1) {
                String ipv6Host = value.substring(1, endBracket);
                Integer ipv6Port = null;
                if (endBracket + 1 < value.length() && value.charAt(endBracket + 1) == ':') {
                    ipv6Port = parsePort(value.substring(endBracket + 2));
                }
                return new HostPort(ipv6Host, ipv6Port);
            }
        }

        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            return new HostPort(value.substring(0, firstColon), parsePort(value.substring(firstColon + 1)));
        }
        return new HostPort(value, null);
    }

    private Integer parsePort(String rawPort) {
        if (!StringUtils.hasText(rawPort)) {
            return null;
        }
        try {
            return Integer.parseInt(rawPort.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректный порт RMS");
        }
    }

    private String requireLogin(String authLogin) {
        if (!StringUtils.hasText(authLogin)) {
            throw new IllegalArgumentException("Укажите логин RMS");
        }
        return authLogin.trim();
    }

    private String requirePassword(String authPassword, boolean createMode, String... fallback) {
        if (StringUtils.hasText(authPassword)) {
            return authPassword.trim();
        }
        if (!createMode && fallback != null) {
            for (String value : fallback) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        throw new IllegalArgumentException("Укажите пароль RMS");
    }

    private String sha1Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private void sleepBetweenQueueItems(int index, int total) {
        if (index >= total - 1) {
            return;
        }
        try {
            Thread.sleep(QUEUE_GAP_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private RmsLicenseMonitor requireMonitor(long id) {
        return repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("RMS-запись не найдена"));
    }

    private String trimText(String value, int limit, String fallback) {
        String safe = StringUtils.hasText(value) ? value.trim() : fallback;
        if (!StringUtils.hasText(safe)) {
            return fallback;
        }
        if (safe.length() <= limit) {
            return safe;
        }
        return safe.substring(0, limit);
    }

    private String decodeHttpResponse(HttpResponse<byte[]> response) {
        byte[] body = response.body() != null ? response.body() : new byte[0];
        Charset declaredCharset = detectHttpCharset(response.headers(), body);
        return decodeBytes(body, declaredCharset);
    }

    private Charset detectHttpCharset(HttpHeaders headers, byte[] body) {
        String contentType = headers != null ? headers.firstValue("Content-Type").orElse("") : "";
        if (StringUtils.hasText(contentType)) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            int charsetIndex = lower.indexOf("charset=");
            if (charsetIndex >= 0) {
                String value = contentType.substring(charsetIndex + "charset=".length()).trim();
                int semicolonIndex = value.indexOf(';');
                if (semicolonIndex >= 0) {
                    value = value.substring(0, semicolonIndex).trim();
                }
                value = value.replace("\"", "");
                try {
                    return Charset.forName(value);
                } catch (Exception ignored) {
                }
            }
        }
        String asciiPrefix = new String(body, 0, Math.min(body.length, 256), StandardCharsets.US_ASCII);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("encoding\\s*=\\s*['\"]([^'\"]+)['\"]", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(asciiPrefix);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1).trim());
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String decodeProcessOutput(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (!isWindows()) {
            return decodeBytes(bytes, StandardCharsets.UTF_8);
        }
        return decodeBytes(bytes, WINDOWS_OEM_CHARSET, WINDOWS_ANSI_CHARSET, StandardCharsets.UTF_8);
    }

    private String decodeBytes(byte[] bytes, Charset... preferredCharsets) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        java.util.ArrayList<Charset> candidates = new java.util.ArrayList<>();
        if (preferredCharsets != null) {
            for (Charset charset : preferredCharsets) {
                if (charset != null && !candidates.contains(charset)) {
                    candidates.add(charset);
                }
            }
        }
        for (Charset charset : List.of(StandardCharsets.UTF_8, WINDOWS_OEM_CHARSET, WINDOWS_ANSI_CHARSET)) {
            if (!candidates.contains(charset)) {
                candidates.add(charset);
            }
        }
        String best = new String(bytes, candidates.get(0));
        int bestScore = scoreDecodedText(best);
        for (int index = 1; index < candidates.size(); index++) {
            String candidate = decodeStrict(bytes, candidates.get(index));
            int score = scoreDecodedText(candidate);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return normalizeLineEndings(best);
    }

    private String decodeMaybeMojibake(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = normalizeLineEndings(value);
        byte[] bytes = new byte[normalized.length()];
        for (int index = 0; index < normalized.length(); index++) {
            bytes[index] = (byte) normalized.charAt(index);
        }
        String repaired = decodeBytes(bytes, StandardCharsets.UTF_8, WINDOWS_ANSI_CHARSET, WINDOWS_OEM_CHARSET);
        return scoreDecodedText(repaired) < scoreDecodedText(normalized) ? repaired : normalized;
    }

    private String decodeStrict(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, charset);
        }
    }

    private int scoreDecodedText(String value) {
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        int score = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\uFFFD') {
                score += 100;
                continue;
            }
            if (Character.isISOControl(ch) && ch != '\r' && ch != '\n' && ch != '\t') {
                score += 5;
                continue;
            }
            if (isBoxDrawingChar(ch)) {
                score += 3;
                continue;
            }
            if (isSuspiciousMojibakeChar(ch)) {
                score += 3;
            }
        }
        if (containsCyrillic(value)) {
            score -= 8;
        }
        if (value.contains("<") && value.contains(">")) {
            score -= 3;
        }
        return score;
    }

    private void addCandidate(List<String> candidates, String value) {
        if (StringUtils.hasText(value) && !candidates.contains(value)) {
            candidates.add(value);
        }
    }

    private String recodeText(String value, Charset sourceCharset, Charset targetCharset) {
        if (!StringUtils.hasText(value) || sourceCharset == null || targetCharset == null) {
            return "";
        }
        byte[] bytes = value.getBytes(sourceCharset);
        return normalizeLineEndings(new String(bytes, targetCharset));
    }

    private boolean containsCyrillic(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if ((ch >= '\u0410' && ch <= '\u044F') || ch == '\u0401' || ch == '\u0451') {
                return true;
            }
        }
        return false;
    }

    private boolean isBoxDrawingChar(char ch) {
        return ch >= '\u2500' && ch <= '\u257F';
    }

    private boolean isSuspiciousMojibakeChar(char ch) {
        return ch == '\u00D0'
            || ch == '\u00D1'
            || ch == '\u00E2'
            || ch == '\u00C2'
            || ch == '\u00C3';
    }
    private int countOccurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while (true) {
            int index = value.indexOf(token, offset);
            if (index < 0) {
                return count;
            }
            count++;
            offset = index + token.length();
        }
    }

    private String sanitizeServerName(String rawServerName, String currentServerName, String fallbackHost) {
        String decoded = trimText(repairDisplayedText(rawServerName), 255, "");
        if (StringUtils.hasText(decoded) && !looksLikeBrokenText(decoded)) {
            return decoded;
        }
        String current = trimText(repairDisplayedText(currentServerName), 255, "");
        if (StringUtils.hasText(current) && !looksLikeBrokenText(current)) {
            return current;
        }
        if (StringUtils.hasText(decoded) && !looksLikeBrokenText(decoded)) {
            return decoded;
        }
        return trimText(fallbackHost, 255, fallbackHost);
    }

    private String resolveDisplayServerNameInternal(String metadataServerName,
                                                    String currentServerName,
                                                    String fallbackHost,
                                                    String detailsJson) {
        String sanitizedMetadata = sanitizeServerName(metadataServerName, currentServerName, fallbackHost);
        if (StringUtils.hasText(sanitizedMetadata) && !looksLikeBrokenText(sanitizedMetadata) && !sanitizedMetadata.equals(fallbackHost)) {
            return sanitizedMetadata;
        }
        String companyName = extractCompanyNameFromLicenseDetails(detailsJson);
        if (StringUtils.hasText(companyName) && !looksLikeBrokenText(companyName)) {
            return trimText(companyName, 255, fallbackHost);
        }
        if (StringUtils.hasText(sanitizedMetadata) && !looksLikeBrokenText(sanitizedMetadata)) {
            return sanitizedMetadata;
        }
        return trimText(fallbackHost, 255, fallbackHost);
    }

    private String extractCompanyNameFromLicenseDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return "";
        }
        try {
            List<LinkedHashMap<String, String>> items = objectMapper.readValue(
                detailsJson,
                new TypeReference<List<LinkedHashMap<String, String>>>() {
                }
            );
            for (Map<String, String> item : items) {
                String companyName = repairDisplayedText(firstNonBlank(
                    item.get("company_name"),
                    item.get("company"),
                    item.get("organization_name")
                ));
                if (StringUtils.hasText(companyName) && !looksLikeBrokenText(companyName)) {
                    return companyName;
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to extract company_name from RMS license details", ex);
        }
        return "";
    }

    private String repairDisplayedText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = normalizeLineEndings(HtmlUtils.htmlUnescape(value));
        List<String> candidates = new java.util.ArrayList<>();
        candidates.add(normalized);
        addCandidate(candidates, recodeText(normalized, WINDOWS_OEM_CHARSET, StandardCharsets.UTF_8));
        addCandidate(candidates, recodeText(normalized, WINDOWS_ANSI_CHARSET, StandardCharsets.UTF_8));
        addCandidate(candidates, recodeText(normalized, StandardCharsets.UTF_8, WINDOWS_ANSI_CHARSET));
        addCandidate(candidates, recodeText(normalized, StandardCharsets.UTF_8, WINDOWS_OEM_CHARSET));
        addCandidate(candidates, decodeMaybeMojibake(normalized));

        String best = normalized;
        int bestScore = scoreDecodedText(normalized);
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            int score = scoreDecodedText(candidate);
            if (looksLikeBrokenText(best) && !looksLikeBrokenText(candidate)) {
                best = candidate;
                bestScore = score;
                continue;
            }
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean looksLikeBrokenText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (containsCyrillic(trimmed)) {
            return false;
        }
        return trimmed.contains("в•Ё")
            || trimmed.contains("в•¤")
            || isLikelyBoxDrawingMojibake(trimmed)
            || scoreDecodedText(trimmed) >= 12;
    }

    private boolean isLikelyBoxDrawingMojibake(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (isBoxDrawingChar(ch) || isSuspiciousMojibakeChar(ch)) {
                return true;
            }
        }
        return false;
    }
    private String formatDiagnosticText(String value, int limit) {
        String normalized = normalizeLineEndings(HtmlUtils.htmlUnescape(value));
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String formatted = repairDisplayedText(normalized).trim();
        if (looksLikeXml(formatted)) {
            formatted = prettyPrintXml(formatted);
        }
        return trimText(formatted, limit, "");
    }

    private boolean looksLikeXml(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith("<") && value.contains(">");
    }

    private String prettyPrintXml(String xml) {
        try {
            Document document = parseXml(xml);
            javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(writer));
            return normalizeLineEndings(writer.toString());
        } catch (Exception ignored) {
            return normalizeLineEndings(xml);
        }
    }

    private String normalizeLineEndings(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String normalizeSearchToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        return normalized.replaceAll("[^a-z0-9а-я]+", " ").trim().replaceAll("\\s+", " ");
    }


    private String resolveExceptionMessage(Throwable throwable, String fallbackMessage) {
        if (throwable == null) {
            return fallbackMessage;
        }
        if (StringUtils.hasText(throwable.getMessage())) {
            return throwable.getMessage().trim();
        }
        String simpleName = throwable.getClass().getSimpleName();
        if (StringUtils.hasText(simpleName)) {
            return fallbackMessage + " (" + simpleName + ")";
        }
        return fallbackMessage;
    }
    private List<Map<String, String>> parseLicenseDetails(Document xml) {
        List<Map<String, String>> items = new java.util.ArrayList<>();
        NodeList licenses = xml.getElementsByTagName("license");
        for (int index = 0; index < licenses.getLength(); index++) {
            Node node = licenses.item(index);
            if (node == null) {
                continue;
            }
            LinkedHashMap<String, String> item = new LinkedHashMap<>();
            appendNodeAttributes(item, node);
            NodeList children = node.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node child = children.item(childIndex);
                if (child == null || child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                collectLicenseNode(item, child, normalizeLicenseKey(child.getNodeName()));
            }
            enrichLicenseDetails(item, index);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        if (!items.isEmpty()) {
            return items;
        }
        return parseEncodedLicenseData(xml);
    }

    private List<Map<String, String>> parseEncodedLicenseData(Document xml) {
        String encodedLicenseData = extractFirstText(xml, "licenseData");
        if (!StringUtils.hasText(encodedLicenseData)) {
            return List.of();
        }
        String rawLicenseData = HtmlUtils.htmlUnescape(encodedLicenseData).trim();
        if (!looksLikeXml(rawLicenseData)) {
            return List.of();
        }
        try {
            Document innerXml = parseXml(rawLicenseData);
            return parseRestrictionsByModule(innerXml);
        } catch (Exception ex) {
            log.debug("Failed to parse encoded RMS licenseData payload", ex);
            return List.of();
        }
    }

    private List<Map<String, String>> parseRestrictionsByModule(Document licenseDataXml) {
        Node restrictionsNode = firstElement(licenseDataXml.getElementsByTagName("restrictionsByModule"));
        if (restrictionsNode == null) {
            return List.of();
        }
        String licenseSerial = extractFirstText(licenseDataXml, "serialNumber");
        String companyName = repairDisplayedText(extractFirstText(licenseDataXml, "companyName"));
        String generatedAt = extractFirstText(licenseDataXml, "generated");
        String overallExpiration = extractFirstText(licenseDataXml, "expiration");
        Map<String, Node> moduleNodes = parseModuleValueNodes(restrictionsNode);
        List<Map<String, String>> items = new java.util.ArrayList<>();
        for (Map.Entry<String, Node> entry : moduleNodes.entrySet()) {
            LinkedHashMap<String, String> item = buildLicenseItemFromModule(entry.getKey(), entry.getValue(), licenseSerial, companyName, generatedAt, overallExpiration);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private Map<String, Node> parseModuleValueNodes(Node restrictionsNode) {
        Map<String, Node> modules = new TreeMap<>();
        String pendingKey = null;
        NodeList children = restrictionsNode.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child == null || child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if ("k".equalsIgnoreCase(child.getNodeName())) {
                pendingKey = trimText(child.getTextContent(), 100, "");
                continue;
            }
            if ("v".equalsIgnoreCase(child.getNodeName()) && StringUtils.hasText(pendingKey)) {
                modules.put(pendingKey, child);
                pendingKey = null;
            }
        }
        return modules;
    }

    private LinkedHashMap<String, String> buildLicenseItemFromModule(String moduleId,
                                                                     Node moduleValueNode,
                                                                     String licenseSerial,
                                                                     String companyName,
                                                                     String generatedAt,
                                                                     String overallExpiration) {
        LinkedHashMap<String, String> item = new LinkedHashMap<>();
        if (!StringUtils.hasText(moduleId) || moduleValueNode == null) {
            return item;
        }
        item.put("id", moduleId);
        item.put("module_id", moduleId);
        item.put("title", resolveModuleTitle(moduleId));
        item.put("license_name", resolveModuleTitle(moduleId));
        if (StringUtils.hasText(licenseSerial)) {
            item.put("serial_number", licenseSerial);
        }
        if (StringUtils.hasText(companyName)) {
            item.put("company_name", companyName);
        }
        if (StringUtils.hasText(generatedAt)) {
            item.put("generated", generatedAt);
        }
        if (StringUtils.hasText(overallExpiration)) {
            item.put("license_expiration", overallExpiration);
        }
        appendNodeAttributes(item, moduleValueNode);
        String thresholdId = extractFirstDescendantText(moduleValueNode, "id");
        if (StringUtils.hasText(thresholdId)) {
            item.putIfAbsent("restriction_id", thresholdId);
        }
        String thresholdClass = extractThresholdClass(moduleValueNode);
        if (StringUtils.hasText(thresholdClass)) {
            item.put("threshold_class", thresholdClass);
        }
        String moduleExpiration = extractModuleExpiration(moduleValueNode);
        if (StringUtils.hasText(moduleExpiration)) {
            item.put("expiration", moduleExpiration);
            item.put("module_expiration", moduleExpiration);
        }
        String moduleFrom = extractFirstDescendantText(moduleValueNode, "from");
        if (StringUtils.hasText(moduleFrom)) {
            item.put("from", moduleFrom);
        }
        String connectionsCount = extractFirstDescendantText(moduleValueNode, "connectionsCount");
        if (StringUtils.hasText(connectionsCount)) {
            item.put("quantity", connectionsCount);
            item.put("connections_count", connectionsCount);
        }
        item.putIfAbsent("state", isModuleActive(moduleExpiration) ? "active" : "unknown");
        return item;
    }

    private String resolveModuleTitle(String moduleId) {
        if (RMS_TARGET_LICENSE_ID.equals(moduleId)) {
            return RMS_TARGET_LICENSE_TITLE;
        }
        if (CHAIN_TARGET_LICENSE_ID.equals(moduleId)) {
            return CHAIN_TARGET_LICENSE_TITLE;
        }
        if (KIOSK_CONNECTOR_LICENSE_ID.equals(moduleId)) {
            return KIOSK_CONNECTOR_LICENSE_TITLE;
        }
        return "Модуль " + moduleId;
    }

    private String extractThresholdClass(Node moduleValueNode) {
        NodeList thresholds = ((org.w3c.dom.Element) moduleValueNode).getElementsByTagName("threshold");
        Node thresholdNode = firstElement(thresholds);
        if (thresholdNode == null) {
            return "";
        }
        Node classAttribute = thresholdNode.getAttributes() != null ? thresholdNode.getAttributes().getNamedItem("cls") : null;
        return classAttribute != null ? trimText(classAttribute.getNodeValue(), 255, "") : "";
    }

    private String extractModuleExpiration(Node moduleValueNode) {
        for (String tagName : List.of("to", "expiration", "validTo", "valid_to", "threshold")) {
            String value = extractFirstDescendantText(moduleValueNode, tagName);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (parseFlexibleDate(value) != null) {
                return value.trim();
            }
        }
        return "";
    }

    private String extractFirstDescendantText(Node parent, String tagName) {
        if (parent == null || !StringUtils.hasText(tagName)) {
            return "";
        }
        if (parent instanceof org.w3c.dom.Element element) {
            NodeList nodes = element.getElementsByTagName(tagName);
            Node first = firstElement(nodes);
            if (first != null) {
                return trimText(first.getTextContent(), 500, "");
            }
        }
        return "";
    }

    private boolean isModuleActive(String moduleExpiration) {
        OffsetDateTime expiresAt = parseFlexibleDate(moduleExpiration);
        return expiresAt != null && !expiresAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Node firstElement(NodeList nodes) {
        if (nodes == null) {
            return null;
        }
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
                return node;
            }
        }
        return null;
    }

    private String buildLicenseDiagnosticExcerpt(String response, Document xml) {
        String encodedLicenseData = extractFirstText(xml, "licenseData");
        if (StringUtils.hasText(encodedLicenseData)) {
            String rawLicenseData = HtmlUtils.htmlUnescape(encodedLicenseData);
            String decodedLicenseData = repairDisplayedText(rawLicenseData);
            String formattedInner = formatDiagnosticText(decodedLicenseData, 12_000);
            if (StringUtils.hasText(formattedInner)) {
                return formattedInner;
            }
        }
        return formatDiagnosticText(response, 12_000);
    }

    private Map<String, String> findTargetLicense(List<Map<String, String>> details, LicenseDescriptor descriptor) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        for (Map<String, String> item : details) {
            String licenseId = firstNonBlank(
                item.get("id"),
                item.get("license_id"),
                item.get("licenseid"),
                item.get("product_id"),
                item.get("module_id"),
                item.get("type_id"),
                item.get("module_license_id")
            );
            if (descriptor.id().equals(StringUtils.trimWhitespace(licenseId))) {
                return item;
            }
        }
        for (Map<String, String> item : details) {
            String title = firstNonBlank(
                item.get("title"),
                item.get("name"),
                item.get("license_name"),
                item.get("license"),
                item.get("type"),
                item.get("license_type"),
                item.get("product"),
                item.get("module_name"),
                item.get("display_name")
            );
            if (StringUtils.hasText(title) && descriptor.title().equalsIgnoreCase(title.trim())) {
                return item;
            }
        }
        for (Map<String, String> item : details) {
            if (containsTargetLicenseMarkers(item, descriptor)) {
                return item;
            }
        }
        return null;
    }

    private void enrichLicenseDetails(LinkedHashMap<String, String> item, int index) {
        String title = firstNonBlank(
            item.get("name"),
            item.get("license_name"),
            item.get("license"),
            item.get("type"),
            item.get("license_type"),
            item.get("module"),
            item.get("product"),
            item.get("module_name"),
            item.get("display_name"),
            "Лицензия #" + (index + 1)
        );
        item.putIfAbsent("title", title);
        String expiration = firstNonBlank(
            item.get("expiration"),
            item.get("expires_at"),
            item.get("valid_to"),
            item.get("expire_date"),
            item.get("expiration_date"),
            item.get("expires"),
            item.get("expire"),
            item.get("date_to"),
            item.get("end_date"),
            item.get("date_end")
        );
        if (StringUtils.hasText(expiration)) {
            item.put("expiration", expiration);
        }
        String quantity = firstNonBlank(
            item.get("amount"),
            item.get("count"),
            item.get("quantity"),
            item.get("limit"),
            item.get("connections")
        );
        if (StringUtils.hasText(quantity)) {
            item.put("quantity", quantity);
        }
        String state = firstNonBlank(
            item.get("status"),
            item.get("state"),
            item.get("active"),
            item.get("enabled")
        );
        if (StringUtils.hasText(state)) {
            item.put("state", state);
        }
    }

    private void appendNodeAttributes(LinkedHashMap<String, String> item, Node node) {
        if (item == null || node == null) {
            return;
        }
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (attribute == null) {
                continue;
            }
            String key = normalizeLicenseKey(attribute.getNodeName());
            String value = trimText(attribute.getNodeValue(), 500, "");
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                item.putIfAbsent(key, value);
            }
        }
    }

    private void collectLicenseNode(LinkedHashMap<String, String> item, Node node, String keyPrefix) {
        if (item == null || node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        String currentKey = normalizeLicenseKey(keyPrefix);
        appendNodeAttributes(item, node);
        if (StringUtils.hasText(currentKey)) {
            appendPrefixedAttributes(item, node, currentKey);
        }
        String value = trimText(node.getTextContent(), 500, "");
        if (StringUtils.hasText(currentKey) && StringUtils.hasText(value)) {
            item.putIfAbsent(currentKey, value);
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child == null || child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String childKey = normalizeLicenseKey(child.getNodeName());
            String nextPrefix = StringUtils.hasText(currentKey) ? currentKey + "_" + childKey : childKey;
            collectLicenseNode(item, child, nextPrefix);
        }
    }

    private void appendPrefixedAttributes(LinkedHashMap<String, String> item, Node node, String keyPrefix) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (attribute == null) {
                continue;
            }
            String key = normalizeLicenseKey(keyPrefix + "_" + attribute.getNodeName());
            String value = trimText(attribute.getNodeValue(), 500, "");
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                item.putIfAbsent(key, value);
            }
        }
    }

    private boolean containsTargetLicenseMarkers(Map<String, String> item, LicenseDescriptor descriptor) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        String normalizedTitle = normalizeSearchToken(descriptor.title());
        boolean hasTargetId = false;
        boolean hasTargetName = false;
        for (Map.Entry<String, String> entry : item.entrySet()) {
            String key = normalizeSearchToken(entry.getKey());
            String value = normalizeSearchToken(entry.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!hasTargetId && key.contains("id") && descriptor.id().equals(value)) {
                hasTargetId = true;
            }
            if (!hasTargetId && descriptor.id().equals(value)) {
                hasTargetId = true;
            }
            if (!hasTargetName && matchesLicenseTitleMarker(value, normalizedTitle, descriptor)) {
                hasTargetName = true;
            }
        }
        return hasTargetName && (hasTargetId || !item.containsKey("id"));
    }

    private boolean matchesLicenseTitleMarker(String value, String normalizedTitle, LicenseDescriptor descriptor) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.equals(normalizedTitle)) {
            return true;
        }
        if (RMS_TARGET_LICENSE_ID.equals(descriptor.id())) {
            return value.contains("rms") && value.contains("front fast food");
        }
        if (CHAIN_TARGET_LICENSE_ID.equals(descriptor.id())) {
            return value.contains("chain") && value.contains("store connector");
        }
        if (KIOSK_CONNECTOR_LICENSE_ID.equals(descriptor.id())) {
            return value.contains("iikoconnector") && value.contains("kiosk");
        }
        return false;
    }

    private LicenseDescriptor resolvePrimaryLicenseDescriptor(RmsLicenseMonitor monitor) {
        if (isIikoChainServer(monitor)) {
            return new LicenseDescriptor(CHAIN_TARGET_LICENSE_ID, CHAIN_TARGET_LICENSE_TITLE);
        }
        return new LicenseDescriptor(RMS_TARGET_LICENSE_ID, RMS_TARGET_LICENSE_TITLE);
    }

    private LicenseDescriptor kioskConnectorLicenseDescriptor() {
        return new LicenseDescriptor(KIOSK_CONNECTOR_LICENSE_ID, KIOSK_CONNECTOR_LICENSE_TITLE);
    }

    private boolean isIikoChainServer(RmsLicenseMonitor monitor) {
        return "IIKO_CHAIN".equals(resolveServerTypeKey(monitor));
    }

    private boolean isIikoRmsServer(RmsLicenseMonitor monitor) {
        return "IIKO_RMS".equals(resolveServerTypeKey(monitor));
    }

    private String normalizeServerTypeKey(String rawServerType) {
        if (!StringUtils.hasText(rawServerType)) {
            return "";
        }
        String normalized = rawServerType.trim()
            .replace('\\', '/')
            .replace('-', '_')
            .replace(' ', '_')
            .toLowerCase(Locale.ROOT);
        if (normalized.contains("iiko_chain") || normalized.contains("iikochain") || "chain".equals(normalized)) {
            return "IIKO_CHAIN";
        }
        if (normalized.contains("iiko_rms")
            || normalized.contains("iikorms")
            || normalized.contains("iiko_office")
            || normalized.contains("iikooffice")
            || "office".equals(normalized)
            || "default".equals(normalized)) {
            return "IIKO_RMS";
        }
        return "";
    }

    private OffsetDateTime resolveLicenseExpiration(Map<String, String> targetLicense) {
        if (targetLicense == null || targetLicense.isEmpty()) {
            return null;
        }
        for (String key : List.of(
            "expiration",
            "expires_at",
            "valid_to",
            "expire_date",
            "expiration_date",
            "expires",
            "expire",
            "date_to",
            "end_date",
            "date_end",
            "module_expiration",
            "module_expires_at"
        )) {
            OffsetDateTime parsed = parseFlexibleDate(targetLicense.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        for (Map.Entry<String, String> entry : targetLicense.entrySet()) {
            String normalizedKey = normalizeSearchToken(entry.getKey());
            if (!normalizedKey.contains("expir")
                && !normalizedKey.contains("valid_to")
                && !normalizedKey.contains("date_to")
                && !normalizedKey.contains("end_date")) {
                continue;
            }
            OffsetDateTime parsed = parseFlexibleDate(entry.getValue());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private String normalizeLicenseKey(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        boolean previousUnderscore = false;
        for (char ch : rawKey.trim().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                if (Character.isUpperCase(ch) && normalized.length() > 0 && !previousUnderscore) {
                    normalized.append('_');
                }
                normalized.append(Character.toLowerCase(ch));
                previousUnderscore = false;
            } else if (!previousUnderscore) {
                normalized.append('_');
                previousUnderscore = true;
            }
        }
        String value = normalized.toString().replaceAll("^_+|_+$", "");
        return value.replaceAll("_+", "_");
    }

    private String writeLicenseDetails(List<Map<String, String>> items) throws Exception {
        return objectMapper.writeValueAsString(items == null ? List.of() : items);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(prefix + "-" + UUID.randomUUID());
            return thread;
        };
    }

    public record QueueState(boolean running,
                             boolean queued,
                             OffsetDateTime lastRequestedAt,
                             OffsetDateTime lastCompletedAt,
                             Long currentMonitorId,
                             int totalCount,
                             int completedCount) {
    }

    public record RefreshState(QueueState licenses, QueueState network) {
    }

    public record RefreshRequestResult(String state, RefreshState refreshState) {
    }

    public record LicenseDetailsView(Long id,
                                     String rmsAddress,
                                     String serverName,
                                     OffsetDateTime lastCheckedAt,
                                     List<Map<String, String>> items) {
    }

    public record DiagnosticsView(Long id,
                                  String rmsAddress,
                                  String serverName,
                                  OffsetDateTime licenseLastCheckedAt,
                                  OffsetDateTime rmsLastCheckedAt,
                                  String licenseStatus,
                                  String licenseErrorMessage,
                                  String licenseDebugExcerpt,
                                  String rmsStatus,
                                  String rmsStatusMessage,
                                  String pingOutput,
                                  String tracerouteSummary,
                                  String tracerouteReport,
                                  List<TimelineEntryView> timeline) {
    }

    public record TimelineEntryView(String checkKind,
                                    String status,
                                    String summary,
                                    String detailsExcerpt,
                                    Integer httpStatus,
                                    Long durationMs,
                                    OffsetDateTime createdAt) {
    }

    public record AvailabilityOverview(int total,
                                       int up,
                                       int down,
                                       int unknown,
                                       int disabled,
                                       double availabilityPercent) {
    }

    private record LicenseRefreshTask(Long monitorId, boolean withNotifications) {
    }

    private record NetworkRefreshTask(Long monitorId) {
    }

    private record EndpointTarget(String normalizedAddress, String scheme, String host, int port) {
    }

    private record HostPort(String host, Integer port) {
    }

    private record ServerMetadata(String serverName, String serverType, String serverVersion, String serverEdition) {
    }

    private record LicenseSnapshot(OffsetDateTime expiresAt, int daysLeft, String detailsJson, String debugExcerpt) {
    }

    private record LicenseDescriptor(String id, String title) {
    }

    private record CommandResult(boolean success, String output) {
    }

    private static final class LicenseSnapshotException extends Exception {
        private final String detailsJson;
        private final String debugExcerpt;

        private LicenseSnapshotException(String message, String detailsJson, String debugExcerpt) {
            super(message);
            this.detailsJson = detailsJson;
            this.debugExcerpt = debugExcerpt;
        }

        private String detailsJson() {
            return detailsJson;
        }

        private String debugExcerpt() {
            return debugExcerpt;
        }
    }

    private static final class QueueProgressTracker {
        private final Set<Long> targetIds = new LinkedHashSet<>();
        private final Set<Long> completedIds = new LinkedHashSet<>();
        private Long currentMonitorId;

        synchronized void start(List<Long> monitorIds) {
            targetIds.clear();
            completedIds.clear();
            currentMonitorId = null;
            if (monitorIds != null) {
                targetIds.addAll(monitorIds.stream().filter(id -> id != null && id > 0L).toList());
            }
        }

        synchronized void markRunning(Long monitorId) {
            if (monitorId != null) {
                currentMonitorId = monitorId;
            }
        }

        synchronized void markCompleted(Long monitorId) {
            if (monitorId != null) {
                completedIds.add(monitorId);
                if (monitorId.equals(currentMonitorId)) {
                    currentMonitorId = null;
                }
            }
        }

        synchronized void finish() {
            currentMonitorId = null;
        }

        synchronized String resolveState(Long monitorId) {
            if (monitorId == null || targetIds.isEmpty() || !targetIds.contains(monitorId)) {
                return "idle";
            }
            if (monitorId.equals(currentMonitorId)) {
                return "running";
            }
            if (completedIds.contains(monitorId)) {
                return "done";
            }
            return "queued";
        }

        synchronized Long currentMonitorId() {
            return currentMonitorId;
        }

        synchronized int totalCount() {
            return targetIds.size();
        }

        synchronized int completedCount() {
            return completedIds.size();
        }
    }
}
