package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
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
        if (channel.getPlatform() != null && !"telegram".equalsIgnoreCase(channel.getPlatform())) {
            log.info("Skipping notification for ticket {}: platform {} not supported", ticketId, channel.getPlatform());
            return;
        }
        if (!StringUtils.hasText(channel.getToken())) {
            log.warn("Unable to notify ticket {}: Telegram token missing for channel {}", ticketId, channel.getId());
            return;
        }
        for (String message : messages) {
            if (!StringUtils.hasText(message)) {
                continue;
            }
            if (sendTelegramMessage(channel, target.userId(), message)) {
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

    private boolean sendTelegramMessage(Channel channel, Long userId, String text) {
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

    private record DialogTarget(Long userId, Long channelId) {}
}
