package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import java.util.List;
import com.example.panel.security.PanelRolePermissionSnapshot;
import java.sql.Types;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private static final String ADMINISTRATOR = """
        WITH account_actor AS MATERIALIZED (
            SELECT account.id,account.role_id FROM users account
            WHERE account.id=:authenticatedActorId AND account.username=:identity AND
            """+CURRENT_ACCOUNT+"""
            FOR SHARE OF account
        ), assigned_role AS MATERIALIZED (
            SELECT assigned.id FROM roles assigned JOIN account_actor actor ON actor.role_id=assigned.id
            WHERE assigned.id=:assignedRoleId AND assigned.name IS NOT DISTINCT FROM :assignedRoleName
              AND assigned.permissions IS NOT DISTINCT FROM :assignedPermissions
            FOR SHARE OF assigned
        ), direct_admin AS MATERIALIZED (
            SELECT authority.user_id FROM user_authorities authority JOIN account_actor actor ON actor.id=authority.user_id
            WHERE authority.authority='ROLE_ADMIN' FOR SHARE OF authority
        ), direct_settings AS MATERIALIZED (
            SELECT authority.user_id FROM user_authorities authority JOIN account_actor actor ON actor.id=authority.user_id
            WHERE authority.authority='PAGE_SETTINGS' FOR SHARE OF authority
        ), administrator AS MATERIALIZED (
            SELECT actor.id FROM account_actor actor
            WHERE (EXISTS(SELECT 1 FROM direct_admin) OR (:assignedAdmin AND EXISTS(SELECT 1 FROM assigned_role)))
              AND (EXISTS(SELECT 1 FROM direct_settings) OR (:assignedSettings AND EXISTS(SELECT 1 FROM assigned_role)))
        )
        """;
    private final NamedParameterJdbcTemplate adminSql;
    private final JdbcTemplate jdbc;

    public GeneralTicketAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.adminSql = new NamedParameterJdbcTemplate(jdbc);
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

    public List<AccountAccess> accounts(Authentication authentication) {
        var accounts=adminSql.query(ADMINISTRATOR+"""
            SELECT account.id,account.username,COALESCE(access.is_active,FALSE) AS can_read,
                   COALESCE(access.is_active,FALSE) AND COALESCE(operation.is_active,FALSE) AS can_operate
              FROM administrator CROSS JOIN users account
              LEFT JOIN general_ticket_read_grants access ON access.user_id=account.id
              LEFT JOIN general_ticket_operate_grants operation ON operation.user_id=account.id
             ORDER BY account.username,account.id
            """,administratorArgs(authentication),(rs,row)->new AccountAccess(rs.getLong("id"),rs.getString("username"),rs.getBoolean("can_read"),rs.getBoolean("can_operate")));
        // A current authorized administrator is itself always present in the inventory.
        if(accounts.isEmpty())throw administratorDenied();
        return accounts;
    }

    public boolean grantRead(Authentication authentication,long userId) {
        return mutate(authentication,userId,"""
            , changed AS (
                INSERT INTO general_ticket_read_grants(user_id,is_active)
                SELECT target.id,TRUE FROM users target CROSS JOIN administrator WHERE target.id=:targetId
                ON CONFLICT(user_id) DO UPDATE SET is_active=TRUE,updated_at=CURRENT_TIMESTAMP
                RETURNING user_id
            )
            """);
    }

    public boolean revokeRead(Authentication authentication,long userId) {
        return mutate(authentication,userId,"""
            , changed AS (
                UPDATE general_ticket_read_grants access SET is_active=FALSE,updated_at=CURRENT_TIMESTAMP
                  FROM administrator WHERE access.user_id=:targetId RETURNING access.user_id
            ), revoked_operation AS (
                UPDATE general_ticket_operate_grants SET is_active=FALSE,updated_at=CURRENT_TIMESTAMP
                 WHERE user_id IN (SELECT user_id FROM changed) RETURNING user_id
            )
            """);
    }

    public boolean grantOperate(Authentication authentication,long userId) {
        return mutate(authentication,userId,"""
            , allowed AS MATERIALIZED (
                SELECT account.id FROM administrator JOIN users account ON account.id=:targetId
                JOIN general_ticket_read_grants read_access ON read_access.user_id=account.id
                WHERE read_access.is_active=TRUE AND
                """+CURRENT_ACCOUNT+"""
                FOR UPDATE OF read_access
            ), changed AS (
                INSERT INTO general_ticket_operate_grants(user_id,is_active) SELECT id,TRUE FROM allowed
                ON CONFLICT(user_id) DO UPDATE SET is_active=TRUE,updated_at=CURRENT_TIMESTAMP
                RETURNING user_id
            )
            """);
    }

    public boolean revokeOperate(Authentication authentication,long userId) {
        return mutate(authentication,userId,"""
            , changed AS (
                UPDATE general_ticket_operate_grants operation SET is_active=FALSE,updated_at=CURRENT_TIMESTAMP
                  FROM administrator WHERE operation.user_id=:targetId RETURNING operation.user_id
            )
            """);
    }

    private boolean mutate(Authentication authentication,long userId,String changes) {
        requirePositive(userId);
        var args=administratorArgs(authentication).addValue("targetId",userId,Types.BIGINT);
        return Boolean.TRUE.equals(adminSql.queryForObject(ADMINISTRATOR+changes+"""
            SELECT EXISTS(SELECT 1 FROM administrator) AS authorized,EXISTS(SELECT 1 FROM changed) AS changed
            """,args,(rs,row)->{
                if(!rs.getBoolean("authorized"))throw administratorDenied();
                return rs.getBoolean("changed");
            }));
    }

    private MapSqlParameterSource administratorArgs(Authentication authentication) {
        var actor=GeneralTicketActor.from(authentication);
        if(actor==null||!actor.valid())throw administratorDenied();
        return PanelRolePermissionSnapshot.capture(jdbc,actor.userId(),actor.username()).bind(actor.bind(new MapSqlParameterSource()));
    }

    private AccessDeniedException administratorDenied() {
        return new AccessDeniedException("Требуются действующие ROLE_ADMIN и PAGE_SETTINGS");
    }

    private void requirePositive(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("Invalid account ID");
    }

    public record AccountAccess(long userId, String username, boolean canRead, boolean canOperate) {
        public AccountAccess(long userId,String username,boolean canRead) { this(userId,username,canRead,false); }
    }
}
