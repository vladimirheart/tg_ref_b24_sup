package com.example.panel.service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private static final Pattern PADDED_CREATED_FROM_DIALOG = Pattern.compile(
            "Создано из диалога #([ \\t]+[A-Za-z0-9._-]+[ \\t]*|[A-Za-z0-9._-]+[ \\t]+)([.,;:!?]?)(?=</p>)");
    private static final Pattern PADDED_OPEN_DIALOG = Pattern.compile(
            "Открыть диалог #([ \\t]+[A-Za-z0-9._-]+[ \\t]*|[A-Za-z0-9._-]+[ \\t]+)([.,;:!?]?)(?=</a>)");
    private static final Pattern LEGACY_DIALOG_ANCHOR_HREF = Pattern.compile(
            "(?i)(<a\\b[^<>]*?\\s+href\\s*=\\s*)([\\\"'])(/dialogs/([^\\\"'<>?#&]+))\\2");
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
        String current = StringUtils.hasText(title) ? title.stripLeading() : null;
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
                PADDED_CREATED_FROM_DIALOG,
                "Создано из обращения №",
                "Создано из обращения"
        );
        normalized = replaceLegacyBodyReference(
                normalized,
                CREATED_FROM_DIALOG,
                "Создано из обращения №",
                "Создано из обращения"
        );
        normalized = replaceLegacyBodyReference(
                normalized,
                PADDED_OPEN_DIALOG,
                "Открыть обращение №",
                "Открыть обращение"
        );
        normalized = replaceLegacyBodyReference(
                normalized,
                OPEN_DIALOG,
                "Открыть обращение №",
                "Открыть обращение"
        );
        return normalizeLegacyDialogLinks(normalized);
    }

    private String normalizeLegacyDialogLinks(String html) {
        Matcher matcher = LEGACY_DIALOG_ANCHOR_HREF.matcher(html);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group();
            try {
                // In a path, a literal '+' is a plus, not a form-encoded space.
                String ticketId = URLDecoder.decode(matcher.group(4).replace("+", "%2B"), StandardCharsets.UTF_8);
                if (StringUtils.hasText(ticketId) && ticketId.indexOf('\uFFFD') < 0) {
                    String encodedTicketId = URLEncoder.encode(ticketId, StandardCharsets.UTF_8).replace("+", "%20");
                    replacement = matcher.group(1) + matcher.group(2)
                            + "/dialogs?ticketId=" + encodedTicketId + matcher.group(2);
                }
            } catch (IllegalArgumentException ignored) {
                // Keep malformed historical href unchanged; never guess a different ticket ID.
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String normalizeLegacyTitle(String title,
                                        String legacyPrefix,
                                        String numberedPrefix,
                                        String fallback) {
        String remainder = title.substring(legacyPrefix.length());
        int separator = remainder.indexOf(':');
        String rawTicketId = separator >= 0 ? remainder.substring(0, separator) : remainder;
        String ticketId = StringUtils.hasText(rawTicketId) ? rawTicketId : null;
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
            boolean paddedReference = matcher.groupCount() == 2;
            String ticketId = paddedReference ? rawTicketId : stripTrailingReferencePunctuation(rawTicketId);
            String trailingPunctuation = paddedReference ? matcher.group(2) : rawTicketId.substring(ticketId.length());
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
        if (!StringUtils.hasText(ticketId)) {
            return null;
        }
        try {
            String requestNumber = trimToNull(dialogLookupReadService.resolveRequestNumber(ticketId));
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
