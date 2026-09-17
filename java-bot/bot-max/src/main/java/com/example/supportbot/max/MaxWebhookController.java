package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.service.ActiveInboundClientMessageCommand;
import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.BlacklistService;
import com.example.supportbot.service.BotWebhookDeliveryGuardService;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.ConversationHistoryEntry;
import com.example.supportbot.service.ConversationProblemTextSupport;
import com.example.supportbot.service.ConversationTicketCreationCommand;
import com.example.supportbot.service.BotIngressCoordinationService;
import com.example.supportbot.service.BotSessionStoreService;
import com.example.supportbot.service.FeedbackService;
import com.example.supportbot.service.MessagingService;
import com.example.supportbot.service.RuntimeConfigService;
import com.example.supportbot.service.SessionStateConflictException;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/max")
public class MaxWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MaxWebhookController.class);
    private static final Duration LOCATION_CACHE_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES = 10;
    private static final String DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE =
            "Вы не ответили. Диалог был закрыт. При возникновении или актуализации вопросов создайте новое обращение.";
    private static final String BLACKLISTED_TEXT =
            "Ваш аккаунт заблокирован. Отправьте /unblock, чтобы подать запрос на разблокировку.";
    private static final String BLACKLISTED_PENDING_TEXT =
            "Ваш аккаунт заблокирован. Запрос уже на рассмотрении.";
    private static final String SESSION_PLATFORM = "max";
    private static final String EXPIRE_SESSIONS_JOB = "expire-silent-question-flow-sessions";
    private static final int SESSION_MUTATION_MAX_RETRIES = 3;

    private final MaxBotProperties properties;
    private final BlacklistService blacklistService;
    private final ChannelService channelService;
    private final TicketService ticketService;
    private final ChatHistoryService chatHistoryService;
    private final MessagingService messagingService;
    private final FeedbackService feedbackService;
    private final BotSettingsService botSettingsService;
    private final BotIngressCoordinationService ingressCoordinationService;
    private final BotWebhookDeliveryGuardService webhookDeliveryGuardService;
    private final BotSessionStoreService sessionStoreService;
    private final RuntimeConfigService runtimeConfigService;
    private final AttachmentService attachmentService;
    private final MaxApiClient maxApiClient;
    private final ObjectMapper objectMapper;

    private static String defaultFirstResponseTimeoutMessage() {
        return "Вы не ответили. Диалог был закрыт. При возникновении или актуализации вопросов создайте новое обращение.";
    }

    private final Object locationCacheMonitor = new Object();
    private volatile Map<String, Object> cachedLocationTree;
    private volatile Map<String, Object> cachedPresetDefinitions;
    private volatile Instant locationCacheUpdatedAt;

    public MaxWebhookController(MaxBotProperties properties,
                                BlacklistService blacklistService,
                                ChannelService channelService,
                                TicketService ticketService,
                                ChatHistoryService chatHistoryService,
                                MessagingService messagingService,
                                FeedbackService feedbackService,
                                BotSettingsService botSettingsService,
                                BotIngressCoordinationService ingressCoordinationService,
                                BotWebhookDeliveryGuardService webhookDeliveryGuardService,
                                BotSessionStoreService sessionStoreService,
                                RuntimeConfigService runtimeConfigService,
                                AttachmentService attachmentService,
                                MaxApiClient maxApiClient,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.channelService = channelService;
        this.ticketService = ticketService;
        this.chatHistoryService = chatHistoryService;
        this.messagingService = messagingService;
        this.feedbackService = feedbackService;
        this.botSettingsService = botSettingsService;
        this.ingressCoordinationService = ingressCoordinationService;
        this.webhookDeliveryGuardService = webhookDeliveryGuardService;
        this.sessionStoreService = sessionStoreService;
        this.runtimeConfigService = runtimeConfigService;
        this.attachmentService = attachmentService;
        this.maxApiClient = maxApiClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleUpdate(
        @RequestBody JsonNode update,
        @RequestHeader(value = "X-Max-Bot-Api-Secret", required = false) String secret
    ) {
        return handleUpdate(update, secret, 0);
    }

    private ResponseEntity<Map<String, Object>> handleUpdate(JsonNode update, String secret, int attempt) {
        try {
            return handleUpdateOnce(update, secret);
        } catch (SessionStateConflictException ex) {
            Long userId = MaxWebhookInputSupport.asLong(update.path("message").path("sender").path("user_id"));
            if (attempt + 1 >= SESSION_MUTATION_MAX_RETRIES) {
                log.warn("MAX session mutation conflict for user {} after {} attempt(s): {}",
                        userId,
                        attempt + 1,
                        ex.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("ok", false, "error", "session-conflict", "retryable", true));
            }
            log.info("Retrying MAX session mutation for user {} after optimistic conflict (attempt {}/{})",
                    userId,
                    attempt + 2,
                    SESSION_MUTATION_MAX_RETRIES);
            return handleUpdate(update, secret, attempt + 1);
        }
    }

    private ResponseEntity<Map<String, Object>> handleUpdateOnce(JsonNode update, String secret) {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", "max-bot-disabled"));
        }
        if (!MaxWebhookInputSupport.isSecretValid(properties.getWebhookSecret(), secret)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "invalid-secret"));
        }
        String updateType = MaxWebhookInputSupport.text(update, "update_type");
        if (!"message_created".equals(updateType)) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", updateType));
        }
        if (!ingressCoordinationService.tryAcquireOrRenew(SESSION_PLATFORM, properties.getChannelId())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "error", "not-owner", "retryable", true));
        }
        BotWebhookDeliveryGuardService.DeliveryClaim claim = webhookDeliveryGuardService.tryClaim(
                SESSION_PLATFORM,
                properties.getChannelId(),
                MaxDeliveryIdentitySupport.buildDeliveryKey(update));
        if (claim.alreadyProcessed()) {
            return ResponseEntity.ok(Map.of("ok", true, "duplicate", true));
        }
        if (claim.inFlight()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "error", "delivery-inflight", "retryable", true));
        }

        JsonNode message = update.path("message");
        Long userId = MaxWebhookInputSupport.asLong(message.path("sender").path("user_id"));
        Long chatId = MaxWebhookInputSupport.asLong(message.path("recipient").path("chat_id"));
        Long providerMessageId = MaxDeliveryIdentitySupport.resolveProviderMessageId(update, message);
        MaxInboundPayloadSupport.ClientProfile clientProfile = MaxInboundPayloadSupport.resolveClientProfile(message, userId);
        MaxInboundPayloadSupport.InboundPayload inboundPayload = MaxInboundPayloadSupport.resolveInboundPayload(message, clientProfile);
        String text = inboundPayload.text();
        List<MaxInboundPayloadSupport.IncomingAttachment> attachments = inboundPayload.attachments();
        boolean hasAttachments = !attachments.isEmpty();
        try {
            if (userId == null || (text.isBlank() && !hasAttachments)) {
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "ignored", "missing-user-or-text")));
            }

            Channel channel = getChannel();
            BlacklistService.ResolvedBlacklistStatus resolvedBlacklist = blacklistService.resolveStatus(
                    userId,
                    clientProfile.identity(),
                    clientProfile.username(),
                    chatId != null ? String.valueOf(chatId) : null
            );
            BlacklistService.BlacklistStatus status = resolvedBlacklist.status();
            if (status.blacklisted()) {
                log.info("Blocked message from blacklisted MAX user {} (matched key: {})",
                        userId,
                        resolvedBlacklist.matchedUserId());
                if (MaxCommandSupport.isUnblockCommand(text)) {
                    handleUnblockRequest(channel, userId);
                    return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "unblock_requested", true)));
                }
                handleBlacklistedUser(channel, userId, status);
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "blocked", true)));
            }

        MaxConversationSession session = loadSession(userId);
        if (MaxCommandSupport.isStartCommand(text)) {
            if (session != null) {
                deleteSession(session);
            }
            session = startSession(userId, chatId, clientProfile.username(), clientProfile.clientName(), channel);
            saveSession(session);
            promptCurrentQuestion(channel, session);
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true)));
        }

        if (MaxCommandSupport.isCancelCommand(text)) {
            if (session != null) {
                deleteSession(session);
            } else {
                deleteSession(userId);
            }
            messagingService.sendToUser(channel, userId, "Текущая заявка отменена.");
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "cancelled", true)));
        }

        Optional<TicketActive> active = ticketService.findActiveTicketForUser(
                userId,
                clientProfile.identity(),
                channel != null ? channel.getId() : null
        );
        if (active.isPresent() && isStaleActiveTicket(active.get().getTicketId())) {
            ticketService.clearTicketActivity(active.get().getTicketId());
            active = Optional.empty();
        }
        if (active.isPresent()) {
            if (session != null) {
                deleteSession(session);
            }
            String ticketId = active.get().getTicketId();
            String clientText = !text.isBlank() ? text : "[вложение от клиента]";
            String messageType = hasAttachments ? MaxInboundPayloadSupport.normalizeAttachmentType(attachments.get(0).type()) : "text";
            StoredIncomingAttachment storedAttachment = hasAttachments
                    ? storeIncomingAttachment(channel, attachments.get(0))
                    : null;
            String attachmentRef = storedAttachment != null ? storedAttachment.storageKey() : null;
            String attachmentName = storedAttachment != null ? storedAttachment.originalName() : null;
            ticketService.recordActiveClientMessage(new ActiveInboundClientMessageCommand(
                userId,
                clientProfile.identity(),
                clientProfile.username(),
                clientProfile.clientName(),
                channel,
                ticketId,
                clientText,
                messageType,
                attachmentRef,
                attachmentName,
                providerMessageId,
                MaxDeliveryIdentitySupport.resolveReplyToProviderMessageId(message),
                inboundPayload.forwardedFrom(),
                OffsetDateTime.now()
            ));
            notifyOperatorsAboutActiveMessage(channel, ticketId, clientProfile, clientText, messageType, attachmentRef, attachments.size());
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "ticket_id", ticketId)));
        }

        if (session == null) {
            ResponseEntity<Map<String, Object>> feedbackResponse = tryHandleFeedback(channel, userId, text);
            if (feedbackResponse != null) {
                return completeDelivery(claim, feedbackResponse);
            }
            session = startSession(userId, chatId, clientProfile.username(), clientProfile.clientName(), channel);
            if (shouldCaptureBootstrapProblemText(text)) {
                session.captureBootstrapClientText(text);
            }
            saveSession(session);
            promptCurrentQuestion(channel, session);
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "session_started", true)));
        }

        session.markClientResponseReceived();
        saveSession(session);

        if (session.awaitingReuseDecision()) {
            if (!session.consumeReuseDecision(text)) {
                messagingService.sendToUser(channel, userId,
                        "Ответьте 'да', чтобы использовать прошлые значения, или 'нет', чтобы заполнить заново.");
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "awaiting_reuse_decision", true)));
            }
            if (session.isComplete()) {
                TicketService.TicketCreationResult created = finalizeConversation(channel, session);
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "ticket_id", created.ticketId())));
            }
            saveSession(session);
            promptCurrentQuestion(channel, session);
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "question_prompted", true)));
        }

        if (MaxQuestionInputSupport.BACK_BUTTON.equalsIgnoreCase(Optional.ofNullable(text).orElse("").trim())) {
            if (session.stepBack()) {
                saveSession(session);
                promptCurrentQuestion(channel, session);
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "stepped_back", true)));
            }
            promptCurrentQuestion(channel, session);
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "already_at_first_question", true)));
        }

        QuestionFlowItemDto current = session.currentQuestion();
        String resolvedAnswer = text;
        if (MaxQuestionInputSupport.isOptionalFreeQuestion(current) && MaxQuestionInputSupport.SKIP_BUTTON.equalsIgnoreCase(String.valueOf(text).trim())) {
            resolvedAnswer = "";
        } else if (MaxQuestionInputSupport.isChoiceQuestion(current)) {
            List<String> options = resolveQuestionOptions(current, session.answers());
            if (options.isEmpty()) {
                messagingService.sendToUser(channel, userId,
                        "Сейчас нет доступных вариантов для выбора. Обратитесь к администратору.");
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "missing_options", true)));
            }
            resolvedAnswer = MaxQuestionInputSupport.resolveChoiceAnswer(resolvedAnswer, options);
            if (!options.contains(resolvedAnswer)) {
                messagingService.sendToUser(channel, userId,
                        "Введите один из вариантов: " + String.join(", ", options));
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "invalid_option", true)));
            }
        }

        if (resolvedAnswer.isBlank() && !MaxQuestionInputSupport.isOptionalFreeQuestion(current)) {
            promptCurrentQuestion(channel, session);
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "blank_answer", true)));
        }

        session.recordAnswer(resolvedAnswer);
        if (session.isComplete()) {
            TicketService.TicketCreationResult created = finalizeConversation(channel, session);
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "ticket_id", created.ticketId())));
        }

        saveSession(session);
        promptCurrentQuestion(channel, session);
        return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "question_prompted", true)));
        } catch (RuntimeException ex) {
            webhookDeliveryGuardService.release(claim);
            throw ex;
        }
    }

    private ResponseEntity<Map<String, Object>> tryHandleFeedback(Channel channel, Long userId, String text) {
        if (userId == null) {
            return null;
        }
        String normalized = MaxFeedbackInputSupport.normalizeNumericRating(text);
        if (normalized == null) {
            return null;
        }
        Optional<PendingFeedbackRequest> pendingOpt = feedbackService.findActiveRequest(userId, channel);
        if (pendingOpt.isEmpty()) {
            return null;
        }

        log.info("Processing MAX feedback rating {} from user {}", normalized, userId);
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        if (!MaxFeedbackInputSupport.isAllowedRating(
                normalized,
                botSettingsService.ratingAllowedValues(settings)
        )) {
            int scale = botSettingsService.ratingScale(settings, 5);
            messagingService.sendToUser(
                    channel,
                    userId,
                    MaxFeedbackInputSupport.buildInvalidRatingPrompt(scale)
            );
            return ResponseEntity.ok(Map.of("ok", true, "awaiting_valid_rating", true));
        }

        int rating = MaxFeedbackInputSupport.parseRating(normalized);
        feedbackService.storeFeedback(pendingOpt.get(), rating);
        String response = botSettingsService.ratingResponseFor(settings, rating).orElse("Спасибо за оценку!");
        messagingService.sendToUser(channel, userId, response);
        return ResponseEntity.ok(Map.of("ok", true, "feedback_saved", true, "rating", rating));
    }

    @Scheduled(fixedDelay = 60000L)
    public void expireSilentQuestionFlowSessions() {
        if (!ingressCoordinationService.tryAcquireOrRenewJob(SESSION_PLATFORM, properties.getChannelId(), EXPIRE_SESSIONS_JOB)) {
            return;
        }
        Channel channel;
        try {
            channel = getChannel();
        } catch (IllegalStateException ex) {
            log.warn("Skipping MAX silent-session cleanup because channel resolution is temporarily unavailable: {}", ex.getMessage());
            return;
        }
        if (channel == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        sessionStoreService.loadAll(SESSION_PLATFORM, properties.getChannelId(), MaxConversationSession.State.class).forEach(storedSession -> {
            MaxConversationSession session = restoreSession(storedSession);
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
            messagingService.sendToUser(
                    channel,
                    session.userId(),
                    botSettingsService.firstResponseTimeoutMessage(
                            session.settings(),
                            defaultFirstResponseTimeoutMessage()
                    )
            );
            log.info("Expired MAX question-flow session for user {} after {} minutes without first response",
                    storedSession.userId(),
                    timeoutMinutes);
        });
    }

    private Channel getChannel() {
        return channelService.resolveConfiguredChannel(properties.getChannelId(), properties.getToken(), "MAX", "max");
    }

    private void handleUnblockRequest(Channel channel, Long userId) {
        if (channel == null || userId == null) {
            return;
        }
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        int cooldownMinutes = botSettingsService.unblockRequestCooldownMinutes(settings, 60);
        Duration cooldown = cooldownMinutes > 0 ? Duration.ofMinutes(cooldownMinutes) : Duration.ZERO;
        Long channelId = channel.getId();
        BlacklistService.UnblockRequestDecision decision =
                blacklistService.requestUnblock(userId, "", channelId, cooldown);
        if (decision.created()) {
            notifyOperatorsAboutUnblockRequest(channel, decision.request());
        }
        messagingService.sendToUser(
                channel,
                userId,
                MaxUnblockMessageSupport.buildClientResponse(decision.request(), decision.created(), decision.retryAfter())
        );
    }

    private void handleBlacklistedUser(Channel channel, Long userId, BlacklistService.BlacklistStatus status) {
        if (channel == null || userId == null || status == null) {
            return;
        }
        messagingService.sendToUser(channel, userId,
                status.unblockRequested() ? BLACKLISTED_PENDING_TEXT : BLACKLISTED_TEXT);
    }

    private void notifyOperatorsAboutUnblockRequest(Channel channel,
                                                    com.example.supportbot.entity.ClientUnblockRequest request) {
        if (channel == null || request == null) {
            return;
        }
        messagingService.sendToSupportChat(channel, MaxUnblockMessageSupport.buildOperatorRequestMessage(request));
    }

    private boolean isStaleActiveTicket(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return true;
        }
        return ticketService.findByTicketId(ticketId)
                .map(ticket -> {
                    String status = ticket.status();
                    if (status == null) {
                        return false;
                    }
                    String normalized = status.trim().toLowerCase();
                    return "resolved".equals(normalized) || "closed".equals(normalized);
                })
                .orElse(true);
    }

    private MaxConversationSession startSession(Long userId, Long chatId, String username, String clientName, Channel channel) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        List<QuestionFlowItemDto> flow = MaxIncidentFlowSupport.normalize(botSettingsService.questionFlow(settings));

        MaxConversationSession session = new MaxConversationSession(userId, chatId, username, clientName, flow, settings);
        ticketService.findLastMessage(userId)
                .ifPresent(last -> session.enableReusePrompt(Map.of(
                        "business", Optional.ofNullable(last.getBusiness()).orElse(""),
                        "location_type", Optional.ofNullable(last.getLocationType()).orElse(""),
                        "city", Optional.ofNullable(last.getCity()).orElse(""),
                        "location_name", Optional.ofNullable(last.getLocationName()).orElse("")
                )));
        return session;
    }

    private boolean shouldCaptureBootstrapProblemText(String text) {
        String normalized = ConversationProblemTextSupport.trimToNull(text);
        return normalized != null && !normalized.startsWith("/");
    }

    private void promptCurrentQuestion(Channel channel, MaxConversationSession session) {
        if (session.awaitingReuseDecision()) {
            messagingService.sendToUser(channel, session.userId(), session.reusePrompt());
            return;
        }
        QuestionFlowItemDto current = session.currentQuestion();
        if (current == null) {
            return;
        }
        List<String> options = MaxQuestionInputSupport.isChoiceQuestion(current) ? resolveQuestionOptions(current, session.answers()) : List.of();
        messagingService.sendToUser(channel, session.userId(), MaxQuestionInputSupport.buildQuestionPromptText(current, options, session.canGoBack()));
    }

    private TicketService.TicketCreationResult finalizeConversation(Channel channel, MaxConversationSession session) {
        deleteSession(session);
        TicketService.TicketCreationResult created = ticketService.createConversationTicket(
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
                                        null,
                                        null,
                                        null,
                                        session.startedAt()
                                ))
                                .toList(),
                        channel,
                        session.startedAt()
                )
        );
        Optional<String> requestNumber = ticketService.awaitClientTicketNumber(created);
        messagingService.sendToUser(
                channel,
                session.userId(),
                requestNumber.map(number -> "Заявка создана. Номер: " + number)
                        .orElse("Заявка принята и передана оператору. Номер появится после регистрации.")
        );
        messagingService.sendToSupportChat(channel, session.buildSummary(created.ticketId()));
        return created;
    }

    private ResponseEntity<Map<String, Object>> completeDelivery(BotWebhookDeliveryGuardService.DeliveryClaim claim,
                                                                 ResponseEntity<Map<String, Object>> response) {
        webhookDeliveryGuardService.markProcessed(claim);
        return response;
    }

    private List<String> resolveQuestionOptions(QuestionFlowItemDto current, Map<String, String> answers) {
        if (MaxQuestionInputSupport.isSelectQuestion(current)) {
            return MaxQuestionOptionSupport.resolveSelectOptions(current);
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
            options = MaxQuestionOptionSupport.resolveLocationOptions(field, answers, tree);
            if (options.isEmpty()) {
                options = MaxQuestionOptionSupport.resolvePresetDefinitionOptions(group, field, presetDefinitions());
            }
        } else {
            options = MaxQuestionOptionSupport.resolvePresetDefinitionOptions(group, field, presetDefinitions());
        }
        return MaxQuestionOptionSupport.applyExcludedOptions(options, current.getExcludedOptions());
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
            Map<String, Object> resolvedTree = runtimeConfigService.locationTree(getChannel());
            Map<String, Object> baseDefinitions = runtimeConfigService.basePresetDefinitions(getChannel());
            Map<String, Object> mergedDefinitions = botSettingsService.buildLocationPresets(resolvedTree, baseDefinitions);
            cachedLocationTree = resolvedTree != null ? resolvedTree : new LinkedHashMap<>();
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

    private void notifyOperatorsAboutActiveMessage(Channel channel,
                                                   String ticketId,
                                                   MaxInboundPayloadSupport.ClientProfile clientProfile,
                                                   String text,
                                                   String messageType,
                                                   String attachmentRef,
                                                   int attachmentCount) {
        if (channel == null || ticketId == null || ticketId.isBlank()) {
            return;
        }
        messagingService.sendToSupportChat(
                channel,
                MaxActiveMessageSupport.buildOperatorMessage(
                        ticketId,
                        clientProfile.displayLabel(),
                        text,
                        messageType,
                        attachmentRef,
                        attachmentCount
                )
        );
    }

    private StoredIncomingAttachment storeIncomingAttachment(Channel channel, MaxInboundPayloadSupport.IncomingAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        String fallbackRef = attachment.urlOrName();
        if (!StringUtils.hasText(attachment.url())) {
            return StringUtils.hasText(fallbackRef)
                    ? new StoredIncomingAttachment(fallbackRef, attachment.name())
                    : null;
        }
        try (MaxApiClient.DownloadedAttachment downloaded = maxApiClient.downloadAttachment(attachment.url())) {
            String originalName = MaxAttachmentMetadataSupport.firstNonBlank(attachment.name(), downloaded.filename());
            String extension = MaxAttachmentMetadataSupport.resolveAttachmentExtension(originalName, downloaded.contentType(), attachment.type());
            String channelPublicId = MaxAttachmentMetadataSupport.firstNonBlank(
                    channel != null ? channel.getPublicId() : null,
                    channel != null && channel.getId() != null ? "max-" + channel.getId() : "max"
            );
            AttachmentService.StoredAttachment stored = attachmentService.store(channelPublicId, extension, downloaded.body());
            return new StoredIncomingAttachment(stored.storageKey(), originalName);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to persist incoming MAX attachment for channel {}: {}",
                    channel != null ? channel.getId() : null,
                    ex.getMessage());
            return StringUtils.hasText(fallbackRef)
                    ? new StoredIncomingAttachment(fallbackRef, attachment.name())
                    : null;
        }
    }

    private record StoredIncomingAttachment(String storageKey, String originalName) {
    }

    private MaxConversationSession loadSession(Long userId) {
        return sessionStoreService.load(SESSION_PLATFORM, properties.getChannelId(), userId, MaxConversationSession.State.class)
                .map(this::restoreSession)
                .orElse(null);
    }

    private MaxConversationSession restoreSession(BotSessionStoreService.StoredBotSession<MaxConversationSession.State> stored) {
        if (stored == null || stored.payload() == null) {
            return null;
        }
        MaxConversationSession session = new MaxConversationSession(stored.payload());
        session.restorePersistedRawPayload(stored.rawPayload());
        return session;
    }

    private void saveSession(MaxConversationSession session) {
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
                    "MAX session changed concurrently for user " + session.userId()));
        session.restorePersistedRawPayload(rawPayload);
    }

    private void deleteSession(Long userId) {
        sessionStoreService.delete(SESSION_PLATFORM, properties.getChannelId(), userId);
    }

    private void deleteSession(MaxConversationSession session) {
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
                    "MAX session delete conflicted for user " + session.userId());
        }
        session.restorePersistedRawPayload(null);
    }
}
