package com.example.panel.service;

import com.example.panel.entity.PublicIngressMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.PublicIngressMonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PublicIngressMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(PublicIngressMonitoringService.class);

    public static final String STATUS_OK = "ok";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_DISABLED = "disabled";
    public static final String STATUS_SKIPPED = "skipped";
    public static final String AVAILABILITY_UP = "up";
    public static final String AVAILABILITY_DOWN = "down";
    public static final String AVAILABILITY_DISABLED = "disabled";
    public static final String AVAILABILITY_UNKNOWN = "unknown";

    private static final String MONITOR_KIND = "public_ingress";
    private static final String CHECK_KIND = "ingress_probe";
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int TLS_WARNING_DAYS = 14;
    private static final int TLS_CRITICAL_DAYS = 3;
    private static final int MAX_SUMMARY_LENGTH = 320;
    private static final int MAX_DETAILS_LENGTH = 1_500;

    private final PublicIngressMonitorRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final DnsResolver dnsResolver;
    private final HttpProbeClient httpProbeClient;
    private final TlsProbeClient tlsProbeClient;

    @Autowired
    public PublicIngressMonitoringService(PublicIngressMonitorRepository repository,
                                          MonitoringCheckHistoryRepository historyRepository) {
        this(
            repository,
            historyRepository,
            new SystemDnsResolver(),
            new JdkHttpProbeClient(),
            new DefaultTlsProbeClient()
        );
    }

    PublicIngressMonitoringService(PublicIngressMonitorRepository repository,
                                   MonitoringCheckHistoryRepository historyRepository,
                                   DnsResolver dnsResolver,
                                   HttpProbeClient httpProbeClient,
                                   TlsProbeClient tlsProbeClient) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.dnsResolver = dnsResolver;
        this.httpProbeClient = httpProbeClient;
        this.tlsProbeClient = tlsProbeClient;
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public List<PublicIngressMonitor> findAll() {
        return repository.findAllByOrderByMonitorNameAscIdAsc();
    }

    public PublicIngressMonitor createMonitor(MonitorDraft draft) {
        EndpointDraft endpoint = normalizeDraft(draft, null);
        repository.findByMonitorName(endpoint.monitorName()).ifPresent(existing -> {
            throw new IllegalArgumentException("Монитор с таким именем уже существует");
        });
        repository.findByEndpointUrl(endpoint.endpointUrl()).ifPresent(existing -> {
            throw new IllegalArgumentException("Этот endpoint уже добавлен в monitoring");
        });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PublicIngressMonitor item = new PublicIngressMonitor();
        item.setMonitorName(endpoint.monitorName());
        item.setEndpointUrl(endpoint.endpointUrl());
        item.setScheme(endpoint.scheme());
        item.setHost(endpoint.host());
        item.setPort(endpoint.port());
        item.setExpectedHttpStatus(endpoint.expectedHttpStatus());
        item.setEnabled(endpoint.enabled());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setLastStatus(Boolean.TRUE.equals(item.getEnabled()) ? STATUS_WARNING : STATUS_DISABLED);
        item.setLastSummary(Boolean.TRUE.equals(item.getEnabled())
            ? "Public ingress monitor создан и ждёт первой проверки"
            : "Мониторинг отключен");
        repository.save(item);
        return refreshMonitor(item);
    }

    public PublicIngressMonitor updateMonitor(long monitorId, MonitorDraft draft) {
        PublicIngressMonitor item = repository.findById(monitorId)
            .orElseThrow(() -> new IllegalArgumentException("Public ingress monitor не найден"));
        EndpointDraft endpoint = normalizeDraft(draft, item);
        repository.findByMonitorName(endpoint.monitorName()).ifPresent(existing -> {
            if (!existing.getId().equals(item.getId())) {
                throw new IllegalArgumentException("Монитор с таким именем уже существует");
            }
        });
        repository.findByEndpointUrl(endpoint.endpointUrl()).ifPresent(existing -> {
            if (!existing.getId().equals(item.getId())) {
                throw new IllegalArgumentException("Этот endpoint уже добавлен в monitoring");
            }
        });

        item.setMonitorName(endpoint.monitorName());
        item.setEndpointUrl(endpoint.endpointUrl());
        item.setScheme(endpoint.scheme());
        item.setHost(endpoint.host());
        item.setPort(endpoint.port());
        item.setExpectedHttpStatus(endpoint.expectedHttpStatus());
        item.setEnabled(endpoint.enabled());
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(item);
        return refreshMonitor(item);
    }

    public void deleteMonitor(long monitorId) {
        if (!repository.existsById(monitorId)) {
            throw new IllegalArgumentException("Public ingress monitor не найден");
        }
        repository.deleteById(monitorId);
    }

    public PublicIngressMonitor refreshById(long monitorId) {
        PublicIngressMonitor item = repository.findById(monitorId)
            .orElseThrow(() -> new IllegalArgumentException("Public ingress monitor не найден"));
        return refreshMonitor(item);
    }

    public RefreshSummary refreshAll() {
        List<PublicIngressMonitor> monitors = repository.findAllByOrderByMonitorNameAscIdAsc();
        int checked = 0;
        for (PublicIngressMonitor monitor : monitors) {
            refreshMonitor(monitor);
            checked++;
        }
        return new RefreshSummary(monitors.size(), checked);
    }

    public List<MonitoringCheckHistoryRepository.HistoryEntry> loadHistory(long monitorId, int limit) {
        if (!repository.existsById(monitorId)) {
            throw new IllegalArgumentException("Public ingress monitor не найден");
        }
        return historyRepository.findRecent(MONITOR_KIND, monitorId, limit);
    }

    public String resolveSeverity(PublicIngressMonitor item) {
        if (item == null) {
            return STATUS_ERROR;
        }
        String normalized = normalizeStatus(item.getLastStatus());
        if (!normalized.isEmpty()) {
            return normalized;
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return STATUS_DISABLED;
        }
        return STATUS_WARNING;
    }

    public String resolveAvailability(PublicIngressMonitor item) {
        if (item == null) {
            return AVAILABILITY_UNKNOWN;
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return AVAILABILITY_DISABLED;
        }
        String status = resolveSeverity(item);
        if (STATUS_OK.equals(status) || STATUS_WARNING.equals(status)) {
            return AVAILABILITY_UP;
        }
        if (STATUS_CRITICAL.equals(status) || STATUS_ERROR.equals(status)) {
            return AVAILABILITY_DOWN;
        }
        return AVAILABILITY_UNKNOWN;
    }

    public AvailabilityOverview buildAvailabilityOverview(List<PublicIngressMonitor> monitors) {
        int total = 0;
        int up = 0;
        int down = 0;
        int unknown = 0;
        int disabled = 0;
        if (monitors != null) {
            for (PublicIngressMonitor monitor : monitors) {
                total++;
                String availability = resolveAvailability(monitor);
                if (AVAILABILITY_UP.equals(availability)) {
                    up++;
                } else if (AVAILABILITY_DOWN.equals(availability)) {
                    down++;
                } else if (AVAILABILITY_DISABLED.equals(availability)) {
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

    private PublicIngressMonitor refreshMonitor(PublicIngressMonitor item) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long startedAtNs = System.nanoTime();
        item.setLastCheckedAt(now);
        item.setUpdatedAt(now);

        if (!Boolean.TRUE.equals(item.getEnabled())) {
            item.setLastStatus(STATUS_DISABLED);
            item.setLastSummary("Мониторинг отключен");
            item.setLastErrorMessage(null);
            repository.save(item);
            recordHistory(item.getId(), STATUS_DISABLED, item.getLastSummary(), buildDetails(item), elapsedMillis(startedAtNs), now);
            return item;
        }

        String overallStatus = STATUS_OK;
        List<String> summary = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            DnsProbeResult dns = dnsResolver.resolve(item.getHost());
            item.setLastDnsResolvedAt(now);
            item.setLastDnsAddresses(String.join(", ", dns.addresses()));
            summary.add("dns ok");
        } catch (Exception ex) {
            item.setLastDnsResolvedAt(null);
            item.setLastDnsAddresses(null);
            overallStatus = elevate(overallStatus, STATUS_CRITICAL);
            String message = "dns failed: " + trimError(ex.getMessage());
            summary.add(message);
            errors.add(message);
        }

        try {
            HttpProbeResult http = httpProbeClient.probe(URI.create(item.getEndpointUrl()), item.getExpectedHttpStatus());
            item.setLastHttpStatus(http.statusCode());
            item.setLastHttpDurationMs(http.durationMs());
            item.setLastHttpCheckedAt(now);
            String httpStatus = evaluateHttpStatus(http.statusCode(), item.getExpectedHttpStatus());
            overallStatus = elevate(overallStatus, httpStatus);
            summary.add("http " + http.statusCode() + " (" + http.durationMs() + " ms)");
        } catch (Exception ex) {
            item.setLastHttpCheckedAt(now);
            item.setLastHttpStatus(null);
            item.setLastHttpDurationMs(null);
            overallStatus = elevate(overallStatus, STATUS_CRITICAL);
            String message = "http failed: " + trimError(ex.getMessage());
            summary.add(message);
            errors.add(message);
        }

        if ("https".equalsIgnoreCase(item.getScheme())) {
            try {
                TlsProbeResult tls = tlsProbeClient.probe(item.getHost(), item.getPort() != null ? item.getPort() : DEFAULT_HTTPS_PORT);
                item.setLastTlsCheckedAt(now);
                item.setLastTlsExpiresAt(tls.expiresAt());
                item.setLastTlsDaysLeft(tls.daysLeft());
                String tlsStatus = evaluateTlsStatus(tls.daysLeft());
                overallStatus = elevate(overallStatus, tlsStatus);
                if (STATUS_WARNING.equals(tlsStatus) || STATUS_CRITICAL.equals(tlsStatus)) {
                    summary.add("tls expires in " + tls.daysLeft() + " d");
                } else {
                    summary.add("tls ok");
                }
            } catch (Exception ex) {
                item.setLastTlsCheckedAt(now);
                item.setLastTlsExpiresAt(null);
                item.setLastTlsDaysLeft(null);
                overallStatus = elevate(overallStatus, STATUS_CRITICAL);
                String message = "tls failed: " + trimError(ex.getMessage());
                summary.add(message);
                errors.add(message);
            }
        } else {
            item.setLastTlsCheckedAt(null);
            item.setLastTlsExpiresAt(null);
            item.setLastTlsDaysLeft(null);
            summary.add("tls skipped (scheme=http)");
        }

        item.setLastStatus(overallStatus);
        item.setLastSummary(trim(String.join("; ", summary), MAX_SUMMARY_LENGTH));
        item.setLastErrorMessage(errors.isEmpty() ? null : trim(String.join("; ", errors), MAX_SUMMARY_LENGTH));
        repository.save(item);
        recordHistory(item.getId(), overallStatus, item.getLastSummary(), buildDetails(item), elapsedMillis(startedAtNs), now);
        return item;
    }

    private EndpointDraft normalizeDraft(MonitorDraft draft, PublicIngressMonitor existing) {
        if (draft == null) {
            throw new IllegalArgumentException("Параметры monitor-а не переданы");
        }
        String monitorName = requireText(draft.monitorName(), "Укажите имя monitor-а");
        EndpointTarget target = parseEndpoint(requireText(draft.endpointUrl(), "Укажите public endpoint URL"));
        boolean enabled = draft.enabled() == null
            ? existing == null || Boolean.TRUE.equals(existing.getEnabled())
            : draft.enabled();
        Integer expectedHttpStatus = normalizeExpectedStatus(draft.expectedHttpStatus());
        return new EndpointDraft(
            monitorName,
            target.normalizedUrl(),
            target.scheme(),
            target.host(),
            target.port(),
            expectedHttpStatus,
            enabled
        );
    }

    private Integer normalizeExpectedStatus(Integer expectedHttpStatus) {
        if (expectedHttpStatus == null) {
            return null;
        }
        if (expectedHttpStatus < 100 || expectedHttpStatus > 599) {
            throw new IllegalArgumentException("expected_http_status должен быть в диапазоне 100-599");
        }
        return expectedHttpStatus;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private EndpointTarget parseEndpoint(String rawEndpoint) {
        URI uri;
        try {
            uri = URI.create(rawEndpoint.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный public endpoint URL");
        }

        String scheme = Optional.ofNullable(uri.getScheme()).orElse("").trim().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Поддерживаются только http:// и https:// endpoint-ы");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("Не удалось определить host endpoint-а");
        }

        String asciiHost;
        try {
            asciiHost = IDN.toASCII(host.trim(), IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Некорректный host public endpoint-а");
        }
        if (!StringUtils.hasText(asciiHost)) {
            throw new IllegalArgumentException("Некорректный host public endpoint-а");
        }

        int port;
        if (uri.getPort() > 0) {
            port = uri.getPort();
        } else {
            port = "https".equals(scheme) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Порт public endpoint-а должен быть в диапазоне 1-65535");
        }

        String path = uri.getRawPath();
        if (!StringUtils.hasText(path)) {
            path = "/";
        }
        String query = uri.getRawQuery();
        String normalizedUrl = scheme + "://" + asciiHost;
        if (("https".equals(scheme) && port != DEFAULT_HTTPS_PORT) || ("http".equals(scheme) && port != DEFAULT_HTTP_PORT)) {
            normalizedUrl += ":" + port;
        }
        normalizedUrl += path;
        if (StringUtils.hasText(query)) {
            normalizedUrl += "?" + query;
        }
        return new EndpointTarget(normalizedUrl, scheme, asciiHost, port);
    }

    private String evaluateHttpStatus(int actualStatus, Integer expectedHttpStatus) {
        if (expectedHttpStatus != null) {
            return actualStatus == expectedHttpStatus ? STATUS_OK : STATUS_CRITICAL;
        }
        return actualStatus >= 200 && actualStatus < 400 ? STATUS_OK : STATUS_CRITICAL;
    }

    private String evaluateTlsStatus(int daysLeft) {
        if (daysLeft < 0) {
            return STATUS_CRITICAL;
        }
        if (daysLeft <= TLS_CRITICAL_DAYS) {
            return STATUS_CRITICAL;
        }
        if (daysLeft <= TLS_WARNING_DAYS) {
            return STATUS_WARNING;
        }
        return STATUS_OK;
    }

    private String elevate(String current, String candidate) {
        Map<String, Integer> rank = Map.of(
            STATUS_DISABLED, 0,
            STATUS_SKIPPED, 0,
            STATUS_OK, 1,
            STATUS_WARNING, 2,
            STATUS_CRITICAL, 3,
            STATUS_ERROR, 4
        );
        String normalizedCurrent = normalizeStatus(current);
        String normalizedCandidate = normalizeStatus(candidate);
        return rank.getOrDefault(normalizedCandidate, 4) > rank.getOrDefault(normalizedCurrent, 4)
            ? normalizedCandidate
            : normalizedCurrent;
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String buildDetails(PublicIngressMonitor item) {
        List<String> details = new ArrayList<>();
        details.add("endpoint=" + item.getEndpointUrl());
        if (StringUtils.hasText(item.getLastDnsAddresses())) {
            details.add("dns=" + item.getLastDnsAddresses().trim());
        }
        if (item.getLastHttpStatus() != null) {
            details.add("http_status=" + item.getLastHttpStatus());
        }
        if (item.getLastHttpDurationMs() != null) {
            details.add("http_duration_ms=" + item.getLastHttpDurationMs());
        }
        if (item.getLastTlsExpiresAt() != null) {
            details.add("tls_expires_at=" + item.getLastTlsExpiresAt());
        }
        if (item.getLastTlsDaysLeft() != null) {
            details.add("tls_days_left=" + item.getLastTlsDaysLeft());
        }
        if (StringUtils.hasText(item.getLastErrorMessage())) {
            details.add("error=" + item.getLastErrorMessage().trim());
        }
        return trim(String.join("; ", details), MAX_DETAILS_LENGTH);
    }

    private void recordHistory(Long monitorId,
                               String status,
                               String summary,
                               String details,
                               Long durationMs,
                               OffsetDateTime createdAt) {
        if (monitorId == null) {
            return;
        }
        historyRepository.record(
            MONITOR_KIND,
            monitorId,
            CHECK_KIND,
            status,
            trim(summary, MAX_SUMMARY_LENGTH),
            trim(details, MAX_DETAILS_LENGTH),
            null,
            durationMs,
            createdAt
        );
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown error";
        }
        return trim(message.trim(), 220);
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

    private long elapsedMillis(long startedAtNs) {
        return Math.max(0L, (System.nanoTime() - startedAtNs) / 1_000_000L);
    }

    public interface DnsResolver {
        DnsProbeResult resolve(String host) throws Exception;
    }

    public interface HttpProbeClient {
        HttpProbeResult probe(URI endpoint, Integer expectedHttpStatus) throws Exception;
    }

    public interface TlsProbeClient {
        TlsProbeResult probe(String host, int port) throws Exception;
    }

    private static final class SystemDnsResolver implements DnsResolver {
        @Override
        public DnsProbeResult resolve(String host) throws Exception {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw new IllegalStateException("resolver returned no addresses");
            }
            List<String> values = List.of(addresses).stream()
                .map(InetAddress::getHostAddress)
                .filter(StringUtils::hasText)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
            if (values.isEmpty()) {
                throw new IllegalStateException("resolver returned empty addresses");
            }
            return new DnsProbeResult(values);
        }
    }

    private static final class JdkHttpProbeClient implements HttpProbeClient {
        private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        @Override
        public HttpProbeResult probe(URI endpoint, Integer expectedHttpStatus) throws Exception {
            long startedAtNs = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "iguana-public-ingress-monitor/1.0")
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return new HttpProbeResult(response.statusCode(), Math.max(0L, (System.nanoTime() - startedAtNs) / 1_000_000L));
        }
    }

    private static final class DefaultTlsProbeClient implements TlsProbeClient {
        @Override
        public TlsProbeResult probe(String host, int port) throws Exception {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
                socket.connect(new InetSocketAddress(host, port), 8_000);
                socket.setSoTimeout(8_000);
                SSLParameters parameters = socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(parameters);
                socket.startHandshake();
                SSLSession session = socket.getSession();
                Certificate[] certificates = session.getPeerCertificates();
                if (certificates == null || certificates.length == 0) {
                    throw new IllegalStateException("certificate not received");
                }
                if (!(certificates[0] instanceof X509Certificate x509Certificate)) {
                    throw new IllegalStateException("peer certificate is not X509");
                }
                OffsetDateTime expiresAt = OffsetDateTime.ofInstant(x509Certificate.getNotAfter().toInstant(), ZoneOffset.UTC);
                int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), expiresAt.toLocalDate());
                return new TlsProbeResult(expiresAt, daysLeft);
            }
        }
    }

    public record MonitorDraft(String monitorName,
                               String endpointUrl,
                               Integer expectedHttpStatus,
                               Boolean enabled) {
    }

    private record EndpointDraft(String monitorName,
                                 String endpointUrl,
                                 String scheme,
                                 String host,
                                 int port,
                                 Integer expectedHttpStatus,
                                 boolean enabled) {
    }

    private record EndpointTarget(String normalizedUrl, String scheme, String host, int port) {
    }

    public record DnsProbeResult(List<String> addresses) {
    }

    public record HttpProbeResult(int statusCode, long durationMs) {
    }

    public record TlsProbeResult(OffsetDateTime expiresAt, int daysLeft) {
    }

    public record RefreshSummary(int total, int checked) {
    }

    public record AvailabilityOverview(int total,
                                       int up,
                                       int down,
                                       int unknown,
                                       int disabled,
                                       double availabilityPercent) {
    }
}
