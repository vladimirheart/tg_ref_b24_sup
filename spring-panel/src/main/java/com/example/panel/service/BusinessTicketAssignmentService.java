package com.example.panel.service;

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

/** Human BUSINESS assignment only. No legacy workflow, provider, AI or participant side effects. */
@Service
public class BusinessTicketAssignmentService {
    static final String ACTOR=" CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n "
        +" JOIN users actor ON lower(actor.username)=n.user_identity ";
    private static final String AUTHENTICATED_ACTOR=" CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n "
        +" JOIN users actor ON actor.id=:authenticatedActorId AND actor.username=:authenticatedActorName ";
    static final String READ=" AND ("+NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS+")";
    static final String UNIQUE_ROOT=" owner.is_general=FALSE AND owner.business_id IS NOT NULL "
        +" AND 1=(SELECT count(*) FROM tickets collision WHERE collision.ticket_id=owner.ticket_id) ";
    static final String OWNER=" owner.user_id=:owner AND owner.ticket_id=:raw AND owner.ticket_public_id=:public "
        +" AND owner.business_id=:business AND "+UNIQUE_ROOT;
    static final String OPEN=" position('closed' IN lower(COALESCE(owner.status,'')))=0 "
        +" AND position('resolved' IN lower(COALESCE(owner.status,'')))=0 ";
    private static String operates(String alias) {
        return " lower(btrim("+alias+".username))<>'ai_agent' AND ("
            +DialogResponsibilityService.ASSIGNEE_CAN_OPERATE_SQL.replace("assignment.responsible",alias+".username")+")";
    }
    static final String OPERATOR=operates("actor");
    private static final String TARGET=operates("candidate");
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService ids;
    private final LegacyTicketIdConcurrencyGuard guard;
    private final TransactionTemplate transaction;

    public BusinessTicketAssignmentService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,UiEventOutboxAppendService ids) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.ids=ids;
        guard=new LegacyTicketIdConcurrencyGuard(jdbc);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<State> find(String publicId,String identity) {
        if(!enabled(identity)||publicId==null||!publicId.matches("[a-f0-9]{32}"))return Optional.empty();
        try {
            var rows=jdbc.query("SELECT owner.*,assigned.responsible,("+OPEN+" AND "+OPERATOR+") AS can_operate "
                +" FROM tickets owner"+ACTOR+" LEFT JOIN ticket_responsibles assigned ON assigned.ticket_id=owner.ticket_id "
                +" WHERE owner.ticket_public_id=:public AND "+UNIQUE_ROOT+READ,
                new MapSqlParameterSource("public",publicId).addValue("identity",normalize(identity)),
                (rs,row)->new State(new UiEventTicketScope(rs.getLong("user_id"),rs.getString("ticket_id"),
                    rs.getString("ticket_public_id"),"BUSINESS",rs.getLong("business_id")),
                    rs.getLong("responsibility_revision"),rs.getString("responsible"),rs.getBoolean("can_operate")));
            return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
        } catch(DataAccessException ex){return Optional.empty();}
    }
    public List<Candidate> candidates(UiEventTicketScope scope,String identity) {
        if(!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT candidate.id,candidate.username FROM tickets owner"+ACTOR
            +" CROSS JOIN users candidate WHERE "+OWNER+READ+" AND "+OPEN+" AND "+OPERATOR+" AND "+TARGET
            +" ORDER BY lower(candidate.username),candidate.id LIMIT 200",args(scope,identity),
            (rs,row)->new Candidate(rs.getLong("id"),rs.getString("username")));
        }catch(DataAccessException ex){return List.of();}
    }
    public List<Event> recent(UiEventTicketScope scope,String identity) {
        if(!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT entry.revision,entry.previous_responsible,entry.assignee,entry.actor,entry.action,entry.created_at "
            +" FROM tickets owner"+ACTOR+" JOIN business_ticket_assignment_events entry ON "
            +" ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)"
            +"=ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id)"
            +" WHERE "+OWNER+READ+" ORDER BY entry.revision DESC LIMIT 10",args(scope,identity),
            (rs,row)->new Event(rs.getLong("revision"),rs.getString("previous_responsible"),rs.getString("assignee"),
                rs.getString("actor"),rs.getString("action"),rs.getObject("created_at",OffsetDateTime.class)));
        }catch(DataAccessException ex){return List.of();}
    }
    public boolean change(UiEventTicketScope scope,Authentication authentication,long revision,Long targetId,boolean take) {
        var account=boundActor(authentication);
        if(account==null||!valid(scope,account.getUsername())||revision<0||revision==Long.MAX_VALUE||!take&&(targetId==null||targetId<=0))return false;
        String identity=account.getUsername();
        var params=args(scope,identity).addValue("revision",revision).addValue("target",targetId,Types.BIGINT).addValue("take",take)
            .addValue("authenticatedActorId",account.getUserId()).addValue("authenticatedActorName",identity);
        String from=" FROM tickets owner"+AUTHENTICATED_ACTOR
            +" JOIN businesses business ON business.id=owner.business_id "
            +" JOIN users candidate ON ((:take=TRUE AND candidate.id=actor.id) OR (:take=FALSE AND candidate.id=:target)) "
            +" LEFT JOIN ticket_responsibles assigned ON assigned.ticket_id=owner.ticket_id WHERE "+OWNER+READ
            +" AND owner.responsibility_revision=:revision AND "+OPEN+" AND "+OPERATOR+" AND "+TARGET;
        try{return Boolean.TRUE.equals(transaction.execute(tx->{
            guard.acquire(scope.ticketId());
            var roots=jdbc.query("SELECT owner.channel_id,assigned.responsible,actor.id AS actor_id,actor.username AS actor,"
                +"candidate.id AS target_id,candidate.username AS target"+from
                +" FOR UPDATE OF owner FOR SHARE OF actor,candidate,business",params,
                (rs,row)->new Root(rs.getObject("channel_id",Long.class),rs.getString("responsible"),rs.getLong("actor_id"),
                    rs.getString("actor"),rs.getLong("target_id"),rs.getString("target")));
            if(roots.size()!=1)return false;
            var root=roots.get(0);
            params.addValue("actorId",root.actorId()).addValue("targetId",root.targetId());
            // Lock qualifying grants, then re-evaluate both accounts in the final statement.
            jdbc.query("SELECT user_id FROM business_memberships WHERE user_id IN (:actorId,:targetId) AND business_id=:business "
                +" AND is_active=TRUE AND role_code IN ('OPERATOR','MANAGER','ADMIN') ORDER BY user_id FOR SHARE",params,(rs,row)->rs.getLong(1));
            jdbc.query("SELECT user_id FROM business_access_grants WHERE user_id IN (:actorId,:targetId) AND grant_scope='ALL_BUSINESSES' "
                +" AND is_active=TRUE AND role_code IN ('OPERATOR','MANAGER','ADMIN') ORDER BY user_id FOR SHARE",params,(rs,row)->rs.getLong(1));
            if(!valid(scope,identity))return false;
            params.addValue("previous",root.previous(),Types.VARCHAR);
            int changed=jdbc.update("INSERT INTO ticket_responsibles AS current(ticket_id,responsible,assigned_by) "
                +" SELECT owner.ticket_id,candidate.username,actor.username"+from
                +" AND assigned.responsible IS NOT DISTINCT FROM CAST(:previous AS TEXT)"
                +" ON CONFLICT(ticket_id) DO UPDATE SET responsible=EXCLUDED.responsible,assigned_by=EXCLUDED.assigned_by,assigned_at=CURRENT_TIMESTAMP"
                +" WHERE current.responsible IS NOT DISTINCT FROM CAST(:previous AS TEXT) AND current.responsible IS DISTINCT FROM EXCLUDED.responsible",params);
            if(changed!=1)return false;
            Long next=jdbc.queryForObject("SELECT responsibility_revision FROM tickets WHERE user_id=:owner AND ticket_id=:raw",params,Long.class);
            if(next==null||next!=revision+1)throw new IllegalStateException("Assignment revision trigger is unavailable");
            params.addValue("next",next).addValue("assignee",root.target()).addValue("actor",root.actor()).addValue("action",take?"take":"assign");
            jdbc.update("INSERT INTO business_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,"
                +"previous_responsible,assignee_user_id,assignee,actor_user_id,actor,action)"
                +" VALUES(:owner,:raw,:public,:business,:next,:previous,:targetId,:assignee,:actorId,:actor,:action)",params);
            jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)"
                +" VALUES(:eventId,'business_assignment_changed',:raw,:channel,CURRENT_TIMESTAMP,:owner,:public,'BUSINESS',:business)",
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
    private boolean enabled(String identity){return flags.isFoundationEnabled()&&flags.isUiSelector()&&identity!=null&&!identity.isBlank();}
    private boolean valid(UiEventTicketScope scope,String identity){return enabled(identity)&&scope!=null&&scope.isValid()&&"BUSINESS".equals(scope.scopeKind());}
    private String normalize(String identity){return identity.toLowerCase(Locale.ROOT);}
    private MapSqlParameterSource args(UiEventTicketScope scope,String identity){
        return new MapSqlParameterSource("owner",scope.ticketUserId()).addValue("raw",scope.ticketId()).addValue("public",scope.ticketPublicId())
            .addValue("business",scope.businessId()).addValue("identity",normalize(identity));
    }
    public record State(UiEventTicketScope scope,long revision,String responsible,boolean canOperate){}
    public record Candidate(long id,String username){}
    public record Event(long revision,String previousResponsible,String assignee,String actor,String action,OffsetDateTime createdAt){}
    private record Root(Long channel,String previous,long actorId,String actor,long targetId,String target){}
}
