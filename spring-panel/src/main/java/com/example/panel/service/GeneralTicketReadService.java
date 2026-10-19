package com.example.panel.service;

import com.example.panel.storage.AttachmentStorageKeyResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Every query intersects canonical GENERAL ownership with the current explicit read grant. */
@Service
public class GeneralTicketReadService {
    private static final String TICKETS = """
        SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status, ticket.created_at,
               left(root.client_name, 200) AS client_name, left(root.problem, 20000) AS problem,
               (SELECT COUNT(*) FROM tickets same_id WHERE same_id.ticket_id = ticket.ticket_id) = 1
                   AS relation_verified
          FROM tickets ticket
          LEFT JOIN messages root ON root.group_msg_id = ticket.group_msg_id
               AND root.user_id = ticket.user_id AND root.ticket_id = ticket.ticket_id
         WHERE ticket.is_general = TRUE AND ticket.business_id IS NULL
           AND ticket.ticket_public_id IS NOT NULL
           AND EXISTS (SELECT 1 FROM general_ticket_read_grants access
                         JOIN users account ON account.id = access.user_id
                        WHERE access.user_id = :userId AND access.is_active = TRUE AND
        """ + GeneralTicketAccessService.CURRENT_ACCOUNT + ") ";
    private static final String ONE_TICKET = "WITH authorized_ticket AS (" + TICKETS
        + " AND ticket.ticket_public_id = :publicId) ";

    private final NamedParameterJdbcTemplate jdbc;
    private final GeneralTicketAccessService access;

    public GeneralTicketReadService(JdbcTemplate jdbc, GeneralTicketAccessService access) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.access = access;
    }

    public TicketPage page(long userId, int limit, int offset, String status, String query) {
        bounds(limit, offset);
        if (status != null && !Set.of("pending", "open", "resolved", "closed").contains(status)) {
            throw new IllegalArgumentException("Invalid ticket status");
        }
        if (query != null && (query.isBlank() || query.length() > 100
                || query.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Invalid ticket search");
        }
        access.requireRead(userId);
        String sql = TICKETS + (status == null ? "" : " AND ticket.status = :status")
            + (query == null ? "" : """
                 AND (strpos(lower(coalesce(root.client_name, '')), lower(:query)) > 0
                      OR strpos(lower(coalesce(root.problem, '')), lower(:query)) > 0
                      OR strpos(lower(ticket.ticket_id), lower(:query)) > 0)
                """)
            + " ORDER BY ticket.created_at DESC NULLS LAST, ticket.user_id, ticket.ticket_id LIMIT :limit OFFSET :offset";
        List<TicketRow> rows = jdbc.query(sql, params(userId, null).addValue("limit", limit + 1)
            .addValue("offset", offset).addValue("status", status).addValue("query", query), (rs, row) -> ticket(rs));
        boolean hasMore = rows.size() > limit;
        return new TicketPage(List.copyOf(rows.subList(0, Math.min(limit, rows.size()))), hasMore);
    }

    public Optional<TicketRow> find(long userId, String publicId) {
        if (!validPublicId(publicId)) return Optional.empty();
        return jdbc.query(ONE_TICKET + "SELECT * FROM authorized_ticket", params(userId, publicId),
            (rs, row) -> ticket(rs)).stream().findFirst();
    }

    public Optional<TicketHistory> history(long userId, String publicId, Long beforeId, int limit) {
        bounds(limit, 0);
        if (beforeId != null && beforeId <= 0) throw new IllegalArgumentException("Invalid history cursor");
        if (!validPublicId(publicId)) return Optional.empty();
        return jdbc.query(ONE_TICKET + """
            SELECT ticket.*, history.id AS history_id, history.sender, left(history.message, 20000) AS message,
                   history.timestamp, history.message_type, history.attachment IS NOT NULL AS has_attachment
              FROM authorized_ticket ticket LEFT JOIN LATERAL (
                  SELECT h.id, h.sender, h.message, h.timestamp, h.message_type, h.attachment
                    FROM chat_history h WHERE h.ticket_owner_user_id = ticket.user_id
                     AND h.ticket_id = ticket.ticket_id AND h.deleted_at IS NULL AND h.id < :beforeId
                   ORDER BY h.id DESC LIMIT :limit
              ) history ON TRUE ORDER BY history.id ASC NULLS LAST
            """, params(userId, publicId).addValue("beforeId", beforeId == null ? Long.MAX_VALUE : beforeId)
                .addValue("limit", limit + 1), rs -> {
                    TicketRow owner = null;
                    List<HistoryEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        if (owner == null) owner = ticket(rs);
                        if (rs.getObject("history_id") != null) entries.add(new HistoryEntry(rs.getLong("history_id"),
                            rs.getString("sender"), rs.getString("message"), rs.getObject("timestamp", OffsetDateTime.class),
                            rs.getString("message_type"), rs.getBoolean("has_attachment")));
                    }
                    if (owner == null) return Optional.<TicketHistory>empty();
                    boolean more = entries.size() > limit;
                    if (more) entries.remove(0);
                    return Optional.of(new TicketHistory(owner, List.copyOf(entries), more,
                        more ? entries.get(0).id() : null));
                });
    }

    public Optional<TicketAnswers> answers(long userId, String publicId) {
        if (!validPublicId(publicId)) return Optional.empty();
        return jdbc.query(ONE_TICKET + """
            SELECT ticket.*, answer.question_id, left(answer.question_text, 2000) AS question_text,
                   left(answer.value_label, 10000) AS value_label, left(answer.value_text, 10000) AS value_text
              FROM authorized_ticket ticket LEFT JOIN LATERAL (
                  SELECT a.question_id, a.question_text, a.value_label, a.value_text
                    FROM ticket_attributes a WHERE a.ticket_id = ticket.ticket_id AND ticket.relation_verified
                   ORDER BY a.question_id LIMIT 101
              ) answer ON TRUE ORDER BY answer.question_id
            """, params(userId, publicId), rs -> {
                TicketRow owner = null;
                boolean verified = false;
                List<AnswerEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    if (owner == null) { owner = ticket(rs); verified = rs.getBoolean("relation_verified"); }
                    if (rs.getString("question_id") != null) entries.add(new AnswerEntry(rs.getString("question_id"),
                        rs.getString("question_text"), rs.getString("value_label"), rs.getString("value_text")));
                }
                if (owner == null) return Optional.<TicketAnswers>empty();
                if (entries.size() > 100) throw new AnswerLimitExceededException();
                return Optional.of(new TicketAnswers(owner, verified, List.copyOf(entries)));
            });
    }

    public Optional<AttachmentReference> attachment(long userId, String publicId, long historyId) {
        if (!validPublicId(publicId) || historyId <= 0) return Optional.empty();
        List<AttachmentReference> rows = jdbc.query(ONE_TICKET + """
            SELECT ticket.ticket_id, history.attachment, metadata.storage_key, metadata.original_name
              FROM authorized_ticket ticket
              JOIN chat_history history ON history.id = :historyId
                   AND history.ticket_owner_user_id = ticket.user_id AND history.ticket_id = ticket.ticket_id
                   AND history.deleted_at IS NULL
              JOIN chat_attachment_metadata metadata ON metadata.chat_history_id = history.id
                   AND metadata.ticket_id = ticket.ticket_id
                   AND metadata.deleted_at IS NULL AND metadata.archived_at IS NULL
                   AND metadata.storage_class = 'dialog_attachment' AND metadata.normalization_status = 'normalized'
                   AND metadata.availability_status = 'available' AND metadata.storage_provider IN ('local_fs', 's3')
                   AND metadata.storage_key IS NOT NULL
             WHERE ticket.relation_verified AND NOT EXISTS (
                 SELECT 1 FROM chat_attachment_metadata other
                  WHERE other.storage_key = metadata.storage_key AND other.id <> metadata.id)
            """, params(userId, publicId).addValue("historyId", historyId), (rs, row) -> {
                String ticketId = rs.getString("ticket_id");
                String raw = rs.getString("attachment");
                String key = rs.getString("storage_key");
                if (!safeStorageKey(ticketId, key)
                        || !key.equals(AttachmentStorageKeyResolver.normalizeStorageKey(ticketId, raw))) return null;
                return new AttachmentReference(key,
                    AttachmentStorageKeyResolver.resolveOriginalName(rs.getString("original_name"), raw, key));
            });
        return rows.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    private boolean safeStorageKey(String ticketId, String key) {
        if (ticketId == null || key == null || key.length() > 4096 || !key.startsWith(ticketId + "/")
                || key.chars().anyMatch(Character::isISOControl)) return false;
        for (String part : key.split("/", -1)) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)
                    || !part.equals(part.trim()) || part.endsWith(".")) return false;
        }
        return true;
    }

    private MapSqlParameterSource params(long userId, String publicId) {
        return new MapSqlParameterSource().addValue("userId", userId).addValue("publicId", publicId);
    }

    private boolean validPublicId(String publicId) {
        return publicId != null && publicId.matches("[0-9a-f]{32}");
    }

    private void bounds(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new IllegalArgumentException("Invalid page bounds");
    }

    private TicketRow ticket(ResultSet rs) throws SQLException {
        return new TicketRow(rs.getString("ticket_public_id"), rs.getString("ticket_id"), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getString("client_name"), rs.getString("problem"));
    }

    public record TicketRow(String ticketPublicId, String ticketId, String status, OffsetDateTime createdAt,
                            String clientName, String problem) {}
    public record TicketPage(List<TicketRow> tickets, boolean hasMore) {}
    public record HistoryEntry(long id, String sender, String message, OffsetDateTime timestamp,
                               String messageType, boolean hasAttachment) {}
    public record TicketHistory(TicketRow ticket, List<HistoryEntry> entries, boolean hasMore, Long nextBeforeId) {}
    public record AnswerEntry(String questionId, String questionText, String valueLabel, String valueText) {}
    public record TicketAnswers(TicketRow ticket, boolean relationVerified, List<AnswerEntry> answers) {}
    public record AttachmentReference(String storageKey, String originalName) {}
    public static class AnswerLimitExceededException extends RuntimeException {}
}
