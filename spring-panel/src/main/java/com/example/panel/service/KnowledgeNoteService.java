package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class KnowledgeNoteService {

    public static final String TARGET_KNOWLEDGE_ARTICLE = "knowledge_article";
    public static final String TARGET_OBJECT_PASSPORT = "object_passport";
    private static final int MAX_CUSTOM_FIELDS = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeNoteService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<NoteSummary> listNotes() {
        String sql = """
            SELECT n.id, n.title, n.body, n.custom_fields::text AS custom_fields_json,
                   n.created_by, n.updated_at,
                   (SELECT COUNT(*) FROM knowledge_note_links l WHERE l.note_id = n.id) AS relation_count
              FROM knowledge_notes n
             ORDER BY n.updated_at DESC, n.id DESC
            """;
        return jdbcTemplate.query(sql, this::mapSummary);
    }

    @Transactional(readOnly = true)
    public Optional<NoteDetails> findNote(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        List<BaseNote> rows = jdbcTemplate.query("""
            SELECT id, title, body, custom_fields::text AS custom_fields_json,
                   created_by, updated_by, created_at, updated_at
              FROM knowledge_notes
             WHERE id = ?
            """, (rs, rowNum) -> new BaseNote(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("custom_fields_json"),
                rs.getString("created_by"),
                rs.getString("updated_by"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            ), id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        BaseNote row = rows.get(0);
        Set<Long> articleIds = loadLinkIds(id, TARGET_KNOWLEDGE_ARTICLE);
        Set<Long> passportIds = loadLinkIds(id, TARGET_OBJECT_PASSPORT);
        return Optional.of(new NoteDetails(
            row.id(), row.title(), row.body(), readCustomFields(row.customFieldsJson()),
            articleIds, passportIds, row.createdBy(), row.updatedBy(), row.createdAt(), row.updatedAt()
        ));
    }

    public long save(Long id,
                     String title,
                     String body,
                     List<String> customFieldKeys,
                     List<String> customFieldValues,
                     List<Long> knowledgeArticleIds,
                     List<Long> objectPassportIds,
                     String actor) {
        String normalizedTitle = limit(trim(title), 500);
        if (!StringUtils.hasText(normalizedTitle)) {
            throw new IllegalArgumentException("Заголовок заметки обязателен");
        }
        String safeBody = limit(body == null ? "" : body, 100000);
        String normalizedActor = limit(trim(actor), 255);
        String customFieldsJson = writeCustomFields(buildCustomFields(customFieldKeys, customFieldValues));
        long noteId;
        if (id == null) {
            Long inserted = jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_notes(title, body, custom_fields, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, CAST(? AS jsonb), ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, normalizedTitle, safeBody, customFieldsJson, normalizedActor, normalizedActor);
            if (inserted == null) {
                throw new IllegalStateException("Не удалось создать заметку");
            }
            noteId = inserted;
        } else {
            int updated = jdbcTemplate.update("""
                UPDATE knowledge_notes
                   SET title = ?, body = ?, custom_fields = CAST(? AS jsonb), updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, normalizedTitle, safeBody, customFieldsJson, normalizedActor, id);
            if (updated != 1) {
                throw new IllegalArgumentException("Заметка не найдена: " + id);
            }
            noteId = id;
        }
        replaceLinks(noteId, TARGET_KNOWLEDGE_ARTICLE, retainExistingIds("knowledge_articles", knowledgeArticleIds));
        if (id == null || objectPassportIds != null) {
            replaceLinks(noteId, TARGET_OBJECT_PASSPORT, retainExistingIds("object_passports", objectPassportIds));
        }
        return noteId;
    }

    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM knowledge_notes WHERE id = ?", id);
    }

    private NoteSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        Map<String, String> customFields = readCustomFields(rs.getString("custom_fields_json"));
        return new NoteSummary(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("created_by"),
            rs.getObject("updated_at", OffsetDateTime.class),
            customFields.size(),
            rs.getInt("relation_count")
        );
    }

    private Set<Long> loadLinkIds(long noteId, String targetType) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
            "SELECT target_id FROM knowledge_note_links WHERE note_id = ? AND target_type = ? ORDER BY id",
            Long.class, noteId, targetType));
    }

    private void replaceLinks(long noteId, String targetType, Set<Long> targetIds) {
        jdbcTemplate.update("DELETE FROM knowledge_note_links WHERE note_id = ? AND target_type = ?", noteId, targetType);
        for (Long targetId : targetIds) {
            jdbcTemplate.update("""
                INSERT INTO knowledge_note_links(note_id, target_type, target_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (note_id, target_type, target_id) DO NOTHING
                """, noteId, targetType, targetId);
        }
    }

    private Set<Long> retainExistingIds(String table, List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = rawIds.stream().filter(value -> value != null && value > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        if (!"knowledge_articles".equals(table) && !"object_passports".equals(table)) {
            throw new IllegalArgumentException("Unsupported note link target");
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        List<Long> existing = jdbcTemplate.queryForList(
            "SELECT id FROM " + table + " WHERE id IN (" + placeholders + ") ORDER BY id",
            Long.class,
            ids.toArray()
        );
        return new LinkedHashSet<>(existing);
    }

    private Map<String, String> buildCustomFields(List<String> keys, List<String> values) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        List<String> safeValues = values == null ? List.of() : values;
        int count = Math.min(keys.size(), MAX_CUSTOM_FIELDS);
        for (int i = 0; i < count; i++) {
            String key = limit(trim(keys.get(i)), 120);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String value = i < safeValues.size() ? limit(trim(safeValues.get(i)), 4000) : "";
            result.put(key, value);
        }
        return result;
    }

    private String writeCustomFields(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields == null ? Map.of() : fields);
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось сохранить пользовательские поля заметки", ex);
        }
    }

    private Map<String, String> readCustomFields(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            LinkedHashMap<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() { });
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            parsed.forEach((key, value) -> result.put(key, value == null ? "" : String.valueOf(value)));
            return result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max);
    }

    private record BaseNote(Long id,
                            String title,
                            String body,
                            String customFieldsJson,
                            String createdBy,
                            String updatedBy,
                            OffsetDateTime createdAt,
                            OffsetDateTime updatedAt) { }

    public record NoteSummary(Long id,
                              String title,
                              String body,
                              String createdBy,
                              OffsetDateTime updatedAt,
                              int customFieldCount,
                              int relationCount) { }

    public record NoteDetails(Long id,
                              String title,
                              String body,
                              Map<String, String> customFields,
                              Set<Long> knowledgeArticleIds,
                              Set<Long> objectPassportIds,
                              String createdBy,
                              String updatedBy,
                              OffsetDateTime createdAt,
                              OffsetDateTime updatedAt) { }
}
