package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyParticipantWriteBoundaryPostgresTest {
    private static final String RAW=" R163/Ю?%#+ ";
    private record Fixture(JdbcTemplate jdbc,JdbcTemplate users,LegacyTicketIdJdbcGuard guard,MultibusinessFeatureFlags flags,
                           DialogParticipantService service){}
    private Fixture fixture(){
        var jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("legacy_participant_write");
        jdbc.execute("""
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_participants(ticket_id TEXT,username TEXT,added_by TEXT,added_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY(ticket_id,username));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,last_read_at TIMESTAMPTZ);
            """);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(901,?),(902,?)",RAW,RAW.trim());
        jdbc.update("""
            INSERT INTO ticket_participants(ticket_id,username,added_by,added_at)
            VALUES(?,'operator',' Original Lead ','2000-01-01T00:00:00Z'),(?,'operator','neighbour','2000-01-01T00:00:00Z')
            """,RAW,RAW.trim());
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES(?,'original','seed')",RAW);
        var users=mock(JdbcTemplate.class);var guard=spy(new LegacyTicketIdJdbcGuard(jdbc));var flags=new MultibusinessFeatureFlags();
        return new Fixture(jdbc,users,guard,flags,new DialogParticipantService(jdbc,users,guard,flags));
    }
    private boolean write(DialogParticipantService service,String action){
        return action.equals("add")?service.addParticipant(RAW," Fresh Member "," Mixed Actor "):service.removeParticipant(RAW," OPERATOR ");
    }
    private void mutate(Fixture f,String kind){
        switch(kind){
            case "business"->f.jdbc.update("UPDATE tickets SET business_id=7 WHERE ticket_id=?",RAW);
            case "general"->f.jdbc.update("UPDATE tickets SET is_general=TRUE WHERE ticket_id=?",RAW);
            case "strict"->f.flags.setStrictReadScope(true);
            case "collision"->f.jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(903,?)",RAW);
            case "missing"->f.jdbc.update("DELETE FROM tickets WHERE ticket_id=?",RAW);
            case "null-marker"->f.jdbc.update("UPDATE tickets SET is_general=NULL WHERE ticket_id=?",RAW);
            case "missing-business"->f.jdbc.execute("ALTER TABLE tickets DROP COLUMN business_id");
            case "missing-marker"->f.jdbc.execute("ALTER TABLE tickets DROP COLUMN is_general");
            default->throw new AssertionError(kind);
        }
    }
    static Stream<Arguments> changes(){
        return Stream.of("add","remove").flatMap(action->Stream.of("business","general","strict","collision","missing","null-marker","missing-business","missing-marker")
            .map(kind->Arguments.of(action,kind)));
    }
    @ParameterizedTest @MethodSource("changes")
    void scopedStrictUnknownOrAmbiguousRootCannotChangeAnyParticipant(String action,String kind){
        var f=fixture();var before=f.jdbc.queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username");
        var assigned=f.jdbc.queryForList("SELECT * FROM ticket_responsibles");
        mutate(f,kind);
        assertThat(write(f.service,action)).isFalse();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username")).isEqualTo(before);
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(assigned);
        verifyNoInteractions(f.users);
    }
    @ParameterizedTest @MethodSource("changes")
    void earlyUniqueProofCannotAuthorizeALaterNamespaceOrStrictMode(String action,String kind){
        var f=fixture();var before=f.jdbc.queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username");
        var once=new AtomicBoolean();
        doAnswer(call->{boolean unique=(boolean)call.callRealMethod();if(unique&&once.compareAndSet(false,true))mutate(f,kind);return unique;})
            .when(f.guard).hasUniqueTicket(RAW);
        assertThat(write(f.service,action)).isFalse();assertThat(once).isTrue();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username")).isEqualTo(before);
        verifyNoInteractions(f.users);
    }
    static Stream<Arguments> boundChanges(){return changes().filter(args->!args.get()[1].equals("strict"));}
    @ParameterizedTest @MethodSource("boundChanges")
    void finalSqlRepeatsNamespaceAndUniquenessAfterArgumentsWereBound(String action,String kind){
        var f=fixture();var before=f.jdbc.queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username");
        var observed=spy(f.jdbc);var once=new AtomicBoolean();
        doAnswer(call->{String sql=call.getArgument(0);if((sql.stripLeading().startsWith("INSERT INTO ticket_participants")
            ||sql.stripLeading().startsWith("DELETE FROM ticket_participants"))&&once.compareAndSet(false,true))mutate(f,kind);
            return call.callRealMethod();}).when(observed).update(anyString(),any(Object[].class));
        assertThat(write(new DialogParticipantService(observed,f.users,f.guard,f.flags),action)).isFalse();
        assertThat(once).isTrue();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username")).isEqualTo(before);
        verifyNoInteractions(f.users);
    }
    @ParameterizedTest @ValueSource(strings={"add","remove"})
    void unresolvedBeforeStrictPreservesOpaqueIdsFieldRulesNoOpAndNeighbour(String action){
        var f=fixture();
        var neighbour=f.jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",RAW.trim());
        var assignments=f.jdbc.queryForList("SELECT * FROM ticket_responsibles");
        assertThat(write(f.service,action)).isTrue();
        assertThat(write(f.service,action)).isFalse();
        if(action.equals("add")){
            var row=f.jdbc.queryForMap("SELECT * FROM ticket_participants WHERE ticket_id=? AND username='fresh member'",RAW);
            assertThat(row).containsEntry("added_by","mixed actor").containsEntry("ticket_id",RAW);
            assertThat(row.get("added_at")).isNotNull();
        }else assertThat(f.jdbc.queryForObject("SELECT count(*) FROM ticket_participants WHERE ticket_id=? AND username='operator'",Long.class,RAW)).isZero();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",RAW.trim())).isEqualTo(neighbour);
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(assignments);
        verifyNoInteractions(f.users);
    }
    @ParameterizedTest @ValueSource(strings={"add","remove"})
    void strictRejectsBeforeAnyJdbcOrLegacyProbe(String action){
        var jdbc=mock(JdbcTemplate.class);var users=mock(JdbcTemplate.class);var guard=mock(LegacyTicketIdJdbcGuard.class);
        var flags=new MultibusinessFeatureFlags();flags.setStrictReadScope(true);
        assertThat(write(new DialogParticipantService(jdbc,users,guard,flags),action)).isFalse();
        verifyNoInteractions(jdbc,users,guard);
    }
    @Test void missingChildSchemaFailsClosedWithoutTouchingAssignment(){
        var f=fixture();var assigned=f.jdbc.queryForList("SELECT * FROM ticket_responsibles");
        f.jdbc.execute("DROP TABLE ticket_participants");
        assertThat(write(f.service,"add")).isFalse();assertThat(write(f.service,"remove")).isFalse();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(assigned);
    }
}
