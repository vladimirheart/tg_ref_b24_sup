package com.example.panel.service;

import com.example.panel.entity.ClientUsername;
import com.example.panel.model.clients.ClientAnalyticsItem;
import com.example.panel.model.clients.ClientBlacklistInfo;
import com.example.panel.model.clients.ClientBlacklistHistoryEntry;
import com.example.panel.model.clients.ClientListItem;
import com.example.panel.model.clients.ClientPhoneEntry;
import com.example.panel.model.clients.ClientProfile;
import com.example.panel.model.clients.ClientProfileHeader;
import com.example.panel.model.clients.ClientProfileStats;
import com.example.panel.model.clients.ClientProfileTicket;
import com.example.panel.model.clients.ClientUsernameEntry;
import com.example.panel.repository.ClientUsernameRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientsService {

    private static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final ClientUsernameRepository clientUsernameRepository;
    private final JdbcTemplate botJdbcTemplate;
    private final BlacklistHistoryService blacklistHistoryService;

    public ClientsService(JdbcTemplate jdbcTemplate,
                          ClientUsernameRepository clientUsernameRepository,
                          @Qualifier("botJdbcTemplate") JdbcTemplate botJdbcTemplate,
                          BlacklistHistoryService blacklistHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientUsernameRepository = clientUsernameRepository;
        this.botJdbcTemplate = botJdbcTemplate;
        this.blacklistHistoryService = blacklistHistoryService;
    }

    public List<ClientListItem> loadClients(String blacklistFilter, String statusFilter) {
        List<ClientRow> baseRows = jdbcTemplate.query(
            """
                SELECT
                    m.user_id,
                    (
                        SELECT latest_username.username
                          FROM messages latest_username
                         WHERE latest_username.user_id = m.user_id
                           AND latest_username.username IS NOT NULL
                           AND latest_username.username <> ''
                         ORDER BY latest_username.created_at DESC NULLS LAST
                         LIMIT 1
                    ) AS username,
                    (
                        SELECT latest_client.client_name
                          FROM messages latest_client
                         WHERE latest_client.user_id = m.user_id
                           AND latest_client.client_name IS NOT NULL
                           AND latest_client.client_name <> ''
                         ORDER BY latest_client.created_at DESC NULLS LAST
                         LIMIT 1
                    ) AS client_name,
                    COUNT(*) AS ticket_count,
                    MIN(m.created_at) AS first_contact,
                    MAX(m.created_at) AS last_contact,
                    (
                        SELECT latest_channel.channel_id
                          FROM messages latest_channel
                         WHERE latest_channel.user_id = m.user_id
                         ORDER BY latest_channel.created_at DESC NULLS LAST
                         LIMIT 1
                    ) AS channel_id
                FROM messages m
                WHERE m.user_id IS NOT NULL
                GROUP BY m.user_id
                ORDER BY last_contact DESC NULLS LAST
                """,
            (rs, rowNum) -> new ClientRow(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("client_name"),
                rs.getLong("ticket_count"),
                rs.getString("first_contact"),
                rs.getString("last_contact"),
                rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null
            )
        );

        Map<String, BlacklistInfo> blacklistMap = loadBlacklistInfo();
        Map<Long, String> statusMap = loadStatusInfo();
        Map<Long, String> channelMap = loadChannelNames(baseRows);

        List<ClientListItem> clients = new ArrayList<>();
        for (ClientRow row : baseRows) {
            BlacklistInfo blacklistInfo = blacklistMap.getOrDefault(
                String.valueOf(row.userId()),
                new BlacklistInfo(false, false)
            );
            String status = statusMap.get(row.userId());
            String channelName = row.channelId() != null ? channelMap.get(row.channelId()) : null;
            int totalMinutes = loadTotalMinutes(row.userId());
            clients.add(new ClientListItem(
                row.userId(),
                row.username(),
                row.clientName(),
                channelName,
                row.ticketCount(),
                totalMinutes,
                formatTimeDuration(totalMinutes),
                formatTimestamp(row.firstContact()),
                formatTimestamp(row.lastContact()),
                status,
                generatePanelId(row.userId()),
                blacklistInfo.blacklisted(),
                blacklistInfo.unblockRequested()
            ));
        }

        if (StringUtils.hasText(blacklistFilter)) {
            clients.removeIf(client -> !String.valueOf(client.blacklisted() ? 1 : 0).equals(blacklistFilter));
        }
        if (StringUtils.hasText(statusFilter)) {
            clients.removeIf(client -> !statusFilter.equals(client.clientStatus()));
        }
        return clients;
    }

    public Optional<ClientProfile> loadClientProfile(long userId) {
        ClientProfileHeader header = jdbcTemplate.query(
            """
                SELECT
                    m.user_id,
                    (
                        SELECT latest_username.username
                          FROM messages latest_username
                         WHERE latest_username.user_id = m.user_id
                           AND latest_username.username IS NOT NULL
                           AND latest_username.username <> ''
                         ORDER BY latest_username.created_at DESC NULLS LAST
                         LIMIT 1
                    ) AS username,
                    (
                        SELECT latest_client.client_name
                          FROM messages latest_client
                         WHERE latest_client.user_id = m.user_id
                           AND latest_client.client_name IS NOT NULL
                           AND latest_client.client_name <> ''
                         ORDER BY latest_client.created_at DESC NULLS LAST
                         LIMIT 1
                    ) AS client_name,
                    m.channel_id,
                    c.channel_name,
                    c.platform,
                    CASE
                        WHEN EXISTS (
                            SELECT 1
                              FROM web_form_sessions w
                             WHERE w.ticket_id = m.ticket_id
                        ) THEN 'web_form'
                        WHEN lower(COALESCE(c.platform, '')) = 'vk' THEN 'vk'
                        WHEN lower(COALESCE(c.platform, '')) = 'max' THEN 'max'
                        WHEN lower(COALESCE(c.platform, '')) = 'telegram'
                             OR c.platform IS NULL
                             OR trim(COALESCE(c.platform, '')) = '' THEN 'telegram'
                        ELSE lower(c.platform)
                    END AS source_key
                FROM messages m
                LEFT JOIN channels c ON m.channel_id = c.id
                WHERE m.user_id = ?
                ORDER BY m.created_at DESC NULLS LAST
                LIMIT 1
                """,
            rs -> rs.next()
                ? new ClientProfileHeader(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getString("client_name"),
                    rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null,
                    rs.getString("channel_name"),
                    rs.getString("platform"),
                    normalizeClientSourceKey(rs.getString("source_key"), rs.getString("platform")),
                    resolveClientSourceLabel(rs.getString("source_key"), rs.getString("platform")),
                    resolveClientSourceIdentifierLabel(rs.getString("source_key"), rs.getString("platform")),
                    resolveClientSourceIdentifierHint(rs.getString("source_key"), rs.getString("platform"))
                )
                : null,
            userId
        );
        if (header == null) {
            return Optional.empty();
        }

        List<ClientProfileTicket> tickets = jdbcTemplate.query(
            """
                SELECT
                    m.ticket_id,
                    m.business,
                    m.city,
                    m.location_type,
                    m.location_name,
                    m.problem,
                    m.created_at,
                    t.status,
                    t.resolved_at AS resolved_at,
                    m.category,
                    m.client_status,
                    m.channel_id,
                    c.channel_name,
                    c.bot_name
                FROM messages m
                LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
                LEFT JOIN channels c ON m.channel_id = c.id
                WHERE m.user_id = ?
                ORDER BY m.created_at DESC NULLS LAST
                """,
            (rs, rowNum) -> new ClientProfileTicket(
                rs.getString("ticket_id"),
                rs.getString("business"),
                rs.getString("city"),
                rs.getString("location_type"),
                rs.getString("location_name"),
                rs.getString("problem"),
                rs.getString("created_at"),
                rs.getString("status"),
                rs.getString("resolved_at"),
                rs.getString("category"),
                rs.getString("client_status"),
                resolveChannelName(rs.getString("channel_name"), rs.getString("bot_name")),
                rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null
            ),
            userId
        );

        ClientProfileStats stats = jdbcTemplate.query(
            """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN t.status = 'resolved' THEN 1 ELSE 0 END) AS resolved,
                    SUM(CASE WHEN t.status <> 'resolved' OR t.status IS NULL THEN 1 ELSE 0 END) AS pending
                FROM messages m
                LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
                WHERE m.user_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return new ClientProfileStats(0, 0, 0);
                }
                return new ClientProfileStats(
                    rs.getInt("total"),
                    rs.getInt("resolved"),
                    rs.getInt("pending")
                );
            },
            userId
        );

        ClientBlacklistInfo blacklistInfo = loadClientBlacklist(userId);
        Double averageRating = loadAverageRating(userId);
        List<ClientAnalyticsItem> ratingStats = loadRatingStats(userId);
        long ratingTotal = ratingStats.stream().mapToLong(ClientAnalyticsItem::count).sum();
        String clientStatus = loadClientStatus(userId);
        List<ClientUsernameEntry> usernameHistory = loadUsernameHistory(userId);
        List<ClientBlacklistHistoryEntry> blacklistHistory = loadBlacklistHistory(userId);
        List<ClientAnalyticsItem> categoryStats = buildCategoryStats(tickets);
        List<ClientAnalyticsItem> locationStats = buildLocationStats(tickets);
        List<ClientPhoneEntry> phonesTelegram = loadClientPhones(userId, "telegram");
        List<ClientPhoneEntry> phonesManual = loadClientPhones(userId, "manual");

        return Optional.of(new ClientProfile(
            header,
            stats,
            tickets,
            blacklistInfo,
            averageRating,
            ratingStats,
            ratingTotal,
            clientStatus,
            usernameHistory,
            blacklistHistory,
            categoryStats,
            locationStats,
            phonesTelegram,
            phonesManual
        ));
    }

    private String normalizeClientSourceKey(String sourceKey, String platform) {
        String normalizedSource = sourceKey != null ? sourceKey.trim().toLowerCase() : "";
        if (!normalizedSource.isEmpty()) {
            return normalizedSource;
        }
        String normalizedPlatform = platform != null ? platform.trim().toLowerCase() : "";
        return normalizedPlatform.isEmpty() ? "telegram" : normalizedPlatform;
    }

    private String resolveClientSourceLabel(String sourceKey, String platform) {
        return switch (normalizeClientSourceKey(sourceKey, platform)) {
            case "vk" -> "VK";
            case "max" -> "MAX";
            case "web_form" -> "Внешняя форма";
            case "telegram" -> "Telegram";
            default -> "Неизвестный источник";
        };
    }

    private String resolveClientSourceIdentifierLabel(String sourceKey, String platform) {
        return switch (normalizeClientSourceKey(sourceKey, platform)) {
            case "vk" -> "ID клиента в VK";
            case "max" -> "ID клиента в MAX";
            case "web_form" -> "ID клиента во внешней форме";
            case "telegram" -> "ID клиента в Telegram";
            default -> "ID клиента в источнике";
        };
    }

    private String resolveClientSourceIdentifierHint(String sourceKey, String platform) {
        return switch (normalizeClientSourceKey(sourceKey, platform)) {
            case "web_form" -> "Для внешней формы используется внутренний синтетический идентификатор сессии клиента.";
            case "vk", "max", "telegram" -> "Показывается внешний идентификатор клиента в исходном канале.";
            default -> "Показывается идентификатор клиента в исходном канале.";
        };
    }

    private List<ClientUsernameEntry> loadUsernameHistory(long userId) {
        List<ClientUsername> entries = clientUsernameRepository.findByUserIdOrderBySeenAtDesc(userId);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<ClientUsernameEntry> history = new ArrayList<>();
        for (ClientUsername entry : entries) {
            history.add(new ClientUsernameEntry(
                entry.getUsername(),
                formatOffsetDateTime(entry.getSeenAt())
            ));
        }
        return history;
    }

    private List<ClientAnalyticsItem> buildCategoryStats(List<ClientProfileTicket> tickets) {
        Map<String, Long> counts = new HashMap<>();
        for (ClientProfileTicket ticket : tickets) {
            String value = ticket.category();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            counts.merge(value.trim(), 1L, Long::sum);
        }
        return toAnalyticsList(counts);
    }

    private List<ClientAnalyticsItem> buildLocationStats(List<ClientProfileTicket> tickets) {
        Map<String, Long> counts = new HashMap<>();
        for (ClientProfileTicket ticket : tickets) {
            String city = ticket.city() != null ? ticket.city().trim() : "";
            String location = ticket.locationName();
            String locationLabel = StringUtils.hasText(location) ? location.trim() : "Без названия";
            String label = StringUtils.hasText(city) ? city + " — " + locationLabel : locationLabel;
            counts.merge(label, 1L, Long::sum);
        }
        return toAnalyticsList(counts);
    }

    private List<ClientAnalyticsItem> toAnalyticsList(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return List.of();
        }
        return counts.entrySet().stream()
            .sorted((a, b) -> {
                int countCompare = Long.compare(b.getValue(), a.getValue());
                return countCompare != 0 ? countCompare : a.getKey().compareToIgnoreCase(b.getKey());
            })
            .map(entry -> new ClientAnalyticsItem(entry.getKey(), entry.getValue()))
            .toList();
    }

    private int loadTotalMinutes(long userId) {
        List<TicketTimingRow> rows = jdbcTemplate.query(
            """
                SELECT DISTINCT
                    t.ticket_id,
                    t.status,
                    t.resolved_at,
                    ch.first_response_time
                FROM messages m
                JOIN tickets t ON m.ticket_id = t.ticket_id
                LEFT JOIN (
                    SELECT ticket_id, MIN(timestamp) AS first_response_time
                    FROM chat_history
                    WHERE sender IS NOT NULL
                      AND TRIM(sender) <> ''
                      AND LOWER(sender) NOT IN ('user', 'клиент', 'client', 'customer', 'пользователь')
                    GROUP BY ticket_id
                ) ch ON t.ticket_id = ch.ticket_id
                WHERE m.user_id = ?
                """,
            (rs, rowNum) -> new TicketTimingRow(
                rs.getString("ticket_id"),
                rs.getString("status"),
                rs.getString("resolved_at"),
                rs.getString("first_response_time")
            ),
            userId
        );

        int total = 0;
        for (TicketTimingRow row : rows) {
            if (!isResolvedStatus(row.status())) {
                continue;
            }
            Instant resolvedAt = parseInstant(row.resolvedAt());
            Instant firstResponseAt = parseInstant(row.firstResponseTime());
            if (resolvedAt == null || firstResponseAt == null || resolvedAt.isBefore(firstResponseAt)) {
                continue;
            }
            total += Math.toIntExact(Duration.between(firstResponseAt, resolvedAt).toMinutes());
        }
        return Math.max(0, total);
    }

    private ClientBlacklistInfo loadClientBlacklist(long userId) {
        return jdbcTemplate.query(
            """
                SELECT is_blacklisted, reason, added_at, added_by, unblock_requested
                FROM client_blacklist
                WHERE user_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return new ClientBlacklistInfo(false, false, null, null, null, null);
                }
                boolean blacklisted = rs.getBoolean("is_blacklisted");
                boolean unblockRequested = rs.getBoolean("unblock_requested");
                String addedAt = formatTimestamp(rs.getString("added_at"));
                String addedBy = rs.getString("added_by");
                String reason = rs.getString("reason");
                String blockedFor = blacklistHistoryService.calculateDurationFromLastBlock(String.valueOf(userId), null)
                    .map(blacklistHistoryService::formatDuration)
                    .orElse(null);
                return new ClientBlacklistInfo(blacklisted, unblockRequested, addedAt, addedBy, reason, blockedFor);
            },
            String.valueOf(userId)
        );
    }

    private List<ClientBlacklistHistoryEntry> loadBlacklistHistory(long userId) {
        if (!blacklistHistoryService.historyTableExists()) {
            return List.of();
        }
        return jdbcTemplate.query(
            """
                SELECT action, reason, actor, created_at
                FROM client_blacklist_history
                WHERE user_id = ?
                ORDER BY created_at DESC
                """,
            (rs, rowNum) -> {
                String action = rs.getString("action");
                String actionLabel = "blocked".equalsIgnoreCase(action)
                    ? "Блокировка"
                    : "unblocked".equalsIgnoreCase(action) ? "Разблокировка" : action;
                return new ClientBlacklistHistoryEntry(
                    action,
                    actionLabel,
                    formatTimestamp(rs.getString("created_at")),
                    rs.getString("actor"),
                    rs.getString("reason")
                );
            },
            String.valueOf(userId)
        );
    }

    private String loadClientStatus(long userId) {
        return jdbcTemplate.query(
            "SELECT status FROM client_statuses WHERE user_id = ?",
            rs -> rs.next() ? rs.getString("status") : null,
            userId
        );
    }

    private List<ClientPhoneEntry> loadClientPhones(long userId, String source) {
        return jdbcTemplate.query(
            """
                SELECT id, phone, label, source, is_active, created_at, created_by
                FROM client_phones
                WHERE user_id = ? AND source = ? AND is_active = TRUE
                ORDER BY created_at DESC
                """,
            (rs, rowNum) -> new ClientPhoneEntry(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("label"),
                rs.getString("source"),
                rs.getObject("is_active") != null ? rs.getBoolean("is_active") : null,
                formatTimestamp(rs.getString("created_at")),
                rs.getString("created_by")
            ),
            userId,
            source
        );
    }

    private Double loadAverageRating(long userId) {
        return botJdbcTemplate.query(
            "SELECT AVG(rating) AS avg_rating FROM feedbacks WHERE user_id = ?",
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                double average = rs.getDouble("avg_rating");
                return rs.wasNull() ? null : average;
            },
            userId
        );
    }

    private List<ClientAnalyticsItem> loadRatingStats(long userId) {
        Map<Integer, Long> counts = new HashMap<>();
        botJdbcTemplate.query(
            "SELECT rating, COUNT(*) AS rating_count FROM feedbacks WHERE user_id = ? GROUP BY rating",
            rs -> {
                while (rs.next()) {
                    Integer rating = rs.getObject("rating") != null ? rs.getInt("rating") : null;
                    if (rating != null && rating >= 1 && rating <= 5) {
                        counts.put(rating, rs.getLong("rating_count"));
                    }
                }
                return null;
            },
            userId
        );

        List<ClientAnalyticsItem> stats = new ArrayList<>();
        for (int rating = 5; rating >= 1; rating--) {
            stats.add(new ClientAnalyticsItem(rating + "★", counts.getOrDefault(rating, 0L)));
        }
        return stats;
    }

    private Map<String, BlacklistInfo> loadBlacklistInfo() {
        return jdbcTemplate.query(
            "SELECT user_id, is_blacklisted, unblock_requested FROM client_blacklist",
            rs -> {
                Map<String, BlacklistInfo> map = new HashMap<>();
                while (rs.next()) {
                    map.put(
                        rs.getString("user_id"),
                        new BlacklistInfo(
                            rs.getBoolean("is_blacklisted"),
                            rs.getBoolean("unblock_requested")
                        )
                    );
                }
                return map;
            }
        );
    }

    private Map<Long, String> loadStatusInfo() {
        return jdbcTemplate.query(
            "SELECT user_id, status FROM client_statuses",
            rs -> {
                Map<Long, String> map = new HashMap<>();
                while (rs.next()) {
                    String status = rs.getString("status");
                    if (StringUtils.hasText(status)) {
                        map.put(rs.getLong("user_id"), status.trim());
                    }
                }
                return map;
            }
        );
    }

    private Map<Long, String> loadChannelNames(List<ClientRow> rows) {
        Set<Long> channelIds = new HashSet<>();
        for (ClientRow row : rows) {
            if (row.channelId() != null) {
                channelIds.add(row.channelId());
            }
        }
        if (channelIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(",", channelIds.stream().map(id -> "?").toList());
        List<Object> params = new ArrayList<>(channelIds);
        return jdbcTemplate.query(
            "SELECT id, channel_name, bot_name FROM channels WHERE id IN (" + placeholders + ")",
            rs -> {
                Map<Long, String> map = new HashMap<>();
                while (rs.next()) {
                    map.put(
                        rs.getLong("id"),
                        resolveChannelName(rs.getString("channel_name"), rs.getString("bot_name"))
                    );
                }
                return map;
            },
            params.toArray()
        );
    }

    private String resolveChannelName(String channelName, String botName) {
        if (StringUtils.hasText(channelName)) {
            return channelName.trim();
        }
        if (StringUtils.hasText(botName)) {
            return botName.trim();
        }
        return null;
    }

    private String formatTimeDuration(Integer minutes) {
        if (minutes == null) {
            return "0 ч 0 мин";
        }
        int value = Math.max(0, minutes);
        int hours = value / 60;
        int mins = value % 60;
        if (hours == 0) {
            return String.format("%d мин", mins);
        }
        if (mins == 0) {
            return String.format("%d ч", hours);
        }
        return String.format("%d ч %d мин", hours, mins);
    }

    private String formatTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim().replace('T', ' ');
        int offsetPlus = trimmed.indexOf('+', 10);
        int offsetMinus = trimmed.indexOf('-', 10);
        int offsetIndex = offsetPlus > 0 ? offsetPlus : offsetMinus;
        if (offsetIndex > 0) {
            trimmed = trimmed.substring(0, offsetIndex);
        }
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex > 0) {
            trimmed = trimmed.substring(0, dotIndex);
        }
        return trimmed;
    }

    private String formatOffsetDateTime(OffsetDateTime value) {
        return value == null ? null : DISPLAY_DATE_FORMAT.format(value);
    }

    private boolean isResolvedStatus(String status) {
        return StringUtils.hasText(status) && "resolved".equalsIgnoreCase(status.trim());
    }

    private Instant parseInstant(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String raw = rawValue.trim();
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            String normalized = raw.replace(' ', 'T');
            if (normalized.matches(".*[+-]\\d{2}$")) {
                normalized += ":00";
            }
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            String compact = raw.replace(' ', 'T');
            if (compact.length() >= 19) {
                return LocalDateTime.parse(compact.substring(0, 19)).toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private String generatePanelId(Long userId) {
        if (userId == null) {
            return "CL-" + Base64.getEncoder()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                .substring(0, 8)
                .toUpperCase();
        }
        UUID uuid = uuid5(NAMESPACE_URL, "client:" + userId);
        return "CL-" + uuid.toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private UUID uuid5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(toBytes(namespace));
            sha1.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();
            hash[6] &= 0x0f;
            hash[6] |= 0x50;
            hash[8] &= 0x3f;
            hash[8] |= 0x80;
            ByteBuffer buffer = ByteBuffer.wrap(hash, 0, 16);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (Exception ex) {
            return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private record ClientRow(
        Long userId,
        String username,
        String clientName,
        long ticketCount,
        String firstContact,
        String lastContact,
        Long channelId
    ) {
    }

    private record BlacklistInfo(boolean blacklisted, boolean unblockRequested) {
    }

    private record TicketTimingRow(
        String ticketId,
        String status,
        String resolvedAt,
        String firstResponseTime
    ) {
    }
}
