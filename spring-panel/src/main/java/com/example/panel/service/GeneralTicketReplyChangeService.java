package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Single-attempt Telegram changes for confirmed R136 GENERAL text replies only. */
@Service
public class GeneralTicketReplyChangeService {
    private static final String DESTINATION="""
        encode(sha256(convert_to(jsonb_build_array(destination.id,destination.token,destination.platform,
            destination.platform_config,destination.delivery_settings,destination.credential_id)::text,'UTF8')),'hex')
        """;
    private static final String CONTENT="""
        encode(sha256(convert_to(jsonb_build_array(history.id,history.message,history.edited_at,
            history.deleted_at,history.tg_message_id)::text,'UTF8')),'hex')
        """;
    private static final String OWNER="""
        ticket.user_id=:ownerId AND ticket.ticket_id=:ticketId AND ticket.ticket_public_id=:publicId
        AND ticket.is_general=TRUE AND ticket.business_id IS NULL
        AND 1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)
        """;
    private static final String TARGET="""
        FROM tickets ticket
        JOIN messages root ON root.group_msg_id=ticket.group_msg_id AND root.user_id=ticket.user_id
            AND root.ticket_id=ticket.ticket_id AND root.channel_id=ticket.channel_id
        JOIN general_ticket_text_replies reply ON reply.ticket_user_id=ticket.user_id
            AND reply.ticket_id=ticket.ticket_id AND reply.ticket_public_id=ticket.ticket_public_id
            AND reply.state='SENT' AND reply.channel_id=ticket.channel_id
        JOIN chat_history history ON history.id=reply.chat_history_id AND history.user_id=ticket.user_id
            AND history.ticket_owner_user_id=ticket.user_id AND history.ticket_id=ticket.ticket_id
            AND history.ticket_public_id=ticket.ticket_public_id AND history.channel_id=ticket.channel_id
            AND history.sender='operator' AND history.message_type='operator_message' AND history.attachment IS NULL
            AND history.tg_message_id>0
        JOIN channels destination ON destination.id=ticket.channel_id
        JOIN users account ON lower(account.username)=lower(:identity)
        JOIN general_ticket_read_grants read_access ON read_access.user_id=account.id AND read_access.is_active=TRUE
        JOIN general_ticket_operate_grants operation ON operation.user_id=account.id AND operation.is_active=TRUE
        WHERE
        """+OWNER+" AND "+GeneralTicketAccessService.CURRENT_ACCOUNT+"""
        AND ticket.general_work_revision=:revision AND ticket.status IN ('pending','open')
        AND destination.is_active=TRUE AND destination.platform='telegram' AND btrim(coalesce(destination.token,''))<>''
        AND reply.destination_sha256=
        """+DESTINATION+"""
        AND 1=(SELECT COUNT(*) FROM chat_history duplicate WHERE duplicate.channel_id=history.channel_id
            AND duplicate.user_id=history.user_id AND duplicate.tg_message_id=history.tg_message_id)
        """;
    private final NamedParameterJdbcTemplate sql;
    private final MultibusinessFeatureFlags flags;
    private final DialogReplyTransportService transport;
    private final UiEventOutboxAppendService ids;
    private final TransactionTemplate transaction;

    public GeneralTicketReplyChangeService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,
            DialogReplyTransportService transport,UiEventOutboxAppendService ids) {
        this.sql=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.transport=transport;this.ids=ids;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public static boolean validInput(String key,String sourceKey,String expected,String action,String message) {
        return key!=null && key.matches("[a-f0-9]{32}") && sourceKey!=null && sourceKey.matches("[a-f0-9]{32}")
            && expected!=null && expected.matches("[a-f0-9]{64}")
            && ("DELETE".equals(action)?"".equals(message):"EDIT".equals(action)&&GeneralTicketReplyService.validInput(key,message));
    }
    public List<Target> targets(GeneralTicketEventScope scope,String identity,long revision) {
        if (!allowed(scope,identity,revision)) return List.of();
        try { return sql.query("SELECT reply.request_key,history.message,"+CONTENT+" AS fingerprint "+TARGET+"""
            AND history.deleted_at IS NULL AND NOT EXISTS(SELECT 1 FROM general_ticket_reply_changes inflight
                WHERE inflight.source_request_key=reply.request_key AND inflight.state IN ('QUEUED','SENDING','UNKNOWN'))
            ORDER BY reply.created_at DESC,reply.request_key LIMIT 20
            """,params(scope,identity,revision),(rs,row)->new Target(rs.getString("request_key"),rs.getString("message"),
                rs.getString("fingerprint"),UUID.randomUUID().toString().replace("-","")));
        } catch (RuntimeException ex) { return List.of(); }
    }
    public List<Change> recent(GeneralTicketEventScope scope,String identity) {
        if (!allowed(scope,identity,0)) return List.of();
        try { return sql.query("""
            SELECT command.action,command.state,command.created_at FROM general_ticket_reply_changes command
            JOIN tickets ticket ON ticket.user_id=command.ticket_user_id AND ticket.ticket_id=command.ticket_id
                AND ticket.ticket_public_id=command.ticket_public_id
            JOIN users account ON lower(account.username)=lower(:identity) WHERE
            """+OWNER+" AND "+GeneralTicketAccessService.READ_ACCOUNT+"""
            ORDER BY command.created_at DESC,command.request_key LIMIT 20
            """,params(scope,identity,0),(rs,row)->new Change(rs.getString("action"),rs.getString("state"),
                rs.getObject("created_at",OffsetDateTime.class)));
        } catch (RuntimeException ex) { return List.of(); }
    }
    public boolean submit(GeneralTicketEventScope scope,String identity,long revision,String key,String sourceKey,
            String expected,String action,String message) {
        if (!prepare(scope,identity,revision,key,sourceKey,expected,action,message)) return false;
        dispatch(key);return true;
    }
    boolean prepare(GeneralTicketEventScope scope,String identity,long revision,String key,String sourceKey,
            String expected,String action,String message) {
        if (!allowed(scope,identity,revision) || !validInput(key,sourceKey,expected,action,message)) return false;
        try { return Boolean.TRUE.equals(transaction.execute(tx->{
            var target=capture(scope,identity,revision,sourceKey);
            if (target.isEmpty()) return false;
            var t=target.get();
            var args=params(scope,identity,revision).addValue("key",key).addValue("sourceKey",sourceKey)
                .addValue("expected",expected).addValue("action",action).addValue("message",message)
                .addValue("actorId",t.actorId()).addValue("channelId",t.channel().getId()).addValue("digest",t.digest())
                .addValue("historyId",t.historyId()).addValue("providerId",t.providerId());
            if (Boolean.TRUE.equals(sql.queryForObject("SELECT EXISTS(SELECT 1 FROM general_ticket_reply_changes WHERE request_key=:key)",args,Boolean.class))) {
                return Boolean.TRUE.equals(sql.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM general_ticket_reply_changes WHERE request_key=:key AND source_request_key=:sourceKey
                        AND ticket_user_id=:ownerId AND ticket_id=:ticketId AND ticket_public_id=:publicId AND owner_revision=:revision
                        AND actor_user_id=:actorId AND channel_id=:channelId AND destination_sha256=:digest
                        AND chat_history_id=:historyId AND provider_message_id=:providerId AND expected_sha256=:expected
                        AND action=:action AND message=:message)
                    """,args,Boolean.class));
            }
            if (t.deleted() || !expected.equals(t.fingerprint()) || ("EDIT".equals(action)&&message.equals(t.message()))) return false;
            if (sql.update("""
                INSERT INTO general_ticket_reply_changes(request_key,source_request_key,ticket_user_id,ticket_id,ticket_public_id,
                    owner_revision,actor_user_id,actor,channel_id,destination_sha256,chat_history_id,provider_message_id,expected_sha256,action,message)
                VALUES(:key,:sourceKey,:ownerId,:ticketId,:publicId,:revision,:actorId,:identity,:channelId,:digest,:historyId,:providerId,:expected,:action,:message)
                """,args)!=1) throw new IllegalStateException("GENERAL change was not recorded");
            audit(key,"QUEUED",t.actorId());return true;
        })); } catch (RuntimeException ex) { return false; }
    }
    void dispatch(String key) {
        Optional<Attempt> claimed;
        try { claimed=transaction.execute(tx->{
            var rows=sql.query("UPDATE general_ticket_reply_changes SET state='SENDING',updated_at=CURRENT_TIMESTAMP WHERE request_key=:key AND state='QUEUED' RETURNING *",
                new MapSqlParameterSource("key",key),(rs,row)->new Attempt(
                    new GeneralTicketEventScope(rs.getLong("ticket_user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id")),
                    rs.getString("actor"),rs.getLong("actor_user_id"),rs.getLong("owner_revision"),rs.getString("source_request_key"),
                    rs.getLong("channel_id"),rs.getString("destination_sha256"),rs.getLong("chat_history_id"),rs.getLong("provider_message_id"),
                    rs.getString("expected_sha256"),rs.getString("action"),rs.getString("message")));
            if (rows.isEmpty()) return Optional.<Attempt>empty();
            audit(key,"SENDING",rows.get(0).actorId());return Optional.of(rows.get(0));
        }); } catch (RuntimeException ex) { return; }
        if (claimed==null || claimed.isEmpty()) return;
        var attempt=claimed.get();
        try { transaction.executeWithoutResult(tx->{
            var target=capture(attempt.scope(),attempt.actor(),attempt.revision(),attempt.sourceKey());
            if (target.isEmpty() || !attempt.matches(target.get())) { finish(key,attempt,"CANCELLED");return; }
            var t=target.get();
            java.util.function.BooleanSupplier guard=()->capture(attempt.scope(),attempt.actor(),attempt.revision(),attempt.sourceKey())
                .filter(attempt::matches).isPresent();
            var result="EDIT".equals(attempt.action())
                ?transport.editTelegramMessageGuarded(t.channel(),attempt.scope().ticketUserId(),t.providerId(),attempt.message(),guard)
                :transport.deleteTelegramMessageGuarded(t.channel(),attempt.scope().ticketUserId(),t.providerId(),guard);
            if (result==null || !result.success()) {
                finish(key,attempt,result!=null&&"scope_denied".equals(result.classification())?"CANCELLED":"UNKNOWN");return;
            }
            String update="EDIT".equals(attempt.action())?"message=:message,edited_at=clock_timestamp()":"deleted_at=clock_timestamp()";
            if (sql.update("UPDATE chat_history SET "+update+" WHERE id=:historyId AND ticket_owner_user_id=:ownerId AND ticket_id=:ticketId AND ticket_public_id=:publicId",
                params(attempt.scope(),attempt.actor(),attempt.revision()).addValue("historyId",t.historyId()).addValue("message",attempt.message()))!=1)
                throw new IllegalStateException("GENERAL history change was not recorded");
            finish(key,attempt,"APPLIED");
        }); } catch (RuntimeException ex) {
            // A provider call cannot be rolled back. Ambiguity blocks this message; never retry automatically.
            try { transaction.executeWithoutResult(tx->finish(key,attempt,"UNKNOWN")); }
            catch (RuntimeException ignored) { /* SENDING remains a durable reconciliation gate. */ }
        }
    }
    private Optional<Captured> capture(GeneralTicketEventScope scope,String identity,long revision,String sourceKey) {
        if (!allowed(scope,identity,revision)) return Optional.empty();
        var rows=sql.query("SELECT account.id AS actor_id,destination.*,history.id AS history_id,history.tg_message_id,history.message,history.deleted_at,"
            +DESTINATION+" AS digest,"+CONTENT+" AS fingerprint "+TARGET+"""
            AND reply.request_key=:sourceKey
            FOR UPDATE OF reply,history FOR SHARE OF ticket,root,destination,account,read_access,operation
            """,params(scope,identity,revision).addValue("sourceKey",sourceKey),(rs,row)->{
                var channel=new Channel();channel.setId(rs.getLong("id"));channel.setToken(rs.getString("token"));
                channel.setPlatform(rs.getString("platform"));channel.setPlatformConfig(rs.getString("platform_config"));
                channel.setDeliverySettings(rs.getString("delivery_settings"));channel.setCredentialId(rs.getObject("credential_id",Long.class));channel.setActive(true);
                return new Captured(rs.getLong("actor_id"),channel,rs.getString("digest"),rs.getLong("history_id"),rs.getLong("tg_message_id"),
                    rs.getString("fingerprint"),rs.getString("message"),rs.getObject("deleted_at")!=null);
            });
        return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
    }
    private void finish(String key,Attempt attempt,String state) {
        if (sql.update("UPDATE general_ticket_reply_changes SET state=:state,updated_at=CURRENT_TIMESTAMP WHERE request_key=:key AND state='SENDING'",
            new MapSqlParameterSource("key",key).addValue("state",state))!=1) throw new IllegalStateException("GENERAL change claim unavailable");
        audit(key,state,attempt.actorId());
        if (sql.update("""
            INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
            VALUES(:eventId,'operator_reply_changed',:ticketId,:channelId,CURRENT_TIMESTAMP,:ownerId,:publicId,'GENERAL',NULL)
            """,params(attempt.scope(),attempt.actor(),attempt.revision()).addValue("eventId",ids.nextEventId()).addValue("channelId",attempt.channelId()))!=1)
            throw new IllegalStateException("GENERAL change event unavailable");
    }
    private void audit(String key,String state,long actorId) {
        if (sql.update("INSERT INTO general_ticket_reply_change_events(request_key,state,actor_user_id) VALUES(:key,:state,:actorId)",
            new MapSqlParameterSource("key",key).addValue("state",state).addValue("actorId",actorId))!=1)
            throw new IllegalStateException("GENERAL change audit unavailable");
    }
    private boolean allowed(GeneralTicketEventScope scope,String identity,long revision) {
        return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue()&&scope!=null&&scope.isValid()
            &&identity!=null&&!identity.isBlank()&&revision>=0;
    }
    private MapSqlParameterSource params(GeneralTicketEventScope scope,String identity,long revision) {
        return new MapSqlParameterSource("ownerId",scope.ticketUserId()).addValue("ticketId",scope.ticketId())
            .addValue("publicId",scope.ticketPublicId()).addValue("identity",identity).addValue("revision",revision);
    }
    public record Target(String sourceKey,String message,String fingerprint,String requestKey) { }
    public record Change(String action,String state,OffsetDateTime createdAt) { }
    private record Captured(long actorId,Channel channel,String digest,long historyId,long providerId,String fingerprint,String message,boolean deleted) {
        @Override public String toString() { return "GeneralReplyChangeTarget"; }
    }
    private record Attempt(GeneralTicketEventScope scope,String actor,long actorId,long revision,String sourceKey,
            long channelId,String digest,long historyId,long providerId,String fingerprint,String action,String message) {
        boolean matches(Captured t) {
            return !t.deleted()&&actorId==t.actorId()&&channelId==t.channel().getId()&&digest.equals(t.digest())
                &&historyId==t.historyId()&&providerId==t.providerId()&&fingerprint.equals(t.fingerprint());
        }
        @Override public String toString() { return "GeneralReplyChangeAttempt"; }
    }
}
