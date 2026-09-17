package com.example.supportbot.telegram;

final class TelegramApiEndpointSupport {

    private static final String DEFAULT_ROOT_URL = "https://api.telegram.org";

    private TelegramApiEndpointSupport() {
    }

    static String botApiBaseUrl(String rawRootUrl) {
        return normalizeRootUrl(rawRootUrl) + "/bot";
    }

    static String normalizeRootUrl(String rawRootUrl) {
        String value = rawRootUrl == null ? "" : rawRootUrl.trim();
        if (value.isEmpty()) {
            return DEFAULT_ROOT_URL;
        }
        String normalized = value.replaceAll("/+$", "");
        if (normalized.equals(DEFAULT_ROOT_URL + "/bot")) {
            return DEFAULT_ROOT_URL;
        }
        if (normalized.endsWith("/bot")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }
}
