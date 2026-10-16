package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Frozen ticket owner plus current recipient READ; never cache access for an open SSE stream. */
@Service
public class UiEventTicketAccessService {

    private static final String CAN_READ_TICKET = """
        SELECT EXISTS (
            SELECT 1 FROM tickets owner
            CROSS JOIN (VALUES (CAST(? AS TEXT))) AS n(user_identity)
             WHERE owner.user_id = ? AND owner.ticket_id = ? AND owner.ticket_public_id = ?
               AND owner.business_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
               AND CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END = ?
               AND owner.is_general = FALSE
               AND (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = ?) = 1
               AND ((owner.business_id IS NULL AND ? = FALSE)
                    OR
        """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + """
               )
        )
        """;

    private final JdbcTemplate jdbcTemplate;
    private final MultibusinessFeatureFlags flags;

    public UiEventTicketAccessService(JdbcTemplate jdbcTemplate, MultibusinessFeatureFlags flags) {
        this.jdbcTemplate = jdbcTemplate;
        this.flags = flags;
    }

    public Optional<UiEventTicketScope> captureScope(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return Optional.empty();
        }
        try {
            var owners = jdbcTemplate.query("""
                SELECT owner.user_id, owner.ticket_id, owner.ticket_public_id, owner.business_id
                  FROM tickets owner
                 WHERE owner.ticket_id = ? AND owner.is_general = FALSE
                   AND (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = ?) = 1
                """, (rs, row) -> {
                    Long businessId = rs.getObject("business_id", Long.class);
                    return new UiEventTicketScope(rs.getObject("user_id", Long.class), rs.getString("ticket_id"),
                        rs.getString("ticket_public_id"), businessId == null ? "UNRESOLVED" : "BUSINESS", businessId);
                }, ticketId, ticketId);
            return owners.size() == 1 && owners.get(0).isValid() ? Optional.of(owners.get(0)) : Optional.empty();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean canReadTicket(String userIdentity, UiEventTicketScope scope) {
        if (!StringUtils.hasText(userIdentity) || scope == null || !scope.isValid()) {
            return false;
        }
        try {
            // The queued owner must still match; broad grants cannot authorize an obsolete payload.
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(CAN_READ_TICKET, Boolean.class,
                userIdentity.trim().toLowerCase(Locale.ROOT), scope.ticketUserId(), scope.ticketId(),
                scope.ticketPublicId(), scope.businessId(), scope.scopeKind(), scope.ticketId(), flags.isStrictReadScope()));
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
