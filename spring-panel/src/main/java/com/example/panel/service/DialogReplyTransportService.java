package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogReplyTransportService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String DEFAULT_TELEGRAM_API_ROOT_URL = "https://api.telegram.org";
    private static final List<String> DEFAULT_MAX_API_ROOT_URLS = List.of(
            "https://platform-api2.max.ru",
            "https://platform-api.max.ru"
    );
    private static final int MAX_ATTACHMENT_READY_RETRY_ATTEMPTS = 5;
    private static final long MAX_ATTACHMENT_READY_RETRY_DELAY_MILLIS = 500L;
    private static final Duration TELEGRAM_REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ChannelRepository channelRepository;
    private final IntegrationNetworkService integrationNetworkService;
    private final ObjectMapper objectMapper;

    public DialogReplyTransportService(ChannelRepository channelRepository,
                                       IntegrationNetworkService integrationNetworkService,
                                       ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.integrationNetworkService = integrationNetworkService;
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
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, TELEGRAM_REQUEST_TIMEOUT);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildTelegramMethodUrl(channel, "editMessageText")))
                    .timeout(TELEGRAM_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, TELEGRAM_REQUEST_TIMEOUT);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildTelegramMethodUrl(channel, "deleteMessage")))
                    .timeout(TELEGRAM_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
                                                String originalName,
                                                Long replyToTelegramId) {
        String platform = normalizePlatform(channel.getPlatform());
        if ("max".equals(platform)) {
            String error = sendMaxMedia(channel, userId, file, caption, originalName);
            return error == null ? DialogReplyTransportResult.success(null) : DialogReplyTransportResult.error(error);
        }
        if ("telegram".equals(platform)) {
            return sendTelegramMedia(channel, userId, file, caption, originalName, replyToTelegramId);
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
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, TELEGRAM_REQUEST_TIMEOUT);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildTelegramMethodUrl(channel, "sendMessage")))
                    .timeout(TELEGRAM_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return resolveTelegramTransportResult(response, "Ошибка отправки сообщения в Telegram.");
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
                                                         String originalName,
                                                         Long replyToTelegramId) {
        if (userId == null) {
            return DialogReplyTransportResult.error("Не удалось определить получателя в Telegram.");
        }
        String method = resolveTelegramMethod(file.getContentType(), originalName);
        String fieldName = resolveTelegramField(method);
        try {
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, TELEGRAM_REQUEST_TIMEOUT);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildTelegramMethodUrl(channel, method)))
                    .timeout(TELEGRAM_REQUEST_TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + MultipartPayload.BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(buildTelegramMultipartBody(
                            userId,
                            caption,
                            fieldName,
                            file,
                            replyToTelegramId
                    )))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return resolveTelegramTransportResult(response, "Ошибка отправки файла в Telegram.");
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
            return sendMaxMediaMessage(channel.getToken(), userId, requestBody);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "Не удалось отправить файл в MAX.";
        } catch (Exception ex) {
            return "Не удалось отправить файл в MAX.";
        }
    }

    private Map<String, Object> createMaxUpload(String token, String uploadType) {
        for (String apiRoot : DEFAULT_MAX_API_ROOT_URLS) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiRoot + "/uploads?type=" + URLEncoder.encode(uploadType, StandardCharsets.UTF_8)))
                        .header("Authorization", token)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() / 100 != 2) {
                    continue;
                }
                return readJsonObject(response.body());
            } catch (Exception ignored) {
                // Try the next known MAX API root.
            }
        }
        return null;
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

    private String buildTelegramMethodUrl(Channel channel, String methodName) {
        return resolveTelegramBotApiPrefix(channel) + channel.getToken() + "/" + methodName;
    }

    private String resolveTelegramBotApiPrefix(Channel channel) {
        return normalizeTelegramApiRootUrl(readTelegramApiRootUrl(channel)) + "/bot";
    }

    private String readTelegramApiRootUrl(Channel channel) {
        Map<String, Object> config = parseJsonMap(channel != null ? channel.getPlatformConfig() : null);
        String configured = firstText(
                config.get("base_url"),
                config.get("baseUrl"),
                config.get("api_base_url"),
                config.get("apiBaseUrl"),
                config.get("telegram_api_base_url"),
                config.get("telegramApiBaseUrl")
        );
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String legacy = integrationNetworkService.resolveTelegramLegacyBotApiBaseUrl(channel);
        return StringUtils.hasText(legacy) ? legacy : DEFAULT_TELEGRAM_API_ROOT_URL;
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (!node.isObject()) {
                return Map.of();
            }
            return objectMapper.convertValue(node, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String firstText(Object... candidates) {
        if (candidates == null) {
            return "";
        }
        for (Object candidate : candidates) {
            if (candidate instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return "";
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

    private String sendMaxMediaMessage(String token,
                                       Long userId,
                                       Map<String, Object> requestBody) throws IOException, InterruptedException {
        String fallbackError = "MAX media send failed.";
        String lastError = fallbackError;
        for (String apiRoot : DEFAULT_MAX_API_ROOT_URLS) {
            for (int attempt = 0; attempt < MAX_ATTACHMENT_READY_RETRY_ATTEMPTS; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiRoot + "/messages?user_id=" + userId))
                        .header("Authorization", token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() / 100 == 2) {
                    return null;
                }
                String responseBody = response.body();
                lastError = resolveMaxApiError(responseBody, fallbackError);
                if (!"attachment.not.ready".equalsIgnoreCase(resolveMaxApiErrorCode(responseBody))) {
                    break;
                }
                if (attempt + 1 >= MAX_ATTACHMENT_READY_RETRY_ATTEMPTS) {
                    break;
                }
                Thread.sleep(MAX_ATTACHMENT_READY_RETRY_DELAY_MILLIS * (attempt + 1L));
            }
        }
        return lastError;
    }

    private String resolveMaxApiErrorCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return firstNonBlank(
                    root.path("code").asText(""),
                    root.path("error_code").asText(""),
                    root.path("errorCode").asText("")
            );
        } catch (IOException ex) {
            return null;
        }
    }

    private String resolveMaxApiError(String responseBody, String fallbackError) {
        if (!StringUtils.hasText(responseBody)) {
            return fallbackError;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String errorCode = resolveMaxApiErrorCode(responseBody);
            String message = firstNonBlank(
                    root.path("message").asText(""),
                    root.path("error").asText(""),
                    root.path("description").asText("")
            );
            if (StringUtils.hasText(message) && StringUtils.hasText(errorCode)) {
                return "MAX: " + errorCode + " - " + message;
            }
            if (StringUtils.hasText(message)) {
                return "MAX: " + message;
            }
            if (StringUtils.hasText(errorCode)) {
                return "MAX: " + errorCode;
            }
        } catch (IOException ignored) {
        }
        return fallbackError;
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

    private static String sanitizeMultipartFilename(String originalFilename, String contentType) {
        String cleaned = StringUtils.cleanPath(StringUtils.hasText(originalFilename) ? originalFilename : "file");
        String sanitized = cleaned
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_")
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_");
        if (StringUtils.hasText(sanitized) && !sanitized.startsWith(".")) {
            return sanitized;
        }
        return "file" + defaultMultipartExtension(contentType, originalFilename);
    }

    private static String defaultMultipartExtension(String contentType, String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename.trim() : "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
            String extension = filename.substring(dotIndex).replaceAll("[^A-Za-z0-9.]", "");
            if (StringUtils.hasText(extension) && extension.startsWith(".")) {
                return extension;
            }
        }
        return switch (resolveMessageType(contentType, originalFilename)) {
            case "audio" -> ".mp3";
            case "video" -> ".mp4";
            case "animation" -> ".gif";
            case "image" -> ".png";
            default -> ".bin";
        };
    }

    private static byte[] buildTelegramMultipartBody(Long chatId,
                                                     String caption,
                                                     String fieldName,
                                                     MultipartFile file,
                                                     Long replyToTelegramId) {
        try {
            List<byte[]> parts = new ArrayList<>();
            parts.add(MultipartPayload.field("chat_id", String.valueOf(chatId)));
            if (StringUtils.hasText(caption)) {
                parts.add(MultipartPayload.field("caption", caption));
            }
            if (replyToTelegramId != null) {
                parts.add(MultipartPayload.field("reply_to_message_id", String.valueOf(replyToTelegramId)));
            }
            parts.add(MultipartPayload.file(fieldName, file));
            parts.add(MultipartPayload.finish());
            return MultipartPayload.combine(parts);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to build multipart body", ex);
        }
    }

    private DialogReplyTransportResult resolveTelegramTransportResult(HttpResponse<String> response, String fallbackError) {
        if (response == null) {
            return DialogReplyTransportResult.error(fallbackError);
        }
        String responseBody = response.body();
        if (response.statusCode() / 100 != 2) {
            return DialogReplyTransportResult.error(resolveTelegramError(responseBody, fallbackError));
        }
        if (isTelegramApiFailure(responseBody)) {
            return DialogReplyTransportResult.error(resolveTelegramError(responseBody, fallbackError));
        }
        return DialogReplyTransportResult.success(extractTelegramMessageId(responseBody));
    }

    private String resolveTelegramError(String responseBody, String fallbackError) {
        if (!StringUtils.hasText(responseBody)) {
            return fallbackError;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String description = root.path("description").asText("");
            if (StringUtils.hasText(description)) {
                return "Telegram: " + description.trim();
            }
        } catch (IOException ignored) {
            // ignore parse errors and return fallback below
        }
        return fallbackError;
    }

    private boolean isTelegramApiFailure(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.has("ok") && !root.path("ok").asBoolean(true);
        } catch (IOException ignored) {
            return false;
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
            String filename = sanitizeMultipartFilename(file.getOriginalFilename(), file.getContentType());
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
