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
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;

    public DialogAiAssistantService(AiMonitoringService aiMonitoringService,
                                    AiInputNormalizerService aiInputNormalizerService,
                                    AiRetrievalService aiRetrievalService,
                                    DialogAiAssistantReviewService dialogAiAssistantReviewService,
                                    DialogAiSolutionMemoryService dialogAiSolutionMemoryService,
                                    DialogAiAssistantStateService dialogAiAssistantStateService,
                                    DialogAiAssistantConfigService dialogAiAssistantConfigService,
                                    DialogAiAssistantOperatorFeedbackService dialogAiAssistantOperatorFeedbackService,
                                    DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService,
                                    DialogAiAssistantMessageFlowService dialogAiAssistantMessageFlowService,
                                    LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
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
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
    }

    public void processIncomingClientMessage(String ticketId, String message) {
        processIncomingClientMessage(ticketId, message, null, null);
    }

    public void processIncomingClientMessage(String ticketId, String message, String messageType, String attachment) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        if (uniqueTicketId != null) {
            dialogAiAssistantMessageFlowService.processIncomingClientMessage(uniqueTicketId, message, messageType, attachment);
        }
    }

    public void registerOperatorReply(String ticketId, String operatorReply, String operator) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        if (uniqueTicketId != null) {
            dialogAiAssistantOperatorFeedbackService.registerOperatorReply(uniqueTicketId, operatorReply, operator);
        }
    }

    public boolean submitOperatorLearningMapping(String ticketId,
                                                 String clientProblemMessage,
                                                 String operatorSolutionMessage,
                                                 String operator) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null && dialogAiAssistantOperatorFeedbackService.submitOperatorLearningMapping(
                uniqueTicketId,
                clientProblemMessage,
                operatorSolutionMessage,
                operator
        );
    }

    public List<Map<String, Object>> loadOperatorSuggestions(String ticketId, Integer limit) {
        String t = uniqueTicketId(ticketId);
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
            item.put("memory_key", s.memoryKey());
            item.put("status", s.status());
            item.put("trust_level", s.trustLevel());
            item.put("source_type", s.sourceType());
            item.put("safety_level", s.safetyLevel());
            item.put("evidence_count", s.evidenceCount());
            item.put("reply", dialogAiAssistantSuggestionService.buildOperatorReplySuggestion(t, lastClient, s));
            item.put("explain", dialogAiAssistantSuggestionService.buildSuggestionExplain(t, lastClient, s));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> loadDialogControlState(String ticketId) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null
                ? dialogAiAssistantStateService.loadDialogControlState(uniqueTicketId)
                : Map.of();
    }

    public boolean updateDialogControlState(String ticketId,
                                            Boolean aiDisabled,
                                            Boolean autoReplyBlocked,
                                            String reason,
                                            String actor) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null && dialogAiAssistantStateService.updateDialogControlState(
                uniqueTicketId,
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
                                         String memoryKey,
                                         String actor) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        if (uniqueTicketId != null) {
            dialogAiAssistantOperatorFeedbackService.recordSuggestionFeedback(
                    uniqueTicketId,
                    decision,
                    source,
                    title,
                    snippet,
                    suggestedReply,
                    memoryKey,
                    actor
            );
        }
    }

    public Map<String, Object> loadPendingReview(String ticketId) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null ? dialogAiAssistantReviewService.loadPendingReview(uniqueTicketId) : Map.of();
    }

    public boolean approvePendingReview(String ticketId, String operator) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null && dialogAiAssistantReviewService.approvePendingReview(uniqueTicketId, operator);
    }

    public boolean approvePendingReview(String ticketId, String operator, Long clientMessageId, Long operatorMessageId) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null
                && dialogAiAssistantReviewService.approvePendingReview(uniqueTicketId, operator, clientMessageId, operatorMessageId);
    }

    public boolean rejectPendingReview(String ticketId, String operator) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null && dialogAiAssistantReviewService.rejectPendingReview(uniqueTicketId, operator);
    }

    public boolean isProcessing(String ticketId) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        return uniqueTicketId != null && dialogAiAssistantStateService.isProcessing(uniqueTicketId);
    }

    public void clearProcessing(String ticketId, String action, String error) {
        String uniqueTicketId = uniqueTicketId(ticketId);
        if (uniqueTicketId != null) {
            dialogAiAssistantStateService.clearProcessing(uniqueTicketId, action, error);
        }
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
                                        Boolean autoReplyAllowed,
                                        String operator) {
        return dialogAiSolutionMemoryService.updateSolutionMemory(
                queryKey,
                queryText,
                solutionText,
                reviewRequired,
                autoReplyAllowed,
                operator
        );
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
        String normalizedTicketId = trim(ticketId);
        if (normalizedTicketId != null && !legacyTicketIdJdbcGuard.hasUniqueTicket(normalizedTicketId)) {
            return List.of();
        }
        return aiMonitoringService.loadMonitoringEvents(days, limit, normalizedTicketId, eventType, actor);
    }

    public boolean approvePendingReviewByKey(String queryKey, String operator) {
        return dialogAiAssistantReviewService.approvePendingReviewByKey(queryKey, operator);
    }

    public boolean rejectPendingReviewByKey(String queryKey, String operator) {
        return dialogAiAssistantReviewService.rejectPendingReviewByKey(queryKey, operator);
    }

    private String uniqueTicketId(String ticketId) {
        String normalizedTicketId = trim(ticketId);
        return normalizedTicketId != null && legacyTicketIdJdbcGuard.hasUniqueTicket(normalizedTicketId)
                ? normalizedTicketId
                : null;
    }

    private String cut(String text, int len) { String t = trim(text); if (t == null) return ""; String c = t.replaceAll("\\s+", " ").trim(); return c.length() <= len ? c : c.substring(0, Math.max(0, len - 3)) + "..."; }
    private String trim(String v) { if (!StringUtils.hasText(v)) return null; String t = v.trim(); return t.isEmpty() ? null : t; }
    private String formatScore(double score) { return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score))); }
}



