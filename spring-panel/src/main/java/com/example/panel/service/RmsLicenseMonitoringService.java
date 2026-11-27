package com.example.panel.service;

import static com.example.panel.service.RmsLicenseClient.RMS_TARGET_LICENSE_ID;
import static com.example.panel.service.RmsLicenseClient.RMS_TARGET_LICENSE_TITLE;

import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeRoleProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.panel.entity.RmsLicenseMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.RmsLicenseMonitorRepository;
import com.example.panel.repository.RmsRefreshQueueRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import com.example.panel.service.RmsLicenseClient.*;
import com.example.panel.service.RmsNetworkProbe.CommandResult;

@Service
public class RmsLicenseMonitoringService {
    @org.springframework.beans.factory.annotation.Autowired
    private IncidentMonitoringPolicyService incidentMonitoringPolicy;

    public boolean isMonitoringPaused() {
        return incidentMonitoringPolicy != null && incidentMonitoringPolicy.isSuppressed("rms");
    }

    private void requireMonitoringEnabled() {
        if (incidentMonitoringPolicy != null && incidentMonitoringPolicy.isSuppressed("rms")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Проверки направления приостановлены в настройках мониторинга инцидентов.");
        }
    }

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

    private static final int LICENSE_WARNING_DAYS = 7;
    private static final int LICENSE_CRITICAL_DAYS = 3;

    private static final long COMMAND_TIMEOUT_MS = 90_000L;
    private static final String MONITORING_PAGE_URL = "/analytics/rms-control";

    private final RmsTextCodec textCodec;
    private final RmsLicenseClient licenseClient;
    private final RmsNetworkProbe networkProbe;
    private final RmsLicenseMonitorRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final RmsRefreshQueueRepository refreshQueueRepository;
    private final ObjectMapper objectMapper;
    private final RmsMonitoringScheduleSettingsService scheduleSettingsService;

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
    @Autowired
    private DialogNotificationService dialogNotificationService;

    @Autowired(required = false)
    private RuntimeRoleProperties runtimeRoleProperties;

    @Autowired(required = false)
    private BackendOpsCommandService backendOpsCommandService;

    @Autowired(required = false)
    private BackendOpsCommandExecutionContext backendOpsExecutionContext;

    @Autowired
    public RmsLicenseMonitoringService(RmsLicenseMonitorRepository repository,
                                       MonitoringCheckHistoryRepository historyRepository,
                                       NotificationService notificationService,
                                       RmsRefreshQueueRepository refreshQueueRepository,
                                        ObjectMapper objectMapper,
                                        RmsMonitoringScheduleSettingsService scheduleSettingsService) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.refreshQueueRepository = refreshQueueRepository;
        this.objectMapper = objectMapper;
        this.scheduleSettingsService = scheduleSettingsService;
        this.textCodec = new RmsTextCodec(objectMapper);
        this.licenseClient = new RmsLicenseClient(objectMapper, textCodec);
        this.networkProbe = new RmsNetworkProbe(textCodec);
        this.licenseRefreshExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("rms-license-refresh"));
        this.networkRefreshExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("rms-network-refresh"));
    }

    RmsLicenseMonitoringService(RmsLicenseMonitorRepository repository, MonitoringCheckHistoryRepository historyRepository, NotificationService notificationService, RmsRefreshQueueRepository refreshQueueRepository, ObjectMapper objectMapper) {
        this(repository, historyRepository, notificationService, refreshQueueRepository, objectMapper, null);
    }

    @PreDestroy
    void shutdownExecutors() {
        licenseRefreshExecutor.shutdownNow();
        networkRefreshExecutor.shutdownNow();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreQueuedRefreshTasks() {
        RuntimeRole role = runtimeRoleProperties == null
            ? RuntimeRole.ALL
            : runtimeRoleProperties.resolvedRole();
        if (role != RuntimeRole.ALL) {
            log.info(
                "Skipping persisted RMS queue restore for runtime role '{}'; explicit roles use backend_ops_command.",
                role.externalName()
            );
            return;
        }
        schedulePersistedLicenseRefreshIfNeeded();
        schedulePersistedNetworkRefreshIfNeeded();
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
        EndpointTarget target = licenseClient.parseEndpointSafe(rmsAddress);
        Optional<RmsLicenseMonitor> existingMonitor = repository.findAnyByRmsAddress(target.normalizedAddress());
        if (existingMonitor.isPresent() && !Boolean.TRUE.equals(existingMonitor.get().getDeleted())) {
            throw new IllegalArgumentException("Этот RMS уже добавлен в мониторинг");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RmsLicenseMonitor monitor = existingMonitor.orElseGet(RmsLicenseMonitor::new);
        monitor.setRmsAddress(target.normalizedAddress());
        monitor.setScheme(target.scheme());
        monitor.setHost(target.host());
        monitor.setPort(target.port());
        monitor.setAuthLogin(requireLogin(authLogin));
        monitor.setAuthPassword(requirePassword(authPassword, true));
        monitor.setEnabled(enabled == null || enabled);
        monitor.setLicenseMonitoringEnabled(licenseMonitoringEnabled == null || licenseMonitoringEnabled);
        monitor.setNetworkMonitoringEnabled(networkMonitoringEnabled == null || networkMonitoringEnabled);
        monitor.setDeleted(false);
        monitor.setDeletedAt(null);
        monitor.setLicenseStatus(LICENSE_STATUS_DISABLED);
        monitor.setRmsStatus(RMS_STATUS_DISABLED);
        if (monitor.getCreatedAt() == null) {
            monitor.setCreatedAt(now);
        }
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

        EndpointTarget target = licenseClient.parseEndpointSafe(rmsAddress);
        repository.findAnyByRmsAddress(target.normalizedAddress()).ifPresent(existing -> {
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
        monitor.setDeleted(false);
        monitor.setDeletedAt(null);
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(monitor);

        synchronizeFeatureStates(monitor, false);
        return repository.findById(monitor.getId()).orElse(monitor);
    }

    public void deleteMonitor(long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("RMS-запись не найдена");
        }
        repository.softDeleteById(id);
    }

    public RefreshRequestResult requestLicenseRefresh(boolean withNotifications) {
        requireMonitoringEnabled();
        return queueLicenseRefresh(null, withNotifications);
    }

    public RefreshRequestResult requestLicenseRefreshForMonitor(long id, boolean withNotifications) {
        requireMonitoringEnabled();
        requireMonitor(id);
        return queueLicenseRefresh(id, withNotifications);
    }

    public RefreshRequestResult requestNetworkRefresh() {
        requireMonitoringEnabled();
        return queueNetworkRefresh(null);
    }

    public RefreshRequestResult requestNetworkRefreshForMonitor(long id) {
        requireMonitoringEnabled();
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
        if (useDurableBackendOps()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (monitorId != null) {
                payload.put("monitor_id", monitorId);
            }
            payload.put("with_notifications", withNotifications);
            BackendOpsCommandService.EnqueueResult queued =
                backendOpsCommandService.enqueueExclusive(
                    BackendOpsCommandTypes.RMS_LICENSE_REFRESH,
                    monitorId == null ? "all" : "monitor:" + monitorId,
                    payload,
                    "panel"
                );
            return new RefreshRequestResult(
                queued.created() ? "queued" : "already_queued",
                currentRefreshState()
            );
        }

        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (!licenseRefreshPending.compareAndSet(false, true)) {
            return new RefreshRequestResult("already_queued", currentRefreshState());
        }
        try {
            RmsRefreshQueueRepository.RefreshQueueEntry queuedTask = refreshQueueRepository.enqueue(
                RmsRefreshQueueRepository.KIND_LICENSE,
                monitorId,
                withNotifications,
                requestedAt
            );
            lastLicenseRefreshRequestedAt.set(requestedAt);
            pendingLicenseTask.set(new LicenseRefreshTask(
                queuedTask.id(),
                monitorId,
                withNotifications,
                requestedAt
            ));
            licenseRefreshExecutor.submit(this::runQueuedLicenseRefresh);
            return new RefreshRequestResult("queued", currentRefreshState());
        } catch (RuntimeException ex) {
            licenseRefreshPending.set(false);
            throw ex;
        }
    }

    private RefreshRequestResult queueNetworkRefresh(Long monitorId) {
        if (useDurableBackendOps()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (monitorId != null) {
                payload.put("monitor_id", monitorId);
            }
            BackendOpsCommandService.EnqueueResult queued =
                backendOpsCommandService.enqueueExclusive(
                    BackendOpsCommandTypes.RMS_NETWORK_REFRESH,
                    monitorId == null ? "all" : "monitor:" + monitorId,
                    payload,
                    "panel"
                );
            return new RefreshRequestResult(
                queued.created() ? "queued" : "already_queued",
                currentRefreshState()
            );
        }

        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (!networkRefreshPending.compareAndSet(false, true)) {
            return new RefreshRequestResult("already_queued", currentRefreshState());
        }
        try {
            RmsRefreshQueueRepository.RefreshQueueEntry queuedTask = refreshQueueRepository.enqueue(
                RmsRefreshQueueRepository.KIND_NETWORK,
                monitorId,
                false,
                requestedAt
            );
            lastNetworkRefreshRequestedAt.set(requestedAt);
            pendingNetworkTask.set(new NetworkRefreshTask(queuedTask.id(), monitorId, requestedAt));
            networkRefreshExecutor.submit(this::runQueuedNetworkRefresh);
            return new RefreshRequestResult("queued", currentRefreshState());
        } catch (RuntimeException ex) {
            networkRefreshPending.set(false);
            throw ex;
        }
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
        return textCodec.resolveDisplayServerNameInternal(
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
        LicenseDescriptor currentDescriptor = licenseClient.resolvePrimaryLicenseDescriptor(monitor);
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
        if (useDurableBackendOps()) {
            return new RefreshState(
                durableQueueState(BackendOpsCommandTypes.RMS_LICENSE_REFRESH),
                durableQueueState(BackendOpsCommandTypes.RMS_NETWORK_REFRESH)
            );
        }

        Long currentLicenseMonitorId = licenseQueueTracker.currentMonitorId();
        Long currentNetworkMonitorId = networkQueueTracker.currentMonitorId();

        return new RefreshState(
            new QueueState(
                licenseRefreshRunning.get(),
                licenseRefreshPending.get(),
                lastLicenseRefreshRequestedAt.get(),
                lastLicenseRefreshCompletedAt.get(),
                currentLicenseMonitorId,
                null,
                licenseQueueTracker.totalCount(),
                licenseQueueTracker.completedCount(),
                licenseRefreshRunning.get()
                    ? (currentLicenseMonitorId != null ? "checking" : "running")
                    : (licenseRefreshPending.get() ? "queued" : "scheduled_wait")
            ),
            new QueueState(
                networkRefreshRunning.get(),
                networkRefreshPending.get(),
                lastNetworkRefreshRequestedAt.get(),
                lastNetworkRefreshCompletedAt.get(),
                currentNetworkMonitorId,
                null,
                networkQueueTracker.totalCount(),
                networkQueueTracker.completedCount(),
                networkRefreshRunning.get()
                    ? (currentNetworkMonitorId != null ? "checking" : "running")
                    : (networkRefreshPending.get() ? "queued" : "scheduled_wait")
            )
        );
    }
    private void schedulePersistedLicenseRefreshIfNeeded() {
        if (licenseRefreshRunning.get()) {
            return;
        }
        if (!licenseRefreshPending.compareAndSet(false, true)) {
            return;
        }
        Optional<RmsRefreshQueueRepository.RefreshQueueEntry> nextTask =
            refreshQueueRepository.findNextActive(RmsRefreshQueueRepository.KIND_LICENSE);
        if (nextTask.isEmpty()) {
            licenseRefreshPending.set(false);
            return;
        }
        LicenseRefreshTask task = toLicenseRefreshTask(nextTask.get());
        pendingLicenseTask.set(task);
        lastLicenseRefreshRequestedAt.set(task.requestedAt());
        licenseRefreshExecutor.submit(this::runQueuedLicenseRefresh);
    }

    private void schedulePersistedNetworkRefreshIfNeeded() {
        if (networkRefreshRunning.get()) {
            return;
        }
        if (!networkRefreshPending.compareAndSet(false, true)) {
            return;
        }
        Optional<RmsRefreshQueueRepository.RefreshQueueEntry> nextTask =
            refreshQueueRepository.findNextActive(RmsRefreshQueueRepository.KIND_NETWORK);
        if (nextTask.isEmpty()) {
            networkRefreshPending.set(false);
            return;
        }
        NetworkRefreshTask task = toNetworkRefreshTask(nextTask.get());
        pendingNetworkTask.set(task);
        lastNetworkRefreshRequestedAt.set(task.requestedAt());
        networkRefreshExecutor.submit(this::runQueuedNetworkRefresh);
    }

    private LicenseRefreshTask toLicenseRefreshTask(RmsRefreshQueueRepository.RefreshQueueEntry entry) {
        return new LicenseRefreshTask(
            entry.id(),
            entry.monitorId(),
            entry.withNotifications(),
            entry.requestedAt()
        );
    }

    private NetworkRefreshTask toNetworkRefreshTask(RmsRefreshQueueRepository.RefreshQueueEntry entry) {
        return new NetworkRefreshTask(
            entry.id(),
            entry.monitorId(),
            entry.requestedAt()
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
        return resolveLicenseQuantity(monitor, licenseClient.resolvePrimaryLicenseDescriptor(monitor));
    }

    public Integer resolveKioskConnectorLicenseQuantity(RmsLicenseMonitor monitor) {
        if (monitor == null || !licenseClient.isIikoRmsServer(monitor)) {
            return null;
        }
        return resolveLicenseQuantity(monitor, licenseClient.kioskConnectorLicenseDescriptor());
    }

    public String resolveServerTypeKey(RmsLicenseMonitor monitor) { return licenseClient.resolveServerTypeKey(monitor); }

    public String resolveServerTypeDisplay(RmsLicenseMonitor monitor) { return licenseClient.resolveServerTypeDisplay(monitor); }

    public String resolveTargetLicenseLabel(RmsLicenseMonitor monitor) {
        return licenseClient.resolvePrimaryLicenseDescriptor(monitor).title();
    }

    public String resolveTargetLicenseId(RmsLicenseMonitor monitor) {
        return licenseClient.resolvePrimaryLicenseDescriptor(monitor).id();
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
            Map<String, String> targetLicense = licenseClient.findTargetLicense(new ArrayList<>(storedItems), descriptor);
            if (targetLicense == null) {
                return null;
            }
            String quantity = textCodec.firstNonBlank(
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
            textCodec.trimText(status, 40, ""),
            textCodec.trimText(summary, 600, ""),
            textCodec.trimText(detailsExcerpt, 4_000, ""),
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
            summary.append(licenseClient.resolvePrimaryLicenseDescriptor(monitor).title())
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
        return textCodec.trimText(summary.toString(), 600, "");
    }

    public Map<String, Object> executeBackendOpsLicenseRefresh(Map<String, Object> payload) {
        requireMonitoringEnabled();
        Long monitorId = longValue(payload == null ? null : payload.get("monitor_id"));
        boolean withNotifications = booleanValue(
            payload == null ? null : payload.get("with_notifications"),
            false
        );
        reportBackendOpsProgress(5, "starting");
        if (monitorId == null) {
            refreshAllLicensesInternal(withNotifications);
        } else {
            RmsLicenseMonitor monitor = requireMonitor(monitorId);
            licenseQueueTracker.start(List.of(monitor.getId()));
            licenseQueueTracker.markRunning(monitor.getId());
            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                licenseQueueTracker.completedCount(),
                licenseQueueTracker.totalCount()
            );
            refreshLicenseState(monitor, withNotifications);
            licenseQueueTracker.markCompleted(monitor.getId());
            reportBackendOpsQueueProgress(
                "finishing",
                null,
                null,
                licenseQueueTracker.completedCount(),
                licenseQueueTracker.totalCount()
            );
        }
        return Map.of(
            "state", "success",
            "scope", monitorId == null ? "all" : "monitor:" + monitorId
        );
    }

    public Map<String, Object> executeBackendOpsNetworkRefresh(Map<String, Object> payload) {
        requireMonitoringEnabled();
        Long monitorId = longValue(payload == null ? null : payload.get("monitor_id"));
        reportBackendOpsProgress(5, "starting");
        if (monitorId == null) {
            refreshAllNetworkStatesInternal();
        } else {
            RmsLicenseMonitor monitor = requireMonitor(monitorId);
            networkQueueTracker.start(List.of(monitor.getId()));
            networkQueueTracker.markRunning(monitor.getId());
            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                networkQueueTracker.completedCount(),
                networkQueueTracker.totalCount()
            );
            refreshNetworkState(monitor);
            networkQueueTracker.markCompleted(monitor.getId());
            reportBackendOpsQueueProgress(
                "finishing",
                null,
                null,
                networkQueueTracker.completedCount(),
                networkQueueTracker.totalCount()
            );
        }
        return Map.of(
            "state", "success",
            "scope", monitorId == null ? "all" : "monitor:" + monitorId
        );
    }

    private QueueState durableQueueState(String commandType) {
        BackendOpsCommandService.CommandSnapshot active =
            backendOpsCommandService.findActiveByType(commandType).orElse(null);
        BackendOpsCommandService.CommandSnapshot latest =
            backendOpsCommandService.findLatestByType(commandType).orElse(null);
        BackendOpsCommandService.CommandSnapshot lastSucceeded =
            backendOpsCommandService.findLatestSucceededByType(commandType).orElse(null);

        BackendOpsCommandService.CommandSnapshot source =
            active != null ? active : latest;
        Long requestedMonitorId = longValue(
            source == null ? null : source.payload().get("monitor_id")
        );

        int totalCount = 0;
        if (source != null) {
            totalCount = requestedMonitorId != null
                ? 1
                : repository.findAllByOrderByRmsAddressAscIdAsc().size();
        }

        int completedCount = source != null && source.succeeded()
            ? totalCount
            : 0;
        Long currentMonitorId = null;
        Long nextMonitorId = null;
        String phase = "scheduled_wait";

        DurableQueueProgress durableProgress = active == null
            ? null
            : parseDurableQueueProgress(active.progressMessage());

        if (active != null && active.queued()) {
            phase = "queued";
            if (durableProgress != null) {
                if (durableProgress.totalCount() > 0) {
                    totalCount = durableProgress.totalCount();
                }
                completedCount = clampCompletedCount(
                    durableProgress.completedCount(),
                    totalCount
                );
            }
        } else if (active != null && active.running()) {
            phase = "running";
            currentMonitorId = requestedMonitorId;

            if (durableProgress != null) {
                phase = durableProgress.phase();
                currentMonitorId = durableProgress.currentMonitorId();
                nextMonitorId = durableProgress.nextMonitorId();

                if (durableProgress.totalCount() > 0) {
                    totalCount = durableProgress.totalCount();
                }

                completedCount = clampCompletedCount(
                    durableProgress.completedCount(),
                    totalCount
                );
            }
        }

        return new QueueState(
            active != null && active.running(),
            active != null && active.queued(),
            latest == null ? null : latest.requestedAt(),
            lastSucceeded == null ? null : lastSucceeded.completedAt(),
            currentMonitorId,
            nextMonitorId,
            totalCount,
            completedCount,
            phase
        );
    }

    private boolean useDurableBackendOps() {
        if (backendOpsCommandService == null || runtimeRoleProperties == null) {
            return false;
        }
        RuntimeRole role = runtimeRoleProperties.resolvedRole();
        return role != RuntimeRole.ALL && role != RuntimeRole.MIGRATOR;
    }

    private void reportBackendOpsProgress(int progressPercent, String message) {
        if (backendOpsExecutionContext != null) {
            backendOpsExecutionContext.reportProgress(progressPercent, message);
        }
    }

    private void reportBackendOpsQueueProgress(String phase,
                                               Long currentMonitorId,
                                               Long nextMonitorId,
                                               int completedCount,
                                               int totalCount) {
        if (backendOpsExecutionContext == null || !backendOpsExecutionContext.active()) {
            return;
        }

        int safeTotal = Math.max(0, totalCount);
        int safeCompleted = clampCompletedCount(completedCount, safeTotal);
        int progressPercent = safeTotal <= 0
            ? 5
            : Math.min(98, 5 + (int) Math.floor((safeCompleted * 93.0d) / safeTotal));

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("kind", "rms_queue_progress");
        progress.put("phase", StringUtils.hasText(phase) ? phase.trim() : "running");
        progress.put("current_monitor_id", currentMonitorId);
        progress.put("next_monitor_id", nextMonitorId);
        progress.put("completed_count", safeCompleted);
        progress.put("total_count", safeTotal);

        try {
            reportBackendOpsProgress(
                progressPercent,
                objectMapper.writeValueAsString(progress)
            );
        } catch (Exception ex) {
            reportBackendOpsProgress(progressPercent, String.valueOf(progress.get("phase")));
        }
    }

    private DurableQueueProgress parseDurableQueueProgress(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }

        try {
            Map<String, Object> progress = objectMapper.readValue(
                trimmed,
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
            );

            if (!"rms_queue_progress".equals(String.valueOf(progress.get("kind")))) {
                return null;
            }

            String phase = progress.get("phase") == null
                ? "running"
                : String.valueOf(progress.get("phase")).trim();

            if (!StringUtils.hasText(phase)) {
                phase = "running";
            }

            return new DurableQueueProgress(
                phase,
                longValue(progress.get("current_monitor_id")),
                longValue(progress.get("next_monitor_id")),
                intValue(progress.get("completed_count"), 0),
                intValue(progress.get("total_count"), 0)
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private int clampCompletedCount(int completedCount, int totalCount) {
        int safeTotal = Math.max(0, totalCount);
        return Math.max(0, Math.min(completedCount, safeTotal));
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean rawBoolean) {
            return rawBoolean;
        }
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        return !"false".equalsIgnoreCase(normalized)
            && !"0".equals(normalized)
            && !"off".equalsIgnoreCase(normalized)
            && !"no".equalsIgnoreCase(normalized);
    }
    private void runQueuedLicenseRefresh() {
        LicenseRefreshTask task = pendingLicenseTask.getAndSet(null);
        licenseRefreshPending.set(false);
        if (task == null) {
            schedulePersistedLicenseRefreshIfNeeded();
            return;
        }
        licenseRefreshRunning.set(true);
        try {
            refreshQueueRepository.markRunning(task.queueEntryId());
            if (task.monitorId() == null) {
                refreshAllLicensesInternal(task.withNotifications());
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
            refreshQueueRepository.delete(task.queueEntryId());
            licenseQueueTracker.finish();
            licenseRefreshRunning.set(false);
            schedulePersistedLicenseRefreshIfNeeded();
        }
    }

    private void runQueuedNetworkRefresh() {
        NetworkRefreshTask task = pendingNetworkTask.getAndSet(null);
        networkRefreshPending.set(false);
        if (task == null) {
            schedulePersistedNetworkRefreshIfNeeded();
            return;
        }
        networkRefreshRunning.set(true);
        try {
            refreshQueueRepository.markRunning(task.queueEntryId());
            if (task.monitorId() == null) {
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
            refreshQueueRepository.delete(task.queueEntryId());
            networkQueueTracker.finish();
            networkRefreshRunning.set(false);
            schedulePersistedNetworkRefreshIfNeeded();
        }
    }

    private void refreshAllLicensesInternal(boolean withNotifications) {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        licenseQueueTracker.start(monitors.stream().map(RmsLicenseMonitor::getId).toList());

        for (int index = 0; index < monitors.size(); index++) {
            if (isMonitoringPaused()) break;
            RmsLicenseMonitor monitor = monitors.get(index);
            licenseQueueTracker.markRunning(monitor.getId());

            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                licenseQueueTracker.completedCount(),
                licenseQueueTracker.totalCount()
            );

            refreshLicenseState(monitor, withNotifications);
            licenseQueueTracker.markCompleted(monitor.getId());

            Long nextMonitorId = index < monitors.size() - 1
                ? monitors.get(index + 1).getId()
                : null;

            if (nextMonitorId != null) {
                reportBackendOpsQueueProgress(
                    "waiting_gap",
                    null,
                    nextMonitorId,
                    licenseQueueTracker.completedCount(),
                    licenseQueueTracker.totalCount()
                );
                sleepBetweenQueueItems(index, monitors.size());
            } else {
                reportBackendOpsQueueProgress(
                    "finishing",
                    null,
                    null,
                    licenseQueueTracker.completedCount(),
                    licenseQueueTracker.totalCount()
                );
            }
        }
    }

    private void refreshAllNetworkStatesInternal() {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        networkQueueTracker.start(monitors.stream().map(RmsLicenseMonitor::getId).toList());

        for (int index = 0; index < monitors.size(); index++) {
            if (isMonitoringPaused()) break;
            RmsLicenseMonitor monitor = monitors.get(index);
            networkQueueTracker.markRunning(monitor.getId());

            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                networkQueueTracker.completedCount(),
                networkQueueTracker.totalCount()
            );

            refreshNetworkState(monitor);
            networkQueueTracker.markCompleted(monitor.getId());

            Long nextMonitorId = index < monitors.size() - 1
                ? monitors.get(index + 1).getId()
                : null;

            if (nextMonitorId != null) {
                reportBackendOpsQueueProgress(
                    "waiting_gap",
                    null,
                    nextMonitorId,
                    networkQueueTracker.completedCount(),
                    networkQueueTracker.totalCount()
                );
                sleepBetweenQueueItems(index, monitors.size());
            } else {
                reportBackendOpsQueueProgress(
                    "finishing",
                    null,
                    null,
                    networkQueueTracker.completedCount(),
                    networkQueueTracker.totalCount()
                );
            }
        }
    }
    private void refreshLicenseState(RmsLicenseMonitor monitor, boolean withNotifications) {
        if (incidentMonitoringPolicy != null && incidentMonitoringPolicy.isSuppressed("rms")) return;
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
            ServerMetadata metadata = licenseClient.fetchServerMetadata(monitor);
            monitor.setServerType(metadata.serverType());
            monitor.setServerVersion(metadata.serverVersion());

            LicenseSnapshot license = licenseClient.fetchLicenseSnapshot(monitor, metadata.serverEdition(), metadata.serverVersion());
            monitor.setServerName(textCodec.resolveDisplayServerNameInternal(metadata.serverName(), monitor.getServerName(), monitor.getHost(), license.detailsJson()));
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
            String errorMessage = textCodec.resolveExceptionMessage(ex, "Не удалось обновить лицензию RMS");
            if (ex instanceof LicenseSnapshotException snapshotException) {
                if (StringUtils.hasText(snapshotException.detailsJson())) {
                    monitor.setLicenseDetailsJson(snapshotException.detailsJson());
                }
                if (StringUtils.hasText(snapshotException.debugExcerpt())) {
                    monitor.setLicenseDebugExcerpt(snapshotException.debugExcerpt());
                }
            }
            monitor.setLicenseStatus(LICENSE_STATUS_ERROR);
            monitor.setLicenseErrorMessage(textCodec.trimText(errorMessage, 600, "Не удалось обновить лицензию RMS"));
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
        if (incidentMonitoringPolicy != null && incidentMonitoringPolicy.isSuppressed("rms")) return;
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
            CommandResult ping = networkProbe.runCommand(networkProbe.buildPingCommand(monitor.getHost()), COMMAND_TIMEOUT_MS);
            monitor.setPingOutput(textCodec.trimText(ping.output(), 20_000, ""));

            if (ping.success()) {
                monitor.setRmsStatus(RMS_STATUS_UP);
                monitor.setRmsStatusMessage(networkProbe.buildPingSuccessMessage(monitor.getHost(), ping.output()));
                monitor.setTracerouteSummary(null);
                monitor.setTracerouteReport(null);
                monitor.setTracerouteCheckedAt(null);
            } else {
                CommandResult traceroute = networkProbe.runCommand(networkProbe.buildTracerouteCommand(monitor.getHost()), COMMAND_TIMEOUT_MS);
                String tracerouteReport = textCodec.trimText(traceroute.output(), 40_000, "");
                String tracerouteSummary = networkProbe.summarizeTraceroute(tracerouteReport);

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
            String errorMessage = textCodec.resolveExceptionMessage(ex, "Не удалось проверить доступность RMS");
            monitor.setRmsStatus(RMS_STATUS_DOWN);
            monitor.setRmsStatusMessage(textCodec.trimText(errorMessage, 600, "Не удалось проверить доступность RMS"));
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
        if (dialogNotificationService != null) {
            dialogNotificationService.notifyAllSupportChats("Лицензии RMS", message);
        }
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

    private void sleepBetweenQueueItems(int index, int total) {
        if (index >= total - 1) {
            return;
        }
        try {
            Thread.sleep((scheduleSettingsService != null ? scheduleSettingsService.load().queueGapSeconds() : 20) * 1000L);
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
                             Long nextMonitorId,
                             int totalCount,
                             int completedCount,
                             String phase) {
    }

    private record DurableQueueProgress(String phase,
                                        Long currentMonitorId,
                                        Long nextMonitorId,
                                        int completedCount,
                                        int totalCount) {
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

    private record LicenseRefreshTask(long queueEntryId,
                                      Long monitorId,
                                      boolean withNotifications,
                                      OffsetDateTime requestedAt) {
    }

    private record NetworkRefreshTask(long queueEntryId,
                                      Long monitorId,
                                      OffsetDateTime requestedAt) {
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
