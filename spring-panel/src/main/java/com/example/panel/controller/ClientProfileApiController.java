package com.example.panel.controller;

import com.example.panel.entity.ClientPhone;
import com.example.panel.entity.ClientStatus;
import com.example.panel.repository.ClientPhoneRepository;
import com.example.panel.repository.ClientStatusRepository;
import com.example.panel.service.BotDatabaseRegistry;
import jakarta.transaction.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.sqlite.SQLiteDataSource;

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAuthority('PAGE_CLIENTS')")
public class ClientProfileApiController {

    private static final Logger log = LoggerFactory.getLogger(ClientProfileApiController.class);

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate botJdbcTemplate;
    private final ClientStatusRepository clientStatusRepository;
    private final ClientPhoneRepository clientPhoneRepository;
    private final BotDatabaseRegistry botDatabaseRegistry;

    public ClientProfileApiController(JdbcTemplate jdbcTemplate,
                                      @Qualifier("botJdbcTemplate") JdbcTemplate botJdbcTemplate,
                                      ClientStatusRepository clientStatusRepository,
                                      ClientPhoneRepository clientPhoneRepository,
                                      BotDatabaseRegistry botDatabaseRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.botJdbcTemplate = botJdbcTemplate;
        this.clientStatusRepository = clientStatusRepository;
        this.clientPhoneRepository = clientPhoneRepository;
        this.botDatabaseRegistry = botDatabaseRegistry;
    }

    @PostMapping("/{userId}/name")
    @Transactional
    public Map<String, Object> updateName(@PathVariable("userId") long userId,
                                          @RequestBody Map<String, Object> payload) {
        String name = payload.get("client_name") != null ? String.valueOf(payload.get("client_name")) : "";
        String trimmed = name == null ? "" : name.trim();
        jdbcTemplate.update("UPDATE messages SET client_name = ? WHERE user_id = ?", trimmed, userId);
        log.info("Updated client name for {} to '{}'", userId, trimmed);
        return Map.of("ok", true, "client_name", trimmed);
    }

    @PostMapping("/{userId}/status")
    @Transactional
    public Map<String, Object> updateStatus(@PathVariable("userId") long userId,
                                            @RequestBody Map<String, Object> payload,
                                            Authentication authentication) {
        String status = payload.get("client_status") != null ? String.valueOf(payload.get("client_status")) : "";
        String trimmed = StringUtils.hasText(status) ? status.trim() : null;
        ClientStatus entry = clientStatusRepository.findById(userId).orElseGet(() -> {
            ClientStatus fresh = new ClientStatus();
            fresh.setUserId(userId);
            return fresh;
        });
        entry.setStatus(trimmed);
        entry.setUpdatedAt(OffsetDateTime.now());
        entry.setUpdatedBy(authentication != null ? authentication.getName() : "system");
        clientStatusRepository.save(entry);
        log.info("Updated client status for {} to '{}'", userId, trimmed);
        return Map.of("ok", true, "client_status", trimmed);
    }

    @PostMapping("/{userId}/phones")
    @Transactional
    public Map<String, Object> addPhone(@PathVariable("userId") long userId,
                                        @RequestBody Map<String, Object> payload,
                                        Authentication authentication) {
        String phone = payload.get("phone") != null ? String.valueOf(payload.get("phone")) : "";
        if (!StringUtils.hasText(phone)) {
            return Map.of("ok", false, "error", "Введите номер телефона");
        }
        String label = payload.get("label") != null ? String.valueOf(payload.get("label")) : null;
        ClientPhone entry = new ClientPhone();
        entry.setUserId(userId);
        entry.setPhone(phone.trim());
        entry.setLabel(StringUtils.hasText(label) ? label.trim() : null);
        entry.setSource("manual");
        entry.setActive(true);
        entry.setCreatedAt(OffsetDateTime.now());
        entry.setCreatedBy(authentication != null ? authentication.getName() : "system");
        ClientPhone saved = clientPhoneRepository.save(entry);
        log.info("Added manual phone {} for {}", saved.getId(), userId);
        return Map.of(
            "ok", true,
            "id", saved.getId(),
            "phone", saved.getPhone(),
            "label", saved.getLabel(),
            "created_at", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null
        );
    }

    @PatchMapping("/{userId}/phones/{phoneId}")
    @Transactional
    public Map<String, Object> updatePhone(@PathVariable("userId") long userId,
                                           @PathVariable("phoneId") long phoneId,
                                           @RequestBody Map<String, Object> payload) {
        Optional<ClientPhone> entryOpt = clientPhoneRepository.findById(phoneId);
        if (entryOpt.isEmpty()) {
            return Map.of("ok", false, "error", "Телефон не найден");
        }
        ClientPhone entry = entryOpt.get();
        if (!userIdEquals(entry.getUserId(), userId)) {
            return Map.of("ok", false, "error", "Телефон не принадлежит клиенту");
        }
        if (payload.containsKey("label")) {
            String label = payload.get("label") != null ? String.valueOf(payload.get("label")) : null;
            entry.setLabel(StringUtils.hasText(label) ? label.trim() : null);
        }
        if (payload.containsKey("active")) {
            entry.setActive(Boolean.parseBoolean(String.valueOf(payload.get("active"))));
        }
        clientPhoneRepository.save(entry);
        return Map.of("ok", true);
    }

    @PostMapping("/{userId}/refresh")
    @Transactional
    public Map<String, Object> refreshExternalProfile(@PathVariable("userId") long userId) {
        List<ChannelSnapshot> channels = jdbcTemplate.query(
            """
                SELECT
                    m.channel_id,
                    c.channel_name,
                    c.platform,
                    MAX(m.created_at) AS last_seen
                FROM messages m
                LEFT JOIN channels c ON m.channel_id = c.id
                WHERE m.user_id = ? AND m.channel_id IS NOT NULL
                GROUP BY m.channel_id, c.channel_name, c.platform
                ORDER BY last_seen DESC
                """,
            (rs, rowNum) -> new ChannelSnapshot(
                rs.getLong("channel_id"),
                rs.getString("channel_name"),
                rs.getString("platform")
            ),
            userId
        );

        if (channels.isEmpty()) {
            return Map.of("ok", false, "error", "Каналы клиента не найдены");
        }

        ChannelSnapshot primaryChannel = channels.get(0);
        List<Map<String, Object>> updatedChannels = new ArrayList<>();
        List<Map<String, Object>> missingChannels = new ArrayList<>();
        String username = null;
        String clientName = null;

        for (ChannelSnapshot channel : channels) {
            Optional<ExternalUserInfo> userInfo = loadExternalUserInfo(userId, channel.channelId());
            if (userInfo.isEmpty()) {
                missingChannels.add(channelToPayload(channel, "Нет данных в источнике"));
                continue;
            }
            ExternalUserInfo info = userInfo.get();
            String channelUsername = StringUtils.hasText(info.username()) ? info.username().trim() : null;
            String channelClientName = StringUtils.hasText(info.clientName()) ? info.clientName().trim() : null;

            if (channelUsername != null) {
                jdbcTemplate.update(
                    "UPDATE messages SET username = ? WHERE user_id = ? AND channel_id = ?",
                    channelUsername,
                    userId,
                    channel.channelId()
                );
            }
            if (channelClientName != null) {
                jdbcTemplate.update(
                    "UPDATE messages SET client_name = ? WHERE user_id = ? AND channel_id = ?",
                    channelClientName,
                    userId,
                    channel.channelId()
                );
            }

            if (channel.channelId() == primaryChannel.channelId()) {
                username = channelUsername != null ? channelUsername : username;
                clientName = channelClientName != null ? channelClientName : clientName;
            }

            Map<String, Object> payload = channelToPayload(channel, null);
            payload.put("username", channelUsername);
            payload.put("client_name", channelClientName);
            updatedChannels.add(payload);
        }

        String message = buildRefreshMessage(updatedChannels, missingChannels);
        String level = missingChannels.isEmpty() ? "success" : (updatedChannels.isEmpty() ? "warning" : "info");

        log.info("Refreshed external info for client {} with {} updates", userId, updatedChannels.size());
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("username", username);
        response.put("client_name", clientName);
        response.put("updated_channels", updatedChannels);
        response.put("missing_channels", missingChannels);
        response.put("message", message);
        response.put("level", level);
        return response;
    }

    private boolean userIdEquals(Long stored, long incoming) {
        return stored != null && stored == incoming;
    }

    private Optional<ExternalUserInfo> loadExternalUserInfo(long userId, long channelId) {
        Path dbPath = botDatabaseRegistry.resolveBotDatabasePath(channelId);
        Optional<ExternalUserInfo> info = Optional.empty();
        if (Files.exists(dbPath)) {
            JdbcTemplate template = buildBotJdbcTemplate(dbPath);
            info = loadExternalUserInfo(template, userId);
        }
        if (info.isEmpty()) {
            info = loadExternalUserInfo(botJdbcTemplate, userId);
        }
        return info;
    }

    private Optional<ExternalUserInfo> loadExternalUserInfo(JdbcTemplate template, long userId) {
        for (String table : List.of("bot_users", "users")) {
            Set<String> columns = loadTableColumns(template, table);
            if (columns.isEmpty()) {
                continue;
            }
            String idColumn = columns.contains("user_id") ? "user_id" : (columns.contains("id") ? "id" : null);
            if (idColumn == null) {
                continue;
            }
            boolean hasUsername = columns.contains("username");
            boolean hasFirstName = columns.contains("first_name");
            boolean hasLastName = columns.contains("last_name");
            boolean hasFullName = columns.contains("full_name");
            if (!hasUsername && !hasFirstName && !hasLastName && !hasFullName) {
                continue;
            }
            Optional<ExternalUserInfo> info = queryExternalUserInfo(template, table, idColumn, hasUsername, hasFirstName, hasLastName, hasFullName, userId);
            if (info.isPresent()) {
                return info;
            }
        }
        return Optional.empty();
    }

    private Optional<ExternalUserInfo> queryExternalUserInfo(JdbcTemplate template,
                                                             String table,
                                                             String idColumn,
                                                             boolean hasUsername,
                                                             boolean hasFirstName,
                                                             boolean hasLastName,
                                                             boolean hasFullName,
                                                             long userId) {
        List<String> selectColumns = new ArrayList<>();
        if (hasUsername) {
            selectColumns.add("username");
        }
        if (hasFirstName) {
            selectColumns.add("first_name");
        }
        if (hasLastName) {
            selectColumns.add("last_name");
        }
        if (hasFullName) {
            selectColumns.add("full_name");
        }
        String sql = "SELECT " + String.join(", ", selectColumns) +
            " FROM " + table + " WHERE " + idColumn + " = ?";
        try {
            ExternalUserInfo info = template.query(
                sql,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    String username = hasUsername ? rs.getString("username") : null;
                    String fullName = hasFullName ? rs.getString("full_name") : null;
                    String firstName = hasFirstName ? rs.getString("first_name") : null;
                    String lastName = hasLastName ? rs.getString("last_name") : null;
                    String combinedName = StringUtils.hasText(fullName)
                        ? fullName.trim()
                        : String.join(" ",
                            firstName != null ? firstName.trim() : "",
                            lastName != null ? lastName.trim() : ""
                        ).trim();
                    String clientName = StringUtils.hasText(combinedName) ? combinedName : null;
                    return new ExternalUserInfo(username, clientName);
                },
                userId
            );
            return Optional.ofNullable(info);
        } catch (DataAccessException ex) {
            log.debug("Failed to read {} from {}: {}", table, idColumn, ex.getMessage());
            return Optional.empty();
        }
    }

    private JdbcTemplate buildBotJdbcTemplate(Path dbPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return new JdbcTemplate(dataSource);
    }

    private Set<String> loadTableColumns(JdbcTemplate template, String table) {
        try {
            return template.query(
                "PRAGMA table_info(" + table + ")",
                rs -> {
                    Set<String> columns = new HashSet<>();
                    while (rs.next()) {
                        columns.add(rs.getString("name"));
                    }
                    return columns;
                }
            );
        } catch (DataAccessException ex) {
            log.debug("Failed to read table info for {}: {}", table, ex.getMessage());
            return Set.of();
        }
    }

    private Map<String, Object> channelToPayload(ChannelSnapshot channel, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel_id", channel.channelId());
        payload.put("channel_name", channel.channelName());
        payload.put("platform", channel.platform());
        if (reason != null) {
            payload.put("reason", reason);
        }
        return payload;
    }

    private String buildRefreshMessage(List<Map<String, Object>> updatedChannels,
                                       List<Map<String, Object>> missingChannels) {
        List<String> parts = new ArrayList<>();
        if (!updatedChannels.isEmpty()) {
            parts.add("Обновлено: " + joinChannelLabels(updatedChannels));
        }
        if (!missingChannels.isEmpty()) {
            parts.add("Нет данных: " + joinChannelLabels(missingChannels));
        }
        return parts.isEmpty() ? "Нет данных для обновления." : String.join(". ", parts) + ".";
    }

    private String joinChannelLabels(List<Map<String, Object>> channels) {
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> channel : channels) {
            String platform = channel.get("platform") != null ? channel.get("platform").toString() : "";
            String platformLabel = "vk".equalsIgnoreCase(platform) ? "VK" : "Telegram";
            String channelName = channel.get("channel_name") != null ? channel.get("channel_name").toString() : "";
            labels.add(StringUtils.hasText(channelName) ? platformLabel + " (" + channelName + ")" : platformLabel);
        }
        return String.join(", ", labels);
    }

    private record ChannelSnapshot(long channelId, String channelName, String platform) {
    }

    private record ExternalUserInfo(String username, String clientName) {
    }
}
