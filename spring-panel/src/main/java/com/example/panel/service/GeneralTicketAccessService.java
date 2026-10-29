package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Read-only GENERAL grant; neither a business membership nor a system administrator bypass. */
@Service
public class GeneralTicketAccessService {
    static final String CURRENT_ACCOUNT = """
        account.enabled = TRUE AND COALESCE(account.is_blocked, FALSE) = FALSE
        AND 1 = (SELECT COUNT(*) FROM users duplicate WHERE lower(duplicate.username) = lower(account.username))
        """;
    static final String READ_ACCOUNT = CURRENT_ACCOUNT + """
        AND EXISTS (SELECT 1 FROM general_ticket_read_grants access
                    WHERE access.user_id = account.id AND access.is_active = TRUE)
        """;
    static final String OPERATE_ACCOUNT = READ_ACCOUNT + """
        AND EXISTS (SELECT 1 FROM general_ticket_operate_grants operation
                    WHERE operation.user_id = account.id AND operation.is_active = TRUE)
        """;
    private final JdbcTemplate jdbc;

    public GeneralTicketAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long requireAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal account)
                || account.getUserId() <= 0 || account.getUsername().isBlank()
                || !account.isEnabled() || !account.isAccountNonLocked()
                || !account.isAccountNonExpired() || !account.isCredentialsNonExpired()) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        List<Long> ids = jdbc.query("SELECT account.id FROM users account WHERE account.id = ? AND account.username = ? AND "
                + CURRENT_ACCOUNT + " LIMIT 2",
            (rs, row) -> rs.getLong("id"), account.getUserId(), account.getUsername());
        if (ids.size() != 1) {
            throw new AccessDeniedException("Authenticated user is not available");
        }
        return ids.get(0);
    }

    public boolean canRead(Long userId) {
        return userId != null && userId > 0 && Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM users account WHERE account.id = ? AND " + READ_ACCOUNT + ")",
            Boolean.class, userId));
    }

    public boolean canOperate(Long userId) {
        return userId!=null&&userId>0&&Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM users account WHERE account.id=? AND "+OPERATE_ACCOUNT+")",Boolean.class,userId));
    }
    public void requireRead(Long userId) {
        if (!canRead(userId)) {
            throw new AccessDeniedException("Нет доступа к обращениям без бизнеса");
        }
    }

    public List<AccountAccess> accounts() {
        return jdbc.query("""
            SELECT account.id, account.username, COALESCE(access.is_active, FALSE) AS can_read,
                   COALESCE(access.is_active, FALSE) AND COALESCE(operation.is_active, FALSE) AS can_operate
              FROM users account LEFT JOIN general_ticket_read_grants access ON access.user_id = account.id
              LEFT JOIN general_ticket_operate_grants operation ON operation.user_id = account.id
             ORDER BY account.username, account.id
            """, (rs, row) -> new AccountAccess(rs.getLong("id"), rs.getString("username"), rs.getBoolean("can_read"), rs.getBoolean("can_operate")));
    }

    public boolean grantRead(long userId) {
        requirePositive(userId);
        return jdbc.update("""
            INSERT INTO general_ticket_read_grants(user_id, is_active)
            SELECT id, TRUE FROM users WHERE id = ?
            ON CONFLICT (user_id) DO UPDATE SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP
            """, userId) == 1;
    }

    public boolean revokeRead(long userId) {
        requirePositive(userId);
        return Integer.valueOf(1).equals(jdbc.queryForObject("""
            WITH revoked AS (
                UPDATE general_ticket_read_grants SET is_active=FALSE,updated_at=CURRENT_TIMESTAMP
                 WHERE user_id=? RETURNING user_id
            ), revoked_operation AS (
                UPDATE general_ticket_operate_grants SET is_active=FALSE,updated_at=CURRENT_TIMESTAMP
                 WHERE user_id IN (SELECT user_id FROM revoked) RETURNING user_id
            ) SELECT COUNT(*) FROM revoked
            """, Integer.class, userId));
    }

    public boolean grantOperate(long userId) {
        requirePositive(userId);
        return jdbc.update("WITH allowed AS (SELECT account.id FROM users account JOIN general_ticket_read_grants read_access "
            + "ON read_access.user_id=account.id WHERE account.id=? AND read_access.is_active=TRUE AND " + CURRENT_ACCOUNT
            + " FOR UPDATE OF read_access) INSERT INTO general_ticket_operate_grants(user_id,is_active) SELECT id,TRUE FROM allowed"
            + " ON CONFLICT(user_id) DO UPDATE SET is_active=TRUE,updated_at=CURRENT_TIMESTAMP",userId)==1;
    }
    public boolean revokeOperate(long userId) {
        requirePositive(userId);
        return jdbc.update("UPDATE general_ticket_operate_grants SET is_active=FALSE,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",userId)==1;
    }

    private void requirePositive(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("Invalid account ID");
    }

    public record AccountAccess(long userId, String username, boolean canRead, boolean canOperate) {
        public AccountAccess(long userId,String username,boolean canRead) { this(userId,username,canRead,false); }
    }
}
