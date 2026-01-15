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
import java.time.OffsetDateTime;
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
import org.springframework.stereotype.Component;

@Component
public class VkSupportBot implements SmartLifecycle, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(VkSupportBot.class);

    private final VkBotProperties properties;
    private final BlacklistService blacklistService;
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
                        AttachmentService attachmentService,
                        ChannelService channelService,
                        BotSettingsService botSettingsService,
                        TicketService ticketService,
                        ChatHistoryService chatHistoryService,
                        FeedbackService feedbackService) {
        this.properties = properties;
        this.blacklistService = blacklistService;
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
            return;
        }
        Channel channel = getChannel();
        BlacklistService.BlacklistStatus status = blacklistService.getStatus(fromId.longValue());
        if (status.blacklisted()) {
            sendText(actor, peerId, status.unblockRequested()
                    ? "Ваш аккаунт заблокирован. Запрос уже на рассмотрении."
                    : "Ваш аккаунт заблокирован. Ответьте /unblock, чтобы подать запрос.");
            return;
        }

        String text = Optional.ofNullable(message.getText()).orElse("").trim();
        if (!text.isEmpty() && tryHandleFeedback(actor, message, channel, text)) {
            return;
        }

        ConversationSession session = sessions.computeIfAbsent(fromId, id -> startSession(actor, message, channel));
        if ("/cancel".equalsIgnoreCase(text)) {
            sessions.remove(fromId);
            sendText(actor, peerId, "Текущая заявка отменена.");
            return;
        }
        if ("/unblock".equalsIgnoreCase(text)) {
            Integer channelId = channel.getId() != null ? Math.toIntExact(channel.getId()) : null;
            blacklistService.registerUnblockRequest(fromId.longValue(), "", channelId);
            sendText(actor, peerId, "Запрос на разблокировку отправлен оператору.");
            return;
        }

        if (session.awaitingReuseDecision()) {
            if (!session.consumeReuseDecision(text)) {
                sendText(actor, peerId, "Ответьте 'да', чтобы использовать прошлые значения, или 'нет', чтобы заполнить заново.");
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
            sendText(actor, session.peerId(), session.reusePrompt());
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
        for (HistoryEvent event : session.history()) {
            chatHistoryService.storeEntry(event.userId().longValue(), null, channel, result.ticketId(), event.text(), event.messageType(), event.attachment());
        }
        sendText(actor, session.peerId(), "Спасибо! Заявка отправлена оператору.");
        if (properties.getChannelId() != null && properties.getChannelId() > 0) {
            sendText(actor, properties.getChannelId().intValue(), session.buildSummary(result.ticketId()));
        }
        int scale = botSettingsService.ratingScale(session.settings(), 5);
        String prompt = botSettingsService.ratingPrompt(session.settings(), "Оцените заявку {ticket_id} по шкале 1-{scale}")
                .replace("{ticket_id}", result.ticketId())
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
            vkClient.messages()
                    .send(actor)
                    .peerId(peerId)
                    .randomId(ThreadLocalRandom.current().nextInt())
                    .message(text)
                    .execute();
            return true;
        } catch (ClientException e) {
            log.error("Failed to send VK message", e);
            return false;
        }
    }

    private void sendText(GroupActor actor, int peerId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            vkClient.messages()
                    .send(actor)
                    .peerId(peerId)
                    .randomId(ThreadLocalRandom.current().nextInt())
                    .message(text)
                    .execute();
        } catch (ClientException e) {
            log.error("Failed to send VK message", e);
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

    private String extensionFrom(String ext, String fallback) {
        if (ext == null || ext.isBlank()) {
            return fallback;
        }
        return ext;
    }
}
