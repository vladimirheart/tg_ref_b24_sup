package com.example.supportbot.telegram;

import com.example.supportbot.config.BotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.BlacklistService;
import com.example.supportbot.service.ChannelService;
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
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SupportBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(SupportBot.class);

    private final BotProperties properties;
    private final BlacklistService blacklistService;
    private final AttachmentService attachmentService;
    private final ChannelService channelService;
    private final BotSettingsService botSettingsService;

    private final Map<Long, ConversationSession> conversations = new ConcurrentHashMap<>();

    private volatile String cachedChannelPublicId;

    public SupportBot(BotProperties properties,
                      BlacklistService blacklistService,
                      AttachmentService attachmentService,
                      ChannelService channelService,
                      BotSettingsService botSettingsService) {
        super(properties.getToken());
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.attachmentService = attachmentService;
        this.channelService = channelService;
        this.botSettingsService = botSettingsService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if (message == null || (!message.hasText() && !message.hasDocument() && !message.hasPhoto() && !message.hasVideo())) {
            return;
        }

        long userId = Optional.ofNullable(message.getFrom()).map(User::getId).orElse(0L);
        BlacklistService.BlacklistStatus status = blacklistService.getStatus(userId);
        if (status.blacklisted()) {
            handleBlacklistedUser(message, status);
            return;
        }

        ConversationSession session = conversations.get(userId);
        if (message.hasText()) {
            if ("/start".equalsIgnoreCase(message.getText())) {
                startConversation(message, session);
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
    }

    private void handleTextMessage(Message message) {
        String text = message.getText();
        if ("/unblock".equalsIgnoreCase(text)) {
            requestUnblock(message);
        }
    }

    private void handleDocument(Message message, ConversationSession session) {
        Document document = message.getDocument();
        try (InputStream data = fetchFile(document.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), getExtension(document.getFileName()), data);
            log.info("Document saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
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
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store video", e);
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
        Channel channel = channelService.ensurePublicIdForToken(properties.getToken());
        cachedChannelPublicId = channel.getPublicId();
        return cachedChannelPublicId;
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

    private void startConversation(Message message, ConversationSession existing) {
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

        BotSettingsDto settings = botSettingsService.buildDefaultSettings();
        List<QuestionFlowItemDto> flow = new ArrayList<>(Optional.ofNullable(settings.getQuestionFlow()).orElseGet(List::of));
        flow.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));
        flow.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", flow.size() + 1, null, List.of()));

        ConversationSession session = new ConversationSession(flow, message.getChatId(), message.getFrom().getId());
        conversations.put(message.getFrom().getId(), session);
        askCurrentQuestion(session);
    }

    private void handleConversationAnswer(Message message, ConversationSession session) {
        String response = message.getText();
        session.recordAnswer(response);
        if (session.isComplete()) {
            finalizeConversation(session);
        } else {
            askCurrentQuestion(session);
        }
    }

    private void askCurrentQuestion(ConversationSession session) {
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
        String summary = session.buildSummary();

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

    private record ConversationSession(List<QuestionFlowItemDto> flow,
                                       long chatId,
                                       long userId,
                                       Map<String, String> answers,
                                       List<Path> attachments,
                                       int currentIndex,
                                       OffsetDateTime startedAt) {

        ConversationSession(List<QuestionFlowItemDto> flow, long chatId, long userId) {
            this(flow, chatId, userId, new LinkedHashMap<>(), new ArrayList<>(), 0, OffsetDateTime.now());
        }

        QuestionFlowItemDto currentQuestion() {
            if (currentIndex < 0 || currentIndex >= flow.size()) {
                return null;
            }
            return flow.get(currentIndex);
        }

        void recordAnswer(String answer) {
            QuestionFlowItemDto current = currentQuestion();
            if (current == null) {
                return;
            }
            answers.put(current.getId(), answer);
            currentIndex += 1;
        }

        boolean isComplete() {
            return currentIndex >= flow.size();
        }

        void addAttachment(Path attachment) {
            attachments.add(attachment);
        }

        String buildSummary() {
            StringBuilder builder = new StringBuilder();
            builder.append("Новая заявка от пользователя ").append(userId).append("\n");
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

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }
}
