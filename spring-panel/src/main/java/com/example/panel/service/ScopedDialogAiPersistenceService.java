package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.repository.ScopedAiSql;
import com.example.panel.security.PanelUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Trusted worker writes and actor-authorized reads/controls for canonical AI records. No legacy fallback. */
@Service
public class ScopedDialogAiPersistenceService {
    private static final String OWNER_COLUMNS = "ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id";
    private static final String OWNER_VALUES = "owner.user_id,owner.ticket_id,owner.ticket_public_id,"
        + "CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END,owner.business_id";
    private static final String SAME_EXCLUDED_OWNER = """
        ROW(target.ticket_user_id,target.ticket_id,target.ticket_public_id,target.ticket_scope_kind,target.ticket_business_id)
        IS NOT DISTINCT FROM ROW(excluded.ticket_user_id,excluded.ticket_id,excluded.ticket_public_id,
            excluded.ticket_scope_kind,excluded.ticket_business_id)
        """;
    private static final String ACTOR = " CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n ";
    private static final String READ_ACTOR = ACTOR + " JOIN users actor ON actor.id=:actorId AND actor.username=:actorName "
        + " AND actor.enabled=TRUE AND actor.is_blocked=FALSE AND lower(actor.username)=n.user_identity ";
    private static final String READ = " AND (" + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + ")";
    private static final String OPERATE = """
        AND (EXISTS (SELECT 1 FROM business_memberships membership
            WHERE membership.user_id = actor.id AND membership.business_id = owner.business_id
              AND membership.is_active = TRUE AND membership.role_code IN ('OPERATOR','MANAGER','ADMIN'))
          OR EXISTS (SELECT 1 FROM business_access_grants access_grant
            WHERE access_grant.user_id = actor.id AND access_grant.grant_scope = 'ALL_BUSINESSES'
              AND access_grant.is_active = TRUE AND access_grant.role_code IN ('OPERATOR','MANAGER','ADMIN')))
        """;
    private static final String CONFIGURE = """
        AND (EXISTS (SELECT 1 FROM business_memberships membership
            WHERE membership.user_id=actor.id AND membership.business_id=owner.business_id
              AND membership.is_active=TRUE AND membership.role_code='ADMIN')
          OR EXISTS (SELECT 1 FROM business_access_grants access_grant
            WHERE access_grant.user_id=actor.id AND access_grant.grant_scope='ALL_BUSINESSES'
              AND access_grant.is_active=TRUE AND access_grant.role_code='ADMIN'))
        """;
    private final NamedParameterJdbcOperations jdbc;
    private final MultibusinessFeatureFlags flags;

    @Autowired
    public ScopedDialogAiPersistenceService(JdbcTemplate jdbc, MultibusinessFeatureFlags flags) {
        this(new NamedParameterJdbcTemplate(jdbc), flags);
    }

    ScopedDialogAiPersistenceService(NamedParameterJdbcOperations jdbc, MultibusinessFeatureFlags flags) {
        this.jdbc = jdbc;
        this.flags = flags;
    }

    /** One statement atomically saves a non-processing state and an event describing the request, not delivery. */
    public boolean recordHandoff(UiEventTicketScope scope) {
        if (!valid(scope)) return false;
        String sql = "WITH saved AS (INSERT INTO ticket_ai_scoped_state AS target (" + OWNER_COLUMNS
            + ",is_processing,mode,last_action,updated_at) SELECT " + OWNER_VALUES
            + ",FALSE,'escalate_only','operator_required',CURRENT_TIMESTAMP FROM tickets owner WHERE "
            + ScopedAiSql.CURRENT_OWNER + " AND (" + ScopedAiSql.CAN_ESCALATE + ")"
            + " ON CONFLICT (ticket_public_id,scope_business_key) DO UPDATE SET is_processing=FALSE,"
            + "mode=excluded.mode,last_action=excluded.last_action,updated_at=excluded.updated_at WHERE "
            + SAME_EXCLUDED_OWNER + " RETURNING " + OWNER_COLUMNS + ")"
            + " INSERT INTO ticket_ai_scoped_events(" + OWNER_COLUMNS + ",event_type,decision_reason)"
            + " SELECT " + OWNER_COLUMNS + ",'operator_handoff_requested','scoped_ai_pipeline_pending' FROM saved";
        try {
            return jdbc.update(sql, parameters(scope, null)) == 1;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    /** Internal service primitive. An HTTP caller must additionally enforce its page authority and CSRF. */
    public boolean setControl(UiEventTicketScope scope, Authentication authentication, boolean aiDisabled,
                              boolean autoReplyBlocked, String reason) {
        var account=boundControlActor(authentication);
        if (account==null || !valid(scope)) return false;
        String sql = "INSERT INTO ticket_ai_scoped_control AS target (" + OWNER_COLUMNS
            + ",ai_disabled,auto_reply_blocked,reason,updated_by_user_id,updated_by) SELECT " + OWNER_VALUES
            + ",:aiDisabled,:autoReplyBlocked,:reason,actor.id,actor.username FROM tickets owner" + READ_ACTOR
            + " WHERE " + ScopedAiSql.CURRENT_OWNER
            + READ + OPERATE + " ON CONFLICT (ticket_public_id,scope_business_key) DO UPDATE SET "
            + "ai_disabled=excluded.ai_disabled,auto_reply_blocked=excluded.auto_reply_blocked,reason=excluded.reason,"
            + "updated_by_user_id=excluded.updated_by_user_id,updated_by=excluded.updated_by,updated_at=CURRENT_TIMESTAMP WHERE "
            + SAME_EXCLUDED_OWNER;
        var args = readParameters(scope, account).addValue("aiDisabled", aiDisabled, Types.BOOLEAN)
            .addValue("autoReplyBlocked", autoReplyBlocked, Types.BOOLEAN).addValue("reason", cut(reason), Types.VARCHAR);
        try {
            return jdbc.update(sql, args) == 1;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    public Optional<Control> findControl(UiEventTicketScope scope, Authentication authentication) {
        var account=boundControlActor(authentication);
        if (account==null || !valid(scope)) return Optional.empty();
        try {
            var rows = jdbc.query("""
                SELECT COALESCE(control.ai_disabled,FALSE) AS ai_disabled,
                       COALESCE(control.auto_reply_blocked,FALSE) AS auto_reply_blocked,
                       control.reason,control.updated_by,control.updated_at
                  FROM tickets owner
                """ + READ_ACTOR + """
                  LEFT JOIN ticket_ai_scoped_control control ON control.ticket_public_id=owner.ticket_public_id
                    AND control.scope_business_key=COALESCE(owner.business_id,0)
                 WHERE
                """ + ScopedAiSql.CURRENT_OWNER + READ + " AND (control.ticket_public_id IS NULL OR ("
                    + ScopedAiSql.CONTROL_MATCHES_OWNER + "))",
                readParameters(scope, account), (rs, row) -> new Control(rs.getBoolean("ai_disabled"),
                    rs.getBoolean("auto_reply_blocked"),rs.getString("reason"),rs.getString("updated_by"),rs.getString("updated_at")));
            return rows.size() == 1 ? Optional.of(rows.get(0)) : Optional.empty();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean canOperate(UiEventTicketScope scope,Authentication authentication) {
        var account=boundControlActor(authentication);
        if (account==null || !valid(scope) || !"BUSINESS".equals(scope.scopeKind())) return false;
        String sql="SELECT EXISTS(SELECT 1 FROM tickets owner"+READ_ACTOR
            +" WHERE "+ScopedAiSql.CURRENT_OWNER+READ+OPERATE+")";
        try { return Boolean.TRUE.equals(jdbc.queryForObject(sql,readParameters(scope,account),Boolean.class)); }
        catch (DataAccessException ex) { return false; }
    }

    /** UI compare-and-set: absent and present controls are separate operations; stale forms never upsert. */
    public boolean setControlExpected(UiEventTicketScope scope,Authentication authentication,String expectedUpdatedAt,
                                      boolean disabled,boolean blocked,String reason) {
        var account=boundControlActor(authentication);
        if (account==null || !valid(scope) || !"BUSINESS".equals(scope.scopeKind())) return false;
        String identity=account.getUsername();
        var args=parameters(scope,identity).addValue("disabled",disabled,Types.BOOLEAN)
            .addValue("blocked",blocked,Types.BOOLEAN).addValue("reason",cut(reason),Types.VARCHAR)
            .addValue("stamp",expectedUpdatedAt,Types.VARCHAR)
            .addValue("actorId",account.getUserId(),Types.BIGINT).addValue("actorName",identity,Types.VARCHAR);
        String changed;
        if (!StringUtils.hasText(expectedUpdatedAt)) {
            changed="INSERT INTO ticket_ai_scoped_control ("+OWNER_COLUMNS
                +",ai_disabled,auto_reply_blocked,reason,updated_by_user_id,updated_by) SELECT "+OWNER_VALUES
                +",:disabled,:blocked,:reason,actor.id,actor.username FROM tickets owner"+READ_ACTOR
                +" WHERE "+ScopedAiSql.CURRENT_OWNER
                +READ+OPERATE+" ON CONFLICT (ticket_public_id,scope_business_key) DO NOTHING RETURNING "+OWNER_COLUMNS;
        } else {
            changed="UPDATE ticket_ai_scoped_control target SET ai_disabled=:disabled,auto_reply_blocked=:blocked,"
                +"reason=:reason,updated_by_user_id=actor.id,updated_by=actor.username,"
                +"updated_at=GREATEST(clock_timestamp(),target.updated_at+INTERVAL '1 microsecond')"
                +" FROM tickets owner"+READ_ACTOR+" WHERE "
                +ScopedAiSql.CURRENT_OWNER+READ+OPERATE
                +" AND ROW(target.ticket_user_id,target.ticket_id,target.ticket_public_id,target.ticket_scope_kind,target.ticket_business_id)"
                +" IS NOT DISTINCT FROM ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,'BUSINESS',owner.business_id)"
                +" AND target.updated_at=CAST(:stamp AS TIMESTAMPTZ) RETURNING target."+OWNER_COLUMNS.replace(",",",target.");
        }
        String sql="WITH changed AS ("+changed+") INSERT INTO ticket_ai_scoped_events("+OWNER_COLUMNS
            +",event_type,decision_reason) SELECT "+OWNER_COLUMNS+",'control_updated','operator_control_changed' FROM changed";
        try { return jdbc.update(sql,args)==1; } catch (DataAccessException ex) { return false; }
    }

    public Optional<Map<String, Object>> findState(UiEventTicketScope scope, Authentication authentication) {
        var rows = readOwned("ticket_ai_scoped_state", "record.is_processing,record.mode,record.last_action,record.updated_at",
            scope, authentication, "", 1);
        return rows.size() == 1 ? Optional.of(rows.get(0)) : Optional.empty();
    }

    public List<Map<String, Object>> findEvents(UiEventTicketScope scope, Authentication authentication, int limit) {
        return readOwned("ticket_ai_scoped_events", "record.id,record.event_type,record.decision_reason,record.created_at",
            scope, authentication, " ORDER BY record.id DESC", Math.max(1, Math.min(limit, 50)));
    }

    /** A single snapshot freezes the displayed history boundary and both mutable row versions. */
    public Optional<CleanupSnapshot> cleanupSnapshot(UiEventTicketScope scope,Authentication authentication) {
        var account=boundControlActor(authentication);
        if (account==null || !valid(scope) || !"BUSINESS".equals(scope.scopeKind())) return Optional.empty();
        String sql="SELECT COALESCE(control.updated_at::TEXT,'') AS control_stamp,COALESCE(state.updated_at::TEXT,'') AS state_stamp,"
            +"(SELECT COALESCE(MAX(event.id),0) FROM ticket_ai_scoped_events event WHERE "+matchesOwner("event")+") AS event_through"
            +cleanupOwner()+" WHERE "+ScopedAiSql.CURRENT_OWNER+READ+CONFIGURE
            +" AND (control.ticket_public_id IS NULL OR "+matchesOwner("control")+")"
            +" AND (state.ticket_public_id IS NULL OR "+matchesOwner("state")+")";
        try {
            var rows=jdbc.query(sql,readParameters(scope,account),(rs,row)->new CleanupSnapshot(
                rs.getString("control_stamp"),rs.getString("state_stamp"),rs.getLong("event_through")));
            return rows.size()==1 ? Optional.of(rows.get(0)) : Optional.empty();
        } catch (DataAccessException ex) { return Optional.empty(); }
    }

    /** Erases the frozen history, never newer events. Retains a blocking control and one content-free audit event. */
    public boolean clearHistoryExpected(UiEventTicketScope scope,Authentication authentication,CleanupSnapshot expected) {
        var account=boundControlActor(authentication);
        if (account==null || !valid(scope) || !"BUSINESS".equals(scope.scopeKind()) || expected==null
                || expected.controlStamp()==null || expected.stateStamp()==null || expected.controlStamp().length()>100
                || expected.stateStamp().length()>100 || expected.eventThrough()<0) return false;
        var args=readParameters(scope,account).addValue("controlStamp",expected.controlStamp(),Types.VARCHAR)
            .addValue("stateStamp",expected.stateStamp(),Types.VARCHAR).addValue("eventThrough",expected.eventThrough(),Types.BIGINT);
        String authorized="SELECT owner.*,actor.id AS actor_id,actor.username AS actor_name"+cleanupOwner()
            +" WHERE "+ScopedAiSql.CURRENT_OWNER+READ+CONFIGURE
            +" AND (control.ticket_public_id IS NULL OR "+matchesOwner("control")+")"
            +" AND (state.ticket_public_id IS NULL OR "+matchesOwner("state")+")"
            +" AND control.updated_at IS NOT DISTINCT FROM CAST(NULLIF(:controlStamp,'') AS TIMESTAMPTZ)"
            +" AND state.updated_at IS NOT DISTINCT FROM CAST(NULLIF(:stateStamp,'') AS TIMESTAMPTZ)"
            +" AND :eventThrough=(SELECT COALESCE(MAX(event.id),0) FROM ticket_ai_scoped_events event WHERE "+matchesOwner("event")+")";
        String changed;
        if (expected.controlStamp().isEmpty()) {
            changed="INSERT INTO ticket_ai_scoped_control ("+OWNER_COLUMNS
                +",ai_disabled,auto_reply_blocked,reason,updated_by_user_id,updated_by) SELECT "+OWNER_VALUES
                +",TRUE,TRUE,NULL,owner.actor_id,owner.actor_name FROM authorized owner"
                +" ON CONFLICT (ticket_public_id,scope_business_key) DO NOTHING RETURNING *";
        } else {
            changed="UPDATE ticket_ai_scoped_control target SET ai_disabled=TRUE,auto_reply_blocked=TRUE,reason=NULL,"
                +"updated_by_user_id=owner.actor_id,updated_by=owner.actor_name,"
                +"updated_at=GREATEST(clock_timestamp(),target.updated_at+INTERVAL '1 microsecond') FROM authorized owner WHERE "
                +matchesOwner("target")+" AND target.updated_at=CAST(:controlStamp AS TIMESTAMPTZ) RETURNING target.*";
        }
        String sql="WITH authorized AS MATERIALIZED ("+authorized+"), changed AS ("+changed+"), removed_state AS ("
            +"DELETE FROM ticket_ai_scoped_state record USING changed owner WHERE "+matchesRecord("record")
            +" AND record.updated_at=CAST(NULLIF(:stateStamp,'') AS TIMESTAMPTZ) RETURNING record.ticket_public_id), removed_events AS ("
            +"DELETE FROM ticket_ai_scoped_events record USING changed owner WHERE "+matchesRecord("record")
            +" AND record.id<=:eventThrough RETURNING record.id), removed_evidence AS ("
            +"DELETE FROM ai_scoped_handoff_evidence WHERE event_id IN (SELECT id FROM removed_events) RETURNING event_id), audit AS ("
            +"INSERT INTO ticket_ai_scoped_events("+OWNER_COLUMNS+",event_type,decision_reason) SELECT "+OWNER_COLUMNS
            +",'ai_data_cleared','manual_cleanup_ai_disabled' FROM changed RETURNING id)"
            // A failed state CAS must roll back every data-modifying CTE, including the blocking control and audit.
            +" SELECT 1/CASE WHEN (SELECT COUNT(*) FROM audit)=1 AND (:stateStamp='' OR (SELECT COUNT(*) FROM removed_state)=1) THEN 1 ELSE 0 END";
        try { return Integer.valueOf(1).equals(jdbc.queryForObject(sql,args,Integer.class)); }
        catch (DataAccessException ex) { return false; }
    }

    private String cleanupOwner() {
        return " FROM tickets owner"+READ_ACTOR
            +" LEFT JOIN ticket_ai_scoped_control control ON control.ticket_public_id=owner.ticket_public_id AND control.scope_business_key=owner.business_id"
            +" LEFT JOIN ticket_ai_scoped_state state ON state.ticket_public_id=owner.ticket_public_id AND state.scope_business_key=owner.business_id";
    }
    private String matchesOwner(String alias) {
        return "ROW("+alias+".ticket_user_id,"+alias+".ticket_id,"+alias+".ticket_public_id,"+alias+".ticket_scope_kind,"+alias+".ticket_business_id)"
            +" IS NOT DISTINCT FROM ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,'BUSINESS',owner.business_id)";
    }
    private String matchesRecord(String alias) {
        return "ROW("+alias+".ticket_user_id,"+alias+".ticket_id,"+alias+".ticket_public_id,"+alias+".ticket_scope_kind,"+alias+".ticket_business_id)"
            +" IS NOT DISTINCT FROM ROW(owner.ticket_user_id,owner.ticket_id,owner.ticket_public_id,owner.ticket_scope_kind,owner.ticket_business_id)";
    }

    private List<Map<String, Object>> readOwned(String table, String columns, UiEventTicketScope scope,
                                                Authentication authentication, String ordering, int limit) {
        var account=boundControlActor(authentication);
        if (account==null || !valid(scope)) return List.of();
        String sql = "SELECT " + columns + " FROM tickets owner" + READ_ACTOR + " JOIN " + table + " record ON "
            + "record.ticket_user_id=owner.user_id AND record.ticket_id=owner.ticket_id"
            + " AND record.ticket_public_id=owner.ticket_public_id"
            + " AND record.ticket_business_id IS NOT DISTINCT FROM owner.business_id"
            + " AND record.ticket_scope_kind=CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END"
            + " WHERE " + ScopedAiSql.CURRENT_OWNER + READ + ordering + " LIMIT :limit";
        try {
            return jdbc.queryForList(sql, readParameters(scope, account).addValue("limit", limit, Types.INTEGER));
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private PanelUserPrincipal boundControlActor(Authentication authentication) {
        if(authentication==null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof PanelUserPrincipal account) || account.getUserId()<=0
            || account.getUsername().isBlank() || !account.isEnabled() || !account.isAccountNonLocked()
            || !account.isAccountNonExpired() || !account.isCredentialsNonExpired()) return null;
        return account;
    }

    private boolean valid(UiEventTicketScope scope) {
        return flags != null && scope != null && scope.isValid();
    }

    private MapSqlParameterSource readParameters(UiEventTicketScope scope, PanelUserPrincipal account) {
        return parameters(scope,account.getUsername()).addValue("actorId",account.getUserId(),Types.BIGINT)
            .addValue("actorName",account.getUsername(),Types.VARCHAR);
    }

    private MapSqlParameterSource parameters(UiEventTicketScope scope, String identity) {
        return new MapSqlParameterSource().addValue("ticketUserId",scope.ticketUserId(),Types.BIGINT)
            .addValue("ticketId",scope.ticketId(),Types.VARCHAR).addValue("publicId",scope.ticketPublicId(),Types.VARCHAR)
            .addValue("businessId",scope.businessId(),Types.BIGINT).addValue("scopeKind",scope.scopeKind(),Types.VARCHAR)
            .addValue("strict",flags.isStrictReadScope(),Types.BOOLEAN)
            .addValue("identity",StringUtils.hasText(identity) ? identity.toLowerCase(Locale.ROOT) : null,Types.VARCHAR);
    }

    private String cut(String value) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        return text.substring(0, Math.min(text.length(), 500));
    }

    public record Control(boolean aiDisabled, boolean autoReplyBlocked, String reason, String updatedBy, String updatedAt) { }
    public record CleanupSnapshot(String controlStamp,String stateStamp,long eventThrough) { }
}
