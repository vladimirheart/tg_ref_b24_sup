package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DialogAiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(DialogAiAssistantService.class);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "во", "на", "не", "что", "как", "для", "или", "по", "из", "к", "у", "о", "об",
            "это", "а", "но", "же", "бы", "ли", "мы", "вы", "они", "он", "она", "оно", "с", "со", "от",
            "the", "a", "an", "to", "of", "in", "on", "for", "and", "or", "is", "are", "be"
    );
    private static final double AUTO_REPLY_THRESHOLD = 0.62d;
    private static final int DEFAULT_SUGGESTION_LIMIT = 3;

    private final JdbcTemplate jdbcTemplate;
    private final DialogReplyService dialogReplyService;
    private final NotificationService notificationService;
    private final SharedConfigService sharedConfigService;

    public DialogAiAssistantService(JdbcTemplate jdbcTemplate,
                                    DialogReplyService dialogReplyService,
                                    NotificationService notificationService,
                                    SharedConfigService sharedConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogReplyService = dialogReplyService;
        this.notificationService = notificationService;
        this.sharedConfigService = sharedConfigService;
    }

    public void processIncomingClientMessage(String ticketId, String message) {
        String normalizedTicketId = trimToNull(ticketId);
        String normalizedMessage = trimToNull(message);
        if (normalizedTicketId == null || normalizedMessage == null) {
            return;
        }
        if (!isAgentEnabled()) {
            clearProcessing(normalizedTicketId, "disabled", null);
            return;
        }
        if (requiresHumanImmediately(normalizedMessage)) {
            clearProcessing(normalizedTicketId, "manual_requested", null);
            notifyOperatorsEscalation(normalizedTicketId, normalizedMessage, "Клиент запросил оператора.");
            return;
        }

        List<AiSuggestion> suggestions = findSuggestions(normalizedTicketId, normalizedMessage, DEFAULT_SUGGESTION_LIMIT);
        if (suggestions.isEmpty()) {
            clearProcessing(normalizedTicketId, "no_match", "Нет релевантных источников.");
            notifyOperatorsEscalation(normalizedTicketId, normalizedMessage, "Агент не нашел релевантный ответ.");
            return;
        }

        AiSuggestion top = suggestions.get(0);
        if (top.score() < AUTO_REPLY_THRESHOLD) {
            clearProcessing(normalizedTicketId, "low_confidence", "Низкая уверенность (" + formatScore(top.score()) + ").");
            notifyOperatorsEscalation(normalizedTicketId, normalizedMessage, "Низкая уверенность агента: " + formatScore(top.score()));
            return;
        }

        String reply = buildAutoReply(top);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(normalizedTicketId, reply, null, null);
        if (!result.success()) {
            clearProcessing(normalizedTicketId, "send_failed", result.error());
            notifyOperatorsEscalation(normalizedTicketId, normalizedMessage, "Ошибка отправки автоответа: " + result.error());
            return;
        }
        markProcessing(normalizedTicketId, "auto_replied", top, null);
    }

    public List<Map<String, Object>> loadOperatorSuggestions(String ticketId, Integer limit) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return List.of();
        }
        String lastClientMessage = loadLastClientMessage(normalizedTicketId);
        if (!StringUtils.hasText(lastClientMessage)) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : DEFAULT_SUGGESTION_LIMIT, 8));
        List<AiSuggestion> suggestions = findSuggestions(normalizedTicketId, lastClientMessage, safeLimit);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (AiSuggestion suggestion : suggestions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", suggestion.source());
            item.put("title", suggestion.title());
            item.put("score", suggestion.score());
            item.put("score_label", formatScore(suggestion.score()));
            item.put("snippet", suggestion.snippet());
            item.put("reply", buildOperatorReplySuggestion(suggestion));
            payload.add(item);
        }
        return payload;
    }

    public boolean isProcessing(String ticketId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return false;
        }
        try {
            Integer value = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(is_processing, 0) FROM ticket_ai_agent_state WHERE ticket_id = ?",
                    Integer.class,
                    normalizedTicketId
            );
            return value != null && value > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public void clearProcessing(String ticketId, String action, String error) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, last_error, updated_at)
                    VALUES (?, 0, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        is_processing = 0,
                        mode = excluded.mode,
                        last_action = excluded.last_action,
                        last_error = excluded.last_error,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    normalizedTicketId,
                    "assist",
                    trimToNull(action),
                    trimToNull(error)
            );
        } catch (Exception ex) {
            log.debug("Unable to clear AI processing for {}: {}", normalizedTicketId, ex.getMessage());
        }
    }

    private void markProcessing(String ticketId, String action, AiSuggestion suggestion, String error) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, last_error, last_source, last_score, last_suggested_reply, updated_at)
                    VALUES (?, 1, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        is_processing = 1,
                        mode = excluded.mode,
                        last_action = excluded.last_action,
                        last_error = excluded.last_error,
                        last_source = excluded.last_source,
                        last_score = excluded.last_score,
                        last_suggested_reply = excluded.last_suggested_reply,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    ticketId,
                    "assist",
                    trimToNull(action),
                    trimToNull(error),
                    suggestion != null ? suggestion.source() : null,
                    suggestion != null ? suggestion.score() : null,
                    suggestion != null ? trimToNull(buildAutoReply(suggestion)) : null
            );
        } catch (Exception ex) {
            log.debug("Unable to mark AI processing for {}: {}", ticketId, ex.getMessage());
        }
    }

    private List<AiSuggestion> findSuggestions(String ticketId, String query, int limit) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<AiSuggestion> candidates = new ArrayList<>();
        candidates.addAll(loadKnowledgeCandidates(queryTokens, limit * 4));
        candidates.addAll(loadTaskCandidates(queryTokens, limit * 4));
        candidates.addAll(loadHistoryCandidates(ticketId, queryTokens, limit * 6));

        return candidates.stream()
                .filter(candidate -> candidate.score() > 0d)
                .sorted(Comparator.comparingDouble(AiSuggestion::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<AiSuggestion> loadKnowledgeCandidates(Set<String> queryTokens, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, title, summary, content
                  FROM knowledge_articles
                 ORDER BY COALESCE(updated_at, created_at) DESC
                 LIMIT ?
                """,
                limit
        );
        List<AiSuggestion> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String title = safeText(row.get("title"));
            String text = joinText(title, safeText(row.get("summary")), safeText(row.get("content")));
            double score = scoreByTokens(queryTokens, text);
            if (score <= 0d) {
                continue;
            }
            result.add(new AiSuggestion(
                    "knowledge",
                    title.isBlank() ? "Статья базы знаний" : title,
                    compactSnippet(text, 280),
                    score
            ));
        }
        return result;
    }

    private List<AiSuggestion> loadTaskCandidates(Set<String> queryTokens, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, title, body_html, status
                  FROM tasks
                 ORDER BY COALESCE(last_activity_at, created_at) DESC
                 LIMIT ?
                """,
                limit
        );
        List<AiSuggestion> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String title = safeText(row.get("title"));
            String bodyHtml = safeText(row.get("body_html"));
            String text = joinText(title, stripHtml(bodyHtml), safeText(row.get("status")));
            double score = scoreByTokens(queryTokens, text);
            if (score <= 0d) {
                continue;
            }
            result.add(new AiSuggestion(
                    "tasks",
                    title.isBlank() ? "Похожая задача" : title,
                    compactSnippet(text, 280),
                    score
            ));
        }
        return result;
    }

    private List<AiSuggestion> loadHistoryCandidates(String ticketId, Set<String> queryTokens, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT ticket_id, message
                  FROM chat_history
                 WHERE ticket_id <> ?
                   AND lower(sender) IN ('operator', 'support', 'admin', 'system')
                   AND message IS NOT NULL
                   AND trim(message) <> ''
                 ORDER BY id DESC
                 LIMIT ?
                """,
                ticketId,
                limit
        );
        List<AiSuggestion> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String ticket = safeText(row.get("ticket_id"));
            String message = safeText(row.get("message"));
            double score = scoreByTokens(queryTokens, message);
            if (score <= 0d) {
                continue;
            }
            result.add(new AiSuggestion(
                    "history",
                    ticket.isBlank() ? "Похожий диалог" : "Похожий диалог #" + ticket,
                    compactSnippet(message, 260),
                    score
            ));
        }
        return result;
    }

    private String buildAutoReply(AiSuggestion suggestion) {
        String body = trimToNull(suggestion != null ? suggestion.snippet() : null);
        if (body == null) {
            body = "Я не нашел готовое решение в базе знаний.";
        }
        return "Подобрал ответ по внутренней базе:\n\n"
                + body
                + "\n\nЕсли это не решает вопрос, напишите «оператор» — подключим специалиста.";
    }

    private String buildOperatorReplySuggestion(AiSuggestion suggestion) {
        String sourceLabel = switch (suggestion.source()) {
            case "knowledge" -> "базе знаний";
            case "tasks" -> "истории задач";
            case "history" -> "похожих диалогах";
            default -> "внутренних данных";
        };
        return "По " + sourceLabel + " можно предложить клиенту:\n\n" + suggestion.snippet();
    }

    private void notifyOperatorsEscalation(String ticketId, String message, String reason) {
        String text = "AI-агент эскалировал обращение " + ticketId + ". " + reason;
        if (StringUtils.hasText(message)) {
            text += " Вопрос клиента: " + compactSnippet(message, 140);
        }
        notificationService.notifyAllOperators(text, "/dialogs?ticketId=" + ticketId, null);
    }

    private boolean isAgentEnabled() {
        try {
            Map<String, Object> settings = sharedConfigService.loadSettings();
            Object rawDialogConfig = settings.get("dialog_config");
            if (rawDialogConfig instanceof Map<?, ?> map) {
                Object enabled = map.get("ai_agent_enabled");
                if (enabled instanceof Boolean bool) {
                    return bool;
                }
                String normalized = String.valueOf(enabled).trim().toLowerCase(Locale.ROOT);
                return !"false".equals(normalized) && !"0".equals(normalized) && !"off".equals(normalized);
            }
        } catch (Exception ex) {
            log.debug("Unable to resolve ai_agent_enabled setting: {}", ex.getMessage());
        }
        return true;
    }

    private String loadLastClientMessage(String ticketId) {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT message
                      FROM chat_history
                     WHERE ticket_id = ?
                       AND lower(sender) NOT IN ('operator', 'support', 'admin', 'system')
                       AND message IS NOT NULL
                       AND trim(message) <> ''
                     ORDER BY id DESC
                     LIMIT 1
                    """,
                    rs -> rs.next() ? trimToNull(rs.getString("message")) : null,
                    ticketId
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean requiresHumanImmediately(String message) {
        String normalized = String.valueOf(message).toLowerCase(Locale.ROOT);
        return normalized.contains("оператор")
                || normalized.contains("человек")
                || normalized.contains("менеджер")
                || normalized.contains("позвоните");
    }

    private double scoreByTokens(Set<String> queryTokens, String sourceText) {
        Set<String> sourceTokens = tokenize(sourceText);
        if (queryTokens.isEmpty() || sourceTokens.isEmpty()) {
            return 0d;
        }
        int overlap = 0;
        for (String token : queryTokens) {
            if (sourceTokens.contains(token)) {
                overlap++;
            }
        }
        if (overlap == 0) {
            return 0d;
        }
        double overlapRate = overlap / (double) queryTokens.size();
        String sourceNormalized = normalizeText(sourceText);
        double boost = 0d;
        for (String token : queryTokens) {
            if (token.length() >= 5 && sourceNormalized.contains(token)) {
                boost += 0.03d;
            }
        }
        return Math.min(1d, overlapRate + boost);
    }

    private Set<String> tokenize(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            String trimmed = trimToNull(token);
            if (trimmed == null || trimmed.length() < 2 || STOP_WORDS.contains(trimmed)) {
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private String stripHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String joinText(String... chunks) {
        StringBuilder builder = new StringBuilder();
        if (chunks == null) {
            return "";
        }
        for (String chunk : chunks) {
            String trimmed = trimToNull(chunk);
            if (trimmed == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(". ");
            }
            builder.append(trimmed);
        }
        return builder.toString();
    }

    private String compactSnippet(String text, int maxLen) {
        String normalized = trimToNull(text);
        if (normalized == null) {
            return "";
        }
        String compact = normalized.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLen) {
            return compact;
        }
        return compact.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private String safeText(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score)));
    }

    private record AiSuggestion(String source, String title, String snippet, double score) {
    }
}
