package com.example.supportbot.service;

import java.util.Locale;

public final class ConversationProblemTextSupport {

    private static final String FOLLOW_UP_PREFIX = "Уточнение после ответов на вопросы:";

    private ConversationProblemTextSupport() {
    }

    public static String mergeProblemText(String bootstrapText, String finalAnswer) {
        String initial = trimToNull(bootstrapText);
        String answer = trimToNull(finalAnswer);
        if (initial == null) {
            return answer;
        }
        if (answer == null) {
            return initial;
        }
        String normalizedInitial = normalizeForComparison(initial);
        String normalizedAnswer = normalizeForComparison(answer);
        if (normalizedInitial.equals(normalizedAnswer)) {
            return answer;
        }
        if (normalizedAnswer.contains(normalizedInitial)) {
            return answer;
        }
        if (normalizedInitial.contains(normalizedAnswer)) {
            return initial;
        }
        return initial + "\n\n" + FOLLOW_UP_PREFIX + "\n" + answer;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeForComparison(String value) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
