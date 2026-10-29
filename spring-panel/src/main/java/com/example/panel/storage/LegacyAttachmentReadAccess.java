package com.example.panel.storage;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.security.PanelUserPrincipal;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Current bound-account READ for legacy HTTP downloads, after metadata proof and before binary open. */
@Service
public class LegacyAttachmentReadAccess {
    private final JdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private static final String CAN_READ = """
        SELECT EXISTS (
            SELECT 1 FROM tickets owner
            JOIN users reader ON reader.id = ? AND reader.username = ?
            CROSS JOIN LATERAL (SELECT lower(reader.username) AS user_identity) n
             WHERE owner.ticket_id = ? AND owner.is_general = FALSE
               AND reader.enabled = TRUE AND reader.is_blocked = FALSE
               AND (SELECT COUNT(*) FROM users duplicate
                     WHERE lower(duplicate.username) = n.user_identity) = 1
               AND (SELECT COUNT(*) FROM tickets candidate
                     WHERE candidate.ticket_id = owner.ticket_id) = 1
               AND ((owner.business_id IS NULL AND ? = FALSE)
                    OR (owner.business_id > 0 AND
        """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + """
                    ))
        )
        """;

    public LegacyAttachmentReadAccess(JdbcTemplate jdbc, MultibusinessFeatureFlags flags) {
        this.jdbc = jdbc;
        this.flags = flags;
    }

    public boolean canRead(Authentication authentication, String ticketId) {
        if (flags == null || !StringUtils.hasText(ticketId)
                || authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal reader)
                || reader.getUserId() <= 0 || reader.getUsername().isBlank()
                || !reader.isEnabled() || !reader.isAccountNonLocked()
                || !reader.isAccountNonExpired() || !reader.isCredentialsNonExpired()) {
            return false;
        }
        try {
            // SQL folding derives from this same ID/raw-name row, never from another account lookup.
            return Boolean.TRUE.equals(jdbc.queryForObject(CAN_READ, Boolean.class,
                    reader.getUserId(), reader.getUsername(), ticketId, flags.isStrictReadScope()));
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
