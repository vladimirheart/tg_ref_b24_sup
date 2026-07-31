package com.example.panel.storage;

import org.springframework.util.StringUtils;

import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AttachmentStorageKeyResolver {

    private AttachmentStorageKeyResolver() {
    }

    public static String normalizeStorageKey(String ticketId, String rawAttachment) {
        String normalized = normalizeReference(rawAttachment);
        if (!StringUtils.hasText(normalized) || isApiReference(normalized) || isExternalUrl(normalized)) {
            return null;
        }
        String attachmentSuffix = extractAttachmentsSuffix(normalized);
        if (StringUtils.hasText(attachmentSuffix)) {
            return attachmentSuffix;
        }
        if (normalized.contains("/")) {
            return normalized;
        }
        if (StringUtils.hasText(ticketId)) {
            return normalizeReference(ticketId) + "/" + normalized;
        }
        return null;
    }

    public static String resolveOriginalName(String preferredName, String rawAttachment, String storageKey) {
        if (StringUtils.hasText(preferredName)) {
            return preferredName.trim();
        }
        String candidate = extractFileName(StringUtils.hasText(storageKey) ? storageKey : rawAttachment);
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String decoded = decode(candidate);
        String stripped = stripStoredAttachmentPrefix(decoded);
        return StringUtils.hasText(stripped) ? stripped : decoded;
    }

    public static boolean isExternalUrl(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return false;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    public static String guessMimeType(String preferredMimeType, String originalName, String storageKey, String messageType) {
        if (StringUtils.hasText(preferredMimeType)) {
            return preferredMimeType.trim();
        }
        String guessedByName = URLConnection.guessContentTypeFromName(firstNonBlank(originalName, extractFileName(storageKey)));
        if (StringUtils.hasText(guessedByName)) {
            return guessedByName;
        }
        String normalizedType = StringUtils.hasText(messageType) ? messageType.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalizedType) {
            case "photo", "image" -> "image/*";
            case "video", "video_note" -> "video/*";
            case "voice", "audio" -> "audio/*";
            case "document", "file" -> "application/octet-stream";
            default -> null;
        };
    }

    public static String extractAttachmentsSuffix(String raw) {
        String normalized = normalizeReference(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        String marker = "/attachments/";
        int markerIndex = lowered.indexOf(marker);
        if (markerIndex >= 0) {
            return normalizeReference(normalized.substring(markerIndex + marker.length()));
        }
        if (lowered.startsWith("attachments/")) {
            return normalizeReference(normalized.substring("attachments/".length()));
        }
        return null;
    }

    public static String extractFileName(String raw) {
        String normalized = normalizeReference(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    public static String stripStoredAttachmentPrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        int separatorIndex = normalized.indexOf('_');
        if (separatorIndex > 0) {
            String prefix = normalized.substring(0, separatorIndex);
            if (prefix.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                return normalized.substring(separatorIndex + 1).trim();
            }
        }
        return normalized;
    }

    private static boolean isApiReference(String normalized) {
        return normalized.startsWith("/api/")
                || normalized.startsWith("api/")
                || normalized.startsWith("attachments-api/");
    }

    private static String normalizeReference(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.trim();
    }

    private static String decode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return URLDecoder.decode(value.trim(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value.trim();
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
