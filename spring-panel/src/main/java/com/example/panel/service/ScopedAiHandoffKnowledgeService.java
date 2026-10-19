package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.repository.ScopedAiSql;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Trusted worker records references only. Actor reads reprove READ, approval/revision and origin. No auto-reply. */
@Service
public class ScopedAiHandoffKnowledgeService {
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final ScopedDialogAiPersistenceService persistence;
    private static final String COLS="ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id";
    public ScopedAiHandoffKnowledgeService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,ScopedDialogAiPersistenceService persistence) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc); this.flags=flags; this.persistence=persistence;
    }
    public boolean record(UiEventTicketScope scope,String message) {
        if (scope==null || !scope.isValid()) return false;
        if (!"BUSINESS".equals(scope.scopeKind())) return persistence.recordHandoff(scope);
        String query=message==null || message.length()>500 ? "" : message.trim();
        String sql="""
            WITH current_owner AS MATERIALIZED (
                SELECT owner.* FROM tickets owner WHERE
            """+ScopedAiSql.CURRENT_OWNER+" AND ("+ScopedAiSql.CAN_ESCALATE+"""
            )), candidates AS MATERIALIZED (
                SELECT source.id,source.revision FROM current_owner owner JOIN ai_scoped_sources source
                    ON source.business_id=owner.business_id
                WHERE source.status='APPROVED' AND :query<>'' AND
            """+ScopedAiSourceService.CURRENT_ORIGIN+"""
                  AND POSITION(lower(:query) IN lower(source.title||' '||source.body))>0
                ORDER BY source.updated_at DESC,source.id DESC LIMIT 3
            ), saved AS (
                INSERT INTO ticket_ai_scoped_state AS target (
            """+COLS+"""
                ,is_processing,mode,last_action,updated_at)
                SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,'BUSINESS',owner.business_id,
                    FALSE,'escalate_only','operator_required',CURRENT_TIMESTAMP FROM current_owner owner
                ON CONFLICT (ticket_public_id,scope_business_key) DO UPDATE
                  SET is_processing=FALSE,mode=excluded.mode,last_action=excluded.last_action,updated_at=excluded.updated_at
                  WHERE ROW(target.ticket_user_id,target.ticket_id,target.ticket_public_id,target.ticket_scope_kind,target.ticket_business_id)
                    IS NOT DISTINCT FROM ROW(excluded.ticket_user_id,excluded.ticket_id,excluded.ticket_public_id,excluded.ticket_scope_kind,excluded.ticket_business_id)
                RETURNING *
            ), event AS (
                INSERT INTO ticket_ai_scoped_events (
            """+COLS+"""
                ,event_type,decision_reason)
                SELECT
            """+COLS+"""
                ,'operator_handoff_requested',CASE WHEN EXISTS(SELECT 1 FROM candidates)
                    THEN 'approved_sources_available' ELSE 'no_approved_sources' END FROM saved RETURNING id
            ), evidence AS (
                INSERT INTO ai_scoped_handoff_evidence(event_id,source_id,source_revision)
                SELECT event.id,candidates.id,candidates.revision FROM event CROSS JOIN candidates RETURNING event_id
            )
            SELECT COUNT(*) FROM event
            """;
        try { return Integer.valueOf(1).equals(jdbc.queryForObject(sql,args(scope).addValue("query",query,Types.VARCHAR),Integer.class)); }
        catch (DataAccessException ex) { return false; }
    }
    public List<ScopedAiSourceService.Summary> candidates(UiEventTicketScope scope,String identity) {
        if (scope==null || !scope.isValid() || !"BUSINESS".equals(scope.scopeKind()) || identity==null || identity.isBlank()) return List.of();
        String sql="""
            SELECT source.id,source.source_kind,source.source_key,source.title,source.status,source.revision
              FROM tickets owner CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n
              JOIN ticket_ai_scoped_events event ON event.ticket_user_id=owner.user_id AND event.ticket_id=owner.ticket_id
                AND event.ticket_public_id=owner.ticket_public_id AND event.ticket_business_id=owner.business_id
                AND event.ticket_scope_kind='BUSINESS'
              JOIN ai_scoped_handoff_evidence evidence ON evidence.event_id=event.id
              JOIN ai_scoped_sources source ON source.id=evidence.source_id AND source.revision=evidence.source_revision
                AND source.business_id=owner.business_id AND source.status='APPROVED'
             WHERE
            """+ScopedAiSql.CURRENT_OWNER+" AND ("+NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS
            +") AND ("+ScopedAiSql.CAN_ESCALATE+") AND "+ScopedAiSourceService.CURRENT_ORIGIN+"""
              AND event.id=(SELECT MAX(latest.id) FROM ticket_ai_scoped_events latest
                WHERE latest.ticket_public_id=owner.ticket_public_id AND latest.ticket_business_id=owner.business_id
                  AND latest.event_type='operator_handoff_requested')
            ORDER BY source.id DESC LIMIT 3
            """;
        try {
            return jdbc.query(sql,args(scope).addValue("identity",identity.trim().toLowerCase(Locale.ROOT),Types.VARCHAR),
                (rs,row)->new ScopedAiSourceService.Summary(rs.getLong("id"),ScopedAiSourceService.Kind.valueOf(rs.getString("source_kind")),
                    rs.getString("source_key"),rs.getString("title"),ScopedAiSourceService.Status.valueOf(rs.getString("status")),rs.getLong("revision")));
        } catch (DataAccessException ex) { return List.of(); }
    }
    private MapSqlParameterSource args(UiEventTicketScope s) {
        return new MapSqlParameterSource().addValue("ticketUserId",s.ticketUserId(),Types.BIGINT)
            .addValue("ticketId",s.ticketId(),Types.VARCHAR).addValue("publicId",s.ticketPublicId(),Types.VARCHAR)
            .addValue("scopeKind",s.scopeKind(),Types.VARCHAR).addValue("businessId",s.businessId(),Types.BIGINT)
            .addValue("strict",flags.isStrictReadScope(),Types.BOOLEAN);
    }
}
