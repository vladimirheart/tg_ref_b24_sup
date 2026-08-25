package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogReplyTransportService {

    private static final Logger log = LoggerFactory.getLogger(DialogReplyTransportService.class);

    private static final String DEFAULT_TELEGRAM_API_ROOT_URL = "https://api.telegram.org";
    private static final List<String> DEFAULT_MAX_API_ROOT_URLS = List.of(
        "https://platform-api2.max.ru",
        "https://platform-api.max.ru"
    );
    private static final int MAX_ATTACHMENT_READY_RETRY_ATTEMPTS = 5;
    private static final long MAX_ATTACHMENT_READY_RETRY_DELAY_MILLIS = 500L;
    private static final Duration TELEGRAM_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TELEGRAM_MEDIA_REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration GENERIC_PROVIDER_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long TELEGRAM_MAX_DOCUMENT_BYTES = 50L * 1024L * 1024L;
    private static final int TELEGRAM_MAX_CAPTION_LENGTH = 1024;
    private static final int RESPONSE_BODY_LOG_LIMIT = 1_000;
    private static final int RESPONSE_EXCERPT_LIMIT = 800;
    private static final String MULTIPART_BOUNDARY = "----BENDER-DIALOGS-BOUNDARY";
    private static final String TELEGRAM_MEDIA_METHOD = "sendDocument";

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
        String platform = normalizePlatform(channel != null ? channel.getPlatform() : null);
        return switch (platform) {
            case "vk" -> sendVkText(channel, userId, message);
            case "max" -> sendMaxText(channel, userId, message);
            default -> sendTelegramText(channel, userId, message, replyToTelegramId);
        };
    }

    public DialogReplyTransportResult editTelegramMessage(Channel channel,
                                                          Long userId,
                                                          Long telegramMessageId,
                                                          String message) {
        String platform = normalizePlatform(channel != null ? channel.getPlatform() : null);
        if (!"telegram".equals(platform)) {
            return DialogReplyTransportResult.failure(
                "Редактирование provider-side сообщений сейчас поддерживается только для Telegram.",
                "validation_error",
                "warning",
                "terminal",
                null,
                null,
                platform,
                null,
                null
            );
        }
        long startedAtNs = System.nanoTime();
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
            DialogReplyTransportResult result = resolveTelegramTransportResult(
                response,
                "Ошибка редактирования сообщения в Telegram.",
                elapsedMillis(startedAtNs)
            );
            return result.success()
                ? DialogReplyTransportResult.success(telegramMessageId, result.durationMs())
                : result;
        } catch (HttpTimeoutException ex) {
            return failureForException(
                "Не удалось отредактировать сообщение в Telegram: превышено время ожидания.",
                "timeout",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failureForException(
                "Не удалось отредактировать сообщение в Telegram.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (IOException ex) {
            return failureForException(
                "Не удалось отредактировать сообщение в Telegram.",
                "network_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        }
    }

    public DialogReplyTransportResult deleteTelegramMessage(Channel channel,
                                                            Long userId,
                                                            Long telegramMessageId) {
        String platform = normalizePlatform(channel != null ? channel.getPlatform() : null);
        if (!"telegram".equals(platform)) {
            return DialogReplyTransportResult.failure(
                "Удаление provider-side сообщений сейчас поддерживается только для Telegram.",
                "validation_error",
                "warning",
                "terminal",
                null,
                null,
                platform,
                null,
                null
            );
        }
        long startedAtNs = System.nanoTime();
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
            DialogReplyTransportResult result = resolveTelegramTransportResult(
                response,
                "Ошибка удаления сообщения в Telegram.",
                elapsedMillis(startedAtNs)
            );
            return result.success()
                ? DialogReplyTransportResult.success(telegramMessageId, result.durationMs())
                : result;
        } catch (HttpTimeoutException ex) {
            return failureForException(
                "Не удалось удалить сообщение в Telegram: превышено время ожидания.",
                "timeout",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failureForException(
                "Не удалось удалить сообщение в Telegram.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (IOException ex) {
            return failureForException(
                "Не удалось удалить сообщение в Telegram.",
                "network_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        }
    }

    public DialogReplyTransportResult sendMedia(Channel channel,
                                                Long userId,
                                                MultipartFile file,
                                                String caption,
                                                String originalName,
                                                Long replyToTelegramId) {
        String platform = normalizePlatform(channel != null ? channel.getPlatform() : null);
        if ("max".equals(platform)) {
            return sendMaxMedia(channel, userId, file, caption, originalName);
        }
        if ("telegram".equals(platform)) {
            return sendTelegramMediaSafely(channel, userId, file, caption, originalName, replyToTelegramId);
        }
        return DialogReplyTransportResult.failure(
            "Отправка медиа пока поддерживается только для Telegram и MAX.",
            "validation_error",
            "warning",
            "terminal",
            null,
            null,
            null,
            null,
            null
        );
    }

    private DialogReplyTransportResult sendTelegramText(Channel channel,
                                                        Long userId,
                                                        String message,
                                                        Long replyToTelegramId) {
        long startedAtNs = System.nanoTime();
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
            return resolveTelegramTransportResult(
                response,
                "Ошибка отправки сообщения в Telegram.",
                elapsedMillis(startedAtNs)
            );
        } catch (HttpTimeoutException ex) {
            return failureForException(
                "Не удалось отправить сообщение в Telegram: превышено время ожидания.",
                "timeout",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failureForException(
                "Не удалось отправить сообщение в Telegram.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (IOException ex) {
            return failureForException(
                "Не удалось отправить сообщение в Telegram.",
                "network_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        }
    }

    private DialogReplyTransportResult sendVkText(Channel channel, Long userId, String text) {
        if (userId == null || userId <= 0 || userId > Integer.MAX_VALUE || !StringUtils.hasText(text)) {
            return DialogReplyTransportResult.failure(
                "Некорректные параметры отправки в VK.",
                "validation_error",
                "warning",
                "terminal",
                null,
                null,
                null,
                null,
                null
            );
        }
        long startedAtNs = System.nanoTime();
        try {
            String query = "peer_id=" + userId.intValue()
                + "&random_id=" + Math.abs((int) System.nanoTime())
                + "&message=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(channel.getToken(), StandardCharsets.UTF_8)
                + "&v=5.199";
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, GENERIC_PROVIDER_REQUEST_TIMEOUT);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.vk.com/method/messages.send"))
                .timeout(GENERIC_PROVIDER_REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(query, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resolveVkTransportResult(response, elapsedMillis(startedAtNs));
        } catch (HttpTimeoutException ex) {
            return failureForException(
                "Не удалось отправить сообщение в VK: превышено время ожидания.",
                "timeout",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failureForException(
                "Не удалось отправить сообщение в VK.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (IOException ex) {
            return failureForException(
                "Не удалось отправить сообщение в VK.",
                "network_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        }
    }

    private DialogReplyTransportResult sendMaxText(Channel channel, Long userId, String text) {
        if (userId == null || !StringUtils.hasText(text)) {
            return DialogReplyTransportResult.failure(
                "Некорректные параметры отправки в MAX.",
                "validation_error",
                "warning",
                "terminal",
                null,
                null,
                null,
                null,
                null
            );
        }
        long startedAtNs = System.nanoTime();
        try {
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, GENERIC_PROVIDER_REQUEST_TIMEOUT);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://platform-api.max.ru/messages?user_id=" + userId))
                .timeout(GENERIC_PROVIDER_REQUEST_TIMEOUT)
                .header("Authorization", channel.getToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("text", text)), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resolveMaxTransportResult(
                response,
                "Не удалось отправить сообщение в MAX.",
                elapsedMillis(startedAtNs)
            );
        } catch (HttpTimeoutException ex) {
            return failureForException(
                "Не удалось отправить сообщение в MAX: превышено время ожидания.",
                "timeout",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failureForException(
                "Не удалось отправить сообщение в MAX.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (IOException ex) {
            return failureForException(
                "Не удалось отправить сообщение в MAX.",
                "network_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        }
    }

    private DialogReplyTransportResult sendTelegramMediaSafely(Channel channel,
                                                               Long userId,
                                                               MultipartFile file,
                                                               String caption,
                                                               String originalName,
                                                               Long replyToTelegramId) {
        long startedAtNs = System.nanoTime();
        if (userId == null) {
            return DialogReplyTransportResult.failure(
                "Не удалось определить получателя в Telegram.",
                "validation_error",
                "warning",
                "terminal",
                null,
                null,
                null,
                null,
                null
            );
        }
        String normalizedCaption = normalizeCaption(caption);
        String validationError = validateTelegramMedia(file, normalizedCaption);
        if (validationError != null) {
            return DialogReplyTransportResult.failure(
                validationError,
                "validation_error",
                "warning",
                "terminal",
                null,
                null,
                null,
                null,
                null
            );
        }

        String resolvedOriginalName = resolveOriginalFilename(file, originalName);
        String sanitizedFilename = sanitizeMultipartFilename(resolvedOriginalName, file.getContentType());
        String contentType = normalizeContentType(file.getContentType());
        long fileSize = safeSize(file);

        try {
            return sendTelegramMediaDirect(
                channel,
                userId,
                file,
                normalizedCaption,
                replyToTelegramId,
                TELEGRAM_MEDIA_METHOD,
                resolvedOriginalName,
                sanitizedFilename,
                contentType,
                fileSize,
                elapsedMillis(startedAtNs)
            );
        } catch (HttpTimeoutException ex) {
            logTelegramMediaException(TELEGRAM_MEDIA_METHOD, userId, resolvedOriginalName, sanitizedFilename, contentType, fileSize, ex);
            return failureForException(
                "Не удалось отправить файл в Telegram: превышено время ожидания загрузки.",
                "timeout",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logTelegramMediaException(TELEGRAM_MEDIA_METHOD, userId, resolvedOriginalName, sanitizedFilename, contentType, fileSize, ex);
            return failureForException(
                "Не удалось отправить файл в Telegram. Проверьте сеть и повторите попытку.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (IOException ex) {
            logTelegramMediaException(TELEGRAM_MEDIA_METHOD, userId, resolvedOriginalName, sanitizedFilename, contentType, fileSize, ex);
            return failureForException(
                "Не удалось отправить файл в Telegram. Проверьте сеть и повторите попытку.",
                "network_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (RuntimeException ex) {
            logTelegramMediaException(TELEGRAM_MEDIA_METHOD, userId, resolvedOriginalName, sanitizedFilename, contentType, fileSize, ex);
            return failureForException(
                "Не удалось отправить файл в Telegram. Проверьте сеть и повторите попытку.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        }
    }

    private DialogReplyTransportResult sendTelegramMediaDirect(Channel channel,
                                                               Long userId,
                                                               MultipartFile file,
                                                               String caption,
                                                               Long replyToTelegramId,
                                                               String method,
                                                               String originalName,
                                                               String sanitizedFilename,
                                                               String contentType,
                                                               long fileSize,
                                                               Long durationMs) throws IOException, InterruptedException {
        HttpClient client = integrationNetworkService.createChannelHttpClient(channel, TELEGRAM_MEDIA_REQUEST_TIMEOUT);
        String fieldName = resolveTelegramField(method);
        Path multipartFile = createMultipartTempFile(
            List.of(
                new MultipartField("chat_id", String.valueOf(userId)),
                new MultipartField("caption", caption),
                new MultipartField("reply_to_message_id", replyToTelegramId != null ? String.valueOf(replyToTelegramId) : null)
            ),
            fieldName,
            file,
            sanitizedFilename,
            contentType
        );
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildTelegramMethodUrl(channel, method)))
                .timeout(TELEGRAM_MEDIA_REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofFile(multipartFile))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || isTelegramApiFailure(response.body())) {
                logTelegramMediaApiError(method, response.statusCode(), userId, originalName, sanitizedFilename, contentType, fileSize, response.body());
            }
            return resolveTelegramTransportResult(
                response,
                "Не удалось отправить файл в Telegram.",
                durationMs
            );
        } finally {
            deleteTempFileQuietly(multipartFile);
        }
    }

    private DialogReplyTransportResult sendMaxMedia(Channel channel,
                                                    Long userId,
                                                    MultipartFile file,
                                                    String caption,
                                                    String originalName) {
        long startedAtNs = System.nanoTime();
        if (userId == null) {
            return DialogReplyTransportResult.failure(
                "Не удалось определить получателя в MAX.",
                "validation_error",
                "warning",
                "terminal",
                null,
                null,
                null,
                null,
                null
            );
        }
        String uploadType = resolveMaxUploadType(file != null ? file.getContentType() : null, originalName);
        String attachmentType = resolveMaxAttachmentType(uploadType);
        Map<String, Object> uploadInit = createMaxUpload(channel, channel.getToken(), uploadType);
        if (uploadInit == null) {
            return DialogReplyTransportResult.failure(
                "Не удалось создать upload-сессию в MAX.",
                "provider_error",
                "critical",
                "transient",
                null,
                null,
                null,
                null,
                elapsedMillis(startedAtNs)
            );
        }
        String uploadUrl = firstNonBlank(
            stringValue(uploadInit.get("url")),
            stringValue(uploadInit.get("upload_url"))
        );
        if (!StringUtils.hasText(uploadUrl)) {
            return DialogReplyTransportResult.failure(
                "MAX не вернул URL загрузки файла.",
                "provider_error",
                "critical",
                "transient",
                null,
                null,
                null,
                null,
                elapsedMillis(startedAtNs)
            );
        }
        Map<String, Object> uploadedPayload = uploadMaxBinary(channel, channel.getToken(), uploadUrl, file, originalName);
        if (uploadedPayload == null || uploadedPayload.isEmpty()) {
            return DialogReplyTransportResult.failure(
                "Не удалось загрузить файл в MAX.",
                "provider_error",
                "critical",
                "transient",
                null,
                null,
                null,
                null,
                elapsedMillis(startedAtNs)
            );
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
            return sendMaxMediaMessage(channel, channel.getToken(), userId, requestBody, elapsedMillis(startedAtNs));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failureForException(
                "Не удалось отправить файл в MAX.",
                "unknown_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        } catch (IOException ex) {
            return failureForException(
                "Не удалось отправить файл в MAX.",
                "network_error",
                "critical",
                "transient",
                ex,
                elapsedMillis(startedAtNs)
            );
        }
    }

    private Map<String, Object> createMaxUpload(Channel channel, String token, String uploadType) {
        for (String apiRoot : DEFAULT_MAX_API_ROOT_URLS) {
            try {
                HttpClient client = integrationNetworkService.createChannelHttpClient(channel, GENERIC_PROVIDER_REQUEST_TIMEOUT);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiRoot + "/uploads?type=" + URLEncoder.encode(uploadType, StandardCharsets.UTF_8)))
                    .timeout(GENERIC_PROVIDER_REQUEST_TIMEOUT)
                    .header("Authorization", token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    private Map<String, Object> uploadMaxBinary(Channel channel,
                                                String token,
                                                String uploadUrl,
                                                MultipartFile file,
                                                String originalName) {
        String sanitizedFilename = sanitizeMultipartFilename(resolveOriginalFilename(file, originalName), file != null ? file.getContentType() : null);
        String contentType = normalizeContentType(file != null ? file.getContentType() : null);
        Path multipartFile = null;
        try {
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, GENERIC_PROVIDER_REQUEST_TIMEOUT);
            multipartFile = createMultipartTempFile(List.of(), "data", file, sanitizedFilename, contentType);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(GENERIC_PROVIDER_REQUEST_TIMEOUT)
                .header("Authorization", token)
                .header("Content-Type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofFile(multipartFile))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return readJsonObject(response.body());
        } catch (Exception ex) {
            return null;
        } finally {
            deleteTempFileQuietly(multipartFile);
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

    private Long extractVkMessageId(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.path("response");
            return response.isNumber() ? response.longValue() : null;
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
        return platform != null ? platform.trim().toLowerCase(Locale.ROOT) : "telegram";
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

    private DialogReplyTransportResult sendMaxMediaMessage(Channel channel,
                                                           String token,
                                                           Long userId,
                                                           Map<String, Object> requestBody,
                                                           Long durationMs) throws IOException, InterruptedException {
        DialogReplyTransportResult lastFailure = DialogReplyTransportResult.failure(
            "Не удалось отправить файл в MAX.",
            "provider_error",
            "critical",
            "transient",
            null,
            null,
            null,
            null,
            durationMs
        );
        for (String apiRoot : DEFAULT_MAX_API_ROOT_URLS) {
            for (int attempt = 0; attempt < MAX_ATTACHMENT_READY_RETRY_ATTEMPTS; attempt++) {
                HttpClient client = integrationNetworkService.createChannelHttpClient(channel, GENERIC_PROVIDER_REQUEST_TIMEOUT);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiRoot + "/messages?user_id=" + userId))
                    .timeout(GENERIC_PROVIDER_REQUEST_TIMEOUT)
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                DialogReplyTransportResult result = resolveMaxTransportResult(
                    response,
                    "Не удалось отправить файл в MAX.",
                    durationMs
                );
                if (result.success()) {
                    return result;
                }
                lastFailure = result;
                String responseBody = response.body();
                if (!"attachment.not.ready".equalsIgnoreCase(resolveMaxApiErrorCode(responseBody))) {
                    break;
                }
                if (attempt + 1 >= MAX_ATTACHMENT_READY_RETRY_ATTEMPTS) {
                    break;
                }
                Thread.sleep(MAX_ATTACHMENT_READY_RETRY_DELAY_MILLIS * (attempt + 1L));
            }
        }
        return lastFailure;
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
            // fallback below
        }
        return fallbackError;
    }

    static String resolveMessageType(String contentType, String filename) {
        String lower = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        if (lower.startsWith("audio/")) return "audio";
        if (lower.startsWith("video/")) return "video";
        if (lower.startsWith("image/")) {
            if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                return "animation";
            }
            return "image";
        }
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".gif")) {
            return "animation";
        }
        return "document";
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

    private DialogReplyTransportResult resolveTelegramTransportResult(HttpResponse<String> response,
                                                                      String fallbackError,
                                                                      Long durationMs) {
        if (response == null) {
            return DialogReplyTransportResult.failure(
                fallbackError,
                "unknown_error",
                "critical",
                "transient",
                null,
                null,
                null,
                null,
                durationMs
            );
        }
        String responseBody = response.body();
        if (response.statusCode() / 100 != 2 || isTelegramApiFailure(responseBody)) {
            String providerMessage = resolveTelegramError(response.statusCode(), responseBody, fallbackError);
            String providerCode = resolveTelegramErrorCode(responseBody);
            return classifyFailure(
                providerMessage,
                response.statusCode(),
                providerCode,
                providerMessage,
                responseBody,
                durationMs
            );
        }
        return DialogReplyTransportResult.success(extractTelegramMessageId(responseBody), durationMs);
    }

    private DialogReplyTransportResult resolveVkTransportResult(HttpResponse<String> response, Long durationMs) {
        if (response == null) {
            return DialogReplyTransportResult.failure(
                "Не удалось отправить сообщение в VK.",
                "unknown_error",
                "critical",
                "transient",
                null,
                null,
                null,
                null,
                durationMs
            );
        }
        String responseBody = response.body();
        if (response.statusCode() / 100 != 2) {
            return classifyFailure(
                "Не удалось отправить сообщение в VK.",
                response.statusCode(),
                null,
                "VK HTTP " + response.statusCode(),
                responseBody,
                durationMs
            );
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                String errorCode = firstNonBlank(
                    error.path("error_code").asText(""),
                    error.path("code").asText("")
                );
                String providerMessage = firstNonBlank(
                    error.path("error_msg").asText(""),
                    error.path("message").asText(""),
                    "VK: unknown error"
                );
                return classifyFailure(
                    "VK: " + providerMessage,
                    response.statusCode(),
                    errorCode,
                    providerMessage,
                    responseBody,
                    durationMs
                );
            }
            return DialogReplyTransportResult.success(extractVkMessageId(responseBody), durationMs);
        } catch (IOException ex) {
            return DialogReplyTransportResult.success(null, durationMs);
        }
    }

    private DialogReplyTransportResult resolveMaxTransportResult(HttpResponse<String> response,
                                                                 String fallbackError,
                                                                 Long durationMs) {
        if (response == null) {
            return DialogReplyTransportResult.failure(
                fallbackError,
                "unknown_error",
                "critical",
                "transient",
                null,
                null,
                null,
                null,
                durationMs
            );
        }
        String responseBody = response.body();
        if (response.statusCode() / 100 == 2 && !looksLikeMaxErrorResponse(responseBody)) {
            return DialogReplyTransportResult.success(null, durationMs);
        }
        String providerCode = resolveMaxApiErrorCode(responseBody);
        String providerMessage = resolveMaxApiError(responseBody, fallbackError);
        return classifyFailure(
            providerMessage,
            response.statusCode(),
            providerCode,
            providerMessage,
            responseBody,
            durationMs
        );
    }

    private String resolveTelegramError(int statusCode, String responseBody, String fallbackError) {
        if (statusCode == 413) {
            return telegramTooLargeError();
        }
        if (!StringUtils.hasText(responseBody)) {
            return fallbackError;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String description = firstNonBlank(
                root.path("description").asText(""),
                root.path("error").asText(""),
                root.path("message").asText("")
            );
            if (StringUtils.hasText(description)) {
                String normalized = description.toLowerCase(Locale.ROOT);
                if (normalized.contains("too big") || normalized.contains("entity too large")) {
                    return telegramTooLargeError();
                }
                return "Telegram: " + description.trim();
            }
        } catch (IOException ignored) {
            // Ignore parse issues and fallback below.
        }
        return fallbackError;
    }

    private String resolveTelegramErrorCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode code = root.path("error_code");
            return code.isMissingNode() || code.isNull() ? null : code.asText();
        } catch (IOException ex) {
            return null;
        }
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

    private boolean looksLikeMaxErrorResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        return StringUtils.hasText(resolveMaxApiErrorCode(responseBody));
    }

    private String validateTelegramMedia(MultipartFile file, String caption) {
        if (file == null || file.isEmpty()) {
            return "Файл не выбран.";
        }
        if (safeSize(file) > TELEGRAM_MAX_DOCUMENT_BYTES) {
            return telegramTooLargeError();
        }
        if (caption != null && caption.length() > TELEGRAM_MAX_CAPTION_LENGTH) {
            return "Подпись к файлу слишком длинная. Максимум — 1024 символа.";
        }
        return null;
    }

    private String telegramTooLargeError() {
        return "Файл слишком большой для Telegram. Максимальный размер — 50 МБ.";
    }

    private String normalizeCaption(String caption) {
        if (!StringUtils.hasText(caption)) {
            return null;
        }
        return caption.trim();
    }

    private String resolveOriginalFilename(MultipartFile file, String originalName) {
        String preferred = StringUtils.hasText(originalName) ? originalName : (file != null ? file.getOriginalFilename() : null);
        return StringUtils.hasText(preferred) ? preferred.trim() : "file.bin";
    }

    private long safeSize(MultipartFile file) {
        return file != null ? Math.max(file.getSize(), 0L) : 0L;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream";
    }

    private Path createMultipartTempFile(List<MultipartField> fields,
                                         String fileFieldName,
                                         MultipartFile file,
                                         String filename,
                                         String contentType) throws IOException {
        Path tempFile = Files.createTempFile("panel-multipart-", ".tmp");
        boolean success = false;
        try (OutputStream output = Files.newOutputStream(tempFile)) {
            for (MultipartField field : fields) {
                if (field != null && StringUtils.hasText(field.value())) {
                    writeMultipartField(output, field.name(), field.value());
                }
            }
            writeMultipartFilePart(output, fileFieldName, filename, contentType, file);
            writeMultipartFinish(output);
            success = true;
            return tempFile;
        } finally {
            if (!success) {
                deleteTempFileQuietly(tempFile);
            }
        }
    }

    private void writeMultipartField(OutputStream output, String name, String value) throws IOException {
        String part = "--" + MULTIPART_BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
            + value + "\r\n";
        output.write(part.getBytes(StandardCharsets.UTF_8));
    }

    private void writeMultipartFilePart(OutputStream output,
                                        String fieldName,
                                        String filename,
                                        String contentType,
                                        MultipartFile file) throws IOException {
        String header = "--" + MULTIPART_BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        try (InputStream input = file.getInputStream()) {
            input.transferTo(output);
        }
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeMultipartFinish(OutputStream output) throws IOException {
        String end = "--" + MULTIPART_BOUNDARY + "--\r\n";
        output.write(end.getBytes(StandardCharsets.UTF_8));
    }

    private void deleteTempFileQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Unable to delete temp multipart file {}: {}", path, ex.getMessage());
        }
    }

    private void logTelegramMediaException(String method,
                                           Long userId,
                                           String originalName,
                                           String sanitizedFilename,
                                           String contentType,
                                           long fileSize,
                                           Exception ex) {
        log.warn(
            "Telegram media send failed: method={}, userId={}, originalName={}, sanitizedFilename={}, contentType={}, size={}, errorClass={}, error={}",
            method,
            userId,
            originalName,
            sanitizedFilename,
            contentType,
            fileSize,
            ex.getClass().getSimpleName(),
            ex.getMessage(),
            ex
        );
    }

    private void logTelegramMediaApiError(String method,
                                          int statusCode,
                                          Long userId,
                                          String originalName,
                                          String sanitizedFilename,
                                          String contentType,
                                          long fileSize,
                                          String responseBody) {
        log.warn(
            "Telegram media API returned error: method={}, status={}, userId={}, originalName={}, sanitizedFilename={}, contentType={}, size={}, body={}",
            method,
            statusCode,
            userId,
            originalName,
            sanitizedFilename,
            contentType,
            fileSize,
            truncateForLog(responseBody, RESPONSE_BODY_LOG_LIMIT)
        );
    }

    private String truncateForLog(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private DialogReplyTransportResult failureForException(String error,
                                                           String classification,
                                                           String severityLevel,
                                                           String retryState,
                                                           Exception ex,
                                                           Long durationMs) {
        String providerMessage = ex != null && StringUtils.hasText(ex.getMessage()) ? ex.getMessage().trim() : null;
        return DialogReplyTransportResult.failure(
            error,
            classification,
            severityLevel,
            retryState,
            null,
            null,
            providerMessage,
            providerMessage,
            durationMs
        );
    }

    private DialogReplyTransportResult classifyFailure(String error,
                                                       Integer httpStatus,
                                                       String providerErrorCode,
                                                       String providerMessage,
                                                       String responseBody,
                                                       Long durationMs) {
        String normalizedMessage = providerMessage != null ? providerMessage.toLowerCase(Locale.ROOT) : "";
        String normalizedCode = providerErrorCode != null ? providerErrorCode.toLowerCase(Locale.ROOT) : "";

        if (httpStatus != null && httpStatus == 429
            || "6".equals(normalizedCode)
            || normalizedCode.contains("rate")
            || normalizedMessage.contains("too many requests")
            || normalizedMessage.contains("rate limit")) {
            return DialogReplyTransportResult.failure(
                error,
                "rate_limited",
                "warning",
                "transient",
                httpStatus,
                providerErrorCode,
                providerMessage,
                excerpt(responseBody),
                durationMs
            );
        }
        if (normalizedMessage.contains("too big")) {
            return DialogReplyTransportResult.failure(
                telegramTooLargeError(),
                "validation_error",
                "warning",
                "terminal",
                httpStatus,
                providerErrorCode,
                providerMessage,
                excerpt(responseBody),
                durationMs
            );
        }
        if (httpStatus != null && (httpStatus == 400 || httpStatus == 404)
            || "901".equals(normalizedCode)
            || "900".equals(normalizedCode)
            || "902".equals(normalizedCode)
            || normalizedMessage.contains("chat not found")
            || normalizedMessage.contains("can't send")
            || normalizedMessage.contains("cannot send")
            || normalizedMessage.contains("peer_id")) {
            return DialogReplyTransportResult.failure(
                error,
                "client_error",
                "warning",
                "terminal",
                httpStatus,
                providerErrorCode,
                providerMessage,
                excerpt(responseBody),
                durationMs
            );
        }
        if (httpStatus != null && (httpStatus == 401 || httpStatus == 403)
            || "5".equals(normalizedCode)
            || normalizedMessage.contains("unauthorized")
            || normalizedMessage.contains("forbidden")
            || normalizedMessage.contains("invalid token")
            || normalizedMessage.contains("access denied")) {
            return DialogReplyTransportResult.failure(
                error,
                "client_error",
                "critical",
                "terminal",
                httpStatus,
                providerErrorCode,
                providerMessage,
                excerpt(responseBody),
                durationMs
            );
        }
        if ("attachment.not.ready".equals(normalizedCode)) {
            return DialogReplyTransportResult.failure(
                error,
                "provider_error",
                "warning",
                "transient",
                httpStatus,
                providerErrorCode,
                providerMessage,
                excerpt(responseBody),
                durationMs
            );
        }
        if (httpStatus != null && httpStatus >= 500) {
            return DialogReplyTransportResult.failure(
                error,
                "provider_error",
                "critical",
                "transient",
                httpStatus,
                providerErrorCode,
                providerMessage,
                excerpt(responseBody),
                durationMs
            );
        }
        return DialogReplyTransportResult.failure(
            error,
            "unknown_error",
            "critical",
            "transient",
            httpStatus,
            providerErrorCode,
            providerMessage,
            excerpt(responseBody),
            durationMs
        );
    }

    private String excerpt(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        String trimmed = responseBody.trim();
        if (trimmed.length() <= RESPONSE_EXCERPT_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, RESPONSE_EXCERPT_LIMIT) + "...";
    }

    private Long elapsedMillis(long startedAtNs) {
        return Math.max(0L, (System.nanoTime() - startedAtNs) / 1_000_000L);
    }

    public record DialogReplyTransportResult(String error,
                                             Long telegramMessageId,
                                             String classification,
                                             String severityLevel,
                                             String retryState,
                                             Integer httpStatus,
                                             String providerErrorCode,
                                             String providerMessage,
                                             String responseExcerpt,
                                             Long durationMs) {

        public DialogReplyTransportResult(String error, Long telegramMessageId) {
            this(
                error,
                telegramMessageId,
                error == null ? "success" : "unknown_error",
                error == null ? "ok" : "critical",
                error == null ? "none" : "transient",
                null,
                null,
                null,
                null,
                null
            );
        }

        public boolean success() {
            return error == null;
        }

        public static DialogReplyTransportResult success(Long telegramMessageId, Long durationMs) {
            return new DialogReplyTransportResult(
                null,
                telegramMessageId,
                "success",
                "ok",
                "none",
                null,
                null,
                null,
                null,
                durationMs
            );
        }

        public static DialogReplyTransportResult failure(String error,
                                                         String classification,
                                                         String severityLevel,
                                                         String retryState,
                                                         Integer httpStatus,
                                                         String providerErrorCode,
                                                         String providerMessage,
                                                         String responseExcerpt,
                                                         Long durationMs) {
            return new DialogReplyTransportResult(
                error,
                null,
                classification,
                severityLevel,
                retryState,
                httpStatus,
                providerErrorCode,
                providerMessage,
                responseExcerpt,
                durationMs
            );
        }
    }

    private record MultipartField(String name, String value) {
    }
}
