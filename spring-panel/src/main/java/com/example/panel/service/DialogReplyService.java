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
import java.net.URLEncoder;
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
        return sendReply(ticketId, message, replyToTelegramId, operator, "operator");
    }

    public DialogReplyResult sendReply(String ticketId,
                                       String message,
                                       Long replyToTelegramId,
                                       String operator,
                                       String sender) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(message)) {
            return DialogReplyResult.error("Сообщение не может быть пустым.");
        }
        String safeSender = normalizeSender(sender);
        Optional<DialogReplyTarget> targetOpt = loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = channelRepository.findById(target.channelId()).orElse(null);
        if (channel == null) {
            return DialogReplyResult.error("Канал для отправки сообщения не найден.");
        }
        if (hasWebFormSession(ticketId)) {
            String timestamp = logOutgoingMessage(target, ticketId, message, "operator_message", null, replyToTelegramId, safeSender);
            touchTicketActivity(ticketId, target.userId());
            dialogService.assignResponsibleIfMissing(ticketId, operator);
            return DialogReplyResult.success(timestamp, null);
        }
        if (!StringUtils.hasText(channel.getToken())) {
            return DialogReplyResult.error("Не задан токен бота для канала.");
        }

        String platform = channel.getPlatform() != null ? channel.getPlatform().trim().toLowerCase() : "telegram";
        Long telegramMessageId = null;
        String transportError = switch (platform) {
            case "vk" -> sendVkText(channel, target.userId(), message) ? null : "Не удалось отправить сообщение в VK.";
            case "max" -> sendMaxText(channel, target.userId(), message) ? null : "Не удалось отправить сообщение в MAX.";
            default -> {
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("chat_id", target.userId());
                    payload.put("text", message);
                    if (replyToTelegramId != null) {
                        payload.put("reply_to_message_id", replyToTelegramId);
                    }
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/sendMessage"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                            .build();
                    HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() / 100 != 2) {
                        yield "Ошибка отправки сообщения в Telegram.";
                    }
                    telegramMessageId = extractTelegramMessageId(response.body());
                    yield null;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    yield "Не удалось отправить сообщение в Telegram.";
                } catch (IOException ex) {
                    yield "Не удалось отправить сообщение в Telegram.";
                }
            }
        };
        if (transportError != null) {
            return DialogReplyResult.error(transportError);
        }

        String timestamp = logOutgoingMessage(target, ticketId, message, "operator_message", telegramMessageId, replyToTelegramId, safeSender);
        touchTicketActivity(ticketId, target.userId());
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        return DialogReplyResult.success(timestamp, telegramMessageId);
    }


    public DialogReplyResult editOperatorMessage(String ticketId, Long telegramMessageId, String message, String operator) {
        if (!StringUtils.hasText(ticketId) || telegramMessageId == null || !StringUtils.hasText(message)) {
            return DialogReplyResult.error("Некорректные параметры редактирования.");
        }
        Optional<DialogReplyTarget> targetOpt = loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = channelRepository.findById(target.channelId()).orElse(null);
        if (channel == null || !StringUtils.hasText(channel.getToken())) {
            return DialogReplyResult.error("Канал Telegram не найден.");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", target.userId());
            payload.put("message_id", telegramMessageId);
            payload.put("text", message);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/editMessageText"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return DialogReplyResult.error("Ошибка редактирования сообщения в Telegram.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DialogReplyResult.error("Не удалось отредактировать сообщение в Telegram.");
        } catch (IOException ex) {
            return DialogReplyResult.error("Не удалось отредактировать сообщение в Telegram.");
        }

        int updated = jdbcTemplate.update("""
                UPDATE chat_history
                   SET original_message = COALESCE(original_message, message),
                       message = ?,
                       edited_at = CURRENT_TIMESTAMP
                 WHERE ticket_id = ?
                   AND tg_message_id = ?
                   AND sender = 'operator'
                """, message, ticketId, telegramMessageId);
        if (updated == 0) {
            return DialogReplyResult.error("Сообщение оператора не найдено.");
        }
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        return DialogReplyResult.success(OffsetDateTime.now().toString(), telegramMessageId);
    }

    public DialogReplyResult deleteOperatorMessage(String ticketId, Long telegramMessageId, String operator) {
        if (!StringUtils.hasText(ticketId) || telegramMessageId == null) {
            return DialogReplyResult.error("Некорректные параметры удаления.");
        }
        Optional<DialogReplyTarget> targetOpt = loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = channelRepository.findById(target.channelId()).orElse(null);
        if (channel == null || !StringUtils.hasText(channel.getToken())) {
            return DialogReplyResult.error("Канал Telegram не найден.");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", target.userId());
            payload.put("message_id", telegramMessageId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/deleteMessage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return DialogReplyResult.error("Ошибка удаления сообщения в Telegram.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DialogReplyResult.error("Не удалось удалить сообщение в Telegram.");
        } catch (IOException ex) {
            return DialogReplyResult.error("Не удалось удалить сообщение в Telegram.");
        }

        int updated = jdbcTemplate.update("""
                UPDATE chat_history
                   SET deleted_at = CURRENT_TIMESTAMP
                 WHERE ticket_id = ?
                   AND tg_message_id = ?
                   AND sender = 'operator'
                """, ticketId, telegramMessageId);
        if (updated == 0) {
            return DialogReplyResult.error("Сообщение оператора не найдено.");
        }
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        return DialogReplyResult.success(OffsetDateTime.now().toString(), telegramMessageId);
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
        if (hasWebFormSession(ticketId)) {
            return DialogMediaReplyResult.error("Для внешней формы доступны только текстовые ответы в общем окне диалога.");
        }
        String contentType = file.getContentType();
        String platform = normalizePlatform(channel.getPlatform());
        if (!StringUtils.hasText(channel.getToken())) {
            return DialogMediaReplyResult.error(
                "max".equals(platform) ? "Не задан токен MAX-бота для канала." : "Не задан токен Telegram-бота для канала."
            );
        }

        Long telegramMessageId = null;
        String telegramResponseBody = null;
        String transportError;
        if ("max".equals(platform)) {
            transportError = sendMaxMedia(channel, target.userId(), file, caption, originalName);
        } else if ("telegram".equals(platform)) {
            MediaTransportResult result = sendTelegramMedia(channel, target.userId(), file, caption, originalName);
            transportError = result.error();
            telegramResponseBody = result.responseBody();
            if (transportError == null) {
                telegramMessageId = extractTelegramMessageId(telegramResponseBody);
            }
        } else {
            transportError = "Отправка медиа пока поддерживается только для Telegram и MAX.";
        }
        if (transportError != null) {
            return DialogMediaReplyResult.error(transportError);
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
        touchTicketActivity(ticketId, target.userId());
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

    private String logOutgoingMessage(DialogReplyTarget target,
                                      String ticketId,
                                      String message,
                                      String messageType,
                                      Long telegramMessageId,
                                      Long replyToTelegramId,
                                      String sender) {
        String timestamp = OffsetDateTime.now().toString();
        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, channel_id, tg_message_id, reply_to_tg_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                target.userId(),
                normalizeSender(sender),
                message,
                timestamp,
                ticketId,
                messageType,
                target.channelId(),
                telegramMessageId,
                replyToTelegramId
        );
        return timestamp;
    }

    private String normalizeSender(String sender) {
        String normalized = StringUtils.hasText(sender) ? sender.trim().toLowerCase() : "";
        return switch (normalized) {
            case "operator", "support", "admin", "system", "ai_agent" -> normalized;
            default -> "operator";
        };
    }

    private void touchTicketActivity(String ticketId, Long userId) {
        if (!StringUtils.hasText(ticketId) || userId == null) {
            return;
        }
        String identity = Long.toString(userId);
        String timestamp = OffsetDateTime.now().toString();
        int updated = jdbcTemplate.update("""
                UPDATE ticket_active
                   SET last_seen = ?,
                       user_identity = CASE
                           WHEN user_identity IS NULL OR trim(user_identity) = '' THEN ?
                           ELSE user_identity
                       END
                 WHERE ticket_id = ?
                """,
                timestamp,
                identity,
                ticketId
        );
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO ticket_active(ticket_id, user_identity, last_seen)
                    VALUES (?, ?, ?)
                    """,
                    ticketId,
                    identity,
                    timestamp
            );
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

    private boolean sendVkText(Channel channel, Long userId, String text) {
        if (userId == null || userId <= 0 || userId > Integer.MAX_VALUE || !StringUtils.hasText(text)) {
            return false;
        }
        try {
            String query = "peer_id=" + userId.intValue()
                    + "&random_id=" + Math.abs((int) System.nanoTime())
                    + "&message=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&access_token=" + URLEncoder.encode(channel.getToken(), StandardCharsets.UTF_8)
                    + "&v=5.199";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.vk.com/method/messages.send"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(query, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() / 100 == 2 && !response.body().contains("\"error\"");
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean sendMaxText(Channel channel, Long userId, String text) {
        if (userId == null || !StringUtils.hasText(text)) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://platform-api.max.ru/messages?user_id=" + userId))
                    .header("Authorization", channel.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("text", text)), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() / 100 == 2;
        } catch (Exception ex) {
            return false;
        }
    }

    private MediaTransportResult sendTelegramMedia(Channel channel,
                                                   Long userId,
                                                   MultipartFile file,
                                                   String caption,
                                                   String originalName) {
        if (userId == null) {
            return MediaTransportResult.error("Не удалось определить получателя в Telegram.");
        }
        String method = resolveTelegramMethod(file.getContentType(), originalName);
        String fieldName = resolveTelegramField(method);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/" + method))
                    .header("Content-Type", "multipart/form-data; boundary=" + MultipartPayload.BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(buildTelegramMultipartBody(
                            userId,
                            caption,
                            fieldName,
                            file
                    )))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return MediaTransportResult.error("Ошибка отправки файла в Telegram.");
            }
            return MediaTransportResult.success(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return MediaTransportResult.error("Не удалось отправить файл в Telegram.");
        } catch (IOException ex) {
            return MediaTransportResult.error("Не удалось отправить файл в Telegram.");
        }
    }

    private String sendMaxMedia(Channel channel,
                                Long userId,
                                MultipartFile file,
                                String caption,
                                String originalName) {
        if (userId == null) {
            return "Не удалось определить получателя в MAX.";
        }
        String uploadType = resolveMaxUploadType(file.getContentType(), originalName);
        String attachmentType = resolveMaxAttachmentType(uploadType);
        Map<String, Object> uploadInit = createMaxUpload(channel.getToken(), uploadType);
        if (uploadInit == null) {
            return "Не удалось создать upload-сессию в MAX.";
        }
        String uploadUrl = firstNonBlank(
            stringValue(uploadInit.get("url")),
            stringValue(uploadInit.get("upload_url"))
        );
        if (!StringUtils.hasText(uploadUrl)) {
            return "MAX не вернул URL загрузки файла.";
        }
        Map<String, Object> uploadedPayload = uploadMaxBinary(channel.getToken(), uploadUrl, file);
        if (uploadedPayload == null || uploadedPayload.isEmpty()) {
            return "Не удалось загрузить файл в MAX.";
        }
        if (!uploadedPayload.containsKey("token") && uploadInit.containsKey("token")) {
            uploadedPayload.put("token", uploadInit.get("token"));
        }
        Map<String, Object> requestBody = new LinkedHashMap<>();
        if (StringUtils.hasText(caption)) {
            requestBody.put("text", caption.trim());
        }
        requestBody.put("attachments", List.of(Map.of(
            "type", attachmentType,
            "payload", uploadedPayload
        )));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://platform-api.max.ru/messages?user_id=" + userId))
                    .header("Authorization", channel.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 == 2) {
                return null;
            }
            return "Ошибка отправки файла в MAX.";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "Не удалось отправить файл в MAX.";
        } catch (Exception ex) {
            return "Не удалось отправить файл в MAX.";
        }
    }

    private Map<String, Object> createMaxUpload(String token, String uploadType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://platform-api.max.ru/uploads?type=" + URLEncoder.encode(uploadType, StandardCharsets.UTF_8)))
                    .header("Authorization", token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return readJsonObject(response.body());
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> uploadMaxBinary(String token, String uploadUrl, MultipartFile file) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", token)
                    .header("Content-Type", "multipart/form-data; boundary=" + MultipartPayload.BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(buildSingleFileMultipartBody("data", file)))
                    .build();
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return readJsonObject(response.body());
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> readJsonObject(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                return null;
            }
            return objectMapper.convertValue(root, Map.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private String normalizePlatform(String platform) {
        return platform != null ? platform.trim().toLowerCase() : "telegram";
    }

    private String resolveMaxUploadType(String contentType, String originalName) {
        String messageType = resolveMessageType(contentType, originalName);
        return switch (messageType) {
            case "audio" -> "audio";
            case "video" -> "video";
            case "image", "animation" -> "image";
            default -> "file";
        };
    }

    private String resolveMaxAttachmentType(String uploadType) {
        return switch (uploadType) {
            case "audio" -> "audio";
            case "video" -> "video";
            case "image" -> "image";
            default -> "file";
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record DialogReplyResult(boolean success, String error, String timestamp, Long telegramMessageId) {
        public static DialogReplyResult error(String error) {
            return new DialogReplyResult(false, error, null, null);
        }

        public static DialogReplyResult success(String timestamp, Long telegramMessageId) {
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

    private record MediaTransportResult(String error, String responseBody) {
        private static MediaTransportResult success(String responseBody) {
            return new MediaTransportResult(null, responseBody);
        }

        private static MediaTransportResult error(String error) {
            return new MediaTransportResult(error, null);
        }
    }

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

    private static byte[] buildTelegramMultipartBody(Long chatId, String caption, String fieldName, MultipartFile file) {
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

    private static byte[] buildSingleFileMultipartBody(String fieldName, MultipartFile file) {
        try {
            List<byte[]> parts = new ArrayList<>();
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
