package com.example.panel.service;

import static com.example.panel.service.BusinessTicketAssignmentService.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.security.PanelUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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

/** New scoped roster. Membership never grants business access, changes assignment or invokes legacy callbacks. */
@Service
public class BusinessTicketParticipantService {
    private static final String AUTHENTICATED_ACTOR=" CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n "
        +" JOIN users actor ON actor.id=:authenticatedActorId AND actor.username=:authenticatedActorName AND "+ACTOR_ACCOUNT;
    private static final String TUPLE=" ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)"
        +"=ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id) ";
    private static final String REVISION=" (SELECT COALESCE(max(entry.revision),0) FROM business_ticket_participant_events entry WHERE "+TUPLE+") ";
    private static final String MEMBER_READ=" lower(btrim(candidate.username))<>'ai_agent' AND ("
        +NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS.replace("n.user_identity","lower(candidate.username)")+") ";
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService ids;
    private final LegacyTicketIdConcurrencyGuard guard;
    private final TransactionTemplate transaction;

    public BusinessTicketParticipantService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,UiEventOutboxAppendService ids) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.ids=ids;
        guard=new LegacyTicketIdConcurrencyGuard(jdbc);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<Long> revision(UiEventTicketScope scope,Authentication authentication) {
        var account=boundActor(authentication);
        String identity=account==null?null:account.getUsername();
        if(account==null||!valid(scope,identity))return Optional.empty();
        try {
            var rows=jdbc.query("SELECT "+REVISION+" FROM tickets owner"+ACTOR+" WHERE "+OWNER+READ,readArgs(scope,account),
                (rs,row)->rs.getLong(1));
            return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
        }catch(DataAccessException ex){return Optional.empty();}
    }
    public List<Member> members(UiEventTicketScope scope,Authentication authentication) {
        var account=boundActor(authentication);
        String identity=account==null?null:account.getUsername();
        if(account==null||!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT entry.member_user_id,entry.member,candidate.username,("+MEMBER_READ+") AS eligible"
            +" FROM tickets owner"+ACTOR+" JOIN business_ticket_participants entry ON "+TUPLE
            +" LEFT JOIN users candidate ON candidate.id=entry.member_user_id WHERE "+OWNER+READ
            +" ORDER BY entry.revision,entry.member_user_id LIMIT 200",readArgs(scope,account),
            (rs,row)->new Member(rs.getLong("member_user_id"),rs.getString("member"),rs.getString("username"),rs.getBoolean("eligible")));
        }catch(DataAccessException ex){return List.of();}
    }
    public List<Candidate> candidates(UiEventTicketScope scope,Authentication authentication) {
        var account=boundActor(authentication);
        String identity=account==null?null:account.getUsername();
        if(account==null||!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT candidate.id,candidate.username FROM tickets owner"+ACTOR+" CROSS JOIN users candidate"
            +" WHERE "+OWNER+READ+" AND "+OPEN+" AND "+OPERATOR+" AND "+MEMBER_READ
            +" AND NOT EXISTS(SELECT 1 FROM business_ticket_participants entry WHERE "+TUPLE+" AND entry.member_user_id=candidate.id)"
            +" ORDER BY lower(candidate.username),candidate.id LIMIT 200",readArgs(scope,account),
            (rs,row)->new Candidate(rs.getLong("id"),rs.getString("username")));
        }catch(DataAccessException ex){return List.of();}
    }
    public List<Event> recent(UiEventTicketScope scope,Authentication authentication) {
        var account=boundActor(authentication);
        String identity=account==null?null:account.getUsername();
        if(account==null||!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT entry.* FROM tickets owner"+ACTOR+" JOIN business_ticket_participant_events entry ON "+TUPLE
            +" WHERE "+OWNER+READ+" ORDER BY entry.revision DESC LIMIT 10",readArgs(scope,account),
            (rs,row)->new Event(rs.getLong("revision"),rs.getLong("member_user_id"),rs.getString("member"),
                rs.getString("actor"),rs.getString("action"),rs.getObject("created_at",OffsetDateTime.class)));
        }catch(DataAccessException ex){return List.of();}
    }
    public boolean change(UiEventTicketScope scope,Authentication authentication,long expectedRevision,Long memberId,boolean add) {
        var account=boundActor(authentication);
        if(account==null||!valid(scope,account.getUsername())||expectedRevision<0||expectedRevision==Long.MAX_VALUE||memberId==null||memberId<=0)return false;
        String identity=account.getUsername();
        var params=args(scope,identity).addValue("revision",expectedRevision).addValue("memberId",memberId)
            .addValue("authenticatedActorId",account.getUserId()).addValue("authenticatedActorName",identity);
        String rootFrom=" FROM tickets owner"+AUTHENTICATED_ACTOR+" JOIN businesses business ON business.id=owner.business_id"
            +" WHERE "+OWNER+READ+" AND "+OPEN+" AND "+OPERATOR;
        try{return Boolean.TRUE.equals(transaction.execute(tx->{
            guard.acquire(scope.ticketId());
            var roots=jdbc.query("SELECT owner.channel_id,actor.id,actor.username"+rootFrom
                +" FOR UPDATE OF owner FOR SHARE OF actor,business",params,
                (rs,row)->new Root(rs.getObject("channel_id",Long.class),rs.getLong("id"),rs.getString("username")));
            if(roots.size()!=1)return false;
            var root=roots.get(0);
            params.addValue("actorId",root.actorId());
            // Freeze current grants. READ includes VIEWER for a member; actor still requires OPERATE.
            jdbc.query("SELECT user_id FROM business_memberships WHERE user_id IN (:actorId,:memberId) AND business_id=:business"
                +" AND is_active=TRUE AND role_code IN ('VIEWER','OPERATOR','MANAGER','ADMIN') ORDER BY user_id FOR SHARE",
                params,(rs,row)->rs.getLong(1));
            jdbc.query("SELECT user_id FROM business_access_grants WHERE user_id IN (:actorId,:memberId) AND grant_scope='ALL_BUSINESSES'"
                +" AND is_active=TRUE AND role_code IN ('VIEWER','OPERATOR','MANAGER','ADMIN') ORDER BY user_id FOR SHARE",
                params,(rs,row)->rs.getLong(1));
            var revisions=jdbc.query("SELECT "+REVISION+rootFrom,params,(rs,row)->rs.getLong(1));
            if(!valid(scope,identity)||revisions.size()!=1||revisions.get(0)!=expectedRevision)return false;
            List<String> names;
            if(add) {
                names=jdbc.query("SELECT candidate.username FROM tickets owner"+AUTHENTICATED_ACTOR+" JOIN users candidate ON candidate.id=:memberId"
                    +" WHERE "+OWNER+READ+" AND "+OPEN+" AND "+OPERATOR+" AND "+MEMBER_READ
                    +" AND (SELECT count(*) FROM business_ticket_participants entry WHERE "+TUPLE+")<200"
                    +" FOR SHARE OF candidate",params,(rs,row)->rs.getString(1));
            }else{
                // Removal must remain possible after member disable/revocation/deletion.
                names=jdbc.query("SELECT entry.member FROM tickets owner"+AUTHENTICATED_ACTOR+" JOIN business_ticket_participants entry ON "+TUPLE
                    +" WHERE "+OWNER+READ+" AND "+OPEN+" AND "+OPERATOR+" AND entry.member_user_id=:memberId",
                    params,(rs,row)->rs.getString(1));
            }
            if(names.size()!=1)return false;
            params.addValue("member",names.get(0)).addValue("actor",root.actor()).addValue("next",expectedRevision+1);
            int changed=add?jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,"
                    +"member_user_id,member,actor_user_id,actor,revision)"
                    +" SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id,candidate.id,candidate.username,actor.id,actor.username,:next"
                    +" FROM tickets owner"+AUTHENTICATED_ACTOR+" JOIN users candidate ON candidate.id=:memberId"
                    +" WHERE "+OWNER+READ+" AND "+OPEN+" AND "+OPERATOR+" AND "+MEMBER_READ+" AND "+REVISION+"=:revision"
                    +" AND (SELECT count(*) FROM business_ticket_participants entry WHERE "+TUPLE+")<200"
                    +" ON CONFLICT DO NOTHING",params)
                :jdbc.update("DELETE FROM business_ticket_participants entry USING tickets owner"+AUTHENTICATED_ACTOR+" WHERE "+TUPLE
                    +" AND "+OWNER+READ+" AND "+OPEN+" AND "+OPERATOR+" AND "+REVISION+"=:revision AND entry.member_user_id=:memberId",params);
            if(changed!=1)return false;
            jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,"
                +"member_user_id,member,actor_user_id,actor,action) VALUES(:owner,:raw,:public,:business,:next,:memberId,:member,:actorId,:actor,:action)",
                params.addValue("action",add?"add":"remove"));
            jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)"
                +" VALUES(:eventId,'business_participants_changed',:raw,:channel,CURRENT_TIMESTAMP,:owner,:public,'BUSINESS',:business)",
                params.addValue("eventId",ids.nextEventId()).addValue("channel",root.channel(),Types.BIGINT));
            return true;
        }));}catch(DataAccessException|IllegalStateException ex){return false;}
    }
    private PanelUserPrincipal boundActor(Authentication authentication){
        if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken
            ||!(authentication.getPrincipal() instanceof PanelUserPrincipal account)||account.getUserId()<=0
            ||account.getUsername().isBlank()||!account.isEnabled()||!account.isAccountNonLocked()
            ||!account.isAccountNonExpired()||!account.isCredentialsNonExpired())return null;
        return account;
    }
    private boolean valid(UiEventTicketScope scope,String identity) {
        return flags.isFoundationEnabled()&&flags.isUiSelector()&&identity!=null&&!identity.isBlank()
            &&scope!=null&&scope.isValid()&&"BUSINESS".equals(scope.scopeKind());
    }
    private MapSqlParameterSource readArgs(UiEventTicketScope scope,PanelUserPrincipal account){
        return args(scope,account.getUsername()).addValue("readerId",account.getUserId()).addValue("readerName",account.getUsername());
    }
    private MapSqlParameterSource args(UiEventTicketScope scope,String identity) {
        return new MapSqlParameterSource("owner",scope.ticketUserId()).addValue("raw",scope.ticketId()).addValue("public",scope.ticketPublicId())
            .addValue("business",scope.businessId()).addValue("identity",identity.toLowerCase(Locale.ROOT));
    }
    public record Candidate(long id,String username){}
    public record Member(long id,String snapshot,String username,boolean eligible){}
    public record Event(long revision,long memberId,String member,String actor,String action,OffsetDateTime createdAt){}
    private record Root(Long channel,long actorId,String actor){}
}
