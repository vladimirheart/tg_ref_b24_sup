package com.example.panel.service;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Read side for canonical business tickets; legacy display labels are never an owner. */
@Service
public class BusinessScopedTicketReadService {

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

    public record BusinessOption(long id, String code, String displayName) {
    }

    public record TicketRow(long userId, String ticketId, String ticketPublicId,
                            long businessId, String businessCode, String businessName) {
    }
}
