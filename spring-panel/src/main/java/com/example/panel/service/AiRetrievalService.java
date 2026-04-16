package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiRetrievalService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> STOP = Set.of("и","в","на","не","что","как","для","или","по","из","к","у","о","об","the","a","an","to","of","in","on","for","and","or","is","are","be");

    private final JdbcTemplate jdbcTemplate;

    public AiRetrievalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Candidate> findSuggestions(String ticketId, String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String normalized = normalize(query);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        Set<String> queryTokens = tokenize(normalized);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    """
                    SELECT query_key, query_text, solution_text, status, trust_level, source_type, safety_level,
                           times_confirmed, times_corrected
                      FROM ai_agent_solution_memory
                     WHERE COALESCE(review_required,0)=0
                       AND solution_text IS NOT NULL
                       AND trim(solution_text) <> ''
                     ORDER BY COALESCE(updated_at, created_at) DESC
                     LIMIT ?
                    """,
                    safeLimit * 8
            );
        } catch (Exception ex) {
            rows = jdbcTemplate.queryForList(
                    """
                    SELECT query_key, query_text, solution_text,
                           NULL AS status, NULL AS trust_level, NULL AS source_type, NULL AS safety_level,
                           times_confirmed, times_corrected
                      FROM ai_agent_solution_memory
                     WHERE COALESCE(review_required,0)=0
                       AND solution_text IS NOT NULL
                       AND trim(solution_text) <> ''
                     ORDER BY COALESCE(updated_at, created_at) DESC
                     LIMIT ?
                    """,
                    safeLimit * 8
            );
        }

        List<Candidate> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String queryText = safe(row.get("query_text"));
            String solution = safe(row.get("solution_text"));
            String haystack = normalize(queryText + " " + solution);
            double score = scoreByOverlap(queryTokens, tokenize(haystack));
            int confirmed = toInt(row.get("times_confirmed"));
            int corrected = toInt(row.get("times_corrected"));
            score = Math.max(0d, Math.min(1d, score + Math.min(0.20d, confirmed * 0.02d) - Math.min(0.15d, corrected * 0.02d)));
            if (score <= 0d) {
                continue;
            }
            out.add(new Candidate(
                    "memory",
                    "Память решений",
                    cut(solution, 420),
                    score,
                    trim(safe(row.get("query_key"))),
                    trimOrDefault(row.get("status"), "approved"),
                    trimOrDefault(row.get("trust_level"), "medium"),
                    trimOrDefault(row.get("source_type"), "operator"),
                    trimOrDefault(row.get("safety_level"), "normal")
            ));
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (out.size() > safeLimit) {
            return out.subList(0, safeLimit);
        }
        return out;
    }

    private double scoreByOverlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        int overlap = 0;
        for (String token : a) {
            if (b.contains(token)) {
                overlap++;
            }
        }
        return overlap / (double) a.size();
    }

    private Set<String> tokenize(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(value)) {
            String normalized = trim(token);
            if (normalized == null || normalized.length() < 2 || STOP.contains(normalized)) {
                continue;
            }
            out.add(normalized);
        }
        return out;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int toInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String trimOrDefault(Object value, String fallback) {
        String normalized = trim(safe(value));
        return normalized != null ? normalized : fallback;
    }

    private String cut(String value, int max) {
        String normalized = trim(value);
        if (normalized == null) {
            return "";
        }
        return normalized.length() <= max ? normalized : normalized.substring(0, Math.max(0, max - 3)) + "...";
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

