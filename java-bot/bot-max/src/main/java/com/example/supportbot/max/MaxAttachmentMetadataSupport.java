package com.example.supportbot.max;

final class MaxAttachmentMetadataSupport {

    private MaxAttachmentMetadataSupport() {
    }

    static String firstNonBlank(String... values) {
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

    static String resolveAttachmentExtension(String filename, String contentType, String attachmentType) {
        if (hasText(filename)) {
            String normalized = filename.trim();
            int dot = normalized.lastIndexOf('.');
            if (dot >= 0 && dot < normalized.length() - 1) {
                String extension = normalized.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
                if (!extension.isBlank() && extension.length() <= 10) {
                    return extension.toLowerCase();
                }
            }
        }
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase();
        if (normalizedContentType.contains("jpeg")) return "jpg";
        if (normalizedContentType.contains("png")) return "png";
        if (normalizedContentType.contains("gif")) return "gif";
        if (normalizedContentType.contains("webp")) return "webp";
        if (normalizedContentType.contains("mp4")) return "mp4";
        if (normalizedContentType.contains("ogg")) return "ogg";
        if (normalizedContentType.contains("mpeg")) return "mp3";
        if (normalizedContentType.contains("pdf")) return "pdf";
        String normalizedType = attachmentType == null ? "" : attachmentType.toLowerCase();
        if (normalizedType.contains("image") || normalizedType.contains("photo")) return "jpg";
        if (normalizedType.contains("video")) return "mp4";
        if (normalizedType.contains("audio")) return "ogg";
        return "bin";
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
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
