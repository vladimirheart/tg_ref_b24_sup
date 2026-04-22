package com.example.panel.service;

import com.example.panel.entity.SslCertificateMonitor;
import com.example.panel.repository.SslCertificateMonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(transactionManager = "monitoringTransactionManager")
public class SslCertificateMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SslCertificateMonitoringService.class);

    public static final String STATUS_OK = "ok";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_DISABLED = "disabled";
    public static final String AVAILABILITY_UP = "up";
    public static final String AVAILABILITY_DOWN = "down";
    public static final String AVAILABILITY_DISABLED = "disabled";
    public static final String AVAILABILITY_UNKNOWN = "unknown";

    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int EXPIRY_NOTIFICATION_THRESHOLD_DAYS = 14;
    private static final int RECHECK_CONNECT_TIMEOUT_MS = 8000;
    private static final int RECHECK_READ_TIMEOUT_MS = 8000;
    private static final String MONITORING_PAGE_URL = "/analytics/certificates";

    private final SslCertificateMonitorRepository repository;
    private final NotificationService notificationService;

    public SslCertificateMonitoringService(SslCertificateMonitorRepository repository,
                                           NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<SslCertificateMonitor> findAll() {
        return repository.findAllByOrderBySiteNameAscIdAsc();
    }

    public SslCertificateMonitor createSite(String siteName, String endpointUrl, Boolean enabled) {
        EndpointTarget target = parseEndpointSafe(endpointUrl);
        if (repository.findByEndpointUrl(target.normalizedUrl()).isPresent()) {
            throw new IllegalArgumentException("Этот сайт уже добавлен в мониторинг");
        }
        SslCertificateMonitor monitor = new SslCertificateMonitor();
        monitor.setSiteName(resolveSiteName(siteName, target.host()));
        monitor.setEndpointUrl(target.normalizedUrl());
        monitor.setHost(target.host());
        monitor.setPort(target.port());
        monitor.setEnabled(enabled == null || enabled);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        monitor.setCreatedAt(now);
        monitor.setUpdatedAt(now);
        monitor.setMonitorStatus(Boolean.TRUE.equals(monitor.getEnabled()) ? STATUS_ERROR : STATUS_DISABLED);
        monitor = repository.save(monitor);
        return refreshMonitor(monitor, false);
    }

    public SslCertificateMonitor updateSite(long id, String siteName, String endpointUrl, Boolean enabled) {
        SslCertificateMonitor monitor = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Сайт мониторинга не найден"));
        Long monitorId = monitor.getId();

        EndpointTarget target = parseEndpointSafe(endpointUrl);
        repository.findByEndpointUrl(target.normalizedUrl()).ifPresent(existing -> {
            if (!existing.getId().equals(monitorId)) {
                throw new IllegalArgumentException("Этот сайт уже добавлен в мониторинг");
            }
        });
        monitor.setSiteName(resolveSiteName(siteName, target.host()));
        monitor.setEndpointUrl(target.normalizedUrl());
        monitor.setHost(target.host());
        monitor.setPort(target.port());
        monitor.setEnabled(enabled == null || enabled);
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        monitor = repository.save(monitor);
        return refreshMonitor(monitor, false);
    }

    public void deleteSite(long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Сайт мониторинга не найден");
        }
        repository.deleteById(id);
    }

    public SslCertificateMonitor refreshById(long id, boolean withNotifications) {
        SslCertificateMonitor monitor = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Сайт мониторинга не найден"));
        return refreshMonitor(monitor, withNotifications);
    }

    public RefreshSummary refreshAll(boolean withNotifications) {
        List<SslCertificateMonitor> monitors = repository.findAllByOrderBySiteNameAscIdAsc();
        int checked = 0;
        int notified = 0;
        for (SslCertificateMonitor monitor : monitors) {
            boolean didNotify = refreshMonitorAndReturnNotifyFlag(monitor, withNotifications);
            checked += 1;
            if (didNotify) {
                notified += 1;
            }
        }
        return new RefreshSummary(monitors.size(), checked, notified);
    }

    public String resolveSeverity(SslCertificateMonitor monitor) {
        if (monitor == null) {
            return STATUS_ERROR;
        }
        String status = normalizeStatus(monitor.getMonitorStatus());
        if (!status.isEmpty()) {
            return status;
        }
        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            return STATUS_DISABLED;
        }
        Integer daysLeft = monitor.getDaysLeft();
        if (daysLeft == null) {
            return STATUS_ERROR;
        }
        return deriveStatusByDays(daysLeft);
    }

    public String resolveAvailability(SslCertificateMonitor monitor) {
        if (monitor == null) {
            return AVAILABILITY_UNKNOWN;
        }
        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            return AVAILABILITY_DISABLED;
        }
        String status = normalizeStatus(monitor.getMonitorStatus());
        if (STATUS_ERROR.equals(status)) {
            return AVAILABILITY_DOWN;
        }
        if (status.isEmpty()) {
            return AVAILABILITY_UNKNOWN;
        }
        return AVAILABILITY_UP;
    }

    private SslCertificateMonitor refreshMonitor(SslCertificateMonitor monitor, boolean withNotifications) {
        refreshMonitorAndReturnNotifyFlag(monitor, withNotifications);
        return monitor;
    }

    private boolean refreshMonitorAndReturnNotifyFlag(SslCertificateMonitor monitor, boolean withNotifications) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        monitor.setLastCheckedAt(now);
        monitor.setUpdatedAt(now);

        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            monitor.setMonitorStatus(STATUS_DISABLED);
            monitor.setErrorMessage(null);
            repository.save(monitor);
            return false;
        }

        try {
            CertificateProbe probe = probeCertificate(monitor.getHost(), Optional.ofNullable(monitor.getPort()).orElse(DEFAULT_HTTPS_PORT));
            monitor.setExpiresAt(probe.expiresAt());
            monitor.setDaysLeft(probe.daysLeft());
            monitor.setErrorMessage(null);
            String status = deriveStatusByDays(probe.daysLeft());
            monitor.setMonitorStatus(status);
            boolean notified = false;
            if (withNotifications && shouldNotify(monitor, now)) {
                sendExpiryNotification(monitor);
                monitor.setLastNotifiedAt(now);
                notified = true;
            }
            repository.save(monitor);
            return notified;
        } catch (Exception ex) {
            monitor.setMonitorStatus(STATUS_ERROR);
            monitor.setErrorMessage(trimErrorMessage(ex.getMessage()));
            monitor.setExpiresAt(null);
            monitor.setDaysLeft(null);
            repository.save(monitor);
            log.warn("SSL monitor check failed for {}:{} ({})", monitor.getHost(), monitor.getPort(), ex.getMessage());
            return false;
        }
    }

    private boolean shouldNotify(SslCertificateMonitor monitor, OffsetDateTime now) {
        Integer daysLeft = monitor.getDaysLeft();
        if (daysLeft == null || daysLeft > EXPIRY_NOTIFICATION_THRESHOLD_DAYS) {
            return false;
        }
        OffsetDateTime lastNotifiedAt = monitor.getLastNotifiedAt();
        if (lastNotifiedAt == null) {
            return true;
        }
        return lastNotifiedAt.isBefore(now.minusHours(23));
    }

    private void sendExpiryNotification(SslCertificateMonitor monitor) {
        Integer daysLeft = monitor.getDaysLeft();
        String siteName = StringUtils.hasText(monitor.getSiteName()) ? monitor.getSiteName().trim() : monitor.getHost();
        String endpoint = monitor.getHost() + ":" + monitor.getPort();
        String expiresAt = monitor.getExpiresAt() != null
            ? monitor.getExpiresAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"))
            : "не определена";

        String message;
        if (daysLeft != null && daysLeft < 0) {
            message = String.format(
                "SSL сертификат сайта \"%s\" (%s) уже истёк. Дата окончания: %s.",
                siteName,
                endpoint,
                expiresAt
            );
        } else {
            int safeDays = daysLeft != null ? daysLeft : EXPIRY_NOTIFICATION_THRESHOLD_DAYS;
            message = String.format(
                "SSL сертификат сайта \"%s\" (%s) истекает через %d дн. Дата окончания: %s.",
                siteName,
                endpoint,
                safeDays,
                expiresAt
            );
        }
        notificationService.notifyAllOperators(message, MONITORING_PAGE_URL, null);
    }

    private CertificateProbe probeCertificate(String host, int port) throws Exception {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
            socket.connect(new InetSocketAddress(host, port), RECHECK_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(RECHECK_READ_TIMEOUT_MS);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            SSLSession session = socket.getSession();
            Certificate[] certificates = session.getPeerCertificates();
            if (certificates == null || certificates.length == 0) {
                throw new IllegalStateException("Сертификат не получен");
            }
            if (!(certificates[0] instanceof X509Certificate x509Certificate)) {
                throw new IllegalStateException("Не удалось прочитать X509 сертификат");
            }
            OffsetDateTime expiresAt = OffsetDateTime.ofInstant(x509Certificate.getNotAfter().toInstant(), ZoneOffset.UTC);
            int daysLeft = calculateDaysLeft(expiresAt);
            return new CertificateProbe(expiresAt, daysLeft);
        }
    }

    private int calculateDaysLeft(OffsetDateTime expiresAt) {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        return (int) ChronoUnit.DAYS.between(todayUtc, expiresAt.toLocalDate());
    }

    private String deriveStatusByDays(int daysLeft) {
        if (daysLeft < 0) {
            return STATUS_EXPIRED;
        }
        if (daysLeft <= 3) {
            return STATUS_CRITICAL;
        }
        if (daysLeft <= EXPIRY_NOTIFICATION_THRESHOLD_DAYS) {
            return STATUS_WARNING;
        }
        return STATUS_OK;
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveSiteName(String siteName, String fallbackHost) {
        if (StringUtils.hasText(siteName)) {
            return siteName.trim();
        }
        return fallbackHost;
    }

    private EndpointTarget parseEndpoint(String rawEndpoint) {
        if (!StringUtils.hasText(rawEndpoint)) {
            throw new IllegalArgumentException("Укажите URL или домен сайта");
        }
        String candidate = rawEndpoint.trim();
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            candidate = "https://" + candidate;
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный URL сайта");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("Не удалось определить host сайта");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_HTTPS_PORT;
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        String normalizedUrl = "https://" + normalizedHost + (port == DEFAULT_HTTPS_PORT ? "" : ":" + port);
        return new EndpointTarget(normalizedUrl, normalizedHost, port);
    }

    private EndpointTarget parseEndpointSafe(String rawEndpoint) {
        if (!StringUtils.hasText(rawEndpoint)) {
            throw new IllegalArgumentException("Укажите URL или домен сайта");
        }
        String candidate = rawEndpoint.trim();
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            candidate = "https://" + candidate;
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный URL сайта");
        }

        String scheme = Optional.ofNullable(uri.getScheme()).orElse("https").toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            throw new IllegalArgumentException("Поддерживаются только HTTPS-адреса");
        }

        HostPort hostPort = extractHostPort(uri);
        if (!StringUtils.hasText(hostPort.host())) {
            throw new IllegalArgumentException(
                "Не удалось определить host сайта. Пример: https://example.com или example.com:8443"
            );
        }

        String host = hostPort.host().trim();
        String asciiHost;
        try {
            asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Некорректный домен сайта");
        }
        if (!StringUtils.hasText(asciiHost)) {
            throw new IllegalArgumentException("Некорректный домен сайта");
        }

        int port = hostPort.port() != null ? hostPort.port() : DEFAULT_HTTPS_PORT;
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Порт сайта должен быть в диапазоне 1-65535");
        }

        String normalizedUrl = "https://" + asciiHost + (port == DEFAULT_HTTPS_PORT ? "" : ":" + port);
        return new EndpointTarget(normalizedUrl, asciiHost, port);
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

        if (value.startsWith("[")) {
            int endBracket = value.indexOf(']');
            if (endBracket > 1) {
                String ipv6Host = value.substring(1, endBracket);
                Integer ipv6Port = null;
                if (endBracket + 1 < value.length() && value.charAt(endBracket + 1) == ':') {
                    ipv6Port = parseSitePort(value.substring(endBracket + 2));
                }
                return new HostPort(ipv6Host, ipv6Port);
            }
        }

        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            String parsedHost = value.substring(0, firstColon);
            Integer parsedPort = parseSitePort(value.substring(firstColon + 1));
            return new HostPort(parsedHost, parsedPort);
        }
        return new HostPort(value, null);
    }

    private Integer parseSitePort(String rawPort) {
        if (!StringUtils.hasText(rawPort)) {
            return null;
        }
        try {
            return Integer.parseInt(rawPort.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректный порт сайта");
        }
    }

    private String trimErrorMessage(String value) {
        if (!StringUtils.hasText(value)) {
            return "Не удалось проверить сертификат";
        }
        String normalized = value.trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }

    private record EndpointTarget(String normalizedUrl, String host, int port) {
    }

    private record HostPort(String host, Integer port) {
    }

    private record CertificateProbe(OffsetDateTime expiresAt, int daysLeft) {
    }

    public record RefreshSummary(int total, int checked, int notified) {
    }
}
