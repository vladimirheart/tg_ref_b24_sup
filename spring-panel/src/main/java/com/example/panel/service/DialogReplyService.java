package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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

    public DialogMediaReplyResult sendMediaReply(String ticketId,
                                                 MultipartFile file,
                                                 String caption,
                                                 String operator,
                                                 String storedName,
                                                 String originalName) {
        if (!StringUtils.hasText(ticketId) || file == null || file.isEmpty()) {
            return DialogMediaReplyResult.error("Файл не выбран.");
        }
        Optional<DialogReplyTarget> targetOpt = loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogMediaReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = channelRepository.findById(target.channelId()).orElse(null);
        if (channel == null) {
            return DialogMediaReplyResult.error("Канал для отправки сообщения не найден.");
        }
        if (channel.getPlatform() != null && !"telegram".equalsIgnoreCase(channel.getPlatform())) {
            return DialogMediaReplyResult.error("Отправка доступна только для Telegram-каналов.");
        }
        if (!StringUtils.hasText(channel.getToken())) {
            return DialogMediaReplyResult.error("Не задан токен Telegram-бота для канала.");
        }

        String contentType = file.getContentType();
        String method = resolveTelegramMethod(contentType, originalName);
        String fieldName = resolveTelegramField(method);
        Long telegramMessageId = null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/" + method))
                    .header("Content-Type", "multipart/form-data; boundary=" + MultipartPayload.BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(
                            target.userId(),
                            caption,
                            fieldName,
                            file
                    )))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return DialogMediaReplyResult.error("Ошибка отправки файла в Telegram.");
            }
            telegramMessageId = extractTelegramMessageId(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DialogMediaReplyResult.error("Не удалось отправить файл в Telegram.");
        } catch (IOException ex) {
            return DialogMediaReplyResult.error("Не удалось отправить файл в Telegram.");
        }

        String timestamp = OffsetDateTime.now().toString();
        String messageType = resolveMessageType(contentType, originalName);
        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, attachment, channel_id, tg_message_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                target.userId(),
                "operator",
                caption != null ? caption : "",
                timestamp,
                ticketId,
                messageType,
                storedName,
                target.channelId(),
                telegramMessageId
        );
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        return DialogMediaReplyResult.success(timestamp, telegramMessageId, storedName, messageType, caption);
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

    public record DialogMediaReplyResult(boolean success,
                                         String error,
                                         String timestamp,
                                         Long telegramMessageId,
                                         String storedName,
                                         String messageType,
                                         String message) {
        static DialogMediaReplyResult error(String error) {
            return new DialogMediaReplyResult(false, error, null, null, null, null, null);
        }

        static DialogMediaReplyResult success(String timestamp,
                                              Long telegramMessageId,
                                              String storedName,
                                              String messageType,
                                              String message) {
            return new DialogMediaReplyResult(true, null, timestamp, telegramMessageId, storedName, messageType, message);
        }
    }

    private record DialogReplyTarget(Long userId, Long channelId) {}

    private static String resolveMessageType(String contentType, String filename) {
        String lower = contentType != null ? contentType.toLowerCase() : "";
        if (lower.startsWith("audio/")) return "audio";
        if (lower.startsWith("video/")) return "video";
        if (lower.startsWith("image/")) {
            if (filename != null && filename.toLowerCase().endsWith(".gif")) {
                return "animation";
            }
            return "image";
        }
        if (filename != null && filename.toLowerCase().endsWith(".gif")) {
            return "animation";
        }
        return "document";
    }

    private static String resolveTelegramMethod(String contentType, String filename) {
        String messageType = resolveMessageType(contentType, filename);
        return switch (messageType) {
            case "audio" -> "sendAudio";
            case "video" -> "sendVideo";
            case "animation" -> "sendAnimation";
            case "image" -> "sendPhoto";
            default -> "sendDocument";
        };
    }

    private static String resolveTelegramField(String method) {
        return switch (method) {
            case "sendAudio" -> "audio";
            case "sendVideo" -> "video";
            case "sendAnimation" -> "animation";
            case "sendPhoto" -> "photo";
            default -> "document";
        };
    }

    private static byte[] buildMultipartBody(Long chatId, String caption, String fieldName, MultipartFile file) {
        try {
            List<byte[]> parts = new ArrayList<>();
            parts.add(MultipartPayload.field("chat_id", String.valueOf(chatId)));
            if (StringUtils.hasText(caption)) {
                parts.add(MultipartPayload.field("caption", caption));
            }
            parts.add(MultipartPayload.file(fieldName, file));
            parts.add(MultipartPayload.finish());
            return MultipartPayload.combine(parts);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to build multipart body", ex);
        }
    }

    private static final class MultipartPayload {
        private static final String BOUNDARY = "----BENDER-DIALOGS-BOUNDARY";

        private static byte[] field(String name, String value) {
            String part = "--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n";
            return part.getBytes(StandardCharsets.UTF_8);
        }

        private static byte[] file(String name, MultipartFile file) throws IOException {
            String filename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file.bin");
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            String header = "--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n";
            try (InputStream input = file.getInputStream()) {
                byte[] fileBytes = input.readAllBytes();
                byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
                byte[] footerBytes = "\r\n".getBytes(StandardCharsets.UTF_8);
                return combine(List.of(headerBytes, fileBytes, footerBytes));
            }
        }

        private static byte[] finish() {
            String end = "--" + BOUNDARY + "--\r\n";
            return end.getBytes(StandardCharsets.UTF_8);
        }

        private static byte[] combine(List<byte[]> parts) {
            int length = parts.stream().mapToInt(part -> part.length).sum();
            byte[] all = new byte[length];
            int position = 0;
            for (byte[] part : parts) {
                System.arraycopy(part, 0, all, position, part.length);
                position += part.length;
            }
            return all;
        }
    }
}
