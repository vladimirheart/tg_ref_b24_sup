package com.example.panel.storage;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AttachmentStorageKeyResolver {

    private AttachmentStorageKeyResolver() {
    }

    public enum LegacyDialogReferenceKind { FILENAME, STORAGE_KEY, STORED_PATH }

    public record LegacyDialogReference(LegacyDialogReferenceKind kind, String reference, String ticketId) { }

    /** Builds a locator only; download still requires the existing ticket/owner/permission guards. */
    public static String buildTicketAttachmentDownloadUrl(String ticketId, String filename) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(filename)) {
            return null;
        }
        if (!ticketId.matches("[A-Za-z0-9._-]+") || filename.contains("/") || filename.contains("\\")
                || filename.contains("%")) {
            return "/api/attachments/tickets/by-ticket?ticketId=" + encodeDownloadQueryValue(ticketId)
                    + "&filename=" + encodeDownloadQueryValue(filename);
        }
        return "/api/attachments/tickets/" + UriUtils.encodePathSegment(ticketId, StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);
    }

    private static String encodeDownloadQueryValue(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8).replace("+", "%2B");
    }

    /**
     * Classifies a local legacy locator, not its owner. API envelopes are decoded once
     * using servlet query/path semantics; literal storage references are never decoded.
     */
    public static LegacyDialogReference parseLegacyDialogReference(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String reference = raw.trim();
        if (reference.length() > 4096 || reference.chars().anyMatch(c -> c < 32 || c == 127)
                || isExternalUrl(reference) || reference.startsWith("//") || reference.startsWith("\\\\")) {
            return null;
        }
        if (isApiReference(reference)) {
            return parseLegacyDialogApiReference(reference);
        }
        String normalized = reference.replace('\\', '/');
        if (StringUtils.hasText(extractAttachmentsSuffix(normalized))) {
            return new LegacyDialogReference(LegacyDialogReferenceKind.STORED_PATH, reference, null);
        }
        return new LegacyDialogReference(normalized.contains("/")
                ? LegacyDialogReferenceKind.STORAGE_KEY : LegacyDialogReferenceKind.FILENAME, normalized, null);
    }

    private static LegacyDialogReference parseLegacyDialogApiReference(String reference) {
        try {
            URI uri = URI.create(reference.startsWith("api/") ? "/" + reference : reference);
            if (uri.getScheme() != null || uri.getRawAuthority() != null || uri.getRawFragment() != null) {
                return null;
            }
            String prefix = "/api/attachments/tickets/";
            String path = uri.getRawPath();
            if (path == null || !path.startsWith(prefix)) {
                return null;
            }
            String suffix = path.substring(prefix.length());
            if ("by-ticket".equals(suffix)) {
                return parseLegacyDialogTicketQuery(uri.getRawQuery());
            }
            if ("by-storage-key".equals(suffix) || "by-path".equals(suffix)) {
                String parameter = "by-storage-key".equals(suffix) ? "key=" : "path=";
                String query = uri.getRawQuery();
                if (query == null || !query.startsWith(parameter) || query.contains("&")) {
                    return null;
                }
                String decoded = URLDecoder.decode(query.substring(parameter.length()), StandardCharsets.UTF_8);
                if (!StringUtils.hasText(decoded)) {
                    return null;
                }
                return new LegacyDialogReference("by-storage-key".equals(suffix)
                        ? LegacyDialogReferenceKind.STORAGE_KEY : LegacyDialogReferenceKind.STORED_PATH, decoded, null);
            }
            String[] segments = suffix.split("/", -1);
            if (uri.getRawQuery() != null || segments.length != 2) {
                return null;
            }
            String ticketId = UriUtils.decode(segments[0], StandardCharsets.UTF_8);
            String filename = UriUtils.decode(segments[1], StandardCharsets.UTF_8);
            if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(filename)) {
                return null;
            }
            // Never trim an explicit opaque ticket identity.
            return new LegacyDialogReference(LegacyDialogReferenceKind.FILENAME, filename, ticketId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LegacyDialogReference parseLegacyDialogTicketQuery(String query) {
        if (query == null) {
            return null;
        }
        String[] parameters = query.split("&", -1);
        if (parameters.length != 2) {
            return null;
        }
        String ticketId = null;
        String filename = null;
        for (String parameter : parameters) {
            if (parameter.startsWith("ticketId=") && ticketId == null) {
                ticketId = URLDecoder.decode(parameter.substring("ticketId=".length()), StandardCharsets.UTF_8);
            } else if (parameter.startsWith("filename=") && filename == null) {
                filename = URLDecoder.decode(parameter.substring("filename=".length()), StandardCharsets.UTF_8);
            } else {
                return null;
            }
        }
        return StringUtils.hasText(ticketId) && StringUtils.hasText(filename)
                ? new LegacyDialogReference(LegacyDialogReferenceKind.FILENAME, filename, ticketId) : null;
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
        if (lowered.startsWith("attachments/")) {
            return normalizeReference(normalized.substring("attachments/".length()));
        }
        String marker = "/attachments/";
        int markerIndex = lowered.indexOf(marker);
        if (markerIndex >= 0) {
            return normalizeReference(normalized.substring(markerIndex + marker.length()));
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

    public static String extractDialogTicketId(String raw) {
        String storageKey = extractAttachmentsSuffix(raw);
        if (!StringUtils.hasText(storageKey)) {
            storageKey = normalizeReference(raw);
        }
        if (!StringUtils.hasText(storageKey)) {
            return null;
        }
        int separatorIndex = storageKey.indexOf('/');
        if (separatorIndex <= 0) {
            return null;
        }
        String ticketId = storageKey.substring(0, separatorIndex).trim();
        return StringUtils.hasText(ticketId) ? ticketId : null;
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
