package com.example.supportbot.max;

import java.util.Set;

final class MaxFeedbackInputSupport {

    private MaxFeedbackInputSupport() {
    }

    static String normalizeNumericRating(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.matches("\\d+") ? normalized : null;
    }

    static boolean isAllowedRating(String normalized, Set<String> allowed) {
        return allowed.contains(normalized);
    }

    static int parseRating(String normalized) {
        return Integer.parseInt(normalized);
    }

    static String buildInvalidRatingPrompt(int scale) {
        return "Отправьте число от 1 до " + scale;
    }
}
