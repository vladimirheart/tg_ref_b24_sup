package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit GENERAL roster, independent of business/legacy membership, assignment and client delivery. */
@Service
public class GeneralTicketParticipantService {
    private static final String ACTOR=" CROSS JOIN users account ";
    private static final String OWNER=" ticket.user_id=:owner AND ticket.ticket_id=:raw AND ticket.ticket_public_id=:public"
        +" AND ticket.is_general=TRUE AND ticket.business_id IS NULL"
        +" AND 1=(SELECT count(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)"
        +" AND lower(account.username)=lower(:identity) ";
    private static final String READ=" AND "+GeneralTicketAccessService.READ_ACCOUNT;
    private static final String WRITE=" AND "+GeneralTicketAccessService.OPERATE_ACCOUNT
        +" AND lower(btrim(account.username))<>'ai_agent' AND ticket.status IN ('pending','open') AND ticket.general_work_revision=:ownerRevision ";
    private static final String TUPLE=" ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id)"
        +"=ROW(ticket.user_id,ticket.ticket_id,ticket.ticket_public_id) ";
    private static final String REVISION=" (SELECT COALESCE(max(entry.revision),0) FROM general_ticket_participant_events entry WHERE "+TUPLE+") ";
    private static final String TARGET=GeneralTicketAccessService.READ_ACCOUNT.replace("account.","candidate.")
        +" AND lower(btrim(candidate.username)) NOT IN ('','ai_agent') ";
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService ids;
    private final LegacyTicketIdConcurrencyGuard guard;
    private final TransactionTemplate transaction;

    public GeneralTicketParticipantService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,UiEventOutboxAppendService ids) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.ids=ids;
        guard=new LegacyTicketIdConcurrencyGuard(jdbc);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<Long> revision(GeneralTicketEventScope scope,String identity) {
        if(!valid(scope,identity))return Optional.empty();
        try {
            var rows=jdbc.query("SELECT "+REVISION+" FROM tickets ticket"+ACTOR+" WHERE "+OWNER+READ,args(scope,identity),
                (rs,row)->rs.getLong(1));
            return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
        }catch(DataAccessException ex){return Optional.empty();}
    }
    public List<Member> members(GeneralTicketEventScope scope,String identity) {
        if(!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT entry.member_user_id,entry.member,candidate.username,("+TARGET+") AS eligible"
            +" FROM tickets ticket"+ACTOR+" JOIN general_ticket_participants entry ON "+TUPLE
            +" LEFT JOIN users candidate ON candidate.id=entry.member_user_id WHERE "+OWNER+READ
            +" ORDER BY entry.revision,entry.member_user_id LIMIT 200",args(scope,identity),
            (rs,row)->new Member(rs.getLong("member_user_id"),rs.getString("member"),rs.getString("username"),rs.getBoolean("eligible")));
        }catch(DataAccessException ex){return List.of();}
    }
    public List<Candidate> candidates(GeneralTicketEventScope scope,String identity,long ownerRevision) {
        if(!valid(scope,identity)||ownerRevision<0)return List.of();
        try{return jdbc.query("SELECT candidate.id,candidate.username FROM tickets ticket"+ACTOR+" CROSS JOIN users candidate"
            +" WHERE "+OWNER+WRITE+" AND "+TARGET
            +" AND NOT EXISTS(SELECT 1 FROM general_ticket_participants entry WHERE "+TUPLE+" AND entry.member_user_id=candidate.id)"
            +" ORDER BY lower(candidate.username),candidate.id LIMIT 200",args(scope,identity).addValue("ownerRevision",ownerRevision),
            (rs,row)->new Candidate(rs.getLong("id"),rs.getString("username")));
        }catch(DataAccessException ex){return List.of();}
    }
    public List<Event> recent(GeneralTicketEventScope scope,String identity) {
        if(!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT entry.* FROM tickets ticket"+ACTOR+" JOIN general_ticket_participant_events entry ON "+TUPLE
            +" WHERE "+OWNER+READ+" ORDER BY entry.revision DESC LIMIT 10",args(scope,identity),
            (rs,row)->new Event(rs.getLong("revision"),rs.getLong("owner_revision"),rs.getLong("member_user_id"),rs.getString("member"),
                rs.getString("actor"),rs.getString("action"),rs.getObject("created_at",OffsetDateTime.class)));
        }catch(DataAccessException ex){return List.of();}
    }
    public boolean change(GeneralTicketEventScope scope,String identity,long ownerRevision,long expectedRevision,Long memberId,boolean add) {
        if(!valid(scope,identity)||ownerRevision<0||expectedRevision<0||expectedRevision==Long.MAX_VALUE||memberId==null||memberId<=0)return false;
        var params=args(scope,identity).addValue("ownerRevision",ownerRevision).addValue("revision",expectedRevision).addValue("memberId",memberId);
        try{return Boolean.TRUE.equals(transaction.execute(tx->{
            guard.acquire(scope.ticketId());
            var roots=jdbc.query("SELECT ticket.channel_id,account.id,account.username FROM tickets ticket"+ACTOR
                +" JOIN general_ticket_read_grants read_access ON read_access.user_id=account.id AND read_access.is_active=TRUE"
                +" JOIN general_ticket_operate_grants operation ON operation.user_id=account.id AND operation.is_active=TRUE"
                +" WHERE "+OWNER+WRITE+" FOR UPDATE OF ticket FOR SHARE OF account,read_access,operation",params,
                (rs,row)->new Root(rs.getObject("channel_id",Long.class),rs.getLong("id"),rs.getString("username")));
            if(roots.size()!=1)return false;
            var root=roots.get(0);
            var revisions=jdbc.query("SELECT "+REVISION+" FROM tickets ticket"+ACTOR+" WHERE "+OWNER+WRITE,params,(rs,row)->rs.getLong(1));
            if(!valid(scope,identity)||revisions.size()!=1||revisions.get(0)!=expectedRevision)return false;
            List<String> names;
            if(add) {
                names=jdbc.query("SELECT candidate.username FROM users candidate JOIN general_ticket_read_grants member_read"
                    +" ON member_read.user_id=candidate.id AND member_read.is_active=TRUE WHERE candidate.id=:memberId AND "+TARGET
                    +" FOR SHARE OF candidate,member_read",params,(rs,row)->rs.getString(1));
            }else{
                // Removing an inaccessible/deleted member does not require that member to regain READ.
                names=jdbc.query("SELECT entry.member FROM general_ticket_participants entry"
                    +" WHERE entry.ticket_user_id=:owner AND entry.ticket_id=:raw AND entry.ticket_public_id=:public AND entry.member_user_id=:memberId",
                    params,(rs,row)->rs.getString(1));
            }
            if(names.size()!=1||!valid(scope,identity))return false;
            params.addValue("actorId",root.actorId()).addValue("actor",root.actor()).addValue("member",names.get(0)).addValue("next",expectedRevision+1);
            int changed=add?jdbc.update("INSERT INTO general_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,member_user_id,member,"
                    +"actor_user_id,actor,revision,owner_revision) SELECT ticket.user_id,ticket.ticket_id,ticket.ticket_public_id,candidate.id,candidate.username,"
                    +"account.id,account.username,:next,:ownerRevision FROM tickets ticket"+ACTOR+" CROSS JOIN users candidate WHERE "+OWNER+WRITE
                    +" AND candidate.id=:memberId AND "+TARGET+" AND "+REVISION+"=:revision"
                    +" AND (SELECT count(*) FROM general_ticket_participants entry WHERE "+TUPLE+")<200 ON CONFLICT DO NOTHING",params)
                :jdbc.update("DELETE FROM general_ticket_participants entry USING tickets ticket"+ACTOR+" WHERE "+TUPLE+" AND "+OWNER+WRITE
                    +" AND "+REVISION+"=:revision AND entry.member_user_id=:memberId",params);
            if(changed!=1)return false;
            jdbc.update("INSERT INTO general_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,revision,owner_revision,"
                +"member_user_id,member,actor_user_id,actor,action) VALUES(:owner,:raw,:public,:next,:ownerRevision,:memberId,:member,:actorId,:actor,:action)",
                params.addValue("action",add?"add":"remove"));
            jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)"
                +" VALUES(:eventId,'participants_changed',:raw,:channel,CURRENT_TIMESTAMP,:owner,:public,'GENERAL',NULL)",
                params.addValue("eventId",ids.nextEventId()).addValue("channel",root.channel(),Types.BIGINT));
            return true;
        }));}catch(DataAccessException|IllegalStateException ex){return false;}
    }
    private boolean valid(GeneralTicketEventScope scope,String identity) {
        return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue()&&identity!=null&&!identity.isBlank()&&scope!=null&&scope.isValid();
    }
    private MapSqlParameterSource args(GeneralTicketEventScope scope,String identity) {
        return new MapSqlParameterSource("owner",scope.ticketUserId()).addValue("raw",scope.ticketId()).addValue("public",scope.ticketPublicId())
            .addValue("identity",identity);
    }
    public record Candidate(long id,String username){}
    public record Member(long id,String snapshot,String username,boolean eligible){}
    public record Event(long revision,long ownerRevision,long memberId,String member,String actor,String action,OffsetDateTime createdAt){}
    private record Root(Long channel,long actorId,String actor){}
}
