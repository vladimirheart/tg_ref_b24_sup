package com.example.supportbot.telegram;

import java.util.List;
import java.util.Locale;

final class TelegramChoiceInputSupport {

    private TelegramChoiceInputSupport() {
    }

    static String resolveDirectAnswer(String rawAnswer, List<String> options) {
        if (rawAnswer == null) {
            return "";
        }
        String trimmed = rawAnswer.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            int numeric = Integer.parseInt(trimmed);
            if (numeric >= 1 && numeric <= options.size()) {
                return options.get(numeric - 1);
            }
        } catch (NumberFormatException ignored) {
            // Fall through to case-insensitive text matching.
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(trimmed)) {
                return option;
            }
        }
        return trimmed;
    }

    static String normalizeAlias(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[\\p{Punct}\\s]+", "");
    }

    static String matchOptionByValue(List<String> options, String value) {
        if (options == null || value == null) {
            return null;
        }
        for (String option : options) {
            if (option != null && option.equalsIgnoreCase(value)) {
                return option;
            }
        }
        return null;
    }

    static String mediaGuidance(List<String> options) {
        String prefix = "\u0414\u043b\u044f \u044d\u0442\u043e\u0433\u043e \u0432\u043e\u043f\u0440\u043e\u0441\u0430 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u043e\u0434\u0438\u043d \u0438\u0437 \u0432\u0430\u0440\u0438\u0430\u043d\u0442\u043e\u0432";
        if (options == null || options.isEmpty()) {
            return prefix + ".";
        }
        return prefix + ": " + String.join(", ", options) + ".";
    }
}
