package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.*;
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
import static com.example.panel.support.AiControlIdentityFixture.SCOPE;
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Frozen bridge exercises the old login API and its bound-session replacement. */
class AiSourceReadIdentityPostgresTest {
    enum Read { availableBusinesses,canConfigure,catalog,find,history }
    private static JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private ScopedAiSourceService service;
    @BeforeAll static void schemaOnce()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_source_read_identity");schema(jdbc,false);
        jdbc.execute("ALTER TABLE businesses ADD COLUMN display_name TEXT DEFAULT 'Private business'");
    }
    @BeforeEach void setup() {
        seed(jdbc,"caller","unused");flags=new MultibusinessFeatureFlags();service=new ScopedAiSourceService(jdbc,flags);
        assertThat(service.createKnowledge(11L,auth(7,"caller"),KEY,"PRIVATE Knowledge title","private reviewed answer")).isTrue();
        assertThat(service.createMemory(SCOPE,auth(7,"caller"),KEY,"PRIVATE Memory title","private reviewed memory")).isTrue();
    }
    private Object read(Read operation,Authentication actor)throws Exception {
        var method=Arrays.stream(ScopedAiSourceService.class.getMethods()).filter(m->m.getName().equals(operation.name())).findFirst().orElseThrow();
        int index=operation==Read.availableBusinesses?0:1;
        Object identity=method.getParameterTypes()[index]==Authentication.class?actor:actor==null?null:actor.getName();
        Object[] args=switch(operation){case availableBusinesses->new Object[]{identity};case canConfigure->new Object[]{11L,identity};case catalog->new Object[]{11L,identity,KNOWLEDGE,null,"",null,25};case find->new Object[]{11L,identity,KNOWLEDGE,KEY};case history->new Object[]{11L,identity,KNOWLEDGE,KEY,10};};
        try{return method.invoke(service,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private boolean visible(Read operation,Authentication actor)throws Exception {
        Object value=read(operation,actor);if(value instanceof Boolean flag)return flag;if(value instanceof Optional<?> optional)return optional.isPresent();return !((List<?>)value).isEmpty();
    }
    private void denied(Read operation,Authentication actor)throws Exception {
        var before=snapshot(jdbc);assertThat(visible(operation,actor)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    private static Stream<Arguments> cases(String... choices){return Arrays.stream(choices).flatMap(c->Arrays.stream(Read.values()).map(r->Arguments.of(c,r)));}
    static Stream<Arguments> identities(){return cases("wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse");}
    @ParameterizedTest @MethodSource("identities")
    void aLoginCannotSelectAnotherReader(String change,Read operation)throws Exception {
        long id=switch(change){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(change.equals("case-change")||change.endsWith("reuse"))replace(jdbc,change);
        if(change.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        denied(operation,auth(id,name));
    }
    static Stream<Arguments> invalidAuth(){return cases("unbound","anonymous","unauthenticated","disabled-principal","null");}
    @ParameterizedTest @MethodSource("invalidAuth")
    void invalidAuthenticationCannotRead(String change,Read operation)throws Exception {
        Authentication actor=switch(change){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};denied(operation,actor);
    }
    static Stream<Arguments> names(){return cases("caller","Caller"," caller ");}
    @ParameterizedTest @MethodSource("names")
    void exactStoredRawNamesRemainReadable(String name,Read operation)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var before=snapshot(jdbc);assertThat(visible(operation,auth(7,name))).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Arrays.stream(Read.values()).map(r->Arguments.of(id,r)));}
    @ParameterizedTest @MethodSource("ids")
    void exactBigintIdentityRemainsReadable(long id,Read operation)throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);var before=snapshot(jdbc);assertThat(visible(operation,auth(id,"caller"))).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> late(){return cases("rename-reuse","delete-reuse","case-change","disabled","blocked","membership","inactive","ambiguous","archived");}
    @ParameterizedTest @MethodSource("late")
    void eachReadRechecksIdentityAndRightsAtItsSqlBoundary(String change,Read operation)throws Exception {
        var fired=new java.util.concurrent.atomic.AtomicBoolean();var delegate=new NamedParameterJdbcTemplate(jdbc);
        var observed=(NamedParameterJdbcOperations)Proxy.newProxyInstance(NamedParameterJdbcOperations.class.getClassLoader(),new Class<?>[]{NamedParameterJdbcOperations.class},(proxy,method,args)->{
            if(method.getName().startsWith("query")&&fired.compareAndSet(false,true))mutation(change);
            try{return method.invoke(delegate,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
        service=new ScopedAiSourceService(observed,flags);denied(operation,auth(7,"caller"));assertThat(fired).isTrue();
    }
    static Stream<Arguments> roles(){return cases("VIEWER","OPERATOR","MANAGER","ADMIN","ALL_ADMIN");}
    @ParameterizedTest @MethodSource("roles")
    void readAndConfigureKeepTheirExistingDistinctRoleRules(String role,Read operation)throws Exception {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=7");if(role.equals("ALL_ADMIN"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES','ADMIN')");else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);
        var before=snapshot(jdbc);assertThat(visible(operation,auth(7,"caller"))).isEqualTo(operation!=Read.canConfigure||role.endsWith("ADMIN"));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void noReadApiAcceptsAStringOnlyIdentity() {
        for(Read operation:Read.values()){int index=operation==Read.availableBusinesses?0:1;assertThat(Arrays.stream(ScopedAiSourceService.class.getMethods()).filter(m->m.getName().equals(operation.name())).map(m->m.getParameterTypes()[index])).containsExactly(Authentication.class);}
    }
    @Test void invalidActorsAndMissingPolicyStopBeforeJdbc()throws Exception {
        var observed=mock(NamedParameterJdbcOperations.class);service=new ScopedAiSourceService(observed,flags);for(Read operation:Read.values()){assertThat(visible(operation,null)).isFalse();assertThat(visible(operation,UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()))).isFalse();}service=new ScopedAiSourceService(observed,null);for(Read operation:Read.values())assertThat(visible(operation,auth(7,"caller"))).isFalse();verifyNoInteractions(observed);
    }
    @Test void readPayloadsAndHistoryStayBusinessScopedAndReadOnly()throws Exception {
        var before=snapshot(jdbc);assertThat(((List<?>)read(Read.availableBusinesses,auth(7,"caller")))).hasSize(1);
        assertThat(((List<?>)read(Read.catalog,auth(7,"caller")))).hasSize(1).allSatisfy(row->assertThat(row.toString()).doesNotContain("private reviewed answer",SCOPE.ticketId()));
        var found=(Optional<?>)read(Read.find,auth(7,"caller"));assertThat(found).isPresent();assertThat(((ScopedAiSourceService.Source)found.orElseThrow()).body()).isEqualTo("private reviewed answer");assertThat(((List<?>)read(Read.history,auth(7,"caller")))).hasSize(1);assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    private void mutation(String change) {
        if(List.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").contains(change))replace(jdbc,change);
        else if(change.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");else if(change.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
    }
}
