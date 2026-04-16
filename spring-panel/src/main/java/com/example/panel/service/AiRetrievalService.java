package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiRetrievalService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern ENTITY_HINT_PATTERN = Pattern.compile("(#?[\\p{L}]{2,}[\\-_]?[0-9]{2,}|\\+?[0-9][0-9\\-()\\s]{6,}|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Set<String> STOP = Set.of("и","в","на","не","что","как","для","или","по","из","к","у","о","об","the","a","an","to","of","in","on","for","and","or","is","are","be");

    private final JdbcTemplate jdbcTemplate;
    private final AiPolicyService aiPolicyService;

    public AiRetrievalService(JdbcTemplate jdbcTemplate, AiPolicyService aiPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiPolicyService = aiPolicyService;
    }

    public List<Candidate> findSuggestions(String ticketId, String query, int limit) {
        Set<String> queryTokens = tokenize(query);
        Set<String> entities = extractEntityHints(query);
        if (queryTokens.isEmpty() && entities.isEmpty()) {
            return List.of();
        }
        Long applicantUserId = resolveApplicantUserId(ticketId);
        List<Candidate> candidates = new ArrayList<>();
        candidates.addAll(loadMemoryCandidates(queryTokens, entities, limit * 4));
        candidates.addAll(loadKnowledgeCandidates(queryTokens, entities, limit * 4));
        candidates.addAll(loadTaskCandidates(queryTokens, entities, limit * 4));
        candidates.addAll(loadHistoryCandidates(ticketId, queryTokens, entities, limit * 6));
        candidates.addAll(loadApplicantHistoryCandidates(applicantUserId, ticketId, queryTokens, entities, limit * 6));
        return rerankSuggestions(queryTokens, candidates)
                .stream()
                .filter(item -> item.score() > 0d)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<Candidate> loadMemoryCandidates(Set<String> queryTokens, Set<String> entities, int limit) {
        List<Map<String, Object>> rows = queryMemoryRows(limit);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = safe(row.get("query_key"));
            String queryText = safe(row.get("query_text"));
            String solutionText = safe(row.get("solution_text"));
            double score = scoreByTokens(queryTokens, entities, join(queryText, solutionText));
            int confirmed = toInt(row.get("times_confirmed"));
            int corrected = toInt(row.get("times_corrected"));
            score = Math.max(0d, Math.min(1d, score + Math.min(0.20d, confirmed * 0.02d) - Math.min(0.12d, corrected * 0.02d)));
            String status = aiPolicyService.normalizeStatus(safe(row.get("status")), "approved");
            String trustLevel = aiPolicyService.normalizeTrustLevel(safe(row.get("trust_level")), "low");
            String sourceType = aiPolicyService.normalizeSourceType("memory", safe(row.get("source_type")));
            String safetyLevel = aiPolicyService.normalizeSafetyLevel(safe(row.get("safety_level")), "normal");
            if ("draft".equals(status)) {
                score = Math.max(0d, Math.min(1d, score - 0.12d));
            }
            if ("low".equals(trustLevel)) {
                score = Math.max(0d, Math.min(1d, score - 0.08d));
            }
            if (score > 0d) {
                out.add(new Candidate("memory", "Проверенное решение", cut(solutionText, 320), score, trim(key), status, trustLevel, sourceType, safetyLevel));
            }
        }
        return out;
    }

    private List<Map<String, Object>> queryMemoryRows(int limit) {
        try {
            return jdbcTemplate.queryForList(
                    """
                    SELECT query_key, query_text, solution_text, times_confirmed, times_corrected,
                           status, trust_level, source_type, safety_level
                      FROM ai_agent_solution_memory
                     WHERE COALESCE(review_required,0)=0
                       AND solution_text IS NOT NULL
                       AND trim(solution_text)<>''
                       AND lower(COALESCE(status,'approved')) IN ('approved','draft')
                       AND (
                           expires_at IS NULL
                           OR trim(COALESCE(expires_at,'')) = ''
                           OR datetime(substr(expires_at,1,19)) >= datetime('now')
                       )
                     ORDER BY COALESCE(updated_at, created_at) DESC
                     LIMIT ?
                    """,
                    limit
            );
        } catch (Exception ignored) {
            try {
                return jdbcTemplate.queryForList(
                        """
                        SELECT query_key, query_text, solution_text, times_confirmed, times_corrected
                          FROM ai_agent_solution_memory
                         WHERE COALESCE(review_required,0)=0
                           AND solution_text IS NOT NULL
                           AND trim(solution_text)<>''
                         ORDER BY COALESCE(updated_at, created_at) DESC
                         LIMIT ?
                        """,
                        limit
                );
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    private List<Candidate> loadKnowledgeCandidates(Set<String> queryTokens, Set<String> entities, int limit) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("SELECT title, summary, content FROM knowledge_articles ORDER BY COALESCE(updated_at, created_at) DESC LIMIT ?", limit);
        } catch (Exception ex) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String title = safe(row.get("title"));
            String text = cleanTextForRetrieval(join(title, safe(row.get("summary")), safe(row.get("content"))));
            double score = scoreByTokens(queryTokens, entities, text);
            if (score > 0d) {
                out.add(new Candidate("knowledge", StringUtils.hasText(title) ? title : "Статья базы знаний", cut(text, 280), score, null, "approved", "high", "knowledge", "normal"));
            }
        }
        return out;
    }

    private List<Candidate> loadTaskCandidates(Set<String> queryTokens, Set<String> entities, int limit) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("SELECT title, body_html, status FROM tasks ORDER BY COALESCE(last_activity_at, created_at) DESC LIMIT ?", limit);
        } catch (Exception ex) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String title = safe(row.get("title"));
            String text = cleanTextForRetrieval(join(title, stripHtml(safe(row.get("body_html"))), safe(row.get("status"))));
            double score = scoreByTokens(queryTokens, entities, text);
            if (score > 0d) {
                out.add(new Candidate("tasks", StringUtils.hasText(title) ? title : "Похожая задача", cut(text, 280), score, null, "draft", "low", "tasks", "normal"));
            }
        }
        return out;
    }

    private List<Candidate> loadHistoryCandidates(String ticketId, Set<String> queryTokens, Set<String> entities, int limit) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT ticket_id, message FROM chat_history WHERE ticket_id <> ? AND lower(sender) IN ('operator','support','admin','system','ai_agent') AND message IS NOT NULL AND trim(message)<>'' ORDER BY id DESC LIMIT ?",
                    ticketId,
                    limit
            );
        } catch (Exception ex) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String prevTicket = safe(row.get("ticket_id"));
            String message = cleanTextForRetrieval(safe(row.get("message")));
            double score = scoreByTokens(queryTokens, entities, message);
            if (score > 0d) {
                out.add(new Candidate("history", StringUtils.hasText(prevTicket) ? "Похожий диалог #" + prevTicket : "Похожий диалог", cut(message, 260), score, null, "draft", "low", "history", "normal"));
            }
        }
        return out;
    }

    private List<Candidate> loadApplicantHistoryCandidates(Long userId, String ticketId, Set<String> queryTokens, Set<String> entities, int limit) {
        if (userId == null || !StringUtils.hasText(ticketId)) {
            return List.of();
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    """
                    SELECT ch.ticket_id, ch.message
                      FROM chat_history ch
                      JOIN messages m ON m.ticket_id = ch.ticket_id
                     WHERE m.user_id = ?
                       AND ch.ticket_id <> ?
                       AND lower(COALESCE(ch.sender, '')) IN ('operator','support','admin','system','ai_agent')
                       AND ch.message IS NOT NULL
                       AND trim(ch.message) <> ''
                     ORDER BY ch.id DESC
                     LIMIT ?
                    """,
                    userId,
                    ticketId,
                    limit
            );
        } catch (Exception ex) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String prevTicket = safe(row.get("ticket_id"));
            String message = cleanTextForRetrieval(safe(row.get("message")));
            double score = scoreByTokens(queryTokens, entities, message);
            if (score <= 0d) {
                continue;
            }
            double boosted = Math.max(0d, Math.min(1d, score + 0.12d));
            out.add(new Candidate(
                    "applicant_history",
                    StringUtils.hasText(prevTicket) ? "История заявителя #" + prevTicket : "История заявителя",
                    cut(message, 260),
                    boosted,
                    null,
                    "draft",
                    "low",
                    "applicant_history",
                    "normal"
            ));
        }
        return out;
    }

    private Long resolveApplicantUserId(String ticketId) {
        String normalizedTicketId = trim(ticketId);
        if (normalizedTicketId == null) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    "SELECT user_id FROM messages WHERE ticket_id = ? AND user_id IS NOT NULL LIMIT 1",
                    rs -> rs.next() ? rs.getLong("user_id") : null,
                    normalizedTicketId
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Candidate> rerankSuggestions(Set<String> queryTokens, List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Candidate> weighted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            double score = applySourceWeight(candidate.source(), candidate.score());
            weighted.add(new Candidate(
                    candidate.source(),
                    candidate.title(),
                    candidate.snippet(),
                    score,
                    candidate.memoryKey(),
                    candidate.status(),
                    candidate.trustLevel(),
                    candidate.sourceType(),
                    candidate.safetyLevel()
            ));
        }
        Map<String, Candidate> bestBySource = new HashMap<>();
        for (Candidate candidate : weighted) {
            Candidate current = bestBySource.get(candidate.source());
            if (current == null || candidate.score() > current.score()) {
                bestBySource.put(candidate.source(), candidate);
            }
        }
        List<Candidate> reranked = new ArrayList<>();
        for (Candidate candidate : weighted) {
            double penalty = 0d;
            for (Candidate anchor : bestBySource.values()) {
                if (anchor.source().equals(candidate.source())) {
                    continue;
                }
                if (anchor.score() < 0.45d || candidate.score() < 0.45d) {
                    continue;
                }
                double similarity = similarity(anchor.snippet(), candidate.snippet());
                if (similarity < 0.12d) {
                    penalty = Math.max(penalty, 0.08d);
                }
            }
            if ("memory".equals(candidate.source())) {
                penalty = Math.max(0d, penalty - 0.03d);
            }
            double tokenCover = queryTokens.isEmpty() ? 0d : similarity(String.join(" ", queryTokens), candidate.snippet());
            double adjusted = Math.max(0d, Math.min(1d, candidate.score() - penalty + Math.min(0.06d, tokenCover * 0.1d)));
            reranked.add(new Candidate(
                    candidate.source(),
                    candidate.title(),
                    candidate.snippet(),
                    adjusted,
                    candidate.memoryKey(),
                    candidate.status(),
                    candidate.trustLevel(),
                    candidate.sourceType(),
                    candidate.safetyLevel()
            ));
        }
        return reranked;
    }

    private double applySourceWeight(String source, double score) {
        double weight = switch (String.valueOf(source).toLowerCase(Locale.ROOT)) {
            case "memory" -> 1.15d;
            case "knowledge" -> 1.08d;
            case "tasks" -> 1.00d;
            case "history" -> 0.92d;
            default -> 1.0d;
        };
        return Math.max(0d, Math.min(1d, score * weight));
    }

    private double scoreByTokens(Set<String> queryTokens, Set<String> entities, String source) {
        Set<String> sourceTokens = tokenize(source);
        if (queryTokens.isEmpty() && (entities == null || entities.isEmpty())) return 0d;
        if (sourceTokens.isEmpty()) return 0d;
        int overlap = 0;
        for (String token : queryTokens) {
            if (sourceTokens.contains(token)) {
                overlap++;
            }
        }
        double base = queryTokens.isEmpty() ? 0d : overlap / (double) queryTokens.size();
        String normalizedSource = normalize(source);
        double phraseBoost = 0d;
        for (String token : queryTokens) {
            if (token.length() >= 5 && normalizedSource.contains(token)) phraseBoost += 0.02d;
        }
        double entityBoost = 0d;
        if (entities != null && !entities.isEmpty()) {
            int entityHits = 0;
            for (String hint : entities) {
                if (normalizedSource.contains(hint)) entityHits++;
            }
            entityBoost = Math.min(0.24d, entityHits * 0.08d);
        }
        return Math.max(0d, Math.min(1d, base + phraseBoost + entityBoost));
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

    private double similarity(String left, String right) {
        Set<String> first = tokenize(left);
        Set<String> second = tokenize(right);
        if (first.isEmpty() || second.isEmpty()) return 0d;
        int intersection = 0;
        for (String token : first) if (second.contains(token)) intersection++;
        int union = first.size() + second.size() - intersection;
        return union <= 0 ? 0d : intersection / (double) union;
    }

    private String cleanTextForRetrieval(String value) {
        if (!StringUtils.hasText(value)) return "";
        String text = stripHtml(value);
        text = text.replaceAll("```[\\s\\S]*?```", " ");
        text = text.replaceAll("\\[[^\\]]+\\]\\([^\\)]+\\)", " ");
        text = text.replaceAll("https?://\\S+", " ");
        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private String stripHtml(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String join(String... chunks) {
        StringBuilder builder = new StringBuilder();
        if (chunks == null) return "";
        for (String chunk : chunks) {
            String item = trim(chunk);
            if (item == null) continue;
            if (!builder.isEmpty()) builder.append(". ");
            builder.append(item);
        }
        return builder.toString();
    }

    private String cut(String text, int len) {
        String value = trim(text);
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int toInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    public record Candidate(String source,
                            String title,
                            String snippet,
                            double score,
                            String memoryKey,
                            String status,
                            String trustLevel,
                            String sourceType,
                            String safetyLevel) {
    }
}

