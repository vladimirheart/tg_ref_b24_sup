package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ProviderDeliveryLedgerEntry;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.ProviderDeliveryLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class ProviderDeliveryAlertingService {

    public static final String MONITOR_KIND = "provider_delivery_alerting";
    public static final String CHECK_KIND = "delivery_burn_rate";

    public static final String STATUS_OK = "ok";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_DISABLED = "disabled";

    private static final Set<String> MONITORED_PLATFORMS = Set.of("telegram", "vk", "max");
    private static final String SIGNAL_TYPE = "provider_delivery";
    private static final String INCIDENT_SOURCE = "provider_delivery_alerting";
    private static final String INCIDENT_ACTOR = "system";

    private static final Duration SHORT_WINDOW = Duration.ofMinutes(15);
    private static final Duration LONG_WINDOW = Duration.ofHours(6);

    private static final int RECENT_SCAN_LIMIT = 500;
    private static final int HISTORY_LIMIT = 30;
    private static final int MAX_SUMMARY_LENGTH = 320;
    private static final int MAX_DETAILS_LENGTH = 1_500;

    private static final AlertThresholds FAILURE_THRESHOLDS = new AlertThresholds(
        0.05d,
        6,
        12,
        6.0d,
        3.0d,
        14.0d,
        6.0d
    );

    private static final AlertThresholds RATE_LIMIT_THRESHOLDS = new AlertThresholds(
        0.02d,
        4,
        8,
        4.0d,
        2.0d,
        10.0d,
        5.0d
    );

    private final ProviderDeliveryLedgerRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final ChannelRepository channelRepository;
    private final IncidentService incidentService;
    private final Clock clock;

    @Autowired
    public ProviderDeliveryAlertingService(ProviderDeliveryLedgerRepository repository,
                                           MonitoringCheckHistoryRepository historyRepository,
                                           ChannelRepository channelRepository,
                                           IncidentService incidentService) {
        this(repository, historyRepository, channelRepository, incidentService, Clock.systemUTC());
    }

    ProviderDeliveryAlertingService(ProviderDeliveryLedgerRepository repository,
                                    MonitoringCheckHistoryRepository historyRepository,
                                    ChannelRepository channelRepository,
                                    IncidentService incidentService,
                                    Clock clock) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.channelRepository = channelRepository;
        this.incidentService = incidentService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public OverviewSnapshot buildOverview() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return evaluate(now, false);
    }

    public RefreshSummary refreshAll() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OverviewSnapshot snapshot = evaluate(now, true);
        return new RefreshSummary(snapshot.items().size(), snapshot.overview().actionableChannels());
    }

    public List<MonitoringCheckHistoryRepository.HistoryEntry> loadHistory(long channelId, int limit) {
        loadMonitoredChannel(channelId);
        return historyRepository.findRecent(MONITOR_KIND, channelId, limit);
    }

    private OverviewSnapshot evaluate(OffsetDateTime now, boolean recordHistory) {
        List<Channel> channels = loadMonitoredChannels();
        Map<Long, ProviderDeliveryLedgerRepository.ChannelAttemptStats> shortStatsByChannel =
            indexByChannel(repository.summarizeByChannelSince(now.minus(SHORT_WINDOW)));
        Map<Long, ProviderDeliveryLedgerRepository.ChannelAttemptStats> longStatsByChannel =
            indexByChannel(repository.summarizeByChannelSince(now.minus(LONG_WINDOW)));
        Map<Long, ProviderDeliveryLedgerEntry> latestByChannel = latestByChannel(repository.findRecent(RECENT_SCAN_LIMIT));
        Map<String, Map<String, Object>> activeIncidentsBySignalKey = loadActiveIncidents();
        Map<Long, PreviousAlertState> previousStatesByChannel = recordHistory ? loadPreviousStates(channels) : Map.of();

        List<ChannelAlertSnapshot> items = new ArrayList<>();
        for (Channel channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            ChannelAlertSnapshot item = buildChannelSnapshot(
                channel,
                shortStatsByChannel.get(channel.getId()),
                longStatsByChannel.get(channel.getId()),
                latestByChannel.get(channel.getId()),
                activeIncidentsBySignalKey,
                now
            );
            items.add(item);
            if (recordHistory) {
                recordHistory(item, now);
                syncIncidents(item, previousStatesByChannel.getOrDefault(channel.getId(), PreviousAlertState.empty()), activeIncidentsBySignalKey);
            }
        }

        items.sort(Comparator
            .comparingInt((ChannelAlertSnapshot item) -> severityRank(item.alertStatus()))
            .thenComparing(item -> !item.active())
            .thenComparing(item -> normalize(item.platform()))
            .thenComparing(item -> normalize(item.channelName()))
            .thenComparing(item -> item.channelId() == null ? Long.MAX_VALUE : item.channelId()));

        return new OverviewSnapshot(now, buildOverview(items), items);
    }

    private ChannelAlertSnapshot buildChannelSnapshot(Channel channel,
                                                      ProviderDeliveryLedgerRepository.ChannelAttemptStats shortStats,
                                                      ProviderDeliveryLedgerRepository.ChannelAttemptStats longStats,
                                                      ProviderDeliveryLedgerEntry latest,
                                                      Map<String, Map<String, Object>> activeIncidentsBySignalKey,
                                                      OffsetDateTime now) {
        boolean active = Boolean.TRUE.equals(channel.getActive());
        BurnRateSignal failureSignal = evaluateSignal(
            channel,
            "delivery_failures",
            "Sustained provider delivery failures",
            active,
            shortStats,
            longStats,
            safeLong(shortStats != null ? shortStats.failureCount() : null),
            safeLong(longStats != null ? longStats.failureCount() : null),
            FAILURE_THRESHOLDS,
            latest
        );
        BurnRateSignal rateLimitSignal = evaluateSignal(
            channel,
            "rate_limit_pressure",
            "Provider rate-limit pressure",
            active,
            shortStats,
            longStats,
            safeLong(shortStats != null ? shortStats.rateLimitedCount() : null),
            safeLong(longStats != null ? longStats.rateLimitedCount() : null),
            RATE_LIMIT_THRESHOLDS,
            latest
        );

        String alertStatus = resolveOverallStatus(active, failureSignal, rateLimitSignal);
        String summary = trim(buildSummary(channel, failureSignal, rateLimitSignal, latest), MAX_SUMMARY_LENGTH);
        List<Map<String, Object>> relatedIncidents = new ArrayList<>();
        attachIncident(relatedIncidents, activeIncidentsBySignalKey.get(failureSignal.signalKey()), "delivery_failures");
        attachIncident(relatedIncidents, activeIncidentsBySignalKey.get(rateLimitSignal.signalKey()), "rate_limit_pressure");

        return new ChannelAlertSnapshot(
            channel.getId(),
            channel.getChannelName(),
            normalizePlatform(channel.getPlatform()),
            active,
            alertStatus,
            summary,
            latest != null ? latest.getAttemptedAt() : null,
            longStats != null ? longStats.lastSuccessAt() : null,
            longStats != null ? longStats.lastFailureAt() : null,
            failureSignal,
            rateLimitSignal,
            relatedIncidents
        );
    }

    private BurnRateSignal evaluateSignal(Channel channel,
                                          String signalSuffix,
                                          String label,
                                          boolean active,
                                          ProviderDeliveryLedgerRepository.ChannelAttemptStats shortStats,
                                          ProviderDeliveryLedgerRepository.ChannelAttemptStats longStats,
                                          long shortErrorCount,
                                          long longErrorCount,
                                          AlertThresholds thresholds,
                                          ProviderDeliveryLedgerEntry latest) {
        long shortAttempts = safeLong(shortStats != null ? shortStats.totalAttempts() : null);
        long longAttempts = safeLong(longStats != null ? longStats.totalAttempts() : null);
        double shortRate = ratio(shortErrorCount, shortAttempts);
        double longRate = ratio(longErrorCount, longAttempts);
        double shortBurnRate = thresholds.errorBudgetRatio() <= 0d ? 0d : shortRate / thresholds.errorBudgetRatio();
        double longBurnRate = thresholds.errorBudgetRatio() <= 0d ? 0d : longRate / thresholds.errorBudgetRatio();

        String status;
        if (!active) {
            status = STATUS_DISABLED;
        } else if (longAttempts <= 0L) {
            status = STATUS_IDLE;
        } else if (meetsThreshold(shortAttempts, longAttempts, shortBurnRate, longBurnRate, thresholds, true)) {
            status = STATUS_CRITICAL;
        } else if (meetsThreshold(shortAttempts, longAttempts, shortBurnRate, longBurnRate, thresholds, false)) {
            status = STATUS_WARNING;
        } else {
            status = STATUS_OK;
        }

        String signalKey = buildSignalKey(channel.getId(), signalSuffix);
        String summary = trim(buildSignalSummary(label, status, shortAttempts, shortErrorCount, shortBurnRate, longAttempts, longErrorCount, longBurnRate, latest), MAX_SUMMARY_LENGTH);
        String fingerprint = buildFingerprint(status, shortAttempts, shortErrorCount, shortBurnRate, longAttempts, longErrorCount, longBurnRate, latest);
        return new BurnRateSignal(
            signalKey,
            label,
            status,
            summary,
            shortAttempts,
            shortErrorCount,
            shortRate,
            shortBurnRate,
            longAttempts,
            longErrorCount,
            longRate,
            longBurnRate,
            fingerprint
        );
    }

    private void syncIncidents(ChannelAlertSnapshot item,
                               PreviousAlertState previous,
                               Map<String, Map<String, Object>> activeIncidentsBySignalKey) {
        syncIncident(item, item.failureSignal(), previous.failureStatus(), previous.failureFingerprint(), activeIncidentsBySignalKey);
        syncIncident(item, item.rateLimitSignal(), previous.rateLimitStatus(), previous.rateLimitFingerprint(), activeIncidentsBySignalKey);
    }

    private void syncIncident(ChannelAlertSnapshot item,
                              BurnRateSignal signal,
                              String previousStatus,
                              String previousFingerprint,
                              Map<String, Map<String, Object>> activeIncidentsBySignalKey) {
        if (signal == null || !StringUtils.hasText(signal.signalKey())) {
            return;
        }
        boolean actionable = isActionable(signal.status());
        boolean wasActionable = isActionable(previousStatus);
        boolean hasActiveIncident = activeIncidentsBySignalKey.containsKey(signal.signalKey());
        if (actionable) {
            if (!hasActiveIncident || !Objects.equals(previousStatus, signal.status()) || !Objects.equals(previousFingerprint, signal.fingerprint())) {
                incidentService.openOrRefreshSignalIncident(
                    SIGNAL_TYPE,
                    signal.signalKey(),
                    buildIncidentTitle(item, signal),
                    buildIncidentSummary(item, signal),
                    buildIncidentDescription(item, signal),
                    STATUS_CRITICAL.equals(signal.status()) ? "critical" : "high",
                    INCIDENT_SOURCE,
                    buildIncidentPayload(item, signal),
                    INCIDENT_ACTOR
                );
            }
            return;
        }
        if (wasActionable || hasActiveIncident) {
            incidentService.resolveSignalIncident(
                SIGNAL_TYPE,
                signal.signalKey(),
                buildResolvedText(item, signal),
                buildIncidentPayload(item, signal),
                INCIDENT_ACTOR
            );
        }
    }

    private void recordHistory(ChannelAlertSnapshot item, OffsetDateTime createdAt) {
        if (item == null || item.channelId() == null) {
            return;
        }
        historyRepository.record(
            MONITOR_KIND,
            item.channelId(),
            CHECK_KIND,
            item.alertStatus(),
            trim(item.summary(), MAX_SUMMARY_LENGTH),
            trim(buildHistoryDetails(item), MAX_DETAILS_LENGTH),
            null,
            null,
            createdAt
        );
    }

    private String buildHistoryDetails(ChannelAlertSnapshot item) {
        List<String> parts = new ArrayList<>();
        parts.add("channel=" + defaultIfBlank(item.channelName(), "channel #" + item.channelId()));
        parts.add("platform=" + normalizePlatform(item.platform()));
        appendSignalDetails(parts, "failure", item.failureSignal());
        appendSignalDetails(parts, "rate_limit", item.rateLimitSignal());
        parts.add("related_incidents=" + item.relatedIncidents().size());
        return String.join("; ", parts);
    }

    private void appendSignalDetails(List<String> parts, String prefix, BurnRateSignal signal) {
        if (signal == null) {
            return;
        }
        parts.add(prefix + "_status=" + defaultIfBlank(signal.status(), ""));
        parts.add(prefix + "_short_attempts=" + signal.shortWindowAttempts());
        parts.add(prefix + "_short_errors=" + signal.shortWindowErrors());
        parts.add(prefix + "_short_burn=" + formatRate(signal.shortWindowBurnRate()));
        parts.add(prefix + "_long_attempts=" + signal.longWindowAttempts());
        parts.add(prefix + "_long_errors=" + signal.longWindowErrors());
        parts.add(prefix + "_long_burn=" + formatRate(signal.longWindowBurnRate()));
        parts.add(prefix + "_fingerprint=" + defaultIfBlank(signal.fingerprint(), ""));
    }

    private Map<Long, PreviousAlertState> loadPreviousStates(List<Channel> channels) {
        Map<Long, PreviousAlertState> result = new LinkedHashMap<>();
        for (Channel channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            List<MonitoringCheckHistoryRepository.HistoryEntry> history = historyRepository.findRecent(MONITOR_KIND, channel.getId(), 1);
            if (history.isEmpty()) {
                result.put(channel.getId(), PreviousAlertState.empty());
                continue;
            }
            result.put(channel.getId(), parsePreviousState(history.get(0).detailsExcerpt()));
        }
        return result;
    }

    private PreviousAlertState parsePreviousState(String details) {
        if (!StringUtils.hasText(details)) {
            return PreviousAlertState.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : details.split(";")) {
            String token = part == null ? "" : part.trim();
            int separator = token.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(token.substring(0, separator), token.substring(separator + 1));
        }
        return new PreviousAlertState(
            values.getOrDefault("failure_status", ""),
            values.getOrDefault("failure_fingerprint", ""),
            values.getOrDefault("rate_limit_status", ""),
            values.getOrDefault("rate_limit_fingerprint", "")
        );
    }

    private Map<String, Map<String, Object>> loadActiveIncidents() {
        Map<String, Map<String, Object>> activeBySignalKey = new LinkedHashMap<>();
        for (Map<String, Object> incident : incidentService.listIncidentSummariesForSignalType(SIGNAL_TYPE)) {
            String signalKey = normalize(stringValue(incident.get("signal_key")));
            if (!StringUtils.hasText(signalKey)) {
                continue;
            }
            String status = normalize(stringValue(incident.get("status")));
            if ("resolved".equals(status) || "closed".equals(status)) {
                continue;
            }
            activeBySignalKey.put(signalKey, incident);
        }
        return activeBySignalKey;
    }

    private void attachIncident(List<Map<String, Object>> target, Map<String, Object> incident, String signalKind) {
        if (target == null || incident == null || incident.isEmpty()) {
            return;
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("signal_kind", signalKind);
        dto.put("id", incident.get("id"));
        dto.put("incident_key", incident.get("incident_key"));
        dto.put("title", incident.get("title"));
        dto.put("status", incident.get("status"));
        dto.put("severity", incident.get("severity"));
        dto.put("updated_at", incident.get("updated_at"));
        target.add(dto);
    }

    private Overview buildOverview(List<ChannelAlertSnapshot> items) {
        int totalChannels = 0;
        int activeChannels = 0;
        int actionableChannels = 0;
        int warningChannels = 0;
        int criticalChannels = 0;
        int idleChannels = 0;
        int disabledChannels = 0;
        int failurePressureChannels = 0;
        int rateLimitPressureChannels = 0;
        int activeIncidents = 0;
        for (ChannelAlertSnapshot item : items) {
            totalChannels++;
            if (item.active()) {
                activeChannels++;
            }
            if (isActionable(item.alertStatus())) {
                actionableChannels++;
            }
            switch (normalize(item.alertStatus())) {
                case STATUS_CRITICAL -> criticalChannels++;
                case STATUS_WARNING -> warningChannels++;
                case STATUS_IDLE -> idleChannels++;
                case STATUS_DISABLED -> disabledChannels++;
                default -> {
                }
            }
            if (isActionable(item.failureSignal().status())) {
                failurePressureChannels++;
            }
            if (isActionable(item.rateLimitSignal().status())) {
                rateLimitPressureChannels++;
            }
            activeIncidents += item.relatedIncidents().size();
        }
        return new Overview(
            totalChannels,
            activeChannels,
            actionableChannels,
            warningChannels,
            criticalChannels,
            idleChannels,
            disabledChannels,
            failurePressureChannels,
            rateLimitPressureChannels,
            activeIncidents
        );
    }

    private boolean meetsThreshold(long shortAttempts,
                                   long longAttempts,
                                   double shortBurnRate,
                                   double longBurnRate,
                                   AlertThresholds thresholds,
                                   boolean critical) {
        if (shortAttempts < thresholds.minShortAttempts() || longAttempts < thresholds.minLongAttempts()) {
            return false;
        }
        if (critical) {
            return shortBurnRate >= thresholds.criticalShortBurnRate()
                && longBurnRate >= thresholds.criticalLongBurnRate();
        }
        return shortBurnRate >= thresholds.warningShortBurnRate()
            && longBurnRate >= thresholds.warningLongBurnRate();
    }

    private String resolveOverallStatus(boolean active, BurnRateSignal failureSignal, BurnRateSignal rateLimitSignal) {
        if (!active) {
            return STATUS_DISABLED;
        }
        if (STATUS_CRITICAL.equals(failureSignal.status()) || STATUS_CRITICAL.equals(rateLimitSignal.status())) {
            return STATUS_CRITICAL;
        }
        if (STATUS_WARNING.equals(failureSignal.status()) || STATUS_WARNING.equals(rateLimitSignal.status())) {
            return STATUS_WARNING;
        }
        if (STATUS_IDLE.equals(failureSignal.status()) && STATUS_IDLE.equals(rateLimitSignal.status())) {
            return STATUS_IDLE;
        }
        return STATUS_OK;
    }

    private List<Channel> loadMonitoredChannels() {
        return channelRepository.findAll().stream()
            .filter(this::isMonitoredChannel)
            .toList();
    }

    private Channel loadMonitoredChannel(long channelId) {
        Optional<Channel> channelOpt = channelRepository.findById(channelId);
        Channel channel = channelOpt.orElseThrow(() -> new IllegalArgumentException("Provider channel not found"));
        if (!isMonitoredChannel(channel)) {
            throw new IllegalArgumentException("Channel is not a Telegram/VK/MAX provider channel");
        }
        return channel;
    }

    private boolean isMonitoredChannel(Channel channel) {
        return channel != null && MONITORED_PLATFORMS.contains(normalizePlatform(channel.getPlatform()));
    }

    private Map<Long, ProviderDeliveryLedgerRepository.ChannelAttemptStats> indexByChannel(List<ProviderDeliveryLedgerRepository.ChannelAttemptStats> items) {
        Map<Long, ProviderDeliveryLedgerRepository.ChannelAttemptStats> result = new LinkedHashMap<>();
        for (ProviderDeliveryLedgerRepository.ChannelAttemptStats item : items) {
            if (item != null && item.channelId() != null) {
                result.put(item.channelId(), item);
            }
        }
        return result;
    }

    private Map<Long, ProviderDeliveryLedgerEntry> latestByChannel(List<ProviderDeliveryLedgerEntry> items) {
        Map<Long, ProviderDeliveryLedgerEntry> result = new LinkedHashMap<>();
        for (ProviderDeliveryLedgerEntry item : items) {
            if (item != null && item.getChannelId() != null && !result.containsKey(item.getChannelId())) {
                result.put(item.getChannelId(), item);
            }
        }
        return result;
    }

    private String buildSignalSummary(String label,
                                      String status,
                                      long shortAttempts,
                                      long shortErrors,
                                      double shortBurnRate,
                                      long longAttempts,
                                      long longErrors,
                                      double longBurnRate,
                                      ProviderDeliveryLedgerEntry latest) {
        if (STATUS_DISABLED.equals(status)) {
            return label + ": channel is disabled.";
        }
        if (STATUS_IDLE.equals(status)) {
            return label + ": no outbound attempts within the long burn-rate window.";
        }
        StringBuilder summary = new StringBuilder();
        summary.append(label)
            .append(": ")
            .append(status)
            .append(", short ")
            .append(shortErrors)
            .append('/')
            .append(shortAttempts)
            .append(" burn=")
            .append(formatRate(shortBurnRate))
            .append("x, long ")
            .append(longErrors)
            .append('/')
            .append(longAttempts)
            .append(" burn=")
            .append(formatRate(longBurnRate))
            .append('x');
        if (latest != null && StringUtils.hasText(latest.getClassification())) {
            summary.append(", latest=").append(latest.getClassification().trim());
        }
        return summary.toString();
    }

    private String buildSummary(Channel channel,
                                BurnRateSignal failureSignal,
                                BurnRateSignal rateLimitSignal,
                                ProviderDeliveryLedgerEntry latest) {
        if (!Boolean.TRUE.equals(channel.getActive())) {
            return "Alerting disabled because provider channel is inactive.";
        }
        if (STATUS_IDLE.equals(failureSignal.status()) && STATUS_IDLE.equals(rateLimitSignal.status())) {
            return "No outbound traffic in the long burn-rate window; alerting stays idle.";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("Failures: ").append(failureSignal.status())
            .append(" (").append(formatRate(failureSignal.shortWindowBurnRate())).append("x / ")
            .append(formatRate(failureSignal.longWindowBurnRate())).append("x). ");
        summary.append("Rate limits: ").append(rateLimitSignal.status())
            .append(" (").append(formatRate(rateLimitSignal.shortWindowBurnRate())).append("x / ")
            .append(formatRate(rateLimitSignal.longWindowBurnRate())).append("x).");
        if (latest != null && StringUtils.hasText(latest.getProviderMessage())) {
            summary.append(" Latest provider message: ").append(latest.getProviderMessage().trim());
        }
        return summary.toString();
    }

    private String buildIncidentTitle(ChannelAlertSnapshot item, BurnRateSignal signal) {
        String channelLabel = defaultIfBlank(item.channelName(), "channel #" + item.channelId());
        return signal.label() + ": " + channelLabel;
    }

    private String buildIncidentSummary(ChannelAlertSnapshot item, BurnRateSignal signal) {
        return defaultIfBlank(signal.summary(), defaultIfBlank(item.summary(), "Provider delivery alert triggered."));
    }

    private String buildIncidentDescription(ChannelAlertSnapshot item, BurnRateSignal signal) {
        String channelLabel = defaultIfBlank(item.channelName(), "channel #" + item.channelId());
        return """
            Provider delivery burn-rate evaluation detected sustained pressure on the operator reply-path.
            Review the provider delivery ledger analytics page, recent outbound attempts and provider-side throttling behavior.
            Channel: %s
            Signal: %s
            Short window: %d errors of %d attempts (burn=%sx)
            Long window: %d errors of %d attempts (burn=%sx)
            """.formatted(
            channelLabel,
            signal.label(),
            signal.shortWindowErrors(),
            signal.shortWindowAttempts(),
            formatRate(signal.shortWindowBurnRate()),
            signal.longWindowErrors(),
            signal.longWindowAttempts(),
            formatRate(signal.longWindowBurnRate())
        ).trim();
    }

    private Map<String, Object> buildIncidentPayload(ChannelAlertSnapshot item, BurnRateSignal signal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel_id", item.channelId());
        payload.put("channel_name", item.channelName());
        payload.put("platform", item.platform());
        payload.put("alert_status", item.alertStatus());
        payload.put("signal_label", signal.label());
        payload.put("signal_status", signal.status());
        payload.put("short_window_minutes", SHORT_WINDOW.toMinutes());
        payload.put("short_window_attempts", signal.shortWindowAttempts());
        payload.put("short_window_errors", signal.shortWindowErrors());
        payload.put("short_window_error_rate", round(signal.shortWindowErrorRate()));
        payload.put("short_window_burn_rate", round(signal.shortWindowBurnRate()));
        payload.put("long_window_hours", LONG_WINDOW.toHours());
        payload.put("long_window_attempts", signal.longWindowAttempts());
        payload.put("long_window_errors", signal.longWindowErrors());
        payload.put("long_window_error_rate", round(signal.longWindowErrorRate()));
        payload.put("long_window_burn_rate", round(signal.longWindowBurnRate()));
        payload.put("summary", signal.summary());
        return payload;
    }

    private String buildResolvedText(ChannelAlertSnapshot item, BurnRateSignal signal) {
        String channelLabel = defaultIfBlank(item.channelName(), "channel #" + item.channelId());
        return signal.label() + " cleared for " + channelLabel;
    }

    private String buildSignalKey(Long channelId, String suffix) {
        return "channel-" + channelId + "/" + suffix;
    }

    private String buildFingerprint(String status,
                                    long shortAttempts,
                                    long shortErrors,
                                    double shortBurnRate,
                                    long longAttempts,
                                    long longErrors,
                                    double longBurnRate,
                                    ProviderDeliveryLedgerEntry latest) {
        return String.join("|",
            defaultIfBlank(status, ""),
            String.valueOf(shortAttempts),
            String.valueOf(shortErrors),
            formatRate(shortBurnRate),
            String.valueOf(longAttempts),
            String.valueOf(longErrors),
            formatRate(longBurnRate),
            latest != null ? defaultIfBlank(latest.getClassification(), "") : "",
            latest != null ? defaultIfBlank(latest.getProviderMessage(), "") : "",
            latest != null && latest.getHttpStatus() != null ? String.valueOf(latest.getHttpStatus()) : ""
        );
    }

    private boolean isActionable(String status) {
        String normalized = normalize(status);
        return STATUS_WARNING.equals(normalized) || STATUS_CRITICAL.equals(normalized);
    }

    private int severityRank(String status) {
        return switch (normalize(status)) {
            case STATUS_CRITICAL -> 0;
            case STATUS_WARNING -> 1;
            case STATUS_OK -> 2;
            case STATUS_IDLE -> 3;
            case STATUS_DISABLED -> 4;
            default -> 5;
        };
    }

    private double ratio(long part, long total) {
        if (total <= 0L) {
            return 0d;
        }
        return (double) part / (double) total;
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private String formatRate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String normalizePlatform(String value) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized : "unknown";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record AlertThresholds(double errorBudgetRatio,
                                  int minShortAttempts,
                                  int minLongAttempts,
                                  double warningShortBurnRate,
                                  double warningLongBurnRate,
                                  double criticalShortBurnRate,
                                  double criticalLongBurnRate) {
    }

    public record OverviewSnapshot(OffsetDateTime generatedAt,
                                   Overview overview,
                                   List<ChannelAlertSnapshot> items) {
    }

    public record Overview(int totalChannels,
                           int activeChannels,
                           int actionableChannels,
                           int warningChannels,
                           int criticalChannels,
                           int idleChannels,
                           int disabledChannels,
                           int failurePressureChannels,
                           int rateLimitPressureChannels,
                           int activeIncidents) {
    }

    public record ChannelAlertSnapshot(Long channelId,
                                       String channelName,
                                       String platform,
                                       boolean active,
                                       String alertStatus,
                                       String summary,
                                       OffsetDateTime lastAttemptAt,
                                       OffsetDateTime lastSuccessAt,
                                       OffsetDateTime lastFailureAt,
                                       BurnRateSignal failureSignal,
                                       BurnRateSignal rateLimitSignal,
                                       List<Map<String, Object>> relatedIncidents) {
    }

    public record BurnRateSignal(String signalKey,
                                 String label,
                                 String status,
                                 String summary,
                                 long shortWindowAttempts,
                                 long shortWindowErrors,
                                 double shortWindowErrorRate,
                                 double shortWindowBurnRate,
                                 long longWindowAttempts,
                                 long longWindowErrors,
                                 double longWindowErrorRate,
                                 double longWindowBurnRate,
                                 String fingerprint) {
    }

    public record RefreshSummary(int checked, int actionable) {
    }

    private record PreviousAlertState(String failureStatus,
                                      String failureFingerprint,
                                      String rateLimitStatus,
                                      String rateLimitFingerprint) {

        private static PreviousAlertState empty() {
            return new PreviousAlertState("", "", "", "");
        }
    }
}
