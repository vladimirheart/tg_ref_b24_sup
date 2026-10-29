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
import static com.example.panel.service.ScopedAiSourceService.Kind.*;
import static com.example.panel.service.ScopedAiSourceService.Status.*;
import static com.example.panel.support.AiControlIdentityFixture.SCOPE;
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Frozen against the login-based baseline and the session-bound replacement. */
class AiSourceReviseIdentityPostgresTest {
    private static JdbcTemplate jdbc;
    private ScopedAiSourceService service;
    private MultibusinessFeatureFlags flags;
    @BeforeAll static void schemaOnce()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_source_revise_identity");schema(jdbc,false);
    }
    @BeforeEach void setup() {
        jdbc.execute("ALTER TABLE ai_scoped_source_events DROP CONSTRAINT IF EXISTS deny_revise");
        seed(jdbc,"caller","unused");flags=new MultibusinessFeatureFlags();service=new ScopedAiSourceService(jdbc,flags);
        assertThat(service.createKnowledge(11L,auth(7,"caller"),KEY,"Original title","Original body")).isTrue();
        assertThat(service.createMemory(SCOPE,auth(7,"caller"),KEY,"Original title","Original body")).isTrue();
    }
    private long sourceId(boolean memory) {return jdbc.queryForObject("SELECT id FROM ai_scoped_sources WHERE source_kind=?",Long.class,memory?"MEMORY":"KNOWLEDGE");}
    private boolean revise(boolean expected,boolean memory,Authentication actor)throws Exception {
        return revise(expected,memory,actor,11L,KEY,sourceId(memory),1,"  Reviewed title  ","  safe curated answer  ",APPROVED);
    }
    private boolean revise(boolean expected,boolean memory,Authentication actor,Long business,String key,long id,long revision,String title,String body,ScopedAiSourceService.Status status)throws Exception {
        String name=expected?"reviseExpected":"revise";var kind=memory?MEMORY:KNOWLEDGE;
        var method=Arrays.stream(ScopedAiSourceService.class.getMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();
        Object identity=method.getParameterTypes()[1]==Authentication.class?actor:actor==null?null:actor.getName();
        Object[] args=expected?new Object[]{business,identity,kind,key,id,revision,title,body,status}:new Object[]{business,identity,kind,key,revision,title,body,status};
        try{return (Boolean)method.invoke(service,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private void denied(boolean expected,boolean memory,Authentication actor)throws Exception {
        var before=snapshot(jdbc);assertThat(revise(expected,memory,actor)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    private static Stream<Arguments> cases(String... choices){return Arrays.stream(choices).flatMap(c->Stream.of(false,true).flatMap(e->Stream.of(false,true).map(m->Arguments.of(c,e,m))));}
    static Stream<Arguments> identities(){return cases("wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse");}
    @ParameterizedTest @MethodSource("identities")
    void theLoginCannotSelectAnotherReviewer(String change,boolean expected,boolean memory)throws Exception {
        long id=switch(change){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(change.equals("case-change")||change.endsWith("reuse"))replace(jdbc,change);
        if(change.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        denied(expected,memory,auth(id,name));
    }
    static Stream<Arguments> invalidAuth(){return cases("unbound","anonymous","unauthenticated","disabled-principal","null");}
    @ParameterizedTest @MethodSource("invalidAuth")
    void invalidAuthenticationCannotReview(String change,boolean expected,boolean memory)throws Exception {
        Authentication actor=switch(change){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};
        denied(expected,memory,actor);
    }
    static Stream<Arguments> names(){return cases("caller","Caller"," caller ","имя","anonymousUser","9007199254740993");}
    @ParameterizedTest @MethodSource("names")
    void reviewAndAuditKeepTheExactRawReviewer(String name,boolean expected,boolean memory)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(revise(expected,memory,auth(7,name))).isTrue();
        var row=jdbc.queryForMap("SELECT * FROM ai_scoped_sources WHERE id=?",sourceId(memory));
        assertThat(row).containsEntry("updated_by_user_id",7L).containsEntry("updated_by",name).containsEntry("revision",2L).containsEntry("status","APPROVED").containsEntry("title","Reviewed title").containsEntry("body","safe curated answer");
        assertThat(row).containsEntry("origin_ticket_user_id",memory?42L:null).containsEntry("origin_ticket_id",memory?SCOPE.ticketId():null).containsEntry("origin_ticket_public_id",memory?SCOPE.ticketPublicId():null).containsEntry("origin_ticket_scope_kind",memory?"BUSINESS":null).containsEntry("origin_ticket_business_id",memory?11L:null);
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,revision,status FROM ai_scoped_source_events WHERE action='revise'")).containsEntry("actor_user_id",7L).containsEntry("actor",name).containsEntry("revision",2L).containsEntry("status","APPROVED");
        assertThat(jdbc.queryForObject("SELECT revision FROM ai_scoped_sources WHERE id<>?",Long.class,sourceId(memory))).isEqualTo(1L);
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_memory_evidence")).isEmpty();
    }
    static Stream<Arguments> late(){return cases("rename-reuse","delete-reuse","case-change","disabled","blocked","membership","inactive","ambiguous","archived");}
    @ParameterizedTest @MethodSource("late")
    void theUpdateRechecksIdentityAndAuthorityInItsOwnStatement(String change,boolean expected,boolean memory)throws Exception {
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));var before=snapshot(jdbc);
        doAnswer(call->{mutation(change);return new NamedParameterJdbcTemplate(jdbc).update(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class));}).when(observed).update(anyString(),any(SqlParameterSource.class));
        service=new ScopedAiSourceService(observed,flags);assertThat(revise(expected,memory,auth(7,"caller"))).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> roles(){return cases("VIEWER","OPERATOR","MANAGER","ADMIN","ALL_ADMIN","ALL_VIEWER","UNKNOWN");}
    @ParameterizedTest @MethodSource("roles")
    void onlyCurrentBusinessAdminCanReview(String role,boolean expected,boolean memory)throws Exception {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=7");if(role.startsWith("ALL_"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role.substring(4));else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);
        var before=snapshot(jdbc);assertThat(revise(expected,memory,auth(7,"caller"))).isEqualTo(role.endsWith("ADMIN"));if(!role.endsWith("ADMIN"))assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> invalidTargets(){return cases("foreign-business","missing-business","null-business","wrong-key","stale-revision","zero-revision","blank-title","blank-body","null-status");}
    @ParameterizedTest @MethodSource("invalidTargets")
    void invalidScopeContentAndCasCannotChangeEitherSource(String change,boolean expected,boolean memory)throws Exception {
        var before=snapshot(jdbc);Long business=switch(change){case "foreign-business"->22L;case "missing-business"->404L;case "null-business"->null;default->11L;};
        assertThat(revise(expected,memory,auth(7,"caller"),business,change.equals("wrong-key")?"missing":KEY,sourceId(memory),change.equals("stale-revision")?2:change.equals("zero-revision")?0:1,change.equals("blank-title")?" ":"Reviewed",change.equals("blank-body")?" ":"Reviewed",change.equals("null-status")?null:APPROVED)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void expectedSourceIdCannotPointToAnotherKindOrAnInvalidId(boolean memory)throws Exception {
        var before=snapshot(jdbc);for(long id:List.of(0L,-1L,sourceId(!memory),Long.MAX_VALUE))assertThat(revise(true,memory,auth(7,"caller"),11L,KEY,id,1,"Reviewed","Reviewed",APPROVED)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> origins(){return Stream.of("owner","raw","public","business","general","ambiguous").flatMap(c->Stream.of(false,true).map(e->Arguments.of(c,e)));}
    @ParameterizedTest @MethodSource("origins")
    void memoryStillNeedsItsExactCurrentUniqueProvenance(String change,boolean expected)throws Exception {
        switch(change){case "owner"->jdbc.update("UPDATE tickets SET user_id=43");case "raw"->jdbc.update("UPDATE tickets SET ticket_id='moved'");case "public"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));case "business"->jdbc.update("UPDATE tickets SET business_id=22");case "general"->jdbc.update("UPDATE tickets SET is_general=TRUE");default->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(43,?,?,11)",SCOPE.ticketId(),"b".repeat(32));}
        denied(expected,true,auth(7,"caller"));assertThat(revise(expected,false,auth(7,"caller"))).isTrue();
    }
    static Stream<Arguments> ids(){return Stream.of(7L,9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).map(m->Arguments.of(id,m)));}
    @ParameterizedTest @MethodSource("ids")
    void exactBigintReviewerSurvivesApprovalAndArchive(long id,boolean memory)throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);assertThat(revise(true,memory,auth(id,"caller"))).isTrue();
        assertThat(revise(false,memory,auth(id,"caller"),11L,KEY,sourceId(memory),2,"Archived title","Archived body",ARCHIVED)).isTrue();
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,status,revision FROM ai_scoped_sources WHERE id=?",sourceId(memory))).containsEntry("updated_by_user_id",id).containsEntry("status","ARCHIVED").containsEntry("revision",3L);
        assertThat(jdbc.queryForList("SELECT actor_user_id FROM ai_scoped_source_events WHERE action='revise'",Long.class)).containsExactly(id,id);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void auditFailureAndDuplicateApprovalAreAtomic(boolean memory)throws Exception {
        jdbc.execute("ALTER TABLE ai_scoped_source_events ADD CONSTRAINT deny_revise CHECK(action<>'revise')");denied(true,memory,auth(7,"caller"));jdbc.execute("ALTER TABLE ai_scoped_source_events DROP CONSTRAINT deny_revise");assertThat(revise(true,memory,auth(7,"caller"))).isTrue();denied(true,memory,auth(7,"caller"));
    }
    @Test void onlyBoundAuthenticationIsAPublicReviewApi() {
        for(String name:List.of("revise","reviseExpected"))assertThat(Arrays.stream(ScopedAiSourceService.class.getMethods()).filter(m->m.getName().equals(name)).map(m->m.getParameterTypes()[1])).containsExactly(Authentication.class);
    }
    @Test void invalidAuthenticationStopsBeforeJdbc()throws Exception {
        var observed=mock(NamedParameterJdbcOperations.class);service=new ScopedAiSourceService(observed,flags);assertThat(revise(true,false,null)).isFalse();assertThat(revise(false,true,UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()))).isFalse();service=new ScopedAiSourceService(observed,null);assertThat(revise(true,false,auth(7,"caller"))).isFalse();verifyNoInteractions(observed);
    }
    private void mutation(String change) {
        if(List.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").contains(change))replace(jdbc,change);
        else if(change.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");else if(change.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
    }
}
