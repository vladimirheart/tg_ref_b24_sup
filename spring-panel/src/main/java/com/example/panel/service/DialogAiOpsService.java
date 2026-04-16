package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogAiOpsService {

    private final DialogAiAssistantService dialogAiAssistantService;
    private final AiOpsRuntimeService aiOpsRuntimeService;

    public DialogAiOpsService(DialogAiAssistantService dialogAiAssistantService,
                              AiOpsRuntimeService aiOpsRuntimeService) {
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.aiOpsRuntimeService = aiOpsRuntimeService;
    }

    public Map<String, Object> loadSuggestions(String ticketId, Integer limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("ticket_id", ticketId);
        payload.put("processing", dialogAiAssistantService.isProcessing(ticketId));
        payload.put("items", dialogAiAssistantService.loadOperatorSuggestions(ticketId, limit));
        return payload;
    }

    public void recordSuggestionFeedback(String ticketId,
                                         String decision,
                                         String source,
                                         String title,
                                         String snippet,
                                         String suggestedReply,
                                         String operator) {
        dialogAiAssistantService.recordSuggestionFeedback(
                ticketId,
                decision,
                source,
                title,
                snippet,
                suggestedReply,
                operator
        );
    }

    public Map<String, Object> loadControlState(String ticketId) {
        return Map.of(
                "success", true,
                "control", dialogAiAssistantService.loadDialogControlState(ticketId)
        );
    }

    public Map<String, Object> updateControlState(String ticketId,
                                                  Boolean aiDisabled,
                                                  Boolean autoReplyBlocked,
                                                  String reason,
                                                  String operator) {
        boolean updated = dialogAiAssistantService.updateDialogControlState(
                ticketId,
                aiDisabled,
                autoReplyBlocked,
                reason,
                operator
        );
        return Map.of(
                "success", true,
                "updated", updated,
                "control", dialogAiAssistantService.loadDialogControlState(ticketId)
        );
    }

    public Map<String, Object> loadReview(String ticketId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("ticket_id", ticketId);
        payload.put("review", dialogAiAssistantService.loadPendingReview(ticketId));
        return payload;
    }

    public Map<String, Object> approveReview(String ticketId, String operator, Long clientMessageId, Long operatorMessageId) {
        boolean updated = dialogAiAssistantService.approvePendingReview(ticketId, operator, clientMessageId, operatorMessageId);
        return Map.of("success", true, "updated", updated);
    }

    public Map<String, Object> rejectReview(String ticketId, String operator) {
        return Map.of("success", true, "updated", dialogAiAssistantService.rejectPendingReview(ticketId, operator));
    }

    public Map<String, Object> submitLearningMapping(String ticketId,
                                                     String clientProblemMessage,
                                                     String operatorSolutionMessage,
                                                     String operator) {
        return Map.of(
                "success", true,
                "updated", dialogAiAssistantService.submitOperatorLearningMapping(
                        ticketId,
                        clientProblemMessage,
                        operatorSolutionMessage,
                        operator
                )
        );
    }

    public Map<String, Object> loadDecisionTrace(String ticketId, Integer limit) {
        return Map.of(
                "success", true,
                "trace", aiOpsRuntimeService.loadDecisionTrace(ticketId, limit)
        );
    }

    public Map<String, Object> reclassify(String ticketId,
                                          String message,
                                          String messageType,
                                          String attachment) {
        return Map.of(
                "success", true,
                "result", aiOpsRuntimeService.reclassify(ticketId, message, messageType, attachment)
        );
    }

    public Map<String, Object> retrieveDebug(String ticketId,
                                             String message,
                                             String messageType,
                                             String attachment,
                                             Integer limit) {
        return Map.of(
                "success", true,
                "result", aiOpsRuntimeService.retrieveDebug(ticketId, message, messageType, attachment, limit)
        );
    }

    public List<Map<String, Object>> loadPendingReviewsQueue(Integer limit) {
        return dialogAiAssistantService.loadPendingReviewsQueue(limit);
    }

    public Map<String, Object> approveReviewByKey(String queryKey, String operator) {
        return Map.of("success", true, "updated", dialogAiAssistantService.approvePendingReviewByKey(queryKey, operator));
    }

    public Map<String, Object> rejectReviewByKey(String queryKey, String operator) {
        return Map.of("success", true, "updated", dialogAiAssistantService.rejectPendingReviewByKey(queryKey, operator));
    }

    public Map<String, Object> loadSolutionMemory(Integer limit, String query) {
        return Map.of("success", true, "items", dialogAiAssistantService.loadSolutionMemory(limit, query));
    }

    public Map<String, Object> updateSolutionMemory(String queryKey,
                                                    String queryText,
                                                    String solutionText,
                                                    Boolean reviewRequired,
                                                    String operator) {
        boolean updated = dialogAiAssistantService.updateSolutionMemory(
                queryKey,
                queryText,
                solutionText,
                reviewRequired,
                operator
        );
        return Map.of("success", true, "updated", updated);
    }

    public Map<String, Object> deleteSolutionMemory(String queryKey, String operator) {
        return Map.of("success", true, "deleted", dialogAiAssistantService.deleteSolutionMemory(queryKey, operator));
    }

    public Map<String, Object> loadSolutionMemoryHistory(String queryKey, Integer limit) {
        return Map.of("success", true, "items", dialogAiAssistantService.loadSolutionMemoryHistory(queryKey, limit));
    }

    public Map<String, Object> rollbackSolutionMemory(String queryKey, Long historyId, String operator) {
        return Map.of("success", true, "updated", dialogAiAssistantService.rollbackSolutionMemory(queryKey, historyId, operator));
    }

    public Map<String, Object> loadIntents(Integer limit, String query, Boolean enabled) {
        return Map.of("success", true, "items", aiOpsRuntimeService.listIntents(limit, query, enabled));
    }

    public Map<String, Object> upsertIntent(String intentKey,
                                            String title,
                                            String description,
                                            String patternHints,
                                            String slotSchemaJson,
                                            Boolean enabled,
                                            Integer priority,
                                            Boolean autoReplyAllowed,
                                            Boolean assistOnly,
                                            Boolean requiresOperator,
                                            String safetyLevel,
                                            String notes) {
        return Map.of(
                "success", true,
                "updated", aiOpsRuntimeService.upsertIntent(
                        intentKey,
                        title,
                        description,
                        patternHints,
                        slotSchemaJson,
                        enabled,
                        priority,
                        autoReplyAllowed,
                        assistOnly,
                        requiresOperator,
                        safetyLevel,
                        notes
                )
        );
    }

    public Map<String, Object> loadKnowledgeUnits(Integer limit, String query, String status) {
        return Map.of("success", true, "items", aiOpsRuntimeService.listKnowledgeUnits(limit, query, status));
    }

    public Map<String, Object> upsertKnowledgeUnit(String unitKey,
                                                   String title,
                                                   String bodyText,
                                                   String intentKey,
                                                   String slotSignature,
                                                   String business,
                                                   String location,
                                                   String channel,
                                                   String status,
                                                   String sourceRef) {
        return Map.of(
                "success", true,
                "updated", aiOpsRuntimeService.upsertKnowledgeUnit(
                        unitKey,
                        title,
                        bodyText,
                        intentKey,
                        slotSignature,
                        business,
                        location,
                        channel,
                        status,
                        sourceRef
                )
        );
    }

    public Map<String, Object> loadMonitoringSummary(Integer days) {
        return Map.of("success", true, "summary", dialogAiAssistantService.loadMonitoringSummary(days));
    }

    public List<Map<String, Object>> loadMonitoringEvents(Integer days,
                                                          Integer limit,
                                                          String ticketId,
                                                          String eventType,
                                                          String actor) {
        return dialogAiAssistantService.loadMonitoringEvents(days, limit, ticketId, eventType, actor);
    }

    public Map<String, Object> loadOfflineEvalSummary() {
        return Map.of("success", true, "offline_eval", aiOpsRuntimeService.loadOfflineEvalSummary());
    }

    public Map<String, Object> runOfflineEvalNow(String actor) {
        return Map.of("success", true, "offline_eval", aiOpsRuntimeService.runOfflineEvalNow(actor));
    }

    public String buildMonitoringEventsCsv(List<Map<String, Object>> items) {
        StringBuilder out = new StringBuilder();
        out.append("id,ticket_id,event_type,actor,decision_type,decision_reason,source,score,detail,created_at\n");
        for (Map<String, Object> row : items) {
            out.append(csvCell(row.get("id"))).append(',')
                    .append(csvCell(row.get("ticket_id"))).append(',')
                    .append(csvCell(row.get("event_type"))).append(',')
                    .append(csvCell(row.get("actor"))).append(',')
                    .append(csvCell(row.get("decision_type"))).append(',')
                    .append(csvCell(row.get("decision_reason"))).append(',')
                    .append(csvCell(row.get("source"))).append(',')
                    .append(csvCell(row.get("score"))).append(',')
                    .append(csvCell(row.get("detail"))).append(',')
                    .append(csvCell(row.get("created_at"))).append('\n');
        }
        return out.toString();
    }

    private String csvCell(Object value) {
        String text = value != null ? String.valueOf(value) : "";
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
