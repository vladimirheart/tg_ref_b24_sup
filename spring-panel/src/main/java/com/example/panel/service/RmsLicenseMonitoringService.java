package com.example.panel.service;

import com.example.panel.entity.RmsLicenseMonitor;
import com.example.panel.repository.RmsLicenseMonitorRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final NotificationService notificationService;
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

    public RmsLicenseMonitoringService(RmsLicenseMonitorRepository repository,
                                       NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
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

    @Transactional(transactionManager = "monitoringTransactionManager")
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

    @Transactional(transactionManager = "monitoringTransactionManager")
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

    @Transactional(transactionManager = "monitoringTransactionManager")
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

    @Transactional(transactionManager = "monitoringTransactionManager")
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

    @Transactional(transactionManager = "monitoringTransactionManager")
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

    public RefreshState currentRefreshState() {
        return new RefreshState(
            new QueueState(
                licenseRefreshRunning.get(),
                licenseRefreshPending.get(),
                lastLicenseRefreshRequestedAt.get(),
                lastLicenseRefreshCompletedAt.get()
            ),
            new QueueState(
                networkRefreshRunning.get(),
                networkRefreshPending.get(),
                lastNetworkRefreshRequestedAt.get(),
                lastNetworkRefreshCompletedAt.get()
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

    private void runQueuedLicenseRefresh() {
        LicenseRefreshTask task = pendingLicenseTask.getAndSet(null);
        licenseRefreshPending.set(false);
        licenseRefreshRunning.set(true);
        try {
            if (task == null || task.monitorId() == null) {
                refreshAllLicensesInternal(task == null || task.withNotifications());
            } else {
                refreshLicenseState(requireMonitor(task.monitorId()), task.withNotifications());
            }
            lastLicenseRefreshCompletedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (Exception ex) {
            log.warn("RMS license refresh queue failed", ex);
        } finally {
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
                refreshNetworkState(requireMonitor(task.monitorId()));
            }
            lastNetworkRefreshCompletedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (Exception ex) {
            log.warn("RMS network refresh queue failed", ex);
        } finally {
            networkRefreshRunning.set(false);
        }
    }

    private void refreshAllLicensesInternal(boolean withNotifications) {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        for (int index = 0; index < monitors.size(); index++) {
            refreshLicenseState(monitors.get(index), withNotifications);
            sleepBetweenQueueItems(index, monitors.size());
        }
    }

    private void refreshAllNetworkStatesInternal() {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        for (int index = 0; index < monitors.size(); index++) {
            refreshNetworkState(monitors.get(index));
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
            monitor.setServerName(metadata.serverName());
            monitor.setServerType(metadata.serverType());
            monitor.setServerVersion(metadata.serverVersion());

            LicenseSnapshot license = fetchLicenseSnapshot(monitor, metadata.serverEdition(), metadata.serverVersion());
            monitor.setLicenseExpiresAt(license.expiresAt());
            monitor.setLicenseDaysLeft(license.daysLeft());
            monitor.setLicenseStatus(deriveLicenseStatus(license.daysLeft()));
            monitor.setLicenseErrorMessage(null);

            if (withNotifications && shouldNotifyLicense(monitor, now)) {
                sendLicenseNotification(monitor);
                monitor.setLicenseLastNotifiedAt(now);
            }
            repository.save(monitor);
        } catch (Exception ex) {
            monitor.setLicenseStatus(LICENSE_STATUS_ERROR);
            monitor.setLicenseErrorMessage(trimText(ex.getMessage(), 600, "Не удалось обновить лицензию RMS"));
            repository.save(monitor);
            log.warn("RMS license refresh failed for {}: {}", monitor.getRmsAddress(), ex.getMessage());
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
                monitor.setRmsStatusMessage("Ping успешен");
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
        } catch (Exception ex) {
            monitor.setRmsStatus(RMS_STATUS_DOWN);
            monitor.setRmsStatusMessage(trimText(ex.getMessage(), 600, "Не удалось проверить доступность RMS"));
            repository.save(monitor);
            log.warn("RMS network refresh failed for {}: {}", monitor.getRmsAddress(), ex.getMessage());
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
        String serverName = extractFirstText(serverInfoXml, "serverName");
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
        OffsetDateTime earliestExpiration = null;

        NodeList licenses = xml.getElementsByTagName("license");
        for (int index = 0; index < licenses.getLength(); index++) {
            OffsetDateTime expiration = parseFlexibleDate(extractChildText(licenses.item(index), "expiration"));
            if (expiration != null && (earliestExpiration == null || expiration.isBefore(earliestExpiration))) {
                earliestExpiration = expiration;
            }
        }
        if (earliestExpiration == null) {
            NodeList expirationNodes = xml.getElementsByTagName("expiration");
            for (int index = 0; index < expirationNodes.getLength(); index++) {
                OffsetDateTime expiration = parseFlexibleDate(expirationNodes.item(index).getTextContent());
                if (expiration != null && (earliestExpiration == null || expiration.isBefore(earliestExpiration))) {
                    earliestExpiration = expiration;
                }
            }
        }
        if (earliestExpiration == null) {
            throw new IllegalStateException("Не удалось определить дату окончания лицензии");
        }

        int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), earliestExpiration.toLocalDate());
        return new LicenseSnapshot(earliestExpiration, daysLeft);
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
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " для " + url);
        }
        return response.body();
    }

    private String postXml(String url, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofMillis(HTTP_TIMEOUT_MS));
        headers.forEach(builder::header);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " для запроса лицензии");
        }
        return response.body();
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
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
                             OffsetDateTime lastCompletedAt) {
    }

    public record RefreshState(QueueState licenses, QueueState network) {
    }

    public record RefreshRequestResult(String state, RefreshState refreshState) {
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

    private record LicenseSnapshot(OffsetDateTime expiresAt, int daysLeft) {
    }

    private record CommandResult(boolean success, String output) {
    }
}
