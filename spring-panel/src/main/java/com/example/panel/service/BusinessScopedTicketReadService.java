package com.example.panel.service;

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
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid page bounds");
        }
        Set<Long> ids = accessService.requireSelectedBusiness(userId, context).businessIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        return namedJdbcTemplate.query("""
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id,
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
                 ORDER BY ticket.business_id, ticket.user_id, ticket.ticket_id
                 LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                        .addValue("businessIds", ids)
                        .addValue("userId", userId)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (rs, rowNum) -> new TicketRow(
                        rs.getLong("user_id"), rs.getString("ticket_id"),
                        rs.getString("ticket_public_id"), rs.getLong("business_id"),
                        rs.getString("business_code"), rs.getString("business_name")));
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
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id,
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
                        rs.getString("business_code"), rs.getString("business_name")));
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
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id,
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
                                    rs.getString("business_code"), rs.getString("business_name"));
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

    public record BusinessOption(long id, String code, String displayName) {
    }

    public record TicketRow(long userId, String ticketId, String ticketPublicId,
                            long businessId, String businessCode, String businessName) {
    }

    public record HistoryEntry(long id, String sender, String message,
                               OffsetDateTime timestamp, String messageType, boolean hasAttachment) {
    }

    public record TicketHistory(TicketRow ticket, List<HistoryEntry> entries,
                                boolean hasMore, Long nextBeforeId) {
    }
}
