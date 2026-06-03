package com.example.panel.controller;

import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogResolveResult;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/dialogs")
public class DialogQuickActionsController {

    private static final Logger log = LoggerFactory.getLogger(DialogQuickActionsController.class);
    private static final long QUICK_ACTION_TARGET_MS = 1500;

    private final DialogQuickActionService dialogQuickActionService;
    private final DialogAuthorizationService dialogAuthorizationService;

    public DialogQuickActionsController(DialogQuickActionService dialogQuickActionService,
                                        DialogAuthorizationService dialogAuthorizationService) {
        this.dialogQuickActionService = dialogQuickActionService;
        this.dialogAuthorizationService = dialogAuthorizationService;
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<?> reply(@PathVariable String ticketId,
                                   @RequestBody DialogReplyRequest request,
                                   Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "reply", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogQuickActionService.sendReply(
                ticketId,
                request.message(),
                request.replyToTelegramId(),
                operator
        );
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("timestamp", result.timestamp());
        payload.put("telegramMessageId", result.telegramMessageId());
        payload.put("responsible", result.responsible());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{ticketId}/edit")
    public ResponseEntity<?> editMessage(@PathVariable String ticketId,
                                         @RequestBody DialogEditRequest request,
                                         Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "edit", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogQuickActionService.editReply(
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
        ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "delete", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogQuickActionService.deleteReply(
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
        ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "reply_media", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        Map<String, Object> payload = dialogQuickActionService.sendMediaReply(ticketId, file, message, operator, authentication);
        if (!Boolean.TRUE.equals(payload.get("success"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", payload.get("error")));
        }
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{ticketId}/resolve")
    public ResponseEntity<?> resolve(@PathVariable String ticketId,
                                     @RequestBody(required = false) DialogResolveRequest request,
                                     Authentication authentication) {
        return withQuickActionTiming("quick_close", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_close", "quick_close", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            List<String> categories = request != null ? request.categories() : List.of();
            DialogResolveResult result = dialogQuickActionService.resolveTicket(ticketId, operator, categories);
            if (!result.exists()) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "quick_close", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            if (result.error() != null) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "quick_close", "error", result.error());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", result.error()));
            }
            dialogAuthorizationService.logDialogAction(operator, ticketId, "quick_close", "success", result.updated() ? "updated" : "noop");
            return ResponseEntity.ok(Map.of("success", true, "updated", result.updated()));
        });
    }

    @PostMapping("/{ticketId}/reopen")
    public ResponseEntity<?> reopen(@PathVariable String ticketId,
                                    Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_close", "reopen", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogResolveResult result = dialogQuickActionService.reopenTicket(ticketId, operator);
        if (!result.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }
        if (result.error() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        return ResponseEntity.ok(Map.of("success", true, "updated", result.updated()));
    }

    @PostMapping("/{ticketId}/spam")
    public ResponseEntity<?> markSpam(@PathVariable String ticketId,
                                      @RequestBody(required = false) DialogSpamRequest request,
                                      Authentication authentication) {
        return withQuickActionTiming("mark_spam", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_close", "mark_spam", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            DialogQuickActionService.DialogSpamResult result = dialogQuickActionService.markClientAsSpam(
                    ticketId,
                    operator,
                    request != null ? request.reason() : null
            );
            if (!result.exists()) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "mark_spam", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            if (result.error() != null) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "mark_spam", "error", result.error());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", result.error()));
            }
            String detail = result.userId() != null
                    ? "blacklisted_user=" + result.userId()
                    : "blacklisted";
            dialogAuthorizationService.logDialogAction(operator, ticketId, "mark_spam", "success", detail);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "updated", result.updated(),
                    "userId", result.userId(),
                    "categories", result.categories()
            ));
        });
    }

    @PostMapping("/{ticketId}/categories")
    public ResponseEntity<?> updateCategories(@PathVariable String ticketId,
                                              @RequestBody(required = false) DialogCategoriesRequest request,
                                              Authentication authentication) {
        return withQuickActionTiming("categories", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_close", "categories", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            DialogQuickActionService.DialogCategoryUpdateResult result = dialogQuickActionService.updateCategories(
                    ticketId,
                    operator,
                    request != null ? request.categories() : List.of()
            );
            if (!result.exists()) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "categories", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            String detail = result.categories().isEmpty() ? "categories_cleared" : "categories_updated";
            dialogAuthorizationService.logDialogAction(operator, ticketId, "categories", "success", detail);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "categories", result.categories()
            ));
        });
    }

    @PostMapping("/{ticketId}/take")
    public ResponseEntity<?> take(@PathVariable String ticketId,
                                  Authentication authentication) {
        return withQuickActionTiming("take", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_assign", "take", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            if (!StringUtils.hasText(operator)) {
                dialogAuthorizationService.logDialogAction(null, ticketId, "take", "unauthorized", "Требуется авторизация");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Требуется авторизация"));
            }
            Optional<String> responsible = dialogQuickActionService.takeTicket(ticketId, operator);
            if (responsible.isEmpty()) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "take", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            dialogAuthorizationService.logDialogAction(operator, ticketId, "take", "success", "responsible_assigned");
            return ResponseEntity.ok(Map.of("success", true, "responsible", responsible.get()));
        });
    }

    @PostMapping("/{ticketId}/snooze")
    public ResponseEntity<?> snooze(@PathVariable String ticketId,
                                    @RequestBody(required = false) DialogSnoozeRequest request,
                                    Authentication authentication) {
        return withQuickActionTiming("snooze", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_snooze", "snooze", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : "anonymous";
            Integer minutes = request != null ? request.minutes() : null;
            if (minutes == null || minutes <= 0) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "snooze", "error", "Некорректная длительность snooze");
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Некорректная длительность snooze"));
            }
            dialogAuthorizationService.logDialogAction(operator, ticketId, "snooze", "success", "minutes=" + minutes);
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    @PostMapping("/{ticketId}/participants")
    public ResponseEntity<?> addParticipant(@PathVariable String ticketId,
                                            @RequestBody DialogParticipantRequest request,
                                            Authentication authentication) {
        return withQuickActionTiming("participants_add", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_assign", "participants_add", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            if (!StringUtils.hasText(operator)) {
                dialogAuthorizationService.logDialogAction(null, ticketId, "participants_add", "unauthorized", "Требуется авторизация");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Требуется авторизация"));
            }
            DialogQuickActionService.DialogParticipantMutationResult result = dialogQuickActionService.addParticipant(
                    ticketId,
                    request != null ? request.username() : null,
                    operator
            );
            if (!result.exists()) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "participants_add", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            if (result.error() != null) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "participants_add", "error", result.error());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", result.error()));
            }
            dialogAuthorizationService.logDialogAction(operator, ticketId, "participants_add", "success", result.changed() ? "participant_added" : "already_present");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "changed", result.changed(),
                    "participants", result.participants()
            ));
        });
    }

    @DeleteMapping("/{ticketId}/participants/{username}")
    public ResponseEntity<?> removeParticipant(@PathVariable String ticketId,
                                               @PathVariable String username,
                                               Authentication authentication) {
        return withQuickActionTiming("participants_remove", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_assign", "participants_remove", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            if (!StringUtils.hasText(operator)) {
                dialogAuthorizationService.logDialogAction(null, ticketId, "participants_remove", "unauthorized", "Требуется авторизация");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Требуется авторизация"));
            }
            DialogQuickActionService.DialogParticipantMutationResult result = dialogQuickActionService.removeParticipant(ticketId, username, operator);
            if (!result.exists()) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "participants_remove", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            if (result.error() != null) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "participants_remove", "error", result.error());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", result.error()));
            }
            dialogAuthorizationService.logDialogAction(operator, ticketId, "participants_remove", "success", result.changed() ? "participant_removed" : "participant_missing");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "changed", result.changed(),
                    "participants", result.participants()
            ));
        });
    }

    @PostMapping("/{ticketId}/reassign")
    public ResponseEntity<?> reassign(@PathVariable String ticketId,
                                      @RequestBody DialogReassignRequest request,
                                      Authentication authentication) {
        return withQuickActionTiming("reassign", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = dialogAuthorizationService.requirePermission(authentication, "can_assign", "reassign", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            if (!StringUtils.hasText(operator)) {
                dialogAuthorizationService.logDialogAction(null, ticketId, "reassign", "unauthorized", "Требуется авторизация");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Требуется авторизация"));
            }
            DialogQuickActionService.DialogReassignResult result = dialogQuickActionService.reassignTicket(
                    ticketId,
                    request != null ? request.username() : null,
                    operator
            );
            if (!result.exists()) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "reassign", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            if (result.error() != null) {
                dialogAuthorizationService.logDialogAction(operator, ticketId, "reassign", "error", result.error());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", result.error()));
            }
            dialogAuthorizationService.logDialogAction(operator, ticketId, "reassign", "success", "responsible_redirected");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("responsible", result.responsible());
            payload.put("displayResponsible", result.responsibleDisplayName());
            payload.put("avatarUrl", result.responsibleAvatarUrl());
            payload.put("participants", result.participants());
            return ResponseEntity.ok(payload);
        });
    }

    private <T> T withQuickActionTiming(String action, String ticketId, Supplier<T> supplier) {
        long startedAtMs = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            if (elapsedMs > QUICK_ACTION_TARGET_MS) {
                log.warn("Quick action '{}' for ticket '{}' exceeded target: {}ms > {}ms", action, ticketId, elapsedMs, QUICK_ACTION_TARGET_MS);
            } else {
                log.debug("Quick action '{}' for ticket '{}' completed in {}ms", action, ticketId, elapsedMs);
            }
        }
    }

    public record DialogReplyRequest(String message, Long replyToTelegramId) {}

    public record DialogResolveRequest(List<String> categories) {}

    public record DialogSpamRequest(String reason) {}

    public record DialogEditRequest(Long telegramMessageId, String message) {}

    public record DialogDeleteRequest(Long telegramMessageId) {}

    public record DialogCategoriesRequest(List<String> categories) {}

    public record DialogSnoozeRequest(Integer minutes) {}

    public record DialogParticipantRequest(String username) {}

    public record DialogReassignRequest(String username) {}
}
