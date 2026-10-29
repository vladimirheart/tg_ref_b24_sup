package com.example.panel.service;

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
import org.springframework.jdbc.core.ResultSetExtractor;
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

/** Real PostgreSQL: private projections belong to the bound account, including its SQL fold. */
class BusinessProjectionFoldIdentityPostgresTest {
    enum Operation { assignments,current,roster,participants,work }
    private static final String FOLDED_RAW="\u0130";
    private static JdbcTemplate jdbc;
    private static SingleConnectionDataSource connection;
    private BusinessTicketAssignmentReadService assignments;
    private BusinessTicketCurrentAssignmentReadService current;
    private BusinessTicketParticipantReadService participants;
    private BusinessTicketWorkReadService work;
    private long actorId;

    @BeforeAll static void schemaOnce()throws Exception {
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_projection_fold_identity");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("business_projection_fold_identity_");
        assertThat(connection.getConnection().getAutoCommit()).isTrue();schema(jdbc,false);
    }
    @AfterAll static void closeConnection(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup() {
        seed(jdbc,"caller","unused");actorId=2;
        assignments=new BusinessTicketAssignmentReadService(jdbc);
        current=new BusinessTicketCurrentAssignmentReadService(jdbc);
        participants=new BusinessTicketParticipantReadService(jdbc);
        work=new BusinessTicketWorkReadService(jdbc,new ObjectMapper());
    }
    private Authentication auth(String name){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(actorId,name,"unused",true,List.of()),null,List.of());}
    private Optional<?> call(Operation operation,Authentication actor) {
        return switch(operation){
            case assignments->assignments.history(PUBLIC,actor,null,1);
            case current->current.current(PUBLIC,actor);
            case roster->participants.find(PUBLIC,actor);
            case participants->participants.history(PUBLIC,actor,null,1);
            case work->work.history(PUBLIC,actor,null,1);
        };
    }
    private void assertResult(Operation operation,Authentication actor,boolean allowed) {
        var before=snapshot(jdbc);var result=call(operation,actor);
        assertThat(result.isPresent()).as(operation.name()).isEqualTo(allowed);
        assertThat(snapshot(jdbc)).isEqualTo(before);
        if(!allowed)return;
        switch(operation){
            case assignments->{
                var row=(BusinessTicketAssignmentReadService.History)result.orElseThrow();
                assertThat(row.owner()).isEqualTo(new BusinessTicketAssignmentReadService.Owner(Long.toString(OWNER),RAW,PUBLIC,"11"));
                assertThat(row.revision()).isEqualTo(1);assertThat(row.latestEventRevision()).isEqualTo(1);
                assertThat(row.events()).hasSize(1);var event=row.events().get(0);
                assertThat(event.revision()).isEqualTo(1);assertThat(event.origin()).isEqualTo("MANUAL");
                assertThat(event.assigneeId()).isEqualTo(6L);assertThat(event.assignee()).isEqualTo(" private assignee ");
                assertThat(event.actorId()).isEqualTo(2L);assertThat(event.actor()).isEqualTo(" private assignment actor ");
                assertThat(row.hasMore()).isFalse();assertThat(row.nextBeforeRevision()).isNull();
            }
            case current->{
                var row=(BusinessTicketCurrentAssignmentReadService.State)result.orElseThrow();
                assertThat(row.owner()).isEqualTo(new BusinessTicketCurrentAssignmentReadService.Owner(Long.toString(OWNER),RAW,PUBLIC,"11"));
                assertThat(row.revision()).isEqualTo(1);assertThat(row.status()).isEqualTo("ASSIGNED");
                assertThat(row.assignee().id()).isEqualTo(6L);assertThat(row.assignee().username()).isEqualTo(" private assignee ");
                assertThat(row.assignee().origin()).isEqualTo("MANUAL");
            }
            case roster->{
                var row=(BusinessTicketParticipantReadService.Roster)result.orElseThrow();
                assertThat(row.owner()).isEqualTo(new BusinessTicketParticipantReadService.Owner(Long.toString(OWNER),RAW,PUBLIC,"11"));
                assertThat(row.revision()).isEqualTo(1);assertThat(row.members()).hasSize(1);var member=row.members().get(0);
                assertThat(member.id()).isEqualTo(7);assertThat(member.snapshot()).isEqualTo(" private member ");
                assertThat(member.username()).isEqualTo("member");assertThat(member.eligible()).isTrue();
            }
            case participants->{
                var row=(BusinessTicketParticipantReadService.History)result.orElseThrow();
                assertThat(row.owner()).isEqualTo(new BusinessTicketParticipantReadService.Owner(Long.toString(OWNER),RAW,PUBLIC,"11"));
                assertThat(row.revision()).isEqualTo(1);assertThat(row.events()).hasSize(1);var event=row.events().get(0);
                assertThat(event.memberId()).isEqualTo(7);assertThat(event.member()).isEqualTo(" private member ");
                assertThat(event.actorId()).isEqualTo(2);assertThat(event.actor()).isEqualTo(" private participant actor ");
                assertThat(row.hasMore()).isFalse();assertThat(row.nextBeforeRevision()).isNull();
            }
            case work->{
                var row=(BusinessTicketWorkReadService.History)result.orElseThrow();
                assertThat(row.owner()).isEqualTo(new BusinessTicketWorkReadService.Owner(Long.toString(OWNER),RAW,PUBLIC,"11"));
                assertThat(row.revision()).isEqualTo(1);assertThat(row.latestEventRevision()).isEqualTo(1);
                assertThat(row.events()).hasSize(1);var event=row.events().get(0);
                assertThat(event.actorId()).isEqualTo(2);assertThat(event.actor()).isEqualTo(" private work actor ");
                assertThat(event.previousCategories()).isEmpty();assertThat(event.categories()).containsExactly("Own");
                assertThat(row.hasMore()).isFalse();assertThat(row.nextBeforeRevision()).isNull();
            }
        }
    }
    private static Stream<Arguments> cases(String... values){return Arrays.stream(values).flatMap(v->Arrays.stream(Operation.values()).map(o->Arguments.of(v,o)));}
    private void foldedNeighbour(){
        jdbc.update("UPDATE users SET username=? WHERE id=2",FOLDED_RAW);
        jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",FOLDED_RAW.toLowerCase(Locale.ROOT));
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");
        assertThat(jdbc.queryForObject("SELECT lower(?)",String.class,FOLDED_RAW)).isNotEqualTo(FOLDED_RAW.toLowerCase(Locale.ROOT));
    }
    static Stream<Arguments> neighbour(){return cases("active","disabled","blocked","no-rights");}
    @ParameterizedTest @MethodSource("neighbour")
    void aFoldedNeighbourCannotSupplyTheBoundReadersAuthority(String status,Operation operation){
        foldedNeighbour();if(status.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
        else if(status.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
        else if(status.equals("no-rights"))jdbc.update("DELETE FROM business_memberships WHERE user_id=2");
        assertResult(operation,auth(FOLDED_RAW),false);
    }
    @ParameterizedTest @EnumSource(Operation.class)
    void aFoldMismatchWithoutANeighbourAlsoFailsClosed(Operation operation){jdbc.update("UPDATE users SET username=? WHERE id=2",FOLDED_RAW);assertResult(operation,auth(FOLDED_RAW),false);}
    static Stream<Arguments> names(){return cases("caller","Caller"," caller ","имя","anonymousUser");}
    @ParameterizedTest @MethodSource("names")
    void exactRawAccountsRetainPrivateProjectionSnapshots(String name,Operation operation){jdbc.update("UPDATE users SET username=? WHERE id=2",name);assertResult(operation,auth(name),true);}
    static Stream<Arguments> roles(){return cases("VIEWER","OPERATOR","MANAGER","ADMIN","ALL_VIEWER","ALL_ADMIN","NONE","UNKNOWN");}
    @ParameterizedTest @MethodSource("roles")
    void allFiveProjectionsRequireReadRatherThanOperate(String role,Operation operation){
        jdbc.update("DELETE FROM business_memberships WHERE user_id=2");
        if(role.startsWith("ALL_"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(2,'ALL_BUSINESSES',?)",role.substring(4));
        else if(!role.equals("NONE"))jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,?)",role);
        assertResult(operation,auth("caller"),!Set.of("NONE","UNKNOWN").contains(role));
    }
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Arrays.stream(Operation.values()).map(o->Arguments.of(id,o)));}
    @ParameterizedTest @MethodSource("ids")
    void bigintBoundReaderIdsDoNotChangeHistoricalActorSnapshots(long id,Operation operation){
        actorId=id;jdbc.update("UPDATE users SET id=? WHERE id=2",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=2",id);
        assertResult(operation,auth("caller"),true);
    }
    static Stream<Arguments> revoked(){return cases("rename-reuse","delete-reuse","case-change","disabled","blocked","membership");}
    @ParameterizedTest @MethodSource("revoked")
    void staleSessionsCannotFollowReusedNamesOrOldPermissions(String mode,Operation operation){
        var actor=auth("caller");replace(jdbc,mode,"caller");assertResult(operation,actor,false);
    }
    static Stream<Arguments> boundaries(){return cases("disabled","blocked","membership","folded-neighbour");}
    @ParameterizedTest @MethodSource("boundaries")
    void theSqlStatementRechecksTheBoundAccountRatherThanANeighbour(String mode,Operation operation){
        if(mode.equals("folded-neighbour"))foldedNeighbour();var actor=auth(mode.equals("folded-neighbour")?FOLDED_RAW:"caller");
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));var fired=new AtomicBoolean();
        org.mockito.stubbing.Answer<Object> boundary=invocation->{
            if(fired.compareAndSet(false,true)){
                if(mode.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
                else if(mode.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
                else jdbc.update("DELETE FROM business_memberships WHERE user_id=2");
            }
            return invocation.callRealMethod();
        };
        if(operation==Operation.current)doAnswer(boundary).when(observed).query(anyString(),any(SqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        else doAnswer(boundary).when(observed).query(anyString(),any(SqlParameterSource.class),org.mockito.ArgumentMatchers.<ResultSetExtractor<Object>>any());
        Object service=switch(operation){case assignments->assignments;case current->current;case roster,participants->participants;case work->work;};
        ReflectionTestUtils.setField(service,"jdbc",observed);assertResult(operation,actor,false);assertThat(fired).isTrue();
    }
}
