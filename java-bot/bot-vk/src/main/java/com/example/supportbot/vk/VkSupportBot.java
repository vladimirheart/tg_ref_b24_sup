package com.example.supportbot.vk;

import com.example.supportbot.config.VkBotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.BlacklistService;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.FeedbackService;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.service.UnblockRequestService;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.vk.api.sdk.callback.longpoll.BotsLongPoll;
import com.vk.api.sdk.callback.longpoll.responses.GetLongPollEventsResponse;
import com.vk.api.sdk.callback.longpoll.responses.LongPollGroupUpdates;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.exceptions.LongPollServerKeyExpiredException;
import com.vk.api.sdk.exceptions.LongPollServerTsException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.AudioMessage;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButton;
import com.vk.api.sdk.objects.messages.KeyboardButtonAction;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionType;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.MessageAttachment;
import com.vk.api.sdk.objects.messages.MessageAttachmentType;
import com.vk.api.sdk.objects.docs.Doc;
import com.vk.api.sdk.objects.photos.Photo;
import com.vk.api.sdk.objects.photos.PhotoSizes;
import com.vk.api.sdk.objects.video.Video;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@Component
public class VkSupportBot implements SmartLifecycle, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(VkSupportBot.class);
    private static final int MAX_LOG_TEXT_LENGTH = 160;

    private final VkBotProperties properties;
    private final BlacklistService blacklistService;
    private final UnblockRequestService unblockRequestService;
    private final AttachmentService attachmentService;
    private final ChannelService channelService;
    private final BotSettingsService botSettingsService;
    private final TicketService ticketService;
    private final ChatHistoryService chatHistoryService;
    private final FeedbackService feedbackService;
    private final VkApiClient vkClient;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, ConversationSession> sessions = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile Channel cachedChannel;

    public VkSupportBot(VkBotProperties properties,
                        BlacklistService blacklistService,
                        UnblockRequestService unblockRequestService,
                        AttachmentService attachmentService,
                        ChannelService channelService,
                        BotSettingsService botSettingsService,
                        TicketService ticketService,
                        ChatHistoryService chatHistoryService,
                        FeedbackService feedbackService) {
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.unblockRequestService = unblockRequestService;
        this.attachmentService = attachmentService;
        this.channelService = channelService;
        this.botSettingsService = botSettingsService;
        this.ticketService = ticketService;
        this.chatHistoryService = chatHistoryService;
        this.feedbackService = feedbackService;
        this.vkClient = new VkApiClient(new HttpTransportClient());
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("VK bot is disabled; skipping start");
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
        onMessage(actor, message);
    }

    private void runLoop() {
        GroupActor actor = createActor();
        BotsLongPoll poller = new BotsLongPoll(vkClient, actor);
        while (running) {
            try {
                poller.runBot(response -> handleUpdates(actor, response));
            } catch (LongPollServerTsException | LongPollServerKeyExpiredException e) {
                log.warn("VK long poll server state expired, restarting", e);
            } catch (Exception ex) {
                log.error("VK long poll failed", ex);
            }
            try {
                Thread.sleep(properties.getRetryDelaySeconds() * 1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private GroupActor createActor() {
        return new GroupActor(properties.getGroupId(), properties.getToken());
    }

    private void handleUpdates(GroupActor actor, GetLongPollEventsResponse response) {
        if (response == null) {
            return;
        }
        log.info("Received VK long poll response with {} update(s)", response.getUpdates().size());
        for (LongPollGroupUpdates update : response.getUpdates()) {
            if ("message_new".equals(update.getType())) {
                Message message = update.getObject().getMessage();
                if (message != null) {
                    onMessage(actor, message);
                }
            }
        }
    }

    private void onMessage(GroupActor actor, Message message) {
        Integer fromId = message.getFromId();
        Integer peerId = message.getPeerId();
        if (fromId == null || peerId == null || !peerId.equals(fromId)) {
            // ignore group chats
            log.info("Ignoring VK message from peer {} user {}: not a direct message", peerId, fromId);
            return;
        }
        logIncomingMessage(message);
        Channel channel = getChannel();
        BlacklistService.BlacklistStatus status = blacklistService.getStatus(fromId.longValue());
        if (status.blacklisted()) {
            log.info("Blocked message from blacklisted VK user {}", fromId);
            String text = Optional.ofNullable(message.getText()).orElse("").trim();
            if ("/unblock".equalsIgnoreCase(text)) {
                handleUnblockRequest(actor, peerId, fromId.longValue(), channel);
            } else {
                sendText(actor, peerId, status.unblockRequested()
                        ? "Ваш аккаунт заблокирован. Запрос уже на рассмотрении."
                        : "Ваш аккаунт заблокирован. Ответьте /unblock, чтобы подать запрос.");
            }
            return;
        }

        String text = Optional.ofNullable(message.getText()).orElse("").trim();
        if ("/start".equalsIgnoreCase(text)) {
            log.info("Received /start from VK user {}", fromId);
        }
        if (!text.isEmpty() && tryHandleFeedback(actor, message, channel, text)) {
            log.info("Handled feedback from VK user {}", fromId);
            return;
        }
        if (isMyTicketsCommand(text)) {
            log.info("Received my tickets command from VK user {}", fromId);
            handleMyTickets(actor, peerId, fromId.longValue());
            return;
        }

        ConversationSession session = sessions.computeIfAbsent(fromId, id -> startSession(actor, message, channel));
        if ("/cancel".equalsIgnoreCase(text)) {
            log.info("Received /cancel from VK user {}", fromId);
            sessions.remove(fromId);
            sendText(actor, peerId, "Текущая заявка отменена.");
            return;
        }
        if ("/unblock".equalsIgnoreCase(text)) {
            log.info("Received /unblock from VK user {}", fromId);
            handleUnblockRequest(actor, peerId, fromId.longValue(), channel);
            return;
        }

        if (session.awaitingReuseDecision()) {
            if (!session.consumeReuseDecision(text)) {
                sendText(actor, peerId, "Ответьте 'да', чтобы использовать прошлые значения, или 'нет', чтобы заполнить заново.",
                        reuseDecisionKeyboard());
                return;
            }
            if (session.isComplete()) {
                finalizeConversation(actor, session);
            } else {
                promptCurrentQuestion(actor, session);
            }
            return;
        }

        if (!text.isBlank()) {
            session.recordAnswer(text);
        }
        storeAttachments(message, session);

        if (session.isComplete()) {
            finalizeConversation(actor, session);
        } else {
            promptCurrentQuestion(actor, session);
        }
    }

    private boolean tryHandleFeedback(GroupActor actor, Message message, Channel channel, String text) {
        if (!text.matches("\\d+")) {
            return false;
        }
        Optional<PendingFeedbackRequest> pendingOpt = feedbackService.findActiveRequest(message.getFromId().longValue(), channel);
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

    private ConversationSession startSession(GroupActor actor, Message message, Channel channel) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        List<QuestionFlowItemDto> flow = new ArrayList<>(Optional.ofNullable(settings.getQuestionFlow()).orElseGet(List::of));
        flow.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));
        flow.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", flow.size() + 1, null, List.of()));

        ConversationSession session = new ConversationSession(message.getPeerId(), message.getFromId(), flow, settings);
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
        promptCurrentQuestion(actor, session);
        return session;
    }

    private void promptCurrentQuestion(GroupActor actor, ConversationSession session) {
        if (session.awaitingReuseDecision()) {
            sendText(actor, session.peerId(), session.reusePrompt(), reuseDecisionKeyboard());
            return;
        }
        QuestionFlowItemDto current = session.currentQuestion();
        if (current != null) {
            sendText(actor, session.peerId(), current.getText());
        }
    }

    private void finalizeConversation(GroupActor actor, ConversationSession session) {
        sessions.remove(session.userId());
        Channel channel = getChannel();
        TicketService.TicketCreationResult result = ticketService.createTicket(session.userId(), null, session.answers(), channel);
        String requestNumber = result.groupMessageId() != null ? result.groupMessageId().toString() : result.ticketId();
        log.info("Created VK ticket {} for user {}", result.ticketId(), session.userId());
        for (HistoryEvent event : session.history()) {
            chatHistoryService.storeEntry(event.userId().longValue(), null, channel, result.ticketId(), event.text(), event.messageType(), event.attachment());
        }
        sendText(actor, session.peerId(), "Спасибо! Ваше обращение №" + requestNumber + " отправлено оператору.");
        if (properties.getChannelId() != null && properties.getChannelId() > 0) {
            sendText(actor, properties.getChannelId().intValue(), session.buildSummary(result.ticketId()));
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
                    case VIDEO -> handleVideo(attachment.getVideo(), session);
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

    private void handleVideo(Video video, ConversationSession session) throws Exception {
        if (video == null || video.getPlayer() == null) {
            return;
        }
        storeFromUrl(video.getPlayer().toString(), "mp4", session, "video");
    }

    private void storeFromUrl(String url, String extension, ConversationSession session, String messageType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Path stored = attachmentService.store(getChannel().getPublicId(), extension, response.body());
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
                    .send(actor)
                    .peerId(peerId)
                    .randomId(ThreadLocalRandom.current().nextInt())
                    .message(text)
                    .execute();
            return true;
        } catch (ClientException e) {
            log.error("Failed to send VK message to peer {}", peerId, e);
            return false;
        }
    }

    private void sendText(GroupActor actor, int peerId, String text) {
        sendText(actor, peerId, text, null);
    }

    private void sendText(GroupActor actor, int peerId, String text, Keyboard keyboard) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            log.info("Sending VK message to peer {}: {}", peerId, summarizeText(text));
            var request = vkClient.messages()
                    .send(actor)
                    .peerId(peerId)
                    .randomId(ThreadLocalRandom.current().nextInt())
                    .message(text);
            if (keyboard != null) {
                request.keyboard(keyboard);
            }
            request.execute();
        } catch (ClientException e) {
            log.error("Failed to send VK message to peer {}", peerId, e);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void sendUnblockDigest() {
        Integer channelId = properties.getChannelId();
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

    private void notifyOperatorsAboutUnblockRequest(GroupActor actor,
                                                    com.example.supportbot.entity.ClientUnblockRequest request) {
        Integer channelId = properties.getChannelId();
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

    private void sendOperatorMessage(Integer channelId, String text) {
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

    private void handleUnblockRequest(GroupActor actor, int peerId, long userId, Channel channel) {
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

    private void handleMyTickets(GroupActor actor, int peerId, long userId) {
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

    private Keyboard reuseDecisionKeyboard() {
        KeyboardButton yesButton = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT).setLabel("Да"))
                .setColor(KeyboardButtonColor.POSITIVE);
        KeyboardButton noButton = new KeyboardButton()
                .setAction(new KeyboardButtonAction().setType(KeyboardButtonActionType.TEXT).setLabel("Нет"))
                .setColor(KeyboardButtonColor.NEGATIVE);
        return new Keyboard()
                .setOneTime(true)
                .setButtons(List.of(List.of(yesButton, noButton)));
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

    private record HistoryEvent(Integer userId, String text, String messageType, String attachment) {
    }

    private static final class ConversationSession {
        private final int peerId;
        private final int userId;
        private final List<QuestionFlowItemDto> flow;
        private final BotSettingsDto settings;
        private final Map<String, String> answers = new LinkedHashMap<>();
        private final List<HistoryEvent> history = new ArrayList<>();
        private final OffsetDateTime startedAt = OffsetDateTime.now();
        private Map<String, String> cachedAnswers = new LinkedHashMap<>();
        private boolean reuseDecisionPending = false;
        private int currentIndex = 0;

        ConversationSession(int peerId, int userId, List<QuestionFlowItemDto> flow, BotSettingsDto settings) {
            this.peerId = peerId;
            this.userId = userId;
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
            answers.put(current.getId(), text);
            history.add(new HistoryEvent(userId, text, "text", null));
            currentIndex += 1;
        }

        boolean isComplete() {
            return currentIndex >= flow.size();
        }

        void addAttachment(Path path, String messageType) {
            history.add(new HistoryEvent(userId, messageType, messageType, path.toString()));
        }

        List<HistoryEvent> history() {
            return history;
        }

        Map<String, String> answers() {
            return answers;
        }

        int peerId() {
            return peerId;
        }

        int userId() {
            return userId;
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
                if (cachedAnswers.containsKey(item.getId())) {
                    answers.put(item.getId(), cachedAnswers.get(item.getId()));
                    currentIndex = i + 1;
                } else {
                    currentIndex = i;
                    break;
                }
            }
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
                builder.append(item.getText()).append(": ")
                        .append(answers.getOrDefault(item.getId(), "")).append("\n");
            }
            if (!history.isEmpty()) {
                builder.append("\nВложения:\n");
                history.stream()
                        .filter(h -> h.attachment() != null)
                        .forEach(h -> builder.append("- ").append(h.attachment()).append("\n"));
            }
            return builder.toString();
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
}
