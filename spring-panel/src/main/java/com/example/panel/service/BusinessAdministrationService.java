package com.example.panel.service;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Explicit business catalog and access grants. No integration is required to create a business. */
@Service
public class BusinessAdministrationService {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    private final JdbcTemplate jdbc;

    public BusinessAdministrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Overview overview() {
        List<Business> businesses = jdbc.query("""
                SELECT id, code, display_name, status FROM businesses ORDER BY id
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")));
        List<PanelUser> users = jdbc.query("""
                SELECT id, username FROM users ORDER BY lower(username), id
                """, (rs, row) -> new PanelUser(rs.getLong("id"), rs.getString("username")));
        List<Membership> memberships = jdbc.query("""
                SELECT membership.user_id, membership.business_id, membership.role_code
                  FROM business_memberships membership
                 WHERE membership.is_active = TRUE
                 ORDER BY membership.business_id, membership.user_id
                """, (rs, row) -> new Membership(rs.getLong("user_id"),
                rs.getLong("business_id"), rs.getString("role_code")));
        List<AllGrant> allGrants = jdbc.query("""
                SELECT user_id, role_code FROM business_access_grants
                 WHERE grant_scope = 'ALL_BUSINESSES' AND is_active = TRUE
                 ORDER BY user_id
                """, (rs, row) -> new AllGrant(rs.getLong("user_id"), rs.getString("role_code")));
        return new Overview(businesses, users, memberships, allGrants);
    }

    public Business createBusiness(String code, String displayName) {
        if (code == null || code.length() > 100 || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid business code");
        }
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > 200) {
            throw new IllegalArgumentException("Invalid business name");
        }
        return jdbc.queryForObject("""
                INSERT INTO businesses(code, display_name) VALUES (?, ?)
                RETURNING id, code, display_name, status
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")), code, name);
    }

    public boolean grantMembership(long businessId, long userId, String roleCode) {
        requirePositiveIds(businessId, userId);
        String role = requireRole(roleCode);
        return jdbc.update("""
                INSERT INTO business_memberships(user_id, business_id, role_code, is_active)
                SELECT account.id, business.id, ?, TRUE
                  FROM users account CROSS JOIN businesses business
                 WHERE account.id = ? AND business.id = ? AND business.status = 'active'
                ON CONFLICT (user_id, business_id) DO UPDATE
                  SET role_code = EXCLUDED.role_code, is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, role, userId, businessId) == 1;
    }

    public void revokeMembership(long businessId, long userId) {
        requirePositiveIds(businessId, userId);
        jdbc.update("""
                UPDATE business_memberships SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE business_id = ? AND user_id = ? AND is_active = TRUE
                """, businessId, userId);
    }

    public boolean grantAllBusinesses(long userId, String roleCode) {
        requirePositiveIds(userId);
        String role = requireRole(roleCode);
        return jdbc.update("""
                INSERT INTO business_access_grants(user_id, grant_scope, role_code, is_active)
                SELECT id, 'ALL_BUSINESSES', ?, TRUE FROM users WHERE id = ?
                ON CONFLICT (user_id, grant_scope) DO UPDATE
                  SET role_code = EXCLUDED.role_code, is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, role, userId) == 1;
    }

    public void revokeAllBusinesses(long userId) {
        requirePositiveIds(userId);
        jdbc.update("""
                UPDATE business_access_grants SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND grant_scope = 'ALL_BUSINESSES' AND is_active = TRUE
                """, userId);
    }

    private String requireRole(String roleCode) {
        if (roleCode == null) {
            throw new IllegalArgumentException("Invalid business role");
        }
        try {
            return BusinessAccessService.BusinessRole.valueOf(roleCode).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid business role", ex);
        }
    }

    private void requirePositiveIds(long... ids) {
        for (long id : ids) {
            if (id <= 0) {
                throw new IllegalArgumentException("Invalid identifier");
            }
        }
    }

    public record Business(long id, String code, String displayName, String status) {
    }

    public record PanelUser(long id, String username) {
    }

    public record Membership(long userId, long businessId, String roleCode) {
    }

    public record AllGrant(long userId, String roleCode) {
    }

    public record Overview(List<Business> businesses, List<PanelUser> users,
                           List<Membership> memberships, List<AllGrant> allGrants) {
    }
}
