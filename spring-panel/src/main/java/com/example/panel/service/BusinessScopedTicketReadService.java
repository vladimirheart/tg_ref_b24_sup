package com.example.panel.service;

import com.example.panel.storage.AttachmentStorageKeyResolver;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Read side for canonical business tickets; legacy display labels are never an owner. */
@Service
public class BusinessScopedTicketReadService {

    private static final Pattern PUBLIC_ID = Pattern.compile("[0-9a-f]{32}");
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final BusinessAccessService accessService;

    public BusinessScopedTicketReadService(JdbcTemplate jdbcTemplate, BusinessAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.accessService = accessService;
    }

    public Long requireAuthenticatedUserId(String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 2",
                (rs, rowNum) -> rs.getLong("id"), username);
        if (ids.size() != 1) {
            throw new AccessDeniedException("Authenticated user is not available");
        }
        return ids.get(0);
    }

    public List<BusinessOption> availableBusinesses(Long userId) {
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return namedJdbcTemplate.query("""
                SELECT business.id, business.code, business.display_name
                  FROM businesses business
                 WHERE business.id IN (:businessIds) AND business.status = 'active'
                   AND (EXISTS (
                       SELECT 1 FROM business_memberships membership
                        WHERE membership.business_id = business.id
                          AND membership.user_id = :userId AND membership.is_active = TRUE
                   ) OR EXISTS (
                       SELECT 1 FROM business_access_grants access_grant
                        WHERE access_grant.user_id = :userId
                          AND access_grant.grant_scope = 'ALL_BUSINESSES'
                          AND access_grant.is_active = TRUE
                   ))
                 ORDER BY business.id
                """, new MapSqlParameterSource()
                        .addValue("businessIds", ids).addValue("userId", userId),
                (rs, rowNum) -> new BusinessOption(
                        rs.getLong("id"), rs.getString("code"), rs.getString("display_name")));
    }

    public List<TicketRow> listTickets(Long userId,
                                       BusinessAccessService.RequestedBusinessContext context,
                                       int limit,
                                       int offset) {
        return listTickets(userId, context, limit, offset, null, null);
    }

    public List<TicketRow> listTickets(Long userId,
                                       BusinessAccessService.RequestedBusinessContext context,
                                       int limit, int offset, String status, String ticketId) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid page bounds");
        }
        if (status != null && !Set.of("pending", "open", "resolved", "closed").contains(status)) {
            throw new IllegalArgumentException("Invalid ticket status");
        }
        if (ticketId != null && (ticketId.isBlank() || ticketId.length() > 255
                || ticketId.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Invalid ticket ID");
        }
        Set<Long> ids = accessService.requireSelectedBusiness(userId, context).businessIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                       business.id AS business_id, business.code AS business_code,
                       business.display_name AS business_name
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                 WHERE ticket.business_id IN (:businessIds)
                   AND business.status = 'active'
                   AND (EXISTS (
                       SELECT 1 FROM business_memberships membership
                        WHERE membership.business_id = business.id
                          AND membership.user_id = :userId AND membership.is_active = TRUE
                   ) OR EXISTS (
                       SELECT 1 FROM business_access_grants access_grant
                        WHERE access_grant.user_id = :userId
                          AND access_grant.grant_scope = 'ALL_BUSINESSES'
                          AND access_grant.is_active = TRUE
                   ))
                """
                + (status == null ? "" : " AND ticket.status = :status\n")
                + (ticketId == null ? "" : " AND (ticket.ticket_id = :ticketId OR ticket.ticket_public_id = :ticketId)\n")
                + """
                 ORDER BY ticket.business_id, ticket.user_id, ticket.ticket_id
                 LIMIT :limit OFFSET :offset
                """;
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("businessIds", ids)
                        .addValue("userId", userId)
                        .addValue("status", status)
                        .addValue("ticketId", ticketId)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (rs, rowNum) -> new TicketRow(
                        rs.getLong("user_id"), rs.getString("ticket_id"),
                        rs.getString("ticket_public_id"), rs.getLong("business_id"),
                        rs.getString("business_code"), rs.getString("business_name"),
                        rs.getString("status")));
    }

    public Optional<TicketRow> findByPublicId(Long userId, String publicId) {
        if (publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(userId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<TicketRow> rows = namedJdbcTemplate.query("""
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                       business.id AS business_id, business.code AS business_code,
                       business.display_name AS business_name
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                 WHERE ticket.ticket_public_id = :publicId
                   AND ticket.business_id IN (:businessIds)
                   AND business.status = 'active'
                   AND (EXISTS (
                       SELECT 1 FROM business_memberships membership
                        WHERE membership.business_id = business.id
                          AND membership.user_id = :userId AND membership.is_active = TRUE
                   ) OR EXISTS (
                       SELECT 1 FROM business_access_grants access_grant
                        WHERE access_grant.user_id = :userId
                          AND access_grant.grant_scope = 'ALL_BUSINESSES'
                          AND access_grant.is_active = TRUE
                   ))
                """, new MapSqlParameterSource()
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", userId),
                (rs, rowNum) -> new TicketRow(
                        rs.getLong("user_id"), rs.getString("ticket_id"),
                        rs.getString("ticket_public_id"), rs.getLong("business_id"),
                        rs.getString("business_code"), rs.getString("business_name"),
                        rs.getString("status")));
        return rows.stream().findFirst();
    }

    /**
     * Only history stamped with the exact canonical ticket tuple is readable.
     * Legacy rows with a bare ticket_id remain unclassified, even if that ID is unique today.
     */
    public Optional<TicketHistory> findHistoryByPublicId(Long userId, String publicId,
                                                          Long beforeId, int limit) {
        if (limit < 1 || limit > 100 || (beforeId != null && beforeId < 1)) {
            throw new IllegalArgumentException("Invalid history page bounds");
        }
        if (publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(userId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return namedJdbcTemplate.query("""
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                       business.id AS business_id, business.code AS business_code,
                       business.display_name AS business_name,
                       history.id AS history_id, history.sender, history.message,
                       history.timestamp AS history_timestamp, history.message_type,
                       history.attachment
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                  LEFT JOIN LATERAL (
                      SELECT h.id, h.sender, h.message, h.timestamp, h.message_type, h.attachment
                        FROM chat_history h
                       WHERE h.ticket_owner_user_id = ticket.user_id
                         AND h.ticket_id = ticket.ticket_id
                         AND h.deleted_at IS NULL
                         AND h.id < :beforeId
                       ORDER BY h.id DESC
                       LIMIT :pageSize
                  ) history ON TRUE
                 WHERE ticket.ticket_public_id = :publicId
                   AND ticket.business_id IN (:businessIds)
                   AND business.status = 'active'
                   AND (EXISTS (
                       SELECT 1 FROM business_memberships membership
                        WHERE membership.business_id = business.id
                          AND membership.user_id = :userId AND membership.is_active = TRUE
                   ) OR EXISTS (
                       SELECT 1 FROM business_access_grants access_grant
                        WHERE access_grant.user_id = :userId
                          AND access_grant.grant_scope = 'ALL_BUSINESSES'
                          AND access_grant.is_active = TRUE
                   ))
                 ORDER BY history.id ASC NULLS LAST
                """, new MapSqlParameterSource()
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", userId)
                        .addValue("beforeId", beforeId == null ? Long.MAX_VALUE : beforeId)
                        .addValue("pageSize", limit + 1),
                rs -> {
                    TicketRow ticket = null;
                    List<HistoryEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        if (ticket == null) {
                            ticket = new TicketRow(
                                    rs.getLong("user_id"), rs.getString("ticket_id"),
                                    rs.getString("ticket_public_id"), rs.getLong("business_id"),
                                    rs.getString("business_code"), rs.getString("business_name"),
                                    rs.getString("status"));
                        }
                        if (rs.getObject("history_id") != null) {
                            entries.add(new HistoryEntry(
                                    rs.getLong("history_id"), rs.getString("sender"),
                                    rs.getString("message"),
                                    rs.getObject("history_timestamp", OffsetDateTime.class),
                                    rs.getString("message_type"), rs.getString("attachment") != null));
                        }
                    }
                    if (ticket == null) {
                        return Optional.<TicketHistory>empty();
                    }
                    boolean hasMore = entries.size() > limit;
                    if (hasMore) {
                        entries.remove(0);
                    }
                    Long nextBeforeId = hasMore ? entries.get(0).id() : null;
                    return Optional.of(new TicketHistory(ticket, entries, hasMore, nextBeforeId));
                });
    }

    /** A binary locator is issued only for one explicit history owner and unambiguous storage key. */
    public Optional<AttachmentReference> findAttachmentByPublicId(Long userId, String publicId, long historyId) {
        if (historyId < 1 || publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(userId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<AttachmentReference> rows = namedJdbcTemplate.query("""
                SELECT ticket.ticket_id, history.attachment, metadata.storage_key,
                       metadata.original_name
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                  JOIN chat_history history ON history.id = :historyId
                       AND history.ticket_owner_user_id = ticket.user_id
                       AND history.ticket_id = ticket.ticket_id
                       AND history.deleted_at IS NULL
                  JOIN chat_attachment_metadata metadata ON metadata.chat_history_id = history.id
                       AND metadata.ticket_id = ticket.ticket_id
                       AND metadata.deleted_at IS NULL
                       AND metadata.archived_at IS NULL
                       AND metadata.storage_class = 'dialog_attachment'
                       AND metadata.normalization_status = 'normalized'
                       AND metadata.availability_status = 'available'
                       AND metadata.storage_provider IN ('local_fs', 's3')
                       AND metadata.storage_key IS NOT NULL
                 WHERE ticket.ticket_public_id = :publicId
                   AND ticket.business_id IN (:businessIds)
                   AND business.status = 'active'
                   AND (SELECT COUNT(*) FROM tickets same_id
                         WHERE same_id.ticket_id = ticket.ticket_id) = 1
                   AND NOT EXISTS (
                       SELECT 1 FROM chat_attachment_metadata other
                        WHERE other.storage_key = metadata.storage_key
                          AND other.id <> metadata.id
                   )
                   AND (EXISTS (
                       SELECT 1 FROM business_memberships membership
                        WHERE membership.business_id = business.id
                          AND membership.user_id = :userId AND membership.is_active = TRUE
                   ) OR EXISTS (
                       SELECT 1 FROM business_access_grants access_grant
                        WHERE access_grant.user_id = :userId
                          AND access_grant.grant_scope = 'ALL_BUSINESSES'
                          AND access_grant.is_active = TRUE
                   ))
                """, new MapSqlParameterSource()
                        .addValue("historyId", historyId)
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", userId),
                (rs, rowNum) -> {
                    String ticketId = rs.getString("ticket_id");
                    String rawReference = rs.getString("attachment");
                    String storageKey = rs.getString("storage_key");
                    String expected = AttachmentStorageKeyResolver.normalizeStorageKey(ticketId, rawReference);
                    if (expected == null || !expected.equals(storageKey)
                            || !safeStorageKey(ticketId, storageKey)) {
                        return null;
                    }
                    String originalName = AttachmentStorageKeyResolver.resolveOriginalName(
                            rs.getString("original_name"), rawReference, storageKey);
                    return new AttachmentReference(storageKey, originalName);
                });
        return rows.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    private boolean safeStorageKey(String ticketId, String storageKey) {
        if (ticketId == null || storageKey == null || storageKey.length() > 4096
                || !storageKey.startsWith(ticketId + "/")
                || storageKey.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        for (String part : storageKey.split("/", -1)) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)
                    || !part.equals(part.trim()) || part.endsWith(".")) {
                return false;
            }
        }
        return true;
    }

    public record BusinessOption(long id, String code, String displayName) {
    }

    public record TicketRow(long userId, String ticketId, String ticketPublicId,
                            long businessId, String businessCode, String businessName,
                            String status) {
    }

    public record HistoryEntry(long id, String sender, String message,
                               OffsetDateTime timestamp, String messageType, boolean hasAttachment) {
    }

    public record TicketHistory(TicketRow ticket, List<HistoryEntry> entries,
                                boolean hasMore, Long nextBeforeId) {
    }

    public record AttachmentReference(String storageKey, String originalName) {
    }
}
