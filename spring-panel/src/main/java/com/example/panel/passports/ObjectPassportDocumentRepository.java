package com.example.panel.passports;

import com.example.panel.passports.ObjectPassportDocumentStorage.FileInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class ObjectPassportDocumentRepository {
    private static final String SELECT = "SELECT d.passport_id, r.* FROM object_passport_documents d "
            + "JOIN object_passport_document_revisions r ON r.document_id=d.id ";
    private static final String FILTER = " WHERE d.passport_id=? AND (? OR NOT d.archived) "
            + "AND POSITION(lower(?) IN lower(r.title || ' ' || r.category || ' ' || r.description))>0 ";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<ObjectPassportDocument> mapper;

    public ObjectPassportDocumentRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.mapper = (rs, row) -> {
            String key = rs.getString("storage_key");
            FileInfo file = key == null ? null : new FileInfo(key, rs.getString("original_name"), rs.getString("media_type"),
                    rs.getLong("file_size"), rs.getString("preview_kind"), rs.getString("preview_text"));
            try {
                return new ObjectPassportDocument(rs.getObject("document_id", UUID.class), rs.getLong("passport_id"),
                        rs.getInt("revision"), rs.getString("title"), rs.getString("category"), rs.getObject("event_date", LocalDate.class),
                        rs.getString("description"), json.readValue(rs.getString("fields"), new TypeReference<Map<String, String>>() {}),
                        rs.getBoolean("archived"), file, rs.getString("action"), rs.getString("actor"), rs.getObject("created_at", OffsetDateTime.class));
            } catch (java.io.IOException ex) { throw new IllegalStateException("Некорректные поля документа", ex); }
        };
    }

    public void requirePassport(long passportId, boolean lock) {
        if (jdbc.queryForList("SELECT id FROM object_passports WHERE id=?" + (lock ? " FOR KEY SHARE" : ""), Long.class, passportId).isEmpty())
            throw missing();
    }

    public List<ObjectPassportDocument> list(long passportId, boolean archived, String query, UUID before, int limit) {
        return jdbc.query(SELECT + FILTER + " AND r.revision=d.current_revision "
                + "AND (?::uuid IS NULL OR (d.created_at,d.id)<(SELECT created_at,id FROM object_passport_documents WHERE id=? AND passport_id=?)) "
                + "ORDER BY d.created_at DESC,d.id DESC LIMIT ?", mapper, passportId, archived, query, before, before, passportId, limit);
    }

    public long count(long passportId, boolean archived, String query) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM object_passport_documents d JOIN object_passport_document_revisions r "
                + "ON r.document_id=d.id AND r.revision=d.current_revision" + FILTER, Long.class, passportId, archived, query);
    }

    public ObjectPassportDocument current(long passportId, UUID id, boolean lock) {
        if (lock && jdbc.queryForList("SELECT id FROM object_passport_documents WHERE passport_id=? AND id=? FOR UPDATE",
                UUID.class, passportId, id).isEmpty()) throw missing();
        return one(jdbc.query(SELECT + "WHERE d.passport_id=? AND d.id=? AND r.revision=d.current_revision", mapper, passportId, id));
    }

    public ObjectPassportDocument version(long passportId, UUID id, int version) {
        return one(jdbc.query(SELECT + "WHERE d.passport_id=? AND d.id=? AND r.revision=?", mapper, passportId, id, version));
    }

    public List<ObjectPassportDocument> history(long passportId, UUID id, int before, int limit) {
        return jdbc.query(SELECT + "WHERE d.passport_id=? AND d.id=? AND r.revision<? ORDER BY r.revision DESC LIMIT ?",
                mapper, passportId, id, before, limit);
    }

    public void create(ObjectPassportDocument document) {
        jdbc.update("INSERT INTO object_passport_documents(id,passport_id,current_revision,archived) VALUES (?,?,1,FALSE)",
                document.id(), document.passportId());
        insertVersion(document);
    }

    public void append(ObjectPassportDocument document, int expected) {
        int updated = jdbc.update("UPDATE object_passport_documents SET current_revision=?,archived=?,updated_at=CURRENT_TIMESTAMP "
                + "WHERE id=? AND passport_id=? AND current_revision=?", document.revision(), document.archived(), document.id(), document.passportId(), expected);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Документ уже изменён. Обновите его перед сохранением.");
        insertVersion(document);
    }

    private void insertVersion(ObjectPassportDocument d) {
        FileInfo f = d.file();
        try {
            jdbc.update("""
                INSERT INTO object_passport_document_revisions(document_id,revision,title,category,event_date,description,fields,
                  archived,storage_key,original_name,media_type,file_size,preview_kind,preview_text,action,actor,created_at)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?)
                """, d.id(), d.revision(), d.title(), d.category(), d.eventDate(), d.description(), json.writeValueAsString(d.fields()),
                    d.archived(), f == null ? null : f.key(), f == null ? null : f.name(), f == null ? null : f.mediaType(),
                    f == null ? null : f.size(), f == null ? "none" : f.previewKind(), f == null ? null : f.previewText(), d.action(), d.actor(), d.changedAt());
        } catch (java.io.IOException ex) { throw new IllegalStateException("Не удалось сохранить поля документа", ex); }
    }

    private ObjectPassportDocument one(List<ObjectPassportDocument> rows) { if (rows.size()!=1) throw missing(); return rows.get(0); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Паспорт или документ не найден"); }
}
