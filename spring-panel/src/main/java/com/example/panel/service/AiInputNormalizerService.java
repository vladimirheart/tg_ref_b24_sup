package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiInputNormalizerService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern ENTITY_HINT_PATTERN = Pattern.compile("(#?[\\p{L}]{2,}[\\-_]?[0-9]{2,}|\\+?[0-9][0-9\\-()\\s]{6,}|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Set<String> STOP = Set.of("и","в","на","не","что","как","для","или","по","из","к","у","о","об","the","a","an","to","of","in","on","for","and","or","is","are","be");
    private static final Set<String> JUNK_MEDIA_TYPES = Set.of(
            "animation",
            "sticker",
            "emoji",
            "reaction",
            "gif",
            "system_notification"
    );
    private static final Set<String> ACTIONABLE_MEDIA_TYPES = Set.of(
            "photo",
            "image",
            "video",
            "video_note",
            "voice",
            "audio",
            "document",
            "file"
    );

    private final JdbcTemplate jdbcTemplate;

    public AiInputNormalizerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public IncomingPayload normalizeIncomingPayload(String ticketId, String message, String messageType, String attachment) {
        IncomingPayload incoming = new IncomingPayload(trim(message), normalize(trim(messageType)), trim(attachment));
        IncomingPayload payload = incoming;
        if (payload.message() == null && payload.type() == null && payload.attachment() == null) {
            payload = loadLastClientPayload(ticketId);
        }
        if (payload == null) {
            return null;
        }
        if (isJunkMediaType(payload.type())) {
            return null;
        }
        String text = trim(payload.message());
        if (payload.attachment() != null && (isActionableMediaType(payload.type()) || text == null)) {
            text = trim(buildMediaContext(payload.type(), payload.attachment(), text));
        }
        if (isNoiseClientMessage(text)) {
            return null;
        }
        return new IncomingPayload(text, payload.type(), payload.attachment());
    }

    public IncomingPayload loadLastClientPayload(String ticketId) {
        String normalizedTicketId = trim(ticketId);
        if (normalizedTicketId == null) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    """
                    SELECT message, message_type, attachment
                      FROM chat_history
                     WHERE ticket_id = ?
                       AND lower(COALESCE(sender, '')) NOT IN ('operator','support','admin','system','ai_agent')
                     ORDER BY id DESC
                     LIMIT 1
                    """,
                    rs -> rs.next()
                            ? new IncomingPayload(
                            trim(rs.getString("message")),
                            normalize(trim(rs.getString("message_type"))),
                            trim(rs.getString("attachment")))
                            : null,
                    normalizedTicketId
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isJunkMediaType(String type) {
        String normalized = normalize(type);
        return normalized != null && JUNK_MEDIA_TYPES.contains(normalized);
    }

    private boolean isActionableMediaType(String type) {
        String normalized = normalize(type);
        return normalized != null && ACTIONABLE_MEDIA_TYPES.contains(normalized);
    }

    private boolean isNoiseClientMessage(String text) {
        String normalized = trim(text);
        if (normalized == null) {
            return true;
        }
        if (normalized.length() <= 2 && !normalized.chars().anyMatch(Character::isLetterOrDigit)) {
            return true;
        }
        boolean hasAlphaNum = normalized.chars().anyMatch(Character::isLetterOrDigit);
        if (!hasAlphaNum) {
            return true;
        }
        Set<String> tokens = tokenize(normalized);
        Set<String> entities = extractEntityHints(normalized);
        return tokens.isEmpty() && entities.isEmpty();
    }

    private String buildMediaContext(String messageType, String attachment, String caption) {
        String type = trim(messageType);
        String mediaType = type != null ? type : "media";
        String fileName = fileNameFromAttachment(attachment);
        String cap = trim(caption);
        if (cap != null) {
            return "client sent " + mediaType + " " + fileName + ". caption: " + cap;
        }
        return "client sent " + mediaType + " " + fileName;
    }

    private String fileNameFromAttachment(String attachment) {
        String value = trim(attachment);
        if (value == null) {
            return "attachment";
        }
        String normalized = value.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        String raw = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        String trimmed = trim(raw);
        return trimmed != null ? trimmed : "attachment";
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            String item = trim(token);
            if (item == null || item.length() < 2 || STOP.contains(item)) continue;
            out.add(item);
        }
        return out;
    }

    private Set<String> extractEntityHints(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        var matcher = ENTITY_HINT_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String hit = trim(matcher.group());
            if (hit == null) continue;
            if (hit.length() >= 4) out.add(hit);
        }
        return out;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record IncomingPayload(String message, String type, String attachment) {
    }
}

