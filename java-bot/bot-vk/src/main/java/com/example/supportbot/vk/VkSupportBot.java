package com.example.supportbot.vk;

import com.example.supportbot.config.VkBotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.service.ActiveInboundClientMessageCommand;
import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.BlacklistService;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.ConversationHistoryEntry;
import com.example.supportbot.service.ConversationProblemTextSupport;
import com.example.supportbot.service.ConversationTicketCreationCommand;
import com.example.supportbot.service.FeedbackService;
import com.example.supportbot.service.BotIngressCoordinationService;
import com.example.supportbot.service.BotSessionStoreService;
import com.example.supportbot.service.RuntimeConfigService;
import com.example.supportbot.service.SessionStateConflictException;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.service.UnblockRequestService;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.QuestionOptionDto;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionRouteDto;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.callback.longpoll.responses.GetLongPollEventsResponse;
import com.vk.api.sdk.objects.messages.AudioMessage;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.MessageAttachment;
import com.vk.api.sdk.objects.messages.MessageAttachmentType;
import com.vk.api.sdk.objects.docs.Doc;
import com.vk.api.sdk.objects.groups.responses.GetLongPollServerResponse;
import com.vk.api.sdk.objects.photos.Photo;
import com.vk.api.sdk.objects.photos.PhotoSizes;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class VkSupportBot implements SmartLifecycle, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(VkSupportBot.class);
    private static final int MAX_LOG_TEXT_LENGTH = 160;
    private static final String SKIP_BUTTON = "Пропустить";
    private static final int DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES = 10;
    private static final String DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE =
            "Вы не ответили. Диалог был закрыт. При возникновении или актуализации вопросов создайте новое обращение.";
    private static final Duration VK_PROFILE_CACHE_TTL = Duration.ofMinutes(30);
    private static final String SESSION_PLATFORM = "vk";
    private static final String UNBLOCK_DIGEST_JOB = "unblock-digest";
    private static final String EXPIRE_SESSIONS_JOB = "expire-silent-question-flow-sessions";
    private static final int SESSION_MUTATION_MAX_RETRIES = 3;

    private final VkBotProperties properties;
    private final BlacklistService blacklistService;
    private final UnblockRequestService unblockRequestService;
    private final AttachmentService attachmentService;
    private final ChannelService channelService;
    private final BotSettingsService botSettingsService;
    private final TicketService ticketService;
    private final ChatHistoryService chatHistoryService;
    private final FeedbackService feedbackService;
    private final BotIngressCoordinationService ingressCoordinationService;
    private final BotSessionStoreService sessionStoreService;
    private final RuntimeConfigService runtimeConfigService;
    private final ObjectMapper objectMapper;
    private final Gson gson;
    private final VkApiClient vkClient;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Long, CachedVkProfile> vkProfileCache = new ConcurrentHashMap<>();

    private static String defaultFirstResponseTimeoutMessage() {
        return "Вы не ответили. Диалог был закрыт. При возникновении или актуализации вопросов создайте новое обращение.";
    }

    private volatile boolean running = false;
    private volatile Channel cachedChannel;
    private volatile Map<String, Object> cachedLocationTree;
    private volatile Map<String, Object> cachedPresetDefinitions;

    public VkSupportBot(VkBotProperties properties,
                        BlacklistService blacklistService,
                        UnblockRequestService unblockRequestService,
                        AttachmentService attachmentService,
                        ChannelService channelService,
                        BotSettingsService botSettingsService,
                        TicketService ticketService,
                        ChatHistoryService chatHistoryService,
                        FeedbackService feedbackService,
                        BotIngressCoordinationService ingressCoordinationService,
                        BotSessionStoreService sessionStoreService,
                        RuntimeConfigService runtimeConfigService,
                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.unblockRequestService = unblockRequestService;
        this.attachmentService = attachmentService;
        this.channelService = channelService;
        this.botSettingsService = botSettingsService;
        this.ticketService = ticketService;
        this.chatHistoryService = chatHistoryService;
        this.feedbackService = feedbackService;
        this.ingressCoordinationService = ingressCoordinationService;
        this.sessionStoreService = sessionStoreService;
        this.runtimeConfigService = runtimeConfigService;
        this.objectMapper = objectMapper;
        this.gson = new Gson();
        this.vkClient = new VkApiClient(new HttpTransportClient());
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("VK bot is disabled; skipping start");
            return;
        }
        if (properties.isWebhookEnabled()) {
            log.info("VK webhook mode is enabled; skipping long-poll runner startup");
            return;
        }
        running = true;
        executor.submit(this::runLoop);
        log.info("VK long poll runner started");
    }

    public void handleIncomingMessage(Message message) {
        if (message == null) {
            return;
        }
        GroupActor actor = createActor();
        handleIncomingMessage(actor, message, 0);
    }

    private void handleIncomingMessage(GroupActor actor, Message message, int attempt) {
        try {
            onMessage(actor, message);
        } catch (SessionStateConflictException ex) {
            if (attempt + 1 >= SESSION_MUTATION_MAX_RETRIES) {
                Long userId = message != null ? message.getFromId() : null;
                log.warn("VK session mutation conflict for user {} after {} attempt(s): {}",
                    userId,
                    attempt + 1,
                    ex.getMessage());
                return;
            }
            Long userId = message != null ? message.getFromId() : null;
            log.info("Retrying VK session mutation for user {} after optimistic conflict (attempt {}/{})",
                userId,
                attempt + 2,
                SESSION_MUTATION_MAX_RETRIES);
            handleIncomingMessage(actor, message, attempt + 1);
        }
    }

    private void runLoop() {
        GroupActor actor = createActor();
        LongPollState state = fetchLongPollState(actor);
        while (running) {
            try {
                if (!ingressCoordinationService.tryAcquireOrRenew("vk", properties.getChannelId())) {
                    sleepSilently(ingressCoordinationService.followerBackoff());
                    continue;
                }
                GetLongPollEventsResponse response = vkClient.longPoll()
                        .getEvents(state.server(), state.key(), state.ts())
                        .waitTime(25)
                        .execute();
                state = state.withTs(response != null ? response.getTs() : state.ts());
                handleUpdates(actor, response);
            } catch (ApiException | ClientException e) {
                log.warn("VK long poll state refresh required, requesting new server", e);
                state = fetchLongPollState(actor);
            } catch (Exception ex) {
                log.error("VK long poll failed", ex);
            }
            sleepSilently(Duration.ofSeconds(Math.max(1L, properties.getRetryDelaySeconds())));
        }
    }

    private GroupActor createActor() {
        Integer groupId = properties.getGroupId();
        if (groupId == null) {
            throw new IllegalStateException("VK groupId is not configured");
        }
        return new GroupActor(groupId.longValue(), properties.getToken());
    }

    private LongPollState fetchLongPollState(GroupActor actor) {
        try {
            GetLongPollServerResponse response = vkClient.groupsLongPoll()
                    .getLongPollServer(actor, actor.getGroupId())
                    .execute();
            if (response == null || response.getServer() == null || response.getKey() == null || response.getTs() == null) {
                throw new IllegalStateException("VK long poll server response is incomplete");
            }
            return new LongPollState(response.getServer(), response.getKey(), response.getTs());
        } catch (ApiException | ClientException e) {
            throw new IllegalStateException("Failed to initialize VK long poll server", e);
        }
    }

    private void handleUpdates(GroupActor actor, GetLongPollEventsResponse response) {
        if (response == null || response.getUpdates() == null) {
            return;
        }
        log.info("Received VK long poll response with {} update(s)", response.getUpdates().size());
        for (JsonObject update : response.getUpdates()) {
            if (update == null || !update.has("type") || !"message_new".equals(update.get("type").getAsString())) {
                continue;
            }
            if (!update.has("object") || !update.get("object").isJsonObject()) {
                continue;
            }
            JsonObject object = update.getAsJsonObject("object");
            if (!object.has("message")) {
                continue;
            }
            Message message = gson.fromJson(object.get("message"), Message.class);
            if (message != null) {
                onMessage(actor, message);
            }
        }
    }

    private void onMessage(GroupActor actor, Message message) {
        Long fromId = message.getFromId();
        Long peerId = message.getPeerId();
        if (fromId == null || peerId == null || !peerId.equals(fromId)) {
            // ignore group chats
            log.info("Ignoring VK message from peer {} user {}: not a direct message", peerId, fromId);
            return;
        }
        logIncomingMessage(message);
        Channel channel = getChannel();
        BlacklistService.BlacklistStatus status = blacklistService.getStatus(fromId);
        if (status.blacklisted()) {
            log.info("Blocked message from blacklisted VK user {}", fromId);
            String text = Optional.ofNullable(message.getText()).orElse("").trim();
            if ("/unblock".equalsIgnoreCase(text)) {
                handleUnblockRequest(actor, peerId, fromId, channel);
            } else {
                sendText(actor, peerId, status.unblockRequested()
                        ? "Ваш аккаунт заблокирован. Запрос уже на рассмотрении."
                        : "Ваш аккаунт заблокирован. Ответьте /unblock, чтобы подать запрос.");
            }
            return;
        }

        String text = Optional.ofNullable(message.getText()).orElse("").trim();
        VkClientProfile clientProfile = resolveVkClientProfile(fromId);
        if ("/start".equalsIgnoreCase(text)) {
            log.info("Received /start from VK user {}", fromId);
        }
        if (!text.isEmpty() && tryHandleFeedback(actor, message, channel, text)) {
            log.info("Handled feedback from VK user {}", fromId);
            return;
        }
        if (isMyTicketsCommand(text)) {
            log.info("Received my tickets command from VK user {}", fromId);
            handleMyTickets(actor, peerId, fromId);
            return;
        }

        ConversationSession session = loadSession(fromId);
        if ("/cancel".equalsIgnoreCase(text)) {
            log.info("Received /cancel from VK user {}", fromId);
            if (session != null) {
                deleteSession(session);
            }
            sendText(actor, peerId, "Текущая заявка отменена.");
            return;
        }
        if ("/unblock".equalsIgnoreCase(text)) {
            log.info("Received /unblock from VK user {}", fromId);
            handleUnblockRequest(actor, peerId, fromId, channel);
            return;
        }

        if (session == null) {
            if (handleActiveTicketMessage(actor, message, channel, text, clientProfile)) {
                return;
            }
            session = startSession(actor, message, channel, clientProfile);
            if (shouldCaptureBootstrapProblemText(text)) {
                session.captureBootstrapClientText(text);
            }
            saveSession(session);
            return;
        }

        session.markClientResponseReceived();
        saveSession(session);

        if (session.awaitingReuseDecision()) {
            if (!session.consumeReuseDecision(text)) {
                sendText(actor, peerId, "Ответьте 'да', чтобы использовать прошлые значения, или 'нет', чтобы заполнить заново.");
                return;
            }
            if (session.isComplete()) {
                finalizeConversation(actor, session);
            } else {
                saveSession(session);
                promptCurrentQuestion(actor, session);
            }
            return;
        }

        String resolvedAnswer = text;
        QuestionFlowItemDto current = session.currentQuestion();
        if (isOptionalFreeQuestion(current) && SKIP_BUTTON.equalsIgnoreCase(String.valueOf(text).trim())) {
            resolvedAnswer = "";
        } else if (isChoiceQuestion(current)) {
            List<String> options = resolveQuestionOptions(current, session.answers());
            if (options.isEmpty()) {
                sendText(actor, peerId, "Сейчас нет доступных вариантов для выбора. Обратитесь к администратору.");
                return;
            }
            resolvedAnswer = resolveChoiceAnswer(resolvedAnswer, options, current, session.settings());
            if (!options.contains(resolvedAnswer)) {
                sendText(actor, peerId, "Введите один из вариантов: " + String.join(", ", options));
                return;
            }
        }

        if (!resolvedAnswer.isBlank() || isOptionalFreeQuestion(current)) {
            session.recordAnswer(resolvedAnswer);
        }
        storeAttachments(message, session);

        if (session.isComplete()) {
            finalizeConversation(actor, session);
        } else {
            saveSession(session);
            promptCurrentQuestion(actor, session);
        }
    }

    private boolean tryHandleFeedback(GroupActor actor, Message message, Channel channel, String text) {
        if (!text.matches("\\d+")) {
            return false;
        }
        Optional<PendingFeedbackRequest> pendingOpt = feedbackService.findActiveRequest(message.getFromId(), channel);
        if (pendingOpt.isEmpty()) {
            return false;
        }
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        Set<String> allowed = botSettingsService.ratingAllowedValues(settings);
        if (!allowed.contains(text)) {
            sendText(actor, message.getPeerId(), "Отправьте число от 1 до " + botSettingsService.ratingScale(settings, 5));
            return true;
        }
        int rating = Integer.parseInt(text);
        feedbackService.storeFeedback(pendingOpt.get(), rating);
        String response = botSettingsService.ratingResponseFor(settings, rating).orElse("Спасибо за оценку!");
        sendText(actor, message.getPeerId(), response);
        return true;
    }

    private VkClientProfile resolveVkClientProfile(Long userId) {
        if (userId == null || userId <= 0) {
            return VkClientProfile.empty(userId);
        }
        Instant now = Instant.now();
        CachedVkProfile cached = vkProfileCache.get(userId);
        if (cached != null && cached.profile() != null && cached.expiresAt() != null && cached.expiresAt().isAfter(now)) {
            return cached.profile();
        }
        VkClientProfile resolved = fetchVkClientProfile(userId);
        vkProfileCache.put(userId, new CachedVkProfile(resolved, now.plus(VK_PROFILE_CACHE_TTL)));
        return resolved;
    }

    private VkClientProfile fetchVkClientProfile(Long userId) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(properties.getToken())) {
            return VkClientProfile.empty(userId);
        }
        try {
            String query = "user_ids=" + userId
                    + "&fields=screen_name"
                    + "&access_token=" + URLEncoder.encode(properties.getToken(), StandardCharsets.UTF_8)
                    + "&v=5.199";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.vk.com/method/users.get?" + query))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return VkClientProfile.empty(userId);
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("error").isMissingNode() && !root.path("error").isNull()) {
                return VkClientProfile.empty(userId);
            }
            JsonNode responseNode = root.path("response");
            if (!responseNode.isArray() || responseNode.isEmpty()) {
                return VkClientProfile.empty(userId);
            }
            JsonNode userNode = responseNode.get(0);
            String screenName = trimOrNull(userNode.path("screen_name").asText(""));
            String firstName = trimOrNull(userNode.path("first_name").asText(""));
            String lastName = trimOrNull(userNode.path("last_name").asText(""));
            String username = screenName != null ? screenName : ("id" + userId);
            String clientName = trimOrNull((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName));
            if (clientName == null) {
                clientName = username;
            }
            return new VkClientProfile(username, clientName, userId);
        } catch (Exception ex) {
            log.debug("Failed to resolve VK profile for {}: {}", userId, ex.getMessage());
            return VkClientProfile.empty(userId);
        }
    }

    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ConversationSession startSession(GroupActor actor, Message message, Channel channel, VkClientProfile clientProfile) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        List<QuestionFlowItemDto> flow = new ArrayList<>(botSettingsService.questionFlow(settings));
        flow.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));
        flow.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", flow.size() + 1, null, List.of()));

        ConversationSession session = new ConversationSession(
                message.getPeerId(),
                message.getFromId(),
                clientProfile != null ? clientProfile.username() : null,
                clientProfile != null ? clientProfile.clientName() : null,
                flow,
                settings
        );
        log.info("Starting VK conversation for user {} peer {} with {} questions",
                session.userId(),
                session.peerId(),
                flow.size());
        ticketService.findLastMessage(message.getFromId())
                .ifPresent(last -> session.enableReusePrompt(Map.of(
                        "business", Optional.ofNullable(last.getBusiness()).orElse(""),
                        "location_type", Optional.ofNullable(last.getLocationType()).orElse(""),
                        "city", Optional.ofNullable(last.getCity()).orElse(""),
                        "location_name", Optional.ofNullable(last.getLocationName()).orElse("")
                )));
        String startAutoReply = botSettingsService.startAutoReply(
                settings,
                "Здравствуйте! Опишите, пожалуйста, ваш вопрос, чтобы мы могли быстрее помочь."
        );
        if (!session.awaitingReuseDecision() && startAutoReply != null && !startAutoReply.isBlank()) {
            sendText(actor, message.getPeerId(), startAutoReply);
        }
        promptCurrentQuestion(actor, session);
        return session;
    }

    private boolean shouldCaptureBootstrapProblemText(String text) {
        String normalized = ConversationProblemTextSupport.trimToNull(text);
        return normalized != null && !normalized.startsWith("/");
    }

    private void promptCurrentQuestion(GroupActor actor, ConversationSession session) {
        if (session.awaitingReuseDecision()) {
            sendText(actor, session.peerId(), session.reusePrompt());
            return;
        }
        QuestionFlowItemDto current = session.currentQuestion();
        if (current != null) {
            List<String> options = isChoiceQuestion(current) ? resolveQuestionOptions(current, session.answers()) : List.of();
            sendText(actor, session.peerId(), buildQuestionPromptText(current, options));
        }
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

    private boolean isSelectQuestion(QuestionFlowItemDto current) {
        return current != null
                && "select".equalsIgnoreCase(Optional.ofNullable(current.getType()).orElse(""))
                && current.getOptions() != null
                && !current.getOptions().isEmpty();
    }

    private boolean isChoiceQuestion(QuestionFlowItemDto current) {
        return isPresetQuestion(current) || isSelectQuestion(current);
    }

    private boolean isOptionalFreeQuestion(QuestionFlowItemDto current) {
        return current != null && !isChoiceQuestion(current) && !current.isRequiredAnswer();
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
        if (isOptionalFreeQuestion(current)) {
            text.append("\n\nМожно пропустить вопрос: отправьте \"").append(SKIP_BUTTON).append("\".");
        }
        return text.toString();
    }

    private String resolveChoiceAnswer(String rawAnswer,
                                       List<String> options,
                                       QuestionFlowItemDto question,
                                       BotSettingsDto settings) {
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
        if (isBusinessPresetQuestion(question)) {
            String normalizedInput = normalizeAlias(trimmed);
            if (!normalizedInput.isBlank()) {
                Map<String, List<String>> aliases = botSettingsService.businessAliases(settings);
                for (Map.Entry<String, List<String>> aliasEntry : aliases.entrySet()) {
                    String canonicalBusiness = aliasEntry.getKey();
                    if (normalizedInput.equals(normalizeAlias(canonicalBusiness))) {
                        String matched = matchOptionByValue(options, canonicalBusiness);
                        if (matched != null) {
                            return matched;
                        }
                    }
                    for (String alias : aliasEntry.getValue()) {
                        if (normalizedInput.equals(normalizeAlias(alias))) {
                            String matched = matchOptionByValue(options, canonicalBusiness);
                            if (matched != null) {
                                return matched;
                            }
                        }
                    }
                }
            }
        }
        return trimmed;
    }

    private boolean isBusinessPresetQuestion(QuestionFlowItemDto question) {
        if (question == null || question.getPreset() == null) {
            return false;
        }
        String group = question.getPreset().group();
        String field = question.getPreset().field();
        return "locations".equalsIgnoreCase(group) && "business".equalsIgnoreCase(field);
    }

    private String matchOptionByValue(List<String> options, String value) {
        if (options == null || value == null) {
            return null;
        }
        for (String option : options) {
            if (option != null && option.equalsIgnoreCase(value)) {
                return option;
            }
        }
        return null;
    }

    private String normalizeAlias(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[\\p{Punct}\\s]+", "");
    }

    private List<String> resolveQuestionOptions(QuestionFlowItemDto current, Map<String, String> answers) {
        if (isSelectQuestion(current)) {
            return current.getOptions().stream()
                    .map(QuestionOptionDto::getLabel)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        return resolvePresetOptions(current, answers);
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

    @SuppressWarnings("unchecked")
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
        Map<String, Object> resolved = runtimeConfigService.locationTree(getChannel());
        cachedLocationTree = resolved != null ? resolved : new LinkedHashMap<>();
        return cachedLocationTree;
    }

    private Map<String, Object> presetDefinitions() {
        if (cachedPresetDefinitions != null) {
            return cachedPresetDefinitions;
        }
        Map<String, Object> baseDefinitions = runtimeConfigService.basePresetDefinitions(getChannel());
        Map<String, Object> merged = botSettingsService.buildLocationPresets(locationTree(), baseDefinitions);
        cachedPresetDefinitions = merged != null ? merged : new LinkedHashMap<>();
        return cachedPresetDefinitions;
    }

    private boolean handleActiveTicketMessage(GroupActor actor,
                                              Message message,
                                              Channel channel,
                                              String text,
                                              VkClientProfile clientProfile) {
        Optional<String> activeTicketId = resolveActiveTicketId(message, clientProfile);
        if (activeTicketId.isEmpty()) {
            return false;
        }
        String ticketId = activeTicketId.get();
        Optional<TicketService.TicketWithUser> ticketDetails = ticketService.findByTicketId(ticketId);
        if (ticketDetails.isEmpty()) {
            return false;
        }
        if (isClosedStatus(ticketDetails.get().status())) {
            return false;
        }
        Long userId = message.getFromId();
        String username = clientProfile != null ? clientProfile.identity() : (userId != null ? userId.toString() : null);
        boolean hasAttachments = message.getAttachments() != null && !message.getAttachments().isEmpty();
        String clientText = text != null && !text.isBlank()
                ? text
                : (hasAttachments ? "[вложение от клиента]" : "");
        if (clientText.isBlank()) {
            return false;
        }
        String messageType = hasAttachments && (text == null || text.isBlank()) ? "attachment" : "text";
        ticketService.recordActiveClientMessage(new ActiveInboundClientMessageCommand(
                userId,
                username,
                clientProfile != null ? clientProfile.username() : null,
                clientProfile != null ? clientProfile.clientName() : null,
                channel,
                ticketId,
                clientText,
                messageType,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now()
        ));
        relayActiveMessageToOperators(actor, ticketId, clientText, userId, hasAttachments, clientProfile);
        return true;
    }

    private Optional<String> resolveActiveTicketId(Message message, VkClientProfile clientProfile) {
        Long userId = message != null ? message.getFromId() : null;
        String username = clientProfile != null ? clientProfile.identity() : (userId != null ? userId.toString() : null);
        Long channelId = Optional.ofNullable(getChannel()).map(Channel::getId).orElse(null);
        return ticketService.findActiveTicketForUser(userId, username, channelId).map(TicketActive::getTicketId);
    }

    private void relayActiveMessageToOperators(GroupActor actor,
                                               String ticketId,
                                               String text,
                                               Long userId,
                                               boolean hasAttachments,
                                               VkClientProfile clientProfile) {
        Long channelId = properties.getChannelId();
        if (channelId == null || channelId <= 0) {
            return;
        }
        String senderLabel = clientProfile != null
                ? clientProfile.displayLabel()
                : (userId != null ? String.valueOf(userId) : "client");
        StringBuilder builder = new StringBuilder();
        builder.append("Новый ответ клиента ").append(senderLabel).append("\n");
        builder.append("ID заявки: #").append(ticketId).append("\n");
        builder.append(text);
        if (hasAttachments) {
            builder.append("\nВ сообщении есть вложения.");
        }
        sendText(actor, channelId, builder.toString());
    }

    private boolean isClosedStatus(String status) {
        if (status == null) {
            return false;
        }
        return "closed".equalsIgnoreCase(status) || "resolved".equalsIgnoreCase(status);
    }

    private void finalizeConversation(GroupActor actor, ConversationSession session) {
        deleteSession(session);
        Channel channel = getChannel();
        TicketService.TicketCreationResult result = ticketService.createConversationTicket(
                new ConversationTicketCreationCommand(
                        session.userId(),
                        session.username() != null ? session.username() : String.valueOf(session.userId()),
                        session.username(),
                        session.clientName(),
                        session.answers(),
                        session.ticketAttributes(),
                        session.history().stream()
                                .map(event -> new ConversationHistoryEntry(
                                        event.userId(),
                                        event.text(),
                                        event.messageType(),
                                        event.attachment(),
                                        null,
                                        null,
                                        session.startedAt()
                                ))
                                .toList(),
                        channel,
                        session.startedAt()
                )
        );
        String requestNumber = result.groupMessageId() != null ? result.groupMessageId().toString() : result.ticketId();
        log.info("Created VK ticket {} for user {}", result.ticketId(), session.userId());
        sendText(actor, session.peerId(), "Спасибо! Ваше обращение №" + requestNumber + " отправлено оператору.");
        if (properties.getChannelId() != null && properties.getChannelId() > 0) {
            sendText(actor, properties.getChannelId().longValue(), session.buildSummary(result.ticketId()));
        }
        int scale = botSettingsService.ratingScale(session.settings(), 5);
        String prompt = botSettingsService.ratingPrompt(session.settings(), "Оцените заявку {ticket_id} по шкале 1-{scale}")
                .replace("{ticket_id}", requestNumber)
                .replace("{scale}", Integer.toString(scale));
        sendText(actor, session.peerId(), prompt);
    }

    private void storeAttachments(Message message, ConversationSession session) {
        List<MessageAttachment> attachments = Optional.ofNullable(message.getAttachments()).orElseGet(List::of);
        for (MessageAttachment attachment : attachments) {
            if (attachment.getType() == null) {
                continue;
            }
            try {
                switch (attachment.getType()) {
                    case PHOTO -> handlePhoto(attachment.getPhoto(), session);
                    case DOC -> handleDoc(attachment.getDoc(), session);
                    case AUDIO_MESSAGE -> handleAudioMessage(attachment.getAudioMessage(), session);
                    default -> log.debug("Unhandled attachment type {}", attachment.getType());
                }
            } catch (Exception e) {
                log.error("Failed to store attachment {}", attachment.getType(), e);
            }
        }
    }

    private void handlePhoto(Photo photo, ConversationSession session) throws Exception {
        if (photo == null || photo.getSizes() == null) {
            return;
        }
        PhotoSizes largest = photo.getSizes().stream().max(Comparator.comparing(PhotoSizes::getHeight)).orElse(null);
        if (largest == null || largest.getUrl() == null) {
            return;
        }
        storeFromUrl(largest.getUrl().toString(), "jpg", session, "photo");
    }

    private void handleDoc(Doc doc, ConversationSession session) throws Exception {
        if (doc == null || doc.getUrl() == null) {
            return;
        }
        storeFromUrl(doc.getUrl().toString(), extensionFrom(doc.getExt(), "bin"), session, "document");
    }

    private void handleAudioMessage(AudioMessage audio, ConversationSession session) throws Exception {
        if (audio == null || audio.getLinkOgg() == null) {
            return;
        }
        storeFromUrl(audio.getLinkOgg().toString(), "ogg", session, MessageAttachmentType.AUDIO_MESSAGE.getValue());
    }

    private void storeFromUrl(String url, String extension, ConversationSession session, String messageType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            AttachmentService.StoredAttachment stored = attachmentService.store(getChannel().getPublicId(), extension, response.body());
            session.addAttachment(stored, messageType);
        } else {
            log.warn("Failed to download attachment {} -> status {}", url, response.statusCode());
        }
    }

    public boolean sendDirectMessage(Integer peerId, String text) {
        if (!properties.isEnabled() || peerId == null || text == null || text.isBlank()) {
            return false;
        }
        GroupActor actor = createActor();
        try {
            log.info("Sending VK direct message to peer {}: {}", peerId, summarizeText(text));
            vkClient.messages()
                    .sendDeprecated(actor)
                    .peerId(peerId.longValue())
                    .randomId(ThreadLocalRandom.current().nextInt())
                    .message(text)
                    .execute();
            return true;
        } catch (ApiException | ClientException e) {
            log.error("Failed to send VK message to peer {}", peerId, e);
            return false;
        }
    }

    private void sendText(GroupActor actor, Long peerId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            log.info("Sending VK message to peer {}: {}", peerId, summarizeText(text));
            vkClient.messages()
                    .sendDeprecated(actor)
                    .peerId(peerId)
                    .randomId(ThreadLocalRandom.current().nextInt())
                    .message(text)
                    .execute();
        } catch (ApiException | ClientException e) {
            log.error("Failed to send VK message to peer {}", peerId, e);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void sendUnblockDigest() {
        if (!ingressCoordinationService.tryAcquireOrRenewJob(SESSION_PLATFORM, properties.getChannelId(), UNBLOCK_DIGEST_JOB)) {
            return;
        }
        Long channelId = properties.getChannelId();
        if (channelId == null || channelId <= 0) {
            return;
        }
        long pending = unblockRequestService.countPending();
        if (pending == 0) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Сводка по разблокировкам: ").append(pending).append(" в ожидании.\n");
        var recent = unblockRequestService.findRecentPending(3);
        if (!recent.isEmpty()) {
            builder.append("Последние запросы:\n");
            for (var request : recent) {
                builder.append("• ").append(request.getUserId());
                if (request.getCreatedAt() != null) {
                    builder.append(" (").append(formatTimestamp(request.getCreatedAt())).append(")");
                }
                builder.append("\n");
            }
        }
        sendOperatorMessage(channelId, builder.toString().trim());
    }

    @Scheduled(fixedDelay = 60000L)
    public void expireSilentQuestionFlowSessions() {
        if (!ingressCoordinationService.tryAcquireOrRenewJob(SESSION_PLATFORM, properties.getChannelId(), EXPIRE_SESSIONS_JOB)) {
            return;
        }
        Channel channel = getChannel();
        if (channel == null) {
            return;
        }
        GroupActor actor = createActor();
        OffsetDateTime now = OffsetDateTime.now();
        sessionStoreService.loadAll(SESSION_PLATFORM, properties.getChannelId(), ConversationSessionState.class).forEach(storedSession -> {
            ConversationSession session = restoreSession(storedSession);
            int timeoutMinutes = botSettingsService.firstResponseTimeoutMinutes(
                    session.settings(),
                    DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES
            );
            if (!session.shouldExpireDueToMissingFirstResponse(now, timeoutMinutes)) {
                return;
            }
            if (!sessionStoreService.deleteIfUnchanged(
                    SESSION_PLATFORM,
                    properties.getChannelId(),
                    storedSession.userId(),
                    storedSession.rawPayload())) {
                return;
            }
            sendText(actor, session.peerId(), botSettingsService.firstResponseTimeoutMessage(
                    session.settings(),
                    defaultFirstResponseTimeoutMessage()
            ));
            log.info("Expired VK question-flow session for user {} after {} minutes without first response",
                    storedSession.userId(),
                    timeoutMinutes);
        });
    }

    private void notifyOperatorsAboutUnblockRequest(GroupActor actor,
                                                    com.example.supportbot.entity.ClientUnblockRequest request) {
        Long channelId = properties.getChannelId();
        if (channelId == null || channelId <= 0 || request == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Новый запрос на разблокировку\n");
        if (request.getId() != null) {
            builder.append("Заявка: #").append(request.getId()).append("\n");
        }
        builder.append("Клиент: ").append(request.getUserId()).append("\n");
        if (request.getReason() != null && !request.getReason().isBlank()) {
            builder.append("Причина: ").append(request.getReason()).append("\n");
        }
        if (request.getCreatedAt() != null) {
            builder.append("Создан: ").append(formatTimestamp(request.getCreatedAt())).append("\n");
        }
        builder.append("Статус: ").append(request.getStatus());
        sendText(actor, channelId, builder.toString());
    }

    private void sendOperatorMessage(Long channelId, String text) {
        if (channelId == null || channelId <= 0 || text == null || text.isBlank()) {
            return;
        }
        GroupActor actor = createActor();
        sendText(actor, channelId, text);
    }

    private String formatTimestamp(OffsetDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private void handleUnblockRequest(GroupActor actor, Long peerId, Long userId, Channel channel) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        int cooldownMinutes = botSettingsService.unblockRequestCooldownMinutes(settings, 60);
        Duration cooldown = cooldownMinutes > 0 ? Duration.ofMinutes(cooldownMinutes) : Duration.ZERO;
        Long channelId = channel != null ? channel.getId() : null;
        var decision = blacklistService.requestUnblock(userId, "", channelId, cooldown);
        if (decision.created()) {
            notifyOperatorsAboutUnblockRequest(actor, decision.request());
        }
        sendText(actor, peerId, buildUnblockResponse(decision));
    }

    private String buildUnblockResponse(BlacklistService.UnblockRequestDecision decision) {
        String requestId = decision.request() != null && decision.request().getId() != null
                ? "#" + decision.request().getId()
                : null;
        if (decision.created()) {
            return requestId == null
                    ? "Запрос на разблокировку отправлен оператору."
                    : "Запрос на разблокировку отправлен оператору. Номер заявки: " + requestId + ".";
        }
        Duration retryAfter = decision.retryAfter();
        if (retryAfter != null && !retryAfter.isZero() && !retryAfter.isNegative()) {
            String retryText = formatRetryAfter(retryAfter);
            if (requestId != null) {
                return "Запрос уже зарегистрирован под номером " + requestId
                        + ". Повторно можно отправить через " + retryText + ".";
            }
            return "Запрос уже зарегистрирован. Повторно можно отправить через " + retryText + ".";
        }
        return requestId == null
                ? "Запрос уже на рассмотрении."
                : "Запрос уже на рассмотрении. Номер заявки: " + requestId + ".";
    }

    private String formatRetryAfter(Duration retryAfter) {
        long seconds = retryAfter.getSeconds();
        if (seconds <= 0) {
            return "несколько минут";
        }
        long minutes = (seconds + 59) / 60;
        if (minutes <= 1) {
            return "менее минуты";
        }
        return minutes + " мин.";
    }

    private boolean isMyTicketsCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase().replaceAll("\\s+", " ");
        return "мои заявки".equals(normalized);
    }

    private void handleMyTickets(GroupActor actor, Long peerId, Long userId) {
        List<TicketService.TicketSummary> tickets = ticketService.findRecentTicketsForUser(userId, 10);
        sendText(actor, peerId, formatTicketsResponse(tickets));
    }

    private String formatTicketsResponse(List<TicketService.TicketSummary> tickets) {
        if (tickets.isEmpty()) {
            return "У вас пока нет заявок.";
        }
        StringBuilder builder = new StringBuilder("Ваши заявки:\n\n");
        for (TicketService.TicketSummary ticket : tickets) {
            builder.append("#").append(Optional.ofNullable(ticket.ticketId()).orElse("—")).append("\n");
            builder.append("Ресторан: ").append(formatRestaurant(ticket)).append("\n");
            builder.append("Проблема: ").append(Optional.ofNullable(ticket.problem()).filter(s -> !s.isBlank()).orElse("—"))
                    .append("\n");
            builder.append("Оценка: ").append(Optional.ofNullable(ticket.rating()).map(Object::toString).orElse("—"))
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String formatRestaurant(TicketService.TicketSummary ticket) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, ticket.business());
        addIfPresent(parts, ticket.locationType());
        addIfPresent(parts, ticket.city());
        addIfPresent(parts, ticket.locationName());
        return parts.isEmpty() ? "—" : String.join(", ", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }


    private Channel getChannel() {
        Channel channel = cachedChannel;
        if (channel != null) {
            return channel;
        }
        cachedChannel = channelService.ensurePublicIdForToken(properties.getToken(), "VK", "vk");
        return cachedChannel;
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
        ingressCoordinationService.release("vk", properties.getChannelId());
        log.info("VK long poll runner stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void destroy() {
        stop();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private record LongPollState(URI server, String key, String ts) {
        private LongPollState withTs(String nextTs) {
            return new LongPollState(server, key, nextTs != null ? nextTs : ts);
        }
    }

    private record CachedVkProfile(VkClientProfile profile, Instant expiresAt) {
    }

    private record VkClientProfile(String username, String clientName, Long userId) {
        static VkClientProfile empty(Long userId) {
            String fallback = userId != null ? "id" + userId : null;
            return new VkClientProfile(fallback, fallback, userId);
        }

        String identity() {
            if (StringUtils.hasText(username)) {
                return username.trim();
            }
            return userId != null ? userId.toString() : null;
        }

        String displayLabel() {
            if (StringUtils.hasText(clientName)) {
                if (StringUtils.hasText(username)
                        && !clientName.trim().equalsIgnoreCase(username.trim())) {
                    return clientName.trim() + " (@" + username.trim() + ")";
                }
                return clientName.trim();
            }
            if (StringUtils.hasText(username)) {
                return "@" + username.trim();
            }
            return userId != null ? String.valueOf(userId) : "client";
        }
    }

    private record HistoryEvent(Long userId, String text, String messageType, String attachment) {
    }

    private record ConversationSessionState(Long peerId,
                                            Long userId,
                                            String username,
                                            String clientName,
                                            List<QuestionFlowItemDto> flow,
                                            BotSettingsDto settings,
                                            Map<String, String> answers,
                                            List<HistoryEvent> history,
                                            List<Integer> visitedQuestionIndexes,
                                            OffsetDateTime startedAt,
                                            Map<String, String> cachedAnswers,
                                            String bootstrapProblemText,
                                            boolean firstClientResponseReceived,
                                            boolean reuseDecisionPending,
                                            int currentIndex) {
    }

    private final class ConversationSession {
        private final Long peerId;
        private final Long userId;
        private final String username;
        private final String clientName;
        private final List<QuestionFlowItemDto> flow;
        private final BotSettingsDto settings;
        private final Map<String, String> answers = new LinkedHashMap<>();
        private final List<HistoryEvent> history = new ArrayList<>();
        private final List<Integer> visitedQuestionIndexes = new ArrayList<>();
        private final Map<String, Integer> questionIndexes;
        private final OffsetDateTime startedAt;
        private Map<String, String> cachedAnswers = new LinkedHashMap<>();
        private String bootstrapProblemText;
        private String persistedRawPayload;
        private boolean firstClientResponseReceived = false;
        private boolean reuseDecisionPending = false;
        private int currentIndex = 0;

        ConversationSession(Long peerId,
                            Long userId,
                            String username,
                            String clientName,
                            List<QuestionFlowItemDto> flow,
                            BotSettingsDto settings) {
            this.peerId = peerId;
            this.userId = userId;
            this.username = username;
            this.clientName = clientName;
            this.flow = flow;
            this.settings = settings;
            this.questionIndexes = indexQuestions(flow);
            this.startedAt = OffsetDateTime.now();
            this.bootstrapProblemText = null;
        }

        ConversationSession(ConversationSessionState state) {
            this.peerId = state.peerId();
            this.userId = state.userId();
            this.username = state.username();
            this.clientName = state.clientName();
            this.flow = state.flow() != null ? new ArrayList<>(state.flow()) : List.of();
            this.settings = state.settings();
            this.questionIndexes = indexQuestions(this.flow);
            this.startedAt = state.startedAt() != null ? state.startedAt() : OffsetDateTime.now();
            if (state.answers() != null) {
                this.answers.putAll(state.answers());
            }
            if (state.history() != null) {
                this.history.addAll(state.history());
            }
            if (state.visitedQuestionIndexes() != null) {
                this.visitedQuestionIndexes.addAll(state.visitedQuestionIndexes());
            }
            this.cachedAnswers = state.cachedAnswers() != null ? new LinkedHashMap<>(state.cachedAnswers()) : new LinkedHashMap<>();
            this.bootstrapProblemText = state.bootstrapProblemText();
            this.persistedRawPayload = null;
            this.firstClientResponseReceived = state.firstClientResponseReceived();
            this.reuseDecisionPending = state.reuseDecisionPending();
            this.currentIndex = Math.max(0, state.currentIndex());
        }

        void captureBootstrapClientText(String text) {
            String normalized = ConversationProblemTextSupport.trimToNull(text);
            if (normalized == null) {
                return;
            }
            bootstrapProblemText = normalized;
            history.add(new HistoryEvent(userId, normalized, "text", null));
        }

        QuestionFlowItemDto currentQuestion() {
            if (currentIndex < 0 || currentIndex >= flow.size()) {
                return null;
            }
            return flow.get(currentIndex);
        }

        void recordAnswer(String text) {
            markClientResponseReceived();
            QuestionFlowItemDto current = currentQuestion();
            if (current == null) {
                return;
            }
            String answerKey = answerKeyFor(current);
            if (answerKey != null) {
                answers.put(answerKey, mergeAnswerWithBootstrap(answerKey, text));
            }
            history.add(new HistoryEvent(userId, text, "text", null));
            int answeredIndex = currentIndex;
            visitedQuestionIndexes.add(answeredIndex);
            currentIndex = resolveNextQuestionIndex(answeredIndex, current, text);
        }

        boolean isComplete() {
            return currentIndex >= flow.size();
        }

        void addAttachment(AttachmentService.StoredAttachment attachment, String messageType) {
            markClientResponseReceived();
            history.add(new HistoryEvent(userId, messageType, messageType, attachment.storageKey()));
        }

        List<HistoryEvent> history() {
            return history;
        }

        Map<String, String> answers() {
            return answers;
        }

        List<TicketService.TicketAttributeInput> ticketAttributes() {
            List<TicketService.TicketAttributeInput> attributes = new ArrayList<>();
            for (QuestionFlowItemDto item : flow) {
                if (item == null) {
                    continue;
                }
                String answerKey = answerKeyFor(item);
                if (answerKey == null) {
                    continue;
                }
                String answer = answers.get(answerKey);
                if (answer == null || answer.isBlank()) {
                    continue;
                }
                String valueId = resolveValueId(item, answer);
                String valueLabel = isChoiceQuestion(item) ? answer : null;
                attributes.add(TicketService.TicketAttributeInput.fromQuestion(item, valueId, valueLabel, answer));
            }
            return attributes;
        }

        Long peerId() {
            return peerId;
        }

        Long userId() {
            return userId;
        }

        String username() {
            return username;
        }

        String clientName() {
            return clientName;
        }

        OffsetDateTime startedAt() {
            return startedAt;
        }

        BotSettingsDto settings() {
            return settings;
        }

        String persistedRawPayload() {
            return persistedRawPayload;
        }

        void restorePersistedRawPayload(String rawPayload) {
            this.persistedRawPayload = rawPayload;
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
            markClientResponseReceived();
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

        void markClientResponseReceived() {
            firstClientResponseReceived = true;
        }

        boolean shouldExpireDueToMissingFirstResponse(OffsetDateTime now, int timeoutMinutes) {
            if (firstClientResponseReceived || timeoutMinutes <= 0 || now == null) {
                return false;
            }
            return !now.isBefore(startedAt.plusMinutes(timeoutMinutes));
        }

        private void applyCachedAnswers() {
            answers.clear();
            visitedQuestionIndexes.clear();
            int index = 0;
            while (index < flow.size()) {
                QuestionFlowItemDto item = flow.get(index);
                String answerKey = answerKeyFor(item);
                if (answerKey == null || !cachedAnswers.containsKey(answerKey)) {
                    currentIndex = index;
                    return;
                }
                String answer = cachedAnswers.get(answerKey);
                answers.put(answerKey, answer);
                visitedQuestionIndexes.add(index);
                int nextIndex = resolveNextQuestionIndex(index, item, answer);
                if (nextIndex <= index) {
                    currentIndex = index + 1;
                    return;
                }
                index = nextIndex;
            }
            currentIndex = flow.size();
        }

        String reusePrompt() {
            return "Использовать прошлые значения? " +
                    String.format("Бизнес: %s, Тип: %s, Город: %s, Локация: %s. Ответьте 'да' или 'нет'.",
                            cachedAnswers.getOrDefault("business", "—"),
                            cachedAnswers.getOrDefault("location_type", "—"),
                            cachedAnswers.getOrDefault("city", "—"),
                            cachedAnswers.getOrDefault("location_name", "—"));
        }

        String buildSummary(String ticketId) {
            StringBuilder builder = new StringBuilder();
            builder.append("Новая заявка #").append(ticketId)
                    .append(" от пользователя ").append(userId).append("\n");
            builder.append("Создана: ").append(startedAt).append("\n\n");
            for (QuestionFlowItemDto item : flow) {
                String answerKey = answerKeyFor(item);
                builder.append(item.getText()).append(": ")
                        .append(answerKey != null ? answers.getOrDefault(answerKey, "") : "").append("\n");
            }
            if (!history.isEmpty()) {
                builder.append("\nВложения:\n");
                history.stream()
                        .filter(h -> h.attachment() != null)
                        .forEach(h -> builder.append("- ").append(h.attachment()).append("\n"));
            }
            return builder.toString();
        }

        ConversationSessionState snapshot() {
            return new ConversationSessionState(
                    peerId,
                    userId,
                    username,
                    clientName,
                    new ArrayList<>(flow),
                    settings,
                    new LinkedHashMap<>(answers),
                    new ArrayList<>(history),
                    new ArrayList<>(visitedQuestionIndexes),
                    startedAt,
                    new LinkedHashMap<>(cachedAnswers),
                    bootstrapProblemText,
                    firstClientResponseReceived,
                    reuseDecisionPending,
                    currentIndex
            );
        }

        private String answerKeyFor(QuestionFlowItemDto item) {
            if (item == null) {
                return null;
            }
            String bindingKey = Optional.ofNullable(item.getBindingKey()).orElse("").trim();
            if (!bindingKey.isEmpty()) {
                return bindingKey;
            }
            if (item.getPreset() != null && item.getPreset().field() != null
                    && !item.getPreset().field().isBlank()) {
                return item.getPreset().field();
            }
            return item.getId();
        }

        private String resolveValueId(QuestionFlowItemDto item, String answer) {
            if (item == null || answer == null) {
                return null;
            }
            if (item.getOptions() != null) {
                for (QuestionOptionDto option : item.getOptions()) {
                    if (option == null || option.getLabel() == null) {
                        continue;
                    }
                    if (option.getLabel().equalsIgnoreCase(answer.trim())) {
                        return option.getId();
                    }
                }
            }
            return isPresetQuestion(item) ? answer : null;
        }

        private String mergeAnswerWithBootstrap(String answerKey, String answer) {
            if (!"problem".equals(answerKey)) {
                return answer;
            }
            return ConversationProblemTextSupport.mergeProblemText(bootstrapProblemText, answer);
        }

        private Map<String, Integer> indexQuestions(List<QuestionFlowItemDto> questions) {
            Map<String, Integer> indexes = new LinkedHashMap<>();
            for (int i = 0; i < questions.size(); i++) {
                QuestionFlowItemDto item = questions.get(i);
                if (item == null || item.getId() == null || item.getId().isBlank()) {
                    continue;
                }
                indexes.put(item.getId(), i);
            }
            return indexes;
        }

        private int resolveNextQuestionIndex(int sourceIndex, QuestionFlowItemDto current, String answer) {
            int sequentialIndex = sourceIndex + 1;
            if (current == null) {
                return sequentialIndex;
            }
            String routeTargetId = resolveRouteTargetId(current, answer);
            if (routeTargetId == null || routeTargetId.isBlank()) {
                return sequentialIndex;
            }
            Integer routedIndex = questionIndexes.get(routeTargetId);
            if (routedIndex == null || routedIndex <= sourceIndex) {
                return sequentialIndex;
            }
            return routedIndex;
        }

        private String resolveRouteTargetId(QuestionFlowItemDto item, String answer) {
            List<QuestionRouteDto> routes = item != null ? item.getRoutes() : null;
            if (routes == null || routes.isEmpty()) {
                return null;
            }
            String valueId = resolveValueId(item, answer);
            if (valueId == null || valueId.isBlank()) {
                return null;
            }
            for (QuestionRouteDto route : routes) {
                if (route == null) {
                    continue;
                }
                String routeValueId = Optional.ofNullable(route.getValueId()).orElse("").trim();
                if (routeValueId.equals(valueId.trim())) {
                    return Optional.ofNullable(route.getNextQuestionId()).orElse("").trim();
                }
            }
            return null;
        }
    }

    private void logIncomingMessage(Message message) {
        log.info("Incoming VK message peer={} user={} text={} attachments={}",
                message.getPeerId(),
                message.getFromId(),
                summarizeText(message.getText()),
                describeAttachments(message));
    }

    private String summarizeText(String text) {
        if (text == null) {
            return "—";
        }
        String normalized = text.replace("\n", " ").replace("\r", " ").trim();
        if (normalized.length() > MAX_LOG_TEXT_LENGTH) {
            return normalized.substring(0, MAX_LOG_TEXT_LENGTH) + "…";
        }
        return normalized.isEmpty() ? "—" : normalized;
    }

    private String describeAttachments(Message message) {
        List<MessageAttachment> attachments = Optional.ofNullable(message.getAttachments()).orElseGet(List::of);
        if (attachments.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        for (MessageAttachment attachment : attachments) {
            MessageAttachmentType type = attachment.getType();
            parts.add(type != null ? type.name().toLowerCase() : "unknown");
        }
        return String.join(",", parts);
    }

    private String extensionFrom(String ext, String fallback) {
        if (ext == null || ext.isBlank()) {
            return fallback;
        }
        return ext;
    }

    private void sleepSilently(Duration duration) {
        long millis = duration == null ? 1000L : Math.max(250L, duration.toMillis());
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private ConversationSession loadSession(Long userId) {
        return sessionStoreService.load(SESSION_PLATFORM, properties.getChannelId(), userId, ConversationSessionState.class)
                .map(this::restoreSession)
                .orElse(null);
    }

    private ConversationSession restoreSession(BotSessionStoreService.StoredBotSession<ConversationSessionState> stored) {
        if (stored == null || stored.payload() == null) {
            return null;
        }
        ConversationSession session = new ConversationSession(stored.payload());
        session.restorePersistedRawPayload(stored.rawPayload());
        return session;
    }

    private void saveSession(ConversationSession session) {
        if (session == null) {
            return;
        }
        String rawPayload = sessionStoreService.saveIfUnchanged(
                SESSION_PLATFORM,
                properties.getChannelId(),
                session.userId(),
                session.persistedRawPayload(),
                session.snapshot())
            .orElseThrow(() -> new SessionStateConflictException(
                "VK session changed concurrently for user " + session.userId()));
        session.restorePersistedRawPayload(rawPayload);
    }

    private void deleteSession(Long userId) {
        sessionStoreService.delete(SESSION_PLATFORM, properties.getChannelId(), userId);
    }

    private void deleteSession(ConversationSession session) {
        if (session == null) {
            return;
        }
        if (!StringUtils.hasText(session.persistedRawPayload())) {
            deleteSession(session.userId());
            return;
        }
        if (!sessionStoreService.deleteIfUnchanged(
                SESSION_PLATFORM,
                properties.getChannelId(),
                session.userId(),
                session.persistedRawPayload())) {
            throw new SessionStateConflictException(
                "VK session delete conflicted for user " + session.userId());
        }
        session.restorePersistedRawPayload(null);
    }
}
