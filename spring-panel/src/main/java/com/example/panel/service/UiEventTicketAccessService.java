package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Current recipient READ for legacy ticket events; never cache access for an open SSE stream. */
@Service
public class UiEventTicketAccessService {

    private static final String CAN_READ_TICKET = """
        SELECT EXISTS (
            SELECT 1 FROM tickets owner
            CROSS JOIN (VALUES (CAST(? AS TEXT))) AS n(user_identity)
             WHERE owner.ticket_id = ? AND owner.is_general = FALSE
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

    public boolean canReadTicket(String userIdentity, String ticketId) {
        if (!StringUtils.hasText(userIdentity) || !StringUtils.hasText(ticketId)) {
            return false;
        }
        try {
            // Identity, explicit marker, business and live grants share one SQL snapshot.
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(CAN_READ_TICKET, Boolean.class,
                userIdentity.trim().toLowerCase(Locale.ROOT), ticketId, ticketId, flags.isStrictReadScope()));
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
