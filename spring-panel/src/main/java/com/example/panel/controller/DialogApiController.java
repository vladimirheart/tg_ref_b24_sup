package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogService;
import com.example.panel.storage.AttachmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dialogs")
@Validated
public class DialogApiController {

    private static final Logger log = LoggerFactory.getLogger(DialogApiController.class);

    private final DialogService dialogService;
    private final DialogReplyService dialogReplyService;
    private final DialogNotificationService dialogNotificationService;
    private final AttachmentService attachmentService;

    public DialogApiController(DialogService dialogService,
                               DialogReplyService dialogReplyService,
                               DialogNotificationService dialogNotificationService,
                               AttachmentService attachmentService) {
        this.dialogService = dialogService;
        this.dialogReplyService = dialogReplyService;
        this.dialogNotificationService = dialogNotificationService;
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public Map<String, Object> list(Authentication authentication) {
        DialogSummary summary = dialogService.loadSummary();
        List<DialogListItem> dialogs = dialogService.loadDialogs(authentication != null ? authentication.getName() : null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", summary);
        payload.put("dialogs", dialogs);
        payload.put("success", true);
        log.info("Loaded dialogs API payload: {} dialogs, summary stats loaded", dialogs.size());
        return payload;
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> details(@PathVariable String ticketId,
                                     @RequestParam(value = "channelId", required = false) Long channelId,
                                     Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.markDialogAsRead(ticketId, operator);
        Optional<DialogDetails> details = dialogService.loadDialogDetails(ticketId, channelId, operator);
        log.info("Dialog details requested for ticket {} (channelId={}): {}", ticketId, channelId,
                details.map(d -> "found").orElse("not found"));
        return details.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден")));
    }

    @GetMapping("/{ticketId}/history")
    public Map<String, Object> history(@PathVariable String ticketId,
                                        @RequestParam(value = "channelId", required = false) Long channelId,
                                        Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.markDialogAsRead(ticketId, operator);
        List<ChatMessageDto> history = dialogService.loadHistory(ticketId, channelId);
        log.info("History requested for ticket {} (channelId={}): {} messages", ticketId, channelId, history.size());
        return Map.of(
                "success", true,
                "messages", history
        );
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<?> reply(@PathVariable String ticketId,
                                   @RequestBody DialogReplyRequest request,
                                   Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(
                ticketId,
                request.message(),
                request.replyToTelegramId(),
                operator
        );
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "timestamp", result.timestamp(),
                "telegramMessageId", result.telegramMessageId(),
                "responsible", operator
        ));
    }


    @PostMapping("/{ticketId}/edit")
    public ResponseEntity<?> editMessage(@PathVariable String ticketId,
                                         @RequestBody DialogEditRequest request,
                                         Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogReplyService.editOperatorMessage(
                ticketId,
                request.telegramMessageId(),
                request.message(),
                operator
        );
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        return ResponseEntity.ok(Map.of("success", true, "timestamp", result.timestamp()));
    }

    @PostMapping("/{ticketId}/delete")
    public ResponseEntity<?> deleteMessage(@PathVariable String ticketId,
                                           @RequestBody DialogDeleteRequest request,
                                           Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogReplyService.deleteOperatorMessage(
                ticketId,
                request.telegramMessageId(),
                operator
        );
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        return ResponseEntity.ok(Map.of("success", true, "timestamp", result.timestamp()));
    }

    @PostMapping(value = "/{ticketId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replyWithMedia(@PathVariable String ticketId,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "message", required = false) String message,
                                            Authentication authentication) throws IOException {
        String operator = authentication != null ? authentication.getName() : null;
        var metadata = attachmentService.storeTicketAttachment(authentication, ticketId, file);
        var result = dialogReplyService.sendMediaReply(ticketId, file, message, operator, metadata.storedName(), metadata.originalName());
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        String attachmentUrl = "/api/attachments/tickets/" + ticketId + "/" + result.storedName();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "timestamp", result.timestamp(),
                "telegramMessageId", result.telegramMessageId(),
                "responsible", operator,
                "attachment", attachmentUrl,
                "messageType", result.messageType(),
                "message", result.message()
        ));
    }

    @PostMapping("/{ticketId}/resolve")
    public ResponseEntity<?> resolve(@PathVariable String ticketId,
                                     @RequestBody(required = false) DialogResolveRequest request,
                                     Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        List<String> categories = request != null ? request.categories() : List.of();
        DialogService.ResolveResult result = dialogService.resolveTicket(ticketId, operator, categories);
        if (!result.exists()) {
            logQuickAction(operator, ticketId, "quick_close", "not_found", "Диалог не найден");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }
        if (result.error() != null) {
            logQuickAction(operator, ticketId, "quick_close", "error", result.error());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        if (result.updated()) {
            dialogNotificationService.notifyResolved(ticketId);
        }
        logQuickAction(operator, ticketId, "quick_close", "success", result.updated() ? "updated" : "noop");
        return ResponseEntity.ok(Map.of("success", true, "updated", result.updated()));
    }

    @PostMapping("/{ticketId}/reopen")
    public ResponseEntity<?> reopen(@PathVariable String ticketId,
                                    Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        DialogService.ResolveResult result = dialogService.reopenTicket(ticketId, operator);
        if (!result.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }
        if (result.error() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        if (result.updated()) {
            dialogNotificationService.notifyReopened(ticketId);
        }
        return ResponseEntity.ok(Map.of("success", true, "updated", result.updated()));
    }

    @PostMapping("/{ticketId}/categories")
    public ResponseEntity<?> updateCategories(@PathVariable String ticketId,
                                              @RequestBody(required = false) DialogCategoriesRequest request,
                                              Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        dialogService.setTicketCategories(ticketId, request != null ? request.categories() : List.of());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{ticketId}/take")
    public ResponseEntity<?> take(@PathVariable String ticketId,
                                  Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        if (operator == null || operator.isBlank()) {
            logQuickAction(null, ticketId, "take", "unauthorized", "Требуется авторизация");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Требуется авторизация"));
        }
        Optional<DialogListItem> dialog = dialogService.findDialog(ticketId, operator);
        if (dialog.isEmpty()) {
            logQuickAction(operator, ticketId, "take", "not_found", "Диалог не найден");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }

        dialogService.assignResponsibleIfMissing(ticketId, operator);

        Optional<DialogListItem> updated = dialogService.findDialog(ticketId, operator);
        String responsible = updated.map(DialogListItem::responsible).orElse(dialog.get().responsible());
        logQuickAction(operator, ticketId, "take", "success", "responsible_assigned");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "responsible", responsible != null && !responsible.isBlank() ? responsible : operator
        ));
    }

    @PostMapping("/{ticketId}/snooze")
    public ResponseEntity<?> snooze(@PathVariable String ticketId,
                                    @RequestBody(required = false) DialogSnoozeRequest request,
                                    Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "anonymous";
        Integer minutes = request != null ? request.minutes() : null;
        if (minutes == null || minutes <= 0) {
            logQuickAction(operator, ticketId, "snooze", "error", "Некорректная длительность snooze");
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Некорректная длительность snooze"));
        }
        logQuickAction(operator, ticketId, "snooze", "success", "minutes=" + minutes);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void logQuickAction(String actor, String ticketId, String action, String result, String detail) {
        log.info("Dialog quick action: actor='{}', ticket='{}', action='{}', result='{}', detail='{}'",
                actor != null ? actor : "anonymous",
                ticketId,
                action,
                result,
                detail != null ? detail : "");
    }

    public record DialogReplyRequest(String message, Long replyToTelegramId) {}

    public record DialogResolveRequest(List<String> categories) {}

    public record DialogEditRequest(Long telegramMessageId, String message) {}

    public record DialogDeleteRequest(Long telegramMessageId) {}

    public record DialogCategoriesRequest(List<String> categories) {}

    public record DialogSnoozeRequest(Integer minutes) {}
}
