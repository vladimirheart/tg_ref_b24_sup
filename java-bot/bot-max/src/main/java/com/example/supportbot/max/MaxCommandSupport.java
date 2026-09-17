package com.example.supportbot.max;

import java.util.Locale;

final class MaxCommandSupport {

    private MaxCommandSupport() {
    }

    static boolean isUnblockCommand(String text) {
        return "/unblock".equalsIgnoreCase(text);
    }

    static boolean isStartCommand(String text) {
        return "/start".equalsIgnoreCase(text);
    }

    static boolean isCancelCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return "/cancel".equals(normalized)
                || "cancel".equals(normalized)
                || "отмена".equals(normalized);
    }
}
