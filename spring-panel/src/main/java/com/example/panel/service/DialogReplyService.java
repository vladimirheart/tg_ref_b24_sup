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
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DialogReplyService {

    private static final HttpClient TELEGRAM_HTTP_CLIENT = HttpClient.newHttpClient();

    private final JdbcTemplate jdbcTemplate;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;
    private final DialogService dialogService;

    public DialogReplyService(JdbcTemplate jdbcTemplate,
                              ChannelRepository channelRepository,
                              ObjectMapper objectMapper,
                              DialogService dialogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
        this.dialogService = dialogService;
    }

    public DialogReplyResult sendReply(String ticketId, String message, Long replyToTelegramId, String operator) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(message)) {
            return DialogReplyResult.error("Сообщение не может быть пустым.");
        }
        Optional<DialogReplyTarget> targetOpt = loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = channelRepository.findById(target.channelId()).orElse(null);
        if (channel == null) {
            return DialogReplyResult.error("Канал для отправки сообщения не найден.");
        }
        if (channel.getPlatform() != null && !"telegram".equalsIgnoreCase(channel.getPlatform())) {
            return DialogReplyResult.error("Отправка доступна только для Telegram-каналов.");
        }
        if (!StringUtils.hasText(channel.getToken())) {
            return DialogReplyResult.error("Не задан токен Telegram-бота для канала.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", target.userId());
        payload.put("text", message);
        if (replyToTelegramId != null) {
            payload.put("reply_to_message_id", replyToTelegramId);
        }
        Long telegramMessageId = null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/sendMessage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return DialogReplyResult.error("Ошибка отправки сообщения в Telegram.");
            }
            telegramMessageId = extractTelegramMessageId(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DialogReplyResult.error("Не удалось отправить сообщение в Telegram.");
        } catch (IOException ex) {
            return DialogReplyResult.error("Не удалось отправить сообщение в Telegram.");
        }

        String timestamp = OffsetDateTime.now().toString();
        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, channel_id, tg_message_id, reply_to_tg_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                target.userId(),
                "operator",
                message,
                timestamp,
                ticketId,
                "operator_message",
                target.channelId(),
                telegramMessageId,
                replyToTelegramId
        );
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        return DialogReplyResult.success(timestamp, telegramMessageId);
    }

    private Optional<DialogReplyTarget> loadReplyTarget(String ticketId) {
        return jdbcTemplate.query("""
                        SELECT user_id, channel_id
                          FROM messages
                         WHERE ticket_id = ?
                         ORDER BY created_at DESC
                         LIMIT 1
                        """,
                (rs, rowNum) -> new DialogReplyTarget(rs.getLong("user_id"), rs.getLong("channel_id")),
                ticketId
        ).stream().findFirst();
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

    public record DialogReplyResult(boolean success, String error, String timestamp, Long telegramMessageId) {
        static DialogReplyResult error(String error) {
            return new DialogReplyResult(false, error, null, null);
        }

        static DialogReplyResult success(String timestamp, Long telegramMessageId) {
            return new DialogReplyResult(true, null, timestamp, telegramMessageId);
        }
    }

    private record DialogReplyTarget(Long userId, Long channelId) {}
}
