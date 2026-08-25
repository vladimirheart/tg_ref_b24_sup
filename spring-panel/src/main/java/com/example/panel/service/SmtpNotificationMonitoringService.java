package com.example.panel.service;

import com.example.panel.entity.SmtpNotificationMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.SmtpNotificationMonitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SmtpNotificationMonitoringService {

    public static final String STATUS_OK = "ok";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_DISABLED = "disabled";
    public static final String AVAILABILITY_UP = "up";
    public static final String AVAILABILITY_DOWN = "down";
    public static final String AVAILABILITY_DISABLED = "disabled";
    public static final String AVAILABILITY_UNKNOWN = "unknown";

    private static final String MONITOR_KIND = "smtp_notification";
    private static final String CHECK_KIND = "smtp_probe";
    private static final int MAX_SUMMARY_LENGTH = 320;
    private static final int MAX_DETAILS_LENGTH = 1_500;
    private static final Set<String> PROTOCOL_MODES = Set.of("plain", "starttls", "tls");

    private final SmtpNotificationMonitorRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final RelayProbeClient relayProbeClient;

    public SmtpNotificationMonitoringService(SmtpNotificationMonitorRepository repository,
                                             MonitoringCheckHistoryRepository historyRepository) {
        this(repository, historyRepository, new DefaultRelayProbeClient());
    }

    SmtpNotificationMonitoringService(SmtpNotificationMonitorRepository repository,
                                      MonitoringCheckHistoryRepository historyRepository,
                                      RelayProbeClient relayProbeClient) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.relayProbeClient = relayProbeClient;
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public List<SmtpNotificationMonitor> findAll() {
        return repository.findAllByOrderByMonitorNameAscIdAsc();
    }

    public SmtpNotificationMonitor createMonitor(MonitorDraft draft) {
        MonitorConfig config = normalizeDraft(draft, null);
        repository.findByMonitorName(config.monitorName()).ifPresent(existing -> {
            throw new IllegalArgumentException("SMTP monitor with this name already exists");
        });
        repository.findByRelayTarget(config.relayHost(), config.relayPort(), config.protocolMode()).ifPresent(existing -> {
            throw new IllegalArgumentException("This SMTP relay target is already monitored");
        });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SmtpNotificationMonitor item = new SmtpNotificationMonitor();
        item.setMonitorName(config.monitorName());
        item.setRelayHost(config.relayHost());
        item.setRelayPort(config.relayPort());
        item.setProtocolMode(config.protocolMode());
        item.setConnectTimeoutMs(config.connectTimeoutMs());
        item.setEnabled(config.enabled());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setLastStatus(Boolean.TRUE.equals(item.getEnabled()) ? STATUS_CRITICAL : STATUS_DISABLED);
        item.setLastSummary(Boolean.TRUE.equals(item.getEnabled())
            ? "SMTP monitor created and waiting for the first probe"
            : "Monitoring disabled");
        repository.save(item);
        return refreshMonitor(item);
    }

    public SmtpNotificationMonitor updateMonitor(long monitorId, MonitorDraft draft) {
        SmtpNotificationMonitor item = repository.findById(monitorId)
            .orElseThrow(() -> new IllegalArgumentException("SMTP monitor not found"));
        MonitorConfig config = normalizeDraft(draft, item);
        repository.findByMonitorName(config.monitorName()).ifPresent(existing -> {
            if (!existing.getId().equals(item.getId())) {
                throw new IllegalArgumentException("SMTP monitor with this name already exists");
            }
        });
        repository.findByRelayTarget(config.relayHost(), config.relayPort(), config.protocolMode()).ifPresent(existing -> {
            if (!existing.getId().equals(item.getId())) {
                throw new IllegalArgumentException("This SMTP relay target is already monitored");
            }
        });

        item.setMonitorName(config.monitorName());
        item.setRelayHost(config.relayHost());
        item.setRelayPort(config.relayPort());
        item.setProtocolMode(config.protocolMode());
        item.setConnectTimeoutMs(config.connectTimeoutMs());
        item.setEnabled(config.enabled());
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(item);
        return refreshMonitor(item);
    }

    public void deleteMonitor(long monitorId) {
        if (!repository.existsById(Long.valueOf(monitorId))) {
            throw new IllegalArgumentException("SMTP monitor not found");
        }
        repository.deleteById(Long.valueOf(monitorId));
    }

    public SmtpNotificationMonitor refreshById(long monitorId) {
        SmtpNotificationMonitor item = repository.findById(Long.valueOf(monitorId))
            .orElseThrow(() -> new IllegalArgumentException("SMTP monitor not found"));
        return refreshMonitor(item);
    }

    public RefreshSummary refreshAll() {
        List<SmtpNotificationMonitor> monitors = repository.findAllByOrderByMonitorNameAscIdAsc();
        int checked = 0;
        for (SmtpNotificationMonitor monitor : monitors) {
            refreshMonitor(monitor);
            checked++;
        }
        return new RefreshSummary(monitors.size(), checked);
    }

    public List<MonitoringCheckHistoryRepository.HistoryEntry> loadHistory(long monitorId, int limit) {
        if (!repository.existsById(Long.valueOf(monitorId))) {
            throw new IllegalArgumentException("SMTP monitor not found");
        }
        return historyRepository.findRecent(MONITOR_KIND, monitorId, limit);
    }

    public String resolveSeverity(SmtpNotificationMonitor item) {
        if (item == null) {
            return STATUS_CRITICAL;
        }
        String normalized = normalizeStatus(item.getLastStatus());
        if (!normalized.isEmpty()) {
            return normalized;
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return STATUS_DISABLED;
        }
        return STATUS_CRITICAL;
    }

    public String resolveAvailability(SmtpNotificationMonitor item) {
        if (item == null) {
            return AVAILABILITY_UNKNOWN;
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return AVAILABILITY_DISABLED;
        }
        return STATUS_OK.equals(resolveSeverity(item)) ? AVAILABILITY_UP : AVAILABILITY_DOWN;
    }

    public AvailabilityOverview buildAvailabilityOverview(List<SmtpNotificationMonitor> monitors) {
        int total = 0;
        int up = 0;
        int down = 0;
        int disabled = 0;
        int unknown = 0;
        if (monitors != null) {
            for (SmtpNotificationMonitor monitor : monitors) {
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
        double availabilityPercent = active == 0 ? 0d : up * 100.0d / active;
        return new AvailabilityOverview(total, up, down, unknown, disabled, Math.round(availabilityPercent * 10.0d) / 10.0d);
    }

    private SmtpNotificationMonitor refreshMonitor(SmtpNotificationMonitor item) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long startedAtNs = System.nanoTime();
        item.setLastCheckedAt(now);
        item.setUpdatedAt(now);

        if (!Boolean.TRUE.equals(item.getEnabled())) {
            item.setLastStatus(STATUS_DISABLED);
            item.setLastSummary("Monitoring disabled");
            item.setLastErrorMessage(null);
            repository.save(item);
            recordHistory(item.getId(), STATUS_DISABLED, item.getLastSummary(), buildDetails(item), elapsedMillis(startedAtNs), now);
            return item;
        }

        try {
            RelayProbeResult result = relayProbeClient.probe(
                item.getRelayHost(),
                item.getRelayPort() != null ? item.getRelayPort() : 25,
                normalizeProtocolMode(item.getProtocolMode()),
                item.getConnectTimeoutMs() != null ? item.getConnectTimeoutMs() : 5_000
            );
            item.setLastConnectedAt(now);
            item.setLastBanner(trim(result.banner(), 220));
            item.setLastTlsProtocol(trim(result.tlsProtocol(), 120));
            item.setLastTlsCipherSuite(trim(result.tlsCipherSuite(), 200));
            item.setLastStatus(STATUS_OK);
            item.setLastErrorMessage(null);
            item.setLastSummary(trim(buildSuccessSummary(item, result), MAX_SUMMARY_LENGTH));
        } catch (Exception ex) {
            item.setLastStatus(STATUS_CRITICAL);
            item.setLastSummary(trim("SMTP probe failed for " + item.getRelayHost() + ":" + item.getRelayPort(), MAX_SUMMARY_LENGTH));
            item.setLastErrorMessage(trimError(ex.getMessage()));
        }

        repository.save(item);
        recordHistory(item.getId(), item.getLastStatus(), item.getLastSummary(), buildDetails(item), elapsedMillis(startedAtNs), now);
        return item;
    }

    private String buildSuccessSummary(SmtpNotificationMonitor item,
                                       RelayProbeResult result) {
        List<String> parts = new ArrayList<>();
        parts.add("smtp ok");
        parts.add(item.getRelayHost() + ":" + item.getRelayPort());
        parts.add("mode=" + normalizeProtocolMode(item.getProtocolMode()));
        if (StringUtils.hasText(result.banner())) {
            parts.add("banner=" + trim(result.banner(), 80));
        }
        if (StringUtils.hasText(result.tlsProtocol())) {
            parts.add("tls=" + result.tlsProtocol());
        }
        if (result.durationMs() != null) {
            parts.add("duration=" + result.durationMs() + " ms");
        }
        return String.join("; ", parts);
    }

    private MonitorConfig normalizeDraft(MonitorDraft draft,
                                         SmtpNotificationMonitor existing) {
        if (draft == null) {
            throw new IllegalArgumentException("SMTP monitor payload is required");
        }
        String monitorName = requireText(draft.monitorName(), "Specify monitor name");
        String relayHost = normalizeHost(requireText(draft.relayHost(), "Specify relay host"));
        int relayPort = normalizePort(draft.relayPort());
        String protocolMode = normalizeProtocolMode(draft.protocolMode());
        boolean enabled = draft.enabled() == null
            ? existing == null || Boolean.TRUE.equals(existing.getEnabled())
            : draft.enabled();
        int connectTimeoutMs = normalizeTimeout(draft.connectTimeoutMs());
        return new MonitorConfig(monitorName, relayHost, relayPort, protocolMode, connectTimeoutMs, enabled);
    }

    private String normalizeHost(String value) {
        try {
            String normalized = IDN.toASCII(value.trim(), IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(normalized)) {
                throw new IllegalArgumentException("Relay host must not be empty");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid relay host");
        }
    }

    private int normalizePort(Integer relayPort) {
        int value = relayPort == null ? 25 : relayPort;
        if (value < 1 || value > 65_535) {
            throw new IllegalArgumentException("Relay port must be between 1 and 65535");
        }
        return value;
    }

    private int normalizeTimeout(Integer connectTimeoutMs) {
        int value = connectTimeoutMs == null ? 5_000 : connectTimeoutMs;
        if (value < 1_000 || value > 30_000) {
            throw new IllegalArgumentException("Connect timeout must be between 1000 and 30000 ms");
        }
        return value;
    }

    private String normalizeProtocolMode(String value) {
        String normalized = StringUtils.hasText(value)
            ? value.trim().toLowerCase(Locale.ROOT)
            : "starttls";
        if (!PROTOCOL_MODES.contains(normalized)) {
            throw new IllegalArgumentException("protocol_mode must be one of: plain, starttls, tls");
        }
        return normalized;
    }

    private String requireText(String value,
                               String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String buildDetails(SmtpNotificationMonitor item) {
        List<String> details = new ArrayList<>();
        details.add("relay=" + item.getRelayHost() + ":" + item.getRelayPort());
        details.add("mode=" + normalizeProtocolMode(item.getProtocolMode()));
        if (item.getConnectTimeoutMs() != null) {
            details.add("timeout_ms=" + item.getConnectTimeoutMs());
        }
        if (StringUtils.hasText(item.getLastBanner())) {
            details.add("banner=" + item.getLastBanner().trim());
        }
        if (StringUtils.hasText(item.getLastTlsProtocol())) {
            details.add("tls_protocol=" + item.getLastTlsProtocol().trim());
        }
        if (StringUtils.hasText(item.getLastTlsCipherSuite())) {
            details.add("tls_cipher=" + item.getLastTlsCipherSuite().trim());
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

    private String trim(String value,
                        int limit) {
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

    public interface RelayProbeClient {
        RelayProbeResult probe(String host,
                               int port,
                               String protocolMode,
                               int timeoutMs) throws Exception;
    }

    private static final class DefaultRelayProbeClient implements RelayProbeClient {

        private static final SSLSocketFactory SSL_SOCKET_FACTORY = (SSLSocketFactory) SSLSocketFactory.getDefault();

        @Override
        public RelayProbeResult probe(String host,
                                      int port,
                                      String protocolMode,
                                      int timeoutMs) throws Exception {
            long startedAtNs = System.nanoTime();
            return switch (protocolMode) {
                case "plain" -> probePlain(host, port, timeoutMs, startedAtNs);
                case "starttls" -> probeStartTls(host, port, timeoutMs, startedAtNs);
                case "tls" -> probeTls(host, port, timeoutMs, startedAtNs);
                default -> throw new IllegalArgumentException("Unsupported protocol mode: " + protocolMode);
            };
        }

        private RelayProbeResult probePlain(String host,
                                            int port,
                                            int timeoutMs,
                                            long startedAtNs) throws Exception {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                BufferedReader reader = reader(socket);
                String banner = readExpectedBanner(reader);
                return new RelayProbeResult(banner, null, null, elapsedMillis(startedAtNs));
            }
        }

        private RelayProbeResult probeStartTls(String host,
                                               int port,
                                               int timeoutMs,
                                               long startedAtNs) throws Exception {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                BufferedReader reader = reader(socket);
                BufferedWriter writer = writer(socket);
                String banner = readExpectedBanner(reader);
                String ehloResponse = executeEhlo(reader, writer);
                if (!ehloResponse.toUpperCase(Locale.ROOT).contains("STARTTLS")) {
                    throw new IllegalStateException("STARTTLS is not advertised by relay");
                }
                writeLine(writer, "STARTTLS");
                String startTlsResponse = readResponse(reader);
                if (!startTlsResponse.startsWith("220")) {
                    throw new IllegalStateException("STARTTLS failed: " + startTlsResponse);
                }
                try (SSLSocket sslSocket = (SSLSocket) SSL_SOCKET_FACTORY.createSocket(socket, host, port, true)) {
                    sslSocket.setUseClientMode(true);
                    sslSocket.setSoTimeout(timeoutMs);
                    sslSocket.startHandshake();
                    return new RelayProbeResult(
                        banner,
                        sslSocket.getSession().getProtocol(),
                        sslSocket.getSession().getCipherSuite(),
                        elapsedMillis(startedAtNs)
                    );
                }
            }
        }

        private RelayProbeResult probeTls(String host,
                                          int port,
                                          int timeoutMs,
                                          long startedAtNs) throws Exception {
            try (SSLSocket sslSocket = (SSLSocket) SSL_SOCKET_FACTORY.createSocket()) {
                sslSocket.connect(new InetSocketAddress(host, port), timeoutMs);
                sslSocket.setSoTimeout(timeoutMs);
                sslSocket.setUseClientMode(true);
                sslSocket.startHandshake();
                BufferedReader reader = reader(sslSocket);
                String banner = readExpectedBanner(reader);
                return new RelayProbeResult(
                    banner,
                    sslSocket.getSession().getProtocol(),
                    sslSocket.getSession().getCipherSuite(),
                    elapsedMillis(startedAtNs)
                );
            }
        }

        private BufferedReader reader(Socket socket) throws IOException {
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        }

        private BufferedWriter writer(Socket socket) throws IOException {
            return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        private String readExpectedBanner(BufferedReader reader) throws IOException {
            String response = readResponse(reader);
            if (!response.startsWith("220")) {
                throw new IllegalStateException("Unexpected SMTP banner: " + response);
            }
            return response;
        }

        private String executeEhlo(BufferedReader reader,
                                   BufferedWriter writer) throws IOException {
            writeLine(writer, "EHLO iguana-monitor");
            String response = readResponse(reader);
            if (!response.startsWith("250")) {
                throw new IllegalStateException("EHLO failed: " + response);
            }
            return response;
        }

        private void writeLine(BufferedWriter writer,
                               String value) throws IOException {
            writer.write(value);
            writer.write("\r\n");
            writer.flush();
        }

        private String readResponse(BufferedReader reader) throws IOException {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                throw new IllegalStateException("Relay closed connection without a response");
            }
            StringBuilder builder = new StringBuilder(firstLine);
            if (firstLine.length() >= 4 && firstLine.charAt(3) == '-') {
                String prefix = firstLine.substring(0, 3);
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(" | ").append(line);
                    if (line.startsWith(prefix + " ")) {
                        break;
                    }
                }
            }
            return builder.toString();
        }

        private long elapsedMillis(long startedAtNs) {
            return Math.max(0L, (System.nanoTime() - startedAtNs) / 1_000_000L);
        }
    }

    public record MonitorDraft(String monitorName,
                               String relayHost,
                               Integer relayPort,
                               String protocolMode,
                               Integer connectTimeoutMs,
                               Boolean enabled) {
    }

    private record MonitorConfig(String monitorName,
                                 String relayHost,
                                 int relayPort,
                                 String protocolMode,
                                 int connectTimeoutMs,
                                 boolean enabled) {
    }

    public record RelayProbeResult(String banner,
                                   String tlsProtocol,
                                   String tlsCipherSuite,
                                   Long durationMs) {
    }

    public record RefreshSummary(int total,
                                 int checked) {
    }

    public record AvailabilityOverview(int total,
                                       int up,
                                       int down,
                                       int unknown,
                                       int disabled,
                                       double availabilityPercent) {
    }
}
