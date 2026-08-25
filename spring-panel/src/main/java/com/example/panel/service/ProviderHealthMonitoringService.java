package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class ProviderHealthMonitoringService {

    public static final String STATUS_OK = "ok";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_DISABLED = "disabled";
    public static final String STATUS_IDLE = "idle";
    public static final String AVAILABILITY_UP = "up";
    public static final String AVAILABILITY_DOWN = "down";
    public static final String AVAILABILITY_DISABLED = "disabled";
    public static final String AVAILABILITY_UNKNOWN = "unknown";
    public static final String ACTIVITY_ACTIVE = "active";
    public static final String ACTIVITY_IDLE = "idle";
    public static final String ACTIVITY_STALE = "stale";
    public static final String ACTIVITY_DISABLED = "disabled";

    private static final String MONITOR_KIND = "provider_health";
    private static final String CHECK_KIND = "provider_probe";
    private static final String DEFAULT_TELEGRAM_API_ROOT_URL = "https://api.telegram.org";
    private static final String MAX_UPDATES_ENDPOINT = "https://platform-api.max.ru/updates?limit=1&timeout=1";
    private static final Set<String> MONITORED_PLATFORMS = Set.of("telegram", "vk", "max");
    private static final Set<String> OUTBOUND_SENDERS = Set.of("operator", "support", "admin", "system", "ai_agent");
    private static final int MAX_SUMMARY_LENGTH = 320;
    private static final int MAX_DETAILS_LENGTH = 1_500;
    private static final int STALE_ACTIVITY_HOURS = 72;

    private final ChannelRepository channelRepository;
    private final BotProcessService botProcessService;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final IntegrationNetworkService integrationNetworkService;
    private final ObjectMapper objectMapper;
    private final ProviderProbeClient providerProbeClient;
    private final Clock clock;

    @Autowired
    public ProviderHealthMonitoringService(ChannelRepository channelRepository,
                                           BotProcessService botProcessService,
                                           MonitoringCheckHistoryRepository historyRepository,
                                           JdbcTemplate jdbcTemplate,
                                           IntegrationNetworkService integrationNetworkService,
                                           ObjectMapper objectMapper) {
        this(
            channelRepository,
            botProcessService,
            historyRepository,
            jdbcTemplate,
            integrationNetworkService,
            objectMapper,
            null,
            Clock.systemUTC()
        );
    }

    ProviderHealthMonitoringService(ChannelRepository channelRepository,
                                    BotProcessService botProcessService,
                                    MonitoringCheckHistoryRepository historyRepository,
                                    JdbcTemplate jdbcTemplate,
                                    IntegrationNetworkService integrationNetworkService,
                                    ObjectMapper objectMapper,
                                    ProviderProbeClient providerProbeClient,
                                    Clock clock) {
        this.channelRepository = channelRepository;
        this.botProcessService = botProcessService;
        this.historyRepository = historyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.integrationNetworkService = integrationNetworkService;
        this.objectMapper = objectMapper;
        this.providerProbeClient = providerProbeClient != null ? providerProbeClient : new DefaultProviderProbeClient();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public OverviewSnapshot buildOverview() {
        List<ProviderChannelHealth> items = evaluateChannels(loadMonitoredChannels(), false);
        return new OverviewSnapshot(
            OffsetDateTime.now(clock),
            buildAvailabilityOverview(items),
            items
        );
    }

    public RefreshSummary refreshAll() {
        List<Channel> channels = loadMonitoredChannels();
        List<ProviderChannelHealth> items = evaluateChannels(channels, true);
        return new RefreshSummary(channels.size(), items.size());
    }

    public ProviderChannelHealth refreshById(long channelId) {
        Channel channel = loadMonitoredChannel(channelId);
        return evaluateChannels(List.of(channel), true).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Provider channel not found"));
    }

    public List<MonitoringCheckHistoryRepository.HistoryEntry> loadHistory(long channelId,
                                                                           int limit) {
        loadMonitoredChannel(channelId);
        return historyRepository.findRecent(MONITOR_KIND, channelId, limit);
    }

    public AvailabilityOverview buildAvailabilityOverview(List<ProviderChannelHealth> items) {
        int total = 0;
        int active = 0;
        int ok = 0;
        int warning = 0;
        int critical = 0;
        int disabled = 0;
        int idle = 0;
        if (items != null) {
            for (ProviderChannelHealth item : items) {
                total++;
                String overallStatus = normalize(item.overallStatus());
                switch (overallStatus) {
                    case STATUS_OK -> {
                        ok++;
                        active++;
                    }
                    case STATUS_WARNING -> {
                        warning++;
                        active++;
                    }
                    case STATUS_CRITICAL -> {
                        critical++;
                        active++;
                    }
                    case STATUS_IDLE -> {
                        idle++;
                        active++;
                    }
                    case STATUS_DISABLED -> disabled++;
                    default -> critical++;
                }
            }
        }
        int available = ok + warning + idle;
        double availabilityPercent = active == 0 ? 0d : Math.round(available * 1000.0d / active) / 10.0d;
        return new AvailabilityOverview(total, active, ok, warning, critical, disabled, idle, availabilityPercent);
    }

    private List<Channel> loadMonitoredChannels() {
        return channelRepository.findAll().stream()
            .filter(this::isMonitoredChannel)
            .sorted(Comparator
                .comparing((Channel channel) -> !Boolean.TRUE.equals(channel.getActive()))
                .thenComparing(channel -> normalize(channel.getPlatform()))
                .thenComparing(channel -> normalize(channel.getChannelName()))
                .thenComparing(channel -> channel.getId() == null ? Long.MAX_VALUE : channel.getId()))
            .toList();
    }

    private Channel loadMonitoredChannel(long channelId) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new IllegalArgumentException("Provider channel not found"));
        if (!isMonitoredChannel(channel)) {
            throw new IllegalArgumentException("Channel is not a Telegram/VK/MAX provider channel");
        }
        return channel;
    }

    private boolean isMonitoredChannel(Channel channel) {
        return channel != null && MONITORED_PLATFORMS.contains(normalizePlatform(channel.getPlatform()));
    }

    private List<ProviderChannelHealth> evaluateChannels(List<Channel> channels,
                                                         boolean recordHistory) {
        Map<Long, ActivityStats> activityByChannel = loadActivityStats();
        List<ProviderChannelHealth> result = new ArrayList<>();
        for (Channel channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            result.add(evaluateChannel(channel, activityByChannel.get(channel.getId()), recordHistory));
        }
        return result;
    }

    private ProviderChannelHealth evaluateChannel(Channel channel,
                                                  ActivityStats activityStats,
                                                  boolean recordHistory) {
        OffsetDateTime checkedAt = OffsetDateTime.now(clock);
        long startedAtNs = System.nanoTime();
        boolean active = Boolean.TRUE.equals(channel.getActive());
        String platform = normalizePlatform(channel.getPlatform());
        BotProcessService.BotProcessStatus runtimeStatus = active
            ? botProcessService.status(channel.getId())
            : BotProcessService.BotProcessStatus.stopped();
        RuntimeView runtimeView = buildRuntimeView(active, runtimeStatus);
        ProviderProbeResult probeResult = active
            ? safeProbe(channel, platform)
            : ProviderProbeResult.disabled(platform);

        ActivityStats effectiveStats = activityStats != null ? activityStats : ActivityStats.empty();
        String ingressStatus = resolveActivityStatus(active, effectiveStats.lastInboundAt(), effectiveStats.inbound24h(), checkedAt);
        String outboundStatus = resolveActivityStatus(active, effectiveStats.lastOutboundAt(), effectiveStats.outbound24h(), checkedAt);
        String overallStatus = resolveOverallStatus(active, runtimeView.status(), probeResult, ingressStatus, outboundStatus);
        String availability = resolveAvailability(overallStatus);
        String summary = trim(buildSummary(runtimeView, probeResult, effectiveStats, ingressStatus, outboundStatus), MAX_SUMMARY_LENGTH);
        String details = trim(buildDetails(channel, runtimeView, probeResult, effectiveStats, ingressStatus, outboundStatus), MAX_DETAILS_LENGTH);
        long durationMs = elapsedMillis(startedAtNs);

        if (recordHistory && channel.getId() != null) {
            historyRepository.record(
                MONITOR_KIND,
                channel.getId(),
                CHECK_KIND,
                overallStatus,
                summary,
                details,
                probeResult.httpStatus(),
                durationMs,
                checkedAt
            );
        }

        return new ProviderChannelHealth(
            channel.getId(),
            channel.getChannelName(),
            platform,
            active,
            overallStatus,
            availability,
            runtimeView.status(),
            runtimeView.message(),
            runtimeView.startedAt(),
            probeResult.status(),
            probeResult.message(),
            probeResult.identity(),
            probeResult.httpStatus(),
            probeResult.durationMs(),
            ingressStatus,
            effectiveStats.lastInboundAt(),
            effectiveStats.inbound24h(),
            outboundStatus,
            effectiveStats.lastOutboundAt(),
            effectiveStats.outbound24h(),
            summary,
            checkedAt,
            durationMs
        );
    }

    private RuntimeView buildRuntimeView(boolean active,
                                         BotProcessService.BotProcessStatus processStatus) {
        if (!active) {
            return new RuntimeView("inactive", "Channel is disabled", null);
        }
        if (processStatus == null) {
            return new RuntimeView("error", "Runtime state is unavailable", null);
        }
        String normalized = normalize(processStatus.message());
        if ("running".equals(normalized)) {
            return new RuntimeView("running", "Runtime is running", processStatus.startedAt());
        }
        if ("stopped".equals(normalized)) {
            return new RuntimeView("stopped", "Runtime is stopped", null);
        }
        String message = StringUtils.hasText(processStatus.message())
            ? processStatus.message().trim()
            : "Runtime reported an error";
        return new RuntimeView("error", message, processStatus.startedAt());
    }

    private ProviderProbeResult safeProbe(Channel channel,
                                          String platform) {
        try {
            return providerProbeClient.probe(channel, platform);
        } catch (Exception ex) {
            return ProviderProbeResult.error(
                trimPlatform(platform) + " provider probe failed: " + trimError(ex.getMessage()),
                null,
                null,
                null
            );
        }
    }

    private String resolveActivityStatus(boolean active,
                                         OffsetDateTime lastActivityAt,
                                         long count24h,
                                         OffsetDateTime now) {
        if (!active) {
            return ACTIVITY_DISABLED;
        }
        if (count24h > 0L) {
            return ACTIVITY_ACTIVE;
        }
        if (lastActivityAt == null || now == null) {
            return ACTIVITY_IDLE;
        }
        long ageHours = Math.max(0L, Duration.between(lastActivityAt, now).toHours());
        return ageHours >= STALE_ACTIVITY_HOURS ? ACTIVITY_STALE : ACTIVITY_IDLE;
    }

    private String resolveOverallStatus(boolean active,
                                        String runtimeStatus,
                                        ProviderProbeResult probeResult,
                                        String ingressStatus,
                                        String outboundStatus) {
        if (!active) {
            return STATUS_DISABLED;
        }
        if (!"running".equals(runtimeStatus)) {
            return STATUS_CRITICAL;
        }
        if (probeResult == null || !probeResult.success()) {
            return STATUS_CRITICAL;
        }
        if (ACTIVITY_STALE.equals(ingressStatus) || ACTIVITY_STALE.equals(outboundStatus)) {
            return STATUS_WARNING;
        }
        if (ACTIVITY_IDLE.equals(ingressStatus) && ACTIVITY_IDLE.equals(outboundStatus)) {
            return STATUS_IDLE;
        }
        return STATUS_OK;
    }

    private String resolveAvailability(String overallStatus) {
        String normalized = normalize(overallStatus);
        if (STATUS_DISABLED.equals(normalized)) {
            return AVAILABILITY_DISABLED;
        }
        if (STATUS_CRITICAL.equals(normalized)) {
            return AVAILABILITY_DOWN;
        }
        if (STATUS_OK.equals(normalized) || STATUS_WARNING.equals(normalized) || STATUS_IDLE.equals(normalized)) {
            return AVAILABILITY_UP;
        }
        return AVAILABILITY_UNKNOWN;
    }

    private String buildSummary(RuntimeView runtimeView,
                                ProviderProbeResult probeResult,
                                ActivityStats activityStats,
                                String ingressStatus,
                                String outboundStatus) {
        List<String> parts = new ArrayList<>();
        parts.add("runtime=" + runtimeView.status());
        parts.add("provider=" + probeResult.status());
        parts.add("ingress=" + ingressStatus + "/" + activityStats.inbound24h() + " per 24h");
        parts.add("outbound=" + outboundStatus + "/" + activityStats.outbound24h() + " per 24h");
        if (StringUtils.hasText(probeResult.identity())) {
            parts.add("identity=" + probeResult.identity().trim());
        }
        if (StringUtils.hasText(probeResult.message()) && !STATUS_OK.equals(probeResult.status())) {
            parts.add("probe=" + probeResult.message().trim());
        }
        return String.join("; ", parts);
    }

    private String buildDetails(Channel channel,
                                RuntimeView runtimeView,
                                ProviderProbeResult probeResult,
                                ActivityStats activityStats,
                                String ingressStatus,
                                String outboundStatus) {
        List<String> parts = new ArrayList<>();
        parts.add("channel=" + normalize(channel.getChannelName()));
        parts.add("platform=" + normalizePlatform(channel.getPlatform()));
        parts.add("runtime_status=" + runtimeView.status());
        parts.add("runtime_message=" + trimEmptyToDash(runtimeView.message()));
        parts.add("provider_status=" + probeResult.status());
        if (probeResult.httpStatus() != null) {
            parts.add("provider_http_status=" + probeResult.httpStatus());
        }
        if (probeResult.durationMs() != null) {
            parts.add("provider_duration_ms=" + probeResult.durationMs());
        }
        if (StringUtils.hasText(probeResult.identity())) {
            parts.add("provider_identity=" + probeResult.identity().trim());
        }
        parts.add("ingress_status=" + ingressStatus);
        parts.add("outbound_status=" + outboundStatus);
        parts.add("inbound_24h=" + activityStats.inbound24h());
        parts.add("outbound_24h=" + activityStats.outbound24h());
        if (activityStats.lastInboundAt() != null) {
            parts.add("last_inbound_at=" + activityStats.lastInboundAt());
        }
        if (activityStats.lastOutboundAt() != null) {
            parts.add("last_outbound_at=" + activityStats.lastOutboundAt());
        }
        if (StringUtils.hasText(probeResult.message())) {
            parts.add("probe_message=" + probeResult.message().trim());
        }
        return String.join("; ", parts);
    }

    private Map<Long, ActivityStats> loadActivityStats() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Timestamp since = Timestamp.from(now.minusHours(24).toInstant());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT channel_id,
                   MAX(CASE WHEN lower(COALESCE(sender, '')) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                            THEN timestamp END) AS last_inbound_at,
                   SUM(CASE WHEN lower(COALESCE(sender, '')) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                 AND timestamp >= ?
                            THEN 1 ELSE 0 END) AS inbound_24h,
                   MAX(CASE WHEN lower(COALESCE(sender, '')) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                            THEN timestamp END) AS last_outbound_at,
                   SUM(CASE WHEN lower(COALESCE(sender, '')) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                 AND timestamp >= ?
                            THEN 1 ELSE 0 END) AS outbound_24h
             FROM chat_history
             WHERE channel_id IS NOT NULL
             GROUP BY channel_id
            """,
            since,
            since
        );

        Map<Long, ActivityStats> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long channelId = readLong(row.get("channel_id"));
            if (channelId == null) {
                continue;
            }
            result.put(channelId, new ActivityStats(
                readOffsetDateTime(row.get("last_inbound_at")),
                Optional.ofNullable(readLong(row.get("inbound_24h"))).orElse(0L),
                readOffsetDateTime(row.get("last_outbound_at")),
                Optional.ofNullable(readLong(row.get("outbound_24h"))).orElse(0L)
            ));
        }
        return result;
    }

    private static OffsetDateTime readOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long readLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String normalizePlatform(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? "telegram" : normalized;
    }

    private String trimPlatform(String value) {
        String normalized = normalizePlatform(value);
        return normalized.isBlank() ? "provider" : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown error";
        }
        return trim(message.trim(), 220);
    }

    private String trimEmptyToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
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

    interface ProviderProbeClient {
        ProviderProbeResult probe(Channel channel,
                                  String platform) throws Exception;
    }

    private final class DefaultProviderProbeClient implements ProviderProbeClient {

        @Override
        public ProviderProbeResult probe(Channel channel,
                                         String platform) throws Exception {
            return switch (normalizePlatform(platform)) {
                case "vk" -> probeVk(channel);
                case "max" -> probeMax(channel);
                default -> probeTelegram(channel);
            };
        }

        private ProviderProbeResult probeTelegram(Channel channel) throws Exception {
            long startedAtNs = System.nanoTime();
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, Duration.ofSeconds(10));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildTelegramMethodUrl(channel, "getMe")))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = elapsedMillis(startedAtNs);
            if (response.statusCode() / 100 != 2) {
                return ProviderProbeResult.error(
                    resolveTelegramError(response.body(), response.statusCode()),
                    response.statusCode(),
                    durationMs,
                    null
                );
            }
            JsonNode root = readJson(response.body());
            if (root == null || !root.path("ok").asBoolean(false)) {
                return ProviderProbeResult.error(
                    resolveTelegramError(response.body(), response.statusCode()),
                    response.statusCode(),
                    durationMs,
                    null
                );
            }
            JsonNode result = root.path("result");
            String identity = firstNonBlank(
                result.path("username").asText(""),
                buildDisplayName(result.path("first_name").asText(""), result.path("last_name").asText(""), "")
            );
            return ProviderProbeResult.ok(
                "Telegram getMe passed",
                response.statusCode(),
                durationMs,
                identity
            );
        }

        private ProviderProbeResult probeVk(Channel channel) throws Exception {
            Map<String, Object> config = parseJsonMap(channel != null ? channel.getPlatformConfig() : null);
            Integer groupId = parseInteger(firstValue(config, "group_id", "groupId"));
            if (groupId == null || groupId <= 0) {
                return ProviderProbeResult.error("VK group_id is missing", null, null, null);
            }
            long startedAtNs = System.nanoTime();
            String query = "group_id=" + groupId
                + "&access_token=" + URLEncoder.encode(Objects.toString(channel.getToken(), ""), StandardCharsets.UTF_8)
                + "&v=5.199";
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, Duration.ofSeconds(10));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.vk.com/method/groups.getById?" + query))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = elapsedMillis(startedAtNs);
            if (response.statusCode() / 100 != 2) {
                return ProviderProbeResult.error(
                    resolveVkError(response.body(), response.statusCode()),
                    response.statusCode(),
                    durationMs,
                    null
                );
            }
            JsonNode root = readJson(response.body());
            if (root == null) {
                return ProviderProbeResult.error("VK probe returned unreadable JSON", response.statusCode(), durationMs, null);
            }
            if (root.has("error")) {
                return ProviderProbeResult.error(
                    resolveVkError(response.body(), response.statusCode()),
                    response.statusCode(),
                    durationMs,
                    null
                );
            }
            JsonNode item = root.path("response").isArray() && root.path("response").size() > 0
                ? root.path("response").get(0)
                : root.path("response");
            String identity = firstNonBlank(
                item.path("name").asText(""),
                item.path("screen_name").asText("")
            );
            return ProviderProbeResult.ok(
                "VK groups.getById passed",
                response.statusCode(),
                durationMs,
                identity
            );
        }

        private ProviderProbeResult probeMax(Channel channel) throws Exception {
            long startedAtNs = System.nanoTime();
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, Duration.ofSeconds(10));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MAX_UPDATES_ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", Objects.toString(channel.getToken(), ""))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = elapsedMillis(startedAtNs);
            if (response.statusCode() / 100 != 2) {
                return ProviderProbeResult.error(
                    resolveMaxError(response.body(), response.statusCode()),
                    response.statusCode(),
                    durationMs,
                    null
                );
            }
            JsonNode root = readJson(response.body());
            if (root != null && (root.has("error") || root.has("code"))) {
                return ProviderProbeResult.error(
                    resolveMaxError(response.body(), response.statusCode()),
                    response.statusCode(),
                    durationMs,
                    null
                );
            }
            String identity = root != null ? firstNonBlank(root.path("marker").asText(""), "updates") : "updates";
            return ProviderProbeResult.ok(
                "MAX updates probe passed",
                response.statusCode(),
                durationMs,
                identity
            );
        }

        private JsonNode readJson(String body) {
            if (!StringUtils.hasText(body)) {
                return null;
            }
            try {
                return objectMapper.readTree(body);
            } catch (Exception ignored) {
                return null;
            }
        }

        private Map<String, Object> parseJsonMap(String rawJson) {
            if (!StringUtils.hasText(rawJson)) {
                return Map.of();
            }
            try {
                JsonNode node = objectMapper.readTree(rawJson);
                if (node == null || !node.isObject()) {
                    return Map.of();
                }
                return objectMapper.convertValue(node, Map.class);
            } catch (Exception ignored) {
                return Map.of();
            }
        }

        private Object firstValue(Map<String, Object> map,
                                  String... keys) {
            if (map == null || keys == null) {
                return null;
            }
            for (String key : keys) {
                if (map.containsKey(key)) {
                    return map.get(key);
                }
            }
            return null;
        }

        private Integer parseInteger(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value == null) {
                return null;
            }
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private String buildTelegramMethodUrl(Channel channel,
                                              String methodName) {
            return resolveTelegramBotApiPrefix(channel) + Objects.toString(channel.getToken(), "") + "/" + methodName;
        }

        private String resolveTelegramBotApiPrefix(Channel channel) {
            return normalizeTelegramApiRootUrl(readTelegramApiRootUrl(channel)) + "/bot";
        }

        private String readTelegramApiRootUrl(Channel channel) {
            Map<String, Object> config = parseJsonMap(channel != null ? channel.getPlatformConfig() : null);
            String configured = firstNonBlank(
                valueAsText(config.get("base_url")),
                valueAsText(config.get("baseUrl")),
                valueAsText(config.get("api_base_url")),
                valueAsText(config.get("apiBaseUrl")),
                valueAsText(config.get("telegram_api_base_url")),
                valueAsText(config.get("telegramApiBaseUrl"))
            );
            if (StringUtils.hasText(configured)) {
                return configured;
            }
            String legacy = integrationNetworkService.resolveTelegramLegacyBotApiBaseUrl(channel);
            return StringUtils.hasText(legacy) ? legacy : DEFAULT_TELEGRAM_API_ROOT_URL;
        }

        private String normalizeTelegramApiRootUrl(String rawUrl) {
            if (!StringUtils.hasText(rawUrl)) {
                return DEFAULT_TELEGRAM_API_ROOT_URL;
            }
            String normalized = rawUrl.trim().replaceAll("/+$", "");
            if ((DEFAULT_TELEGRAM_API_ROOT_URL + "/bot").equals(normalized)) {
                return DEFAULT_TELEGRAM_API_ROOT_URL;
            }
            if (normalized.endsWith("/bot")) {
                return normalized.substring(0, normalized.length() - 4);
            }
            return normalized;
        }

        private String resolveTelegramError(String responseBody,
                                            int statusCode) {
            JsonNode root = readJson(responseBody);
            String description = root == null ? "" : firstNonBlank(
                root.path("description").asText(""),
                root.path("error").asText(""),
                root.path("message").asText("")
            );
            return StringUtils.hasText(description)
                ? "Telegram: " + description.trim()
                : "Telegram probe failed with status " + statusCode;
        }

        private String resolveVkError(String responseBody,
                                      int statusCode) {
            JsonNode root = readJson(responseBody);
            JsonNode errorNode = root != null ? root.path("error") : null;
            String description = errorNode == null ? "" : firstNonBlank(
                errorNode.path("error_msg").asText(""),
                errorNode.path("error_text").asText(""),
                errorNode.path("error_code").asText("")
            );
            return StringUtils.hasText(description)
                ? "VK: " + description.trim()
                : "VK probe failed with status " + statusCode;
        }

        private String resolveMaxError(String responseBody,
                                       int statusCode) {
            JsonNode root = readJson(responseBody);
            String description = root == null ? "" : firstNonBlank(
                root.path("message").asText(""),
                root.path("error").asText(""),
                root.path("description").asText(""),
                root.path("code").asText("")
            );
            return StringUtils.hasText(description)
                ? "MAX: " + description.trim()
                : "MAX probe failed with status " + statusCode;
        }

        private String valueAsText(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }

        private String buildDisplayName(String firstName,
                                        String lastName,
                                        String fallback) {
            String joined = String.join(" ", List.of(
                Objects.toString(firstName, "").trim(),
                Objects.toString(lastName, "").trim()
            )).trim();
            return StringUtils.hasText(joined) ? joined : fallback;
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
    }

    private record RuntimeView(String status,
                               String message,
                               OffsetDateTime startedAt) {
    }

    private record ActivityStats(OffsetDateTime lastInboundAt,
                                 long inbound24h,
                                 OffsetDateTime lastOutboundAt,
                                 long outbound24h) {
        private static ActivityStats empty() {
            return new ActivityStats(null, 0L, null, 0L);
        }
    }

    record ProviderProbeResult(String status,
                               boolean success,
                               String message,
                               String identity,
                               Integer httpStatus,
                               Long durationMs) {
        private static ProviderProbeResult ok(String message,
                                              Integer httpStatus,
                                              Long durationMs,
                                              String identity) {
            return new ProviderProbeResult(STATUS_OK, true, message, identity, httpStatus, durationMs);
        }

        private static ProviderProbeResult error(String message,
                                                 Integer httpStatus,
                                                 Long durationMs,
                                                 String identity) {
            return new ProviderProbeResult(STATUS_CRITICAL, false, message, identity, httpStatus, durationMs);
        }

        private static ProviderProbeResult disabled(String platform) {
            return new ProviderProbeResult(STATUS_DISABLED, false, platform + " channel is disabled", null, null, null);
        }
    }

    public record ProviderChannelHealth(Long channelId,
                                        String channelName,
                                        String platform,
                                        boolean active,
                                        String overallStatus,
                                        String availability,
                                        String runtimeStatus,
                                        String runtimeMessage,
                                        OffsetDateTime runtimeStartedAt,
                                        String providerStatus,
                                        String providerMessage,
                                        String providerIdentity,
                                        Integer providerHttpStatus,
                                        Long providerDurationMs,
                                        String ingressStatus,
                                        OffsetDateTime lastInboundAt,
                                        long inbound24h,
                                        String outboundStatus,
                                        OffsetDateTime lastOutboundAt,
                                        long outbound24h,
                                        String summary,
                                        OffsetDateTime checkedAt,
                                        long checkDurationMs) {
    }

    public record AvailabilityOverview(int total,
                                       int active,
                                       int ok,
                                       int warning,
                                       int critical,
                                       int disabled,
                                       int idle,
                                       double availabilityPercent) {
    }

    public record OverviewSnapshot(OffsetDateTime generatedAt,
                                   AvailabilityOverview availabilityOverview,
                                   List<ProviderChannelHealth> items) {
    }

    public record RefreshSummary(int total,
                                 int checked) {
    }
}
