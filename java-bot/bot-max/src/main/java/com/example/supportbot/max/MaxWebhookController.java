package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.MessagingService;
import com.example.supportbot.service.PublicFormConversationLinkService;
import com.example.supportbot.service.SharedConfigService;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.PresetReference;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
// @RestController
// @RequestMapping("/webhooks/max")
public class MaxWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MaxWebhookController.class);
    private static final List<String> CORE_LOCATION_FIELDS = List.of("business", "location_type", "city", "location_name");
    private static final Duration LOCATION_CACHE_TTL = Duration.ofMinutes(5);

    private final MaxBotProperties properties;
    private final ChannelService channelService;
    private final TicketService ticketService;
    private final ChatHistoryService chatHistoryService;
    private final MessagingService messagingService;
    private final PublicFormConversationLinkService publicFormConversationLinkService;
    private final BotSettingsService botSettingsService;
    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;

    private final Map<Long, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Object locationCacheMonitor = new Object();
    private volatile Map<String, Object> cachedLocationTree;
    private volatile Map<String, Object> cachedPresetDefinitions;
    private volatile Instant locationCacheUpdatedAt;

    public MaxWebhookController(MaxBotProperties properties,
                                ChannelService channelService,
                                TicketService ticketService,
                                ChatHistoryService chatHistoryService,
                                MessagingService messagingService,
                                PublicFormConversationLinkService publicFormConversationLinkService,
                                BotSettingsService botSettingsService,
                                SharedConfigService sharedConfigService,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.channelService = channelService;
        this.ticketService = ticketService;
        this.chatHistoryService = chatHistoryService;
        this.messagingService = messagingService;
        this.publicFormConversationLinkService = publicFormConversationLinkService;
        this.botSettingsService = botSettingsService;
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    // @PostMapping
    public ResponseEntity<Map<String, Object>> handleUpdate(
        JsonNode update,
        String secret
    ) {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", "max-bot-disabled"));
        }
        if (!secretValid(secret)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "invalid-secret"));
        }
        String updateType = text(update, "update_type");
        if (!"message_created".equals(updateType)) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", updateType));
        }

        JsonNode message = update.path("message");
        Long userId = asLong(message.path("sender").path("user_id"));
        Long chatId = asLong(message.path("recipient").path("chat_id"));
        String text = extractMessageText(message);
        MaxClientProfile clientProfile = resolveClientProfile(message, userId);
        List<MaxIncomingAttachment> attachments = extractIncomingAttachments(message);
        boolean hasAttachments = !attachments.isEmpty();
        if (userId == null || (text.isBlank() && !hasAttachments)) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", "missing-user-or-text"));
        }

        Channel channel = channelService.resolveConfiguredChannel(properties.getChannelId(), properties.getToken(), "MAX", "max");

        String publicFormToken = extractPublicFormContinueToken(text);
        if (publicFormToken != null) {
            PublicFormConversationLinkService.LinkResult result =
                    publicFormConversationLinkService.bindSessionToChannel(publicFormToken, userId, clientProfile.username(), channel);
            if (!result.success()) {
                messagingService.sendToUser(channel, userId, result.error());
            } else if (result.closed()) {
                messagingService.sendToUser(channel, userId,
                        "Диалог #" + result.ticketId() + " привязан к этому боту. Сейчас он закрыт, но после переоткрытия вы сможете продолжить переписку здесь.");
            } else {
                messagingService.sendToUser(channel, userId,
                        "Диалог #" + result.ticketId() + " привязан к этому боту. Продолжайте переписку здесь следующим сообщением.");
            }
            return ResponseEntity.ok(Map.of("ok", true, "continued", true));
        }

        if ("/start".equalsIgnoreCase(text)) {
            ConversationSession session = startSession(userId, chatId, clientProfile.username(), clientProfile.clientName(), channel);
            sessions.put(userId, session);
            promptCurrentQuestion(channel, session);
            return ResponseEntity.ok(Map.of("ok", true));
        }

        if (isCancelCommand(text)) {
            sessions.remove(userId);
            messagingService.sendToUser(channel, userId, "Текущая заявка отменена.");
            return ResponseEntity.ok(Map.of("ok", true, "cancelled", true));
        }

        Optional<TicketActive> active = ticketService.findActiveTicketForUser(userId, clientProfile.identity());
        if (active.isPresent()) {
            sessions.remove(userId);
            String ticketId = active.get().getTicketId();
            String clientText = !text.isBlank() ? text : "[вложение от клиента]";
            String messageType = hasAttachments ? normalizeAttachmentType(attachments.get(0).type()) : "text";
            String attachmentRef = hasAttachments ? attachments.get(0).urlOrName() : null;
            chatHistoryService.storeUserMessage(userId, null, clientText, channel, ticketId, messageType, attachmentRef, null, null);
            ticketService.updateClientProfile(ticketId, clientProfile.username(), clientProfile.clientName());
            ticketService.registerActivity(ticketId, clientProfile.identity());
            notifyOperatorsAboutActiveMessage(channel, ticketId, clientProfile, clientText, messageType, attachmentRef, attachments.size());
            return ResponseEntity.ok(Map.of("ok", true, "ticket_id", ticketId));
        }

        ConversationSession session = sessions.get(userId);
        if (session == null) {
            session = startSession(userId, chatId, clientProfile.username(), clientProfile.clientName(), channel);
            sessions.put(userId, session);
            promptCurrentQuestion(channel, session);
            return ResponseEntity.ok(Map.of("ok", true, "session_started", true));
        }

        if (session.awaitingReuseDecision()) {
            if (!session.consumeReuseDecision(text)) {
                messagingService.sendToUser(channel, userId,
                        "Ответьте 'да', чтобы использовать прошлые значения, или 'нет', чтобы заполнить заново.");
                return ResponseEntity.ok(Map.of("ok", true, "awaiting_reuse_decision", true));
            }
            if (session.isComplete()) {
                TicketService.TicketCreationResult created = finalizeConversation(channel, session);
                return ResponseEntity.ok(Map.of("ok", true, "ticket_id", created.ticketId()));
            }
            promptCurrentQuestion(channel, session);
            return ResponseEntity.ok(Map.of("ok", true, "question_prompted", true));
        }

        QuestionFlowItemDto current = session.currentQuestion();
        String resolvedAnswer = text;
        if (isPresetQuestion(current)) {
            List<String> options = resolvePresetOptions(current, session.answers());
            if (options.isEmpty()) {
                messagingService.sendToUser(channel, userId,
                        "Сейчас нет доступных вариантов для выбора. Обратитесь к администратору.");
                return ResponseEntity.ok(Map.of("ok", true, "missing_options", true));
            }
            resolvedAnswer = resolvePresetAnswer(resolvedAnswer, options);
            if (!options.contains(resolvedAnswer)) {
                messagingService.sendToUser(channel, userId,
                        "Введите один из вариантов: " + String.join(", ", options));
                return ResponseEntity.ok(Map.of("ok", true, "invalid_option", true));
            }
        }

        if (resolvedAnswer.isBlank()) {
            promptCurrentQuestion(channel, session);
            return ResponseEntity.ok(Map.of("ok", true, "blank_answer", true));
        }

        session.recordAnswer(resolvedAnswer);
        if (session.isComplete()) {
            TicketService.TicketCreationResult created = finalizeConversation(channel, session);
            return ResponseEntity.ok(Map.of("ok", true, "ticket_id", created.ticketId()));
        }

        promptCurrentQuestion(channel, session);
        return ResponseEntity.ok(Map.of("ok", true, "question_prompted", true));
    }

    private ConversationSession startSession(Long userId, Long chatId, String username, String clientName, Channel channel) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        List<QuestionFlowItemDto> flow = buildIncidentFlow(settings);

        ConversationSession session = new ConversationSession(userId, chatId, username, clientName, flow, settings);
        ticketService.findLastMessage(userId)
                .ifPresent(last -> session.enableReusePrompt(Map.of(
                        "business", Optional.ofNullable(last.getBusiness()).orElse(""),
                        "location_type", Optional.ofNullable(last.getLocationType()).orElse(""),
                        "city", Optional.ofNullable(last.getCity()).orElse(""),
                        "location_name", Optional.ofNullable(last.getLocationName()).orElse("")
                )));
        return session;
    }

    private List<QuestionFlowItemDto> buildIncidentFlow(BotSettingsDto settings) {
        List<QuestionFlowItemDto> source = new ArrayList<>(Optional.ofNullable(settings.getQuestionFlow()).orElseGet(List::of));
        source.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));

        Map<String, QuestionFlowItemDto> byField = new LinkedHashMap<>();
        for (QuestionFlowItemDto item : source) {
            if (item == null || item.getPreset() == null) {
                continue;
            }
            String field = item.getPreset().field();
            String group = item.getPreset().group();
            if (!"locations".equalsIgnoreCase(group) || field == null || field.isBlank()) {
                continue;
            }
            if (CORE_LOCATION_FIELDS.contains(field) && !byField.containsKey(field)) {
                byField.put(field, item);
            }
        }

        List<QuestionFlowItemDto> normalized = new ArrayList<>();
        int order = 1;
        for (String field : CORE_LOCATION_FIELDS) {
            QuestionFlowItemDto existing = byField.get(field);
            String text = existing != null ? existing.getText() : defaultPrompt(field);
            List<String> excluded = existing != null && existing.getExcludedOptions() != null
                    ? existing.getExcludedOptions()
                    : List.of();
            normalized.add(new QuestionFlowItemDto(
                    field,
                    "preset",
                    (text == null || text.isBlank()) ? defaultPrompt(field) : text,
                    order++,
                    new PresetReference("locations", field),
                    excluded
            ));
        }

        normalized.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", order, null, List.of()));
        return normalized;
    }

    private String defaultPrompt(String field) {
        return switch (field) {
            case "business" -> "Бизнес";
            case "location_type" -> "Тип бизнеса";
            case "city" -> "Город";
            case "location_name" -> "Локация";
            default -> field;
        };
    }

    private void promptCurrentQuestion(Channel channel, ConversationSession session) {
        if (session.awaitingReuseDecision()) {
            messagingService.sendToUser(channel, session.userId(), session.reusePrompt());
            return;
        }
        QuestionFlowItemDto current = session.currentQuestion();
        if (current == null) {
            return;
        }
        List<String> options = isPresetQuestion(current) ? resolvePresetOptions(current, session.answers()) : List.of();
        messagingService.sendToUser(channel, session.userId(), buildQuestionPromptText(current, options));
    }

    private TicketService.TicketCreationResult finalizeConversation(Channel channel, ConversationSession session) {
        sessions.remove(session.userId());
        TicketService.TicketCreationResult created = ticketService.createTicket(
                session.userId(),
                session.username(),
                session.clientName(),
                session.answers(),
                channel
        );
        for (HistoryEvent event : session.history()) {
            chatHistoryService.storeEntry(event.userId(), null, channel, created.ticketId(), event.text(), event.messageType(), null, null, null);
        }
        messagingService.sendToUser(channel, session.userId(), "Заявка создана. Номер: " + created.ticketId());
        messagingService.sendToSupportChat(channel, session.buildSummary(created.ticketId()));
        return created;
    }

    private boolean isPresetQuestion(QuestionFlowItemDto current) {
        if (current == null) {
            return false;
        }
        if ("preset".equalsIgnoreCase(current.getType())) {
            return true;
        }
        return current.getPreset() != null && current.getPreset().field() != null;
    }

    private String buildQuestionPromptText(QuestionFlowItemDto current, List<String> options) {
        StringBuilder text = new StringBuilder(Optional.ofNullable(current.getText()).orElse(""));
        if (options != null && !options.isEmpty()) {
            text.append("\n\nВарианты:");
            for (int i = 0; i < options.size(); i++) {
                text.append("\n").append(i + 1).append(". ").append(options.get(i));
            }
            text.append("\nМожно ответить номером (1, 2, ...) или текстом варианта.");
        }
        return text.toString();
    }

    private String resolvePresetAnswer(String rawAnswer, List<String> options) {
        if (rawAnswer == null) {
            return "";
        }
        String trimmed = rawAnswer.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            int numeric = Integer.parseInt(trimmed);
            if (numeric >= 1 && numeric <= options.size()) {
                return options.get(numeric - 1);
            }
        } catch (NumberFormatException ignored) {
            // fallback to text matching
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(trimmed)) {
                return option;
            }
        }
        return trimmed;
    }

    private List<String> resolvePresetOptions(QuestionFlowItemDto current, Map<String, String> answers) {
        if (current == null || current.getPreset() == null) {
            return List.of();
        }
        String group = current.getPreset().group();
        String field = current.getPreset().field();
        if (field == null || field.isBlank() || group == null || group.isBlank()) {
            return List.of();
        }
        List<String> options;
        if ("locations".equalsIgnoreCase(group)) {
            Map<String, Object> tree = locationTree();
            options = resolveLocationOptions(field, answers, tree);
            if (options.isEmpty()) {
                options = resolvePresetDefinitionOptions(group, field);
            }
        } else {
            options = resolvePresetDefinitionOptions(group, field);
        }
        List<String> excluded = Optional.ofNullable(current.getExcludedOptions()).orElseGet(List::of);
        if (!excluded.isEmpty() && !options.isEmpty()) {
            options = options.stream()
                    .filter(option -> !excluded.contains(option))
                    .toList();
        }
        return options;
    }

    private List<String> resolvePresetDefinitionOptions(String group, String field) {
        if (group == null || field == null) {
            return List.of();
        }
        Map<String, Object> definitions = presetDefinitions();
        Map<String, Object> groupData = asMap(definitions.get(group));
        Map<String, Object> fields = asMap(groupData.get("fields"));
        Map<String, Object> fieldData = asMap(fields.get(field));
        return asList(fieldData.get("options"));
    }

    private List<String> resolveLocationOptions(String field, Map<String, String> answers, Map<String, Object> tree) {
        if (tree.isEmpty()) {
            return List.of();
        }
        String business = answers.get("business");
        String locationType = answers.get("location_type");
        String city = answers.get("city");
        return switch (field) {
            case "business" -> sortedKeys(tree);
            case "location_type" -> business == null ? List.of() : sortedKeys(asMap(tree.get(business)));
            case "city" -> {
                if (business == null || locationType == null) {
                    yield List.of();
                }
                Map<String, Object> businessNode = asMap(tree.get(business));
                yield sortedKeys(asMap(businessNode.get(locationType)));
            }
            case "location_name" -> {
                if (business == null || locationType == null || city == null) {
                    yield List.of();
                }
                Map<String, Object> businessNode = asMap(tree.get(business));
                Map<String, Object> typeNode = asMap(businessNode.get(locationType));
                yield asList(typeNode.get(city));
            }
            default -> List.of();
        };
    }

    private List<String> sortedKeys(Map<String, Object> node) {
        if (node == null || node.isEmpty()) {
            return List.of();
        }
        return node.keySet().stream()
                .map(Object::toString)
                .sorted()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object node) {
        if (node instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private List<String> asList(Object node) {
        if (node instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    private Map<String, Object> locationTree() {
        ensureLocationCacheFresh();
        return cachedLocationTree;
    }

    private Map<String, Object> presetDefinitions() {
        ensureLocationCacheFresh();
        return cachedPresetDefinitions;
    }

    private void ensureLocationCacheFresh() {
        if (isLocationCacheFresh()) {
            return;
        }
        synchronized (locationCacheMonitor) {
            if (isLocationCacheFresh()) {
                return;
            }
            JsonNode locations = sharedConfigService.loadLocations();
            Map<String, Object> resolvedTree = new LinkedHashMap<>();
            if (locations != null && !locations.isNull()) {
                JsonNode treeNode = locations.get("tree");
                Map<String, Object> converted = objectMapper.convertValue(
                        treeNode != null && !treeNode.isNull() ? treeNode : locations,
                        new TypeReference<>() {
                        }
                );
                if (converted != null) {
                    resolvedTree = converted;
                }
            }
            Map<String, Object> baseDefinitions = sharedConfigService.presetDefinitions();
            Map<String, Object> mergedDefinitions = botSettingsService.buildLocationPresets(resolvedTree, baseDefinitions);
            cachedLocationTree = resolvedTree;
            cachedPresetDefinitions = mergedDefinitions != null ? mergedDefinitions : new LinkedHashMap<>();
            locationCacheUpdatedAt = Instant.now();
        }
    }

    private boolean isLocationCacheFresh() {
        return cachedLocationTree != null
                && cachedPresetDefinitions != null
                && locationCacheUpdatedAt != null
                && locationCacheUpdatedAt.plus(LOCATION_CACHE_TTL).isAfter(Instant.now());
    }

    private boolean isCancelCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        return "/cancel".equals(normalized)
                || "cancel".equals(normalized)
                || "отмена".equals(normalized);
    }

    private String extractMessageText(JsonNode message) {
        if (message == null || message.isNull() || message.isMissingNode()) {
            return "";
        }
        String bodyText = text(message.path("body"), "text").trim();
        if (!bodyText.isBlank()) {
            return bodyText;
        }
        return text(message, "text").trim();
    }

    private MaxClientProfile resolveClientProfile(JsonNode message, Long userId) {
        JsonNode sender = message != null ? message.path("sender") : null;
        String username = firstNonBlank(
                text(sender, "username"),
                text(sender, "user_name"),
                text(sender, "screen_name"),
                text(sender, "login")
        );
        String clientName = firstNonBlank(
                text(sender, "name"),
                text(sender, "display_name"),
                joinNames(text(sender, "first_name"), text(sender, "last_name")),
                joinNames(text(sender, "firstName"), text(sender, "lastName")),
                username
        );
        if ((username == null || username.isBlank()) && userId != null) {
            username = "max_" + userId;
        }
        if ((clientName == null || clientName.isBlank()) && userId != null) {
            clientName = "MAX user " + userId;
        }
        return new MaxClientProfile(trimOrNull(username), trimOrNull(clientName), userId);
    }

    private List<MaxIncomingAttachment> extractIncomingAttachments(JsonNode message) {
        List<MaxIncomingAttachment> result = new ArrayList<>();
        if (message == null || message.isNull() || message.isMissingNode()) {
            return result;
        }
        collectIncomingAttachments(result, message.path("attachments"));
        JsonNode body = message.path("body");
        collectIncomingAttachments(result, body.path("attachments"));
        collectIncomingAttachments(result, body.path("media"));
        collectIncomingAttachments(result, body.path("files"));
        return result;
    }

    private void collectIncomingAttachments(List<MaxIncomingAttachment> result, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                addIncomingAttachment(result, item);
            }
            return;
        }
        addIncomingAttachment(result, node);
    }

    private void addIncomingAttachment(List<MaxIncomingAttachment> result, JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return;
        }
        String type = firstNonBlank(
                text(raw, "type"),
                text(raw, "kind"),
                text(raw, "media_type"),
                text(raw, "mime_type"),
                "attachment"
        );
        String url = firstNonBlank(
                text(raw, "url"),
                text(raw, "link"),
                text(raw, "download_url"),
                text(raw, "downloadUrl"),
                text(raw, "src"),
                text(raw.path("file"), "url"),
                text(raw.path("photo"), "url"),
                text(raw.path("video"), "url"),
                text(raw.path("payload"), "url")
        );
        String name = firstNonBlank(
                text(raw, "name"),
                text(raw, "file_name"),
                text(raw, "filename"),
                text(raw.path("file"), "name")
        );
        if ((url == null || url.isBlank()) && (name == null || name.isBlank())) {
            return;
        }
        result.add(new MaxIncomingAttachment(type, trimOrNull(url), trimOrNull(name)));
    }

    private String normalizeAttachmentType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase();
        if (type.contains("animation") || type.contains("gif")) {
            return "animation";
        }
        if (type.contains("video")) {
            return "video";
        }
        if (type.contains("audio") || type.contains("voice")) {
            return "audio";
        }
        if (type.contains("photo") || type.contains("image") || type.contains("sticker")) {
            return "photo";
        }
        if (type.contains("doc") || type.contains("file")) {
            return "document";
        }
        return "attachment";
    }

    private void notifyOperatorsAboutActiveMessage(Channel channel,
                                                   String ticketId,
                                                   MaxClientProfile clientProfile,
                                                   String text,
                                                   String messageType,
                                                   String attachmentRef,
                                                   int attachmentCount) {
        if (channel == null || ticketId == null || ticketId.isBlank()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Новый ответ клиента ").append(clientProfile.displayLabel()).append("\n");
        builder.append("ID заявки: #").append(ticketId).append("\n");
        if (text != null && !text.isBlank()) {
            builder.append(text);
        } else {
            builder.append("[").append(messageType).append("]");
        }
        if (attachmentRef != null && !attachmentRef.isBlank()) {
            builder.append("\nВложение: ").append(attachmentRef);
        } else if (attachmentCount > 0) {
            builder.append("\nВложений: ").append(attachmentCount);
        }
        messagingService.sendToSupportChat(channel, builder.toString());
    }

    private String joinNames(String first, String last) {
        String left = trimOrNull(first);
        String right = trimOrNull(last);
        if (left == null && right == null) {
            return null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + " " + right;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            String normalized = trimOrNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean secretValid(String provided) {
        String expected = properties.getWebhookSecret();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(provided);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.path(field) : null;
        return value == null || value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private Long asLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractPublicFormContinueToken(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String[] parts = normalized.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1].trim() : "";
        if ("/continue".equals(command) && !argument.isBlank()) {
            return argument;
        }
        if ("/start".equals(command) && argument.toLowerCase().startsWith("web_")) {
            String token = argument.substring(4).trim();
            return token.isBlank() ? null : token;
        }
        return null;
    }

    private record MaxIncomingAttachment(String type, String url, String name) {
        String urlOrName() {
            if (url != null && !url.isBlank()) {
                return url;
            }
            return name;
        }
    }

    private record MaxClientProfile(String username, String clientName, Long userId) {
        String identity() {
            if (username != null && !username.isBlank()) {
                return username;
            }
            return userId != null ? userId.toString() : null;
        }

        String displayLabel() {
            if (clientName != null && !clientName.isBlank()) {
                if (username != null && !username.isBlank() && !clientName.equalsIgnoreCase(username)) {
                    return clientName + " (@" + username + ")";
                }
                return clientName;
            }
            if (username != null && !username.isBlank()) {
                return "@" + username;
            }
            return userId != null ? "MAX user " + userId : "клиент";
        }
    }

    private record HistoryEvent(Long userId, String text, String messageType) {
    }

    private static final class ConversationSession {
        private final Long userId;
        private final Long chatId;
        private final String username;
        private final String clientName;
        private final List<QuestionFlowItemDto> flow;
        private final BotSettingsDto settings;
        private final Map<String, String> answers = new LinkedHashMap<>();
        private final List<HistoryEvent> history = new ArrayList<>();
        private final OffsetDateTime startedAt = OffsetDateTime.now();
        private Map<String, String> cachedAnswers = new LinkedHashMap<>();
        private boolean reuseDecisionPending = false;
        private int currentIndex = 0;

        ConversationSession(Long userId,
                            Long chatId,
                            String username,
                            String clientName,
                            List<QuestionFlowItemDto> flow,
                            BotSettingsDto settings) {
            this.userId = userId;
            this.chatId = chatId;
            this.username = username;
            this.clientName = clientName;
            this.flow = flow;
            this.settings = settings;
        }

        QuestionFlowItemDto currentQuestion() {
            if (currentIndex < 0 || currentIndex >= flow.size()) {
                return null;
            }
            return flow.get(currentIndex);
        }

        void recordAnswer(String text) {
            QuestionFlowItemDto current = currentQuestion();
            if (current == null) {
                return;
            }
            String answerKey = answerKeyFor(current);
            if (answerKey != null) {
                answers.put(answerKey, text);
            }
            history.add(new HistoryEvent(userId, text, "text"));
            currentIndex += 1;
        }

        boolean isComplete() {
            return currentIndex >= flow.size();
        }

        Map<String, String> answers() {
            return answers;
        }

        List<HistoryEvent> history() {
            return history;
        }

        Long userId() {
            return userId;
        }

        Long chatId() {
            return chatId;
        }

        String username() {
            return username;
        }

        String clientName() {
            return clientName;
        }

        BotSettingsDto settings() {
            return settings;
        }

        void enableReusePrompt(Map<String, String> defaults) {
            if (defaults == null || defaults.isEmpty()) {
                return;
            }
            this.cachedAnswers = new LinkedHashMap<>(defaults);
            this.reuseDecisionPending = true;
        }

        boolean awaitingReuseDecision() {
            return reuseDecisionPending;
        }

        boolean consumeReuseDecision(String decision) {
            if (!reuseDecisionPending) {
                return true;
            }
            if (decision == null) {
                return false;
            }
            String normalized = decision.trim().toLowerCase();
            if (normalized.startsWith("д") || normalized.startsWith("y")) {
                applyCachedAnswers();
                reuseDecisionPending = false;
                return true;
            }
            if (normalized.startsWith("н") || normalized.startsWith("n")) {
                reuseDecisionPending = false;
                return true;
            }
            return false;
        }

        private void applyCachedAnswers() {
            for (int i = 0; i < flow.size(); i++) {
                QuestionFlowItemDto item = flow.get(i);
                String answerKey = answerKeyFor(item);
                if (answerKey != null && cachedAnswers.containsKey(answerKey)) {
                    answers.put(answerKey, cachedAnswers.get(answerKey));
                    currentIndex = i + 1;
                } else {
                    currentIndex = i;
                    break;
                }
            }
        }

        String reusePrompt() {
            return "Использовать прошлые значения? "
                    + String.format("Бизнес: %s, Тип: %s, Город: %s, Локация: %s. Ответьте 'да' или 'нет'.",
                    cachedAnswers.getOrDefault("business", "—"),
                    cachedAnswers.getOrDefault("location_type", "—"),
                    cachedAnswers.getOrDefault("city", "—"),
                    cachedAnswers.getOrDefault("location_name", "—"));
        }

        String buildSummary(String ticketId) {
            StringBuilder builder = new StringBuilder();
            builder.append("Новая заявка #").append(ticketId)
                    .append(" от пользователя ").append(userId).append("\n");
            builder.append("Создана: ").append(startedAt).append("\n");
            if (chatId != null) {
                builder.append("Чат: ").append(chatId).append("\n");
            }
            builder.append("\n");
            for (QuestionFlowItemDto item : flow) {
                String answerKey = answerKeyFor(item);
                builder.append(item.getText()).append(": ")
                        .append(answerKey != null ? answers.getOrDefault(answerKey, "") : "")
                        .append("\n");
            }
            return builder.toString();
        }

        private String answerKeyFor(QuestionFlowItemDto item) {
            if (item == null) {
                return null;
            }
            if (item.getPreset() != null && item.getPreset().field() != null
                    && !item.getPreset().field().isBlank()) {
                return item.getPreset().field();
            }
            return item.getId();
        }
    }
}
