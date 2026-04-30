package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogReplyTransportService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;

    public DialogReplyTransportService(ChannelRepository channelRepository,
                                       ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<Channel> loadChannel(Long channelId) {
        if (channelId == null) {
            return Optional.empty();
        }
        return channelRepository.findById(channelId);
    }

    public DialogReplyTransportResult sendText(Channel channel,
                                               Long userId,
                                               String message,
                                               Long replyToTelegramId) {
        String platform = channel.getPlatform() != null ? channel.getPlatform().trim().toLowerCase() : "telegram";
        return switch (platform) {
            case "vk" -> sendVkText(channel, userId, message)
                    ? DialogReplyTransportResult.success(null)
                    : DialogReplyTransportResult.error("Не удалось отправить сообщение в VK.");
            case "max" -> sendMaxText(channel, userId, message)
                    ? DialogReplyTransportResult.success(null)
                    : DialogReplyTransportResult.error("Не удалось отправить сообщение в MAX.");
            default -> sendTelegramText(channel, userId, message, replyToTelegramId);
        };
    }

    public String editTelegramMessage(Channel channel, Long userId, Long telegramMessageId, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", userId);
            payload.put("message_id", telegramMessageId);
            payload.put("text", message);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/editMessageText"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return "Ошибка редактирования сообщения в Telegram.";
            }
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "Не удалось отредактировать сообщение в Telegram.";
        } catch (IOException ex) {
            return "Не удалось отредактировать сообщение в Telegram.";
        }
    }

    public String deleteTelegramMessage(Channel channel, Long userId, Long telegramMessageId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", userId);
            payload.put("message_id", telegramMessageId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/deleteMessage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return "Ошибка удаления сообщения в Telegram.";
            }
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "Не удалось удалить сообщение в Telegram.";
        } catch (IOException ex) {
            return "Не удалось удалить сообщение в Telegram.";
        }
    }

    public DialogReplyTransportResult sendMedia(Channel channel,
                                                Long userId,
                                                MultipartFile file,
                                                String caption,
                                                String originalName) {
        String platform = normalizePlatform(channel.getPlatform());
        if ("max".equals(platform)) {
            String error = sendMaxMedia(channel, userId, file, caption, originalName);
            return error == null ? DialogReplyTransportResult.success(null) : DialogReplyTransportResult.error(error);
        }
        if ("telegram".equals(platform)) {
            return sendTelegramMedia(channel, userId, file, caption, originalName);
        }
        return DialogReplyTransportResult.error("Отправка медиа пока поддерживается только для Telegram и MAX.");
    }

    private DialogReplyTransportResult sendTelegramText(Channel channel,
                                                        Long userId,
                                                        String message,
                                                        Long replyToTelegramId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", userId);
            payload.put("text", message);
            if (replyToTelegramId != null) {
                payload.put("reply_to_message_id", replyToTelegramId);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + channel.getToken() + "/sendMessage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return DialogReplyTransportResult.error("Ошибка отправки сообщения в Telegram.");
            }
            return DialogReplyTransportResult.success(extractTelegramMessageId(response.body()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DialogReplyTransportResult.error("Не удалось отправить сообщение в Telegram.");
        } catch (IOException ex) {
            return DialogReplyTransportResult.error("Не удалось отправить сообщение в Telegram.");
        }
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
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() / 100 == 2;
        } catch (Exception ex) {
            return false;
        }
    }

    private DialogReplyTransportResult sendTelegramMedia(Channel channel,
                                                         Long userId,
                                                         MultipartFile file,
                                                         String caption,
                                                         String originalName) {
        if (userId == null) {
            return DialogReplyTransportResult.error("Не удалось определить получателя в Telegram.");
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
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return DialogReplyTransportResult.error("Ошибка отправки файла в Telegram.");
            }
            return DialogReplyTransportResult.success(extractTelegramMessageId(response.body()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DialogReplyTransportResult.error("Не удалось отправить файл в Telegram.");
        } catch (IOException ex) {
            return DialogReplyTransportResult.error("Не удалось отправить файл в Telegram.");
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
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return readJsonObject(response.body());
        } catch (Exception ex) {
            return null;
        }
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

    static String resolveMessageType(String contentType, String filename) {
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

    public record DialogReplyTransportResult(String error, Long telegramMessageId) {
        static DialogReplyTransportResult success(Long telegramMessageId) {
            return new DialogReplyTransportResult(null, telegramMessageId);
        }

        static DialogReplyTransportResult error(String error) {
            return new DialogReplyTransportResult(error, null);
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
