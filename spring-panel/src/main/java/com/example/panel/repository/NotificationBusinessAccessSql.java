package com.example.panel.repository;

/** Shared statement-time READ policy for a canonical ticket owner and notification recipient. */
public final class NotificationBusinessAccessSql {
    private NotificationBusinessAccessSql() { }

    // Callers bind aliases owner (tickets) and n (the normalized notification recipient).
    // Every currently supported business role includes READ, as in BusinessAccessService.
    public static final String CAN_READ_OWNER_BUSINESS = """
        EXISTS (
            SELECT 1 FROM users recipient
            JOIN businesses readable_business ON readable_business.id = owner.business_id
             WHERE lower(recipient.username) = n.user_identity
               AND recipient.enabled = TRUE AND recipient.is_blocked = FALSE
               AND (SELECT COUNT(*) FROM users identity_candidate
                     WHERE lower(identity_candidate.username) = n.user_identity) = 1
               AND readable_business.status = 'active'
               AND (EXISTS (
                   SELECT 1 FROM business_memberships membership
                    WHERE membership.user_id = recipient.id
                      AND membership.business_id = owner.business_id
                      AND membership.is_active = TRUE
                      AND membership.role_code IN ('VIEWER', 'OPERATOR', 'MANAGER', 'ADMIN')
               ) OR EXISTS (
                   SELECT 1 FROM business_access_grants access_grant
                    WHERE access_grant.user_id = recipient.id
                      AND access_grant.grant_scope = 'ALL_BUSINESSES'
                      AND access_grant.is_active = TRUE
                      AND access_grant.role_code IN ('VIEWER', 'OPERATOR', 'MANAGER', 'ADMIN')
               ))
        )
        """;
}
