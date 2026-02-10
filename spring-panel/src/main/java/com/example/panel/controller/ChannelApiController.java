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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChannelApiController {

    private static final Logger log = LoggerFactory.getLogger(ChannelApiController.class);
    private static final HttpClient TELEGRAM_HTTP_CLIENT = HttpClient.newHttpClient();

    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;
    private final SharedConfigService sharedConfigService;

    public ChannelApiController(ChannelRepository channelRepository,
                                ObjectMapper objectMapper,
                                SharedConfigService sharedConfigService) {
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
        this.sharedConfigService = sharedConfigService;
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
        String token = stringValue(data.get("token"));
        channel.setToken(token.isEmpty() ? generateToken() : token);
        channel.setFilters("{}");
        channel.setQuestionsCfg("{}");
        channel.setDeliverySettings("{}");
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
            if (sendTelegramMessage(channel.getToken(), target, message)) {
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
            String encoded = serializeIfNeeded(data.get("questions_cfg"));
            channel.setQuestionsCfg(encoded);
            updated = true;
        }

        if (data.containsKey("token")) {
            String token = stringValue(data.get("token"));
            if (!token.isEmpty()) {
                channel.setToken(token);
                updated = true;
            }
        }

        if (!updated) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Нет полей для обновления"));
        }

        channel.setUpdatedAt(OffsetDateTime.now());
        if (data.containsKey("token") || data.containsKey("platform")) {
            updateTelegramBotInfo(channel, false);
        }
        channel.setUpdatedAt(OffsetDateTime.now());
        channelRepository.save(channel);
        Map<Long, Map<String, Object>> credentials = buildCredentialIndex(loadBotCredentials());
        return ResponseEntity.ok(Map.of("success", true, "channel", toChannelResponse(channel, credentials)));
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
        response.put("support_chat_id", channel.getSupportChatId());
        response.put("delivery_settings", parseJsonMap(channel.getDeliverySettings()));
        response.put("public_id", channel.getPublicId());
        if (channel.getCredentialId() != null) {
            response.put("credential", credentials.get(channel.getCredentialId()));
        }
        return response;
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

    private boolean sendTelegramMessage(String token, String recipient, String message) {
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
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
        Optional<TelegramBotInfo> info = fetchTelegramBotInfo(channel.getToken());
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

    private Optional<TelegramBotInfo> fetchTelegramBotInfo(String token) {
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
            HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
