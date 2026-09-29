package com.example.panel.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DialogTaskDisplayService {

    private static final String LEGACY_AUTO_CLOSE_PREFIX = "Проверить автозакрытый диалог #";
    private static final String LEGACY_DIALOG_PREFIX = "Обращение #";
    private static final String DISPLAY_AUTO_CLOSE_PREFIX = "Проверить автозакрытое обращение №";
    private static final String DISPLAY_DIALOG_PREFIX = "Обращение №";
    private static final Pattern CREATED_FROM_DIALOG = Pattern.compile("Создано из диалога #([A-Za-z0-9._-]+)");
    private static final Pattern OPEN_DIALOG = Pattern.compile("Открыть диалог #([A-Za-z0-9._-]+)");
    private static final int TITLE_PROBLEM_LIMIT = 72;

    private final DialogLookupReadService dialogLookupReadService;

    public DialogTaskDisplayService(DialogLookupReadService dialogLookupReadService) {
        this.dialogLookupReadService = dialogLookupReadService;
    }

    public String buildAutoCloseTitle(String ticketId, String problem) {
        String requestNumber = resolveCanonicalRequestNumber(ticketId);
        String base = requestNumber != null
                ? DISPLAY_AUTO_CLOSE_PREFIX + requestNumber
                : "Проверить автозакрытое обращение";
        String normalizedProblem = trimToNull(problem);
        return normalizedProblem == null ? base : base + ": " + abbreviate(normalizedProblem, TITLE_PROBLEM_LIMIT);
    }

    public String dialogReference(String ticketId) {
        String requestNumber = resolveCanonicalRequestNumber(ticketId);
        return requestNumber != null ? "обращение №" + requestNumber : "обращение";
    }

    public String normalizeNotificationText(String text) {
        String current = trimToNull(text);
        if (current == null || !current.startsWith("Новая задача «") || !current.endsWith("»")) {
            return text;
        }
        String title = current.substring("Новая задача «".length(), current.length() - 1);
        return "Новая задача «" + normalizeTitle(title) + "»";
    }

    public String normalizeTitle(String title) {
        String current = trimToNull(title);
        if (current == null) {
            return title;
        }
        if (current.startsWith(LEGACY_AUTO_CLOSE_PREFIX)) {
            return normalizeLegacyTitle(current, LEGACY_AUTO_CLOSE_PREFIX, DISPLAY_AUTO_CLOSE_PREFIX,
                    "Проверить автозакрытое обращение");
        }
        if (current.startsWith(LEGACY_DIALOG_PREFIX)) {
            return normalizeLegacyTitle(current, LEGACY_DIALOG_PREFIX, DISPLAY_DIALOG_PREFIX, "Обращение");
        }
        return title;
    }

    public String normalizeBodyHtml(String bodyHtml) {
        if (!StringUtils.hasText(bodyHtml)) {
            return bodyHtml;
        }
        String normalized = replaceLegacyBodyReference(
                bodyHtml,
                CREATED_FROM_DIALOG,
                "Создано из обращения №",
                "Создано из обращения"
        );
        return replaceLegacyBodyReference(
                normalized,
                OPEN_DIALOG,
                "Открыть обращение №",
                "Открыть обращение"
        );
    }

    private String normalizeLegacyTitle(String title,
                                        String legacyPrefix,
                                        String numberedPrefix,
                                        String fallback) {
        String remainder = title.substring(legacyPrefix.length());
        int separator = remainder.indexOf(':');
        String ticketId = trimToNull(separator >= 0 ? remainder.substring(0, separator) : remainder);
        String suffix = separator >= 0 ? remainder.substring(separator) : "";
        String requestNumber = resolveCanonicalRequestNumber(ticketId);
        return (requestNumber != null ? numberedPrefix + requestNumber : fallback) + suffix;
    }

    private String replaceLegacyBodyReference(String html,
                                              Pattern pattern,
                                              String numberedPrefix,
                                              String fallback) {
        Matcher matcher = pattern.matcher(html);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rawTicketId = matcher.group(1);
            String ticketId = stripTrailingReferencePunctuation(rawTicketId);
            String trailingPunctuation = rawTicketId.substring(ticketId.length());
            String requestNumber = resolveCanonicalRequestNumber(ticketId);
            String replacement = requestNumber != null ? numberedPrefix + requestNumber : fallback;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement + trailingPunctuation));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String stripTrailingReferencePunctuation(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int end = value.length();
        while (end > 0 && ".,;:!?".indexOf(value.charAt(end - 1)) >= 0) {
            end -= 1;
        }
        return value.substring(0, end);
    }

    private String resolveCanonicalRequestNumber(String ticketId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return null;
        }
        try {
            String requestNumber = trimToNull(dialogLookupReadService.resolveRequestNumber(normalizedTicketId));
            return requestNumber != null && requestNumber.matches("\\d{8}-\\d{3,}") ? requestNumber : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(1, maxLength - 1)).trim() + "…";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
