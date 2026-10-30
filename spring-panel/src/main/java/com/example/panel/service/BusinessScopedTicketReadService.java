package com.example.panel.service;

import com.example.panel.storage.AttachmentStorageKeyResolver;
import com.example.panel.security.PanelUserPrincipal;
import java.sql.ResultSet;
import java.sql.Types;
import java.sql.SQLException;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Read side for canonical business tickets; legacy display labels are never an owner. */
@Service
public class BusinessScopedTicketReadService {

    public static final int MAX_EXPORT_TICKETS = 1000;
    public static final int MAX_CATEGORY_SUMMARY = 1000;
    public static final int MAX_TICKET_ANSWERS = 100;
    public static final int MAX_WORKSPACE_ITEMS = 100;
    public static final int MAX_TICKET_SPANS = 100;
    private static final Pattern PUBLIC_ID = Pattern.compile("[0-9a-f]{32}");
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final BusinessAccessService accessService;

    public BusinessScopedTicketReadService(JdbcTemplate jdbcTemplate, BusinessAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.accessService = accessService;
    }

    public Long requireAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal account)
                || account.getUserId() <= 0 || account.getUsername().isBlank()
                || !account.isEnabled() || !account.isAccountNonLocked()
                || !account.isAccountNonExpired() || !account.isCredentialsNonExpired()) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT account.id FROM users account
                 WHERE account.id = ? AND account.username = ?
                   AND account.enabled = TRUE AND account.is_blocked = FALSE
                   AND 1 = (SELECT COUNT(*) FROM users duplicate
                            WHERE lower(duplicate.username) = lower(account.username))
                """,
                (rs, rowNum) -> rs.getLong("id"), account.getUserId(), account.getUsername());
        if (ids.size() != 1) {
            throw new AccessDeniedException("Authenticated user is not available");
        }
        return ids.get(0);
    }

    public List<BusinessOption> availableBusinesses(Authentication authentication) {
        ReadActor actor = ReadActor.from(authentication);
        if (actor == null) return List.of();
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(actor.userId());
        if (ids.isEmpty()) {
            return List.of();
        }
        return namedJdbcTemplate.query("""
                SELECT business.id, business.code, business.display_name
                  FROM businesses business
                 WHERE business.id IN (:businessIds) AND business.status = 'active'
                   AND EXISTS (
                       SELECT 1 FROM users reader
                        WHERE reader.id = :userId AND reader.username = :readerUsername
                          AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                          AND 1 = (SELECT COUNT(*) FROM users duplicate
                                   WHERE lower(duplicate.username) = lower(reader.username))
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
                 ORDER BY business.id
                """, new MapSqlParameterSource()
                        .addValue("businessIds", ids).addValue("userId", actor.userId(), Types.BIGINT)
                        .addValue("readerUsername", actor.username(), Types.VARCHAR),
                (rs, rowNum) -> new BusinessOption(
                        rs.getLong("id"), rs.getString("code"), rs.getString("display_name")));
    }

    /** Counts only canonical tickets in currently authorized active businesses, including empty businesses. */
    public TicketSummary summarizeTickets(Authentication authentication,
                                          BusinessAccessService.RequestedBusinessContext context) {
        ReadActor actor = ReadActor.from(authentication);
        if (actor == null) return new TicketSummary(0, 0, 0, 0, 0, 0, List.of());
        Set<Long> ids = accessService.requireSelectedBusiness(actor.userId(), context).businessIds();
        if (ids.isEmpty()) {
            return new TicketSummary(0, 0, 0, 0, 0, 0, List.of());
        }
        List<BusinessTicketCounts> businesses = namedJdbcTemplate.query("""
                SELECT business.id, business.code, business.display_name,
                       COUNT(ticket.user_id) AS total,
                       COUNT(ticket.user_id) FILTER (WHERE ticket.status = 'pending') AS pending,
                       COUNT(ticket.user_id) FILTER (WHERE ticket.status = 'open') AS open,
                       COUNT(ticket.user_id) FILTER (WHERE ticket.status = 'resolved') AS resolved,
                       COUNT(ticket.user_id) FILTER (WHERE ticket.status = 'closed') AS closed
                  FROM businesses business
                  LEFT JOIN tickets ticket ON ticket.business_id = business.id
                 WHERE business.id IN (:businessIds) AND business.status = 'active'
                   AND EXISTS (
                       SELECT 1 FROM users reader
                        WHERE reader.id = :userId AND reader.username = :readerUsername
                          AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                          AND 1 = (SELECT COUNT(*) FROM users duplicate
                                   WHERE lower(duplicate.username) = lower(reader.username))
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
                 GROUP BY business.id, business.code, business.display_name
                 ORDER BY business.id
                """, new MapSqlParameterSource().addValue("businessIds", ids).addValue("userId", actor.userId(), Types.BIGINT)
                        .addValue("readerUsername", actor.username(), Types.VARCHAR),
                (rs, rowNum) -> {
                    long total = rs.getLong("total");
                    long pending = rs.getLong("pending");
                    long open = rs.getLong("open");
                    long resolved = rs.getLong("resolved");
                    long closed = rs.getLong("closed");
                    return new BusinessTicketCounts(rs.getLong("id"), rs.getString("code"),
                            rs.getString("display_name"), total, pending, open, resolved, closed,
                            total - pending - open - resolved - closed);
                });
        long total = 0, pending = 0, open = 0, resolved = 0, closed = 0, other = 0;
        for (BusinessTicketCounts business : businesses) {
            total += business.total();
            pending += business.pending();
            open += business.open();
            resolved += business.resolved();
            closed += business.closed();
            other += business.other();
        }
        return new TicketSummary(total, pending, open, resolved, closed, other, businesses);
    }

    /** One count per canonical owner/category; never joins legacy ticket_categories. */
    public List<CategoryCount> summarizeCategories(Authentication authentication,
            BusinessAccessService.RequestedBusinessContext context) {
        ReadActor actor = ReadActor.from(authentication);
        if (actor == null) return List.of();
        Set<Long> ids = accessService.requireSelectedBusiness(actor.userId(), context).businessIds();
        if (ids.isEmpty()) return List.of();
        List<CategoryCount> result = namedJdbcTemplate.query("""
                SELECT business.id AS business_id, business.code AS business_code,
                       business.display_name AS business_name, category.value AS category,
                       COUNT(DISTINCT (ticket.user_id, ticket.ticket_id)) AS total
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                  CROSS JOIN LATERAL jsonb_array_elements_text(ticket.business_categories) category(value)
                 WHERE ticket.business_id IN (:businessIds) AND business.status = 'active'
                   AND EXISTS (
                       SELECT 1 FROM users reader
                        WHERE reader.id = :userId AND reader.username = :readerUsername
                          AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                          AND 1 = (SELECT COUNT(*) FROM users duplicate
                                   WHERE lower(duplicate.username) = lower(reader.username))
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
                 GROUP BY business.id, business.code, business.display_name, category.value
                 ORDER BY business.id, category.value COLLATE "C"
                 LIMIT :summaryLimit
                """, new MapSqlParameterSource().addValue("businessIds", ids).addValue("userId", actor.userId(), Types.BIGINT)
                        .addValue("readerUsername", actor.username(), Types.VARCHAR)
                        .addValue("summaryLimit", MAX_CATEGORY_SUMMARY + 1),
                (rs, rowNum) -> new CategoryCount(rs.getLong("business_id"), rs.getString("business_code"),
                        rs.getString("business_name"), rs.getString("category"), rs.getLong("total")));
        if (result.size() > MAX_CATEGORY_SUMMARY) throw new CategorySummaryTooLargeException();
        return result;
    }

    public List<TicketRow> listTickets(Authentication authentication,
                                       BusinessAccessService.RequestedBusinessContext context,
                                       int limit,
                                       int offset) {
        return listTickets(authentication, context, limit, offset, null, null);
    }

    public List<TicketRow> listTickets(Authentication authentication,
                                       BusinessAccessService.RequestedBusinessContext context,
                                       int limit, int offset, String status, String ticketId) {
        return listTickets(authentication, context, limit, offset, status, ticketId, null);
    }

    public List<TicketRow> listTickets(Authentication authentication,
                                       BusinessAccessService.RequestedBusinessContext context,
                                       int limit, int offset, String status, String ticketId, String query) {
        return listTickets(authentication, context, limit, offset, status, ticketId, query, null);
    }

    public List<TicketRow> listTickets(Authentication authentication,
            BusinessAccessService.RequestedBusinessContext context,
            int limit, int offset, String status, String ticketId, String query, String category) {
        validateTicketPage(limit, offset, status, ticketId, query);
        validateCategory(category);
        ReadActor actor = ReadActor.from(authentication);
        if (actor == null) return List.of();
        Set<Long> ids = accessService.requireSelectedBusiness(actor.userId(), context).businessIds();
        return queryTickets(actor, ids, limit, offset, status, ticketId, query, category);
    }

    public TicketPage pageTickets(Authentication authentication,
                                  BusinessAccessService.RequestedBusinessContext context,
                                  int limit, int offset, String status, String ticketId) {
        return pageTickets(authentication, context, limit, offset, status, ticketId, null);
    }

    public TicketPage pageTickets(Authentication authentication,
                                  BusinessAccessService.RequestedBusinessContext context,
                                  int limit, int offset, String status, String ticketId, String query) {
        return pageTickets(authentication, context, limit, offset, status, ticketId, query, null);
    }

    public TicketPage pageTickets(Authentication authentication,
            BusinessAccessService.RequestedBusinessContext context,
            int limit, int offset, String status, String ticketId, String query, String category) {
        validateTicketPage(limit, offset, status, ticketId, query);
        validateCategory(category);
        ReadActor actor = ReadActor.from(authentication);
        if (actor == null) return new TicketPage(List.of(), false);
        Set<Long> ids = accessService.requireSelectedBusiness(actor.userId(), context).businessIds();
        List<TicketRow> rows = queryTickets(actor, ids, limit + 1, offset, status, ticketId, query, category);
        boolean hasMore = rows.size() > limit;
        return new TicketPage(hasMore ? List.copyOf(rows.subList(0, limit)) : rows, hasMore);
    }

    /** Exports a complete filtered snapshot or refuses it; never returns a silently truncated file. */
    public List<TicketRow> exportTickets(Authentication authentication,
                                         BusinessAccessService.RequestedBusinessContext context,
                                         String status, String ticketId, String query) {
        return exportTickets(authentication, context, status, ticketId, query, null);
    }

    public List<TicketRow> exportTickets(Authentication authentication,
            BusinessAccessService.RequestedBusinessContext context,
            String status, String ticketId, String query, String category) {
        validateTicketFilters(status, ticketId, query);
        validateCategory(category);
        ReadActor actor = ReadActor.from(authentication);
        if (actor == null) return List.of();
        Set<Long> ids = accessService.requireSelectedBusiness(actor.userId(), context).businessIds();
        List<TicketRow> rows = queryTickets(actor, ids, MAX_EXPORT_TICKETS + 1, 0,
                status, ticketId, query, category);
        if (rows.size() > MAX_EXPORT_TICKETS) {
            throw new ExportTooLargeException();
        }
        return rows;
    }

    private void validateTicketPage(int limit, int offset, String status, String ticketId, String query) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid page bounds");
        }
        validateTicketFilters(status, ticketId, query);
    }

    private void validateTicketFilters(String status, String ticketId, String query) {
        if (status != null && !Set.of("pending", "open", "resolved", "closed").contains(status)) {
            throw new IllegalArgumentException("Invalid ticket status");
        }
        if (ticketId != null && (ticketId.isBlank() || ticketId.length() > 255
                || ticketId.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Invalid ticket ID");
        }
        if (query != null && (query.isBlank() || query.length() > 100
                || query.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Invalid ticket search");
        }
    }

    public static void validateCategory(String category) {
        if (category != null && (category.isBlank() || category.codePointCount(0, category.length()) > 160
                || category.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Invalid ticket category");
        }
    }

    private List<TicketRow> queryTickets(ReadActor actor, Set<Long> ids,
            int limit, int offset, String status, String ticketId, String query, String category) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                       ticket.created_at, ticket.resolved_at,
                       ARRAY(SELECT jsonb_array_elements_text(ticket.business_categories)) AS canonical_categories,
                       left(ticket.resolved_by, 200) AS resolved_by,
                       business.id AS business_id, business.code AS business_code,
                       business.display_name AS business_name,
                       left(root.client_name, 200) AS client_name,
                       left(root.problem, 500) AS problem_preview
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                  LEFT JOIN messages root ON root.group_msg_id = ticket.group_msg_id
                       AND root.user_id = ticket.user_id AND root.ticket_id = ticket.ticket_id
                 WHERE ticket.business_id IN (:businessIds)
                   AND business.status = 'active'
                   AND EXISTS (
                       SELECT 1 FROM users reader
                        WHERE reader.id = :userId AND reader.username = :readerUsername
                          AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                          AND 1 = (SELECT COUNT(*) FROM users duplicate
                                   WHERE lower(duplicate.username) = lower(reader.username))
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
                """
                + (status == null ? "" : " AND ticket.status = :status\n")
                + (ticketId == null ? "" : " AND (ticket.ticket_id = :ticketId OR ticket.ticket_public_id = :ticketId)\n")
                + (category == null ? "" : " AND ticket.business_categories @> jsonb_build_array(CAST(:category AS TEXT))\n")
                + (query == null ? "" : """
                   AND (strpos(lower(coalesce(root.client_name, '')), lower(:query)) > 0
                        OR strpos(lower(coalesce(root.problem, '')), lower(:query)) > 0
                         OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(ticket.business_categories) c(value)
                                     WHERE strpos(lower(c.value), lower(:query)) > 0))
                   """)
                + """
                 ORDER BY ticket.created_at DESC, ticket.business_id, ticket.user_id, ticket.ticket_id
                 LIMIT :limit OFFSET :offset
                """;
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("businessIds", ids)
                        .addValue("userId", actor.userId(), Types.BIGINT)
                        .addValue("readerUsername", actor.username(), Types.VARCHAR)
                        .addValue("status", status)
                        .addValue("ticketId", ticketId)
                        .addValue("query", query == null ? null : query.strip())
                        .addValue("category", category)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (rs, rowNum) -> ticketRow(rs));
    }

    public Optional<TicketRow> findByPublicId(Authentication authentication, String publicId) {
        return findByPublicId(ReadActor.from(authentication), publicId);
    }

    private Optional<TicketRow> findByPublicId(ReadActor actor, String publicId) {
        if (actor == null || publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(actor.userId());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<TicketRow> rows = namedJdbcTemplate.query("""
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                       ticket.created_at, ticket.resolved_at,
                       ARRAY(SELECT jsonb_array_elements_text(ticket.business_categories)) AS canonical_categories,
                       left(ticket.resolved_by, 200) AS resolved_by,
                       business.id AS business_id, business.code AS business_code,
                       business.display_name AS business_name,
                       left(root.client_name, 200) AS client_name,
                       left(root.problem, 500) AS problem_preview
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                  LEFT JOIN messages root ON root.group_msg_id = ticket.group_msg_id
                       AND root.user_id = ticket.user_id AND root.ticket_id = ticket.ticket_id
                 WHERE ticket.ticket_public_id = :publicId
                   AND EXISTS (SELECT 1 FROM users reader
                       WHERE reader.id = :userId AND reader.username = :readerUsername
                         AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                         AND 1 = (SELECT COUNT(*) FROM users duplicate
                                  WHERE lower(duplicate.username) = lower(reader.username)))
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
                """, actor.bind(new MapSqlParameterSource())
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", actor.userId(), Types.BIGINT),
                (rs, rowNum) -> ticketRow(rs));
        return rows.stream().findFirst();
    }

    /**
     * Only history stamped with the exact canonical ticket tuple is readable.
     * Legacy rows with a bare ticket_id remain unclassified, even if that ID is unique today.
     */
    public Optional<TicketHistory> findHistoryByPublicId(Authentication authentication, String publicId,
                                                          Long beforeId, int limit) {
        return findHistoryByPublicId(ReadActor.from(authentication), publicId, beforeId, limit);
    }

    private Optional<TicketHistory> findHistoryByPublicId(ReadActor actor, String publicId,
                                                          Long beforeId, int limit) {
        if (limit < 1 || limit > 100 || (beforeId != null && beforeId < 1)) {
            throw new IllegalArgumentException("Invalid history page bounds");
        }
        if (actor == null || publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(actor.userId());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return namedJdbcTemplate.query("""
                SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                       ticket.created_at, ticket.resolved_at,
                       ARRAY(SELECT jsonb_array_elements_text(ticket.business_categories)) AS canonical_categories,
                       left(ticket.resolved_by, 200) AS resolved_by,
                       business.id AS business_id, business.code AS business_code,
                       business.display_name AS business_name,
                       left(root.client_name, 200) AS client_name,
                       left(root.problem, 500) AS problem_preview,
                       history.id AS history_id, history.sender, history.message,
                       history.timestamp AS history_timestamp, history.message_type,
                       history.attachment
                  FROM tickets ticket
                  JOIN businesses business ON business.id = ticket.business_id
                  LEFT JOIN messages root ON root.group_msg_id = ticket.group_msg_id
                       AND root.user_id = ticket.user_id AND root.ticket_id = ticket.ticket_id
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
                   AND EXISTS (SELECT 1 FROM users reader
                       WHERE reader.id = :userId AND reader.username = :readerUsername
                         AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                         AND 1 = (SELECT COUNT(*) FROM users duplicate
                                  WHERE lower(duplicate.username) = lower(reader.username)))
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
                """, actor.bind(new MapSqlParameterSource())
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", actor.userId(), Types.BIGINT)
                        .addValue("beforeId", beforeId == null ? Long.MAX_VALUE : beforeId)
                        .addValue("pageSize", limit + 1),
                rs -> {
                    TicketRow ticket = null;
                    List<HistoryEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        if (ticket == null) {
                            ticket = ticketRow(rs);
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

    /** Legacy attributes have only a raw ticket ID; expose them only when that ID has one owner. */
    public Optional<TicketAnswers> findAnswersByPublicId(Authentication authentication, String publicId) {
        return findAnswersByPublicId(ReadActor.from(authentication), publicId);
    }

    private Optional<TicketAnswers> findAnswersByPublicId(ReadActor actor, String publicId) {
        if (actor == null || publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(actor.userId());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return namedJdbcTemplate.query("""
                WITH authorized_ticket AS (
                    SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                           ticket.created_at, ticket.resolved_at,
                       ARRAY(SELECT jsonb_array_elements_text(ticket.business_categories)) AS canonical_categories,
                           left(ticket.resolved_by, 200) AS resolved_by,
                           business.id AS business_id, business.code AS business_code,
                           business.display_name AS business_name,
                           left(root.client_name, 200) AS client_name,
                           left(root.problem, 500) AS problem_preview,
                           (SELECT COUNT(*) FROM tickets same_id
                             WHERE same_id.ticket_id = ticket.ticket_id) = 1 AS relation_verified
                      FROM tickets ticket
                      JOIN businesses business ON business.id = ticket.business_id
                      LEFT JOIN messages root ON root.group_msg_id = ticket.group_msg_id
                           AND root.user_id = ticket.user_id AND root.ticket_id = ticket.ticket_id
                     WHERE ticket.ticket_public_id = :publicId
                   AND EXISTS (SELECT 1 FROM users reader
                       WHERE reader.id = :userId AND reader.username = :readerUsername
                         AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                         AND 1 = (SELECT COUNT(*) FROM users duplicate
                                  WHERE lower(duplicate.username) = lower(reader.username)))
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
                )
                SELECT ticket.*, answer.question_id AS answer_question_id,
                       answer.question_text, answer.input_type,
                       answer.value_label, answer.value_text
                  FROM authorized_ticket ticket
                  LEFT JOIN LATERAL (
                      SELECT attribute.question_id, attribute.question_text,
                             attribute.input_type, attribute.value_label, attribute.value_text
                        FROM ticket_attributes attribute
                       WHERE attribute.ticket_id = ticket.ticket_id
                         AND ticket.relation_verified
                       ORDER BY attribute.question_id
                       LIMIT :answerLimit
                  ) answer ON TRUE
                 ORDER BY answer.question_id NULLS LAST
                """, actor.bind(new MapSqlParameterSource())
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", actor.userId(), Types.BIGINT)
                        .addValue("answerLimit", MAX_TICKET_ANSWERS + 1),
                rs -> {
                    TicketRow ticket = null;
                    boolean relationVerified = false;
                    List<AnswerEntry> answers = new ArrayList<>();
                    while (rs.next()) {
                        if (ticket == null) {
                            ticket = ticketRow(rs);
                            relationVerified = rs.getBoolean("relation_verified");
                        }
                        if (rs.getString("answer_question_id") != null) {
                            answers.add(new AnswerEntry(rs.getString("answer_question_id"),
                                    rs.getString("question_text"), rs.getString("input_type"),
                                    rs.getString("value_label"), rs.getString("value_text")));
                        }
                    }
                    if (ticket == null) {
                        return Optional.<TicketAnswers>empty();
                    }
                    if (answers.size() > MAX_TICKET_ANSWERS) {
                        throw new AnswerLimitExceededException();
                    }
                    return Optional.of(new TicketAnswers(ticket, relationVerified, List.copyOf(answers)));
                });
    }

    /** Canonical categories and legacy responsible only; roster data has its own full-owner API. */
    public Optional<TicketWorkspace> findWorkspaceByPublicId(Authentication authentication, String publicId) {
        return findWorkspaceByPublicId(ReadActor.from(authentication), publicId);
    }

    private Optional<TicketWorkspace> findWorkspaceByPublicId(ReadActor actor, String publicId) {
        if (actor == null || publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(actor.userId());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return namedJdbcTemplate.query("""
                WITH authorized_ticket AS (
                    SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                           ticket.created_at, ticket.resolved_at,
                       ARRAY(SELECT jsonb_array_elements_text(ticket.business_categories)) AS canonical_categories,
                           left(ticket.resolved_by, 200) AS resolved_by,
                           business.id AS business_id, business.code AS business_code,
                           business.display_name AS business_name,
                           left(root.client_name, 200) AS client_name,
                           left(root.problem, 500) AS problem_preview,
                           (SELECT COUNT(*) FROM tickets same_id
                             WHERE same_id.ticket_id = ticket.ticket_id) = 1 AS relation_verified
                      FROM tickets ticket
                      JOIN businesses business ON business.id = ticket.business_id
                      LEFT JOIN messages root ON root.group_msg_id = ticket.group_msg_id
                           AND root.user_id = ticket.user_id AND root.ticket_id = ticket.ticket_id
                     WHERE ticket.ticket_public_id = :publicId
                   AND EXISTS (SELECT 1 FROM users reader
                       WHERE reader.id = :userId AND reader.username = :readerUsername
                         AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                         AND 1 = (SELECT COUNT(*) FROM users duplicate
                                  WHERE lower(duplicate.username) = lower(reader.username)))
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
                )
                SELECT ticket.*, item.entry_kind, item.entry_value
                  FROM authorized_ticket ticket
                  LEFT JOIN LATERAL (
                      SELECT 'responsible' AS entry_kind, left(r.responsible, 200) AS entry_value
                        FROM ticket_responsibles r
                       WHERE r.ticket_id = ticket.ticket_id AND ticket.relation_verified
                  ) item ON TRUE
                 ORDER BY item.entry_kind, item.entry_value
                """, actor.bind(new MapSqlParameterSource())
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", actor.userId(), Types.BIGINT),
                rs -> {
                    TicketRow ticket = null;
                    boolean relationVerified = false;
                    String responsible = null;
                    while (rs.next()) {
                        if (ticket == null) {
                            ticket = ticketRow(rs);
                            relationVerified = rs.getBoolean("relation_verified");
                        }
                        String kind = rs.getString("entry_kind");
                        if ("responsible".equals(kind)) {
                            responsible = rs.getString("entry_value");
                        }
                    }
                    if (ticket == null) {
                        return Optional.<TicketWorkspace>empty();
                    }
                    return Optional.of(new TicketWorkspace(ticket, relationVerified, responsible,
                            ticket.categories(), List.of()));
                });
    }

    /** Legacy work intervals use a bare ticket ID and are readable only for its unique owner. */
    public Optional<TicketSpans> findSpansByPublicId(Authentication authentication, String publicId) {
        return findSpansByPublicId(ReadActor.from(authentication), publicId);
    }

    private Optional<TicketSpans> findSpansByPublicId(ReadActor actor, String publicId) {
        if (actor == null || publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(actor.userId());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return namedJdbcTemplate.query("""
                WITH authorized_ticket AS (
                    SELECT ticket.user_id, ticket.ticket_id, ticket.ticket_public_id, ticket.status,
                           ticket.created_at, ticket.resolved_at,
                       ARRAY(SELECT jsonb_array_elements_text(ticket.business_categories)) AS canonical_categories,
                           left(ticket.resolved_by, 200) AS resolved_by,
                           business.id AS business_id, business.code AS business_code,
                           business.display_name AS business_name,
                           left(root.client_name, 200) AS client_name,
                           left(root.problem, 500) AS problem_preview,
                           (SELECT COUNT(*) FROM tickets same_id
                             WHERE same_id.ticket_id = ticket.ticket_id) = 1 AS relation_verified
                      FROM tickets ticket
                      JOIN businesses business ON business.id = ticket.business_id
                      LEFT JOIN messages root ON root.group_msg_id = ticket.group_msg_id
                           AND root.user_id = ticket.user_id AND root.ticket_id = ticket.ticket_id
                     WHERE ticket.ticket_public_id = :publicId
                   AND EXISTS (SELECT 1 FROM users reader
                       WHERE reader.id = :userId AND reader.username = :readerUsername
                         AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                         AND 1 = (SELECT COUNT(*) FROM users duplicate
                                  WHERE lower(duplicate.username) = lower(reader.username)))
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
                )
                SELECT ticket.*, span.id AS span_id, span.span_no, span.started_at,
                       span.ended_at, span.duration_seconds
                  FROM authorized_ticket ticket
                  LEFT JOIN LATERAL (
                      SELECT work_span.id, work_span.span_no, work_span.started_at,
                             work_span.ended_at, work_span.duration_seconds
                        FROM ticket_spans work_span
                       WHERE work_span.ticket_id = ticket.ticket_id
                         AND ticket.relation_verified
                       ORDER BY work_span.span_no, work_span.id
                       LIMIT :spanLimit
                  ) span ON TRUE
                 ORDER BY span.span_no NULLS LAST, span.id NULLS LAST
                """, actor.bind(new MapSqlParameterSource())
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", actor.userId(), Types.BIGINT)
                        .addValue("spanLimit", MAX_TICKET_SPANS + 1),
                rs -> {
                    TicketRow ticket = null;
                    boolean relationVerified = false;
                    List<SpanEntry> spans = new ArrayList<>();
                    while (rs.next()) {
                        if (ticket == null) {
                            ticket = ticketRow(rs);
                            relationVerified = rs.getBoolean("relation_verified");
                        }
                        if (rs.getObject("span_id") != null) {
                            spans.add(new SpanEntry(rs.getInt("span_no"),
                                    rs.getObject("started_at", OffsetDateTime.class),
                                    rs.getObject("ended_at", OffsetDateTime.class),
                                    rs.getObject("duration_seconds", Integer.class)));
                        }
                    }
                    if (ticket == null) {
                        return Optional.<TicketSpans>empty();
                    }
                    if (spans.size() > MAX_TICKET_SPANS) {
                        throw new SpanLimitExceededException();
                    }
                    return Optional.of(new TicketSpans(ticket, relationVerified, List.copyOf(spans)));
                });
    }

    /** A binary locator is issued only for one explicit history owner and unambiguous storage key. */
    public Optional<AttachmentReference> findAttachmentByPublicId(Authentication authentication, String publicId, long historyId) {
        return findAttachmentByPublicId(ReadActor.from(authentication), publicId, historyId);
    }

    private Optional<AttachmentReference> findAttachmentByPublicId(ReadActor actor, String publicId, long historyId) {
        if (actor == null || historyId < 1 || publicId == null || !PUBLIC_ID.matcher(publicId).matches()) {
            return Optional.empty();
        }
        Set<Long> ids = accessService.resolveAccessibleBusinessIds(actor.userId());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return attachmentRows(actor,publicId,historyId,ids,true)
            .or(()->attachmentRows(actor,publicId,historyId,ids,false));
    }

    private Optional<AttachmentReference> attachmentRows(ReadActor actor,String publicId,long historyId,Set<Long> ids,boolean withProof) {
        try {
        List<AttachmentReference> rows = namedJdbcTemplate.query("""
                SELECT ticket.ticket_id, history.attachment, metadata.storage_key,
                       metadata.original_name%s
                  FROM tickets ticket%s
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
                   AND EXISTS (SELECT 1 FROM users reader
                       WHERE reader.id = :userId AND reader.username = :readerUsername
                         AND reader.enabled = TRUE AND reader.is_blocked = FALSE
                         AND 1 = (SELECT COUNT(*) FROM users duplicate
                                  WHERE lower(duplicate.username) = lower(reader.username)))
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
                """.formatted(withProof?", metadata.content_hash, metadata.size,\n                       ticket.user_id AS proof_user, ticket.business_id AS proof_business,\n                       ticket.channel_id AS proof_channel, ticket.questionnaire_catalog_sha256 AS proof_catalog,\n                       COALESCE(ticket.business_selection_source,ticket.general_selection_source) AS proof_source,\n                       proof_channel.platform AS proof_platform, history.channel_id AS proof_history_channel,\n                       metadata.channel_id AS proof_metadata_channel, history.tg_message_id AS proof_provider":"",withProof?" LEFT JOIN channels proof_channel ON proof_channel.id=ticket.channel_id":""), actor.bind(new MapSqlParameterSource())
                        .addValue("historyId", historyId)
                        .addValue("publicId", publicId)
                        .addValue("businessIds", ids)
                        .addValue("userId", actor.userId(), Types.BIGINT),
                (rs, rowNum) -> {
                    String ticketId = rs.getString("ticket_id");
                    String rawReference = rs.getString("attachment");
                    String storageKey = rs.getString("storage_key");
                    if(com.example.panel.storage.QuestionnaireMediaStorageProof.isScoped(storageKey)) {
                        if(!withProof || !java.util.Objects.equals(rawReference,storageKey) || !com.example.panel.storage.QuestionnaireMediaStorageProof.matches(rs,storageKey)) return null;
                        return new AttachmentReference(storageKey,rs.getString("original_name"),rs.getString("content_hash"),(Long)rs.getObject("size"));
                    }
                    if(withProof) return null;
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
        } catch(org.springframework.dao.DataAccessException ex) {
            if(withProof) return Optional.empty(); // A legacy schema cannot authorize the new namespace.
            throw ex;
        }
    }

    /** Immutable principal identity. Each child SELECT checks current account and business rights. */
    private record ReadActor(long userId,String username) {
        static ReadActor from(Authentication authentication) {
            if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken
                    ||!(authentication.getPrincipal() instanceof PanelUserPrincipal account)
                    ||account.getUserId()<=0||account.getUsername()==null||account.getUsername().isBlank()
                    ||!account.isEnabled()||!account.isAccountNonLocked()||!account.isAccountNonExpired()||!account.isCredentialsNonExpired())return null;
            return new ReadActor(account.getUserId(),account.getUsername());
        }
        MapSqlParameterSource bind(MapSqlParameterSource parameters){return parameters.addValue("readerUsername",username,Types.VARCHAR);}
        @Override public String toString(){return "BusinessReadActor";}
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

    private TicketRow ticketRow(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        boolean resolved = "resolved".equalsIgnoreCase(status) || "closed".equalsIgnoreCase(status);
        return new TicketRow(rs.getLong("user_id"), rs.getString("ticket_id"),
                rs.getString("ticket_public_id"), rs.getLong("business_id"),
                rs.getString("business_code"), rs.getString("business_name"),
                status, rs.getObject("created_at", OffsetDateTime.class),
                resolved ? rs.getObject("resolved_at", OffsetDateTime.class) : null,
                resolved ? rs.getString("resolved_by") : null,
                rs.getString("client_name"), rs.getString("problem_preview"), canonicalCategories(rs));
    }

    private List<String> canonicalCategories(ResultSet rs) throws SQLException {
        java.sql.Array values = rs.getArray("canonical_categories");
        try {
            return List.of((String[]) values.getArray());
        } finally {
            values.free();
        }
    }

    public record CategoryCount(long businessId, String businessCode, String businessName,
                                String category, long total) {
    }

    public static class CategorySummaryTooLargeException extends RuntimeException {
        public CategorySummaryTooLargeException() {
            super("Category summary exceeds " + MAX_CATEGORY_SUMMARY + " rows; select a business");
        }
    }

    public record BusinessOption(long id, String code, String displayName) {
    }

    public record BusinessTicketCounts(long businessId, String businessCode, String businessName,
                                       long total, long pending, long open, long resolved,
                                       long closed, long other) {
    }

    public record TicketSummary(long total, long pending, long open, long resolved,
                                long closed, long other, List<BusinessTicketCounts> businesses) {
    }

    public record TicketRow(long userId, String ticketId, String ticketPublicId,
                            long businessId, String businessCode, String businessName,
                            String status, OffsetDateTime createdAt,
                            OffsetDateTime resolvedAt, String resolvedBy,
                            String clientName, String problemPreview, List<String> categories) {
        public TicketRow {
            categories = List.copyOf(categories);
        }

        public TicketRow(long userId, String ticketId, String ticketPublicId,
                         long businessId, String businessCode, String businessName,
                         String status, OffsetDateTime createdAt, OffsetDateTime resolvedAt, String resolvedBy,
                         String clientName, String problemPreview) {
            this(userId, ticketId, ticketPublicId, businessId, businessCode, businessName,
                    status, createdAt, resolvedAt, resolvedBy, clientName, problemPreview, List.of());
        }

        public TicketRow(long userId, String ticketId, String ticketPublicId,
                         long businessId, String businessCode, String businessName,
                         String status, OffsetDateTime createdAt,
                         String clientName, String problemPreview) {
            this(userId, ticketId, ticketPublicId, businessId, businessCode, businessName,
                    status, createdAt, null, null, clientName, problemPreview);
        }

        public TicketRow(long userId, String ticketId, String ticketPublicId,
                         long businessId, String businessCode, String businessName,
                         String status, String clientName, String problemPreview) {
            this(userId, ticketId, ticketPublicId, businessId, businessCode, businessName,
                    status, null, null, null, clientName, problemPreview);
        }

        public TicketRow(long userId, String ticketId, String ticketPublicId,
                         long businessId, String businessCode, String businessName, String status) {
            this(userId, ticketId, ticketPublicId, businessId, businessCode, businessName,
                    status, null, null, null, null, null);
        }
    }

    public record TicketPage(List<TicketRow> tickets, boolean hasMore) {
    }

    public static class ExportTooLargeException extends RuntimeException {
        public ExportTooLargeException() {
            super("Ticket export exceeds " + MAX_EXPORT_TICKETS + " rows; narrow the filters");
        }
    }

    public static class AnswerLimitExceededException extends RuntimeException {
        public AnswerLimitExceededException() {
            super("Ticket answers exceed " + MAX_TICKET_ANSWERS + " rows");
        }
    }

    public static class WorkspaceLimitExceededException extends RuntimeException {
        public WorkspaceLimitExceededException() {
            super("Ticket workspace exceeds " + MAX_WORKSPACE_ITEMS + " items per collection");
        }
    }

    public static class SpanLimitExceededException extends RuntimeException {
        public SpanLimitExceededException() {
            super("Ticket work intervals exceed " + MAX_TICKET_SPANS + " rows");
        }
    }

    public record AnswerEntry(String questionId, String questionText, String inputType,
                              String valueLabel, String valueText) {
    }

    public record TicketAnswers(TicketRow ticket, boolean relationVerified, List<AnswerEntry> answers) {
    }

    public record TicketWorkspace(TicketRow ticket, boolean relationVerified,
                                  String responsible, List<String> categories, List<String> participants) {
    }

    public record SpanEntry(int spanNo, OffsetDateTime startedAt, OffsetDateTime endedAt,
                            Integer durationSeconds) {
    }

    public record TicketSpans(TicketRow ticket, boolean relationVerified, List<SpanEntry> spans) {
    }

    public record HistoryEntry(long id, String sender, String message,
                               OffsetDateTime timestamp, String messageType, boolean hasAttachment) {
    }

    public record TicketHistory(TicketRow ticket, List<HistoryEntry> entries,
                                boolean hasMore, Long nextBeforeId) {
    }

    public record AttachmentReference(String storageKey, String originalName,String sha256,Long size) {
        public AttachmentReference(String storageKey,String originalName){this(storageKey,originalName,null,null);}
    }
}
