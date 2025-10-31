package com.example.supportbot.telegram;

import com.example.supportbot.config.BotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.BlacklistService;
import com.example.supportbot.service.ChannelService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class SupportBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(SupportBot.class);

    private final BotProperties properties;
    private final BlacklistService blacklistService;
    private final AttachmentService attachmentService;
    private final ChannelService channelService;

    private volatile String cachedChannelPublicId;

    public SupportBot(BotProperties properties,
                      BlacklistService blacklistService,
                      AttachmentService attachmentService,
                      ChannelService channelService) {
        super(properties.getToken());
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.attachmentService = attachmentService;
        this.channelService = channelService;
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

        if (message.hasText()) {
            handleTextMessage(message);
        }

        if (message.hasDocument()) {
            handleDocument(message);
        }

        if (message.hasPhoto()) {
            handlePhoto(message);
        }

        if (message.hasVideo()) {
            handleVideo(message);
        }
    }

    private void handleTextMessage(Message message) {
        String text = message.getText();
        if ("/unblock".equalsIgnoreCase(text)) {
            requestUnblock(message);
        }
    }

    private void handleDocument(Message message) {
        Document document = message.getDocument();
        try (InputStream data = fetchFile(document.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), getExtension(document.getFileName()), data);
            log.info("Document saved for user {} at {}", message.getFrom().getId(), stored);
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store document", e);
        }
    }

    private void handlePhoto(Message message) {
        List<PhotoSize> photos = message.getPhoto();
        PhotoSize largest = photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(null);
        if (largest == null) {
            return;
        }
        try (InputStream data = fetchFile(largest.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), "jpg", data);
            log.info("Photo saved for user {} at {}", message.getFrom().getId(), stored);
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store photo", e);
        }
    }

    private void handleVideo(Message message) {
        Video video = message.getVideo();
        if (video == null) {
            return;
        }
        try (InputStream data = fetchFile(video.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), "mp4", data);
            log.info("Video saved for user {} at {}", message.getFrom().getId(), stored);
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

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }
}
