package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import static com.example.panel.support.AiControlIdentityFixture.*;
import static com.example.panel.support.AiMemoryIdentityFixture.KEY;
import static com.example.panel.support.AiMemoryIdentityFixture.fingerprint;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A Java-folded neighbour must never authorize a different session ID in an AI SQL statement. */
class AiWorkbenchFoldIdentityPostgresTest {
    enum Operation { resolve,messages,createMemory,findControl,canOperate,findState,findEvents,
        insertControl,updateControl,insertExpected,updateExpected }
    private static final String FOLDED_RAW="\u0130";
    private static JdbcTemplate jdbc;
    private static SingleConnectionDataSource connection;
    private ScopedDialogAiWorkbenchService workbench;
    private ScopedDialogAiPersistenceService persistence;
    private String questionHash,answerHash,stamp;
    private long actorId=7;
    @BeforeAll static void schemaOnce()throws Exception {
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_workbench_fold_identity");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("ai_workbench_fold_identity_");
        assertThat(connection.getConnection().getAutoCommit()).isTrue();
        com.example.panel.support.AiMemoryIdentityFixture.schema(jdbc,false);
    }
    @AfterAll static void closeConnection(){if(connection!=null)connection.destroy();}
    @BeforeEach void seed() {
        com.example.panel.support.AiMemoryIdentityFixture.seed(jdbc,"caller","unused");actorId=7;
        workbench=new ScopedDialogAiWorkbenchService(jdbc);persistence=new ScopedDialogAiPersistenceService(jdbc,new MultibusinessFeatureFlags());
        stamp=existing(jdbc);jdbc.update("UPDATE ticket_ai_scoped_control SET reason='PRIVATE reason'");
        jdbc.update("INSERT INTO ticket_ai_scoped_state(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,is_processing,mode,last_action) VALUES(42,?,?,'BUSINESS',11,FALSE,'escalate_only','PRIVATE state')",RAW,PUBLIC);
        jdbc.update("INSERT INTO ticket_ai_scoped_events(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,event_type,decision_reason) VALUES(42,?,?,'BUSINESS',11,'control_updated','PRIVATE event')",RAW,PUBLIC);
        questionHash=fingerprint(jdbc,40);answerHash=fingerprint(jdbc,41);
    }
    private void prepare(Operation operation){if(operation==Operation.insertControl||operation==Operation.insertExpected)jdbc.update("DELETE FROM ticket_ai_scoped_control");}
    private Object call(Operation operation,Authentication actor) {
        return switch(operation){
            case resolve->workbench.resolve(PUBLIC,actor);
            case messages->workbench.messages(SCOPE,actor);
            case createMemory->workbench.createMemory(SCOPE,actor,KEY,40,41,questionHash,answerHash,"Reviewed title","Reviewed answer",true);
            case findControl->persistence.findControl(SCOPE,actor);
            case canOperate->persistence.canOperate(SCOPE,actor);
            case findState->persistence.findState(SCOPE,actor);
            case findEvents->persistence.findEvents(SCOPE,actor,50);
            case insertControl,updateControl->persistence.setControl(SCOPE,actor,true,true,"Changed reason");
            case insertExpected->persistence.setControlExpected(SCOPE,actor,null,true,true,"Changed reason");
            case updateExpected->persistence.setControlExpected(SCOPE,actor,stamp,true,true,"Changed reason");
        };
    }
    private boolean visible(Object result){return result instanceof Boolean value?value:result instanceof Optional<?> value?value.isPresent():!((List<?>)result).isEmpty();}
    private boolean writes(Operation operation){return switch(operation){case createMemory,insertControl,updateControl,insertExpected,updateExpected->true;default->false;};}
    private Map<String,List<Map<String,Object>>> snapshot(){return com.example.panel.support.AiMemoryIdentityFixture.snapshot(jdbc);}
    private void assertResult(Operation operation,Authentication actor,boolean allowed) {
        var before=snapshot();var result=call(operation,actor);assertThat(visible(result)).as(operation.name()).isEqualTo(allowed);
        if(!allowed||!writes(operation))assertThat(snapshot()).isEqualTo(before);
        if(!allowed)return;
        switch(operation){
            case resolve->assertThat(result).isEqualTo(Optional.of(SCOPE));
            case messages->assertThat((List<?>)result).hasSize(2);
            case findControl->assertThat(((ScopedDialogAiPersistenceService.Control)((Optional<?>)result).orElseThrow()).reason()).isEqualTo("PRIVATE reason");
            case findState->assertThat(((Map<?,?>)((Optional<?>)result).orElseThrow()).get("last_action")).isEqualTo("PRIVATE state");
            case findEvents->assertThat((List<?>)result).hasSize(1);
            case createMemory->{
                assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by,title,body,status,revision FROM ai_scoped_sources"))
                    .containsEntry("updated_by_user_id",actorId).containsEntry("updated_by",actor.getName()).containsEntry("title","Reviewed title")
                    .containsEntry("body","Reviewed answer").containsEntry("status","DRAFT").containsEntry("revision",1L);
                assertThat(jdbc.queryForMap("SELECT actor_user_id,actor FROM ai_scoped_source_events")).containsEntry("actor_user_id",actorId).containsEntry("actor",actor.getName());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_scoped_memory_evidence",Integer.class)).isEqualTo(1);
            }
            case insertControl,updateControl,insertExpected,updateExpected->{
                assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by,reason,ai_disabled,auto_reply_blocked FROM ticket_ai_scoped_control"))
                    .containsEntry("updated_by_user_id",actorId).containsEntry("updated_by",actor.getName()).containsEntry("reason","Changed reason")
                    .containsEntry("ai_disabled",true).containsEntry("auto_reply_blocked",true);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_events",Integer.class)).isEqualTo(operation==Operation.insertExpected||operation==Operation.updateExpected?2:1);
            }
            default->{}
        }
    }
    private static Stream<Arguments> cases(String... values){return Arrays.stream(values).flatMap(v->Arrays.stream(Operation.values()).map(o->Arguments.of(v,o)));}
    private void foldedNeighbour() {
        jdbc.update("UPDATE users SET username=? WHERE id=7",FOLDED_RAW);
        jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",FOLDED_RAW.toLowerCase(Locale.ROOT));
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");
        assertThat(jdbc.queryForObject("SELECT lower(?)",String.class,FOLDED_RAW)).isNotEqualTo(FOLDED_RAW.toLowerCase(Locale.ROOT));
    }
    static Stream<Arguments> neighbour(){return cases("active","disabled","blocked","no-rights");}
    @ParameterizedTest @MethodSource("neighbour")
    void aFoldedNeighbourNeverSuppliesReadStatusOrWriteAuthority(String status,Operation operation) {
        prepare(operation);foldedNeighbour();
        if(status.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");
        else if(status.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");
        else if(status.equals("no-rights"))jdbc.update("DELETE FROM business_memberships WHERE user_id=7");
        assertResult(operation,auth(7,FOLDED_RAW),false);
    }
    @ParameterizedTest @EnumSource(Operation.class)
    void aFoldMismatchWithoutANeighbourAlsoFailsClosed(Operation operation) {
        prepare(operation);jdbc.update("UPDATE users SET username=? WHERE id=7",FOLDED_RAW);assertResult(operation,auth(7,FOLDED_RAW),false);
    }
    static Stream<Arguments> names(){return cases("caller","Caller"," caller ","имя","anonymousUser");}
    @ParameterizedTest @MethodSource("names")
    void validExactAccountsRetainTheirPayloadAndRawAudit(String name,Operation operation) {
        prepare(operation);jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertResult(operation,auth(7,name),true);
    }
    static Stream<Arguments> roles(){return cases("VIEWER","ALL_VIEWER","OPERATOR","ALL_ADMIN");}
    @ParameterizedTest @MethodSource("roles")
    void readOperateAndConfigureRemainDistinct(String role,Operation operation) {
        prepare(operation);jdbc.update("DELETE FROM business_memberships WHERE user_id=7");
        if(role.startsWith("ALL_"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role.substring(4));
        else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);
        boolean allowed=operation==Operation.createMemory?role.equals("ALL_ADMIN"):operation==Operation.canOperate||writes(operation)?!role.endsWith("VIEWER"):true;
        assertResult(operation,auth(7,"caller"),allowed);
    }
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Arrays.stream(Operation.values()).map(o->Arguments.of(id,o)));}
    @ParameterizedTest @MethodSource("ids")
    void bigintActorIdsRemainExact(long id,Operation operation) {
        prepare(operation);actorId=id;jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);assertResult(operation,auth(id,"caller"),true);
    }
    @ParameterizedTest @EnumSource(Operation.class)
    void theSqlStatementRechecksOwnStatusAfterThePrincipalGuard(Operation operation) {
        prepare(operation);foldedNeighbour();var observed=spy(new NamedParameterJdbcTemplate(jdbc));var fired=new AtomicBoolean();
        org.mockito.stubbing.Answer<Object> query=call->{if(fired.compareAndSet(false,true))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");return call.callRealMethod();};
        doAnswer(query).when(observed).query(anyString(),any(SqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        doAnswer(query).when(observed).queryForObject(anyString(),any(SqlParameterSource.class),eq(Boolean.class));
        doAnswer(query).when(observed).queryForList(anyString(),any(SqlParameterSource.class));
        doAnswer(query).when(observed).update(anyString(),any(SqlParameterSource.class));
        ReflectionTestUtils.setField(workbench,"jdbc",observed);persistence=new ScopedDialogAiPersistenceService(observed,new MultibusinessFeatureFlags());
        assertResult(operation,auth(7,FOLDED_RAW),false);assertThat(fired).isTrue();
    }
}
