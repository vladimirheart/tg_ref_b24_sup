package com.example.panel.service;

import com.example.panel.entity.Channel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class DialogReplyService {

    private final DialogReplyTargetService dialogReplyTargetService;
    private final DialogReplyTransportService dialogReplyTransportService;
    private final DialogResponsibilityService dialogResponsibilityService;

    public DialogReplyService(DialogReplyTargetService dialogReplyTargetService,
                              DialogReplyTransportService dialogReplyTransportService,
                              DialogResponsibilityService dialogResponsibilityService) {
        this.dialogReplyTargetService = dialogReplyTargetService;
        this.dialogReplyTransportService = dialogReplyTransportService;
        this.dialogResponsibilityService = dialogResponsibilityService;
    }

    public DialogReplyResult sendReply(String ticketId, String message, Long replyToTelegramId, String operator) {
        return sendReply(ticketId, message, replyToTelegramId, operator, "operator");
    }

    public DialogReplyResult sendReply(String ticketId,
                                       String message,
                                       Long replyToTelegramId,
                                       String operator,
                                       String sender) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(message)) {
            return DialogReplyResult.error("Сообщение не может быть пустым.");
        }
        Optional<DialogReplyTarget> targetOpt = dialogReplyTargetService.loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = dialogReplyTransportService.loadChannel(target.channelId()).orElse(null);
        if (channel == null) {
            return DialogReplyResult.error("Канал для отправки сообщения не найден.");
        }
        if (dialogReplyTargetService.hasWebFormSession(ticketId)) {
            String timestamp = dialogReplyTargetService.logOutgoingMessage(
                    target,
                    ticketId,
                    message,
                    "operator_message",
                    null,
                    replyToTelegramId,
                    sender
            );
            dialogReplyTargetService.touchTicketActivity(ticketId, target.userId());
            dialogResponsibilityService.assignResponsibleIfMissing(ticketId, operator);
            return DialogReplyResult.success(timestamp, null);
        }
        if (!StringUtils.hasText(channel.getToken())) {
            return DialogReplyResult.error("Не задан токен бота для канала.");
        }

        DialogReplyTransportService.DialogReplyTransportResult transportResult =
                dialogReplyTransportService.sendText(channel, target.userId(), message, replyToTelegramId);
        if (transportResult.error() != null) {
            return DialogReplyResult.error(transportResult.error());
        }

        String timestamp = dialogReplyTargetService.logOutgoingMessage(
                target,
                ticketId,
                message,
                "operator_message",
                transportResult.telegramMessageId(),
                replyToTelegramId,
                sender
        );
        dialogReplyTargetService.touchTicketActivity(ticketId, target.userId());
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, operator);
        return DialogReplyResult.success(timestamp, transportResult.telegramMessageId());
    }

    public DialogReplyResult editOperatorMessage(String ticketId, Long telegramMessageId, String message, String operator) {
        if (!StringUtils.hasText(ticketId) || telegramMessageId == null || !StringUtils.hasText(message)) {
            return DialogReplyResult.error("Некорректные параметры редактирования.");
        }
        Optional<DialogReplyTarget> targetOpt = dialogReplyTargetService.loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = dialogReplyTransportService.loadChannel(target.channelId()).orElse(null);
        if (channel == null || !StringUtils.hasText(channel.getToken())) {
            return DialogReplyResult.error("Канал Telegram не найден.");
        }
        String transportError = dialogReplyTransportService.editTelegramMessage(channel, target.userId(), telegramMessageId, message);
        if (transportError != null) {
            return DialogReplyResult.error(transportError);
        }
        int updated = dialogReplyTargetService.markOperatorMessageEdited(ticketId, telegramMessageId, message);
        if (updated == 0) {
            return DialogReplyResult.error("Сообщение оператора не найдено.");
        }
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, operator);
        return DialogReplyResult.success(OffsetDateTime.now().toString(), telegramMessageId);
    }

    public DialogReplyResult deleteOperatorMessage(String ticketId, Long telegramMessageId, String operator) {
        if (!StringUtils.hasText(ticketId) || telegramMessageId == null) {
            return DialogReplyResult.error("Некорректные параметры удаления.");
        }
        Optional<DialogReplyTarget> targetOpt = dialogReplyTargetService.loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = dialogReplyTransportService.loadChannel(target.channelId()).orElse(null);
        if (channel == null || !StringUtils.hasText(channel.getToken())) {
            return DialogReplyResult.error("Канал Telegram не найден.");
        }
        String transportError = dialogReplyTransportService.deleteTelegramMessage(channel, target.userId(), telegramMessageId);
        if (transportError != null) {
            return DialogReplyResult.error(transportError);
        }
        int updated = dialogReplyTargetService.markOperatorMessageDeleted(ticketId, telegramMessageId);
        if (updated == 0) {
            return DialogReplyResult.error("Сообщение оператора не найдено.");
        }
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, operator);
        return DialogReplyResult.success(OffsetDateTime.now().toString(), telegramMessageId);
    }

    public DialogMediaReplyResult sendMediaReply(String ticketId,
                                                 MultipartFile file,
                                                 String caption,
                                                 String operator,
                                                 String storedName,
                                                 String originalName) {
        if (!StringUtils.hasText(ticketId) || file == null || file.isEmpty()) {
            return DialogMediaReplyResult.error("Файл не выбран.");
        }
        Optional<DialogReplyTarget> targetOpt = dialogReplyTargetService.loadReplyTarget(ticketId);
        if (targetOpt.isEmpty()) {
            return DialogMediaReplyResult.error("Не удалось определить получателя сообщения.");
        }
        DialogReplyTarget target = targetOpt.get();
        Channel channel = dialogReplyTransportService.loadChannel(target.channelId()).orElse(null);
        if (channel == null) {
            return DialogMediaReplyResult.error("Канал для отправки сообщения не найден.");
        }
        if (dialogReplyTargetService.hasWebFormSession(ticketId)) {
            return DialogMediaReplyResult.error("Для внешней формы доступны только текстовые ответы в общем окне диалога.");
        }
        String platform = channel.getPlatform() != null ? channel.getPlatform().trim().toLowerCase() : "telegram";
        if (!StringUtils.hasText(channel.getToken())) {
            return DialogMediaReplyResult.error(
                    "max".equals(platform) ? "Не задан токен MAX-бота для канала." : "Не задан токен Telegram-бота для канала."
            );
        }

        DialogReplyTransportService.DialogReplyTransportResult transportResult =
                dialogReplyTransportService.sendMedia(channel, target.userId(), file, caption, originalName);
        if (transportResult.error() != null) {
            return DialogMediaReplyResult.error(transportResult.error());
        }

        String messageType = DialogReplyTransportService.resolveMessageType(file.getContentType(), originalName);
        String timestamp = dialogReplyTargetService.logOutgoingMediaMessage(
                target,
                ticketId,
                caption,
                storedName,
                messageType,
                transportResult.telegramMessageId()
        );
        dialogReplyTargetService.touchTicketActivity(ticketId, target.userId());
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, operator);
        return DialogMediaReplyResult.success(timestamp, transportResult.telegramMessageId(), storedName, messageType, caption);
    }

    public record DialogReplyResult(boolean success, String error, String timestamp, Long telegramMessageId) {
        public static DialogReplyResult error(String error) {
            return new DialogReplyResult(false, error, null, null);
        }

        public static DialogReplyResult success(String timestamp, Long telegramMessageId) {
            return new DialogReplyResult(true, null, timestamp, telegramMessageId);
        }
    }

    public record DialogMediaReplyResult(boolean success,
                                         String error,
                                         String timestamp,
                                         Long telegramMessageId,
                                         String storedName,
                                         String messageType,
                                         String message) {
        static DialogMediaReplyResult error(String error) {
            return new DialogMediaReplyResult(false, error, null, null, null, null, null);
        }

        static DialogMediaReplyResult success(String timestamp,
                                              Long telegramMessageId,
                                              String storedName,
                                              String messageType,
                                              String message) {
            return new DialogMediaReplyResult(true, null, timestamp, telegramMessageId, storedName, messageType, message);
        }
    }
}
