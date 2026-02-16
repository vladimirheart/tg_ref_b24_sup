package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.storage.AttachmentService;
import com.fasterxml.jackson.annotation.JsonAlias;
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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/dialogs")
@Validated
public class DialogApiController {

    private static final Logger log = LoggerFactory.getLogger(DialogApiController.class);

    private final DialogService dialogService;
    private final DialogReplyService dialogReplyService;
    private final DialogNotificationService dialogNotificationService;
    private final AttachmentService attachmentService;
    private final SharedConfigService sharedConfigService;
    private final PermissionService permissionService;
    private static final long QUICK_ACTION_TARGET_MS = 1500;
    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
    private static final int DEFAULT_SLA_WARNING_MINUTES = 4 * 60;
    private static final int DEFAULT_WORKSPACE_LIMIT = 50;
    private static final int MAX_WORKSPACE_LIMIT = 200;
    private static final Set<String> WORKSPACE_INCLUDE_ALLOWED = Set.of("messages", "context", "sla", "permissions");

    public DialogApiController(DialogService dialogService,
                               DialogReplyService dialogReplyService,
                               DialogNotificationService dialogNotificationService,
                               AttachmentService attachmentService,
                               SharedConfigService sharedConfigService,
                               PermissionService permissionService) {
        this.dialogService = dialogService;
        this.dialogReplyService = dialogReplyService;
        this.dialogNotificationService = dialogNotificationService;
        this.attachmentService = attachmentService;
        this.sharedConfigService = sharedConfigService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public Map<String, Object> list(Authentication authentication) {
        DialogSummary summary = dialogService.loadSummary();
        List<DialogListItem> dialogs = dialogService.loadDialogs(authentication != null ? authentication.getName() : null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", summary);
        payload.put("dialogs", dialogs);
        payload.put("sla_orchestration", buildSlaOrchestration(dialogs));
        payload.put("success", true);
        log.info("Loaded dialogs API payload: {} dialogs, summary stats loaded", dialogs.size());
        return payload;
    }

    private Map<String, Object> buildSlaOrchestration(List<DialogListItem> dialogs) {
        int targetMinutes = resolveDialogConfigMinutes("sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int warningMinutes = Math.min(resolveDialogConfigMinutes("sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES), targetMinutes);
        int criticalMinutes = resolveDialogConfigMinutes("sla_critical_minutes", 30);
        boolean escalationEnabled = resolveDialogConfigBoolean("sla_critical_escalation_enabled", true);

        Map<String, Object> ticketSignals = new LinkedHashMap<>();
        long nowMs = System.currentTimeMillis();
        for (DialogListItem dialog : dialogs) {
            String ticketId = dialog.ticketId();
            if (ticketId == null || ticketId.isBlank()) {
                continue;
            }
            String statusKey = dialog.statusKey();
            String state = resolveSlaState(dialog.createdAt(), targetMinutes, warningMinutes, statusKey);
            Long minutesLeft = resolveSlaMinutesLeft(dialog.createdAt(), targetMinutes, statusKey, nowMs);
            boolean critical = escalationEnabled && "open".equals(normalizeSlaLifecycleState(statusKey))
                    && minutesLeft != null && minutesLeft <= criticalMinutes;
            boolean assigned = dialog.responsible() != null && !dialog.responsible().isBlank();

            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("state", state);
            signal.put("minutes_left", minutesLeft);
            signal.put("is_critical", critical);
            signal.put("auto_pin", critical);
            signal.put("escalation_required", critical && !assigned);
            signal.put("escalation_reason", critical && !assigned ? "critical_sla_unassigned" : null);
            ticketSignals.put(ticketId, signal);
        }

        return Map.of(
                "enabled", escalationEnabled,
                "target_minutes", targetMinutes,
                "warning_minutes", warningMinutes,
                "critical_minutes", criticalMinutes,
                "generated_at", Instant.ofEpochMilli(nowMs).toString(),
                "tickets", ticketSignals
        );
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

    @GetMapping("/{ticketId}/workspace")
    public ResponseEntity<?> workspace(@PathVariable String ticketId,
                                       @RequestParam(value = "channelId", required = false) Long channelId,
                                       @RequestParam(value = "include", required = false) String include,
                                       @RequestParam(value = "limit", required = false) Integer limit,
                                       @RequestParam(value = "cursor", required = false) String cursor,
                                       Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.markDialogAsRead(ticketId, operator);
        Optional<DialogDetails> details = dialogService.loadDialogDetails(ticketId, channelId, operator);
        if (details.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }

        DialogDetails dialogDetails = details.get();
        DialogListItem summary = dialogDetails.summary();
        Set<String> includeSections = resolveWorkspaceInclude(include);
        int resolvedLimit = resolveWorkspaceLimit(limit);
        int resolvedCursor = resolveWorkspaceCursor(cursor);
        List<ChatMessageDto> history = dialogService.loadHistory(ticketId, channelId);

        int safeCursor = Math.min(Math.max(resolvedCursor, 0), history.size());
        int endExclusive = Math.min(safeCursor + resolvedLimit, history.size());
        List<ChatMessageDto> pagedHistory = history.subList(safeCursor, endExclusive);
        boolean hasMore = endExclusive < history.size();
        Integer nextCursor = hasMore ? endExclusive : null;

        int slaTargetMinutes = resolveDialogConfigMinutes("sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int slaWarningMinutes = Math.min(
                resolveDialogConfigMinutes("sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES),
                slaTargetMinutes
        );
        List<Map<String, Object>> clientHistory = dialogService.loadClientDialogHistory(summary.userId(), ticketId, 5);
        List<Map<String, Object>> relatedEvents = dialogService.loadRelatedEvents(ticketId, 5);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contract_version", "workspace.v1");
        payload.put("conversation", summary);
        payload.put("messages", includeSections.contains("messages")
                ? Map.of(
                "items", pagedHistory,
                "next_cursor", nextCursor,
                "has_more", hasMore,
                "limit", resolvedLimit,
                "cursor", safeCursor
        )
                : Map.of(
                "items", List.of(),
                "next_cursor", null,
                "has_more", false,
                "unavailable", true
        ));
        payload.put("context", includeSections.contains("context")
                ? Map.of(
                "client", Map.of(
                        "id", summary.userId(),
                        "name", summary.displayClientName(),
                        "language", "ru",
                        "username", summary.username(),
                        "status", summary.clientStatusLabel(),
                        "channel", summary.channelLabel(),
                        "business", summary.businessLabel(),
                        "location", summary.location(),
                        "responsible", summary.responsible(),
                        "unread_count", summary.unreadCount(),
                        "rating", summary.rating(),
                        "last_message_at", summary.lastMessageTimestamp(),
                        "segments", buildWorkspaceClientSegments(summary)
                ),
                "history", clientHistory,
                "related_events", relatedEvents
        )
                : Map.of(
                "client", Map.of(),
                "history", List.of(),
                "related_events", List.of(),
                "unavailable", true
        ));
        payload.put("permissions", includeSections.contains("permissions")
                ? resolveWorkspacePermissions(authentication)
                : Map.of(
                "can_reply", false,
                "can_assign", false,
                "can_close", false,
                "can_snooze", false,
                "can_bulk", false,
                "unavailable", true
        ));
        payload.put("sla", includeSections.contains("sla")
                ? Map.of(
                "target_minutes", slaTargetMinutes,
                "warning_minutes", slaWarningMinutes,
                "deadline_at", computeDeadlineAt(summary.createdAt(), slaTargetMinutes),
                "state", resolveSlaState(summary.createdAt(), slaTargetMinutes, slaWarningMinutes, summary.statusKey())
        )
                : Map.of(
                "target_minutes", slaTargetMinutes,
                "warning_minutes", slaWarningMinutes,
                "deadline_at", computeDeadlineAt(summary.createdAt(), slaTargetMinutes),
                "state", "unknown",
                "unavailable", true
        ));
        payload.put("meta", Map.of(
                "include", includeSections,
                "limit", resolvedLimit,
                "cursor", safeCursor
        ));
        payload.put("success", true);
        return ResponseEntity.ok(payload);
    }

    private List<String> buildWorkspaceClientSegments(DialogListItem summary) {
        if (summary == null) {
            return List.of();
        }
        List<String> segments = new java.util.ArrayList<>();
        if (summary.unreadCount() != null && summary.unreadCount() > 0) {
            segments.add("needs_reply");
        }
        if (summary.responsible() == null || summary.responsible().isBlank()) {
            segments.add("unassigned");
        }
        if (summary.rating() != null && summary.rating() > 0 && summary.rating() <= 2) {
            segments.add("low_csat_risk");
        }
        if ("new".equals(summary.statusKey())) {
            segments.add("new_dialog");
        }
        return segments;
    }

    private Set<String> resolveWorkspaceInclude(String include) {
        if (include == null || include.isBlank()) {
            return WORKSPACE_INCLUDE_ALLOWED;
        }
        Set<String> result = new HashSet<>();
        Arrays.stream(include.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(WORKSPACE_INCLUDE_ALLOWED::contains)
                .forEach(result::add);
        return result.isEmpty() ? WORKSPACE_INCLUDE_ALLOWED : Collections.unmodifiableSet(result);
    }

    private int resolveWorkspaceLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_WORKSPACE_LIMIT;
        }
        return Math.min(limit, MAX_WORKSPACE_LIMIT);
    }

    private int resolveWorkspaceCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }


    @PostMapping("/workspace-telemetry")
    public ResponseEntity<?> workspaceTelemetry(@RequestBody(required = false) WorkspaceTelemetryRequest request,
                                                Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "anonymous";
        if (request == null || request.eventType() == null || request.eventType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "event_type is required"));
        }
        log.info("Workspace telemetry: actor='{}', event='{}', group='{}', ticket='{}', reason='{}', error='{}', contract='{}', durationMs={}, experiment='{}', cohort='{}', segment='{}', primaryKpis='{}', secondaryKpis='{}', templateId='{}', templateName='{}'",
                operator,
                request.eventType(),
                request.eventGroup(),
                request.ticketId(),
                request.reason(),
                request.errorCode(),
                request.contractVersion(),
                request.durationMs(),
                request.experimentName(),
                request.experimentCohort(),
                request.operatorSegment(),
                request.primaryKpis(),
                request.secondaryKpis(),
                request.templateId(),
                request.templateName());
        dialogService.logWorkspaceTelemetry(
                operator,
                request.eventType(),
                request.eventGroup(),
                request.ticketId(),
                request.reason(),
                request.errorCode(),
                request.contractVersion(),
                request.durationMs(),
                request.experimentName(),
                request.experimentCohort(),
                request.operatorSegment(),
                request.primaryKpis(),
                request.secondaryKpis(),
                request.templateId(),
                request.templateName());
        maybeAuditMacroUsage(operator, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void maybeAuditMacroUsage(String operator, WorkspaceTelemetryRequest request) {
        if (request == null || !"macro_apply".equalsIgnoreCase(String.valueOf(request.eventType()))) {
            return;
        }
        if (!StringUtils.hasText(request.ticketId())) {
            return;
        }
        StringBuilder detail = new StringBuilder("Macro applied from workspace telemetry");
        if (StringUtils.hasText(request.templateName())) {
            detail.append(": ").append(request.templateName().trim());
        }
        if (StringUtils.hasText(request.templateId())) {
            detail.append(" [").append(request.templateId().trim()).append("]");
        }
        dialogService.logDialogActionAudit(
                request.ticketId(),
                operator,
                "macro_apply",
                "success",
                detail.toString());
    }

    @GetMapping("/workspace-telemetry/summary")
    public ResponseEntity<?> workspaceTelemetrySummary(@RequestParam(name = "days", defaultValue = "7") Integer days,
                                                       @RequestParam(name = "experiment_name", required = false) String experimentName) {
        int safeDays = days != null ? days : 7;
        Map<String, Object> payload = new LinkedHashMap<>(dialogService.loadWorkspaceTelemetrySummary(safeDays, experimentName));
        payload.put("success", true);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<?> reply(@PathVariable String ticketId,
                                   @RequestBody DialogReplyRequest request,
                                   Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "reply", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
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
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "edit", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
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
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "delete", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
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
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "reply_media", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
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
        return withQuickActionTiming("quick_close", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_close", "quick_close", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
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
        });
    }

    @PostMapping("/{ticketId}/reopen")
    public ResponseEntity<?> reopen(@PathVariable String ticketId,
                                    Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_close", "reopen", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
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
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_close", "categories", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        dialogService.setTicketCategories(ticketId, request != null ? request.categories() : List.of());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{ticketId}/take")
    public ResponseEntity<?> take(@PathVariable String ticketId,
                                  Authentication authentication) {
        return withQuickActionTiming("take", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_assign", "take", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
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
        });
    }

    @PostMapping("/{ticketId}/snooze")
    public ResponseEntity<?> snooze(@PathVariable String ticketId,
                                    @RequestBody(required = false) DialogSnoozeRequest request,
                                    Authentication authentication) {
        return withQuickActionTiming("snooze", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_snooze", "snooze", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : "anonymous";
            Integer minutes = request != null ? request.minutes() : null;
            if (minutes == null || minutes <= 0) {
                logQuickAction(operator, ticketId, "snooze", "error", "Некорректная длительность snooze");
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Некорректная длительность snooze"));
            }
            logQuickAction(operator, ticketId, "snooze", "success", "minutes=" + minutes);
            return ResponseEntity.ok(Map.of("success", true));
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

    private void logQuickAction(String actor, String ticketId, String action, String result, String detail) {
        String safeActor = actor != null ? actor : "anonymous";
        String safeDetail = detail != null ? detail : "";
        log.info("Dialog quick action: actor='{}', ticket='{}', action='{}', result='{}', detail='{}'",
                safeActor,
                ticketId,
                action,
                result,
                safeDetail);
        dialogService.logDialogActionAudit(ticketId, safeActor, action, result, safeDetail);
    }

    private Map<String, Object> resolveWorkspacePermissions(Authentication authentication) {
        boolean canDialog = permissionService.hasAuthority(authentication, "PAGE_DIALOGS");
        boolean canBulk = canDialog && (permissionService.hasAuthority(authentication, "DIALOG_BULK_ACTIONS")
                || permissionService.hasAuthority(authentication, "ROLE_ADMIN"));
        return Map.of(
                "can_reply", canDialog,
                "can_assign", canDialog,
                "can_close", canDialog,
                "can_snooze", canDialog,
                "can_bulk", canBulk
        );
    }

    private ResponseEntity<Map<String, Object>> requireDialogPermission(Authentication authentication,
                                                                         String permission,
                                                                         String action,
                                                                         String ticketId) {
        Map<String, Object> permissions = resolveWorkspacePermissions(authentication);
        boolean allowed = Boolean.TRUE.equals(permissions.get(permission));
        if (allowed) {
            return null;
        }
        String operator = authentication != null ? authentication.getName() : null;
        logQuickAction(operator, ticketId, action, "forbidden", "Недостаточно прав: " + permission);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "error", "Недостаточно прав для выполнения действия"));
    }

    private int resolveDialogConfigMinutes(String key, int fallbackValue) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallbackValue;
        } catch (NumberFormatException ex) {
            return fallbackValue;
        }
    }

    private boolean resolveDialogConfigBoolean(String key, boolean fallbackValue) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallbackValue;
    }

    private String resolveSlaState(String createdAt, int targetMinutes, int warningMinutes, String statusKey) {
        if ("closed".equals(normalizeSlaLifecycleState(statusKey))) {
            return "closed";
        }
        Long createdAtMs = parseTimestampToMillis(createdAt);
        if (createdAtMs == null) {
            return "normal";
        }
        long deadlineMs = createdAtMs + targetMinutes * 60_000L;
        long warningMs = deadlineMs - warningMinutes * 60_000L;
        long nowMs = System.currentTimeMillis();
        if (nowMs >= deadlineMs) {
            return "breached";
        }
        if (nowMs >= warningMs) {
            return "at_risk";
        }
        return "normal";
    }

    private Long resolveSlaMinutesLeft(String createdAt, int targetMinutes, String statusKey, long nowMs) {
        if (!"open".equals(normalizeSlaLifecycleState(statusKey))) {
            return null;
        }
        Long createdAtMs = parseTimestampToMillis(createdAt);
        if (createdAtMs == null) {
            return null;
        }
        long deadlineMs = createdAtMs + targetMinutes * 60_000L;
        return Math.round((deadlineMs - nowMs) / 60_000d);
    }

    private String normalizeSlaLifecycleState(String statusKey) {
        String normalized = statusKey != null ? statusKey.trim().toLowerCase() : "";
        if ("closed".equals(normalized) || "auto_closed".equals(normalized)) {
            return "closed";
        }
        return "open";
    }

    private String computeDeadlineAt(String createdAt, int targetMinutes) {
        Long createdAtMs = parseTimestampToMillis(createdAt);
        if (createdAtMs == null) {
            return null;
        }
        return Instant.ofEpochMilli(createdAtMs + targetMinutes * 60_000L).toString();
    }

    private Long parseTimestampToMillis(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim();
        if (value.matches("\\d{10,13}")) {
            try {
                long epoch = Long.parseLong(value);
                return value.length() == 10 ? epoch * 1000 : epoch;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public record WorkspaceTelemetryRequest(@JsonAlias("event_type") String eventType,
                                          String timestamp,
                                          @JsonAlias("event_group") String eventGroup,
                                          @JsonAlias("ticket_id") String ticketId,
                                          String reason,
                                          @JsonAlias("error_code") String errorCode,
                                          @JsonAlias("contract_version") String contractVersion,
                                          @JsonAlias("duration_ms") Long durationMs,
                                          @JsonAlias("experiment_name") String experimentName,
                                          @JsonAlias("experiment_cohort") String experimentCohort,
                                          @JsonAlias("operator_segment") String operatorSegment,
                                          @JsonAlias("primary_kpis") List<String> primaryKpis,
                                          @JsonAlias("secondary_kpis") List<String> secondaryKpis,
                                          @JsonAlias("template_id") String templateId,
                                          @JsonAlias("template_name") String templateName) {}

    public record DialogReplyRequest(String message, Long replyToTelegramId) {}

    public record DialogResolveRequest(List<String> categories) {}

    public record DialogEditRequest(Long telegramMessageId, String message) {}

    public record DialogDeleteRequest(Long telegramMessageId) {}

    public record DialogCategoriesRequest(List<String> categories) {}

    public record DialogSnoozeRequest(Integer minutes) {}
}
