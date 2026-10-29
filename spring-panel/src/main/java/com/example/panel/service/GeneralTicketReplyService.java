package com.example.panel.service;

import org.springframework.security.core.Authentication;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable single-attempt GENERAL text delivery; never calls legacy reply/lifecycle orchestration. */
@Service
public class GeneralTicketReplyService {
    private static final String DIGEST = """
        encode(sha256(convert_to(jsonb_build_array(destination.id,destination.token,destination.platform,
            destination.platform_config,destination.delivery_settings,destination.credential_id)::text,'UTF8')),'hex')
        """;
    private static final String OWNER = """
        ticket.user_id=:ownerId AND ticket.ticket_id=:ticketId AND ticket.ticket_public_id=:publicId
        AND ticket.is_general=TRUE AND ticket.business_id IS NULL
        AND 1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)
        """;
    private final NamedParameterJdbcTemplate sql;
    private final MultibusinessFeatureFlags flags;
    private final DialogReplyTransportService transport;
    private final UiEventOutboxAppendService ids;
    private final TransactionTemplate transaction;

    public GeneralTicketReplyService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,
            DialogReplyTransportService transport,UiEventOutboxAppendService ids) {
        this.sql=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.transport=transport;this.ids=ids;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public static boolean validInput(String key,String message) {
        return key!=null && key.matches("[a-f0-9]{32}") && message!=null && !message.isBlank() && message.length()<=4000
            && message.codePoints().noneMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t')
            && wellFormed(message);
    }
    private static boolean wellFormed(String message) {
        for (int i=0;i<message.length();i++) {
            char c=message.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i>=message.length() || !Character.isLowSurrogate(message.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    public Optional<String> destinationFingerprint(GeneralTicketEventScope scope,Authentication authentication,long revision) {
        return destinationFingerprint(scope,GeneralTicketActor.from(authentication),revision);
    }
    private Optional<String> destinationFingerprint(GeneralTicketEventScope scope,GeneralTicketActor identity,long revision) {
        try { return transaction.execute(tx->capture(scope,identity,revision).map(Destination::digest)); }
        catch (RuntimeException ex) { return Optional.empty(); }
    }
    public boolean submit(GeneralTicketEventScope scope,Authentication authentication,long revision,String key,String message,String expectedDestination) {
        return submit(scope,GeneralTicketActor.from(authentication),revision,key,message,expectedDestination);
    }
    private boolean submit(GeneralTicketEventScope scope,GeneralTicketActor identity,long revision,String key,String message,String expectedDestination) {
        if (!prepare(scope,identity,revision,key,message,expectedDestination)) return false;
        dispatch(key);
        return true; // Accepted is not a promise of provider delivery; the persisted state is shown separately.
    }
    boolean prepare(GeneralTicketEventScope scope,Authentication authentication,long revision,String key,String message,String expectedDestination) {
        return prepare(scope,GeneralTicketActor.from(authentication),revision,key,message,expectedDestination);
    }
    private boolean prepare(GeneralTicketEventScope scope,GeneralTicketActor identity,long revision,String key,String message,String expectedDestination) {
        if (!validInput(key,message) || revision<0 || expectedDestination==null || !expectedDestination.matches("[a-f0-9]{64}")) return false;
        try { return Boolean.TRUE.equals(transaction.execute(tx->{
            var target=capture(scope,identity,revision);
            if (target.isEmpty() || !expectedDestination.equals(target.get().digest())) return false;
            var destination=target.get();
            var args=params(scope,identity).addValue("revision",revision).addValue("key",key).addValue("message",message)
                .addValue("actorId",destination.actorId()).addValue("channelId",destination.channel().getId()).addValue("digest",destination.digest());
            int inserted=sql.update("""
                INSERT INTO general_ticket_text_replies(request_key,ticket_user_id,ticket_id,ticket_public_id,
                    owner_revision,actor_user_id,actor,channel_id,destination_sha256,message)
                VALUES(:key,:ownerId,:ticketId,:publicId,:revision,:actorId,:identity,:channelId,:digest,:message)
                ON CONFLICT(request_key) DO NOTHING
                """,args);
            if (inserted==1) { audit(key,"QUEUED",destination.actorId());return true; }
            return Boolean.TRUE.equals(sql.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM general_ticket_text_replies WHERE request_key=:key
                    AND ticket_user_id=:ownerId AND ticket_id=:ticketId AND ticket_public_id=:publicId
                    AND owner_revision=:revision AND actor_user_id=:actorId AND message=:message
                    AND channel_id=:channelId AND destination_sha256=:digest)
                """,args,Boolean.class));
        })); } catch (RuntimeException ex) { return false; }
    }
    void dispatch(String key) {
        Optional<Attempt> claimed;
        try { claimed=transaction.execute(tx->{
            var rows=sql.query("""
                UPDATE general_ticket_text_replies SET state='SENDING',updated_at=CURRENT_TIMESTAMP
                 WHERE request_key=:key AND state='QUEUED' RETURNING *
                """,new MapSqlParameterSource("key",key),(rs,row)->new Attempt(
                    new GeneralTicketEventScope(rs.getLong("ticket_user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id")),
                    rs.getString("actor"),rs.getLong("actor_user_id"),rs.getLong("owner_revision"),
                    rs.getLong("channel_id"),rs.getString("destination_sha256"),rs.getString("message")));
            if (rows.isEmpty()) return Optional.<Attempt>empty();
            audit(key,"SENDING",rows.get(0).actorId());return Optional.of(rows.get(0));
        }); } catch (RuntimeException ex) { return; }
        if (claimed==null || claimed.isEmpty()) return;
        var attempt=claimed.get();
        try { transaction.executeWithoutResult(tx->{
            var target=capture(attempt.scope(),GeneralTicketActor.stored(attempt.actorId(),attempt.actor()),attempt.revision());
            if (target.isEmpty() || !attempt.matches(target.get())) {
                finish(key,attempt,"CANCELLED",null);return;
            }
            var destination=target.get();
            var result=transport.sendTextGuarded(destination.channel(),attempt.scope().ticketUserId(),attempt.message(),null,
                ()->capture(attempt.scope(),GeneralTicketActor.stored(attempt.actorId(),attempt.actor()),attempt.revision()).filter(attempt::matches).isPresent());
            if (result==null || !result.success()) {
                finish(key,attempt,result!=null && "scope_denied".equals(result.classification())?"CANCELLED":"UNKNOWN",null);return;
            }
            var args=params(attempt.scope(),GeneralTicketActor.stored(attempt.actorId(),attempt.actor())).addValue("message",attempt.message())
                .addValue("channelId",attempt.channelId()).addValue("providerId",result.telegramMessageId());
            Long historyId=sql.queryForObject("""
                INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,message_type,
                    channel_id,tg_message_id,ticket_owner_user_id,ticket_public_id)
                VALUES(:ownerId,'operator',:message,CURRENT_TIMESTAMP,:ticketId,'operator_message',
                    :channelId,:providerId,:ownerId,:publicId) RETURNING id
                """,args,Long.class);
            finish(key,attempt,"SENT",historyId);
        }); } catch (RuntimeException ex) {
            // The provider might already have accepted the message. Never return this claim to QUEUED.
            try { transaction.executeWithoutResult(tx->finish(key,attempt,"UNKNOWN",null)); }
            catch (RuntimeException ignored) { /* SENDING also requires manual reconciliation, never auto-retry. */ }
        }
    }
    public List<Reply> recent(GeneralTicketEventScope scope,Authentication authentication) {
        return recent(scope,GeneralTicketActor.from(authentication));
    }
    private List<Reply> recent(GeneralTicketEventScope scope,GeneralTicketActor identity) {
        if (!enabled() || scope==null || !scope.isValid() || identity==null || !identity.valid()) return List.of();
        try { return sql.query("""
            SELECT reply.request_key,reply.message,reply.state,reply.created_at
            FROM general_ticket_text_replies reply JOIN tickets ticket
              ON ticket.user_id=reply.ticket_user_id AND ticket.ticket_id=reply.ticket_id AND ticket.ticket_public_id=reply.ticket_public_id
            CROSS JOIN users account WHERE account.id=:authenticatedActorId AND account.username=:identity AND
            """+OWNER+" AND "+GeneralTicketAccessService.READ_ACCOUNT+"""
            ORDER BY reply.created_at DESC,reply.request_key LIMIT 20
            """,params(scope,identity),(rs,row)->new Reply(rs.getString("request_key"),rs.getString("message"),
                rs.getString("state"),rs.getObject("created_at",OffsetDateTime.class)));
        } catch (RuntimeException ex) { return List.of(); }
    }
    private Optional<Destination> capture(GeneralTicketEventScope scope,GeneralTicketActor identity,long revision) {
        if (!enabled() || scope==null || !scope.isValid() || identity==null || !identity.valid()) return Optional.empty();
        var rows=sql.query("""
            SELECT account.id AS actor_id,destination.id,destination.token,destination.platform,
                destination.platform_config,destination.delivery_settings,destination.credential_id,
            """+DIGEST+"""
                AS digest FROM tickets ticket
            JOIN messages root ON root.group_msg_id=ticket.group_msg_id AND root.user_id=ticket.user_id
                AND root.ticket_id=ticket.ticket_id AND root.channel_id=ticket.channel_id
            JOIN channels destination ON destination.id=ticket.channel_id
            JOIN users account ON account.id=:authenticatedActorId AND account.username=:identity
            JOIN general_ticket_read_grants read_access ON read_access.user_id=account.id AND read_access.is_active=TRUE
            JOIN general_ticket_operate_grants operation ON operation.user_id=account.id AND operation.is_active=TRUE
            WHERE
            """+OWNER+" AND "+GeneralTicketAccessService.CURRENT_ACCOUNT+"""
            AND ticket.general_work_revision=:revision AND ticket.status IN ('pending','open')
            AND destination.is_active=TRUE AND btrim(coalesce(destination.token,''))<>''
            AND destination.platform IN ('telegram','vk','max')
            AND (destination.platform<>'vk' OR ticket.user_id BETWEEN 1 AND 2147483647)
            FOR SHARE OF ticket,root,destination,account,read_access,operation
            """,params(scope,identity).addValue("revision",revision),(rs,row)->{
                var channel=new Channel();channel.setId(rs.getLong("id"));channel.setToken(rs.getString("token"));
                channel.setPlatform(rs.getString("platform"));channel.setPlatformConfig(rs.getString("platform_config"));
                channel.setDeliverySettings(rs.getString("delivery_settings"));channel.setCredentialId(rs.getObject("credential_id",Long.class));channel.setActive(true);
                return new Destination(rs.getLong("actor_id"),channel,rs.getString("digest"));
            });
        return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
    }
    private void finish(String key,Attempt attempt,String state,Long historyId) {
        var args=new MapSqlParameterSource("key",key).addValue("state",state).addValue("historyId",historyId,java.sql.Types.BIGINT);
        if (sql.update("""
            UPDATE general_ticket_text_replies SET state=:state,chat_history_id=:historyId,updated_at=CURRENT_TIMESTAMP
             WHERE request_key=:key AND state='SENDING'
            """,args)!=1) throw new IllegalStateException("GENERAL reply claim is unavailable");
        audit(key,state,attempt.actorId());
        sql.update("""
            INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,
                ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
            VALUES(:eventId,'operator_reply_changed',:ticketId,:channelId,CURRENT_TIMESTAMP,:ownerId,:publicId,'GENERAL',NULL)
            """,params(attempt.scope(),GeneralTicketActor.stored(attempt.actorId(),attempt.actor())).addValue("eventId",ids.nextEventId()).addValue("channelId",attempt.channelId()));
    }
    private void audit(String key,String state,long actorId) {
        sql.update("INSERT INTO general_ticket_reply_events(request_key,state,actor_user_id) VALUES(:key,:state,:actorId)",
            new MapSqlParameterSource("key",key).addValue("state",state).addValue("actorId",actorId));
    }
    private boolean enabled() { return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue(); }
    private MapSqlParameterSource params(GeneralTicketEventScope scope,GeneralTicketActor identity) {
        return new MapSqlParameterSource("ownerId",scope.ticketUserId()).addValue("ticketId",scope.ticketId())
            .addValue("publicId",scope.ticketPublicId()).addValue("identity",identity.username()).addValue("authenticatedActorId",identity.userId());
    }
    public record Reply(String requestKey,String message,String state,OffsetDateTime createdAt) { }
    private record Destination(long actorId,Channel channel,String digest) {
        @Override public String toString() { return "GeneralReplyDestination"; }
    }
    private record Attempt(GeneralTicketEventScope scope,String actor,long actorId,long revision,long channelId,String digest,String message) {
        boolean matches(Destination destination) {
            return actorId==destination.actorId() && channelId==destination.channel().getId() && digest.equals(destination.digest());
        }
        @Override public String toString() { return "GeneralReplyAttempt"; }
    }
}
