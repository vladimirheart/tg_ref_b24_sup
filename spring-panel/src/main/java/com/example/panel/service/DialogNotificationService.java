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
import java.util.ArrayList;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final String DEFAULT_TELEGRAM_API_ROOT_URL = "https://api.telegram.org";

    private final JdbcTemplate jdbcTemplate;
    private final ChannelRepository channelRepository;
    private final IntegrationNetworkService integrationNetworkService;
    private final SharedConfigService sharedConfigService;
    private final BotSettingsPayloadNormalizer botSettingsPayloadNormalizer;
    private final ObjectMapper objectMapper;

    public DialogNotificationService(JdbcTemplate jdbcTemplate,
                                     ChannelRepository channelRepository,
                                     IntegrationNetworkService integrationNetworkService,
                                     SharedConfigService sharedConfigService,
                                     BotSettingsPayloadNormalizer botSettingsPayloadNormalizer,
                                     ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelRepository = channelRepository;
        this.integrationNetworkService = integrationNetworkService;
        this.sharedConfigService = sharedConfigService;
        this.botSettingsPayloadNormalizer = botSettingsPayloadNormalizer;
        this.objectMapper = objectMapper;
    }

    public void notifyResolved(String ticketId) {
        sendNotifications(ticketId, this::buildResolvedMessages);
    }

    public void notifyReopened(String ticketId) {
        sendNotifications(ticketId, channel -> List.of(
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

    public boolean notifySupportChat(Channel channel, String message) {
        if (channel == null || !StringUtils.hasText(message) || !StringUtils.hasText(channel.getSupportChatId())) {
            return false;
        }
        Long chatId = parseTelegramLikeChatId(channel.getSupportChatId());
        if (chatId == null) {
            log.warn("Unable to notify support chat for channel {}: invalid support_chat_id '{}'",
                channel.getId(),
                channel.getSupportChatId());
            return false;
        }
        return sendPlatformMessage(channel, chatId, message);
    }

    public int notifyAllSupportChats(String message) {
        if (!StringUtils.hasText(message)) {
            return 0;
        }
        int delivered = 0;
        LinkedHashSet<String> destinations = new LinkedHashSet<>();
        for (Channel channel : channelRepository.findAll()) {
            if (channel == null || !StringUtils.hasText(channel.getSupportChatId())) {
                continue;
            }
            String destinationKey = String.join("|",
                Optional.ofNullable(channel.getPlatform()).orElse("telegram").trim().toLowerCase(),
                Optional.ofNullable(channel.getToken()).orElse("").trim(),
                channel.getSupportChatId().trim());
            if (!destinations.add(destinationKey)) {
                continue;
            }
            if (notifySupportChat(channel, message)) {
                delivered++;
            }
        }
        return delivered;
    }

    List<String> buildResolvedMessages(Channel channel) {
        List<String> messages = new ArrayList<>();
        messages.add("Диалог закрыт. Спасибо за обращение!");
        messages.add(resolveRatingPrompt(channel));
        return messages;
    }

    String resolveRatingPrompt(Channel channel) {
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> botSettings = botSettingsPayloadNormalizer.normalize(settings.get("bot_settings"));
        List<Map<String, Object>> ratingTemplates = castTemplateList(botSettings.get("rating_templates"));
        if (ratingTemplates.isEmpty()) {
            return defaultRatingPrompt(5);
        }

        Map<String, Object> selectedTemplate = null;
        String channelTemplateId = channel != null ? stringValue(channel.getRatingTemplateId()) : "";
        if (StringUtils.hasText(channelTemplateId)) {
            selectedTemplate = findTemplateById(ratingTemplates, channelTemplateId);
        }
        if (selectedTemplate == null) {
            selectedTemplate = findTemplateById(
                    ratingTemplates,
                    stringValue(botSettings.get("active_rating_template_id"))
            );
        }
        if (selectedTemplate == null) {
            selectedTemplate = ratingTemplates.get(0);
        }

        String prompt = stringValue(selectedTemplate.get("prompt_text"));
        if (prompt.isBlank()) {
            prompt = stringValue(selectedTemplate.get("prompt"));
        }
        if (prompt.isBlank()) {
            prompt = stringValue(selectedTemplate.get("promptText"));
        }
        if (!prompt.isBlank()) {
            return prompt;
        }
        return defaultRatingPrompt(resolveRatingScale(selectedTemplate));
    }

    private void sendNotifications(String ticketId, java.util.function.Function<Channel, List<String>> messageFactory) {
        if (!StringUtils.hasText(ticketId) || messageFactory == null) {
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
        List<String> messages = messageFactory.apply(channel);
        if (messages == null || messages.isEmpty()) {
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

    private List<Map<String, Object>> castTemplateList(Object rawTemplates) {
        List<Map<String, Object>> templates = new ArrayList<>();
        if (!(rawTemplates instanceof List<?> list)) {
            return templates;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> template = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    template.put(key.toString(), value);
                }
            });
            templates.add(template);
        }
        return templates;
    }

    private Map<String, Object> findTemplateById(List<Map<String, Object>> templates, String templateId) {
        if (!StringUtils.hasText(templateId)) {
            return null;
        }
        for (Map<String, Object> template : templates) {
            if (templateId.equals(stringValue(template.get("id")))) {
                return template;
            }
        }
        return null;
    }

    private int resolveRatingScale(Map<String, Object> template) {
        Integer scale = parseInteger(template != null ? template.get("scale_size") : null);
        if (scale == null) {
            scale = parseInteger(template != null ? template.get("scale") : null);
        }
        if (scale == null && template != null && template.get("responses") instanceof List<?> responses) {
            scale = responses.size();
        }
        return scale != null && scale > 0 ? scale : 5;
    }

    private String defaultRatingPrompt(int scale) {
        if (scale <= 1) {
            return "Пожалуйста, оцените качество ответа: отправьте число 1.";
        }
        return "Пожалуйста, оцените качество ответа от 1 до " + scale + ".";
    }

    private String stringValue(Object value) {
        return value != null ? value.toString().trim() : "";
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String raw = stringValue(value);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean sendTelegramText(Channel channel, Long userId, String text) {
        if (userId == null || !StringUtils.hasText(text)) {
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", userId);
        payload.put("text", text);
        try {
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, Duration.ofSeconds(10));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildTelegramMethodUrl(channel, "sendMessage")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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

    private Long parseTelegramLikeChatId(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
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
            return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {});
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

    private record DialogTarget(Long userId, Long channelId) {}
}
