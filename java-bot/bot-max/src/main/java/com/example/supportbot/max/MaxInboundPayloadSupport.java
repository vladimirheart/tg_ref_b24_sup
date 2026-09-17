package com.example.supportbot.max;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

final class MaxInboundPayloadSupport {

    private MaxInboundPayloadSupport() {
    }

    static ClientProfile resolveClientProfile(JsonNode message, Long userId) {
        JsonNode sender = message != null ? message.path("sender") : null;
        ClientProfile profile = resolveClientProfileFromSender(sender);
        String username = profile.username();
        String clientName = profile.clientName();
        if ((username == null || username.isBlank()) && userId != null) {
            username = "max_" + userId;
        }
        if ((clientName == null || clientName.isBlank()) && userId != null) {
            clientName = "MAX user " + userId;
        }
        return new ClientProfile(trimOrNull(username), trimOrNull(clientName), userId);
    }

    /**
     * MAX places the original content under link for forwarded messages and may
     * leave the outer body null. Keep the outer sender as the client while
     * storing the original author separately for the operator timeline.
     */
    static InboundPayload resolveInboundPayload(JsonNode message, ClientProfile clientProfile) {
        String directText = extractMessageText(message);
        List<IncomingAttachment> attachments = extractIncomingAttachments(message);
        JsonNode forwardedMessage = resolveForwardedMessage(message);
        boolean forwarded = forwardedMessage != null;

        if (directText.isBlank() && forwarded) {
            directText = extractMessageText(forwardedMessage);
        }
        if (attachments.isEmpty() && forwarded) {
            attachments = extractIncomingAttachments(forwardedMessage);
        }

        return new InboundPayload(
                directText,
                attachments,
                forwarded ? resolveForwardedFrom(message, forwardedMessage, clientProfile) : null
        );
    }

    static String normalizeAttachmentType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase();
        if (type.contains("animation") || type.contains("gif")) {
            return "animation";
        }
        if (type.contains("video")) {
            return "video";
        }
        if (type.contains("audio") || type.contains("voice")) {
            return "audio";
        }
        if (type.contains("photo") || type.contains("image") || type.contains("sticker")) {
            return "photo";
        }
        if (type.contains("doc") || type.contains("file")) {
            return "document";
        }
        return "attachment";
    }

    private static String extractMessageText(JsonNode message) {
        if (message == null || message.isNull() || message.isMissingNode()) {
            return "";
        }
        String bodyText = text(message.path("body"), "text").trim();
        if (!bodyText.isBlank()) {
            return bodyText;
        }
        return text(message, "text").trim();
    }

    private static JsonNode resolveForwardedMessage(JsonNode message) {
        if (message == null || message.isNull() || message.isMissingNode()) {
            return null;
        }
        JsonNode link = message.path("link");
        if (link.isMissingNode() || link.isNull() || !isForwardLink(link)) {
            return null;
        }
        for (String field : List.of("message", "linked_message", "forwarded_message", "forward", "source")) {
            JsonNode candidate = link.path(field);
            if (candidate.isObject()) {
                return candidate;
            }
        }
        return link.isObject() ? link : null;
    }

    private static boolean isForwardLink(JsonNode link) {
        String type = firstNonBlank(text(link, "type"), text(link, "link_type"));
        return type != null && ("forward".equalsIgnoreCase(type) || "forwarded".equalsIgnoreCase(type));
    }

    private static String resolveForwardedFrom(JsonNode message,
                                               JsonNode forwardedMessage,
                                               ClientProfile outerClient) {
        List<JsonNode> authorCandidates = new ArrayList<>();
        collectForwardedAuthorCandidates(authorCandidates, forwardedMessage);
        JsonNode link = message != null ? message.path("link") : null;
        if (link != forwardedMessage) {
            collectForwardedAuthorCandidates(authorCandidates, link);
        }
        for (JsonNode candidate : authorCandidates) {
            ClientProfile profile = resolveClientProfileFromSender(candidate);
            if (isSameClient(profile, outerClient)) {
                continue;
            }
            String label = profile.displayLabel();
            if (label != null && !label.isBlank() && !label.startsWith("MAX user ")) {
                return label;
            }
        }
        return null;
    }

    private static void collectForwardedAuthorCandidates(List<JsonNode> candidates, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        for (String field : List.of(
                "author", "original_author", "original_sender", "forwarded_from", "from", "user", "sender", "owner"
        )) {
            JsonNode candidate = node.path(field);
            if (candidate.isObject()) {
                candidates.add(candidate);
            }
        }
    }

    private static boolean isSameClient(ClientProfile candidate, ClientProfile outerClient) {
        if (candidate == null || outerClient == null) {
            return false;
        }
        if (candidate.userId() != null && outerClient.userId() != null) {
            return candidate.userId().equals(outerClient.userId());
        }
        return candidate.identity() != null && candidate.identity().equalsIgnoreCase(outerClient.identity());
    }

    private static ClientProfile resolveClientProfileFromSender(JsonNode sender) {
        String username = firstNonBlank(
                text(sender, "username"),
                text(sender, "user_name"),
                text(sender, "screen_name"),
                text(sender, "login")
        );
        String clientName = firstNonBlank(
                text(sender, "name"),
                text(sender, "display_name"),
                joinNames(text(sender, "first_name"), text(sender, "last_name")),
                joinNames(text(sender, "firstName"), text(sender, "lastName")),
                username
        );
        return new ClientProfile(trimOrNull(username), trimOrNull(clientName), asLong(sender != null ? sender.path("user_id") : null));
    }

    private static List<IncomingAttachment> extractIncomingAttachments(JsonNode message) {
        List<IncomingAttachment> result = new ArrayList<>();
        if (message == null || message.isNull() || message.isMissingNode()) {
            return result;
        }
        collectIncomingAttachments(result, message.path("attachments"));
        JsonNode body = message.path("body");
        collectIncomingAttachments(result, body.path("attachments"));
        collectIncomingAttachments(result, body.path("media"));
        collectIncomingAttachments(result, body.path("files"));
        return result;
    }

    private static void collectIncomingAttachments(List<IncomingAttachment> result, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                addIncomingAttachment(result, item);
            }
            return;
        }
        addIncomingAttachment(result, node);
    }

    private static void addIncomingAttachment(List<IncomingAttachment> result, JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return;
        }
        String type = firstNonBlank(
                text(raw, "type"),
                text(raw, "kind"),
                text(raw, "media_type"),
                text(raw, "mime_type"),
                "attachment"
        );
        String url = firstNonBlank(
                text(raw, "url"),
                text(raw, "link"),
                text(raw, "download_url"),
                text(raw, "downloadUrl"),
                text(raw, "src"),
                text(raw.path("file"), "url"),
                text(raw.path("photo"), "url"),
                text(raw.path("video"), "url"),
                text(raw.path("payload"), "url")
        );
        String name = firstNonBlank(
                text(raw, "name"),
                text(raw, "file_name"),
                text(raw, "filename"),
                text(raw.path("file"), "name")
        );
        if ((url == null || url.isBlank()) && (name == null || name.isBlank())) {
            return;
        }
        result.add(new IncomingAttachment(type, trimOrNull(url), trimOrNull(name)));
    }

    private static String joinNames(String first, String last) {
        String left = trimOrNull(first);
        String right = trimOrNull(last);
        if (left == null && right == null) {
            return null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + " " + right;
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

    record IncomingAttachment(String type, String url, String name) {
        String urlOrName() {
            if (url != null && !url.isBlank()) {
                return url;
            }
            return name;
        }
    }

    record InboundPayload(String text,
                          List<IncomingAttachment> attachments,
                          String forwardedFrom) {
    }

    record ClientProfile(String username, String clientName, Long userId) {
        String identity() {
            if (username != null && !username.isBlank()) {
                return username;
            }
            return userId != null ? userId.toString() : null;
        }

        String displayLabel() {
            if (clientName != null && !clientName.isBlank()) {
                if (username != null && !username.isBlank() && !clientName.equalsIgnoreCase(username)) {
                    return clientName + " (@" + username + ")";
                }
                return clientName;
            }
            if (username != null && !username.isBlank()) {
                return "@" + username;
            }
            return userId != null ? "MAX user " + userId : "\u043a\u043b\u0438\u0435\u043d\u0442";
        }
    }
}
