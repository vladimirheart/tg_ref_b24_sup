package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import static com.example.panel.support.BusinessWorkbenchFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SQL/transactions: a differently folded neighbour cannot supply the session actor's rights. */
class BusinessWorkbenchFoldIdentityPostgresTest {
    enum Operation { assignment,assignmentCandidates,assignmentHistory,revision,members,participantCandidates,
        participantHistory,work,workHistory,take,assign,add,remove,close,reopen,categories }
    private static final String FOLDED_RAW="\u0130";
    private static final UiEventTicketScope SCOPE=new UiEventTicketScope(OWNER,RAW,PUBLIC,"BUSINESS",11L);
    private static JdbcTemplate jdbc;
    private static SingleConnectionDataSource connection;
    private BusinessTicketAssignmentService assignments;
    private BusinessTicketParticipantService participants;
    private BusinessTicketWorkService work;
    private long actorId,assignmentRevision,participantRevision,workRevision;
    @BeforeAll static void schemaOnce()throws Exception {
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_workbench_fold_identity");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("business_workbench_fold_identity_");
        assertThat(connection.getConnection().getAutoCommit()).isTrue();schema(jdbc,false);
        jdbc.execute("CREATE TABLE ticket_categories(ticket_id TEXT,category TEXT); CREATE TABLE ticket_participants(ticket_id TEXT,username TEXT)");
    }
    @AfterAll static void closeConnection(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup() {
        seed(jdbc,"caller","unused");actorId=2;jdbc.execute("TRUNCATE ticket_categories,ticket_participants");
        jdbc.update("INSERT INTO ticket_categories VALUES(?,'legacy category')",RAW);jdbc.update("INSERT INTO ticket_participants VALUES(?,'legacy participant')",RAW);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        var ids=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        assignments=new BusinessTicketAssignmentService(jdbc,flags,ids);
        participants=new BusinessTicketParticipantService(jdbc,flags,ids);
        work=new BusinessTicketWorkService(jdbc,flags,ids,new ObjectMapper());
    }
    private Authentication auth(String name){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(actorId,name,"unused",true,List.of()),null,List.of());}
    private boolean writes(Operation operation){return operation.ordinal()>=Operation.take.ordinal();}
    private boolean operate(Operation operation){return writes(operation)||operation==Operation.assignmentCandidates||operation==Operation.participantCandidates;}
    private void prepare(Operation operation) {
        if(operation==Operation.reopen)jdbc.update("UPDATE tickets SET status='resolved',resolved_by='earlier' WHERE user_id=?",OWNER);
        assignmentRevision=jdbc.queryForObject("SELECT responsibility_revision FROM tickets",Long.class);
        workRevision=jdbc.queryForObject("SELECT business_work_revision FROM tickets",Long.class);
        participantRevision=jdbc.queryForObject("SELECT max(revision) FROM business_ticket_participant_events",Long.class);
    }
    private Object call(Operation operation,Authentication actor) {
        return switch(operation){
            case assignment->assignments.find(PUBLIC,actor);
            case assignmentCandidates->assignments.candidates(SCOPE,actor);
            case assignmentHistory->assignments.recent(SCOPE,actor);
            case revision->participants.revision(SCOPE,actor);
            case members->participants.members(SCOPE,actor);
            case participantCandidates->participants.candidates(SCOPE,actor);
            case participantHistory->participants.recent(SCOPE,actor);
            case work->work.find(PUBLIC,actor);
            case workHistory->work.recent(SCOPE,actor);
            case take,assign->assignments.change(SCOPE,actor,assignmentRevision,operation==Operation.take?null:6L,operation==Operation.take);
            case add,remove->participants.change(SCOPE,actor,participantRevision,operation==Operation.add?6L:7L,operation==Operation.add);
            case close,reopen,categories->work.change(SCOPE,actor,workRevision,operation==Operation.reopen?"resolved":"open",operation.name(),operation==Operation.reopen?List.of():List.of("New"));
        };
    }
    private boolean visible(Object result){return result instanceof Boolean value?value:result instanceof Optional<?> value?value.isPresent():!((List<?>)result).isEmpty();}
    private Map<String,List<Map<String,Object>>> snapshot(){var result=com.example.panel.support.BusinessWorkbenchFixture.snapshot(jdbc);for(String table:List.of("ticket_categories","ticket_participants"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));return result;}
    private void assertResult(Operation operation,Authentication actor,boolean allowed) {
        var before=snapshot();var result=call(operation,actor);assertThat(visible(result)).as(operation.name()).isEqualTo(allowed);
        if(!allowed||!writes(operation))assertThat(snapshot()).isEqualTo(before);
        if(!allowed)return;
        switch(operation){
            case assignment->{var row=(BusinessTicketAssignmentService.State)((Optional<?>)result).orElseThrow();assertThat(row.scope()).isEqualTo(SCOPE);assertThat(row.responsible()).isEqualTo(" private assignee ");}
            case assignmentHistory->assertThat(((BusinessTicketAssignmentService.Event)((List<?>)result).get(0)).actor()).isEqualTo(" private assignment actor ");
            case revision->assertThat(result).isEqualTo(Optional.of(1L));
            case members->assertThat(((BusinessTicketParticipantService.Member)((List<?>)result).get(0)).snapshot()).isEqualTo(" private member ");
            case participantHistory->assertThat(((BusinessTicketParticipantService.Event)((List<?>)result).get(0)).actor()).isEqualTo(" private participant actor ");
            case work->assertThat(((BusinessTicketWorkService.View)((Optional<?>)result).orElseThrow()).categories()).containsExactly("Own");
            case workHistory->assertThat(((BusinessTicketWorkService.Event)((List<?>)result).get(0)).actor()).isEqualTo(" private work actor ");
            case take,assign->{
                assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,revision,assignee_user_id,assignee FROM business_ticket_assignment_events ORDER BY revision DESC LIMIT 1"))
                    .containsEntry("actor_user_id",actorId).containsEntry("actor",actor.getName()).containsEntry("revision",assignmentRevision+1)
                    .containsEntry("assignee_user_id",operation==Operation.take?actorId:6L).containsEntry("assignee",operation==Operation.take?actor.getName():"target");
                assertThat(jdbc.queryForObject("SELECT responsibility_revision FROM tickets",Long.class)).isEqualTo(assignmentRevision+1);
            }
            case add,remove->{
                assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,revision,action FROM business_ticket_participant_events ORDER BY revision DESC LIMIT 1"))
                    .containsEntry("actor_user_id",actorId).containsEntry("actor",actor.getName()).containsEntry("revision",participantRevision+1).containsEntry("action",operation.name());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_participants",Integer.class)).isEqualTo(operation==Operation.add?2:0);
            }
            case close,reopen,categories->{
                assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,revision,action FROM business_ticket_work_events ORDER BY revision DESC LIMIT 1"))
                    .containsEntry("actor_user_id",actorId).containsEntry("actor",actor.getName()).containsEntry("revision",workRevision+1).containsEntry("action",operation.name());
                assertThat(jdbc.queryForObject("SELECT business_work_revision FROM tickets",Long.class)).isEqualTo(workRevision+1);
                assertThat(jdbc.queryForObject("SELECT status FROM tickets",String.class)).isEqualTo(operation==Operation.close?"resolved":operation==Operation.reopen?"pending":"open");
            }
            default->{}
        }
        if(writes(operation)){
            assertThat(jdbc.queryForMap("SELECT ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,channel_id FROM ui_event_outbox"))
                .containsEntry("ticket_user_id",OWNER).containsEntry("ticket_id",RAW).containsEntry("ticket_public_id",PUBLIC)
                .containsEntry("ticket_scope_kind","BUSINESS").containsEntry("ticket_business_id",11L).containsEntry("channel_id",9L);
            assertThat(jdbc.queryForObject("SELECT category FROM ticket_categories",String.class)).isEqualTo("legacy category");
            assertThat(jdbc.queryForObject("SELECT username FROM ticket_participants",String.class)).isEqualTo("legacy participant");
        }
    }
    private static Stream<Arguments> cases(String... values){return Arrays.stream(values).flatMap(v->Arrays.stream(Operation.values()).map(o->Arguments.of(v,o)));}
    private void foldedNeighbour(){
        jdbc.update("UPDATE users SET username=? WHERE id=2",FOLDED_RAW);jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",FOLDED_RAW.toLowerCase(Locale.ROOT));
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");
        assertThat(jdbc.queryForObject("SELECT lower(?)",String.class,FOLDED_RAW)).isNotEqualTo(FOLDED_RAW.toLowerCase(Locale.ROOT));
    }
    static Stream<Arguments> neighbour(){return cases("active","disabled","blocked","no-rights");}
    @ParameterizedTest @MethodSource("neighbour")
    void aFoldedNeighbourCannotSupplyTheSessionActorsAuthority(String status,Operation operation){
        prepare(operation);foldedNeighbour();if(status.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
        else if(status.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");else if(status.equals("no-rights"))jdbc.update("DELETE FROM business_memberships WHERE user_id=2");
        assertResult(operation,auth(FOLDED_RAW),false);
    }
    @ParameterizedTest @EnumSource(Operation.class)
    void aFoldMismatchWithoutANeighbourAlsoFailsClosed(Operation operation){prepare(operation);jdbc.update("UPDATE users SET username=? WHERE id=2",FOLDED_RAW);assertResult(operation,auth(FOLDED_RAW),false);}
    static Stream<Arguments> names(){return cases("caller","Caller"," caller ","имя","anonymousUser");}
    @ParameterizedTest @MethodSource("names")
    void exactRawAccountsRetainPrivateReadsAndNumericAudit(String name,Operation operation){prepare(operation);jdbc.update("UPDATE users SET username=? WHERE id=2",name);assertResult(operation,auth(name),true);}
    static Stream<Arguments> roles(){return cases("VIEWER","ALL_VIEWER","OPERATOR","ALL_ADMIN");}
    @ParameterizedTest @MethodSource("roles")
    void readAndOperateRemainDistinctForLocalAndAllBusinessGrants(String role,Operation operation){
        prepare(operation);jdbc.update("DELETE FROM business_memberships WHERE user_id=2");if(role.startsWith("ALL_"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(2,'ALL_BUSINESSES',?)",role.substring(4));
        else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,?)",role);
        assertResult(operation,auth("caller"),!operate(operation)||!role.endsWith("VIEWER"));
    }
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Arrays.stream(Operation.values()).map(o->Arguments.of(id,o)));}
    @ParameterizedTest @MethodSource("ids")
    void bigintActorIdsRemainExact(long id,Operation operation){prepare(operation);actorId=id;jdbc.update("UPDATE users SET id=? WHERE id=2",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=2",id);assertResult(operation,auth("caller"),true);}
    static Stream<Operation> reads(){return Arrays.stream(Operation.values()).filter(o->o.ordinal()<Operation.take.ordinal());}
    @ParameterizedTest @MethodSource("reads")
    void eachReadSqlRechecksTheFrozenActorsOwnStatus(Operation operation){
        prepare(operation);foldedNeighbour();var observed=spy(new NamedParameterJdbcTemplate(jdbc));var fired=new AtomicBoolean();
        doAnswer(call->{if(fired.compareAndSet(false,true))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");return call.callRealMethod();})
            .when(observed).query(anyString(),any(SqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        observe(observed);assertResult(operation,auth(FOLDED_RAW),false);assertThat(fired).isTrue();
    }
    static Stream<Arguments> writerBoundaries(){return Stream.concat(Arrays.stream(Operation.values()).filter(o->o.ordinal()>=Operation.take.ordinal()).map(o->Arguments.of(o,"mutation")),Stream.of(Operation.close,Operation.reopen,Operation.categories).map(o->Arguments.of(o,"confirmation")));}
    @ParameterizedTest @MethodSource("writerBoundaries")
    void lateStatusRevocationLeavesNoWriteAuditOrOutbox(Operation operation,String site){
        prepare(operation);var observed=spy(new NamedParameterJdbcTemplate(jdbc));var fired=new AtomicBoolean();
        org.mockito.stubbing.Answer<Object> boundary=call->{String sql=call.getArgument(0);boolean match=site.equals("confirmation")?sql.startsWith("SELECT owner.business_work_revision"):
            sql.startsWith("INSERT INTO ticket_responsibles AS current")||sql.startsWith("INSERT INTO business_ticket_participants(")||sql.startsWith("DELETE FROM business_ticket_participants entry")||sql.startsWith("UPDATE tickets owner SET");
            if(match&&fired.compareAndSet(false,true))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");return call.callRealMethod();};
        doAnswer(boundary).when(observed).query(anyString(),any(SqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        doAnswer(boundary).when(observed).update(anyString(),any(SqlParameterSource.class));
        observe(observed);assertResult(operation,auth("caller"),false);assertThat(fired).isTrue();
    }
    private void observe(NamedParameterJdbcTemplate jdbc){ReflectionTestUtils.setField(assignments,"jdbc",jdbc);ReflectionTestUtils.setField(participants,"jdbc",jdbc);ReflectionTestUtils.setField(work,"jdbc",jdbc);}
}
