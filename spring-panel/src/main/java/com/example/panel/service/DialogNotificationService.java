package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DialogNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DialogNotificationService.class);
    private static final HttpClient TELEGRAM_HTTP_CLIENT = HttpClient.newHttpClient();

    private final JdbcTemplate jdbcTemplate;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;

    public DialogNotificationService(JdbcTemplate jdbcTemplate,
                                     ChannelRepository channelRepository,
                                     ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
    }

    public void notifyResolved(String ticketId) {
        List<String> messages = List.of(
                "Диалог закрыт. Спасибо за обращение!",
                "Пожалуйста, оцените диалог по шкале 1-5, ответив числом."
        );
        sendNotifications(ticketId, messages);
    }

    public void notifyReopened(String ticketId) {
        sendNotifications(ticketId, List.of(
                "Ваше обращение снова открыто. Мы продолжаем работу."
        ));
    }

    public boolean notifyUserByLastChannel(Long userId, String message, boolean logHistory) {
        if (userId == null || !StringUtils.hasText(message)) {
            return false;
        }
        Optional<DialogTarget> targetOpt = loadLatestTargetForUser(userId);
        if (targetOpt.isEmpty()) {
            log.warn("Unable to notify user {}: no recent channel found", userId);
            return false;
        }
        DialogTarget target = targetOpt.get();
        return notifyUser(target.userId(), target.channelId(), message, logHistory);
    }

    public boolean notifyUser(Long userId, Long channelId, String message, boolean logHistory) {
        if (userId == null || channelId == null || !StringUtils.hasText(message)) {
            return false;
        }
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            log.warn("Unable to notify user {}: channel {} not found", userId, channelId);
            return false;
        }
        if (!StringUtils.hasText(channel.getToken())) {
            log.warn("Unable to notify user {}: bot token missing for channel {}", userId, channelId);
            return false;
        }
        if (!sendPlatformMessage(channel, userId, message)) {
            return false;
        }
        if (logHistory) {
            logSystemMessage(new DialogTarget(userId, channelId), null, message);
        }
        return true;
    }

    private void sendNotifications(String ticketId, List<String> messages) {
        if (!StringUtils.hasText(ticketId) || messages == null || messages.isEmpty()) {
            return;
        }
        Optional<DialogTarget> targetOpt = loadTarget(ticketId);
        if (targetOpt.isEmpty()) {
            log.warn("Unable to notify ticket {}: no recipient found", ticketId);
            return;
        }
        DialogTarget target = targetOpt.get();
        Channel channel = channelRepository.findById(target.channelId()).orElse(null);
        if (channel == null) {
            log.warn("Unable to notify ticket {}: channel {} not found", ticketId, target.channelId());
            return;
        }
        if (hasWebFormSession(ticketId)) {
            for (String message : messages) {
                if (!StringUtils.hasText(message)) {
                    continue;
                }
                logSystemMessage(target, ticketId, message);
            }
            return;
        }
        if (!StringUtils.hasText(channel.getToken())) {
            log.warn("Unable to notify ticket {}: bot token missing for channel {}", ticketId, channel.getId());
            return;
        }
        for (String message : messages) {
            if (!StringUtils.hasText(message)) {
                continue;
            }
            if (sendPlatformMessage(channel, target.userId(), message)) {
                logSystemMessage(target, ticketId, message);
            }
        }
    }

    private Optional<DialogTarget> loadTarget(String ticketId) {
        return jdbcTemplate.query("""
                        SELECT user_id, channel_id
                          FROM messages
                         WHERE ticket_id = ?
                         ORDER BY created_at DESC
                         LIMIT 1
                        """,
                (rs, rowNum) -> new DialogTarget(rs.getLong("user_id"), rs.getLong("channel_id")),
                ticketId
        ).stream().findFirst();
    }

    private Optional<DialogTarget> loadLatestTargetForUser(Long userId) {
        return jdbcTemplate.query("""
                        SELECT user_id, channel_id
                          FROM messages
                         WHERE user_id = ?
                         ORDER BY created_at DESC
                         LIMIT 1
                        """,
                (rs, rowNum) -> new DialogTarget(rs.getLong("user_id"), rs.getLong("channel_id")),
                userId
        ).stream().findFirst();
    }

    private boolean sendTelegramMessage(Channel channel, Long userId, String text) {
        String platform = channel.getPlatform() != null ? channel.getPlatform().trim().toLowerCase() : "telegram";
        return switch (platform) {
            case "vk" -> sendVkMessage(channel, userId, text);
            case "max" -> sendMaxMessage(channel, userId, text);
            default -> sendTelegramText(channel, userId, text);
        };
    }

    private boolean sendPlatformMessage(Channel channel, Long userId, String text) {
        return sendTelegramMessage(channel, userId, text);
    }

    private boolean sendTelegramText(Channel channel, Long userId, String text) {
        if (userId == null || !StringUtils.hasText(text)) {
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", userId);
        payload.put("text", text);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/sendMessage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Telegram notification failed: status {}", response.statusCode());
                return false;
            }
            return extractTelegramMessageId(response.body()) != null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Telegram notification interrupted");
            return false;
        } catch (IOException ex) {
            log.warn("Telegram notification failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean sendVkMessage(Channel channel, Long userId, String text) {
        if (userId == null || userId <= 0 || userId > Integer.MAX_VALUE || !StringUtils.hasText(text)) {
            return false;
        }
        try {
            String query = "peer_id=" + userId.intValue()
                    + "&random_id=" + Math.abs((int) System.nanoTime())
                    + "&message=" + URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8)
                    + "&access_token=" + URLEncoder.encode(channel.getToken(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&v=5.199";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.vk.com/method/messages.send"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(query, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            return response.statusCode() / 100 == 2 && !response.body().contains("\"error\"");
        } catch (Exception ex) {
            log.warn("VK notification failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean sendMaxMessage(Channel channel, Long userId, String text) {
        if (userId == null || !StringUtils.hasText(text)) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://platform-api.max.ru/messages?user_id=" + userId))
                    .header("Authorization", channel.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("text", text)), java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            return response.statusCode() / 100 == 2;
        } catch (Exception ex) {
            log.warn("MAX notification failed: {}", ex.getMessage());
            return false;
        }
    }

    private void logSystemMessage(DialogTarget target, String ticketId, String message) {
        String timestamp = OffsetDateTime.now().toString();
        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, channel_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                target.userId(),
                "system",
                message,
                timestamp,
                ticketId,
                "system_notification",
                target.channelId()
        );
    }

    private Long extractTelegramMessageId(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messageId = root.path("result").path("message_id");
            return messageId.isNumber() ? messageId.longValue() : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private boolean hasWebFormSession(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM web_form_sessions WHERE ticket_id = ?",
                Integer.class,
                ticketId
        );
        return count != null && count > 0;
    }

    private record DialogTarget(Long userId, Long channelId) {}
}
