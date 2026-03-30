package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChannelApiController {

    private static final Logger log = LoggerFactory.getLogger(ChannelApiController.class);

    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;
    private final SharedConfigService sharedConfigService;
    private final com.example.panel.service.IntegrationNetworkService integrationNetworkService;

    public ChannelApiController(ChannelRepository channelRepository,
                                ObjectMapper objectMapper,
                                SharedConfigService sharedConfigService,
                                com.example.panel.service.IntegrationNetworkService integrationNetworkService) {
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
        this.sharedConfigService = sharedConfigService;
        this.integrationNetworkService = integrationNetworkService;
    }

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> getChannels() {
        List<Channel> channels = channelRepository.findAll();
        try {
            updateTelegramBotInfoIfMissing(channels);
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh Telegram bot info: {}", ex.getMessage());
        }
        Map<Long, Map<String, Object>> credentials = buildCredentialIndex(loadBotCredentials());
        List<Map<String, Object>> responseChannels = channels.stream()
            .map(channel -> toChannelResponse(channel, credentials))
            .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("channels", responseChannels);
        body.put("success", true);
        log.info("Channels API returned {} channels", channels.size());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/channels")
    public ResponseEntity<Map<String, Object>> createChannel(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> data = payload != null ? payload : Collections.emptyMap();
        String name = stringValue(firstValue(data, "channel_name", "name"));
        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Название канала обязательно"));
        }
        Channel channel = new Channel();
        channel.setChannelName(name);
        channel.setDescription(stringValue(data.get("description")));
        channel.setPlatform(stringValue(data.getOrDefault("platform", "telegram")));
        channel.setCredentialId(parseLong(data.get("credential_id")));
        channel.setActive(parseBoolean(data.getOrDefault("is_active", true)));
        channel.setMaxQuestions(parseInteger(data.get("max_questions")));
        channel.setSupportChatId(stringValue(firstValue(data, "support_chat_id", "supportChatId")));
        channel.setQuestionTemplateId(stringValue(data.get("question_template_id")));
        channel.setRatingTemplateId(stringValue(data.get("rating_template_id")));
        channel.setAutoActionTemplateId(stringValue(data.get("auto_action_template_id")));
        if (data.containsKey("platform_config") || data.containsKey("settings")) {
            Object raw = firstValue(data, "platform_config", "settings");
            channel.setPlatformConfig(serializeIfNeeded(raw));
        }
        if (data.containsKey("delivery_settings") || data.containsKey("deliverySettings")) {
            Object raw = firstValue(data, "delivery_settings", "deliverySettings");
            channel.setDeliverySettings(serializeIfNeeded(raw));
        }
        if (data.containsKey("questions_cfg")) {
            try {
                channel.setQuestionsCfg(normalizeQuestionsConfig(data.get("questions_cfg")));
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
            }
        }
        String token = stringValue(data.get("token"));
        channel.setToken(token.isEmpty() ? generateToken() : token);
        channel.setFilters("{}");
        if (isBlank(channel.getQuestionsCfg())) {
            channel.setQuestionsCfg("{}");
        }
        if (isBlank(channel.getDeliverySettings())) {
            channel.setDeliverySettings("{}");
        }
        channel.setCreatedAt(OffsetDateTime.now());
        channel.setUpdatedAt(OffsetDateTime.now());
        updateTelegramBotInfo(channel, false);

        Channel saved = channelRepository.save(channel);
        Map<Long, Map<String, Object>> credentials = buildCredentialIndex(loadBotCredentials());
        return ResponseEntity.ok(Map.of("success", true, "channel", toChannelResponse(saved, credentials)));
    }

    @PatchMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> patchChannel(@PathVariable long channelId,
                                                            @RequestBody(required = false) Map<String, Object> payload) {
        return applyChannelUpdate(channelId, payload);
    }

    @PutMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> putChannel(@PathVariable long channelId,
                                                          @RequestBody(required = false) Map<String, Object> payload) {
        return applyChannelUpdate(channelId, payload);
    }

    @PostMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> postChannel(@PathVariable long channelId,
                                                           @RequestBody(required = false) Map<String, Object> payload) {
        return applyChannelUpdate(channelId, payload);
    }

    @DeleteMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> deleteChannel(@PathVariable long channelId) {
        Optional<Channel> channel = channelRepository.findById(channelId);
        if (channel.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Канал не найден"));
        }
        channelRepository.delete(channel.get());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/channels/{channelId}/test-message")
    public ResponseEntity<Map<String, Object>> testChannel(@PathVariable long channelId,
                                                           @RequestBody(required = false) Map<String, Object> payload) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Канал не найден"));
        }
        if (!isTelegramPlatform(channel.getPlatform())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Тестовая отправка доступна только для Telegram"));
        }
        if (isBlank(channel.getToken())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "У канала не задан токен"));
        }

        Map<String, Object> data = payload != null ? payload : Collections.emptyMap();
        String mode = stringValue(firstValue(data, "target_mode", "targetMode")).toLowerCase();
        String recipient = stringValue(data.get("recipient"));
        String message = stringValue(payload != null ? payload.get("message") : null);
        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Нужно указать текст сообщения"));
        }

        Map<String, Object> deliverySettings = parseJsonMap(channel.getDeliverySettings());
        String channelRecipient = stringValue(firstValue(deliverySettings, "broadcast_channel_id", "broadcastChannelId"));
        String groupRecipient = stringValue(channel.getSupportChatId());

        List<String> recipients = new ArrayList<>();
        if (mode.equals("group")) {
            if (!groupRecipient.isEmpty()) {
                recipients.add(groupRecipient);
            }
        } else if (mode.equals("channel")) {
            if (!channelRecipient.isEmpty()) {
                recipients.add(channelRecipient);
            }
        } else if (mode.equals("both")) {
            if (!groupRecipient.isEmpty()) {
                recipients.add(groupRecipient);
            }
            if (!channelRecipient.isEmpty()) {
                recipients.add(channelRecipient);
            }
        }
        if (!recipient.isEmpty()) {
            recipients.add(recipient);
        }
        List<String> uniqueRecipients = recipients.stream()
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
        if (uniqueRecipients.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Не найден получатель. Укажите ID вручную или настройте группу/канал."));
        }

        List<String> failedRecipients = new ArrayList<>();
        List<String> sentRecipients = new ArrayList<>();
        for (String target : uniqueRecipients) {
            if (sendTelegramMessage(channel, channel.getToken(), target, message)) {
                sentRecipients.add(target);
            } else {
                failedRecipients.add(target);
            }
        }
        log.info("Test message requested for channel {} to {} targets ({} chars)", channelId, uniqueRecipients.size(), message.length());
        if (sentRecipients.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Не удалось отправить сообщение ни одному получателю", "failed", failedRecipients));
        }
        return ResponseEntity.ok(Map.of("success", true, "sent", sentRecipients, "failed", failedRecipients));
    }

    @PostMapping("/channels/{channelId}/bot-info")
    public ResponseEntity<Map<String, Object>> refreshBotInfo(@PathVariable long channelId) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Канал не найден"));
        }
        if (!isTelegramPlatform(channel.getPlatform())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Обновление доступно только для Telegram"));
        }
        boolean updated = updateTelegramBotInfo(channel, true);
        if (!updated) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Не удалось получить данные бота"));
        }
        channel.setUpdatedAt(OffsetDateTime.now());
        channelRepository.save(channel);
        Map<Long, Map<String, Object>> credentials = buildCredentialIndex(loadBotCredentials());
        return ResponseEntity.ok(Map.of("success", true, "channel", toChannelResponse(channel, credentials)));
    }

    private ResponseEntity<Map<String, Object>> applyChannelUpdate(long channelId,
                                                                   Map<String, Object> payload) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Канал не найден"));
        }
        Map<String, Object> data = payload != null ? payload : Collections.emptyMap();
        boolean updated = false;
        boolean tokenChanged = false;
        boolean platformChanged = false;

        if (data.containsKey("channel_name") || data.containsKey("name")) {
            String name = stringValue(firstValue(data, "channel_name", "name"));
            if (!name.isEmpty()) {
                channel.setChannelName(name);
                updated = true;
            }
        }

        if (data.containsKey("description")) {
            channel.setDescription(stringValue(data.get("description")));
            updated = true;
        }

        if (data.containsKey("platform")) {
            String platform = stringValue(data.get("platform")).toLowerCase();
            if (!platform.isEmpty()) {
                channel.setPlatform(platform);
                updated = true;
                platformChanged = true;
            }
        }

        if (data.containsKey("platform_config") || data.containsKey("settings")) {
            Object raw = firstValue(data, "platform_config", "settings");
            String encoded = serializeIfNeeded(raw);
            channel.setPlatformConfig(encoded);
            updated = true;
        }

        if (data.containsKey("filters")) {
            String encoded = serializeIfNeeded(data.get("filters"));
            channel.setFilters(encoded);
            updated = true;
        }

        if (data.containsKey("delivery_settings") || data.containsKey("deliverySettings")) {
            Object raw = firstValue(data, "delivery_settings", "deliverySettings");
            String encoded = serializeIfNeeded(raw);
            channel.setDeliverySettings(encoded);
            updated = true;
        }

        if (data.containsKey("network_route") || data.containsKey("networkRoute")) {
            Map<String, Object> deliverySettings = parseJsonMap(channel.getDeliverySettings());
            Object raw = firstValue(data, "network_route", "networkRoute");
            deliverySettings.put("network_route", raw == null ? Map.of() : raw);
            channel.setDeliverySettings(serializeIfNeeded(deliverySettings));
            updated = true;
        }

        if (data.containsKey("is_active")) {
            channel.setActive(parseBoolean(data.get("is_active")));
            updated = true;
        }

        if (data.containsKey("support_chat_id") || data.containsKey("supportChatId")) {
            Object raw = firstValue(data, "support_chat_id", "supportChatId");
            String value = stringValue(raw);
            channel.setSupportChatId(value.isEmpty() ? null : value);
            updated = true;
        }

        if (data.containsKey("max_questions")) {
            Integer maxQuestions = parseInteger(data.get("max_questions"));
            channel.setMaxQuestions(maxQuestions);
            updated = true;
        }

        if (data.containsKey("credential_id")) {
            channel.setCredentialId(parseLong(data.get("credential_id")));
            updated = true;
        }

        if (data.containsKey("question_template_id")) {
            channel.setQuestionTemplateId(stringValue(data.get("question_template_id")));
            updated = true;
        }

        if (data.containsKey("rating_template_id")) {
            channel.setRatingTemplateId(stringValue(data.get("rating_template_id")));
            updated = true;
        }

        if (data.containsKey("auto_action_template_id")) {
            channel.setAutoActionTemplateId(stringValue(data.get("auto_action_template_id")));
            updated = true;
        }

        if (data.containsKey("questions_cfg")) {
            try {
                String encoded = normalizeQuestionsConfig(data.get("questions_cfg"));
                channel.setQuestionsCfg(encoded);
                updated = true;
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
            }
        }

        if (data.containsKey("token")) {
            String token = stringValue(data.get("token"));
            if (!token.isEmpty()) {
                channel.setToken(token);
                updated = true;
                tokenChanged = true;
            }
        }

        if (!updated) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Нет полей для обновления"));
        }

        channel.setUpdatedAt(OffsetDateTime.now());
        if (tokenChanged || platformChanged) {
            updateTelegramBotInfo(channel, true);
        }
        channel.setUpdatedAt(OffsetDateTime.now());
        channelRepository.save(channel);
        Map<Long, Map<String, Object>> credentials = buildCredentialIndex(loadBotCredentials());
        return ResponseEntity.ok(Map.of("success", true, "channel", toChannelResponse(channel, credentials)));
    }

    @PostMapping({"/{id}/public-id/regenerate", "/channels/{id}/public-id/regenerate"})
    public ResponseEntity<Map<String, Object>> regeneratePublicId(@PathVariable("id") long id) {
        Channel channel = channelRepository.findById(id).orElse(null);
        if (channel == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Канал не найден"));
        }
        channel.setPublicId(UUID.randomUUID().toString().replace("-", ""));
        channel.setUpdatedAt(OffsetDateTime.now());
        channelRepository.save(channel);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "public_id", channel.getPublicId(),
                "channel", toChannelResponse(channel, buildCredentialIndex(loadBotCredentials()))
        ));
    }

    @GetMapping("/bot-credentials")
    public ResponseEntity<Map<String, Object>> getBotCredentials() {
        List<BotCredential> credentials = loadBotCredentials();
        List<Map<String, Object>> response = credentials.stream()
            .map(this::toCredentialResponse)
            .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("credentials", response);
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/bot-credentials")
    public ResponseEntity<Map<String, Object>> createBotCredential(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> data = payload != null ? payload : Collections.emptyMap();
        String name = stringValue(data.get("name"));
        String token = stringValue(data.get("token"));
        if (name.isEmpty() || token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Название и токен обязательны"));
        }
        String platform = stringValue(data.getOrDefault("platform", "telegram"));
        boolean active = Boolean.TRUE.equals(parseBoolean(data.getOrDefault("is_active", true)));

        List<BotCredential> credentials = new ArrayList<>(loadBotCredentials());
        long nextId = credentials.stream()
            .map(BotCredential::id)
            .filter(Objects::nonNull)
            .max(Long::compareTo)
            .orElse(0L) + 1;
        BotCredential credential = new BotCredential(nextId, name, platform, token, active);
        credentials.add(credential);
        sharedConfigService.saveBotCredentials(credentials);

        return ResponseEntity.ok(Map.of("success", true, "credential", toCredentialResponse(credential)));
    }

    @DeleteMapping("/bot-credentials/{credentialId}")
    public ResponseEntity<Map<String, Object>> deleteBotCredential(@PathVariable long credentialId) {
        List<BotCredential> credentials = new ArrayList<>(loadBotCredentials());
        boolean removed = credentials.removeIf(cred -> Objects.equals(cred.id(), credentialId));
        if (!removed) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Учётные данные не найдены"));
        }
        sharedConfigService.saveBotCredentials(credentials);
        List<Channel> channels = channelRepository.findAll();
        boolean updated = false;
        for (Channel channel : channels) {
            if (Objects.equals(channel.getCredentialId(), credentialId)) {
                channel.setCredentialId(null);
                updated = true;
            }
        }
        if (updated) {
            channelRepository.saveAll(channels);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/channel-notifications")
    public ResponseEntity<Map<String, Object>> getChannelNotifications() {
        Map<String, Object> body = new HashMap<>();
        body.put("notifications", Collections.emptyList());
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Object firstValue(Map<String, Object> data, String primaryKey, String fallbackKey) {
        Object value = data.get(primaryKey);
        return value != null ? value : data.get(fallbackKey);
    }

    private Boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String value = stringValue(raw).toLowerCase();
        if (value.isEmpty()) {
            return null;
        }
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("y");
    }

    private Integer parseInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String value = stringValue(raw);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String value = stringValue(raw);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private List<BotCredential> loadBotCredentials() {
        return sharedConfigService.loadBotCredentials();
    }

    private Map<Long, Map<String, Object>> buildCredentialIndex(List<BotCredential> credentials) {
        Map<Long, Map<String, Object>> index = new HashMap<>();
        for (BotCredential credential : credentials) {
            if (credential.id() != null) {
                index.put(credential.id(), toCredentialResponse(credential));
            }
        }
        return index;
    }

    private Map<String, Object> toChannelResponse(Channel channel, Map<Long, Map<String, Object>> credentials) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", channel.getId());
        response.put("channel_name", channel.getChannelName());
        response.put("platform", channel.getPlatform());
        response.put("token", channel.getToken());
        response.put("is_active", channel.getActive());
        response.put("description", channel.getDescription());
        response.put("max_questions", channel.getMaxQuestions());
        response.put("credential_id", channel.getCredentialId());
        response.put("bot_name", channel.getBotName());
        response.put("bot_username", channel.getBotUsername());
        response.put("question_template_id", channel.getQuestionTemplateId());
        response.put("rating_template_id", channel.getRatingTemplateId());
        response.put("auto_action_template_id", channel.getAutoActionTemplateId());
        response.put("support_chat_id", channel.getSupportChatId());
        response.put("platform_config", parseJsonMap(channel.getPlatformConfig()));
        response.put("delivery_settings", parseJsonMap(channel.getDeliverySettings()));
        response.put("public_id", channel.getPublicId());
        response.put("questions_cfg", parseJsonValue(channel.getQuestionsCfg()));
        response.put("network_route", parseNetworkRoute(channel));
        if (channel.getCredentialId() != null) {
            response.put("credential", credentials.get(channel.getCredentialId()));
        }
        return response;
    }

    private Object parseJsonValue(String raw) {
        if (isBlank(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node != null && node.isArray()) {
                return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));
            }
            if (node != null && node.isObject()) {
                return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            }
        } catch (Exception ex) {
            log.debug("Unable to parse json value: {}", ex.getMessage());
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (isBlank(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node != null && node.isObject()) {
                return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            }
        } catch (Exception ex) {
            log.debug("Unable to parse json map: {}", ex.getMessage());
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> parseNetworkRoute(Channel channel) {
        Map<String, Object> deliverySettings = parseJsonMap(channel.getDeliverySettings());
        Object route = deliverySettings.get("network_route");
        if (route instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
            return normalized;
        }
        return Map.of();
    }

    private boolean sendTelegramMessage(Channel channel, String token, String recipient, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", recipient);
            payload.put("text", message);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, Duration.ofSeconds(10));
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Telegram sendMessage failed for {}: HTTP {}", recipient, response.statusCode());
                return false;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("ok").asBoolean(false);
        } catch (Exception ex) {
            log.warn("Telegram sendMessage error for {}: {}", recipient, ex.getMessage());
            return false;
        }
    }

    private Map<String, Object> toCredentialResponse(BotCredential credential) {
        String platform = credential.platform() != null && !credential.platform().isBlank()
            ? credential.platform()
            : "telegram";
        String masked = maskToken(credential.token());
        return Map.of(
            "id", credential.id(),
            "name", credential.name(),
            "platform", platform,
            "masked_token", masked,
            "is_active", Boolean.TRUE.equals(credential.active())
        );
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim();
        int visible = Math.min(4, trimmed.length());
        String suffix = trimmed.substring(trimmed.length() - visible);
        return "*".repeat(Math.max(0, trimmed.length() - visible)) + suffix;
    }


    private String normalizeQuestionsConfig(Object raw) {
        JsonNode node = objectMapper.valueToTree(raw);
        JsonNode root = node != null && !node.isNull() ? node : objectMapper.createObjectNode();
        if (root.isArray()) {
            return serializeIfNeeded(Map.of(
                    "schemaVersion", 1,
                    "enabled", true,
                    "captchaEnabled", false,
                    "disabledStatus", 404,
                    "fields", normalizeQuestionFields(root)
            ));
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("questions_cfg должен быть объектом или массивом");
        }
        int schemaVersion = Math.max(1, root.path("schemaVersion").asInt(1));
        boolean enabled = !root.has("enabled") || root.path("enabled").asBoolean(true);
        boolean captchaEnabled = root.path("captchaEnabled").asBoolean(false);
        Boolean rateLimitEnabled = root.has("rateLimitEnabled") ? root.path("rateLimitEnabled").asBoolean(false) : null;
        Integer rateLimitWindowSeconds = root.has("rateLimitWindowSeconds")
                ? normalizeIntegerOrNull(root.path("rateLimitWindowSeconds"), 10, 3600)
                : null;
        Integer rateLimitMaxRequests = root.has("rateLimitMaxRequests")
                ? normalizeIntegerOrNull(root.path("rateLimitMaxRequests"), 1, 500)
                : null;
        Map<String, Object> alertQueue = root.has("alertQueue")
                ? normalizeAlertQueue(root.path("alertQueue"))
                : null;
        int disabledStatus = root.path("disabledStatus").asInt(404) == 410 ? 410 : 404;
        List<Map<String, Object>> fields = normalizeQuestionFields(root.path("fields"));
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("schemaVersion", schemaVersion);
        normalized.put("enabled", enabled);
        normalized.put("captchaEnabled", captchaEnabled);
        if (rateLimitEnabled != null) {
            normalized.put("rateLimitEnabled", rateLimitEnabled);
        }
        if (rateLimitWindowSeconds != null) {
            normalized.put("rateLimitWindowSeconds", rateLimitWindowSeconds);
        }
        if (rateLimitMaxRequests != null) {
            normalized.put("rateLimitMaxRequests", rateLimitMaxRequests);
        }
        if (alertQueue != null) {
            normalized.put("alertQueue", alertQueue);
        }
        normalized.put("disabledStatus", disabledStatus);
        normalized.put("fields", fields);
        return serializeIfNeeded(normalized);
    }

    private Map<String, Object> normalizeAlertQueue(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("questions_cfg.alertQueue должен быть объектом");
        }
        String mode = stringValue(node.path("targetMode").asText("department_all")).toLowerCase();
        if (!Set.of("department_all", "employees_only", "department_except").contains(mode)) {
            mode = "department_all";
        }
        String deliveryMode = stringValue(node.path("deliveryMode").asText("all")).toLowerCase();
        if (!Set.of("all", "online_only_fallback_all").contains(deliveryMode)) {
            deliveryMode = "all";
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("enabled", node.path("enabled").asBoolean(false));
        normalized.put("department", stringValue(node.path("department").asText("")));
        normalized.put("targetMode", mode);
        normalized.put("deliveryMode", deliveryMode);
        normalized.put("employeeUsernames", normalizeStringList(node.path("employeeUsernames")));
        normalized.put("excludeUsernames", normalizeStringList(node.path("excludeUsernames")));
        return normalized;
    }

    private List<String> normalizeStringList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = stringValue(item.asText(""));
            if (!value.isEmpty()) {
                values.add(value.toLowerCase());
            }
        }
        return new ArrayList<>(values);
    }

    private Integer normalizeIntegerOrNull(JsonNode node, int min, int max) {
        if (node == null || node.isNull()) {
            return null;
        }
        Integer value;
        if (node.isIntegralNumber()) {
            value = node.asInt();
        } else if (node.isTextual()) {
            try {
                value = Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Значение должно быть целым числом");
            }
        } else {
            throw new IllegalArgumentException("Значение должно быть целым числом");
        }
        return Math.max(min, Math.min(max, value));
    }

    private List<Map<String, Object>> normalizeQuestionFields(JsonNode fieldsNode) {
        if (fieldsNode == null || fieldsNode.isNull()) {
            return List.of();
        }
        if (!fieldsNode.isArray()) {
            throw new IllegalArgumentException("questions_cfg.fields должен быть массивом");
        }
        Set<String> allowedTypes = Set.of("text", "textarea", "select", "checkbox", "phone", "email", "file");
        List<Map<String, Object>> fields = new ArrayList<>();
        int index = 0;
        for (JsonNode fieldNode : fieldsNode) {
            index++;
            if (!fieldNode.isObject()) {
                throw new IllegalArgumentException("Каждое поле формы должно быть объектом");
            }
            String id = stringValue(fieldNode.path("id").asText(""));
            String text = stringValue(fieldNode.path("text").asText(""));
            String type = stringValue(fieldNode.path("type").asText("text")).toLowerCase();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Поле #" + index + " должно содержать id");
            }
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Поле «" + id + "» должно содержать название");
            }
            if (!allowedTypes.contains(type)) {
                throw new IllegalArgumentException("Поле «" + id + "» содержит неподдерживаемый тип: " + type);
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", id);
            normalized.put("text", text);
            normalized.put("type", type);
            normalized.put("order", fieldNode.path("order").asInt(index));
            if (fieldNode.has("required")) {
                normalized.put("required", fieldNode.path("required").asBoolean(false));
            }
            if (fieldNode.has("placeholder")) {
                normalized.put("placeholder", stringValue(fieldNode.path("placeholder").asText("")));
            }
            if (fieldNode.has("helpText")) {
                normalized.put("helpText", stringValue(fieldNode.path("helpText").asText("")));
            }
            if (fieldNode.has("minLength")) {
                normalized.put("minLength", Math.max(0, fieldNode.path("minLength").asInt(0)));
            }
            if (fieldNode.has("maxLength")) {
                normalized.put("maxLength", Math.max(1, fieldNode.path("maxLength").asInt(500)));
            }
            if ("select".equals(type)) {
                List<String> options = new ArrayList<>();
                if (fieldNode.has("options") && fieldNode.path("options").isArray()) {
                    for (JsonNode optionNode : fieldNode.path("options")) {
                        String option = stringValue(optionNode.asText(""));
                        if (!option.isEmpty()) {
                            options.add(option);
                        }
                    }
                }
                if (options.isEmpty()) {
                    throw new IllegalArgumentException("Поле «" + id + "» типа select должно содержать хотя бы одну option");
                }
                normalized.put("options", options);
            }
            fields.add(normalized);
        }
        return fields;
    }

    private String serializeIfNeeded(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Не удалось сериализовать JSON", ex);
        }
    }

    private void updateTelegramBotInfoIfMissing(List<Channel> channels) {
        List<Channel> updated = new ArrayList<>();
        for (Channel channel : channels) {
            if (!isTelegramPlatform(channel.getPlatform())) {
                continue;
            }
            if (isBlank(channel.getToken())) {
                continue;
            }
            if (!isBlank(channel.getBotName()) || !isBlank(channel.getBotUsername())) {
                continue;
            }
            boolean changed = updateTelegramBotInfo(channel, true);
            if (changed) {
                channel.setUpdatedAt(OffsetDateTime.now());
                updated.add(channel);
            }
        }
        if (!updated.isEmpty()) {
            try {
                channelRepository.saveAll(updated);
            } catch (RuntimeException ex) {
                log.warn("Failed to save refreshed Telegram bot info: {}", ex.getMessage());
            }
        }
    }

    private boolean updateTelegramBotInfo(Channel channel, boolean forceRefresh) {
        if (!isTelegramPlatform(channel.getPlatform()) || isBlank(channel.getToken())) {
            return false;
        }
        if (!forceRefresh && !isBlank(channel.getBotName()) && !isBlank(channel.getBotUsername())) {
            return false;
        }
        Optional<TelegramBotInfo> info = fetchTelegramBotInfo(channel);
        if (info.isEmpty()) {
            return false;
        }
        TelegramBotInfo data = info.get();
        if (!isBlank(data.name())) {
            channel.setBotName(data.name());
        }
        if (!isBlank(data.username())) {
            channel.setBotUsername(data.username());
        }
        return true;
    }

    private Optional<TelegramBotInfo> fetchTelegramBotInfo(Channel channel) {
        String token = channel != null ? channel.getToken() : null;
        if (isBlank(token)) {
            return Optional.empty();
        }
        String url = "https://api.telegram.org/bot" + token + "/getMe";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();
        try {
            HttpClient client = integrationNetworkService.createChannelHttpClient(channel, Duration.ofSeconds(10));
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Telegram getMe failed with status {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("ok").asBoolean(false)) {
                log.warn("Telegram getMe returned ok=false");
                return Optional.empty();
            }
            JsonNode result = root.path("result");
            String username = result.path("username").asText("");
            String firstName = result.path("first_name").asText("");
            String lastName = result.path("last_name").asText("");
            String name = buildDisplayName(firstName, lastName, username);
            if (isBlank(name) && isBlank(username)) {
                return Optional.empty();
            }
            return Optional.of(new TelegramBotInfo(name, username));
        } catch (Exception ex) {
            log.warn("Failed to load Telegram bot info: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildDisplayName(String firstName, String lastName, String username) {
        List<String> parts = Arrays.asList(stringValue(firstName), stringValue(lastName));
        String joined = parts.stream()
            .filter(value -> !value.isBlank())
            .reduce((left, right) -> left + " " + right)
            .orElse("");
        if (!joined.isBlank()) {
            return joined;
        }
        return stringValue(username);
    }

    private boolean isTelegramPlatform(String platform) {
        return platform == null || platform.isBlank() || platform.equalsIgnoreCase("telegram");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record TelegramBotInfo(String name, String username) {
    }
}
