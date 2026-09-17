package com.example.supportbot.max;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

final class MaxDeliveryIdentitySupport {

    private MaxDeliveryIdentitySupport() {
    }

    static String buildDeliveryKey(JsonNode update) {
        String updateId = firstNonBlank(
                text(update, "update_id"),
                text(update, "event_id")
        );
        if (updateId != null) {
            return "update:" + updateId;
        }
        JsonNode message = update.path("message");
        String messageId = firstNonBlank(
                text(message, "message_id"),
                text(message.path("body"), "mid"),
                text(message.path("body"), "message_id")
        );
        if (messageId != null) {
            return "message:" + messageId;
        }
        String senderId = text(message.path("sender"), "user_id");
        String chatId = firstNonBlank(
                text(message.path("recipient"), "chat_id"),
                text(message.path("recipient"), "user_id")
        );
        String createdAt = firstNonBlank(
                text(message, "timestamp"),
                text(message.path("body"), "created_at"),
                text(message.path("body"), "timestamp")
        );
        if (hasText(senderId) || hasText(chatId) || hasText(createdAt)) {
            return "message_created|sender=" + senderId + "|chat=" + chatId + "|created_at=" + createdAt;
        }
        return update != null ? update.toString() : "missing-update";
    }

    static Long resolveProviderMessageId(JsonNode update, JsonNode message) {
        Long numericId = asLong(message.path("message_id"));
        if (numericId == null) {
            numericId = asLong(message.path("body").path("mid"));
        }
        if (numericId != null) {
            return numericId;
        }
        UUID stableId = UUID.nameUUIDFromBytes(buildDeliveryKey(update).getBytes(StandardCharsets.UTF_8));
        long value = stableId.getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0L ? 1L : value;
    }

    static Long resolveReplyToProviderMessageId(JsonNode message) {
        JsonNode link = message != null ? message.path("link") : null;
        if (link == null || link.isMissingNode() || link.isNull()
                || !"reply".equalsIgnoreCase(text(link, "type"))) {
            return null;
        }

        JsonNode linkedMessage = link.path("message");
        for (JsonNode candidate : List.of(
                link.path("message_id"),
                link.path("mid"),
                linkedMessage.path("message_id"),
                linkedMessage.path("mid"),
                linkedMessage.path("body").path("message_id"),
                linkedMessage.path("body").path("mid"))) {
            Long messageId = asLong(candidate);
            if (messageId != null) {
                return messageId;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            String normalized = trimOrNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean hasText(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.path(field) : null;
        return value == null || value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static Long asLong(JsonNode node) {
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
