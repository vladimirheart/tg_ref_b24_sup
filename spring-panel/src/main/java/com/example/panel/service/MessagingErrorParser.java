package com.example.panel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;

/** Parses provider replies, never transports a request or invents a size limit. */
public final class MessagingErrorParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private MessagingErrorParser() {}
    public record Failure(String message, String code, String description, String classification, String severity, String retryState) {}

    public static Failure parse(String platform, Integer httpStatus, String suppliedCode, String suppliedMessage, String body) {
        String provider = platform == null ? "" : platform.toLowerCase(Locale.ROOT);
        String name = switch (provider) { case "telegram" -> "Telegram"; case "vk" -> "VK"; case "max" -> "MAX"; default -> "Мессенджер"; };
        JsonNode root = json(body), error = root != null && root.path("error").isObject() ? root.path("error") : root;
        String code = first(text(error, "error_code"), text(error, "code"), text(error, "errorCode"), suppliedCode);
        if (code != null && !code.matches("[A-Za-z0-9_.:-]{1,80}")) code = null;
        String description = safe(first(text(error, "error_msg"), text(error, "description"), text(error, "message"),
            root != null && root.path("error").isTextual() ? root.path("error").asText() : null, suppliedMessage));
        String words = (String.valueOf(code) + " " + description).toLowerCase(Locale.ROOT);
        int status = httpStatus == null ? 0 : httpStatus;
        if (status / 100 == 2 && root != null && (root.path("ok").isBoolean() && !root.path("ok").booleanValue() || root.hasNonNull("error"))) status = 400;
        if (provider.equals("telegram") && code != null && code.matches("[45][0-9]{2}")) status = Integer.parseInt(code);
        String classification = "client_error", severity = "warning", retry = "terminal", reason;
        if (provider.equals("max") && words.contains("attachment.not.ready")) {
            reason = "Файл загружен, но мессенджер ещё обрабатывает его. Дождитесь окончания обработки перед повтором.";
            classification = "provider_error"; retry = "transient";
        } else if (status == 429 || provider.equals("vk") && java.util.Set.of("6", "9", "29").contains(String.valueOf(code))
            || words.contains("rate_limit") || words.contains("rate limit") || words.contains("too many requests")) {
            reason = "Превышена частота запросов.";
            long seconds = retrySeconds(root);
            if (seconds > 0) reason += " Повтор возможен через " + seconds + " сек.";
            classification = "rate_limited"; retry = "transient";
        } else if (provider.equals("vk") && "914".equals(code) || words.contains("message is too long") || words.contains("text is too long")
            || words.contains("caption is too long") || words.contains("message_too_long")) {
            reason = "Слишком длинный текст сообщения или подписи. Сократите его или разделите на несколько сообщений.";
            classification = "validation_error";
        } else if (status == 413 || provider.equals("vk") && "910".equals(code) || words.contains("too big")
            || words.contains("too large") || words.contains("size") && (words.contains("exceed") || words.contains("limit"))) {
            reason = "Превышен допустимый размер сообщения или вложения. Уменьшите файл или число вложений.";
            classification = "validation_error";
        } else if (provider.equals("vk") && ("900".equals(code) || "901".equals(code) || "902".equals(code))
            || words.contains("blocked by the user") || words.contains("bot was blocked") || words.contains("privacy settings")) {
            reason = "Получатель запретил сообщения или заблокировал бота. Попросите клиента разрешить сообщения.";
        } else if (words.contains("chat not found") || words.contains("peer_id")) {
            reason = "Не найден получатель. Проверьте выбранный канал и адресата.";
        } else if (status == 401 || status == 403 || provider.equals("vk") && java.util.Set.of("5", "7", "15", "27", "28").contains(String.valueOf(code))
            || words.contains("unauthorized") || words.contains("invalid token") || words.contains("access denied")) {
            reason = "Мессенджер отказал в доступе. Администратору нужно проверить ключ и права бота.";
            severity = "critical";
        } else if (status >= 500 || status == 408 || provider.equals("vk") && java.util.Set.of("10", "13", "36").contains(String.valueOf(code))) {
            reason = "Сервис мессенджера временно недоступен. Результат операции не подтвержден; проверьте историю перед повтором.";
            classification = "provider_error"; severity = "critical"; retry = "transient";
        } else if (status >= 400 && status < 500 || status >= 200 && status < 300 && code != null) {
            reason = "Мессенджер отклонил запрос. Проверьте параметры сообщения.";
        } else {
            reason = "Не удалось подтвердить результат операции. Проверьте историю диалога перед повтором.";
            classification = "unknown_error"; severity = "critical"; retry = "transient";
        }
        String message = name + ": " + reason;
        if (code != null) message += " Код: " + code + ".";
        if (!description.isBlank()) message += " Причина: " + description;
        return new Failure(message, code, description.isBlank() ? null : description, classification, severity, retry);
    }

    public static boolean confirmedSuccess(String platform, String body) {
        JsonNode root = json(body);
        if (root == null || !root.isObject() || root.path("ok").isBoolean() && !root.path("ok").booleanValue()
            || root.path("success").isBoolean() && !root.path("success").booleanValue()
            || root.hasNonNull("error") || root.hasNonNull("code") || root.hasNonNull("error_code")) return false;
        return switch (platform) {
            case "telegram" -> root.path("ok").isBoolean() && root.path("ok").booleanValue();
            case "vk" -> root.path("response").isIntegralNumber() || root.path("response").isObject();
            case "max" -> root.path("message").isObject() || root.path("success").asBoolean(false);
            default -> false;
        };
    }

    public static String safe(String value) {
        if (value == null || value.stripLeading().startsWith("<")) return "";
        String clean = value.replaceAll("(?i)https?://[^\\s\"'<>]+", "[адрес скрыт]")
            .replaceAll("\\b\\d{5,14}:[A-Za-z0-9_-]{20,}\\b", "[секрет скрыт]")
            .replaceAll("(?i)((?:access_token|token|password|secret|api[_-]?key|authorization)[\"']?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|(?:bearer\\s+)?[^\\s,;}&]+)", "$1[скрыто]")
            .replaceAll("<[^>]*>", " ").replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ").replaceAll("\\s+", " ").trim();
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }
    private static JsonNode json(String body) {
        if (body == null || body.isBlank()) return null;
        try { return JSON.readTree(body); } catch (Exception ignored) { return null; }
    }
    private static String text(JsonNode node, String key) {
        if (node == null || !node.path(key).isValueNode() || node.path(key).isNull()) return null;
        return node.path(key).asText();
    }
    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
    private static long retrySeconds(JsonNode root) {
        if (root == null) return 0;
        JsonNode value = root.path("parameters").path("retry_after");
        if (!value.isIntegralNumber()) value = root.path("retry_after");
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0 ? value.longValue() : 0;
    }
}
