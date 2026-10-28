package com.example.panel.service;

import static com.example.panel.service.BusinessTicketAssignmentService.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Local human BUSINESS lifecycle. No raw category, activity, feedback, AI or provider workflow. */
@Service
public class BusinessTicketWorkService {
    private static final String AUTHENTICATED_ACTOR=" CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n "
        +" JOIN users actor ON actor.id=:authenticatedActorId AND actor.username=:authenticatedActorName ";
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService ids;
    private final ObjectMapper json;
    private final LegacyTicketIdConcurrencyGuard guard;
    private final TransactionTemplate transaction;

    public BusinessTicketWorkService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,UiEventOutboxAppendService ids,ObjectMapper json) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.ids=ids;this.json=json;
        guard=new LegacyTicketIdConcurrencyGuard(jdbc);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<View> find(String publicId,Authentication authentication) {
        var account=boundActor(authentication);
        String identity=account==null?null:account.getUsername();
        if(account==null||!enabled(identity)||publicId==null||!publicId.matches("[a-f0-9]{32}"))return Optional.empty();
        try {
            var rows=jdbc.query("SELECT owner.*,("+OPERATOR+") AS can_operate FROM tickets owner"+ACTOR
                +" WHERE owner.ticket_public_id=:public AND "+UNIQUE_ROOT+READ,
                new MapSqlParameterSource("public",publicId).addValue("identity",identity.toLowerCase(Locale.ROOT)).addValue("readerId",account.getUserId()).addValue("readerName",identity),
                (rs,row)->new View(new UiEventTicketScope(rs.getLong("user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id"),
                    "BUSINESS",rs.getLong("business_id")),rs.getString("status"),rs.getLong("business_work_revision"),
                    categories(rs.getString("business_categories")),rs.getBoolean("can_operate")));
            return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
        }catch(DataAccessException|IllegalArgumentException ex){return Optional.empty();}
    }
    public List<Event> recent(UiEventTicketScope scope,Authentication authentication) {
        var account=boundActor(authentication);
        String identity=account==null?null:account.getUsername();
        if(account==null||!valid(scope,identity))return List.of();
        try{return jdbc.query("SELECT entry.* FROM tickets owner"+ACTOR+" JOIN business_ticket_work_events entry ON "
            +"ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)"
            +"=ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id) WHERE "+OWNER+READ
            +" ORDER BY entry.revision DESC LIMIT 10",readArgs(scope,account),
            (rs,row)->new Event(rs.getLong("revision"),rs.getString("action"),rs.getString("from_status"),rs.getString("to_status"),
                categories(rs.getString("previous_categories")),categories(rs.getString("categories")),
                rs.getString("actor"),rs.getObject("created_at",OffsetDateTime.class)));
        }catch(DataAccessException|IllegalArgumentException ex){return List.of();}
    }
    public boolean change(UiEventTicketScope scope,Authentication authentication,long revision,String expectedStatus,String action,List<String> submitted) {
        var account=boundActor(authentication);
        if(account==null||!valid(scope,account.getUsername())||revision<0||revision==Long.MAX_VALUE||!allowed(expectedStatus,action))return false;
        String identity=account.getUsername();
        final List<String> requested;
        try { requested=normalizeCategories(submitted); }catch(IllegalArgumentException ex){return false;}
        if(action.equals("close")&&requested.isEmpty()||action.equals("reopen")&&!requested.isEmpty())return false;
        String nextStatus=action.equals("close")?"resolved":action.equals("reopen")?"pending":expectedStatus;
        var params=args(scope,identity).addValue("revision",revision).addValue("expectedStatus",expectedStatus)
            .addValue("nextStatus",nextStatus).addValue("action",action)
            .addValue("authenticatedActorId",account.getUserId()).addValue("authenticatedActorName",identity);
        String from=" FROM tickets owner"+AUTHENTICATED_ACTOR+" JOIN businesses business ON business.id=owner.business_id"
            +" WHERE "+OWNER+READ+" AND "+OPERATOR;
        try{return Boolean.TRUE.equals(transaction.execute(tx->{
            guard.acquire(scope.ticketId());
            var roots=jdbc.query("SELECT owner.channel_id,owner.business_categories,actor.id AS actor_id,actor.username"+from
                +" AND owner.business_work_revision=:revision AND owner.status=:expectedStatus FOR UPDATE OF owner FOR SHARE OF actor,business",params,
                (rs,row)->new Root(rs.getObject("channel_id",Long.class),rs.getString("business_categories"),rs.getLong("actor_id"),rs.getString("username")));
            if(roots.size()!=1)return false;
            var root=roots.get(0);
            params.addValue("actorId",root.actorId());
            jdbc.query("SELECT user_id FROM business_memberships WHERE user_id=:actorId AND business_id=:business"
                +" AND is_active=TRUE AND role_code IN ('VIEWER','OPERATOR','MANAGER','ADMIN') FOR SHARE",params,(rs,row)->rs.getLong(1));
            jdbc.query("SELECT user_id FROM business_access_grants WHERE user_id=:actorId AND grant_scope='ALL_BUSINESSES'"
                +" AND is_active=TRUE AND role_code IN ('VIEWER','OPERATOR','MANAGER','ADMIN') FOR SHARE",params,(rs,row)->rs.getLong(1));
            if(!valid(scope,identity))return false;
            var previous=categories(root.categories());
            var next=action.equals("reopen")?previous:requested;
            if(action.equals("categories")&&previous.equals(next))return false;
            params.addValue("previousCategories",root.categories()).addValue("categories",encode(next))
                .addValue("actor",root.actor()).addValue("channel",root.channel(),Types.BIGINT).addValue("enabled",enabled(identity));
            var changed=jdbc.query("""
                UPDATE tickets owner SET status=:nextStatus,business_categories=CAST(:categories AS JSONB),
                    resolved_at=CASE WHEN :action='close' THEN CURRENT_TIMESTAMP WHEN :action='reopen' THEN NULL ELSE owner.resolved_at END,
                    resolved_by=CASE WHEN :action='close' THEN actor.username WHEN :action='reopen' THEN NULL ELSE owner.resolved_by END,
                    closed_count=CASE WHEN :action='close' THEN COALESCE(owner.closed_count,0)+1 ELSE owner.closed_count END,
                    reopen_count=CASE WHEN :action='reopen' THEN COALESCE(owner.reopen_count,0)+1 ELSE owner.reopen_count END,
                    last_reopen_at=CASE WHEN :action='reopen' THEN CURRENT_TIMESTAMP ELSE owner.last_reopen_at END
                FROM users actor CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n
                WHERE actor.id=:authenticatedActorId AND actor.username=:authenticatedActorName AND actor.id=:actorId AND
                """+OWNER+READ+" AND "+OPERATOR+"""
                 AND owner.business_work_revision=:revision AND owner.status=:expectedStatus
                 AND owner.business_categories=CAST(:previousCategories AS JSONB)
                 AND owner.channel_id IS NOT DISTINCT FROM CAST(:channel AS BIGINT) AND :enabled=TRUE
                 RETURNING owner.business_work_revision
                """,params,(rs,row)->rs.getLong(1));
            if(changed.size()!=1){tx.setRollbackOnly();return false;}
            if(changed.get(0)!=revision+1)throw new IllegalStateException("Business work revision trigger is unavailable");
            params.addValue("next",changed.get(0));
            int audit=jdbc.update("""
                INSERT INTO business_ticket_work_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,action,
                    from_status,to_status,previous_categories,categories,actor_user_id,actor)
                VALUES(:owner,:raw,:public,:business,:next,:action,:expectedStatus,:nextStatus,
                    CAST(:previousCategories AS JSONB),CAST(:categories AS JSONB),:actorId,:actor)
                """,params);
            int outbox=jdbc.update("""
                INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
                VALUES(:eventId,'business_work_changed',:raw,:channel,CURRENT_TIMESTAMP,:owner,:public,'BUSINESS',:business)
                """,params.addValue("eventId",ids.nextEventId()));
            var confirmation=jdbc.query("SELECT owner.business_work_revision"+from
                +" AND owner.business_work_revision=:next AND owner.status=:nextStatus"
                +" AND owner.business_categories=CAST(:categories AS JSONB)"
                +" AND owner.channel_id IS NOT DISTINCT FROM CAST(:channel AS BIGINT) AND actor.id=:actorId",
                params,(rs,row)->rs.getLong(1));
            if(audit!=1||outbox!=1||confirmation.size()!=1||!valid(scope,identity)) {
                tx.setRollbackOnly();return false;
            }
            return true;
        }));}catch(DataAccessException|IllegalStateException|IllegalArgumentException ex){return false;}
    }
    private PanelUserPrincipal boundActor(Authentication authentication){
        if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken
            ||!(authentication.getPrincipal() instanceof PanelUserPrincipal account)||account.getUserId()<=0
            ||account.getUsername().isBlank()||!account.isEnabled()||!account.isAccountNonLocked()
            ||!account.isAccountNonExpired()||!account.isCredentialsNonExpired())return null;
        return account;
    }
    private boolean allowed(String status,String action) {
        return ("pending".equals(status)||"open".equals(status))&&("close".equals(action)||"categories".equals(action))
            ||("closed".equals(status)||"resolved".equals(status))&&"reopen".equals(action);
    }
    private List<String> normalizeCategories(List<String> values) {
        if(values==null||values.size()>50)throw new IllegalArgumentException("Invalid categories");
        var result=new LinkedHashSet<String>();
        for(String value:values) {
            if(value==null)throw new IllegalArgumentException("Invalid category");
            String normalized=value.trim();
            if(normalized.isEmpty())continue;
            if(normalized.codePointCount(0,normalized.length())>160||normalized.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid category");
            result.add(normalized);
        }
        return List.copyOf(result);
    }
    private List<String> categories(String value) {
        try{return List.of(json.readValue(value,String[].class));}catch(JsonProcessingException ex){throw new IllegalArgumentException("Invalid category snapshot",ex);}
    }
    private String encode(List<String> value) {
        try{return json.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalArgumentException("Invalid categories",ex);}
    }
    private boolean enabled(String identity){return flags.isFoundationEnabled()&&flags.isUiSelector()&&identity!=null&&!identity.isBlank();}
    private boolean valid(UiEventTicketScope scope,String identity){return enabled(identity)&&scope!=null&&scope.isValid()&&"BUSINESS".equals(scope.scopeKind());}
    private MapSqlParameterSource readArgs(UiEventTicketScope scope,PanelUserPrincipal account){
        return args(scope,account.getUsername()).addValue("readerId",account.getUserId()).addValue("readerName",account.getUsername());
    }
    private MapSqlParameterSource args(UiEventTicketScope scope,String identity) {
        return new MapSqlParameterSource("owner",scope.ticketUserId()).addValue("raw",scope.ticketId()).addValue("public",scope.ticketPublicId())
            .addValue("business",scope.businessId()).addValue("identity",identity.toLowerCase(Locale.ROOT));
    }
    public record View(UiEventTicketScope scope,String status,long revision,List<String> categories,boolean canOperate){}
    public record Event(long revision,String action,String fromStatus,String toStatus,List<String> previousCategories,List<String> categories,String actor,OffsetDateTime createdAt){}
    private record Root(Long channel,String categories,long actorId,String actor){}
}
