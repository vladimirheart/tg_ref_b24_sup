package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static com.example.panel.support.AiControlIdentityFixture.SCOPE;
import static com.example.panel.support.AiControlIdentityFixture.RAW;
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The frozen assertions exercise the old login API and the bound-session API. */
class AiSourceCreateIdentityPostgresTest {
    private static JdbcTemplate jdbc;
    private ScopedAiSourceService service;
    private MultibusinessFeatureFlags flags;
    @BeforeAll static void schemaOnce()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_source_create_identity");schema(jdbc,false);
    }
    @BeforeEach void setup() {
        seed(jdbc,"caller","unused");flags=new MultibusinessFeatureFlags();service=new ScopedAiSourceService(jdbc,flags);
    }
    private boolean create(boolean memory,Authentication actor)throws Exception {
        var types=new Class<?>[]{memory?UiEventTicketScope.class:Long.class,Authentication.class,String.class,String.class,String.class};
        String method=memory?"createMemory":"createKnowledge";Object owner=memory?SCOPE:11L;
        try {
            try{return (Boolean)service.getClass().getMethod(method,types).invoke(service,owner,actor,KEY,"  Reviewed title  ","  safe curated answer  ");}
            catch(NoSuchMethodException oldApi){types[1]=String.class;return (Boolean)service.getClass().getMethod(method,types).invoke(service,owner,actor==null?null:actor.getName(),KEY,"  Reviewed title  ","  safe curated answer  ");}
        }catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private void denied(boolean memory,Authentication actor)throws Exception {var before=snapshot(jdbc);assertThat(create(memory,actor)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);}
    private static Stream<Arguments> kinds(String... choices){return Arrays.stream(choices).flatMap(c->Stream.of(false,true).map(m->Arguments.of(c,m)));}
    static Stream<Arguments> identities(){return kinds("wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse");}
    @ParameterizedTest @MethodSource("identities")
    void theLoginCannotSelectAnotherSourceAuthor(String kind,boolean memory)throws Exception {
        long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-change")||kind.endsWith("reuse"))replace(jdbc,kind);
        if(kind.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        denied(memory,auth(id,name));
    }
    static Stream<Arguments> invalidAuth(){return kinds("unbound","anonymous","unauthenticated","disabled-principal","null");}
    @ParameterizedTest @MethodSource("invalidAuth")
    void invalidAuthenticationCannotCreateSources(String kind,boolean memory)throws Exception {
        Authentication actor=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};denied(memory,actor);
    }
    static Stream<Arguments> names(){return kinds("caller","Caller"," caller ","имя","anonymousUser","9007199254740993");}
    @ParameterizedTest @MethodSource("names")
    void sourceAndEventPreserveTheExactRawAuthor(String name,boolean memory)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(create(memory,auth(7,name))).isTrue();
        var source=jdbc.queryForMap("SELECT * FROM ai_scoped_sources");
        assertThat(source).containsEntry("updated_by_user_id",7L).containsEntry("updated_by",name).containsEntry("source_kind",memory?"MEMORY":"KNOWLEDGE").containsEntry("status","DRAFT").containsEntry("revision",1L).containsEntry("title","Reviewed title").containsEntry("body","safe curated answer");
        assertThat(source).containsEntry("origin_ticket_user_id",memory?42L:null).containsEntry("origin_ticket_id",memory?RAW:null).containsEntry("origin_ticket_public_id",memory?SCOPE.ticketPublicId():null).containsEntry("origin_ticket_scope_kind",memory?"BUSINESS":null).containsEntry("origin_ticket_business_id",memory?11L:null);
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,action FROM ai_scoped_source_events")).containsEntry("actor_user_id",7L).containsEntry("actor",name).containsEntry("action","create");
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_memory_evidence")).isEmpty();
    }
    static Stream<Arguments> ids(){return Stream.of(7L,9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).map(m->Arguments.of(id,m)));}
    @ParameterizedTest @MethodSource("ids")
    void sourceAndEventKeepExactBigintAuthorIds(long id,boolean memory)throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);assertThat(create(memory,auth(id,"caller"))).isTrue();assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM ai_scoped_sources",Long.class)).isEqualTo(id);assertThat(jdbc.queryForObject("SELECT actor_user_id FROM ai_scoped_source_events",Long.class)).isEqualTo(id);
    }
    static Stream<Arguments> statuses(){return kinds("disabled","blocked","membership","inactive","unknown","ambiguous","archived");}
    @ParameterizedTest @MethodSource("statuses")
    void currentUniqueAccountAndConfigureRightsRemainRequired(String kind,boolean memory)throws Exception {
        mutation(kind);denied(memory,auth(7,"caller"));
    }
    static Stream<Arguments> roles(){return Stream.of("VIEWER","OPERATOR","MANAGER","ADMIN","UNKNOWN").flatMap(r->Stream.of("membership","all").flatMap(g->Stream.of(false,true).map(m->Arguments.of(r,g,m))));}
    @ParameterizedTest @MethodSource("roles")
    void onlyBusinessAdminCanCreateEitherSourceKind(String role,String grant,boolean memory)throws Exception {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=7");if(grant.equals("membership"))jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);else jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role);
        var before=snapshot(jdbc);assertThat(create(memory,auth(7,"caller"))).isEqualTo(role.equals("ADMIN"));if(!role.equals("ADMIN"))assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> late(){return kinds("rename-reuse","delete-reuse","case-change","disabled","blocked","membership","inactive","ambiguous","archived");}
    @ParameterizedTest @MethodSource("late")
    void theInsertRechecksIdentityAndAuthorityAtTheSqlBoundary(String kind,boolean memory)throws Exception {
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));var before=snapshot(jdbc);
        doAnswer(call->{mutation(kind);return new NamedParameterJdbcTemplate(jdbc).update(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class));}).when(observed).update(anyString(),any(SqlParameterSource.class));
        service=new ScopedAiSourceService(observed,flags);assertThat(create(memory,auth(7,"caller"))).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void auditFailureAndDuplicateKeyCannotLeavePartialSources(boolean memory)throws Exception {
        jdbc.execute("ALTER TABLE ai_scoped_source_events ADD CONSTRAINT deny_create CHECK(action<>'create')");denied(memory,auth(7,"caller"));jdbc.execute("ALTER TABLE ai_scoped_source_events DROP CONSTRAINT deny_create");assertThat(create(memory,auth(7,"caller"))).isTrue();denied(memory,auth(7,"caller"));
    }
    @Test void stringIdentityIsNoLongerACreateApi() {
        for(String name:List.of("createKnowledge","createMemory"))assertThat(Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals(name)).map(m->m.getParameterTypes()[1])).containsExactly(Authentication.class);
    }
    @Test void invalidPrincipalsAndMissingPolicyStopBeforeJdbc()throws Exception {
        var observed=mock(NamedParameterJdbcOperations.class);service=new ScopedAiSourceService(observed,flags);assertThat(create(false,UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()))).isFalse();assertThat(create(true,null)).isFalse();service=new ScopedAiSourceService(observed,null);assertThat(create(false,auth(7,"caller"))).isFalse();verifyNoInteractions(observed);
    }
    private void mutation(String kind) {
        if(List.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").contains(kind))replace(jdbc,kind);
        else if(kind.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");else if(kind.equals("unknown"))jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=7");else if(kind.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
    }
}
