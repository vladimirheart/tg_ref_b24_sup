package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static com.example.panel.support.AiControlIdentityFixture.SCOPE;
import static com.example.panel.support.AiControlIdentityFixture.RAW;
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The same assertions execute against the original String API and the bound-session API. */
class AiMemoryActorIdentityPostgresTest {
    private JdbcTemplate jdbc;
    private ScopedDialogAiWorkbenchService service;
    private String questionHash,answerHash;
    @BeforeEach void setup()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_memory_identity");schema(jdbc,false);seed(jdbc,"caller","unused");service=new ScopedDialogAiWorkbenchService(jdbc);questionHash=fingerprint(jdbc,40);answerHash=fingerprint(jdbc,41);
    }
    private boolean create(UiEventTicketScope scope,Authentication actor,String key)throws Exception {
        var types=new Class<?>[]{UiEventTicketScope.class,Authentication.class,String.class,long.class,long.class,String.class,String.class,String.class,String.class,boolean.class};
        try {
            try {return (Boolean)service.getClass().getMethod("createMemory",types).invoke(service,scope,actor,key,40L,41L,questionHash,answerHash,"  Reviewed title  ","  safe curated answer  ",true);}
            catch(NoSuchMethodException oldApi){types[1]=String.class;return (Boolean)service.getClass().getMethod("createMemory",types).invoke(service,scope,actor==null?null:actor.getName(),key,40L,41L,questionHash,answerHash,"  Reviewed title  ","  safe curated answer  ",true);}
        } catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private boolean create(Authentication actor)throws Exception {return create(SCOPE,actor,KEY);}
    private void denied(Authentication actor)throws Exception {var before=snapshot(jdbc);assertThat(create(actor)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);}
    @ParameterizedTest @ValueSource(strings={"wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse"})
    void currentOrReusedLoginCannotReplaceTheMemoryAuthor(String kind)throws Exception {
        long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-change")||kind.endsWith("reuse"))replace(jdbc,kind);
        if(kind.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        denied(auth(id,name));
    }
    @ParameterizedTest @ValueSource(strings={"unbound","anonymous","unauthenticated","disabled-principal","null"})
    void invalidAuthenticationCannotCreateMemory(String kind)throws Exception {
        Authentication actor=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};denied(actor);
    }
    @ParameterizedTest @ValueSource(strings={"caller","Caller"," caller ","имя","anonymousUser","9007199254740993"})
    void literalAuthorIsPreservedInBothSourceAndAudit(String name)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(create(auth(7,name))).isTrue();
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by,source_kind,status,revision,title,body,origin_ticket_user_id,origin_ticket_id,origin_ticket_public_id,origin_ticket_scope_kind,origin_ticket_business_id FROM ai_scoped_sources"))
            .containsEntry("updated_by_user_id",7L).containsEntry("updated_by",name).containsEntry("source_kind","MEMORY").containsEntry("status","DRAFT").containsEntry("revision",1L).containsEntry("title","Reviewed title").containsEntry("body","safe curated answer").containsEntry("origin_ticket_user_id",42L).containsEntry("origin_ticket_id",RAW).containsEntry("origin_ticket_public_id",SCOPE.ticketPublicId()).containsEntry("origin_ticket_scope_kind","BUSINESS").containsEntry("origin_ticket_business_id",11L);
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,action FROM ai_scoped_source_events")).containsEntry("actor_user_id",7L).containsEntry("actor",name).containsEntry("action","create");
        assertThat(jdbc.queryForMap("SELECT question_history_id,answer_history_id,question_fingerprint,answer_fingerprint FROM ai_scoped_memory_evidence")).containsEntry("question_history_id",40L).containsEntry("answer_history_id",41L).containsEntry("question_fingerprint",questionHash).containsEntry("answer_fingerprint",answerHash);
    }
    @ParameterizedTest @ValueSource(longs={7L,9007199254740993L,Long.MAX_VALUE})
    void bigintAuthorIdsAreNotRounded(long id)throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);assertThat(create(auth(id,"caller"))).isTrue();assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM ai_scoped_sources",Long.class)).isEqualTo(id);assertThat(jdbc.queryForObject("SELECT actor_user_id FROM ai_scoped_source_events",Long.class)).isEqualTo(id);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","membership","inactive","unknown","ambiguous","archived"})
    void currentUniqueAccountBusinessAndConfigureGrantRemainRequired(String kind)throws Exception {
        if(List.of("disabled","blocked","membership").contains(kind))replace(jdbc,kind);
        else if(kind.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");else if(kind.equals("unknown"))jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=7");else if(kind.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");denied(auth(7,"caller"));
    }
    static Stream<Arguments> roles(){return Stream.of("VIEWER","OPERATOR","MANAGER","ADMIN","UNKNOWN").flatMap(r->Stream.of("membership","all").map(g->Arguments.of(r,g)));}
    @ParameterizedTest @MethodSource("roles")
    void onlyAdminCanConfigureMemoryWithEitherGrantKind(String role,String grant)throws Exception {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=7");if(grant.equals("membership"))jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);else jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role);
        var before=snapshot(jdbc);assertThat(create(auth(7,"caller"))).isEqualTo(role.equals("ADMIN"));if(!role.equals("ADMIN"))assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"business","generation","tuple","general","collision","missing","unresolved"})
    void fullOwnerAndCollisionDenialRemainRequired(String kind)throws Exception {
        switch(kind){case "business"->jdbc.update("UPDATE tickets SET business_id=22");case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));case "tuple"->jdbc.update("UPDATE tickets SET user_id=88");case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE");case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(88,?,?,22)",RAW,"b".repeat(32));case "missing"->jdbc.update("DELETE FROM tickets");default->jdbc.update("UPDATE tickets SET business_id=NULL");}denied(auth(7,"caller"));
    }
    @ParameterizedTest @ValueSource(strings={"audit","evidence"})
    void sourceAuditAndEvidenceStillRollBackTogether(String table)throws Exception {
        jdbc.execute(table.equals("audit")?"ALTER TABLE ai_scoped_source_events ADD CONSTRAINT deny_create CHECK(action<>'create')":"ALTER TABLE ai_scoped_memory_evidence ADD CONSTRAINT deny_evidence CHECK(source_id<0)");denied(auth(7,"caller"));
    }
    @Test void aRepeatedKeyCannotOverwriteAnyPartOfAReviewedDraft()throws Exception {
        assertThat(create(auth(7,"caller"))).isTrue();denied(auth(7,"caller"));
    }
    @Test void publicMemoryApiHasNoStringIdentityOverload() {
        assertThat(Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals("createMemory")).map(m->m.getParameterTypes()[1])).containsExactly(Authentication.class);
    }
    @Test void badAuthenticationAndInvalidScopeStopBeforeJdbc()throws Exception {
        var observed=mock(JdbcTemplate.class);service=new ScopedDialogAiWorkbenchService(observed);
        assertThat(create(UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()))).isFalse();assertThat(create(null,auth(7,"caller"),KEY)).isFalse();verifyNoInteractions(observed);
    }
}
