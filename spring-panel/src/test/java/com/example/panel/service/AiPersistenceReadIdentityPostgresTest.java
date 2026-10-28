package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static com.example.panel.support.AiControlIdentityFixture.*;
import static com.example.panel.support.AiPersistenceReadIdentityFixture.*;
import org.assertj.core.api.SoftAssertions;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Every identity/grant scenario exercises all four read APIs against the same snapshot. */
class AiPersistenceReadIdentityPostgresTest {
    private static final List<String> METHODS=List.of("findControl","canOperate","findState","findEvents");
    private JdbcTemplate jdbc;
    private ScopedDialogAiPersistenceService service;
    private MultibusinessFeatureFlags flags;
    @BeforeEach void setup()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_persistence_read_identity");AiPersistenceReadIdentityFixture.schema(jdbc,false);AiPersistenceReadIdentityFixture.seed(jdbc,"caller","unused");flags=new MultibusinessFeatureFlags();service=new ScopedDialogAiPersistenceService(jdbc,flags);
    }
    private List<?> read(String name,UiEventTicketScope scope,Authentication actor,int limit)throws Exception {
        boolean events=name.equals("findEvents");Class<?>[] types=events?new Class<?>[]{UiEventTicketScope.class,Authentication.class,int.class}:new Class<?>[]{UiEventTicketScope.class,Authentication.class};
        Object value;try {
            try{value=service.getClass().getMethod(name,types).invoke(service,events?new Object[]{scope,actor,limit}:new Object[]{scope,actor});}
            catch(NoSuchMethodException oldApi){types[1]=String.class;String identity=actor==null?null:actor.getName();value=service.getClass().getMethod(name,types).invoke(service,events?new Object[]{scope,identity,limit}:new Object[]{scope,identity});}
        }catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
        if(value instanceof Boolean allowed)return allowed?List.of(true):List.of();return value instanceof Optional<?> optional?optional.stream().toList():(List<?>)value;
    }
    private Map<String,List<?>> readAll(Authentication actor)throws Exception {var rows=new LinkedHashMap<String,List<?>>();for(String method:METHODS)rows.put(method,read(method,SCOPE,actor,50));return rows;}
    private void denied(Authentication actor)throws Exception {var rows=readAll(actor);SoftAssertions.assertSoftly(check->rows.forEach((name,value)->check.assertThat(value).as(name).isEmpty()));}
    @ParameterizedTest @ValueSource(strings={"wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse"})
    void currentOrReusedNameCannotSelectAnotherReader(String kind)throws Exception {
        long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-change")||kind.endsWith("reuse"))replace(jdbc,kind);
        if(kind.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        var before=snapshot(jdbc);denied(auth(id,name));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"unbound","anonymous","unauthenticated","disabled-principal","null"})
    void invalidAuthenticationCannotRead(String kind)throws Exception {
        Authentication actor=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};denied(actor);
    }
    @ParameterizedTest @ValueSource(strings={"caller","Caller"," caller ","имя","anonymousUser","9007199254740993"})
    void exactLiteralNamesKeepPrivatePayloadsAndAudit(String name)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var rows=readAll(auth(7,name));for(String method:METHODS)assertThat(rows.get(method)).as(method).hasSize(1);
        var control=(ScopedDialogAiPersistenceService.Control)rows.get("findControl").get(0);assertThat(control.reason()).isEqualTo(REASON);assertThat(control.updatedBy()).isEqualTo("PRIVATE_R211_ACTOR");
        assertThat(((Map<?,?>)rows.get("findState").get(0)).get("last_action")).isEqualTo(ACTION);assertThat(((Map<?,?>)rows.get("findEvents").get(0)).get("decision_reason")).isEqualTo(EVENT);
    }
    @ParameterizedTest @ValueSource(longs={7L,9007199254740993L,Long.MAX_VALUE})
    void bigintIdsAreNotRounded(long id)throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);for(var row:readAll(auth(id,"caller")).entrySet())assertThat(row.getValue()).as(row.getKey()).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","membership","inactive","unknown","ambiguous","archived"})
    void currentStatusReadGrantAndUniquenessRemainRequired(String kind)throws Exception {
        if(List.of("disabled","blocked","membership").contains(kind))replace(jdbc,kind);
        else if(kind.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");else if(kind.equals("unknown"))jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=7");else if(kind.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");denied(auth(7,"caller"));
    }
    @ParameterizedTest @ValueSource(strings={"business","generation","tuple","general","collision","missing","unresolved"})
    void fullOwnerAndLegacyCollisionGuardsRemainClosed(String kind)throws Exception {
        switch(kind){case "business"->jdbc.update("UPDATE tickets SET business_id=22");case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));case "tuple"->jdbc.update("UPDATE tickets SET user_id=88");case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE");case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(88,?,? ,22)",RAW,"b".repeat(32));case "missing"->jdbc.update("DELETE FROM tickets");default->jdbc.update("UPDATE tickets SET business_id=NULL");}denied(auth(7,"caller"));
    }
    static Stream<Arguments> roles(){return Stream.of("VIEWER","OPERATOR","MANAGER","ADMIN","UNKNOWN").flatMap(r->Stream.of("membership","all").map(g->Arguments.of(r,g)));}
    @ParameterizedTest @MethodSource("roles")
    void readAndOperateStayDifferentForBothGrantKinds(String role,String grant)throws Exception {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=7");if(grant.equals("membership"))jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);else jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role);
        var rows=readAll(auth(7,"caller"));for(String method:METHODS){boolean allowed=!role.equals("UNKNOWN")&&(!method.equals("canOperate")||!role.equals("VIEWER"));assertThat(rows.get(method)).as(method+"/"+role+"/"+grant).hasSize(allowed?1:0);}
    }
    @ParameterizedTest @ValueSource(strings={"findControl","canOperate","findState","findEvents"})
    void readApisHaveNoStringIdentityOverload(String method){assertThat(Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals(method)).map(m->m.getParameterTypes()[1])).doesNotContain(String.class);}
    @ParameterizedTest @ValueSource(strings={"control","state","events"})
    void mismatchedSavedOwnerIsNotBorrowedButDoesNotRevokeOperate(String table)throws Exception {
        jdbc.update("UPDATE tickets SET user_id=88");var fresh=new UiEventTicketScope(88L,RAW,PUBLIC,"BUSINESS",11L);String method=switch(table){case "control"->"findControl";case "state"->"findState";default->"findEvents";};assertThat(read(method,fresh,auth(7,"caller"),50)).isEmpty();assertThat(read("canOperate",fresh,auth(7,"caller"),50)).hasSize(1);
    }
    @Test void absentControlHasDefaultsOnlyForAnAuthorizedBoundReader()throws Exception {
        jdbc.update("DELETE FROM ticket_ai_scoped_control");var rows=readAll(auth(7,"caller"));var control=(ScopedDialogAiPersistenceService.Control)rows.get("findControl").get(0);assertThat(control.aiDisabled()).isFalse();assertThat(control.autoReplyBlocked()).isFalse();assertThat(control.reason()).isNull();assertThat(control.updatedAt()).isNull();assertThat(readAll(auth(404,"caller")).get("findControl")).isEmpty();
    }
    @Test void eventCapAndOrderRemainAppliedAfterOwnerAndReadFiltering()throws Exception {
        jdbc.update("INSERT INTO ticket_ai_scoped_events(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,event_type,decision_reason) SELECT 42,?,?,'BUSINESS',11,'control_updated','event '||n FROM generate_series(1,60) n",RAW,PUBLIC);
        var capped=read("findEvents",SCOPE,auth(7,"caller"),100);assertThat(capped).hasSize(50);long latest=((Number)((Map<?,?>)capped.get(0)).get("id")).longValue();assertThat(latest).isEqualTo(61);assertThat(((Map<?,?>)capped.get(49)).get("id")).isEqualTo(12L);for(int limit:List.of(-1,0,1))assertThat(read("findEvents",SCOPE,auth(7,"caller"),limit)).hasSize(1);
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");assertThat(read("findEvents",SCOPE,auth(7,"caller"),100)).isEmpty();
    }
    @Test void invalidAuthenticationAndMissingPolicyStopBeforeJdbc()throws Exception {
        var observed=mock(NamedParameterJdbcOperations.class);service=new ScopedDialogAiPersistenceService(observed,flags);denied(UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()));service=new ScopedDialogAiPersistenceService(observed,null);denied(auth(7,"caller"));verifyNoInteractions(observed);
    }
}
