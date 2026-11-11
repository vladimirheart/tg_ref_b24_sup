package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ScopedAiSql;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** One provider attempt per captured incoming event/history. No raw-ID recapture, media or LLM calls. */
@Service
public class ScopedAiAutoReplyService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ScopedAiAutoReplyService.class);
    private static final String DESTINATION="""
        encode(sha256(convert_to(jsonb_build_array(destination.id,destination.token,destination.platform,
            destination.platform_config,destination.delivery_settings,destination.credential_id)::text,'UTF8')),'hex')
        """;
    private static final String BODY_HASH="encode(sha256(convert_to(source.body,'UTF8')),'hex')";
    private static final String HISTORY="""
        history.ticket_owner_user_id=owner.user_id AND history.user_id=owner.user_id AND history.ticket_id=owner.ticket_id
        AND history.ticket_public_id=owner.ticket_public_id AND history.channel_id=owner.channel_id
        AND history.id=incoming.incoming_history_id AND history.sender='client' AND history.deleted_at IS NULL
        AND history.message_type='text' AND NULLIF(btrim(history.attachment),'') IS NULL
        AND history.message=incoming.message_text
        """;
    private final NamedParameterJdbcTemplate sql;
    private final MultibusinessFeatureFlags flags;
    private final DialogAiAssistantConfigService config;
    private final DialogReplyTransportService transport;
    private final UiEventOutboxAppendService events;
    private final TransactionTemplate tx;
    public ScopedAiAutoReplyService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,DialogAiAssistantConfigService config,
                                   DialogReplyTransportService transport,UiEventOutboxAppendService events) {
        this.sql=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.config=config;this.transport=transport;this.events=events;
        tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    /** True suppresses only the redundant AI handoff. Ordinary incoming notifications still run. */
    public boolean process(UiEventTicketScope scope,long eventId) {
        if(!enabled(scope,eventId)) return false;
        prepare(scope,eventId);
        dispatch(scope,eventId);
        try { return Boolean.TRUE.equals(sql.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM ai_scoped_text_replies WHERE incoming_event_id=:eventId
              AND ticket_user_id=:ticketUserId AND ticket_id=:ticketId AND ticket_public_id=:publicId
              AND business_id=:businessId AND state IN ('QUEUED','SENDING','SENT'))
            """,args(scope,eventId),Boolean.class));
        } catch(RuntimeException ex) { return false; }
    }
    /** Recover only work that has never crossed the durable SENDING claim; independent of the UI cursor. */
    int recoverQueued(int limit) {
        if(limit<1 || !runtimeEnabled()) return 0;
        try {
            var queued=sql.query("""
                SELECT incoming_event_id,ticket_user_id,ticket_id,ticket_public_id,business_id
                  FROM ai_scoped_text_replies WHERE state='QUEUED'
                 ORDER BY incoming_event_id LIMIT :limit
                """,new MapSqlParameterSource("limit",Math.min(limit,25)),(rs,n)->new Queued(
                    rs.getLong("incoming_event_id"),new UiEventTicketScope(rs.getLong("ticket_user_id"),
                        rs.getString("ticket_id"),rs.getString("ticket_public_id"),"BUSINESS",rs.getLong("business_id"))));
            for(var item:queued) dispatch(item.scope(),item.eventId());
            return queued.size();
        } catch(org.springframework.dao.DataAccessException ex) { return 0; }
    }
    boolean prepare(UiEventTicketScope scope,long eventId) {
        if(!enabled(scope,eventId)) return false;
        try { return Boolean.TRUE.equals(tx.execute(status->{
            var candidate=capture(scope,eventId);if(candidate.isEmpty()) return false;
            var c=candidate.get();
            if(!GeneralTicketReplyService.validInput("a".repeat(32),c.body())) return false;
            int inserted=sql.update("""
                INSERT INTO ai_scoped_text_replies(incoming_event_id,ticket_user_id,ticket_id,ticket_public_id,business_id,
                    channel_id,destination_sha256,input_history_id,owner_revision,control_stamp,rule_key,rule_revision,source_id,source_revision,response_sha256)
                VALUES(:eventId,:ticketUserId,:ticketId,:publicId,:businessId,:channelId,:destination,:historyId,:ownerRevision,:controlStamp,
                    :ruleKey,:ruleRevision,:sourceId,:sourceRevision,:response)
                ON CONFLICT DO NOTHING
                """,snapshotArgs(scope,eventId,c));
            if(inserted==1) audit(scope,eventId,"QUEUED");
            return inserted==1;
        })); } catch(RuntimeException ex) { return false; }
    }
    void dispatch(UiEventTicketScope scope,long eventId) {
        if(!enabled(scope,eventId)) return;
        boolean claimed;
        try { claimed=Boolean.TRUE.equals(tx.execute(status->{
            int count=sql.update("""
                UPDATE ai_scoped_text_replies SET state='SENDING',updated_at=CURRENT_TIMESTAMP
                 WHERE incoming_event_id=:eventId AND ticket_user_id=:ticketUserId AND ticket_id=:ticketId
                   AND ticket_public_id=:publicId AND business_id=:businessId AND state='QUEUED'
                """,args(scope,eventId));
            if(count==1) audit(scope,eventId,"SENDING");return count==1;
        })); } catch(RuntimeException ex) { return; }
        if(!claimed) return;
        try { tx.executeWithoutResult(status->{
            var candidate=capture(scope,eventId).filter(c->matches(scope,eventId,c));
            if(candidate.isEmpty()) { finish(scope,eventId,"CANCELLED",null);return; }
            var c=candidate.get();
            var result=transport.sendTextGuarded(c.channel(),scope.ticketUserId(),c.body(),null,
                ()->capture(scope,eventId).filter(current->same(c,current) && matches(scope,eventId,current)).isPresent());
            if(result==null || !result.success()) {
                if(result==null || !"scope_denied".equals(result.classification()))
                    log.warn("AI reply delivery unconfirmed: eventId={}, httpStatus={}",eventId,result==null?null:result.httpStatus());
                finish(scope,eventId,result!=null && "scope_denied".equals(result.classification())?"CANCELLED":"UNKNOWN",null);return;
            }
            Long historyId=sql.queryForObject("""
                INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,message_type,channel_id,tg_message_id,
                    ticket_owner_user_id,ticket_public_id)
                VALUES(:ticketUserId,'ai_agent',:body,CURRENT_TIMESTAMP,:ticketId,'text',:channelId,:providerId,:ticketUserId,:publicId)
                RETURNING id
                """,snapshotArgs(scope,eventId,c).addValue("body",c.body()).addValue("providerId",result.telegramMessageId(),java.sql.Types.BIGINT),Long.class);
            finish(scope,eventId,"SENT",historyId);
        }); } catch(RuntimeException ex) {
            log.warn("AI reply delivery unconfirmed: eventId={}, exceptionType={}, causeType={}",eventId,
                ex.getClass().getSimpleName(),ex.getCause()==null?"none":ex.getCause().getClass().getSimpleName());
            // Provider may have accepted the request. A committed SENDING claim is never queued again.
            try { tx.executeWithoutResult(status->finish(scope,eventId,"UNKNOWN",null)); }
            catch(RuntimeException ignored) { /* Leave SENDING for manual reconciliation. */ }
        }
    }
    private Optional<Candidate> capture(UiEventTicketScope scope,long eventId) {
        if(!enabled(scope,eventId)) return Optional.empty();
        var rows=sql.query("""
            SELECT owner.business_work_revision,coalesce((SELECT extract(epoch FROM control.updated_at)::text FROM ticket_ai_scoped_control control
                    WHERE control.ticket_public_id=owner.ticket_public_id AND control.scope_business_key=owner.business_id),'absent') AS control_stamp,
                rule.rule_key,rule.revision AS rule_revision,source.id AS source_id,source.revision AS source_revision,
                source.body,history.id AS history_id,destination.id,destination.token,destination.platform,
                destination.platform_config,destination.delivery_settings,destination.credential_id,
            """+DESTINATION+" AS destination_sha,"+BODY_HASH+"""
                AS response_sha
            FROM tickets owner
            JOIN messages root ON root.user_id=owner.user_id AND root.ticket_id=owner.ticket_id
                AND root.group_msg_id=owner.group_msg_id AND root.channel_id=owner.channel_id
            JOIN channels destination ON destination.id=owner.channel_id
            JOIN ui_event_outbox incoming ON incoming.id=:eventId AND incoming.event_type='client_message_created'
                AND incoming.ticket_user_id=owner.user_id AND incoming.ticket_id=owner.ticket_id
                AND incoming.ticket_public_id=owner.ticket_public_id AND incoming.ticket_scope_kind='BUSINESS'
                AND incoming.ticket_business_id=owner.business_id AND incoming.channel_id=owner.channel_id
            JOIN chat_history history ON
            """+HISTORY+"""
            JOIN ai_scoped_reply_rules rule ON rule.business_id=owner.business_id AND rule.active=TRUE
                AND rule.trigger_text=btrim(incoming.message_text) AND rule.created_at<=incoming.created_at
            JOIN ai_scoped_sources source ON source.id=rule.source_id AND source.business_id=rule.business_id
                AND source.revision=rule.source_revision AND source.status='APPROVED'
            JOIN users actor ON actor.id=rule.reviewed_by_user_id AND actor.username=rule.reviewed_by
            WHERE
            """+ScopedAiSql.CURRENT_OWNER+" AND "+ScopedAiReplyRuleService.CONFIGURE+" AND "+ScopedAiSourceService.CURRENT_ORIGIN+"""
                AND owner.status IN ('pending','open')
                AND EXISTS(SELECT 1 FROM user_authorities permission WHERE permission.user_id=actor.id AND permission.authority='PAGE_KNOWLEDGE_BASE')
                AND incoming.message_type='text' AND NULLIF(btrim(incoming.attachment),'') IS NULL
                AND length(incoming.message_text) BETWEEN 1 AND 500 AND length(source.body) BETWEEN 1 AND 4000
                AND destination.is_active=TRUE AND btrim(coalesce(destination.token,''))<>''
                AND destination.platform IN ('telegram','vk','max')
                AND (destination.platform<>'vk' OR owner.user_id BETWEEN 1 AND 2147483647)
                AND NOT EXISTS(SELECT 1 FROM ticket_ai_scoped_control control
                    WHERE control.ticket_public_id=owner.ticket_public_id AND control.scope_business_key=owner.business_id
                      AND (control.ai_disabled OR control.auto_reply_blocked OR NOT (
            """+ScopedAiSql.CONTROL_MATCHES_OWNER+"""
                )))
                AND 1=(SELECT COUNT(*) FROM chat_history history WHERE
            """+HISTORY+"""
                )
                AND NOT EXISTS(SELECT 1 FROM chat_history later WHERE later.ticket_owner_user_id=owner.user_id
                    AND later.ticket_id=owner.ticket_id AND later.ticket_public_id=owner.ticket_public_id AND later.id>history.id)
                AND :maxReplies>(SELECT COUNT(*) FROM ai_scoped_text_replies previous
                    WHERE previous.ticket_user_id=owner.user_id AND previous.ticket_id=owner.ticket_id
                      AND previous.ticket_public_id=owner.ticket_public_id AND previous.business_id=owner.business_id
                      AND previous.incoming_event_id<>:eventId AND previous.state IN ('SENDING','SENT','UNKNOWN')
                      AND previous.updated_at>CURRENT_TIMESTAMP-(:windowMinutes*INTERVAL '1 minute'))
                AND NOT EXISTS(SELECT 1 FROM ai_scoped_text_replies previous
                    WHERE previous.ticket_user_id=owner.user_id AND previous.ticket_id=owner.ticket_id
                      AND previous.ticket_public_id=owner.ticket_public_id AND previous.business_id=owner.business_id
                      AND previous.incoming_event_id<>:eventId AND previous.state IN ('SENDING','SENT','UNKNOWN')
                      AND previous.updated_at>CURRENT_TIMESTAMP-(:cooldownSeconds*INTERVAL '1 second'))
            FOR SHARE OF owner,root,destination,incoming,history,rule,source,actor
            """,args(scope,eventId),(rs,n)->{
                var channel=new Channel();channel.setId(rs.getLong("id"));channel.setToken(rs.getString("token"));
                channel.setPlatform(rs.getString("platform"));channel.setPlatformConfig(rs.getString("platform_config"));
                channel.setDeliverySettings(rs.getString("delivery_settings"));channel.setCredentialId(rs.getObject("credential_id",Long.class));channel.setActive(true);
                return new Candidate(rs.getString("rule_key"),rs.getLong("rule_revision"),rs.getLong("source_id"),rs.getLong("source_revision"),
                    rs.getLong("history_id"),rs.getLong("business_work_revision"),rs.getString("control_stamp"),channel,rs.getString("destination_sha"),rs.getString("response_sha"),rs.getString("body"));
            });
        return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
    }
    private boolean matches(UiEventTicketScope scope,long eventId,Candidate c) {
        return Boolean.TRUE.equals(sql.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM ai_scoped_text_replies WHERE incoming_event_id=:eventId AND state='SENDING'
                AND ticket_user_id=:ticketUserId AND ticket_id=:ticketId AND ticket_public_id=:publicId AND business_id=:businessId
                AND channel_id=:channelId AND destination_sha256=:destination AND input_history_id=:historyId
                AND owner_revision=:ownerRevision AND control_stamp=:controlStamp
                AND rule_key=:ruleKey AND rule_revision=:ruleRevision AND source_id=:sourceId
                AND source_revision=:sourceRevision AND response_sha256=:response)
            """,snapshotArgs(scope,eventId,c),Boolean.class));
    }
    private boolean same(Candidate a,Candidate b) {
        return a.ruleKey().equals(b.ruleKey()) && a.ruleRevision()==b.ruleRevision() && a.sourceId()==b.sourceId()
            && a.sourceRevision()==b.sourceRevision() && a.historyId()==b.historyId()
            && a.ownerRevision()==b.ownerRevision() && a.controlStamp().equals(b.controlStamp())
            && a.channel().getId().equals(b.channel().getId()) && a.destination().equals(b.destination()) && a.response().equals(b.response());
    }
    private void finish(UiEventTicketScope scope,long eventId,String state,Long historyId) {
        if(sql.update("""
            UPDATE ai_scoped_text_replies SET state=:state,chat_history_id=:historyId,updated_at=CURRENT_TIMESTAMP
             WHERE incoming_event_id=:eventId AND state='SENDING' AND ticket_user_id=:ticketUserId
               AND ticket_id=:ticketId AND ticket_public_id=:publicId AND business_id=:businessId
            """,args(scope,eventId).addValue("state",state).addValue("historyId",historyId,java.sql.Types.BIGINT))!=1)
            throw new IllegalStateException("AI reply claim unavailable");
        audit(scope,eventId,state);
        sql.update("""
            INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,message_text,created_at,ticket_user_id,ticket_public_id,
                ticket_scope_kind,ticket_business_id)
            SELECT :outId,'ai_reply_changed',ticket_id,channel_id,state,CURRENT_TIMESTAMP,ticket_user_id,ticket_public_id,
                'BUSINESS',business_id FROM ai_scoped_text_replies
             WHERE incoming_event_id=:eventId AND ticket_user_id=:ticketUserId AND ticket_id=:ticketId
               AND ticket_public_id=:publicId AND business_id=:businessId AND state=:state
            """,args(scope,eventId).addValue("outId",events.nextEventId()).addValue("state",state));
    }
    private void audit(UiEventTicketScope scope,long eventId,String state) {
        sql.update("""
            INSERT INTO ticket_ai_scoped_events(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,
                event_type,decision_reason)
            VALUES(:ticketUserId,:ticketId,:publicId,'BUSINESS',:businessId,'auto_reply_delivery',:reason)
            """,args(scope,eventId).addValue("reason","event="+eventId+";state="+state));
    }
    private boolean enabled(UiEventTicketScope scope,long eventId) {
        return scope!=null && scope.isValid() && "BUSINESS".equals(scope.scopeKind()) && eventId>0
            && runtimeEnabled();
    }
    private boolean runtimeEnabled() {
        return flags.isFoundationEnabled() && flags.isUiSelector() && config.isAgentEnabled()
            && "auto_reply".equals(config.resolveAgentMode());
    }
    private record Queued(long eventId,UiEventTicketScope scope) { }
    private MapSqlParameterSource args(UiEventTicketScope scope,long eventId) {
        var limits=config.scopedAutoReplyLimits();
        return new MapSqlParameterSource("eventId",eventId).addValue("ticketUserId",scope.ticketUserId())
            .addValue("ticketId",scope.ticketId()).addValue("publicId",scope.ticketPublicId()).addValue("businessId",scope.businessId())
            .addValue("scopeKind",scope.scopeKind()).addValue("strict",flags.isStrictReadScope())
            .addValue("maxReplies",limits.maxReplies()).addValue("windowMinutes",limits.windowMinutes()).addValue("cooldownSeconds",limits.cooldownSeconds());
    }
    private MapSqlParameterSource snapshotArgs(UiEventTicketScope scope,long eventId,Candidate c) {
        return args(scope,eventId).addValue("channelId",c.channel().getId()).addValue("destination",c.destination())
            .addValue("historyId",c.historyId()).addValue("ownerRevision",c.ownerRevision()).addValue("controlStamp",c.controlStamp()).addValue("ruleKey",c.ruleKey()).addValue("ruleRevision",c.ruleRevision())
            .addValue("sourceId",c.sourceId()).addValue("sourceRevision",c.sourceRevision()).addValue("response",c.response());
    }
    private record Candidate(String ruleKey,long ruleRevision,long sourceId,long sourceRevision,long historyId,
                             long ownerRevision,String controlStamp,Channel channel,String destination,String response,String body) {
        @Override public String toString() { return "ScopedAiReplyCandidate[sourceId="+sourceId+",revision="+sourceRevision+"]"; }
    }
}
