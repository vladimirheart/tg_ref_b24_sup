package com.example.panel.controller;

import com.example.panel.service.DialogAiOpsService;
import com.example.panel.service.DialogAuthorizationService;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dialogs")
public class DialogAiOpsController {

    private final DialogAiOpsService dialogAiOpsService;
    private final DialogAuthorizationService dialogAuthorizationService;

    public DialogAiOpsController(DialogAiOpsService dialogAiOpsService,
                                 DialogAuthorizationService dialogAuthorizationService) {
        this.dialogAiOpsService = dialogAiOpsService;
        this.dialogAuthorizationService = dialogAuthorizationService;
    }

    @GetMapping("/{ticketId}/ai-suggestions")
    public ResponseEntity<?> aiSuggestions(@PathVariable String ticketId,
                                           @RequestParam(value = "limit", required = false) Integer limit,
                                           Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_suggestions", ticketId);
        return denied != null ? denied : ResponseEntity.ok(dialogAiOpsService.loadSuggestions(ticketId, limit));
    }

    @PostMapping("/{ticketId}/ai-suggestions/feedback")
    public ResponseEntity<?> aiSuggestionFeedback(@PathVariable String ticketId,
                                                  @RequestBody(required = false) AiSuggestionFeedbackRequest request,
                                                  Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_suggestion_feedback", ticketId);
        if (denied != null) {
            return denied;
        }
        if (request == null || !StringUtils.hasText(request.decision())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "decision is required"));
        }
        String operator = authentication != null ? authentication.getName() : null;
        dialogAiOpsService.recordSuggestionFeedback(
                ticketId,
                request.decision(),
                request.source(),
                request.title(),
                request.snippet(),
                request.suggestedReply(),
                operator
        );
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/{ticketId}/ai-control")
    public ResponseEntity<?> aiControlState(@PathVariable String ticketId, Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_control_state", ticketId);
        return denied != null ? denied : ResponseEntity.ok(dialogAiOpsService.loadControlState(ticketId));
    }

    @PostMapping("/{ticketId}/ai-control")
    public ResponseEntity<?> aiControlUpdate(@PathVariable String ticketId,
                                             @RequestBody(required = false) AiControlRequest request,
                                             Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_control_update", ticketId);
        if (denied != null) {
            return denied;
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "request body is required"));
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.updateControlState(
                ticketId,
                request.aiDisabled(),
                request.autoReplyBlocked(),
                request.reason(),
                operator
        ));
    }

    @GetMapping("/{ticketId}/ai-review")
    public ResponseEntity<?> aiReview(@PathVariable String ticketId, Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_review", ticketId);
        return denied != null ? denied : ResponseEntity.ok(dialogAiOpsService.loadReview(ticketId));
    }

    @PostMapping("/{ticketId}/ai-review/approve")
    public ResponseEntity<?> approveAiReview(@PathVariable String ticketId,
                                             @RequestBody(required = false) AiReviewApproveRequest request,
                                             Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_review_approve", ticketId);
        if (denied != null) {
            return denied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.approveReview(
                ticketId,
                operator,
                request != null ? request.clientMessageId() : null,
                request != null ? request.operatorMessageId() : null
        ));
    }

    @PostMapping("/{ticketId}/ai-review/reject")
    public ResponseEntity<?> rejectAiReview(@PathVariable String ticketId, Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_review_reject", ticketId);
        if (denied != null) {
            return denied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.rejectReview(ticketId, operator));
    }

    @GetMapping("/ai-reviews")
    public ResponseEntity<?> aiReviewsQueue(@RequestParam(value = "limit", required = false) Integer limit,
                                            Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_reviews_queue", null);
        return denied != null ? denied : ResponseEntity.ok(dialogAiOpsService.loadReviewsQueue(limit));
    }

    @PostMapping("/ai-reviews/{queryKey}/approve")
    public ResponseEntity<?> approveAiReviewByKey(@PathVariable String queryKey, Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_review_approve_key", null);
        if (denied != null) {
            return denied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.approveReviewByKey(queryKey, operator));
    }

    @PostMapping("/ai-reviews/{queryKey}/reject")
    public ResponseEntity<?> rejectAiReviewByKey(@PathVariable String queryKey, Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_review_reject_key", null);
        if (denied != null) {
            return denied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.rejectReviewByKey(queryKey, operator));
    }

    @GetMapping("/ai-solution-memory")
    public ResponseEntity<?> aiSolutionMemory(@RequestParam(value = "limit", required = false) Integer limit,
                                              @RequestParam(value = "query", required = false) String query,
                                              Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_solution_memory", null);
        return denied != null ? denied : ResponseEntity.ok(dialogAiOpsService.loadSolutionMemory(limit, query));
    }

    @PostMapping("/ai-solution-memory/{queryKey}")
    public ResponseEntity<?> updateAiSolutionMemory(@PathVariable String queryKey,
                                                    @RequestBody(required = false) AiSolutionMemoryUpdateRequest request,
                                                    Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_solution_memory_update", null);
        if (denied != null) {
            return denied;
        }
        if (request == null || !StringUtils.hasText(request.queryText()) || !StringUtils.hasText(request.solutionText())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "query_text and solution_text are required"));
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.updateSolutionMemory(
                queryKey,
                request.queryText(),
                request.solutionText(),
                request.reviewRequired(),
                operator
        ));
    }

    @DeleteMapping("/ai-solution-memory/{queryKey}")
    public ResponseEntity<?> deleteAiSolutionMemory(@PathVariable String queryKey,
                                                    Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_solution_memory_delete", null);
        if (denied != null) {
            return denied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.deleteSolutionMemory(queryKey, operator));
    }

    @GetMapping("/ai-solution-memory/{queryKey}/history")
    public ResponseEntity<?> aiSolutionMemoryHistory(@PathVariable String queryKey,
                                                     @RequestParam(value = "limit", required = false) Integer limit,
                                                     Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_solution_memory_history", null);
        return denied != null ? denied : ResponseEntity.ok(dialogAiOpsService.loadSolutionMemoryHistory(queryKey, limit));
    }

    @PostMapping("/ai-solution-memory/{queryKey}/rollback")
    public ResponseEntity<?> rollbackAiSolutionMemory(@PathVariable String queryKey,
                                                      @RequestBody(required = false) AiSolutionMemoryRollbackRequest request,
                                                      Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_solution_memory_rollback", null);
        if (denied != null) {
            return denied;
        }
        if (request == null || request.historyId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "history_id is required"));
        }
        String operator = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dialogAiOpsService.rollbackSolutionMemory(queryKey, request.historyId(), operator));
    }

    @GetMapping("/ai-monitoring/summary")
    public ResponseEntity<?> aiMonitoringSummary(@RequestParam(value = "days", required = false) Integer days,
                                                 Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_monitoring_summary", null);
        return denied != null ? denied : ResponseEntity.ok(dialogAiOpsService.loadMonitoringSummary(days));
    }

    @GetMapping("/ai-monitoring/events")
    public ResponseEntity<?> aiMonitoringEvents(@RequestParam(value = "days", required = false) Integer days,
                                                @RequestParam(value = "limit", required = false) Integer limit,
                                                @RequestParam(value = "ticketId", required = false) String ticketId,
                                                @RequestParam(value = "eventType", required = false) String eventType,
                                                @RequestParam(value = "actor", required = false) String actor,
                                                @RequestParam(value = "format", required = false) String format,
                                                Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(authentication, "can_reply", "ai_monitoring_events", ticketId);
        if (denied != null) {
            return denied;
        }
        List<Map<String, Object>> items = dialogAiOpsService.loadMonitoringEvents(days, limit, ticketId, eventType, actor);
        if ("csv".equalsIgnoreCase(String.valueOf(format))) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(dialogAiOpsService.buildMonitoringEventsCsv(items));
        }
        return ResponseEntity.ok(Map.of("success", true, "items", items));
    }

    public record AiSuggestionFeedbackRequest(String decision,
                                              String source,
                                              String title,
                                              String snippet,
                                              @JsonAlias({"suggested_reply", "suggestedReply"}) String suggestedReply) {
    }

    public record AiControlRequest(@JsonAlias({"ai_disabled", "aiDisabled"}) Boolean aiDisabled,
                                   @JsonAlias({"auto_reply_blocked", "autoReplyBlocked"}) Boolean autoReplyBlocked,
                                   String reason) {
    }

    public record AiReviewApproveRequest(@JsonAlias({"client_message_id", "clientMessageId"}) Long clientMessageId,
                                         @JsonAlias({"operator_message_id", "operatorMessageId"}) Long operatorMessageId) {
    }

    public record AiSolutionMemoryUpdateRequest(@JsonAlias({"query_text", "queryText"}) String queryText,
                                                @JsonAlias({"solution_text", "solutionText"}) String solutionText,
                                                @JsonAlias({"review_required", "reviewRequired"}) Boolean reviewRequired) {
    }

    public record AiSolutionMemoryRollbackRequest(@JsonAlias({"history_id", "historyId"}) Long historyId) {
    }
}
