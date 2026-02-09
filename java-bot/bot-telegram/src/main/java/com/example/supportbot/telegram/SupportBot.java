package com.example.supportbot.telegram;

import com.example.supportbot.config.BotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.BlacklistService;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.FeedbackService;
import com.example.supportbot.service.SharedConfigService;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.games.Animation;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SupportBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(SupportBot.class);
    private static final int MAX_LOG_TEXT_LENGTH = 160;
    private static final String BACK_BUTTON = "Назад";

    private final BotProperties properties;
    private final BlacklistService blacklistService;
    private final AttachmentService attachmentService;
    private final ChannelService channelService;
    private final BotSettingsService botSettingsService;
    private final TicketService ticketService;
    private final ChatHistoryService chatHistoryService;
    private final FeedbackService feedbackService;
    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;

    private final Map<Long, ConversationSession> conversations = new ConcurrentHashMap<>();

    private volatile String cachedChannelPublicId;
    private volatile Channel cachedChannel;
    private volatile Map<String, Object> cachedLocationTree;
    private volatile Map<String, Object> cachedPresetDefinitions;

    public SupportBot(BotProperties properties,
                      BlacklistService blacklistService,
                      AttachmentService attachmentService,
                      ChannelService channelService,
                      BotSettingsService botSettingsService,
                      TicketService ticketService,
                      ChatHistoryService chatHistoryService,
                      FeedbackService feedbackService,
                      SharedConfigService sharedConfigService,
                      ObjectMapper objectMapper) {
        super(properties.getToken());
        this.properties = properties;
        this.blacklistService = blacklistService;
        this.attachmentService = attachmentService;
        this.channelService = channelService;
        this.botSettingsService = botSettingsService;
        this.ticketService = ticketService;
        this.chatHistoryService = chatHistoryService;
        this.feedbackService = feedbackService;
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void logBotConfiguration() {
        log.info("Initializing Telegram bot configuration check");
        String token = properties.getToken();
        String username = properties.getUsername();
        Long channelId = properties.getChannelId();
        boolean tokenConfigured = token != null && !token.isBlank() && !"YOUR_TELEGRAM_BOT_TOKEN".equals(token);
        boolean usernameConfigured = username != null && !username.isBlank() && !"your_bot_username".equalsIgnoreCase(username);

        log.info("Telegram bot configuration: username='{}' token={} channelId={}",
                usernameConfigured ? username : "(missing)",
                tokenConfigured ? maskToken(token) : "(missing)",
                channelId);

        if (!tokenConfigured) {
            log.warn("Telegram bot token is not configured; check TELEGRAM_BOT_TOKEN or support-bot.token");
        }
        if (!usernameConfigured) {
            log.warn("Telegram bot username is not configured; check TELEGRAM_BOT_USERNAME or support-bot.username");
        }

        verifyBotCredentials(tokenConfigured, usernameConfigured);
    }

    @PreDestroy
    private void logBotShutdown() {
        log.info("Telegram bot stopped. username={}", getBotUsername());
    }

    private void verifyBotCredentials(boolean tokenConfigured, boolean usernameConfigured) {
        if (!tokenConfigured) {
            log.warn("Skipping Telegram credentials verification because token is missing");
            return;
        }

        try {
            User me = execute(new GetMe());
            log.info("Telegram bot connected successfully: id={} username={}", me.getId(), me.getUserName());
            if (usernameConfigured && me.getUserName() != null && !me.getUserName().equalsIgnoreCase(properties.getUsername())) {
                log.warn("Configured Telegram bot username '{}' does not match API response '{}'",
                        properties.getUsername(), me.getUserName());
            }
        } catch (TelegramApiException e) {
            log.error("Telegram bot credentials verification failed; check token/username and network connectivity", e);
        }
    }

    public void deleteWebhookIfAny() {
        try {
            execute(new DeleteWebhook());
            log.info("Telegram webhook deleted (if it existed). Long polling can receive updates now.");
        } catch (TelegramApiException e) {
            log.warn("Failed to delete Telegram webhook (may be OK if none set).", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.info("Received Telegram update {} (message={}, callbackQuery={}, editedMessage={}, channelPost={})",
                update.getUpdateId(),
                update.getMessage() != null,
                update.getCallbackQuery() != null,
                update.getEditedMessage() != null,
                update.getChannelPost() != null);
        Message editedMessage = update.getEditedMessage();
        if (editedMessage != null) {
            handleEditedClientMessage(editedMessage);
            return;
        }

        Message message = update.getMessage();
        if (message == null) {
            log.debug("Skipping update {} without message payload", update.getUpdateId());
            return;
        }

        logIncomingMessage(update, message);

        boolean hasMemberEvent = message.getNewChatMembers() != null && !message.getNewChatMembers().isEmpty();
        if (!message.hasText()
                && !message.hasDocument()
                && !message.hasPhoto()
                && !message.hasVideo()
                && !message.hasVoice()
                && !message.hasAudio()
                && !message.hasAnimation()
                && message.getSticker() == null
                && message.getVideoNote() == null
                && !hasMemberEvent) {
            log.info("Ignoring update {} from chat {} user {}: unsupported message payload",
                    update.getUpdateId(),
                    message.getChatId(),
                    message.getFrom() != null ? message.getFrom().getId() : null);
            return;
        }

        if (handleOperatorMessage(message)) {
            log.info("Handled operator message from chat {} in update {}", message.getChatId(), update.getUpdateId());
            return;
        }

        Channel channel = getChannel();
        if (hasMemberEvent && handleSupportChatAutoRegistration(message, channel)) {
            log.info("Handled support chat auto-registration for chat {}", message.getChatId());
            return;
        }
        long userId = Optional.ofNullable(message.getFrom()).map(User::getId).orElse(0L);
        BlacklistService.BlacklistStatus status = blacklistService.getStatus(userId);
        if (status.blacklisted()) {
            log.info("Blocked message from blacklisted user {} in update {}", userId, update.getUpdateId());
            handleBlacklistedUser(message, status);
            return;
        }

        ConversationSession session = conversations.get(userId);
        if (message.hasText() && handleSupportChatConfirm(message, channel)) {
            log.info("Handled support chat confirmation for chat {}", message.getChatId());
            return;
        }
        if (message.hasText() && tryHandleFeedback(message, channel)) {
            log.info("Handled rating feedback from user {} in update {}", userId, update.getUpdateId());
            return;
        }
        if (message.hasText()) {
            if (isMyTicketsCommand(message.getText())) {
                log.info("Received my tickets command from user {} in update {}", userId, update.getUpdateId());
                handleMyTickets(message);
            } else if ("/start".equalsIgnoreCase(message.getText())) {
                log.info("Received /start from user {} in update {}", userId, update.getUpdateId());
                startConversation(message, session, channel);
            } else if ("/cancel".equalsIgnoreCase(message.getText())) {
                log.info("Received /cancel from user {} in update {}", userId, update.getUpdateId());
                cancelConversation(message);
            } else if (session != null) {
                log.info("Received conversation answer from user {} in update {}", userId, update.getUpdateId());
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
            log.info("Received /unblock from user {}", message.getFrom() != null ? message.getFrom().getId() : null);
            requestUnblock(message);
        } else if (isMyTicketsCommand(text)) {
            handleMyTickets(message);
        } else if (!handleActiveTextMessage(message)) {
            log.info("Ignoring text message from user {}: {}",
                    message.getFrom() != null ? message.getFrom().getId() : null,
                    summarizeText(text));
        }
    }

    private boolean handleSupportChatAutoRegistration(Message message, Channel channel) {
        if (!isGroupChat(message)) {
            return false;
        }
        if (channel == null) {
            return false;
        }
        if (message.getNewChatMembers() == null || message.getNewChatMembers().isEmpty()) {
            return false;
        }
        String botUsername = getBotUsername();
        boolean botAdded = message.getNewChatMembers().stream()
                .filter(Objects::nonNull)
                .anyMatch(member -> {
                    String username = member.getUserName();
                    return username != null && botUsername != null && username.equalsIgnoreCase(botUsername);
                });
        if (!botAdded) {
            return false;
        }
        if (!isSenderAdmin(message)) {
            log.warn("Skipping support chat auto-registration: user {} is not an admin of chat {}",
                    message.getFrom() != null ? message.getFrom().getId() : null,
                    message.getChatId());
            return false;
        }
        String currentSupportChatId = channel.getSupportChatId();
        String newSupportChatId = String.valueOf(message.getChatId());
        if (currentSupportChatId != null && !currentSupportChatId.isBlank()) {
            log.info("Support chat already configured ({}); skipping auto-registration for chat {}",
                    currentSupportChatId,
                    newSupportChatId);
            return false;
        }
        channelService.updateSupportChatId(channel, newSupportChatId);
        cachedChannel = channel;
        sendSupportChatConfirmation(message.getChatId(), "Группа привязана автоматически. Уведомления будут приходить сюда.");
        return true;
    }

    private boolean handleSupportChatConfirm(Message message, Channel channel) {
        if (!message.hasText()) {
            return false;
        }
        String command = message.getText().trim();
        if (!isConfirmCommand(command)) {
            return false;
        }
        if (!isGroupChat(message)) {
            sendSupportChatConfirmation(message.getChatId(), "Команда /confirm работает только в группе, где должен быть бот.");
            return true;
        }
        if (!isSenderAdmin(message)) {
            sendSupportChatConfirmation(message.getChatId(), "Только администратор может подтвердить группу для уведомлений.");
            return true;
        }
        String newSupportChatId = String.valueOf(message.getChatId());
        String currentSupportChatId = channel.getSupportChatId();
        if (newSupportChatId.equals(currentSupportChatId)) {
            sendSupportChatConfirmation(message.getChatId(), "Эта группа уже привязана к уведомлениям.");
            return true;
        }
        channelService.updateSupportChatId(channel, newSupportChatId);
        cachedChannel = channel;
        sendSupportChatConfirmation(message.getChatId(), "Группа подтверждена. Уведомления будут приходить сюда.");
        return true;
    }

    private boolean isConfirmCommand(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String candidate = text.trim().split("\\s+", 2)[0];
        if (!candidate.startsWith("/confirm")) {
            return false;
        }
        int atIndex = candidate.indexOf('@');
        if (atIndex == -1) {
            return "/confirm".equalsIgnoreCase(candidate);
        }
        String command = candidate.substring(0, atIndex);
        String botName = candidate.substring(atIndex + 1);
        String username = getBotUsername();
        return "/confirm".equalsIgnoreCase(command) && username != null && botName.equalsIgnoreCase(username);
    }

    private boolean isGroupChat(Message message) {
        if (message == null || message.getChat() == null) {
            return false;
        }
        Chat chat = message.getChat();
        return chat.isGroupChat() || chat.isSuperGroupChat();
    }

    private boolean isSenderAdmin(Message message) {
        if (message == null || message.getFrom() == null) {
            return false;
        }
        if (message.getChat() == null) {
            return false;
        }
        try {
            ChatMember member = execute(new GetChatMember(
                    message.getChatId().toString(),
                    message.getFrom().getId().toString()));
            if (member == null) {
                return false;
            }
            String status = member.getStatus();
            return "creator".equalsIgnoreCase(status) || "administrator".equalsIgnoreCase(status);
        } catch (TelegramApiException e) {
            log.warn("Failed to validate admin status for user {} in chat {}",
                    message.getFrom().getId(),
                    message.getChatId(),
                    e);
            return false;
        }
    }

    private void sendSupportChatConfirmation(Long chatId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        SendMessage confirmation = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(confirmation);
        } catch (TelegramApiException e) {
            log.error("Failed to send support chat confirmation", e);
        }
    }

    private boolean handleOperatorMessage(Message message) {
        Long configuredChannelId = properties.getChannelId();
        if (configuredChannelId == null) {
            return false;
        }
        long chatId = message.getChatId();
        if (chatId != configuredChannelId) {
            return false;
        }

        String operatorText = Optional.ofNullable(message.getText()).orElse("").trim();
        TicketReference ticketReference = resolveTicketReference(message, operatorText);
        if (ticketReference.ticketId == null || ticketReference.outboundText.isBlank()) {
            return false;
        }

        log.info("Operator reply resolved for ticket {} from chat {}", ticketReference.ticketId, chatId);

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
        if (isClosedStatus(ticket.status())) {
            ticketService.reopenTicket(ticket.ticketId());
        }
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
            ticketService.registerActivity(ticket.ticketId(), operatorUsername(message));
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

    private boolean isClosedStatus(String status) {
        if (status == null) {
            return false;
        }
        return status.equalsIgnoreCase("closed") || status.equalsIgnoreCase("resolved");
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

        log.info("Processing feedback rating {} from user {}", text, userId);

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
        String response = botSettingsService.ratingResponseFor(settings, rating).orElse("Спасибо за оценку!");
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
            } else {
                handleActiveAttachment(message, "document", stored.toString(), message.getCaption());
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
            } else {
                handleActiveAttachment(message, "photo", stored.toString(), message.getCaption());
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
            } else {
                handleActiveAttachment(message, "video", stored.toString(), message.getCaption());
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
            } else {
                handleActiveAttachment(message, "voice", stored.toString(), message.getCaption());
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
            } else {
                handleActiveAttachment(message, "audio", stored.toString(), message.getCaption());
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
                .orElse("mp4");
        try (InputStream data = fetchFile(animation.getFileId())) {
            Path stored = attachmentService.store(getChannelPublicId(), extension, data);
            log.info("Animation saved for user {} at {}", message.getFrom().getId(), stored);
            if (session != null) {
                session.addAttachment(stored);
                session.addHistoryEvent(message, "animation", stored.toString());
            } else {
                handleActiveAttachment(message, "animation", stored.toString(), message.getCaption());
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
            } else {
                handleActiveAttachment(message, "sticker", stored.toString(), null);
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
            } else {
                handleActiveAttachment(message, "video_note", stored.toString(), message.getCaption());
            }
        } catch (IOException | TelegramApiException e) {
            log.error("Failed to store video note", e);
        }
    }

    private boolean handleActiveTextMessage(Message message) {
        String text = message.getText();
        if (text == null || text.isBlank()) {
            return false;
        }
        return handleActiveTicketMessage(message, text, "text", null);
    }

    private void handleActiveAttachment(Message message, String messageType, String attachmentPath, String caption) {
        handleActiveTicketMessage(message, caption, messageType, attachmentPath);
    }

    private boolean handleActiveTicketMessage(Message message, String text, String messageType, String attachmentPath) {
        Optional<String> activeTicketId = resolveActiveTicketId(message);
        if (activeTicketId.isEmpty()) {
            notifyNoActiveDialog(message);
            return true;
        }
        String ticketId = activeTicketId.get();
        String username = Optional.ofNullable(message.getFrom()).map(User::getUserName).orElse(null);
        Long userId = Optional.ofNullable(message.getFrom()).map(User::getId).orElse(null);
        Optional<TicketService.TicketWithUser> ticketDetails = ticketService.findByTicketId(ticketId);
        if (ticketDetails.isEmpty()) {
            notifyNoActiveDialog(message);
            return true;
        }
        if (isClosedStatus(ticketDetails.get().status())) {
            notifyClosedDialog(message);
            return true;
        }
        log.info("Active ticket message received: ticketId={} userId={} username={} messageType={} telegramMessageId={} attachment={}",
                ticketId,
                userId,
                username,
                messageType,
                message.getMessageId(),
                attachmentPath);
        chatHistoryService.storeUserMessage(
                userId,
                message.getMessageId() != null ? message.getMessageId().longValue() : null,
                text,
                getChannel(),
                ticketId,
                messageType,
                attachmentPath,
                message.getReplyToMessage() != null && message.getReplyToMessage().getMessageId() != null
                        ? message.getReplyToMessage().getMessageId().longValue()
                        : null,
                resolveForwardedFrom(message)
        );
        log.info("Stored client message in history: ticketId={} userId={} messageType={} attachment={}",
                ticketId,
                userId,
                messageType,
                attachmentPath);
        ticketService.registerActivity(ticketId, username != null ? username : (userId != null ? userId.toString() : null));
        relayActiveMessageToOperators(ticketId, messageType, text, attachmentPath, username, userId);
        return true;
    }

    private void notifyNoActiveDialog(Message message) {
        if (message == null) {
            return;
        }
        SendMessage warning = SendMessage.builder()
                .chatId(message.getChatId())
                .text("Активного диалога нет. Чтобы создать новое обращение, нажмите /start.")
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(warning);
        } catch (TelegramApiException e) {
            log.error("Failed to notify about missing active dialog", e);
        }
    }

    private void notifyClosedDialog(Message message) {
        if (message == null) {
            return;
        }
        SendMessage warning = SendMessage.builder()
                .chatId(message.getChatId())
                .text("Диалог закрыт. Оператор сможет открыть его снова, после этого вы сможете продолжить переписку.")
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(warning);
        } catch (TelegramApiException e) {
            log.error("Failed to notify about closed dialog", e);
        }
    }

    private void handleEditedClientMessage(Message editedMessage) {
        if (editedMessage == null || editedMessage.getMessageId() == null) {
            return;
        }
        Long chatId = editedMessage.getChatId();
        if (chatId == null || chatId.equals(properties.getChannelId())) {
            return;
        }
        String text = editedMessage.getText();
        if (text == null) {
            return;
        }
        Channel channel = getChannel();
        Long channelId = channel != null ? channel.getId() : null;
        if (channelId == null) {
            return;
        }
        boolean updated = chatHistoryService.markClientMessageEdited(channelId, editedMessage.getMessageId().longValue(), text);
        if (updated) {
            log.info("Updated edited client message in history: telegramMessageId={} chatId={}", editedMessage.getMessageId(), chatId);
        }
    }

    private String resolveForwardedFrom(Message message) {
        if (message == null) return null;
        if (message.getForwardFrom() != null) {
            User from = message.getForwardFrom();
            if (from.getUserName() != null && !from.getUserName().isBlank()) {
                return "@" + from.getUserName();
            }
            String fullName = ((from.getFirstName() != null ? from.getFirstName() : "") + " "
                    + (from.getLastName() != null ? from.getLastName() : "")).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
        }
        if (message.getForwardSenderName() != null && !message.getForwardSenderName().isBlank()) {
            return message.getForwardSenderName();
        }
        if (message.getForwardFromChat() != null && message.getForwardFromChat().getTitle() != null) {
            return message.getForwardFromChat().getTitle();
        }
        return null;
    }

    private Optional<String> resolveActiveTicketId(Message message) {
        User user = message.getFrom();
        Long userId = user != null ? user.getId() : null;
        String username = user != null ? user.getUserName() : null;
        return ticketService.findActiveTicketForUser(userId, username).map(TicketActive::getTicketId);
    }

    private void relayActiveMessageToOperators(String ticketId,
                                               String messageType,
                                               String text,
                                               String attachmentPath,
                                               String username,
                                               Long userId) {
        Long channelId = properties.getChannelId();
        if (channelId == null || channelId <= 0) {
            return;
        }
        log.info("Relaying client message to operator chat {}: ticketId={} userId={} messageType={} attachment={}",
                channelId,
                ticketId,
                userId,
                messageType,
                attachmentPath);
        String senderLabel = username != null && !username.isBlank()
                ? "@" + username
                : (userId != null ? String.valueOf(userId) : "клиент");
        StringBuilder builder = new StringBuilder();
        builder.append("Новый ответ клиента ").append(senderLabel).append("\n");
        builder.append("ID заявки: #").append(ticketId).append("\n");
        if (text != null && !text.isBlank()) {
            builder.append(text);
        } else {
            builder.append("[").append(messageType).append("]");
        }
        if (attachmentPath != null && !attachmentPath.isBlank()) {
            builder.append("\nВложение: ").append(attachmentPath);
        }
        SendMessage toChannel = SendMessage.builder()
                .chatId(channelId)
                .text(builder.toString())
                .build();
        try {
            execute(toChannel);
        } catch (TelegramApiException e) {
            log.error("Failed to relay active ticket message to operator channel", e);
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

    public boolean sendDirectMessage(Long chatId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return false;
        }
        log.info("Sending direct Telegram message to chat {}: {}", chatId, summarizeText(text));
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(message);
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send direct Telegram message to chat {}", chatId, e);
            return false;
        }
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
            log.info("User {} attempted to start a new conversation while one is active", existing.userId());
            SendMessage warning = SendMessage.builder()
                    .chatId(message.getChatId())
                    .text("У вас уже есть активная заявка. Отправьте /cancel, чтобы начать заново.")
                    .replyMarkup(new ReplyKeyboardRemove(true))
                    .build();
            try {
                log.info("Sending active conversation warning to user {}", existing.userId());
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
        log.info("Starting conversation for user {} chat {} with {} questions",
                session.userId(),
                session.chatId(),
                flow.size());
        ticketService.findLastMessage(message.getFrom().getId())
                .ifPresent(last -> session.enableReusePrompt(Map.of(
                        "business", Optional.ofNullable(last.getBusiness()).orElse(""),
                        "location_type", Optional.ofNullable(last.getLocationType()).orElse(""),
                        "city", Optional.ofNullable(last.getCity()).orElse(""),
                        "location_name", Optional.ofNullable(last.getLocationName()).orElse("")
                )));
        conversations.put(message.getFrom().getId(), session);
        log.info("Conversation initialized for user {} - sending first prompt", session.userId());
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

        if (BACK_BUTTON.equalsIgnoreCase(Optional.ofNullable(message.getText()).orElse("").trim())) {
            if (session.stepBack()) {
                askCurrentQuestion(session);
            }
            return;
        }

        QuestionFlowItemDto current = session.currentQuestion();
        if (isPresetQuestion(current)) {
            List<String> options = resolvePresetOptions(current, session.answers());
            String answer = Optional.ofNullable(message.getText()).orElse("");
            if (options.isEmpty()) {
                SendMessage retry = SendMessage.builder()
                        .chatId(session.chatId())
                        .text("Сейчас нет доступных вариантов для выбора. Обратитесь к администратору.")
                        .replyMarkup(new ReplyKeyboardRemove(true))
                        .build();
                try {
                    execute(retry);
                } catch (TelegramApiException e) {
                    log.error("Failed to notify about missing preset options", e);
                }
                return;
            }
            if (!options.contains(answer)) {
                SendMessage retry = SendMessage.builder()
                        .chatId(session.chatId())
                        .text("Выберите вариант кнопкой.")
                        .replyMarkup(keyboardMarkup(options, session.canGoBack()))
                        .build();
                try {
                    execute(retry);
                } catch (TelegramApiException e) {
                    log.error("Failed to resend preset options", e);
                }
                return;
            }
        }

        session.recordAnswer(message);
        log.info("Recorded answer for user {} at step {}", session.userId(), session.currentIndex);
        if (session.isComplete()) {
            finalizeConversation(session);
        } else {
            askCurrentQuestion(session);
        }
    }

    private void askCurrentQuestion(ConversationSession session) {
        if (session.awaitingReuseDecision()) {
            log.info("Prompting reuse decision for user {}", session.userId());
            SendMessage prompt = SendMessage.builder()
                    .chatId(session.chatId())
                    .text(session.reusePrompt())
                    .replyMarkup(keyboardMarkup(List.of("Да", "Нет"), false))
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
            log.warn("No current question available for user {}", session.userId());
            return;
        }
        log.info("Prompting question {} for user {}", current.getId(), session.userId());
        SendMessage.SendMessageBuilder promptBuilder = SendMessage.builder()
                .chatId(session.chatId())
                .text(current.getText());
        boolean includeBack = session.canGoBack();
        if (isPresetQuestion(current)) {
            List<String> options = resolvePresetOptions(current, session.answers());
            if (!options.isEmpty()) {
                promptBuilder.replyMarkup(keyboardMarkup(options, includeBack));
            } else {
                promptBuilder.replyMarkup(new ReplyKeyboardRemove(true));
            }
        } else {
            if (includeBack) {
                promptBuilder.replyMarkup(keyboardMarkup(List.of(), true));
            } else {
                promptBuilder.replyMarkup(new ReplyKeyboardRemove(true));
            }
        }
        SendMessage prompt = promptBuilder.build();
        try {
            execute(prompt);
        } catch (TelegramApiException e) {
            log.error("Failed to send conversation prompt", e);
        }
    }

    private boolean isMyTicketsCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase().replaceAll("\\s+", " ");
        return "мои заявки".equals(normalized);
    }

    private void handleMyTickets(Message message) {
        if (message.getFrom() == null) {
            return;
        }
        List<TicketService.TicketSummary> tickets = ticketService.findRecentTicketsForUser(message.getFrom().getId(), 10);
        String response = formatTicketsResponse(tickets);
        SendMessage reply = SendMessage.builder()
                .chatId(message.getChatId())
                .text(response)
                .replyMarkup(new ReplyKeyboardRemove(true))
                .build();
        try {
            execute(reply);
        } catch (TelegramApiException e) {
            log.error("Failed to send my tickets response", e);
        }
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

    private boolean isPresetQuestion(QuestionFlowItemDto current) {
        if (current == null) {
            return false;
        }
        if ("preset".equalsIgnoreCase(current.getType())) {
            return true;
        }
        return current.getPreset() != null && current.getPreset().field() != null;
    }

    private ReplyKeyboardMarkup keyboardMarkup(List<String> options, boolean includeBack) {
        List<KeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i += 2) {
            KeyboardRow row = new KeyboardRow();
            row.add(options.get(i));
            if (i + 1 < options.size()) {
                row.add(options.get(i + 1));
            }
            rows.add(row);
        }
        if (includeBack) {
            KeyboardRow backRow = new KeyboardRow();
            backRow.add(BACK_BUTTON);
            rows.add(backRow);
        }
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        markup.setSelective(true);
        return markup;
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
        JsonNode locations = sharedConfigService.loadLocations();
        if (locations == null || locations.isNull()) {
            cachedLocationTree = new LinkedHashMap<>();
            return cachedLocationTree;
        }
        JsonNode treeNode = locations.get("tree");
        Map<String, Object> resolved = objectMapper.convertValue(
                treeNode != null && !treeNode.isNull() ? treeNode : locations,
                new TypeReference<>() {}
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

    private void finalizeConversation(ConversationSession session) {
        conversations.remove(session.userId());
        Channel channel = getChannel();
        String username = Optional.ofNullable(session.user()).map(User::getUserName).orElse(null);
        TicketService.TicketCreationResult ticket = ticketService.createTicket(session.userId(), username, session.answers(), channel);
        log.info("Created ticket {} for user {} with {} attachments",
                ticket.ticketId(),
                session.userId(),
                session.attachments().size());
        String summary = session.buildSummary(ticket.ticketId());

        for (HistoryEvent event : session.historyEvents()) {
            chatHistoryService.storeEntry(
                    event.userId(),
                    event.telegramMessageId(),
                    channel,
                    ticket.ticketId(),
                    event.text(),
                    event.messageType(),
                    event.attachmentPath(),
                    null,
                    null
            );
        }

        ticketService.registerActivity(ticket.ticketId(), Optional.ofNullable(session.user()).map(User::getUserName).orElse(null));

        if (properties.getChannelId() != null && properties.getChannelId() > 0) {
            SendMessage toChannel = SendMessage.builder()
                    .chatId(properties.getChannelId())
                    .text(summary)
                    .build();
            try {
                execute(toChannel);
            } catch (TelegramApiException e) {
                log.error("Failed to send ticket to operator channel", e);
            }
        }

        String requestNumber = ticket.groupMessageId() != null ? ticket.groupMessageId().toString() : ticket.ticketId();
        SendMessage confirmation = SendMessage.builder()
                .chatId(session.chatId())
                .text("Спасибо! Ваше обращение №" + requestNumber + " отправлено оператору. Мы свяжемся с вами после обработки.")
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
            log.info("Cancel ignored: no active conversation for user {}",
                    message.getFrom() != null ? message.getFrom().getId() : null);
            return;
        }
        log.info("Conversation cancelled for user {}", session.userId());
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
            String answerKey = answerKeyFor(current);
            if (answerKey != null) {
                answers.put(answerKey, answer);
            }
            currentIndex += 1;
            addHistoryEvent(message, "text", null);
        }

        boolean isComplete() {
            return currentIndex >= flow.size();
        }

        boolean isLastQuestion() {
            return currentIndex == flow.size() - 1;
        }

        boolean canGoBack() {
            return currentIndex > 0;
        }

        boolean stepBack() {
            if (currentIndex <= 0) {
                return false;
            }
            currentIndex -= 1;
            QuestionFlowItemDto previous = flow.get(currentIndex);
            String answerKey = answerKeyFor(previous);
            if (answerKey != null) {
                answers.remove(answerKey);
            }
            return true;
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
                String answerKey = answerKeyFor(item);
                String answer = answerKey != null ? answers.getOrDefault(answerKey, "") : "";
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

        private String answerKeyFor(QuestionFlowItemDto item) {
            if (item == null) {
                return null;
            }
            if (item.getPreset() != null) {
                String field = Optional.ofNullable(item.getPreset().field()).orElse("").trim();
                if (!field.isEmpty()) {
                    return field;
                }
            }
            return item.getId();
        }
    }

    private record TicketReference(String ticketId, String outboundText, Long replyToTelegramId) {}

    private record HistoryEvent(Long userId, Long telegramMessageId, String text, String messageType, String attachmentPath) {}

    private String operatorUsername(Message message) {
        return Optional.ofNullable(message.getFrom())
                .map(User::getUserName)
                .orElse(null);
    }

    private void logIncomingMessage(Update update, Message message) {
        log.info("Incoming update {} chat={} user={} text={} attachments={}",
                update.getUpdateId(),
                message.getChatId(),
                message.getFrom() != null ? message.getFrom().getId() : null,
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
        List<String> parts = new ArrayList<>();
        if (message.hasDocument()) {
            parts.add("document");
        }
        if (message.hasPhoto()) {
            parts.add("photo(" + message.getPhoto().size() + ")");
        }
        if (message.hasVideo()) {
            parts.add("video");
        }
        if (message.hasVoice()) {
            parts.add("voice");
        }
        if (message.hasAudio()) {
            parts.add("audio");
        }
        if (message.hasAnimation()) {
            parts.add("animation");
        }
        if (message.getSticker() != null) {
            parts.add("sticker");
        }
        if (message.getVideoNote() != null) {
            parts.add("video_note");
        }
        return parts.isEmpty() ? "none" : String.join(",", parts);
    }

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "(missing)";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 8) {
            return "****" + trimmed.length();
        }
        return trimmed.substring(0, 4) + "…" + trimmed.substring(trimmed.length() - 4) + " (" + trimmed.length() + ")";
    }
}
