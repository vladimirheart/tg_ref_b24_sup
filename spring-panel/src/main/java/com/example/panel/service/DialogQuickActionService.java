package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.storage.AttachmentService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogQuickActionService {

    private final DialogService dialogService;
    private final DialogReplyService dialogReplyService;
    private final DialogNotificationService dialogNotificationService;
    private final DialogAiAssistantService dialogAiAssistantService;
    private final NotificationService notificationService;
    private final AttachmentService attachmentService;

    public DialogQuickActionService(DialogService dialogService,
                                    DialogReplyService dialogReplyService,
                                    DialogNotificationService dialogNotificationService,
                                    DialogAiAssistantService dialogAiAssistantService,
                                    NotificationService notificationService,
                                    AttachmentService attachmentService) {
        this.dialogService = dialogService;
        this.dialogReplyService = dialogReplyService;
        this.dialogNotificationService = dialogNotificationService;
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.notificationService = notificationService;
        this.attachmentService = attachmentService;
    }

    public DialogReplyService.DialogReplyResult sendReply(String ticketId,
                                                          String message,
                                                          Long replyToTelegramId,
                                                          String operator) {
        dialogAiAssistantService.clearProcessing(ticketId, "operator_reply", null);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(ticketId, message, replyToTelegramId, operator);
        if (result.success()) {
            dialogAiAssistantService.registerOperatorReply(ticketId, message, operator);
            notificationService.notifyDialogParticipants(
                    ticketId,
                    "Новое сообщение в обращении " + ticketId,
                    "/dialogs?ticketId=" + ticketId,
                    operator
            );
        }
        return result;
    }

    public DialogReplyService.DialogReplyResult editReply(String ticketId,
                                                          Long telegramMessageId,
                                                          String message,
                                                          String operator) {
        DialogReplyService.DialogReplyResult result = dialogReplyService.editOperatorMessage(
                ticketId,
                telegramMessageId,
                message,
                operator
        );
        if (result.success()) {
            notificationService.notifyDialogParticipants(
                    ticketId,
                    "Сообщение в обращении " + ticketId + " было отредактировано",
                    "/dialogs?ticketId=" + ticketId,
                    operator
            );
        }
        return result;
    }

    public DialogReplyService.DialogReplyResult deleteReply(String ticketId,
                                                            Long telegramMessageId,
                                                            String operator) {
        DialogReplyService.DialogReplyResult result = dialogReplyService.deleteOperatorMessage(
                ticketId,
                telegramMessageId,
                operator
        );
        if (result.success()) {
            notificationService.notifyDialogParticipants(
                    ticketId,
                    "Сообщение в обращении " + ticketId + " было удалено",
                    "/dialogs?ticketId=" + ticketId,
                    operator
            );
        }
        return result;
    }

    public Map<String, Object> sendMediaReply(String ticketId,
                                              MultipartFile file,
                                              String message,
                                              String operator,
                                              Authentication authentication) throws IOException {
        dialogAiAssistantService.clearProcessing(ticketId, "operator_reply_media", null);
        var metadata = attachmentService.storeTicketAttachment(authentication, ticketId, file);
        var result = dialogReplyService.sendMediaReply(ticketId, file, message, operator, metadata.storedName(), metadata.originalName());
        if (!result.success()) {
            return Map.of(
                    "success", false,
                    "error", result.error()
            );
        }
        dialogAiAssistantService.registerOperatorReply(ticketId, message, operator);
        String attachmentUrl = "/api/attachments/tickets/" + ticketId + "/" + result.storedName();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("timestamp", result.timestamp());
        response.put("telegramMessageId", result.telegramMessageId());
        response.put("responsible", operator);
        response.put("attachment", attachmentUrl);
        response.put("messageType", result.messageType());
        response.put("message", result.message());
        notificationService.notifyDialogParticipants(
                ticketId,
                "Новое медиа-сообщение в обращении " + ticketId,
                "/dialogs?ticketId=" + ticketId,
                operator
        );
        return response;
    }

    public DialogService.ResolveResult resolveTicket(String ticketId,
                                                     String operator,
                                                     List<String> categories) {
        DialogService.ResolveResult result = dialogService.resolveTicket(ticketId, operator, categories);
        if (result.updated()) {
            dialogAiAssistantService.clearProcessing(ticketId, "resolved", null);
            dialogNotificationService.notifyResolved(ticketId);
            notificationService.notifyDialogParticipants(
                    ticketId,
                    "Обращение " + ticketId + " закрыто",
                    "/dialogs?ticketId=" + ticketId,
                    operator
            );
        }
        return result;
    }

    public DialogService.ResolveResult reopenTicket(String ticketId, String operator) {
        DialogService.ResolveResult result = dialogService.reopenTicket(ticketId, operator);
        if (result.updated()) {
            dialogAiAssistantService.clearProcessing(ticketId, "reopened", null);
            dialogNotificationService.notifyReopened(ticketId);
            notificationService.notifyDialogParticipants(
                    ticketId,
                    "Обращение " + ticketId + " снова открыто",
                    "/dialogs?ticketId=" + ticketId,
                    operator
            );
        }
        return result;
    }

    public void updateCategories(String ticketId,
                                 String operator,
                                 List<String> categories) {
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        dialogService.setTicketCategories(ticketId, categories);
        notificationService.notifyDialogParticipants(
                ticketId,
                "В обращении " + ticketId + " обновлены категории",
                "/dialogs?ticketId=" + ticketId,
                operator
        );
    }

    public Optional<String> takeTicket(String ticketId, String operator) {
        Optional<DialogListItem> dialog = dialogService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            return Optional.empty();
        }
        dialogService.assignResponsibleIfMissingOrRedirected(ticketId, operator, operator);
        dialogAiAssistantService.clearProcessing(ticketId, "operator_take", null);
        Optional<DialogListItem> updated = dialogService.findDialog(ticketId, operator);
        String responsible = updated.map(DialogListItem::responsible).orElse(dialog.get().responsible());
        notificationService.notifyDialogParticipants(
                ticketId,
                "Обращение " + ticketId + " взято в работу оператором " + operator,
                "/dialogs?ticketId=" + ticketId,
                operator
        );
        return Optional.ofNullable(responsible != null && !responsible.isBlank() ? responsible : operator);
    }
}
