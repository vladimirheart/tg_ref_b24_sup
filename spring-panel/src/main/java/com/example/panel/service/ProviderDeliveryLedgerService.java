package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ProviderDeliveryLedgerEntry;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.ProviderDeliveryLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ProviderDeliveryLedgerService {

    public static final String MONITOR_KIND = "provider_delivery";
    public static final String CHECK_KIND = "delivery_attempt";

    public static final String STATUS_OK = "ok";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_DISABLED = "disabled";

    private static final int MAX_SUMMARY_LENGTH = 320;
    private static final int MAX_DETAILS_LENGTH = 1_500;
    private static final int RECENT_SCAN_LIMIT = 500;
    private static final int RECENT_ATTEMPTS_LIMIT = 50;
    private static final Set<String> MONITORED_PLATFORMS = Set.of("telegram", "vk", "max");

    private final ProviderDeliveryLedgerRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final ChannelRepository channelRepository;
    private final Clock clock;

    public ProviderDeliveryLedgerService(ProviderDeliveryLedgerRepository repository,
                                         MonitoringCheckHistoryRepository historyRepository,
                                         ChannelRepository channelRepository) {
        this(repository, historyRepository, channelRepository, Clock.systemUTC());
    }

    ProviderDeliveryLedgerService(ProviderDeliveryLedgerRepository repository,
                                  MonitoringCheckHistoryRepository historyRepository,
                                  ChannelRepository channelRepository,
                                  Clock clock) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.channelRepository = channelRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void recordAttempt(Channel channel,
                              String ticketId,
                              Long userId,
                              String senderKind,
                              String messageKind,
                              Long replyToMessageId,
                              DialogReplyTransportService.DialogReplyTransportResult result) {
        if (channel == null || channel.getId() == null || result == null) {
            return;
        }
        OffsetDateTime attemptedAt = OffsetDateTime.now(clock);
        ProviderDeliveryLedgerEntry entry = new ProviderDeliveryLedgerEntry();
        entry.setChannelId(channel.getId());
        entry.setTicketId(normalizeOptional(ticketId));
        entry.setPlatform(normalizePlatform(channel.getPlatform()));
        entry.setProvider(normalizePlatform(channel.getPlatform()));
        entry.setUserId(userId);
        entry.setSenderKind(defaultIfBlank(senderKind, "operator"));
        entry.setMessageKind(defaultIfBlank(messageKind, "text"));
        entry.setDeliveryStatus(result.success() ? "success" : "failed");
        entry.setClassification(defaultIfBlank(result.classification(), result.success() ? "success" : "unknown_error"));
        entry.setSeverityLevel(defaultIfBlank(result.severityLevel(), result.success() ? STATUS_OK : STATUS_CRITICAL));
        entry.setRetryState(defaultIfBlank(result.retryState(), result.success() ? "none" : "transient"));
        entry.setHttpStatus(result.httpStatus());
        entry.setProviderErrorCode(normalizeOptional(result.providerErrorCode()));
        entry.setProviderMessage(normalizeOptional(trim(result.providerMessage(), 500)));
        entry.setResponseExcerpt(normalizeOptional(trim(result.responseExcerpt(), 800)));
        entry.setProviderMessageId(result.telegramMessageId());
        entry.setReplyToMessageId(replyToMessageId);
        entry.setDurationMs(result.durationMs());
        entry.setAttemptedAt(attemptedAt);
        repository.save(entry);
        recordHistory(channel, entry, attemptedAt);
    }

    public OverviewSnapshot buildOverview() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Channel> channels = loadMonitoredChannels();
        Map<Long, ProviderDeliveryLedgerRepository.ChannelAttemptStats> statsByChannel = new LinkedHashMap<>();
        for (ProviderDeliveryLedgerRepository.ChannelAttemptStats item : repository.summarizeByChannelSince(now.minusHours(24))) {
            if (item.channelId() != null) {
                statsByChannel.put(item.channelId(), item);
            }
        }
        Map<Long, ProviderDeliveryLedgerEntry> latestByChannel = new LinkedHashMap<>();
        for (ProviderDeliveryLedgerEntry item : repository.findRecent(RECENT_SCAN_LIMIT)) {
            if (item.getChannelId() != null && !latestByChannel.containsKey(item.getChannelId())) {
                latestByChannel.put(item.getChannelId(), item);
            }
        }

        List<ChannelDeliverySummary> items = new ArrayList<>();
        for (Channel channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            ProviderDeliveryLedgerRepository.ChannelAttemptStats stats = statsByChannel.get(channel.getId());
            ProviderDeliveryLedgerEntry latest = latestByChannel.get(channel.getId());
            items.add(buildChannelSummary(channel, stats, latest));
        }
        items.sort(Comparator
            .comparingInt((ChannelDeliverySummary item) -> severityRank(item.status()))
            .thenComparing(item -> !item.active())
            .thenComparing(item -> normalize(item.platform()))
            .thenComparing(item -> normalize(item.channelName()))
            .thenComparing(item -> item.channelId() == null ? Long.MAX_VALUE : item.channelId()));

        List<ProviderDeliveryLedgerEntry> recentAttempts = repository.findRecent(RECENT_ATTEMPTS_LIMIT);
        return new OverviewSnapshot(
            now,
            buildOverview(items),
            items,
            recentAttempts
        );
    }

    public List<ProviderDeliveryLedgerEntry> loadHistory(long channelId, int limit) {
        loadMonitoredChannel(channelId);
        return repository.findRecentByChannel(channelId, limit);
    }

    private ChannelDeliverySummary buildChannelSummary(Channel channel,
                                                       ProviderDeliveryLedgerRepository.ChannelAttemptStats stats,
                                                       ProviderDeliveryLedgerEntry latest) {
        boolean active = Boolean.TRUE.equals(channel.getActive());
        String status = resolveStatus(active, latest);
        long total24h = safeLong(stats != null ? stats.totalAttempts() : null);
        long success24h = safeLong(stats != null ? stats.successCount() : null);
        long failure24h = safeLong(stats != null ? stats.failureCount() : null);
        long rateLimited24h = safeLong(stats != null ? stats.rateLimitedCount() : null);
        long terminal24h = safeLong(stats != null ? stats.terminalFailureCount() : null);
        long transient24h = safeLong(stats != null ? stats.transientFailureCount() : null);
        String summary = buildSummary(channel, latest, total24h, success24h, failure24h);
        return new ChannelDeliverySummary(
            channel.getId(),
            channel.getChannelName(),
            normalizePlatform(channel.getPlatform()),
            active,
            status,
            total24h,
            success24h,
            failure24h,
            rateLimited24h,
            terminal24h,
            transient24h,
            stats != null ? stats.lastAttemptAt() : null,
            stats != null ? stats.lastSuccessAt() : null,
            stats != null ? stats.lastFailureAt() : null,
            latest != null ? latest.getClassification() : null,
            latest != null ? latest.getSeverityLevel() : null,
            latest != null ? latest.getProviderMessage() : null,
            latest != null ? latest.getHttpStatus() : null,
            trim(summary, MAX_SUMMARY_LENGTH)
        );
    }

    private DeliveryOverview buildOverview(List<ChannelDeliverySummary> items) {
        int totalChannels = 0;
        int activeChannels = 0;
        int okChannels = 0;
        int warningChannels = 0;
        int criticalChannels = 0;
        int idleChannels = 0;
        int disabledChannels = 0;
        long attempts24h = 0L;
        long success24h = 0L;
        long failure24h = 0L;
        long rateLimited24h = 0L;
        long terminal24h = 0L;
        long transient24h = 0L;
        for (ChannelDeliverySummary item : items) {
            totalChannels++;
            if (item.active()) {
                activeChannels++;
            }
            switch (normalize(item.status())) {
                case STATUS_OK -> okChannels++;
                case STATUS_WARNING -> warningChannels++;
                case STATUS_CRITICAL -> criticalChannels++;
                case STATUS_IDLE -> idleChannels++;
                case STATUS_DISABLED -> disabledChannels++;
                default -> criticalChannels++;
            }
            attempts24h += item.total24h();
            success24h += item.success24h();
            failure24h += item.failure24h();
            rateLimited24h += item.rateLimited24h();
            terminal24h += item.terminalFailures24h();
            transient24h += item.transientFailures24h();
        }
        return new DeliveryOverview(
            totalChannels,
            activeChannels,
            okChannels,
            warningChannels,
            criticalChannels,
            idleChannels,
            disabledChannels,
            attempts24h,
            success24h,
            failure24h,
            rateLimited24h,
            terminal24h,
            transient24h
        );
    }

    private void recordHistory(Channel channel,
                               ProviderDeliveryLedgerEntry entry,
                               OffsetDateTime attemptedAt) {
        if (channel == null || channel.getId() == null || entry == null) {
            return;
        }
        String summary = trim(buildHistorySummary(channel, entry), MAX_SUMMARY_LENGTH);
        String details = trim(buildHistoryDetails(channel, entry), MAX_DETAILS_LENGTH);
        historyRepository.record(
            MONITOR_KIND,
            channel.getId(),
            CHECK_KIND,
            entry.getSeverityLevel(),
            summary,
            details,
            entry.getHttpStatus(),
            entry.getDurationMs(),
            attemptedAt
        );
    }

    private String buildHistorySummary(Channel channel, ProviderDeliveryLedgerEntry entry) {
        String outcome = "success".equalsIgnoreCase(entry.getDeliveryStatus()) ? "success" : entry.getClassification();
        StringBuilder summary = new StringBuilder();
        summary.append(normalizePlatform(channel.getPlatform()).toUpperCase(Locale.ROOT))
            .append(' ')
            .append(defaultIfBlank(entry.getMessageKind(), "text"))
            .append(' ')
            .append(outcome);
        if (StringUtils.hasText(entry.getTicketId())) {
            summary.append(" ticket=").append(entry.getTicketId().trim());
        }
        return summary.toString();
    }

    private String buildHistoryDetails(Channel channel, ProviderDeliveryLedgerEntry entry) {
        List<String> parts = new ArrayList<>();
        parts.add("channel=" + defaultIfBlank(channel.getChannelName(), "channel #" + channel.getId()));
        parts.add("platform=" + normalizePlatform(channel.getPlatform()));
        parts.add("sender=" + defaultIfBlank(entry.getSenderKind(), "operator"));
        parts.add("message_kind=" + defaultIfBlank(entry.getMessageKind(), "text"));
        parts.add("delivery_status=" + defaultIfBlank(entry.getDeliveryStatus(), "unknown"));
        parts.add("classification=" + defaultIfBlank(entry.getClassification(), "unknown"));
        parts.add("retry_state=" + defaultIfBlank(entry.getRetryState(), "unknown"));
        if (entry.getHttpStatus() != null) {
            parts.add("http_status=" + entry.getHttpStatus());
        }
        if (StringUtils.hasText(entry.getProviderErrorCode())) {
            parts.add("provider_code=" + entry.getProviderErrorCode().trim());
        }
        if (StringUtils.hasText(entry.getProviderMessage())) {
            parts.add("provider_message=" + entry.getProviderMessage().trim());
        }
        return String.join(", ", parts);
    }

    private String buildSummary(Channel channel,
                                ProviderDeliveryLedgerEntry latest,
                                long total24h,
                                long success24h,
                                long failure24h) {
        if (latest == null) {
            return "No outbound delivery attempts recorded yet.";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("24h: ")
            .append(success24h)
            .append(" success / ")
            .append(failure24h)
            .append(" failed / ")
            .append(total24h)
            .append(" total.");
        if (StringUtils.hasText(latest.getClassification())) {
            summary.append(" Last=").append(latest.getClassification());
        }
        if (StringUtils.hasText(latest.getProviderMessage())) {
            summary.append(" (").append(latest.getProviderMessage().trim()).append(')');
        } else if (latest.getHttpStatus() != null) {
            summary.append(" (HTTP ").append(latest.getHttpStatus()).append(')');
        }
        return summary.toString();
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

    private String resolveStatus(boolean active, ProviderDeliveryLedgerEntry latest) {
        if (!active) {
            return STATUS_DISABLED;
        }
        if (latest == null) {
            return STATUS_IDLE;
        }
        if ("success".equalsIgnoreCase(latest.getDeliveryStatus())) {
            return STATUS_OK;
        }
        if (STATUS_CRITICAL.equalsIgnoreCase(latest.getSeverityLevel())) {
            return STATUS_CRITICAL;
        }
        return STATUS_WARNING;
    }

    private int severityRank(String status) {
        return switch (normalize(status)) {
            case STATUS_CRITICAL -> 0;
            case STATUS_WARNING -> 1;
            case STATUS_IDLE -> 2;
            case STATUS_OK -> 3;
            case STATUS_DISABLED -> 4;
            default -> 5;
        };
    }

    private String normalizePlatform(String platform) {
        String normalized = normalize(platform);
        return normalized.isEmpty() ? "telegram" : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalize(String value) {
        return value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    public record OverviewSnapshot(OffsetDateTime generatedAt,
                                   DeliveryOverview overview,
                                   List<ChannelDeliverySummary> items,
                                   List<ProviderDeliveryLedgerEntry> recentAttempts) {
    }

    public record DeliveryOverview(int totalChannels,
                                   int activeChannels,
                                   int okChannels,
                                   int warningChannels,
                                   int criticalChannels,
                                   int idleChannels,
                                   int disabledChannels,
                                   long attempts24h,
                                   long success24h,
                                   long failure24h,
                                   long rateLimited24h,
                                   long terminalFailures24h,
                                   long transientFailures24h) {
    }

    public record ChannelDeliverySummary(Long channelId,
                                         String channelName,
                                         String platform,
                                         boolean active,
                                         String status,
                                         long total24h,
                                         long success24h,
                                         long failure24h,
                                         long rateLimited24h,
                                         long terminalFailures24h,
                                         long transientFailures24h,
                                         OffsetDateTime lastAttemptAt,
                                         OffsetDateTime lastSuccessAt,
                                         OffsetDateTime lastFailureAt,
                                         String lastClassification,
                                         String lastSeverityLevel,
                                         String lastProviderMessage,
                                         Integer lastHttpStatus,
                                         String summary) {
    }
}
