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
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private volatile Map<String, Object> cachedLocationTree;
    private volatile Map<String, Object> cachedPresetDefinitions;

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
        String text = text(message.path("body"), "text").trim();
        String username = text(message.path("sender"), "username");
        if (userId == null || text.isBlank()) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", "missing-user-or-text"));
        }

        Channel channel = channelService.resolveConfiguredChannel(properties.getChannelId(), properties.getToken(), "MAX", "max");

        String publicFormToken = extractPublicFormContinueToken(text);
        if (publicFormToken != null) {
            PublicFormConversationLinkService.LinkResult result =
                    publicFormConversationLinkService.bindSessionToChannel(publicFormToken, userId, username, channel);
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
            ConversationSession session = startSession(userId, chatId, username, channel);
            sessions.put(userId, session);
            promptCurrentQuestion(channel, session);
            return ResponseEntity.ok(Map.of("ok", true));
        }

        if ("/cancel".equalsIgnoreCase(text)) {
            sessions.remove(userId);
            messagingService.sendToUser(channel, userId, "Текущая заявка отменена.");
            return ResponseEntity.ok(Map.of("ok", true, "cancelled", true));
        }

        Optional<TicketActive> active = ticketService.findActiveTicketForUser(userId, username);
        if (active.isPresent()) {
            sessions.remove(userId);
            String ticketId = active.get().getTicketId();
            chatHistoryService.storeUserMessage(userId, null, text, channel, ticketId, "text", null, null, null);
            messagingService.sendToUser(channel, userId, "Сообщение добавлено в заявку #" + ticketId + ".");
            return ResponseEntity.ok(Map.of("ok", true, "ticket_id", ticketId));
        }

        ConversationSession session = sessions.computeIfAbsent(userId, ignored -> {
            ConversationSession created = startSession(userId, chatId, username, channel);
            promptCurrentQuestion(channel, created);
            return created;
        });

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

    private ConversationSession startSession(Long userId, Long chatId, String username, Channel channel) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        List<QuestionFlowItemDto> flow = new ArrayList<>(Optional.ofNullable(settings.getQuestionFlow()).orElseGet(List::of));
        flow.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));
        flow.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", flow.size() + 1, null, List.of()));

        ConversationSession session = new ConversationSession(userId, chatId, username, flow, settings);
        ticketService.findLastMessage(userId)
                .ifPresent(last -> session.enableReusePrompt(Map.of(
                        "business", Optional.ofNullable(last.getBusiness()).orElse(""),
                        "location_type", Optional.ofNullable(last.getLocationType()).orElse(""),
                        "city", Optional.ofNullable(last.getCity()).orElse(""),
                        "location_name", Optional.ofNullable(last.getLocationName()).orElse("")
                )));
        return session;
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
        if (cachedLocationTree != null) {
            return cachedLocationTree;
        }
        JsonNode locations = sharedConfigService.loadLocations();
        if (locations == null || locations.isNull()) {
            cachedLocationTree = new LinkedHashMap<>();
            return cachedLocationTree;
        }
        JsonNode treeNode = locations.get("tree");
        Map<String, Object> resolved = objectMapper.convertValue(
                treeNode != null && !treeNode.isNull() ? treeNode : locations,
                new TypeReference<>() {
                }
        );
        cachedLocationTree = resolved != null ? resolved : new LinkedHashMap<>();
        return cachedLocationTree;
    }

    private Map<String, Object> presetDefinitions() {
        if (cachedPresetDefinitions != null) {
            return cachedPresetDefinitions;
        }
        Map<String, Object> baseDefinitions = sharedConfigService.presetDefinitions();
        Map<String, Object> merged = botSettingsService.buildLocationPresets(locationTree(), baseDefinitions);
        cachedPresetDefinitions = merged != null ? merged : new LinkedHashMap<>();
        return cachedPresetDefinitions;
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

    private record HistoryEvent(Long userId, String text, String messageType) {
    }

    private static final class ConversationSession {
        private final Long userId;
        private final Long chatId;
        private final String username;
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
                            List<QuestionFlowItemDto> flow,
                            BotSettingsDto settings) {
            this.userId = userId;
            this.chatId = chatId;
            this.username = username;
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
