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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
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

/** Frozen assertions exercise name-only APIs and their session-bound replacements. */
class AiEvidenceReadIdentityPostgresTest {
    enum Read { retrieve,isCurrentCandidate,candidates }
    private static JdbcTemplate jdbc;
    private static SingleConnectionDataSource connection;
    private MultibusinessFeatureFlags flags;
    private ScopedAiSourceService sources;
    private ScopedAiHandoffKnowledgeService handoff;
    private ScopedAiSourceService.Source expected;
    private static final UiEventTicketScope ORIGIN=new UiEventTicketScope(84L," origin/Ю?% ","b".repeat(32),"BUSINESS",11L);
    @BeforeAll static void schemaOnce()throws Exception {
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_evidence_read_identity");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("ai_evidence_read_identity_");
        assertThat(connection.getConnection().getAutoCommit()).isTrue();schema(jdbc,false);
    }
    @AfterAll static void closeConnection(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup() {
        seed(jdbc,"caller","unused");flags=new MultibusinessFeatureFlags();sources=new ScopedAiSourceService(jdbc,flags);
        handoff=new ScopedAiHandoffKnowledgeService(jdbc,flags,new ScopedDialogAiPersistenceService(jdbc,flags));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(84,?,?,11)",ORIGIN.ticketId(),ORIGIN.ticketPublicId());
        assertThat(sources.createKnowledge(11L,auth(7,"caller"),KEY,"Knowledge title","refund safe knowledge")).isTrue();
        assertThat(sources.createMemory(ORIGIN,auth(7,"caller"),KEY,"Memory title","refund safe memory")).isTrue();
        for(var kind:List.of(KNOWLEDGE,MEMORY))assertThat(sources.revise(11L,auth(7,"caller"),kind,KEY,1,"Reviewed title",kind==MEMORY?"refund safe memory":"refund safe knowledge",APPROVED)).isTrue();
        expected=sources.find(11L,auth(7,"caller"),MEMORY,KEY).orElseThrow();assertThat(handoff.record(SCOPE,"refund")).isTrue();
    }
    private Object read(Read operation,Authentication actor)throws Exception {
        Object target=operation==Read.candidates?handoff:sources;var method=Arrays.stream(target.getClass().getMethods()).filter(m->m.getName().equals(operation.name())).findFirst().orElseThrow();
        Object identity=method.getParameterTypes()[1]==Authentication.class?actor:actor==null?null:actor.getName();
        Object[] args=switch(operation){case retrieve->new Object[]{SCOPE,identity,"refund",10};case isCurrentCandidate->new Object[]{SCOPE,identity,expected};default->new Object[]{SCOPE,identity};};
        try{return method.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private boolean visible(Read operation,Authentication actor)throws Exception {Object result=read(operation,actor);return result instanceof Boolean value?value:!((List<?>)result).isEmpty();}
    private void denied(Read operation,Authentication actor)throws Exception {var before=snapshot(jdbc);assertThat(visible(operation,actor)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);}
    private static Stream<Arguments> cases(String... choices){return Arrays.stream(choices).flatMap(c->Arrays.stream(Read.values()).map(r->Arguments.of(c,r)));}
    static Stream<Arguments> identities(){return cases("wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse");}
    @ParameterizedTest @MethodSource("identities")
    void aNameCannotSelectAnotherEvidenceReader(String change,Read operation)throws Exception {
        long id=switch(change){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(change.equals("case-change")||change.endsWith("reuse"))replace(jdbc,change);
        if(change.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        denied(operation,auth(id,name));
    }
    static Stream<Arguments> invalidAuth(){return cases("unbound","anonymous","unauthenticated","disabled-principal","null");}
    @ParameterizedTest @MethodSource("invalidAuth")
    void invalidAuthenticationCannotReadEvidence(String kind,Read operation)throws Exception {
        Authentication actor=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};denied(operation,actor);
    }
    static Stream<Arguments> names(){return cases("caller","Caller"," caller ","имя","anonymousUser");}
    @ParameterizedTest @MethodSource("names")
    void theExactRawAccountRetainsReadAccess(String name,Read operation)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var before=snapshot(jdbc);assertThat(visible(operation,auth(7,name))).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Arrays.stream(Read.values()).map(r->Arguments.of(id,r)));}
    @ParameterizedTest @MethodSource("ids")
    void bigintReaderIdsRemainExact(long id,Read operation)throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);var before=snapshot(jdbc);assertThat(visible(operation,auth(id,"caller"))).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> late(){return cases("rename-reuse","delete-reuse","case-change","disabled","blocked","membership","inactive","ambiguous","archived");}
    @ParameterizedTest @MethodSource("late")
    void eachSqlStatementRechecksTheFrozenActor(String change,Read operation)throws Exception {
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));var fired=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.stubbing.Answer<Object> query=call->{if(fired.compareAndSet(false,true))mutation(change);return call.callRealMethod();};
        doAnswer(query).when(observed).query(anyString(),any(SqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        doAnswer(query).when(observed).queryForObject(anyString(),any(SqlParameterSource.class),eq(Boolean.class));
        sources=new ScopedAiSourceService(observed,flags);handoff=observedHandoff(observed);denied(operation,auth(7,"caller"));assertThat(fired).isTrue();
    }
    private ScopedAiHandoffKnowledgeService observedHandoff(NamedParameterJdbcTemplate observed)throws Exception {
        var persistence=new ScopedDialogAiPersistenceService(jdbc,flags);
        try{var constructor=ScopedAiHandoffKnowledgeService.class.getDeclaredConstructor(NamedParameterJdbcOperations.class,MultibusinessFeatureFlags.class,ScopedDialogAiPersistenceService.class);constructor.setAccessible(true);return constructor.newInstance(observed,flags,persistence);}
        catch(NoSuchMethodException oldApi){var original=new ScopedAiHandoffKnowledgeService(jdbc,flags,persistence);var field=ScopedAiHandoffKnowledgeService.class.getDeclaredField("jdbc");field.setAccessible(true);field.set(original,observed);return original;}
    }
    static Stream<Arguments> roles(){return cases("VIEWER","OPERATOR","MANAGER","ADMIN","ALL_VIEWER","ALL_ADMIN");}
    @ParameterizedTest @MethodSource("roles")
    void allExistingReadRolesRemainSufficient(String role,Read operation)throws Exception {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=7");if(role.startsWith("ALL_"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role.substring(4));else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);
        var before=snapshot(jdbc);assertThat(visible(operation,auth(7,"caller"))).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> unicode(){return cases("disabled","blocked","no-rights");}
    @ParameterizedTest @MethodSource("unicode")
    void aFoldedNeighbourCannotSupplyAccountStatusOrRights(String status,Read operation)throws Exception {
        String name="\u0130";jdbc.update("UPDATE users SET username=? WHERE id=7",name);jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name.toLowerCase(Locale.ROOT));jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");
        assertThat(jdbc.queryForObject("SELECT lower(?)",String.class,name)).isNotEqualTo(name.toLowerCase(Locale.ROOT));
        if(status.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");else if(status.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");else jdbc.update("DELETE FROM business_memberships WHERE user_id=7");denied(operation,auth(7,name));
    }
    static Stream<Arguments> owners(){return cases("business","generation","tuple","general","collision","missing","unresolved");}
    @ParameterizedTest @MethodSource("owners")
    void staleScopeAndLegacyCollisionsDoNotExposeEvidence(String change,Read operation)throws Exception {
        switch(change){case "business"->jdbc.update("UPDATE tickets SET business_id=22 WHERE user_id=42");case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=42","c".repeat(32));case "tuple"->jdbc.update("UPDATE tickets SET user_id=88 WHERE user_id=42");case "general"->jdbc.update("UPDATE tickets SET is_general=TRUE,business_id=NULL WHERE user_id=42");case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(88,?,?,22)",SCOPE.ticketId(),"c".repeat(32));case "missing"->jdbc.update("DELETE FROM tickets WHERE user_id=42");default->jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=42");}denied(operation,auth(7,"caller"));
    }
    @Test void anOriginChangeFiltersMemoryWithoutHidingValidKnowledge()throws Exception {
        jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=84","d".repeat(32));var before=snapshot(jdbc);
        assertThat((List<?>)read(Read.retrieve,auth(7,"caller"))).hasSize(1);assertThat(visible(Read.isCurrentCandidate,auth(7,"caller"))).isFalse();assertThat((List<?>)read(Read.candidates,auth(7,"caller"))).hasSize(1);assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void allBusinessReadStaysScopedAndCandidateSummariesDoNotContainBodies()throws Exception {
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES','ADMIN')");
        assertThat(sources.createKnowledge(22L,auth(99,"foreign"),KEY,"Foreign title","refund FOREIGN PRIVATE BODY")).isTrue();assertThat(sources.revise(22L,auth(99,"foreign"),KNOWLEDGE,KEY,1,"Foreign title","refund FOREIGN PRIVATE BODY",APPROVED)).isTrue();
        var before=snapshot(jdbc);var result=(List<?>)read(Read.retrieve,auth(7,"caller"));assertThat(result).hasSize(2).allSatisfy(v->assertThat(((ScopedAiSourceService.Source)v).businessId()).isEqualTo(11L));
        var candidates=(List<?>)read(Read.candidates,auth(7,"caller"));assertThat(candidates).hasSize(2);assertThat(candidates.toString()).doesNotContain("refund safe knowledge","refund safe memory","FOREIGN",ORIGIN.ticketId());assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"valid","wrong-id","unbound","no-source"})
    void theScopedFacadeRetainsAuthenticationAndNeverEntersLegacyRetrieval(String mode)throws Exception {
        var legacy=mock(JdbcTemplate.class);var intents=mock(AiIntentService.class);var timestamps=mock(com.example.panel.support.PanelTimestampSqlSupport.class);
        var facade=mode.equals("no-source")?new AiRetrievalService(legacy,intents,timestamps):new AiRetrievalService(legacy,intents,timestamps,sources);
        Authentication actor=mode.equals("unbound")?UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()):auth(mode.equals("wrong-id")?99:7,"caller");
        var method=Arrays.stream(AiRetrievalService.class.getMethods()).filter(m->m.getName().equals("retrieveScoped")).findFirst().orElseThrow();Object identity=method.getParameterTypes()[1]==Authentication.class?actor:actor.getName();
        var before=snapshot(jdbc);var result=(AiRetrievalService.ScopedRetrievalResult)method.invoke(facade,SCOPE,identity,"refund",10);assertThat(result.scope()).isEqualTo(SCOPE);assertThat(result.autoReplyAllowed()).isFalse();assertThat(result.candidates()).hasSize(mode.equals("valid")?2:0);verifyNoInteractions(legacy,intents,timestamps);assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void stringOnlyEvidenceApisAreClosed() {
        for(Read operation:Read.values()){Class<?> type=operation==Read.candidates?ScopedAiHandoffKnowledgeService.class:ScopedAiSourceService.class;assertThat(Arrays.stream(type.getMethods()).filter(m->m.getName().equals(operation.name())).map(m->m.getParameterTypes()[1])).containsExactly(Authentication.class);}
        assertThat(Arrays.stream(AiRetrievalService.class.getMethods()).filter(m->m.getName().equals("retrieveScoped")).map(m->m.getParameterTypes()[1])).containsExactly(Authentication.class);
    }
    @Test void invalidActorsStopBeforeJdbc()throws Exception {
        var observed=mock(NamedParameterJdbcTemplate.class);sources=new ScopedAiSourceService(observed,flags);handoff=observedHandoff(observed);
        for(Read operation:Read.values()){assertThat(visible(operation,null)).isFalse();assertThat(visible(operation,UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()))).isFalse();}verifyNoInteractions(observed);
    }
    private void mutation(String change) {
        if(List.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").contains(change))replace(jdbc,change);
        else if(change.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");else if(change.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
    }
}
