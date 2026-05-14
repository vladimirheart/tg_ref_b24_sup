package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogAiAssistantService {
    private static final int DEFAULT_SUGGESTION_LIMIT = 3;
    private final AiMonitoringService aiMonitoringService;
    private final AiInputNormalizerService aiInputNormalizerService;
    private final AiRetrievalService aiRetrievalService;
    private final DialogAiAssistantReviewService dialogAiAssistantReviewService;
    private final DialogAiSolutionMemoryService dialogAiSolutionMemoryService;
    private final DialogAiAssistantStateService dialogAiAssistantStateService;
    private final DialogAiAssistantConfigService dialogAiAssistantConfigService;
    private final DialogAiAssistantOperatorFeedbackService dialogAiAssistantOperatorFeedbackService;
    private final DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService;
    private final DialogAiAssistantMessageFlowService dialogAiAssistantMessageFlowService;

    public DialogAiAssistantService(AiMonitoringService aiMonitoringService,
                                    AiInputNormalizerService aiInputNormalizerService,
                                    AiRetrievalService aiRetrievalService,
                                    DialogAiAssistantReviewService dialogAiAssistantReviewService,
                                    DialogAiSolutionMemoryService dialogAiSolutionMemoryService,
                                    DialogAiAssistantStateService dialogAiAssistantStateService,
                                    DialogAiAssistantConfigService dialogAiAssistantConfigService,
                                    DialogAiAssistantOperatorFeedbackService dialogAiAssistantOperatorFeedbackService,
                                    DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService,
                                    DialogAiAssistantMessageFlowService dialogAiAssistantMessageFlowService) {
        this.aiMonitoringService = aiMonitoringService;
        this.aiInputNormalizerService = aiInputNormalizerService;
        this.aiRetrievalService = aiRetrievalService;
        this.dialogAiAssistantReviewService = dialogAiAssistantReviewService;
        this.dialogAiSolutionMemoryService = dialogAiSolutionMemoryService;
        this.dialogAiAssistantStateService = dialogAiAssistantStateService;
        this.dialogAiAssistantConfigService = dialogAiAssistantConfigService;
        this.dialogAiAssistantOperatorFeedbackService = dialogAiAssistantOperatorFeedbackService;
        this.dialogAiAssistantSuggestionService = dialogAiAssistantSuggestionService;
        this.dialogAiAssistantMessageFlowService = dialogAiAssistantMessageFlowService;
    }

    public void processIncomingClientMessage(String ticketId, String message) {
        processIncomingClientMessage(ticketId, message, null, null);
    }

    public void processIncomingClientMessage(String ticketId, String message, String messageType, String attachment) {
        dialogAiAssistantMessageFlowService.processIncomingClientMessage(ticketId, message, messageType, attachment);
    }

    public void registerOperatorReply(String ticketId, String operatorReply, String operator) {
        dialogAiAssistantOperatorFeedbackService.registerOperatorReply(ticketId, operatorReply, operator);
    }

    public boolean submitOperatorLearningMapping(String ticketId,
                                                 String clientProblemMessage,
                                                 String operatorSolutionMessage,
                                                 String operator) {
        return dialogAiAssistantOperatorFeedbackService.submitOperatorLearningMapping(
                ticketId,
                clientProblemMessage,
                operatorSolutionMessage,
                operator
        );
    }

    public List<Map<String, Object>> loadOperatorSuggestions(String ticketId, Integer limit) {
        String t = trim(ticketId);
        if (t == null) return List.of();
        AiInputNormalizerService.IncomingPayload payload = aiInputNormalizerService.loadLastClientPayload(t);
        String lastClient = payload != null ? payload.message() : null;
        if (!StringUtils.hasText(lastClient)) return List.of();
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : DEFAULT_SUGGESTION_LIMIT, 8));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DialogAiAssistantSuggestionCandidate s : findSuggestions(t, lastClient, safeLimit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", s.source());
            item.put("title", s.title());
            item.put("score", s.score());
            item.put("score_label", formatScore(s.score()));
            item.put("snippet", s.snippet());
            item.put("reply", dialogAiAssistantSuggestionService.buildOperatorReplySuggestion(t, lastClient, s));
            item.put("explain", dialogAiAssistantSuggestionService.buildSuggestionExplain(t, lastClient, s));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> loadDialogControlState(String ticketId) {
        return dialogAiAssistantStateService.loadDialogControlState(ticketId);
    }

    public boolean updateDialogControlState(String ticketId,
                                            Boolean aiDisabled,
                                            Boolean autoReplyBlocked,
                                            String reason,
                                            String actor) {
        return dialogAiAssistantStateService.updateDialogControlState(
                ticketId,
                aiDisabled,
                autoReplyBlocked,
                reason,
                actor,
                dialogAiAssistantConfigService.resolveAgentMode()
        );
    }

    public void recordSuggestionFeedback(String ticketId,
                                         String decision,
                                         String source,
                                         String title,
                                         String snippet,
                                         String suggestedReply,
                                         String actor) {
        dialogAiAssistantOperatorFeedbackService.recordSuggestionFeedback(
                ticketId,
                decision,
                source,
                title,
                snippet,
                suggestedReply,
                actor
        );
    }

    public Map<String, Object> loadPendingReview(String ticketId) {
        return dialogAiAssistantReviewService.loadPendingReview(ticketId);
    }

    public boolean approvePendingReview(String ticketId, String operator) {
        return dialogAiAssistantReviewService.approvePendingReview(ticketId, operator);
    }

    public boolean approvePendingReview(String ticketId, String operator, Long clientMessageId, Long operatorMessageId) {
        return dialogAiAssistantReviewService.approvePendingReview(ticketId, operator, clientMessageId, operatorMessageId);
    }

    public boolean rejectPendingReview(String ticketId, String operator) {
        return dialogAiAssistantReviewService.rejectPendingReview(ticketId, operator);
    }

    public boolean isProcessing(String ticketId) {
        return dialogAiAssistantStateService.isProcessing(ticketId);
    }

    public void clearProcessing(String ticketId, String action, String error) {
        dialogAiAssistantStateService.clearProcessing(ticketId, action, error);
    }

    private List<DialogAiAssistantSuggestionCandidate> findSuggestions(String ticketId, String query, int limit) {
        return mapSuggestions(aiRetrievalService.findSuggestions(ticketId, query, limit));
    }

    private List<DialogAiAssistantSuggestionCandidate> mapSuggestions(List<AiRetrievalService.Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<DialogAiAssistantSuggestionCandidate> mapped = new java.util.ArrayList<>(candidates.size());
        for (AiRetrievalService.Candidate candidate : candidates) {
            mapped.add(new DialogAiAssistantSuggestionCandidate(
                    candidate.source(),
                    candidate.title(),
                    candidate.snippet(),
                    candidate.score(),
                    candidate.memoryKey(),
                    candidate.status(),
                    candidate.trustLevel(),
                    candidate.sourceType(),
                    candidate.safetyLevel(),
                    candidate.sourceRef(),
                    candidate.intentKey(),
                    candidate.slotSignature(),
                    candidate.canonicalKey(),
                    candidate.evidenceCount(),
                    candidate.trace(),
                    candidate.updatedAt(),
                    candidate.stale()
            ));
        }
        return mapped;
    }

    public List<Map<String, Object>> loadPendingReviewsQueue(Integer limit) {
        return dialogAiAssistantReviewService.loadPendingReviewsQueue(limit);
    }

    public List<Map<String, Object>> loadSolutionMemory(Integer limit, String query) {
        return dialogAiSolutionMemoryService.loadSolutionMemory(limit, query);
    }

    public boolean updateSolutionMemory(String queryKey,
                                        String queryText,
                                        String solutionText,
                                        Boolean reviewRequired,
                                        String operator) {
        return dialogAiSolutionMemoryService.updateSolutionMemory(queryKey, queryText, solutionText, reviewRequired, operator);
    }

    public boolean deleteSolutionMemory(String queryKey, String operator) {
        return dialogAiSolutionMemoryService.deleteSolutionMemory(queryKey, operator);
    }

    public List<Map<String, Object>> loadSolutionMemoryHistory(String queryKey, Integer limit) {
        return dialogAiSolutionMemoryService.loadSolutionMemoryHistory(queryKey, limit);
    }

    public boolean rollbackSolutionMemory(String queryKey, Long historyId, String operator) {
        return dialogAiSolutionMemoryService.rollbackSolutionMemory(queryKey, historyId, operator);
    }

    public Map<String, Object> loadMonitoringSummary(Integer days) {
        return aiMonitoringService.loadMonitoringSummary(days);
    }

    public List<Map<String, Object>> loadMonitoringEvents(Integer days,
                                                          Integer limit,
                                                          String ticketId,
                                                          String eventType,
                                                          String actor) {
        return aiMonitoringService.loadMonitoringEvents(days, limit, ticketId, eventType, actor);
    }

    public boolean approvePendingReviewByKey(String queryKey, String operator) {
        return dialogAiAssistantReviewService.approvePendingReviewByKey(queryKey, operator);
    }

    public boolean rejectPendingReviewByKey(String queryKey, String operator) {
        return dialogAiAssistantReviewService.rejectPendingReviewByKey(queryKey, operator);
    }

    private String cut(String text, int len) { String t = trim(text); if (t == null) return ""; String c = t.replaceAll("\\s+", " ").trim(); return c.length() <= len ? c : c.substring(0, Math.max(0, len - 3)) + "..."; }
    private String trim(String v) { if (!StringUtils.hasText(v)) return null; String t = v.trim(); return t.isEmpty() ? null : t; }
    private String formatScore(double score) { return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score))); }
}



