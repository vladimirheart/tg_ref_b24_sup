package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DialogAiAssistantOperatorFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(DialogAiAssistantOperatorFeedbackService.class);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> STOP = Set.of("и", "в", "на", "не", "что", "как", "для", "или", "по", "из", "к", "у", "о", "об", "the", "a", "an", "to", "of", "in", "on", "for", "and", "or", "is", "are", "be");

    private final AiLearningService aiLearningService;
    private final NotificationService notificationService;
    private final DialogAiAssistantPersistenceService persistenceService;
    private final DialogAiAssistantStateService stateService;
    private final DialogAiAssistantConfigService configService;

    public DialogAiAssistantOperatorFeedbackService(AiLearningService aiLearningService,
                                                    NotificationService notificationService,
                                                    DialogAiAssistantPersistenceService persistenceService,
                                                    DialogAiAssistantStateService stateService,
                                                    DialogAiAssistantConfigService configService) {
        this.aiLearningService = aiLearningService;
        this.notificationService = notificationService;
        this.persistenceService = persistenceService;
        this.stateService = stateService;
        this.configService = configService;
    }

    public void registerOperatorReply(String ticketId, String operatorReply, String operator) {
        try {
            String ticket = persistenceService.trim(ticketId);
            String reply = persistenceService.trim(operatorReply);
            if (ticket == null || reply == null) {
                return;
            }
            String lastClient = persistenceService.loadLastClientMessage(ticket);
            if (!StringUtils.hasText(lastClient)) {
                return;
            }
            AiLearningService.UpsertResult upsertResult = aiLearningService.upsertLearningSolution(
                    ticket,
                    lastClient,
                    reply,
                    operator,
                    configService.resolveDifferenceThreshold()
            );
            if (upsertResult == null) {
                return;
            }
            String key = upsertResult.queryKey();
            String suggested = stateService.loadLastSuggestedReply(ticket);
            if (!StringUtils.hasText(suggested)
                    || !isMeaningfullyDifferent(suggested, reply)
                    || stateService.hasOpenCorrectionRequest(ticket)) {
                return;
            }
            notificationService.notifyDialogParticipants(
                    ticket,
                    "AI suggestion for ticket " + ticket + " differs from operator reply. " +
                            "Please refine learning markup: 1) which client message describes the issue; " +
                            "2) which operator message is the correct solution.",
                    "/dialogs?ticketId=" + ticket,
                    null
            );
            stateService.clearProcessing(
                    ticket,
                    "operator_correction_requested",
                    "operator_reply_differs",
                    "review",
                    "operator_reply_differs",
                    null,
                    configService.resolveAgentMode()
            );
            persistenceService.recordAiEvent(
                    ticket,
                    "ai_agent_correction_requested",
                    persistenceService.trim(operator),
                    "review",
                    "operator_reply_differs",
                    null,
                    null,
                    "Operator reply differs from AI memory",
                    Map.of(
                            "ticket_id", ticket,
                            "memory_key", key,
                            "learning_action", upsertResult.action()
                    )
            );
            persistenceService.updatePendingSolutionText(key, reply);
        } catch (Exception ex) {
            log.debug("registerOperatorReply failed for {}: {}", ticketId, ex.getMessage());
        }
    }

    public boolean submitOperatorLearningMapping(String ticketId,
                                                 String clientProblemMessage,
                                                 String operatorSolutionMessage,
                                                 String operator) {
        String ticket = persistenceService.trim(ticketId);
        String client = persistenceService.trim(clientProblemMessage);
        String solution = persistenceService.trim(operatorSolutionMessage);
        if (ticket == null || client == null || solution == null) {
            return false;
        }
        try {
            AiLearningService.UpsertResult result = aiLearningService.upsertLearningSolution(
                    ticket,
                    client,
                    solution,
                    persistenceService.trim(operator),
                    configService.resolveDifferenceThreshold()
            );
            if (result == null) {
                return false;
            }
            stateService.clearProcessing(
                    ticket,
                    "operator_learning_mapping_submitted",
                    null,
                    "review",
                    "operator_mapping_submitted",
                    null,
                    configService.resolveAgentMode()
            );
            persistenceService.recordAiEvent(
                    ticket,
                    "ai_agent_operator_mapping_submitted",
                    persistenceService.trim(operator),
                    "review",
                    "operator_mapping_submitted",
                    null,
                    null,
                    result.action(),
                    Map.of(
                            "query_key", result.queryKey(),
                            "mapping_action", result.action()
                    )
            );
            return true;
        } catch (Exception ex) {
            log.debug("submitOperatorLearningMapping failed for {}: {}", ticketId, ex.getMessage());
            return false;
        }
    }

    public void recordSuggestionFeedback(String ticketId,
                                         String decision,
                                         String source,
                                         String title,
                                         String snippet,
                                         String suggestedReply,
                                         String actor) {
        String ticket = persistenceService.trim(ticketId);
        String normalizedDecision = persistenceService.trim(decision);
        if (ticket == null || normalizedDecision == null) {
            return;
        }
        try {
            persistenceService.persistSuggestionFeedback(ticket, normalizedDecision, source, title, snippet, suggestedReply, actor);
            persistenceService.recordAiEvent(
                    ticket,
                    resolveFeedbackEventType(normalizedDecision),
                    persistenceService.trim(actor),
                    "suggestion_feedback",
                    normalizedDecision.toLowerCase(Locale.ROOT),
                    persistenceService.trim(source),
                    null,
                    persistenceService.trim(title),
                    Map.of("decision", normalizedDecision.toLowerCase(Locale.ROOT))
            );
        } catch (Exception ex) {
            log.debug("Failed to persist ai feedback for {}: {}", ticket, ex.getMessage());
        }
    }

    private String resolveFeedbackEventType(String decision) {
        String normalized = decision.toLowerCase(Locale.ROOT);
        if ("accepted".equals(normalized)) {
            return "ai_agent_suggestion_applied";
        }
        if ("rejected".equals(normalized)) {
            return "ai_agent_suggestion_rejected";
        }
        return "ai_agent_suggestion_feedback";
    }

    private boolean isMeaningfullyDifferent(String left, String right) {
        return similarity(left, right) < configService.resolveDifferenceThreshold();
    }

    private double similarity(String left, String right) {
        Set<String> x = tokenize(left);
        Set<String> y = tokenize(right);
        if (x.isEmpty() || y.isEmpty()) {
            return 0d;
        }
        int intersection = 0;
        for (String token : x) {
            if (y.contains(token)) {
                intersection++;
            }
        }
        int union = x.size() + y.size() - intersection;
        return union <= 0 ? 0d : intersection / (double) union;
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            String trimmed = persistenceService.trim(token);
            if (trimmed == null || trimmed.length() < 2 || STOP.contains(trimmed)) {
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }
}
