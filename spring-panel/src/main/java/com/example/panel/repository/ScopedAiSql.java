package com.example.panel.repository;

/** SQL fragments bind the canonical tickets alias owner; controls use the alias control. */
public final class ScopedAiSql {
    private ScopedAiSql() { }

    public static final String CURRENT_OWNER = """
        owner.user_id = :ticketUserId AND owner.ticket_id = :ticketId AND owner.ticket_public_id = :publicId
        AND owner.business_id IS NOT DISTINCT FROM CAST(:businessId AS BIGINT)
        AND CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END = :scopeKind
        AND owner.is_general = FALSE
        AND 1 = (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = owner.ticket_id)
        AND (owner.business_id IS NOT NULL OR :strict = FALSE)
        AND (owner.business_id IS NULL OR EXISTS (
            SELECT 1 FROM businesses business WHERE business.id = owner.business_id AND business.status = 'active'
        ))
        """;

    public static final String CONTROL_MATCHES_OWNER = """
        ROW(control.ticket_user_id, control.ticket_id, control.ticket_public_id, control.ticket_scope_kind, control.ticket_business_id)
        IS NOT DISTINCT FROM ROW(owner.user_id, owner.ticket_id, owner.ticket_public_id,
            CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END, owner.business_id)
        """;

    /** Also used inside the final notification INSERT; never replace this with a cached control read. */
    public static final String CAN_ESCALATE = """
        NOT EXISTS (
            SELECT 1 FROM ticket_ai_scoped_control control
             WHERE control.ticket_public_id = owner.ticket_public_id
               AND control.scope_business_key = COALESCE(owner.business_id, 0)
               AND (control.ai_disabled = TRUE OR NOT (
        """ + CONTROL_MATCHES_OWNER + ")))";
}
