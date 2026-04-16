package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiKnowledgeService {

    private final JdbcTemplate jdbcTemplate;

    public AiKnowledgeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void syncFromMemory(String queryKey) {
        String key = trim(queryKey);
        if (key == null) {
            return;
        }
        try {
            Map<String, Object> row = jdbcTemplate.query(
                    """
                    SELECT query_key,
                           query_text,
                           solution_text,
                           status,
                           intent_key,
                           slot_signature,
                           scope_channel,
                           scope_business,
                           scope_location
                      FROM ai_agent_solution_memory
                     WHERE query_key = ?
                     LIMIT 1
                    """,
                    rs -> rs.next() ? Map.of(
                            "query_key", trim(rs.getString("query_key")),
                            "query_text", trim(rs.getString("query_text")),
                            "solution_text", trim(rs.getString("solution_text")),
                            "status", trim(rs.getString("status")),
                            "intent_key", trim(rs.getString("intent_key")),
                            "slot_signature", trim(rs.getString("slot_signature")),
                            "scope_channel", trim(rs.getString("scope_channel")),
                            "scope_business", trim(rs.getString("scope_business")),
                            "scope_location", trim(rs.getString("scope_location"))
                    ) : null,
                    key
            );
            if (row == null) {
                forgetMemory(key);
                return;
            }
            if (!"approved".equalsIgnoreCase(String.valueOf(row.get("status"))) || !StringUtils.hasText(String.valueOf(row.get("solution_text")))) {
                forgetMemory(key);
                return;
            }

            List<Long> previousUnitIds = jdbcTemplate.query(
                    "SELECT knowledge_unit_id FROM ai_agent_memory_link WHERE query_key = ?",
                    (rs, rowNum) -> rs.getLong("knowledge_unit_id"),
                    key
            );

            String unitKey = buildUnitKey(
                    safe(row.get("intent_key")),
                    safe(row.get("slot_signature")),
                    safe(row.get("scope_channel")),
                    safe(row.get("scope_business")),
                    safe(row.get("scope_location")),
                    safe(row.get("solution_text"))
            );
            Long knowledgeUnitId = jdbcTemplate.query(
                    "SELECT id FROM ai_agent_knowledge_unit WHERE unit_key = ? LIMIT 1",
                    rs -> rs.next() ? rs.getLong("id") : null,
                    unitKey
            );
            if (knowledgeUnitId == null) {
                jdbcTemplate.update(
                        """
                        INSERT INTO ai_agent_knowledge_unit(
                            unit_key, title, body_text, intent_key, slot_signature,
                            business, location, channel, status, source_ref, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                        unitKey,
                        buildTitle(safe(row.get("intent_key")), safe(row.get("query_text"))),
                        safe(row.get("solution_text")),
                        trim(safe(row.get("intent_key"))),
                        trim(safe(row.get("slot_signature"))),
                        trim(safe(row.get("scope_business"))),
                        trim(safe(row.get("scope_location"))),
                        trim(safe(row.get("scope_channel"))),
                        "memory:" + key
                );
                knowledgeUnitId = jdbcTemplate.query(
                        "SELECT id FROM ai_agent_knowledge_unit WHERE unit_key = ? LIMIT 1",
                        rs -> rs.next() ? rs.getLong("id") : null,
                        unitKey
                );
            } else {
                jdbcTemplate.update(
                        """
                        UPDATE ai_agent_knowledge_unit
                           SET title = ?,
                               body_text = ?,
                               intent_key = ?,
                               slot_signature = ?,
                               business = ?,
                               location = ?,
                               channel = ?,
                               status = 'active',
                               source_ref = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                        """,
                        buildTitle(safe(row.get("intent_key")), safe(row.get("query_text"))),
                        safe(row.get("solution_text")),
                        trim(safe(row.get("intent_key"))),
                        trim(safe(row.get("slot_signature"))),
                        trim(safe(row.get("scope_business"))),
                        trim(safe(row.get("scope_location"))),
                        trim(safe(row.get("scope_channel"))),
                        "memory:" + key,
                        knowledgeUnitId
                );
            }

            jdbcTemplate.update("DELETE FROM ai_agent_memory_link WHERE query_key = ?", key);
            if (knowledgeUnitId != null) {
                jdbcTemplate.update(
                        """
                        INSERT INTO ai_agent_memory_link(query_key, knowledge_unit_id, link_type, weight, created_at)
                        VALUES (?, ?, 'supports', 1.0, CURRENT_TIMESTAMP)
                        """,
                        key,
                        knowledgeUnitId
                );
            }
            cleanupOrphanUnits(previousUnitIds);
        } catch (Exception ignored) {
        }
    }

    public void forgetMemory(String queryKey) {
        String key = trim(queryKey);
        if (key == null) {
            return;
        }
        try {
            List<Long> previousUnitIds = jdbcTemplate.query(
                    "SELECT knowledge_unit_id FROM ai_agent_memory_link WHERE query_key = ?",
                    (rs, rowNum) -> rs.getLong("knowledge_unit_id"),
                    key
            );
            jdbcTemplate.update("DELETE FROM ai_agent_memory_link WHERE query_key = ?", key);
            cleanupOrphanUnits(previousUnitIds);
        } catch (Exception ignored) {
        }
    }

    private void cleanupOrphanUnits(List<Long> previousUnitIds) {
        if (previousUnitIds != null) {
            for (Long unitId : previousUnitIds) {
                if (unitId == null) {
                    continue;
                }
                jdbcTemplate.update(
                        """
                        UPDATE ai_agent_knowledge_unit
                           SET status = 'deprecated',
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                           AND NOT EXISTS (
                               SELECT 1
                                 FROM ai_agent_memory_link
                                WHERE knowledge_unit_id = ?
                           )
                        """,
                        unitId,
                        unitId
                );
            }
        }
    }

    private String buildTitle(String intentKey, String queryText) {
        String normalizedIntent = trim(intentKey);
        if (normalizedIntent != null) {
            return normalizedIntent.replace('_', ' ');
        }
        String normalizedQuery = trim(queryText);
        if (normalizedQuery == null) {
            return "knowledge unit";
        }
        return normalizedQuery.length() <= 80 ? normalizedQuery : normalizedQuery.substring(0, 77) + "...";
    }

    private String buildUnitKey(String intentKey,
                                String slotSignature,
                                String scopeChannel,
                                String scopeBusiness,
                                String scopeLocation,
                                String solutionText) {
        String seed = normalize(intentKey) + "|" + normalize(slotSignature) + "|" + normalize(scopeChannel)
                + "|" + normalize(scopeBusiness) + "|" + normalize(scopeLocation) + "|" + normalize(solutionText);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }
}
