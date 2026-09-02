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
import com.example.supportbot.settings.dto.PresetReference;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionOptionDto;
import com.example.supportbot.settings.dto.QuestionRouteDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private static final List<String> CORE_LOCATION_FIELDS = List.of("business", "location_type", "city", "location_name");
    private static final Duration LOCATION_CACHE_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES = 10;
    private static final String DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE =
            "Вы не ответили. Диалог был закрыт. При возникновении или актуализации вопросов создайте новое обращение.";
    private static final String SKIP_BUTTON = "Пропустить";
    private static final String BACK_BUTTON = "Назад";
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
            Long userId = asLong(update.path("message").path("sender").path("user_id"));
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
        if (!secretValid(secret)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "invalid-secret"));
        }
        String updateType = text(update, "update_type");
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
                buildDeliveryKey(update));
        if (claim.alreadyProcessed()) {
            return ResponseEntity.ok(Map.of("ok", true, "duplicate", true));
        }
        if (claim.inFlight()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "error", "delivery-inflight", "retryable", true));
        }

        JsonNode message = update.path("message");
        Long userId = asLong(message.path("sender").path("user_id"));
        Long chatId = asLong(message.path("recipient").path("chat_id"));
        Long providerMessageId = resolveProviderMessageId(update, message);
        MaxClientProfile clientProfile = resolveClientProfile(message, userId);
        MaxInboundPayload inboundPayload = resolveInboundPayload(message, clientProfile);
        String text = inboundPayload.text();
        List<MaxIncomingAttachment> attachments = inboundPayload.attachments();
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
                if ("/unblock".equalsIgnoreCase(text)) {
                    handleUnblockRequest(channel, userId);
                    return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "unblock_requested", true)));
                }
                handleBlacklistedUser(channel, userId, status);
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "blocked", true)));
            }

        ConversationSession session = loadSession(userId);
        if ("/start".equalsIgnoreCase(text)) {
            if (session != null) {
                deleteSession(session);
            }
            session = startSession(userId, chatId, clientProfile.username(), clientProfile.clientName(), channel);
            saveSession(session);
            promptCurrentQuestion(channel, session);
            return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true)));
        }

        if (isCancelCommand(text)) {
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
            String messageType = hasAttachments ? normalizeAttachmentType(attachments.get(0).type()) : "text";
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
                resolveReplyToProviderMessageId(message),
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

        if (BACK_BUTTON.equalsIgnoreCase(Optional.ofNullable(text).orElse("").trim())) {
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
        if (isOptionalFreeQuestion(current) && SKIP_BUTTON.equalsIgnoreCase(String.valueOf(text).trim())) {
            resolvedAnswer = "";
        } else if (isChoiceQuestion(current)) {
            List<String> options = resolveQuestionOptions(current, session.answers());
            if (options.isEmpty()) {
                messagingService.sendToUser(channel, userId,
                        "Сейчас нет доступных вариантов для выбора. Обратитесь к администратору.");
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "missing_options", true)));
            }
            resolvedAnswer = resolveChoiceAnswer(resolvedAnswer, options);
            if (!options.contains(resolvedAnswer)) {
                messagingService.sendToUser(channel, userId,
                        "Введите один из вариантов: " + String.join(", ", options));
                return completeDelivery(claim, ResponseEntity.ok(Map.of("ok", true, "invalid_option", true)));
            }
        }

        if (resolvedAnswer.isBlank() && !isOptionalFreeQuestion(current)) {
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
        if (userId == null || text == null) {
            return null;
        }
        String normalized = text.trim();
        if (!normalized.matches("\\d+")) {
            return null;
        }
        Optional<PendingFeedbackRequest> pendingOpt = feedbackService.findActiveRequest(userId, channel);
        if (pendingOpt.isEmpty()) {
            return null;
        }

        log.info("Processing MAX feedback rating {} from user {}", normalized, userId);
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        Set<String> allowed = botSettingsService.ratingAllowedValues(settings);
        if (!allowed.contains(normalized)) {
            int scale = botSettingsService.ratingScale(settings, 5);
            messagingService.sendToUser(channel, userId, "Отправьте число от 1 до " + scale);
            return ResponseEntity.ok(Map.of("ok", true, "awaiting_valid_rating", true));
        }

        int rating = Integer.parseInt(normalized);
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
        messagingService.sendToUser(channel, userId, buildUnblockResponse(decision));
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
        messagingService.sendToSupportChat(channel, builder.toString());
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
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            return "несколько минут";
        }
        long seconds = retryAfter.getSeconds();
        if (seconds < 60) {
            return "менее минуты";
        }
        long minutes = (seconds + 59) / 60;
        if (minutes <= 1) {
            return "менее минуты";
        }
        return minutes + " мин.";
    }

    private String formatTimestamp(OffsetDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
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
        List<QuestionFlowItemDto> source = new ArrayList<>(botSettingsService.questionFlow(settings));
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
            QuestionFlowItemDto question = new QuestionFlowItemDto(
                    field,
                    "preset",
                    (text == null || text.isBlank()) ? defaultPrompt(field) : text,
                    order++,
                    new PresetReference("locations", field),
                    excluded
            );
            if (existing != null) {
                question.setBindingKey(existing.getBindingKey());
                question.setIncludeInDashboard(existing.getIncludeInDashboard());
                question.setRoutes(existing.getRoutes());
            }
            normalized.add(question);
        }

        normalized.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", order, null, List.of()));
        return normalized;
    }

    private boolean shouldCaptureBootstrapProblemText(String text) {
        String normalized = ConversationProblemTextSupport.trimToNull(text);
        return normalized != null && !normalized.startsWith("/");
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
        List<String> options = isChoiceQuestion(current) ? resolveQuestionOptions(current, session.answers()) : List.of();
        messagingService.sendToUser(channel, session.userId(), buildQuestionPromptText(current, options, session.canGoBack()));
    }

    private TicketService.TicketCreationResult finalizeConversation(Channel channel, ConversationSession session) {
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

    private String buildQuestionPromptText(QuestionFlowItemDto current, List<String> options, boolean includeBack) {
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
        if (includeBack) {
            text.append("\n\nЧтобы вернуться к предыдущему вопросу, отправьте \"").append(BACK_BUTTON).append("\".");
        }
        return text.toString();
    }

    private String resolveChoiceAnswer(String rawAnswer, List<String> options) {
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

    /**
     * MAX places the original content under {@code link} for forwarded messages
     * and may leave the outer body null. Keep the outer sender as the client
     * while storing the original author separately for the operator timeline.
     */
    private MaxInboundPayload resolveInboundPayload(JsonNode message, MaxClientProfile clientProfile) {
        String directText = extractMessageText(message);
        List<MaxIncomingAttachment> attachments = extractIncomingAttachments(message);
        JsonNode forwardedMessage = resolveForwardedMessage(message);
        boolean forwarded = forwardedMessage != null;

        if (directText.isBlank() && forwarded) {
            directText = extractMessageText(forwardedMessage);
        }
        if (attachments.isEmpty() && forwarded) {
            attachments = extractIncomingAttachments(forwardedMessage);
        }

        return new MaxInboundPayload(
                directText,
                attachments,
                forwarded ? resolveForwardedFrom(message, forwardedMessage, clientProfile) : null
        );
    }

    private JsonNode resolveForwardedMessage(JsonNode message) {
        if (message == null || message.isNull() || message.isMissingNode()) {
            return null;
        }
        JsonNode link = message.path("link");
        if (link.isMissingNode() || link.isNull() || !isForwardLink(link)) {
            return null;
        }
        for (String field : List.of("message", "linked_message", "forwarded_message", "forward", "source")) {
            JsonNode candidate = link.path(field);
            if (candidate.isObject()) {
                return candidate;
            }
        }
        return link.isObject() ? link : null;
    }

    private boolean isForwardLink(JsonNode link) {
        String type = firstNonBlank(text(link, "type"), text(link, "link_type"));
        return type != null && ("forward".equalsIgnoreCase(type) || "forwarded".equalsIgnoreCase(type));
    }

    private String resolveForwardedFrom(JsonNode message,
                                        JsonNode forwardedMessage,
                                        MaxClientProfile outerClient) {
        List<JsonNode> authorCandidates = new ArrayList<>();
        collectForwardedAuthorCandidates(authorCandidates, forwardedMessage);
        JsonNode link = message != null ? message.path("link") : null;
        if (link != forwardedMessage) {
            collectForwardedAuthorCandidates(authorCandidates, link);
        }
        for (JsonNode candidate : authorCandidates) {
            MaxClientProfile profile = resolveClientProfileFromSender(candidate);
            if (isSameClient(profile, outerClient)) {
                continue;
            }
            String label = profile.displayLabel();
            if (label != null && !label.isBlank() && !label.startsWith("MAX user ")) {
                return label;
            }
        }
        return null;
    }

    private void collectForwardedAuthorCandidates(List<JsonNode> candidates, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        for (String field : List.of(
                "author", "original_author", "original_sender", "forwarded_from", "from", "user", "sender", "owner"
        )) {
            JsonNode candidate = node.path(field);
            if (candidate.isObject()) {
                candidates.add(candidate);
            }
        }
    }

    private boolean isSameClient(MaxClientProfile candidate, MaxClientProfile outerClient) {
        if (candidate == null || outerClient == null) {
            return false;
        }
        if (candidate.userId() != null && outerClient.userId() != null) {
            return candidate.userId().equals(outerClient.userId());
        }
        return candidate.identity() != null && candidate.identity().equalsIgnoreCase(outerClient.identity());
    }

    private MaxClientProfile resolveClientProfile(JsonNode message, Long userId) {
        JsonNode sender = message != null ? message.path("sender") : null;
        MaxClientProfile profile = resolveClientProfileFromSender(sender);
        String username = profile.username();
        String clientName = profile.clientName();
        if ((username == null || username.isBlank()) && userId != null) {
            username = "max_" + userId;
        }
        if ((clientName == null || clientName.isBlank()) && userId != null) {
            clientName = "MAX user " + userId;
        }
        return new MaxClientProfile(trimOrNull(username), trimOrNull(clientName), userId);
    }

    private MaxClientProfile resolveClientProfileFromSender(JsonNode sender) {
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
        return new MaxClientProfile(trimOrNull(username), trimOrNull(clientName), asLong(sender != null ? sender.path("user_id") : null));
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

    private StoredIncomingAttachment storeIncomingAttachment(Channel channel, MaxIncomingAttachment attachment) {
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
            String originalName = firstNonBlank(attachment.name(), downloaded.filename());
            String extension = resolveAttachmentExtension(originalName, downloaded.contentType(), attachment.type());
            String channelPublicId = firstNonBlank(
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

    private String resolveAttachmentExtension(String filename, String contentType, String attachmentType) {
        if (StringUtils.hasText(filename)) {
            String normalized = filename.trim();
            int dot = normalized.lastIndexOf('.');
            if (dot >= 0 && dot < normalized.length() - 1) {
                String extension = normalized.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
                if (!extension.isBlank() && extension.length() <= 10) {
                    return extension.toLowerCase();
                }
            }
        }
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase();
        if (normalizedContentType.contains("jpeg")) return "jpg";
        if (normalizedContentType.contains("png")) return "png";
        if (normalizedContentType.contains("gif")) return "gif";
        if (normalizedContentType.contains("webp")) return "webp";
        if (normalizedContentType.contains("mp4")) return "mp4";
        if (normalizedContentType.contains("ogg")) return "ogg";
        if (normalizedContentType.contains("mpeg")) return "mp3";
        if (normalizedContentType.contains("pdf")) return "pdf";
        String normalizedType = attachmentType == null ? "" : attachmentType.toLowerCase();
        if (normalizedType.contains("image") || normalizedType.contains("photo")) return "jpg";
        if (normalizedType.contains("video")) return "mp4";
        if (normalizedType.contains("audio")) return "ogg";
        return "bin";
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

    private String buildDeliveryKey(JsonNode update) {
        String updateId = firstNonBlank(
                text(update, "update_id"),
                text(update, "event_id")
        );
        if (updateId != null) {
            return "update:" + updateId;
        }
        JsonNode message = update.path("message");
        String messageId = firstNonBlank(
                text(message, "message_id"),
                text(message.path("body"), "mid"),
                text(message.path("body"), "message_id")
        );
        if (messageId != null) {
            return "message:" + messageId;
        }
        String senderId = text(message.path("sender"), "user_id");
        String chatId = firstNonBlank(
                text(message.path("recipient"), "chat_id"),
                text(message.path("recipient"), "user_id")
        );
        String createdAt = firstNonBlank(
                text(message, "timestamp"),
                text(message.path("body"), "created_at"),
                text(message.path("body"), "timestamp")
        );
        if (StringUtils.hasText(senderId) || StringUtils.hasText(chatId) || StringUtils.hasText(createdAt)) {
            return "message_created|sender=" + senderId + "|chat=" + chatId + "|created_at=" + createdAt;
        }
        return update != null ? update.toString() : "missing-update";
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

    private Long resolveProviderMessageId(JsonNode update, JsonNode message) {
        Long numericId = asLong(message.path("message_id"));
        if (numericId == null) {
            numericId = asLong(message.path("body").path("mid"));
        }
        if (numericId != null) {
            return numericId;
        }
        UUID stableId = UUID.nameUUIDFromBytes(buildDeliveryKey(update).getBytes(StandardCharsets.UTF_8));
        long value = stableId.getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0L ? 1L : value;
    }

    private Long resolveReplyToProviderMessageId(JsonNode message) {
        JsonNode link = message != null ? message.path("link") : null;
        if (link == null || link.isMissingNode() || link.isNull()
                || !"reply".equalsIgnoreCase(text(link, "type"))) {
            return null;
        }

        JsonNode linkedMessage = link.path("message");
        for (JsonNode candidate : List.of(
                link.path("message_id"),
                link.path("mid"),
                linkedMessage.path("message_id"),
                linkedMessage.path("mid"),
                linkedMessage.path("body").path("message_id"),
                linkedMessage.path("body").path("mid"))) {
            Long messageId = asLong(candidate);
            if (messageId != null) {
                return messageId;
            }
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

    private record MaxInboundPayload(String text,
                                     List<MaxIncomingAttachment> attachments,
                                     String forwardedFrom) {
    }

    private record StoredIncomingAttachment(String storageKey, String originalName) {
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

    private record ConversationSessionState(Long userId,
                                            Long chatId,
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
        private final Long userId;
        private final Long chatId;
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
            this.questionIndexes = indexQuestions(flow);
            this.startedAt = OffsetDateTime.now();
            this.bootstrapProblemText = null;
            this.persistedRawPayload = null;
        }

        ConversationSession(ConversationSessionState state) {
            this.userId = state.userId();
            this.chatId = state.chatId();
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
            history.add(new HistoryEvent(userId, normalized, "text"));
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
            history.add(new HistoryEvent(userId, text, "text"));
            int answeredIndex = currentIndex;
            visitedQuestionIndexes.add(answeredIndex);
            currentIndex = resolveNextQuestionIndex(answeredIndex, current, text);
        }

        boolean isComplete() {
            return currentIndex >= flow.size();
        }

        boolean canGoBack() {
            return !visitedQuestionIndexes.isEmpty();
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

        boolean stepBack() {
            if (visitedQuestionIndexes.isEmpty()) {
                return false;
            }
            currentIndex = visitedQuestionIndexes.remove(visitedQuestionIndexes.size() - 1);
            QuestionFlowItemDto previous = currentQuestion();
            String answerKey = answerKeyFor(previous);
            if (answerKey != null) {
                answers.remove(answerKey);
            }
            return true;
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

        ConversationSessionState snapshot() {
            return new ConversationSessionState(
                    userId,
                    chatId,
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
                    "MAX session changed concurrently for user " + session.userId()));
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
                    "MAX session delete conflicted for user " + session.userId());
        }
        session.restorePersistedRawPayload(null);
    }
}
