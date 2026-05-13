package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DialogAiAssistantSuggestionService {

    private final AiIntentService aiIntentService;
    private final AiControlledLlmService aiControlledLlmService;

    public DialogAiAssistantSuggestionService(AiIntentService aiIntentService,
                                              AiControlledLlmService aiControlledLlmService) {
        this.aiIntentService = aiIntentService;
        this.aiControlledLlmService = aiControlledLlmService;
    }

    public String buildDeterministicReply(DialogAiAssistantSuggestionCandidate candidate) {
        String body = cleanTextForRetrieval(candidate != null ? candidate.snippet() : null);
        if (!StringUtils.hasText(body)) {
            body = "Не удалось найти точный ответ в базе. Ниже безопасный общий план действий.";
        }
        List<String> steps = splitIntoSteps(body, 3);
        StringBuilder reply = new StringBuilder();
        reply.append("Коротко: ").append(cut(firstSentence(body), 220)).append("\n\n");
        reply.append("Что сделать:\n");
        if (steps.isEmpty()) {
            reply.append("1. ").append(cut(body, 280)).append("\n");
        } else {
            for (int i = 0; i < steps.size(); i++) {
                reply.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }
        reply.append("\nЕсли после этих шагов проблема не решится, напишите, что именно не сработало.");
        return reply.toString();
    }

    public String buildAutoReply(String ticketId,
                                 String clientMessage,
                                 DialogAiAssistantSuggestionCandidate candidate,
                                 AiIntentService.IntentPolicy intentPolicy,
                                 boolean autoReplyRequested) {
        String fallback = buildDeterministicReply(candidate);
        if (candidate == null) {
            return fallback;
        }
        if (intentPolicy != null && intentPolicy.requiresOperator() && autoReplyRequested) {
            return fallback;
        }
        String intentKey = firstNonBlank(candidate.intentKey(), intentPolicy != null ? intentPolicy.intentKey() : null);
        AiControlledLlmService.TextResult composed = aiControlledLlmService.composeReply(
                ticketId,
                clientMessage,
                candidate.snippet(),
                candidate.sourceRef(),
                intentKey,
                autoReplyRequested
        );
        return StringUtils.hasText(composed.text()) ? composed.text() : fallback;
    }

    public String buildOperatorReplySuggestion(String ticketId,
                                               String clientMessage,
                                               DialogAiAssistantSuggestionCandidate candidate) {
        String sourceLabel = switch (candidate.source()) {
            case "memory" -> "память подтвержденных решений";
            case "knowledge" -> "база знаний";
            case "tasks" -> "связанные задачи";
            case "history" -> "история похожих диалогов";
            case "applicant_history" -> "история заявителя";
            default -> "доступные данные";
        };
        AiIntentService.IntentPolicy intentPolicy = aiIntentService.resolvePolicy(candidate.intentKey());
        String prepared = buildAutoReply(ticketId, clientMessage, candidate, intentPolicy, false);
        return "Подсказка на основе источника \"" + sourceLabel + "\":\n\n" + prepared;
    }

    public String buildSuggestionExplain(String ticketId,
                                         String clientMessage,
                                         DialogAiAssistantSuggestionCandidate candidate) {
        String sourceExplain = switch (String.valueOf(candidate.source()).toLowerCase(Locale.ROOT)) {
            case "memory" -> "Подсказка выбрана из подтвержденной памяти решений.";
            case "knowledge" -> "Подсказка собрана из базы знаний.";
            case "tasks" -> "Подсказка опирается на связанные задачи и инструкции.";
            case "history" -> "Подсказка основана на похожих диалогах.";
            case "applicant_history" -> "Подсказка учитывает историю заявителя.";
            default -> "Подсказка сформирована на основе доступных данных.";
        };
        AiControlledLlmService.TextResult explained = aiControlledLlmService.explainSuggestion(
                ticketId,
                clientMessage,
                candidate.snippet(),
                candidate.source(),
                candidate.trustLevel(),
                candidate.intentKey(),
                candidate.evidenceCount()
        );
        if (StringUtils.hasText(explained.text())) {
            return explained.text();
        }
        String trust = StringUtils.hasText(candidate.trustLevel()) ? candidate.trustLevel() : "unknown";
        return sourceExplain + " Итоговый confidence: " + formatScore(candidate.score())
                + ". trust=" + trust
                + ", intent=" + firstNonBlank(candidate.intentKey(), "unknown")
                + ", evidence=" + Math.max(1, candidate.evidenceCount()) + ".";
    }

    private String cleanTextForRetrieval(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = stripHtml(value);
        text = text.replaceAll("```[\\s\\S]*?```", " ");
        text = text.replaceAll("\\[[^\\]]+\\]\\([^\\)]+\\)", " ");
        text = text.replaceAll("https?://\\S+", " ");
        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private String firstSentence(String text) {
        String prepared = cleanTextForRetrieval(text);
        if (!StringUtils.hasText(prepared)) {
            return "";
        }
        String[] parts = prepared.split("(?<=[.!?])\\s+");
        return parts.length > 0 ? parts[0].trim() : prepared;
    }

    private List<String> splitIntoSteps(String text, int max) {
        String prepared = cleanTextForRetrieval(text);
        if (!StringUtils.hasText(prepared) || max <= 0) {
            return List.of();
        }
        String[] parts = prepared.split("(?<=[.!?])\\s+");
        List<String> steps = new ArrayList<>();
        for (String part : parts) {
            String step = cut(part, 180);
            if (!StringUtils.hasText(step)) {
                continue;
            }
            steps.add(step);
            if (steps.size() >= max) {
                break;
            }
        }
        return steps;
    }

    private String stripHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String cut(String text, int len) {
        String trimmed = trim(text);
        if (trimmed == null) {
            return "";
        }
        String compact = trimmed.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score)));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
}
