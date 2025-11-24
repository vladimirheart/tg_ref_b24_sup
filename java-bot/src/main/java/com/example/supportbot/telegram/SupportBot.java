package com.example.supportbot.telegram;

import com.example.supportbot.config.BotProperties;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SupportBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(SupportBot.class);

    private final BotProperties properties;
    private final BlacklistService blacklistService;
    private final AttachmentService attachmentService;
    private final ChannelService channelService;
    private final BotSettingsService botSettingsService;
    private final TicketService ticketService;
    private final ChatHistoryService chatHistoryService;
    private final FeedbackService feedbackService;

    private final Map<Long, ConversationSession> conversations = new ConcurrentHashMap<>();

    private volatile String cachedChannelPublicId;
    private volatile Channel cachedChannel;

    public SupportBot(BotProperties properties,
                      BlacklistService blacklistService,
                      AttachmentService attachmentService,
                      ChannelService channelService,
                      BotSettingsService botSettingsService,
                      TicketService ticketService,
                      ChatHistoryService chatHistoryService,
                      FeedbackService feedbackService) {
        super(properties.getToken());
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.attachmentService = attachmentService;
        this.channelService = channelService;
        this.botSettingsService = botSettingsService;
        this.ticketService = ticketService;
        this.chatHistoryService = chatHistoryService;
        this.feedbackService = feedbackService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if (message == null || (!message.hasText()
                && !message.hasDocument()
                && !message.hasPhoto()
                && !message.hasVideo()
                && !message.hasVoice()
                && !message.hasAudio()
                && !message.hasAnimation()
                && message.getSticker() == null
                && message.getVideoNote() == null)) {
            return;
        }

        if (handleOperatorMessage(message)) {
            return;
        }

        Channel channel = getChannel();
        long userId = Optional.ofNullable(message.getFrom()).map(User::getId).orElse(0L);
        BlacklistService.BlacklistStatus status = blacklistService.getStatus(userId);
        if (status.blacklisted()) {
            handleBlacklistedUser(message, status);
            return;
        }

        ConversationSession session = conversations.get(userId);
        if (message.hasText() && tryHandleFeedback(message, channel)) {
            return;
        }
        if (message.hasText()) {
            if ("/start".equalsIgnoreCase(message.getText())) {
                startConversation(message, session, channel);
            } else if ("/cancel".equalsIgnoreCase(message.getText())) {
                cancelConversation(message);
            } else if (session != null) {
                handleConversationAnswer(message, session);
            } else {
                handleTextMessage(message);
            }
        }

        if (message.hasDocument()) {
            handleDocument(message, session);
        }

        if (message.hasPhoto()) {
            handlePhoto(message, session);
        }

        if (message.hasVideo()) {
            handleVideo(message, session);
        }

        if (message.hasVoice()) {
            handleVoice(message, session);
        }

        if (message.hasAudio()) {
            handleAudio(message, session);
        }

        if (message.hasAnimation()) {
            handleAnimation(message, session);
        }

        if (message.getSticker() != null) {
            handleSticker(message, session);
        }

        if (message.getVideoNote() != null) {
            handleVideoNote(message, session);
        }
    }

    private void handleTextMessage(Message message) {
        String text = message.getText();
        if ("/unblock".equalsIgnoreCase(text)) {
            requestUnblock(message);
        }
    }

    private boolean handleOperatorMessage(Message message) {
        Integer configuredChannelId = properties.getChannelId();
        if (configuredChannelId == null) {
            return false;
        }
        long chatId = message.getChatId();
        if (chatId != configuredChannelId.longValue()) {
            return false;
        }

        String operatorText = Optional.ofNullable(message.getText()).orElse("").trim();
        TicketReference ticketReference = resolveTicketReference(message, operatorText);
        if (ticketReference.ticketId == null || ticketReference.outboundText.isBlank()) {
            return false;
        }

        Optional<TicketService.TicketWithUser> ticketOpt = ticketService.findByTicketId(ticketReference.ticketId);
        if (ticketOpt.isEmpty()) {
            SendMessage warning = SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось найти заявку с ID " + ticketReference.ticketId)
                    .build();
            try {
                execute(warning);
            } catch (TelegramApiException e) {
                log.error("Failed to notify about missing ticket", e);
            }
            return true;
        }

        TicketService.TicketWithUser ticket = ticketOpt.get();
        SendMessage toClient = SendMessage.builder()
                .chatId(ticket.userId())
                .text(ticketReference.outboundText)
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(toClient);
            chatHistoryService.storeOperatorMessage(
                    ticket.userId(),
                    ticket.ticketId(),
                    ticketReference.outboundText,
                    getChannel(),
                    message.getMessageId() != null ? message.getMessageId().longValue() : null,
                    ticketReference.replyToTelegramId);
        } catch (TelegramApiException e) {
            log.error("Failed to relay operator reply to user {}", ticket.userId(), e);
        }

        return true;
    }

    private TicketReference resolveTicketReference(Message message, String operatorText) {
        String candidateText = operatorText == null ? "" : operatorText.trim();
        Matcher replyCommand = Pattern.compile("^/reply\\s+(\\S+)\\s+(.+)$", Pattern.DOTALL).matcher(candidateText);
        if (replyCommand.matches()) {
            return new TicketReference(replyCommand.group(1), replyCommand.group(2).trim(), null);
        }

        Message repliedTo = message.getReplyToMessage();
        if (repliedTo != null && repliedTo.hasText()) {
            String ticketId = extractTicketId(repliedTo.getText());
            if (ticketId != null) {
                return new TicketReference(ticketId, candidateText, repliedTo.getMessageId() != null
                        ? repliedTo.getMessageId().longValue()
                        : null);
            }
        }

        String ticketId = extractTicketId(candidateText);
        return new TicketReference(ticketId, candidateText, null);
    }

    private String extractTicketId(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("#([A-Za-z0-9-]{6,})").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean tryHandleFeedback(Message message, Channel channel) {
        String text = Optional.ofNullable(message.getText()).orElse("").trim();
        if (!text.matches("\\d+")) {
            return false;
        }
        long userId = Optional.ofNullable(message.getFrom()).map(User::getId).orElse(0L);
        Optional<PendingFeedbackRequest> pendingOpt = feedbackService.findActiveRequest(userId, channel);
        if (pendingOpt.isEmpty()) {
            return false;
        }

        BotSettingsDto settings = loadSettings();
        Set<String> allowed = botSettingsService.ratingAllowedValues(settings);
        if (!allowed.contains(text)) {
            int scale = botSettingsService.ratingScale(settings, 5);
            SendMessage retry = SendMessage.builder()
                    .chatId(message.getChatId())
                    .text("Отправьте число от 1 до " + scale)
                    .build();
            try {
                execute(retry);
            } catch (TelegramApiException e) {
                log.error("Failed to prompt for valid rating", e);
            }
            return true;
        }

        int rating = Integer.parseInt(text);
        feedbackService.storeFeedback(pendingOpt.get(), rating);
        String response = botSettingsService.ratingResponseFor(settings, rating)
                .orElse("Спасибо за оценку!");
        SendMessage confirmation = SendMessage.builder()
                .chatId(message.getChatId())
                .text(response)
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(confirmation);
        } catch (TelegramApiException e) {
            log.error("Failed to send rating confirmation", e);
        }
        return true;
    }

    private void handleDocument(Message message, ConversationSession session) {
        Document document = message.getDocument();
        try (InputStream data = fetchFile(document.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), getExtension(document.getFileName()), data);
            log.info("Document saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "document", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store document", e);
        }
    }

    private void handlePhoto(Message message, ConversationSession session) {
        List<PhotoSize> photos = message.getPhoto();
        PhotoSize largest = photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(null);
        if (largest == null) {
            return;
        }
        try (InputStream data = fetchFile(largest.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), "jpg", data);
            log.info("Photo saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "photo", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store photo", e);
        }
    }

    private void handleVideo(Message message, ConversationSession session) {
        Video video = message.getVideo();
        if (video == null) {
            return;
        }
        try (InputStream data = fetchFile(video.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), "mp4", data);
            log.info("Video saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "video", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store video", e);
        }
    }

    private void handleVoice(Message message, ConversationSession session) {
        Voice voice = message.getVoice();
        if (voice == null) {
            return;
        }
        String extension = extensionFromMime(voice.getMimeType(), "ogg");
        try (InputStream data = fetchFile(voice.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), extension, data);
            log.info("Voice saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "voice", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store voice", e);
        }
    }

    private void handleAudio(Message message, ConversationSession session) {
        Audio audio = message.getAudio();
        if (audio == null) {
            return;
        }
        String extension = Optional.ofNullable(getExtension(audio.getFileName()))
                .orElseGet(() -> extensionFromMime(audio.getMimeType(), "mp3"));
        try (InputStream data = fetchFile(audio.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), extension, data);
            log.info("Audio saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "audio", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store audio", e);
        }
    }

    private void handleAnimation(Message message, ConversationSession session) {
        Animation animation = message.getAnimation();
        if (animation == null) {
            return;
        }
        String extension = Optional.ofNullable(getExtension(animation.getFileName()))
                .orElseGet(() -> extensionFromMime(animation.getMimeType(), "mp4"));
        try (InputStream data = fetchFile(animation.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), extension, data);
            log.info("Animation saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "animation", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store animation", e);
        }
    }

    private void handleSticker(Message message, ConversationSession session) {
        Sticker sticker = message.getSticker();
        if (sticker == null) {
            return;
        }
        String extension = sticker.getIsAnimated() != null && sticker.getIsAnimated()
                ? "tgs"
                : (sticker.getIsVideo() != null && sticker.getIsVideo() ? "webm" : "webp");
        try (InputStream data = fetchFile(sticker.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), extension, data);
            log.info("Sticker saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "sticker", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store sticker", e);
        }
    }

    private void handleVideoNote(Message message, ConversationSession session) {
        VideoNote note = message.getVideoNote();
        if (note == null) {
            return;
        }
        try (InputStream data = fetchFile(note.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), "mp4", data);
            log.info("Video note saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "video_note", stored.toString());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store video note", e);
        }
    }

    private InputStream fetchFile(String fileId) throws TelegramApiException {
        GetFile request = new GetFile(fileId);
        org.telegram.telegrambots.meta.api.objects.File file = execute(request);
        return downloadFileAsStream(file);
    }

    @Transactional
    protected void requestUnblock(Message message) {
        long userId = message.getFrom().getId();
        blacklistService.registerUnblockRequest(userId, "", properties.getChannelId());
        SendMessage confirmation = SendMessage.builder()
                .chatId(message.getChatId())
                .text("Запрос на разблокировку отправлен оператору.")
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(confirmation);
        } catch (TelegramApiException e) {
            log.error("Failed to send unblock confirmation", e);
        }
    }

    private void handleBlacklistedUser(Message message, BlacklistService.BlacklistStatus status) {
        String text = status.unblockRequested()
                ? "Ваш аккаунт заблокирован. Запрос уже на рассмотрении."
                : "Ваш аккаунт заблокирован. Отправьте /unblock, чтобы подать запрос на разблокировку.";
        SendMessage warning = SendMessage.builder()
                .chatId(message.getChatId())
                .text(text)
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(warning);
        } catch (TelegramApiException e) {
            log.error("Failed to notify blacklisted user", e);
        }
    }

    private String getChannelPublicId() {
        String cached = cachedChannelPublicId;
        if (cached != null) {
            return cached;
        }
        Channel channel = getChannel();
        cachedChannelPublicId = channel.getPublicId();
        return cachedChannelPublicId;
    }

    private Channel getChannel() {
        Channel channel = cachedChannel;
        if (channel != null) {
            return channel;
        }
        Channel ensured = channelService.ensurePublicIdForToken(properties.getToken());
        cachedChannel = ensured;
        return ensured;
    }

    private BotSettingsDto loadSettings() {
        return botSettingsService.loadFromChannel(getChannel());
    }

    private String getExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int idx = fileName.lastIndexOf('.');
        if (idx == -1) {
            return null;
        }
        return fileName.substring(idx + 1);
    }

    private String extensionFromMime(String mimeType, String fallback) {
        if (mimeType == null || !mimeType.contains("/")) {
            return fallback;
        }
        String candidate = mimeType.substring(mimeType.lastIndexOf('/') + 1);
        if (candidate.isBlank()) {
            return fallback;
        }
        return candidate;
    }

    private void startConversation(Message message, ConversationSession existing, Channel channel) {
        if (existing != null) {
            SendMessage warning = SendMessage.builder()
                    .chatId(message.getChatId())
                    .text("У вас уже есть активная заявка. Отправьте /cancel, чтобы начать заново.")
                    .replyMarkup(new ReplyKeyboardRemove(true))
                    .build();
            try {
                execute(warning);
            } catch (TelegramApiException e) {
                log.error("Failed to notify about existing conversation", e);
            }
            return;
        }

        BotSettingsDto settings = loadSettings();
        List<QuestionFlowItemDto> flow = new ArrayList<>(Optional.ofNullable(settings.getQuestionFlow()).orElseGet(List::of));
        flow.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));
        flow.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", flow.size() + 1, null, List.of()));

        ConversationSession session = new ConversationSession(flow, message.getChatId(), message.getFrom(), settings);
        ticketService.findLastMessage(message.getFrom().getId())
                .ifPresent(last -> session.enableReusePrompt(Map.of(
                        "business", Optional.ofNullable(last.getBusiness()).orElse(""),
                        "location_type", Optional.ofNullable(last.getLocationType()).orElse(""),
                        "city", Optional.ofNullable(last.getCity()).orElse(""),
                        "location_name", Optional.ofNullable(last.getLocationName()).orElse("")
                )));
        conversations.put(message.getFrom().getId(), session);
        askCurrentQuestion(session);
    }

    private void handleConversationAnswer(Message message, ConversationSession session) {
        if (session.awaitingReuseDecision()) {
            if (!session.consumeReuseDecision(message.getText())) {
                SendMessage retry = SendMessage.builder()
                        .chatId(session.chatId())
                        .text("Ответьте 'да' чтобы повторить прошлые данные или 'нет' чтобы заполнить заново.")
                        .build();
                try {
                    execute(retry);
                } catch (TelegramApiException e) {
                    log.error("Failed to resend reuse prompt", e);
                }
                return;
            }
            if (session.isComplete()) {
                finalizeConversation(session);
            } else {
                askCurrentQuestion(session);
            }
            return;
        }

        session.recordAnswer(message);
        if (session.isComplete()) {
            finalizeConversation(session);
        } else {
            askCurrentQuestion(session);
        }
    }

    private void askCurrentQuestion(ConversationSession session) {
        if (session.awaitingReuseDecision()) {
            SendMessage prompt = SendMessage.builder()
                    .chatId(session.chatId())
                    .text(session.reusePrompt())
                    .replyMarkup(new ReplyKeyboardRemove(true))
                    .build();
            try {
                execute(prompt);
            } catch (TelegramApiException e) {
                log.error("Failed to send reuse prompt", e);
            }
            return;
        }
        QuestionFlowItemDto current = session.currentQuestion();
        if (current == null) {
            return;
        }
        SendMessage prompt = SendMessage.builder()
                .chatId(session.chatId())
                .text(current.getText())
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(prompt);
        } catch (TelegramApiException e) {
            log.error("Failed to send conversation prompt", e);
        }
    }

    private void finalizeConversation(ConversationSession session) {
        conversations.remove(session.userId());
        Channel channel = getChannel();
        TicketService.TicketCreationResult ticket = ticketService.createTicket(session.userId(), session.user(), session.answers(), channel);
        String summary = session.buildSummary(ticket.ticketId());

        for (HistoryEvent event : session.historyEvents()) {
            chatHistoryService.storeEntry(event.userId(), event.telegramMessageId(), channel, ticket.ticketId(), event.text(), event.messageType(), event.attachmentPath());
        }

        if (properties.getChannelId() != null) {
            SendMessage toChannel = SendMessage.builder()
                    .chatId(properties.getChannelId().longValue())
                    .text(summary)
                    .build();
            try {
                execute(toChannel);
            } catch (TelegramApiException e) {
                log.error("Failed to send ticket to operator channel", e);
            }
        }

        SendMessage confirmation = SendMessage.builder()
                .chatId(session.chatId())
                .text("Спасибо! Заявка отправлена оператору. Мы свяжемся с вами после обработки.")
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(confirmation);
        } catch (TelegramApiException e) {
            log.error("Failed to send confirmation", e);
        }

        int scale = botSettingsService.ratingScale(session.settings(), 5);
        String promptTemplate = botSettingsService.ratingPrompt(session.settings(), "Оцените заявку {ticket_id} по шкале 1-{scale}");
        String promptText = promptTemplate
                .replace("{ticket_id}", ticket.ticketId())
                .replace("{scale}", Integer.toString(scale));
        SendMessage ratingPrompt = SendMessage.builder()
                .chatId(session.chatId())
                .text(promptText)
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(ratingPrompt);
        } catch (TelegramApiException e) {
            log.error("Failed to send rating prompt", e);
        }
    }

    private void cancelConversation(Message message) {
        ConversationSession session = conversations.remove(message.getFrom().getId());
        if (session == null) {
            return;
        }
        SendMessage cancelled = SendMessage.builder()
                .chatId(message.getChatId())
                .text("Текущая заявка отменена.")
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(cancelled);
        } catch (TelegramApiException e) {
            log.error("Failed to send cancellation notice", e);
        }
    }

    private static final class ConversationSession {
        private final List<QuestionFlowItemDto> flow;
        private final long chatId;
        private final User user;
        private final BotSettingsDto settings;
        private final Map<String, String> answers;
        private final List<Path> attachments;
        private final List<HistoryEvent> historyEvents;
        private final OffsetDateTime startedAt;
        private Map<String, String> cachedAnswers;
        private boolean reuseDecisionPending;
        private int currentIndex;

        ConversationSession(List<QuestionFlowItemDto> flow, long chatId, User user, BotSettingsDto settings) {
            this.flow = flow;
            this.chatId = chatId;
            this.user = user;
            this.settings = settings;
            this.answers = new LinkedHashMap<>();
            this.attachments = new ArrayList<>();
            this.historyEvents = new ArrayList<>();
            this.startedAt = OffsetDateTime.now();
            this.cachedAnswers = new LinkedHashMap<>();
            this.reuseDecisionPending = false;
            this.currentIndex = 0;
        }

        QuestionFlowItemDto currentQuestion() {
            if (currentIndex < 0 || currentIndex >= flow.size()) {
                return null;
            }
            return flow.get(currentIndex);
        }

        void recordAnswer(Message message) {
            QuestionFlowItemDto current = currentQuestion();
            if (current == null) {
                return;
            }
            String answer = message.getText();
            answers.put(current.getId(), answer);
            currentIndex += 1;
            addHistoryEvent(message, "text", null);
        }

        boolean isComplete() {
            return currentIndex >= flow.size();
        }

        void addAttachment(Path attachment) {
            attachments.add(attachment);
        }

        void addHistoryEvent(Message message, String messageType, String attachmentPath) {
            HistoryEvent event = new HistoryEvent(
                    message.getFrom() != null ? message.getFrom().getId() : null,
                    message.getMessageId() != null ? message.getMessageId().longValue() : null,
                    Optional.ofNullable(message.getText()).orElse(messageType),
                    messageType,
                    attachmentPath);
            historyEvents.add(event);
        }

        List<HistoryEvent> historyEvents() {
            return historyEvents;
        }

        long chatId() {
            return chatId;
        }

        long userId() {
            return user != null ? user.getId() : 0L;
        }

        User user() {
            return user;
        }

        Map<String, String> answers() {
            return answers;
        }

        List<Path> attachments() {
            return attachments;
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
            builder.append("Новая заявка #").append(ticketId).append(" от пользователя ").append(userId()).append("\n");
            builder.append("Создана: ").append(startedAt).append("\n\n");
            for (QuestionFlowItemDto item : flow) {
                String answer = answers.getOrDefault(item.getId(), "");
                builder.append(item.getText()).append(": ").append(answer).append("\n");
            }
            if (!attachments.isEmpty()) {
                builder.append("\nВложения:\n");
                for (Path attachment : attachments) {
                    builder.append("- ").append(attachment).append("\n");
                }
            }
            return builder.toString();
        }
    }

    private record TicketReference(String ticketId, String outboundText, Long replyToTelegramId) {}

    private record HistoryEvent(Long userId, Long telegramMessageId, String text, String messageType, String attachmentPath) {}

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }
}
