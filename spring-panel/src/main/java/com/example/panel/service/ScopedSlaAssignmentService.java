package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Root generation, revision CAS, current OPERATE and provenance commit together. */
@Service
public class ScopedSlaAssignmentService {
    private static final String OWNER="""
        owner.user_id=:user AND owner.ticket_id=:ticket AND owner.ticket_public_id=:public
        AND owner.business_id IS NOT DISTINCT FROM CAST(:business AS BIGINT)
        AND CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END=:kind
        AND owner.is_general=FALSE AND owner.channel_id=:channel AND channel.is_active=TRUE
        AND owner.responsibility_revision=:revision
        AND position('closed' IN lower(COALESCE(owner.status,'')))=0
        AND position('resolved' IN lower(COALESCE(owner.status,'')))=0
        AND 1=(SELECT count(*) FROM tickets duplicate_root WHERE duplicate_root.ticket_id=owner.ticket_id)
        AND (owner.business_id IS NOT NULL OR :strict=FALSE)
        AND (owner.business_id IS NULL OR
        """+DialogResponsibilityService.ASSIGNEE_CAN_OPERATE_SQL+")";
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final TransactionTemplate transaction;
    private final LegacyTicketIdConcurrencyGuard guard;

    public ScopedSlaAssignmentService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.guard=new LegacyTicketIdConcurrencyGuard(jdbc);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean assign(DialogLookupReadService.ScopedSlaDialog expected,String assignee,String actor,String source,String route) {
        if(expected==null || expected.scope()==null || !expected.scope().isValid() || expected.channelId()==null || expected.channelId()<=0
                || expected.revision()<0 || expected.revision()==Long.MAX_VALUE || assignee==null || assignee.isBlank()
                || assignee.equalsIgnoreCase("ai_agent") || source==null || source.isBlank()
                || ("BUSINESS".equals(expected.scope().scopeKind()) && !flags.isFoundationEnabled())) return false;
        String target=assignee,who=actor==null||actor.isBlank()?"sla_orchestrator":actor;
        if(target.length()>255 || who.length()>255 || source.length()>4096 || route!=null&&route.length()>4096) return false;
        var scope=expected.scope();
        var params=new MapSqlParameterSource().addValue("user",scope.ticketUserId()).addValue("ticket",scope.ticketId())
            .addValue("public",scope.ticketPublicId()).addValue("business",scope.businessId()).addValue("kind",scope.scopeKind())
            .addValue("channel",expected.channelId()).addValue("revision",expected.revision()).addValue("strict",flags.isStrictReadScope())
            .addValue("assignee",target).addValue("actor",who).addValue("previous",expected.expectedResponsible())
            .addValue("source",source).addValue("route",route);
        try {
            return Boolean.TRUE.equals(transaction.execute(tx->{
                guard.acquire(scope.ticketId());
                String root=" FROM tickets owner JOIN channels channel ON channel.id=owner.channel_id "
                    +"CROSS JOIN (VALUES(CAST(:assignee AS TEXT))) AS assignment(responsible) WHERE "+OWNER;
                var roots=jdbc.query("SELECT owner.ticket_id"+root+" FOR UPDATE OF owner,channel",(params),(rs,row)->rs.getString(1));
                if(roots.size()!=1) return false;
                int changed=jdbc.update("""
                    INSERT INTO ticket_responsibles AS current(ticket_id,responsible,assigned_by)
                    SELECT :ticket,:assignee,:actor WHERE EXISTS(SELECT 1
                    """+root+"""
                    ) AND (SELECT responsible FROM ticket_responsibles WHERE ticket_id=:ticket) IS NOT DISTINCT FROM CAST(:previous AS TEXT)
                    ON CONFLICT(ticket_id) DO UPDATE SET responsible=EXCLUDED.responsible,assigned_by=EXCLUDED.assigned_by,assigned_at=CURRENT_TIMESTAMP
                    WHERE current.responsible IS NOT DISTINCT FROM CAST(:previous AS TEXT) AND current.responsible IS DISTINCT FROM EXCLUDED.responsible
                    """,params);
                if(changed!=1) return false;
                Long revision=jdbc.queryForObject("SELECT responsibility_revision FROM tickets WHERE user_id=:user AND ticket_id=:ticket",params,Long.class);
                if(revision==null || revision!=expected.revision()+1) throw new IllegalStateException("Assignment revision trigger is unavailable");
                jdbc.update("""
                    INSERT INTO scoped_sla_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,scope_kind,
                        revision,previous_responsible,assignee,actor,source,route)
                    VALUES(:user,:ticket,:public,:business,:kind,:next,:previous,:assignee,:actor,:source,:route)
                    """,params.addValue("next",revision));
                return true;
            }));
        } catch(DataAccessException | IllegalStateException ex) {return false;}
    }
}
