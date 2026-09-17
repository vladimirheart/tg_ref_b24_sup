package com.example.supportbot.max;

import com.fasterxml.jackson.databind.JsonNode;

final class MaxWebhookInputSupport {

    private MaxWebhookInputSupport() {
    }

    static boolean isSecretValid(String expected, String provided) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(provided);
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.path(field) : null;
        return value == null || value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    static Long asLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
