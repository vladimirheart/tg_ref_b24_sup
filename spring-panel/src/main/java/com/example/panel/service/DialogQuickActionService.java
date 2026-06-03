package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.model.dialog.DialogParticipantDto;
import com.example.panel.storage.AttachmentService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogQuickActionService {

    private static final Logger log = LoggerFactory.getLogger(DialogQuickActionService.class);

    private final DialogTicketLifecycleService dialogTicketLifecycleService;
    private final DialogLookupReadService dialogLookupReadService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogParticipantService dialogParticipantService;
    private final DialogReplyService dialogReplyService;
    private final DialogNotificationService dialogNotificationService;
    private final DialogAiAssistantService dialogAiAssistantService;
    private final NotificationService notificationService;
    private final AttachmentService attachmentService;
    private final ClientBlacklistService clientBlacklistService;

    @Autowired
    public DialogQuickActionService(DialogTicketLifecycleService dialogTicketLifecycleService,
                                    DialogLookupReadService dialogLookupReadService,
                                    DialogResponsibilityService dialogResponsibilityService,
                                    DialogParticipantService dialogParticipantService,
                                    DialogReplyService dialogReplyService,
                                    DialogNotificationService dialogNotificationService,
                                    DialogAiAssistantService dialogAiAssistantService,
                                    NotificationService notificationService,
                                    AttachmentService attachmentService,
                                    ClientBlacklistService clientBlacklistService) {
        this.dialogTicketLifecycleService = dialogTicketLifecycleService;
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogParticipantService = dialogParticipantService;
        this.dialogReplyService = dialogReplyService;
        this.dialogNotificationService = dialogNotificationService;
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.notificationService = notificationService;
        this.attachmentService = attachmentService;
        this.clientBlacklistService = clientBlacklistService;
    }

    DialogQuickActionService(DialogTicketLifecycleService dialogTicketLifecycleService,
                             DialogLookupReadService dialogLookupReadService,
                             DialogResponsibilityService dialogResponsibilityService,
                             DialogParticipantService dialogParticipantService,
                             DialogReplyService dialogReplyService,
                             DialogNotificationService dialogNotificationService,
                             DialogAiAssistantService dialogAiAssistantService,
                             NotificationService notificationService,
                             AttachmentService attachmentService) {
        this(
                dialogTicketLifecycleService,
                dialogLookupReadService,
                dialogResponsibilityService,
                dialogParticipantService,
                dialogReplyService,
                dialogNotificationService,
                dialogAiAssistantService,
                notificationService,
                attachmentService,
                null
        );
    }

    public DialogReplyService.DialogReplyResult sendReply(String ticketId,
                                                          String message,
                                                          Long replyToTelegramId,
                                                          String operator) {
        dialogAiAssistantService.clearProcessing(ticketId, "operator_reply", null);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(ticketId, message, replyToTelegramId, operator);
        if (result.success()) {
            dialogAiAssistantService.registerOperatorReply(ticketId, message, operator);
            notifyDialogParticipantsSafely(
                    ticketId,
                    "Новое сообщение в обращении " + ticketId,
                    notificationService.buildDialogUrl(ticketId),
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
            notifyDialogParticipantsSafely(
                    ticketId,
                    "Сообщение в обращении " + ticketId + " было отредактировано",
                    notificationService.buildDialogUrl(ticketId),
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
            notifyDialogParticipantsSafely(
                    ticketId,
                    "Сообщение в обращении " + ticketId + " было удалено",
                    notificationService.buildDialogUrl(ticketId),
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
        response.put("responsible", result.responsible());
        response.put("attachment", attachmentUrl);
        response.put("messageType", result.messageType());
        response.put("message", result.message());
        notifyDialogParticipantsSafely(
                ticketId,
                "Новое медиа-сообщение в обращении " + ticketId,
                notificationService.buildDialogUrl(ticketId),
                operator
        );
        return response;
    }

    public DialogResolveResult resolveTicket(String ticketId,
                                             String operator,
                                             List<String> categories) {
        DialogResolveResult result = dialogTicketLifecycleService.resolveTicket(ticketId, operator, categories);
        if (result.updated()) {
            dialogAiAssistantService.clearProcessing(ticketId, "resolved", null);
            dialogNotificationService.notifyResolved(ticketId);
            notifyDialogParticipantsSafely(
                    ticketId,
                    "Обращение " + ticketId + " закрыто",
                    notificationService.buildDialogUrl(ticketId),
                    operator
            );
        }
        return result;
    }

    public DialogResolveResult reopenTicket(String ticketId, String operator) {
        DialogResolveResult result = dialogTicketLifecycleService.reopenTicket(ticketId, operator);
        if (result.updated()) {
            dialogAiAssistantService.clearProcessing(ticketId, "reopened", null);
            dialogNotificationService.notifyReopened(ticketId);
            notifyDialogParticipantsSafely(
                    ticketId,
                    "Обращение " + ticketId + " снова открыто",
                    notificationService.buildDialogUrl(ticketId),
                    operator
            );
        }
        return result;
    }

    public DialogCategoryUpdateResult updateCategories(String ticketId,
                                                       String operator,
                                                       List<String> categories) {
        Optional<DialogListItem> dialog = dialogLookupReadService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            return new DialogCategoryUpdateResult(false, List.of());
        }
        List<String> normalizedCategories = normalizeCategories(categories);
        dialogTicketLifecycleService.setTicketCategories(ticketId, normalizedCategories);
        notifyDialogParticipantsSafely(
                ticketId,
                "В обращении " + ticketId + " обновлены категории",
                notificationService.buildDialogUrl(ticketId),
                operator
        );
        return new DialogCategoryUpdateResult(true, normalizedCategories);
    }

    public DialogSpamResult markClientAsSpam(String ticketId,
                                             String operator,
                                             String reason) {
        Optional<DialogListItem> dialog = dialogLookupReadService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            return new DialogSpamResult(false, false, "Диалог не найден", null, List.of());
        }
        String userId = dialog.get().userId() != null ? String.valueOf(dialog.get().userId()) : null;
        if (!StringUtils.hasText(userId)) {
            return new DialogSpamResult(true, false, "Не удалось определить клиента для блокировки", null, List.of());
        }
        if (clientBlacklistService == null) {
            return new DialogSpamResult(true, false, "Blacklist service is not configured", userId, List.of());
        }

        String normalizedReason = StringUtils.hasText(reason)
                ? reason.trim()
                : "Спам в диалоге " + ticketId;
        ClientBlacklistService.BlacklistMutationResult blockResult = clientBlacklistService.blockClient(
                userId,
                normalizedReason,
                operator,
                false
        );
        if (!blockResult.ok()) {
            return new DialogSpamResult(true, false, blockResult.error(), userId, List.of());
        }

        List<String> categories = mergeCategoriesWithSpam(dialog.get().categories());
        dialogTicketLifecycleService.setTicketCategories(ticketId, categories);
        return new DialogSpamResult(true, true, null, userId, categories);
    }

    public Optional<String> takeTicket(String ticketId, String operator) {
        Optional<DialogListItem> dialog = dialogLookupReadService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            return Optional.empty();
        }
        dialogResponsibilityService.assignResponsibleIfMissingOrRedirected(ticketId, operator, operator);
        dialogParticipantService.removeParticipant(ticketId, operator);
        dialogAiAssistantService.clearProcessing(ticketId, "operator_take", null);
        Optional<DialogListItem> updated = dialogLookupReadService.findDialog(ticketId, operator);
        String responsible = updated.map(DialogListItem::responsible).orElse(dialog.get().responsible());
        notifyDialogParticipantsSafely(
                ticketId,
                "Обращение " + ticketId + " взято в работу оператором " + operator,
                notificationService.buildDialogUrl(ticketId),
                operator
        );
        return Optional.ofNullable(responsible != null && !responsible.isBlank() ? responsible : operator);
    }

    public DialogParticipantMutationResult addParticipant(String ticketId,
                                                          String username,
                                                          String operator) {
        Optional<DialogListItem> dialog = dialogLookupReadService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            return new DialogParticipantMutationResult(false, false, "Диалог не найден", List.of());
        }
        if (isClosedDialog(dialog.get())) {
            return new DialogParticipantMutationResult(true, false, "К закрытому диалогу нельзя добавлять новых участников", dialogParticipantService.loadParticipants(ticketId));
        }
        Optional<DialogOperatorOption> targetOperator = dialogParticipantService.findOperator(username);
        if (targetOperator.isEmpty()) {
            return new DialogParticipantMutationResult(true, false, "Пользователь панели не найден", dialogParticipantService.loadParticipants(ticketId));
        }
        String currentResponsible = dialogResponsibilityService.loadResponsible(ticketId);
        if (sameIdentity(currentResponsible, targetOperator.get().username())) {
            return new DialogParticipantMutationResult(true, false, "Этот пользователь уже назначен ответственным за диалог", dialogParticipantService.loadParticipants(ticketId));
        }
        boolean changed = dialogParticipantService.addParticipant(ticketId, targetOperator.get().username(), operator);
        List<DialogParticipantDto> participants = dialogParticipantService.loadParticipants(ticketId);
        if (changed) {
            notifyDialogParticipantsSafely(
                    ticketId,
                    "К обращению " + ticketId + " подключен оператор " + targetOperator.get().displayLabel(),
                    notificationService.buildDialogUrl(ticketId),
                    operator
            );
        }
        return new DialogParticipantMutationResult(true, changed, null, participants);
    }

    public DialogParticipantMutationResult removeParticipant(String ticketId,
                                                             String username,
                                                             String operator) {
        Optional<DialogListItem> dialog = dialogLookupReadService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            return new DialogParticipantMutationResult(false, false, "Диалог не найден", List.of());
        }
        Optional<DialogOperatorOption> targetOperator = dialogParticipantService.findOperator(username);
        String targetLabel = targetOperator.map(DialogOperatorOption::displayLabel).orElse(username);
        boolean changed = dialogParticipantService.removeParticipant(ticketId, username);
        List<DialogParticipantDto> participants = dialogParticipantService.loadParticipants(ticketId);
        if (changed) {
            notifyDialogParticipantsSafely(
                    ticketId,
                    "Из обращения " + ticketId + " исключен оператор " + targetLabel,
                    notificationService.buildDialogUrl(ticketId),
                    operator
            );
        }
        return new DialogParticipantMutationResult(true, changed, null, participants);
    }

    public DialogReassignResult reassignTicket(String ticketId,
                                               String username,
                                               String operator) {
        Optional<DialogListItem> dialog = dialogLookupReadService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            return new DialogReassignResult(false, "Диалог не найден", null, null, null, List.of());
        }
        if (isClosedDialog(dialog.get())) {
            return new DialogReassignResult(true, "Переадресовать можно только открытый диалог", null, null, null, dialogParticipantService.loadParticipants(ticketId));
        }
        Optional<DialogOperatorOption> targetOperator = dialogParticipantService.findOperator(username);
        if (targetOperator.isEmpty()) {
            return new DialogReassignResult(true, "Пользователь панели не найден", null, null, null, dialogParticipantService.loadParticipants(ticketId));
        }
        String currentResponsible = dialogResponsibilityService.loadResponsible(ticketId);
        if (sameIdentity(currentResponsible, targetOperator.get().username())) {
            return new DialogReassignResult(true, "Диалог уже назначен на этого пользователя", currentResponsible, dialog.get().responsible(), dialog.get().responsibleAvatarUrl(), dialogParticipantService.loadParticipants(ticketId));
        }
        dialogResponsibilityService.assignResponsibleIfMissingOrRedirected(ticketId, targetOperator.get().username(), operator);
        dialogParticipantService.removeParticipant(ticketId, targetOperator.get().username());
        dialogAiAssistantService.clearProcessing(ticketId, "operator_reassign", null);
        List<DialogParticipantDto> participants = dialogParticipantService.loadParticipants(ticketId);
        notifyDialogParticipantsSafely(
                ticketId,
                "Обращение " + ticketId + " передано оператору " + targetOperator.get().displayLabel(),
                notificationService.buildDialogUrl(ticketId),
                operator
        );
        return new DialogReassignResult(
                true,
                null,
                targetOperator.get().username(),
                targetOperator.get().displayLabel(),
                targetOperator.get().avatarUrl(),
                participants
        );
    }

    private void notifyDialogParticipantsSafely(String ticketId,
                                                String text,
                                                String url,
                                                String excludedIdentity) {
        try {
            notificationService.notifyDialogParticipants(ticketId, text, url, excludedIdentity);
        } catch (RuntimeException ex) {
            log.warn("Unable to create dialog notifications for ticket {}: {}", ticketId, ex.getMessage());
        }
    }

    private boolean isClosedDialog(DialogListItem dialog) {
        if (dialog == null) {
            return false;
        }
        String statusKey = dialog.statusKey();
        return "closed".equalsIgnoreCase(statusKey) || "auto_closed".equalsIgnoreCase(statusKey);
    }

    private boolean sameIdentity(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private List<String> mergeCategoriesWithSpam(String categoriesRaw) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (StringUtils.hasText(categoriesRaw)) {
            for (String part : categoriesRaw.split(",")) {
                if (StringUtils.hasText(part)) {
                    normalized.add(part.trim());
                }
            }
        }
        normalized.add("Спам");
        return new ArrayList<>(normalized);
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : categories) {
            if (StringUtils.hasText(category)) {
                normalized.add(category.trim());
            }
        }
        return List.copyOf(normalized);
    }

    public record DialogParticipantMutationResult(boolean exists,
                                                  boolean changed,
                                                  String error,
                                                  List<DialogParticipantDto> participants) {
    }

    public record DialogReassignResult(boolean exists,
                                       String error,
                                       String responsible,
                                       String responsibleDisplayName,
                                       String responsibleAvatarUrl,
                                       List<DialogParticipantDto> participants) {
    }

    public record DialogSpamResult(boolean exists,
                                   boolean updated,
                                   String error,
                                   String userId,
                                   List<String> categories) {
    }

    public record DialogCategoryUpdateResult(boolean exists,
                                             List<String> categories) {
    }
}
