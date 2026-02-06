package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ClientPhone;
import com.example.panel.entity.ClientStatus;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ClientPhoneRepository;
import com.example.panel.repository.ClientStatusRepository;
import com.example.panel.service.BotDatabaseRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Value;
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
    private static final HttpClient TELEGRAM_HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate botJdbcTemplate;
    private final ClientStatusRepository clientStatusRepository;
    private final ClientPhoneRepository clientPhoneRepository;
    private final BotDatabaseRegistry botDatabaseRegistry;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;
    private final Path avatarsRoot;

    public ClientProfileApiController(JdbcTemplate jdbcTemplate,
                                      @Qualifier("botJdbcTemplate") JdbcTemplate botJdbcTemplate,
                                      ClientStatusRepository clientStatusRepository,
                                      ClientPhoneRepository clientPhoneRepository,
                                      BotDatabaseRegistry botDatabaseRegistry,
                                      ChannelRepository channelRepository,
                                      ObjectMapper objectMapper,
                                      @Value("${app.storage.avatars:attachments/avatars}") String avatarsDir)
        throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.botJdbcTemplate = botJdbcTemplate;
        this.clientStatusRepository = clientStatusRepository;
        this.clientPhoneRepository = clientPhoneRepository;
        this.botDatabaseRegistry = botDatabaseRegistry;
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
        this.avatarsRoot = ensureDirectory(avatarsDir);
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
        List<Map<String, Object>> unchangedChannels = new ArrayList<>();
        List<Map<String, Object>> missingChannels = new ArrayList<>();
        String username = null;
        String clientName = null;
        boolean avatarUpdated = false;

        for (ChannelSnapshot channel : channels) {
            Optional<ExternalUserInfo> userInfo = loadExternalUserInfo(userId, channel.channelId());
            ExternalUserInfo info = userInfo.orElse(null);
            String channelUsername = info != null && StringUtils.hasText(info.username()) ? info.username().trim() : null;
            String channelClientName = info != null && StringUtils.hasText(info.clientName()) ? info.clientName().trim() : null;
            boolean updated = false;

            if (channelUsername != null && shouldUpdateField("username", channelUsername, userId, channel.channelId())) {
                jdbcTemplate.update(
                    "UPDATE messages SET username = ? WHERE user_id = ? AND channel_id = ?",
                    channelUsername,
                    userId,
                    channel.channelId()
                );
                updated = true;
            }
            if (channelClientName != null && shouldUpdateField("client_name", channelClientName, userId, channel.channelId())) {
                jdbcTemplate.update(
                    "UPDATE messages SET client_name = ? WHERE user_id = ? AND channel_id = ?",
                    channelClientName,
                    userId,
                    channel.channelId()
                );
                updated = true;
            }

            Optional<Channel> storedChannel = channelRepository.findById(channel.channelId());
            if (storedChannel.isPresent()
                && isTelegramPlatform(channel.platform())
                && StringUtils.hasText(storedChannel.get().getToken())) {
                Optional<TelegramProfilePhoto> photo = fetchTelegramProfilePhoto(storedChannel.get().getToken(), userId);
                if (photo.isPresent()) {
                    boolean saved = storeAvatar(userId, photo.get());
                    if (saved) {
                        updated = true;
                        avatarUpdated = true;
                    }
                }
            }

            if (channel.channelId() == primaryChannel.channelId()) {
                username = channelUsername != null ? channelUsername : username;
                clientName = channelClientName != null ? channelClientName : clientName;
            }

            Map<String, Object> payload = channelToPayload(channel, null);
            payload.put("username", channelUsername);
            payload.put("client_name", channelClientName);
            if (updated) {
                updatedChannels.add(payload);
            } else if (info != null) {
                unchangedChannels.add(payload);
            } else {
                missingChannels.add(channelToPayload(channel, "Нет данных в источнике"));
            }
        }

        String message = buildRefreshMessage(updatedChannels, unchangedChannels, missingChannels);
        String level = missingChannels.isEmpty() && unchangedChannels.isEmpty()
            ? "success"
            : (updatedChannels.isEmpty() ? "warning" : "info");

        log.info("Refreshed external info for client {} with {} updates", userId, updatedChannels.size());
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("username", username);
        response.put("client_name", clientName);
        response.put("updated_channels", updatedChannels);
        response.put("unchanged_channels", unchangedChannels);
        response.put("missing_channels", missingChannels);
        response.put("avatar_updated", avatarUpdated);
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
        for (String table : List.of("bot_users", "users", "messages")) {
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
            boolean hasClientName = columns.contains("client_name");
            if (!hasUsername && !hasFirstName && !hasLastName && !hasFullName && !hasClientName) {
                continue;
            }
            Optional<ExternalUserInfo> info = queryExternalUserInfo(
                template,
                table,
                idColumn,
                hasUsername,
                hasFirstName,
                hasLastName,
                hasFullName,
                hasClientName,
                columns.contains("created_at"),
                columns.contains("created_date"),
                columns.contains("created_time"),
                userId
            );
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
                                                             boolean hasClientName,
                                                             boolean hasCreatedAt,
                                                             boolean hasCreatedDate,
                                                             boolean hasCreatedTime,
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
        if (hasClientName) {
            selectColumns.add("client_name");
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", selectColumns));
        sql.append(" FROM ").append(table).append(" WHERE ").append(idColumn).append(" = ?");
        List<String> nonEmptyChecks = new ArrayList<>();
        if (hasUsername) {
            nonEmptyChecks.add("username IS NOT NULL AND username != ''");
        }
        if (hasFirstName) {
            nonEmptyChecks.add("first_name IS NOT NULL AND first_name != ''");
        }
        if (hasLastName) {
            nonEmptyChecks.add("last_name IS NOT NULL AND last_name != ''");
        }
        if (hasFullName) {
            nonEmptyChecks.add("full_name IS NOT NULL AND full_name != ''");
        }
        if (hasClientName) {
            nonEmptyChecks.add("client_name IS NOT NULL AND client_name != ''");
        }
        if (!nonEmptyChecks.isEmpty()) {
            sql.append(" AND (").append(String.join(" OR ", nonEmptyChecks)).append(")");
        }
        if ("messages".equals(table)) {
            if (hasCreatedAt) {
                sql.append(" ORDER BY created_at DESC");
            } else if (hasCreatedDate && hasCreatedTime) {
                sql.append(" ORDER BY created_date DESC, created_time DESC");
            }
        }
        sql.append(" LIMIT 1");
        try {
            ExternalUserInfo info = template.query(
                sql.toString(),
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    String username = hasUsername ? rs.getString("username") : null;
                    String fullName = hasFullName ? rs.getString("full_name") : null;
                    String firstName = hasFirstName ? rs.getString("first_name") : null;
                    String lastName = hasLastName ? rs.getString("last_name") : null;
                    String clientName = null;
                    if (hasClientName) {
                        String rawClientName = rs.getString("client_name");
                        if (StringUtils.hasText(rawClientName)) {
                            clientName = rawClientName.trim();
                        }
                    }
                    if (clientName == null) {
                        String combinedName = StringUtils.hasText(fullName)
                            ? fullName.trim()
                            : String.join(" ",
                                firstName != null ? firstName.trim() : "",
                                lastName != null ? lastName.trim() : ""
                            ).trim();
                        clientName = StringUtils.hasText(combinedName) ? combinedName : null;
                    }
                    String trimmedUsername = StringUtils.hasText(username) ? username.trim() : null;
                    if (trimmedUsername == null && clientName == null) {
                        return null;
                    }
                    return new ExternalUserInfo(trimmedUsername, clientName);
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
                                       List<Map<String, Object>> unchangedChannels,
                                       List<Map<String, Object>> missingChannels) {
        List<String> parts = new ArrayList<>();
        if (!updatedChannels.isEmpty()) {
            parts.add("Обновлено: " + joinChannelLabels(updatedChannels));
        }
        if (!unchangedChannels.isEmpty()) {
            parts.add("Без изменений: " + joinChannelLabels(unchangedChannels));
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

    private boolean shouldUpdateField(String field, String value, long userId, long channelId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM messages WHERE user_id = ? AND channel_id = ? AND (" + field + " IS NULL OR " + field + " != ?)",
            Integer.class,
            userId,
            channelId,
            value
        );
        return count != null && count > 0;
    }

    private Optional<TelegramProfilePhoto> fetchTelegramProfilePhoto(String token, long userId) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            String url = "https://api.telegram.org/bot" + token + "/getUserProfilePhotos?user_id="
                + URLEncoder.encode(String.valueOf(userId), java.nio.charset.StandardCharsets.UTF_8)
                + "&limit=1";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Telegram getUserProfilePhotos failed with status {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("ok").asBoolean(false)) {
                log.warn("Telegram getUserProfilePhotos returned ok=false");
                return Optional.empty();
            }
            JsonNode photos = root.path("result").path("photos");
            if (!photos.isArray() || photos.isEmpty()) {
                return Optional.empty();
            }
            JsonNode sizes = photos.get(0);
            if (!sizes.isArray() || sizes.isEmpty()) {
                return Optional.empty();
            }
            JsonNode smallest = sizes.get(0);
            JsonNode largest = sizes.get(0);
            for (JsonNode size : sizes) {
                if (comparePhotoSize(size, smallest) < 0) {
                    smallest = size;
                }
                if (comparePhotoSize(size, largest) > 0) {
                    largest = size;
                }
            }
            String thumbFileId = smallest.path("file_id").asText("");
            String fullFileId = largest.path("file_id").asText("");
            if (!StringUtils.hasText(fullFileId)) {
                return Optional.empty();
            }
            byte[] full = downloadTelegramFile(token, fullFileId);
            if (full == null) {
                return Optional.empty();
            }
            byte[] thumb = full;
            if (StringUtils.hasText(thumbFileId) && !thumbFileId.equals(fullFileId)) {
                byte[] thumbBytes = downloadTelegramFile(token, thumbFileId);
                if (thumbBytes != null) {
                    thumb = thumbBytes;
                }
            }
            return Optional.of(new TelegramProfilePhoto(thumb, full));
        } catch (Exception ex) {
            log.warn("Failed to load Telegram profile photo: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private byte[] downloadTelegramFile(String token, String fileId) throws IOException, InterruptedException {
        String filePath = fetchTelegramFilePath(token, fileId);
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        String fileUrl = "https://api.telegram.org/file/bot" + token + "/" + filePath;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(fileUrl))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<byte[]> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            log.warn("Telegram file download failed with status {}", response.statusCode());
            return null;
        }
        return response.body();
    }

    private String fetchTelegramFilePath(String token, String fileId) throws IOException, InterruptedException {
        String url = "https://api.telegram.org/bot" + token + "/getFile?file_id="
            + URLEncoder.encode(fileId, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();
        HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Telegram getFile failed with status {}", response.statusCode());
            return null;
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("ok").asBoolean(false)) {
            log.warn("Telegram getFile returned ok=false");
            return null;
        }
        return root.path("result").path("file_path").asText(null);
    }

    private int comparePhotoSize(JsonNode left, JsonNode right) {
        int leftSize = left.path("file_size").asInt(0);
        int rightSize = right.path("file_size").asInt(0);
        if (leftSize != 0 || rightSize != 0) {
            return Integer.compare(leftSize, rightSize);
        }
        int leftArea = left.path("width").asInt(0) * left.path("height").asInt(0);
        int rightArea = right.path("width").asInt(0) * right.path("height").asInt(0);
        return Integer.compare(leftArea, rightArea);
    }

    private boolean storeAvatar(long userId, TelegramProfilePhoto photo) {
        boolean updated = false;
        try {
            Path thumbPath = resolveAvatarPath(userId, false);
            Path fullPath = resolveAvatarPath(userId, true);
            updated |= storeAvatarFile(thumbPath, photo.thumbBytes());
            updated |= storeAvatarFile(fullPath, photo.fullBytes());
        } catch (IOException ex) {
            log.warn("Failed to store avatar for user {}: {}", userId, ex.getMessage());
        }
        return updated;
    }

    private boolean storeAvatarFile(Path target, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return false;
        }
        long existingSize = Files.isRegularFile(target) ? Files.size(target) : -1;
        if (existingSize == data.length) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path tempFile = Files.createTempFile(target.getParent(), "avatar", ".tmp");
        Files.write(tempFile, data);
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private Path resolveAvatarPath(long userId, boolean full) {
        String suffix = full ? "_full" : "";
        return avatarsRoot.resolve(userId + suffix + ".jpg").normalize();
    }

    private Path ensureDirectory(String directory) throws IOException {
        Path path = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    private boolean isTelegramPlatform(String platform) {
        return platform == null || platform.isBlank() || platform.equalsIgnoreCase("telegram");
    }

    private record TelegramProfilePhoto(byte[] thumbBytes, byte[] fullBytes) {
    }
}
