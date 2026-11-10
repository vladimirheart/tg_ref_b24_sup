package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.example.panel.support.AiControlIdentityFixture.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.AiControlIdentityFixture;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class ScopedAiDataCleanupPostgresTest {
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource independent;
    private static SingleConnectionDataSource connection;
    private ScopedDialogAiPersistenceService service;
    private MultibusinessFeatureFlags flags;

    @BeforeAll static void schema() throws Exception {
        independent=(DriverManagerDataSource)PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r311_ai_cleanup").getDataSource();
        connection=new SingleConnectionDataSource(independent.getUrl(),independent.getUsername(),independent.getPassword(),true);
        jdbc=new JdbcTemplate(connection);
        AiControlIdentityFixture.schema(jdbc,false);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V64__scoped_ai_handoff_evidence.sql")));
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY,message TEXT); CREATE TABLE ai_scoped_sources(id BIGINT PRIMARY KEY,body TEXT)");
    }
    @AfterAll static void close(){if(connection!=null)connection.destroy();}
    @BeforeEach void seed() {
        jdbc.execute("DROP TRIGGER IF EXISTS r311_control_wait ON ticket_ai_scoped_control; DROP TRIGGER IF EXISTS r311_audit_failure ON ticket_ai_scoped_events; DROP TRIGGER IF EXISTS r311_state_skip ON ticket_ai_scoped_state");
        AiControlIdentityFixture.seed(jdbc,"caller","unused");
        jdbc.update("UPDATE business_memberships SET role_code='ADMIN' WHERE user_id=7");
        jdbc.execute("TRUNCATE ai_scoped_handoff_evidence,ai_scoped_memory_evidence,chat_history,ai_scoped_sources");
        jdbc.execute("INSERT INTO chat_history VALUES(40,'private chat'); INSERT INTO ai_scoped_sources VALUES(90,'approved knowledge')");
        jdbc.update("INSERT INTO ai_scoped_memory_evidence VALUES(90,40,41,?,?)","a".repeat(64),"b".repeat(64));
        flags=new MultibusinessFeatureFlags();service=new ScopedDialogAiPersistenceService(jdbc,flags);
    }
    private ScopedDialogAiPersistenceService.CleanupSnapshot snapshot(){return service.cleanupSnapshot(SCOPE,auth(7,"caller")).orElseThrow();}
    private void history(boolean control) {
        if(control)existing(jdbc);
        assertThat(service.recordHandoff(SCOPE)).isTrue();
        jdbc.update("INSERT INTO ai_scoped_handoff_evidence SELECT id,90,2 FROM ticket_ai_scoped_events");
        jdbc.update("INSERT INTO ticket_ai_scoped_events(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,event_type,decision_reason) VALUES(99,'foreign',?,'BUSINESS',22,'private_other','private other reason')","b".repeat(32));
    }
    private Map<String,List<Map<String,Object>>> rows() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("ticket_ai_scoped_state","ticket_ai_scoped_control","ticket_ai_scoped_events","ai_scoped_handoff_evidence","ai_scoped_memory_evidence","chat_history","ai_scoped_sources"))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));
        return result;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void clearsOnlyFrozenAiRecordsAndBlocksAiWithAtomicContentFreeAudit(boolean control) {
        history(control);var expected=snapshot();var before=rows();
        assertThat(service.clearHistoryExpected(SCOPE,auth(7,"caller"),expected)).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM ticket_ai_scoped_state")).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_handoff_evidence")).isEmpty();
        assertThat(jdbc.queryForList("SELECT event_type,decision_reason FROM ticket_ai_scoped_events WHERE ticket_public_id=?",PUBLIC))
            .containsExactly(Map.of("event_type","ai_data_cleared","decision_reason","manual_cleanup_ai_disabled"));
        var current=service.findControl(SCOPE,auth(7,"caller")).orElseThrow();
        assertThat(current.aiDisabled()).isTrue();assertThat(current.autoReplyBlocked()).isTrue();assertThat(current.reason()).isNull();
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by FROM ticket_ai_scoped_control WHERE ticket_public_id=?",PUBLIC))
            .containsEntry("updated_by_user_id",7L).containsEntry("updated_by","caller");
        for(String table:List.of("chat_history","ai_scoped_sources","ai_scoped_memory_evidence"))assertThat(rows().get(table)).isEqualTo(before.get(table));
        assertThat(jdbc.queryForMap("SELECT * FROM ticket_ai_scoped_events WHERE ticket_business_id=22")).isEqualTo(before.get("ticket_ai_scoped_events").stream().filter(r->Long.valueOf(22).equals(r.get("ticket_business_id"))).findFirst().orElseThrow());
        var after=rows();
        assertThat(service.clearHistoryExpected(SCOPE,auth(7,"caller"),expected)).isFalse();
        assertThat(service.setControlExpected(SCOPE,auth(7,"caller"),expected.controlStamp(),false,false,"stale restore")).isFalse();
        assertThat(service.recordHandoff(SCOPE)).isFalse();assertThat(rows()).isEqualTo(after);
    }
    @ParameterizedTest @ValueSource(strings={"VIEWER","OPERATOR","MANAGER","ADMIN","ALL_ADMIN","ROLE_ADMIN"})
    void erasureRequiresConfigureAndCannotUseSystemRoleAlone(String role) {
        history(false);var expected=snapshot();
        jdbc.update("DELETE FROM business_memberships WHERE user_id=7");
        if(role.equals("ALL_ADMIN"))jdbc.update("INSERT INTO business_access_grants VALUES(7,'ALL_BUSINESSES','ADMIN',TRUE)");
        else if(!role.equals("ROLE_ADMIN"))jdbc.update("INSERT INTO business_memberships VALUES(7,11,?,TRUE)",role);
        var before=rows();boolean allowed=role.equals("ADMIN")||role.equals("ALL_ADMIN");
        assertThat(service.cleanupSnapshot(SCOPE,auth(7,"caller")).isPresent()).isEqualTo(allowed);
        assertThat(service.clearHistoryExpected(SCOPE,auth(7,"caller"),expected)).isEqualTo(allowed);
        if(!allowed)assertThat(rows()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","membership","rename-reuse","delete-reuse","case-change","foreign-id"})
    void finalSqlRechecksNumericRawAccountAndLiveAuthority(String mutation) {
        history(true);var expected=snapshot();var before=rows();
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));
        doAnswer(call->{
            if(!mutation.equals("foreign-id"))replace(jdbc,mutation);
            return new NamedParameterJdbcTemplate(jdbc).queryForObject(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class),Integer.class);
        }).when(observed).queryForObject(anyString(),any(SqlParameterSource.class),eq(Integer.class));
        var guarded=new ScopedDialogAiPersistenceService(observed,flags);
        assertThat(guarded.clearHistoryExpected(SCOPE,auth(mutation.equals("foreign-id")?99:7,"caller"),expected)).isFalse();
        assertThat(rows()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"business","generation","general","unresolved","collision","missing","archive"})
    void changedCanonicalOwnerCannotDeleteOldOrForeignAiRecords(String change) {
        history(true);var expected=snapshot();var before=rows();
        switch(change){case "business"->jdbc.update("UPDATE tickets SET business_id=22");case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=?","c".repeat(32));case "general"->jdbc.update("UPDATE tickets SET is_general=TRUE,business_id=NULL");case "unresolved"->jdbc.update("UPDATE tickets SET business_id=NULL");case "collision"->jdbc.update("INSERT INTO tickets VALUES(99,?,?,22,FALSE)",RAW,"c".repeat(32));case "missing"->jdbc.update("DELETE FROM tickets");default->jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");}
        assertThat(service.clearHistoryExpected(SCOPE,auth(7,"caller"),expected)).isFalse();assertThat(rows()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"state","event","control","forged-max"})
    void staleOrForgedSnapshotsCannotEraseNewData(String change) {
        history(true);var expected=snapshot();
        switch(change){case "state"->jdbc.update("UPDATE ticket_ai_scoped_state SET updated_at=updated_at+INTERVAL '1 second'");case "event"->assertThat(service.recordHandoff(SCOPE)).isTrue();case "control"->assertThat(service.setControlExpected(SCOPE,auth(7,"caller"),expected.controlStamp(),false,true,"new reason")).isTrue();default->expected=new ScopedDialogAiPersistenceService.CleanupSnapshot(expected.controlStamp(),expected.stateStamp(),Long.MAX_VALUE);}
        var before=rows();assertThat(service.clearHistoryExpected(SCOPE,auth(7,"caller"),expected)).isFalse();assertThat(rows()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"audit","state-cas"})
    void auditFailureOrFailedStateDeletionRollsBackEveryCte(String failure) {
        history(true);var expected=snapshot();var before=rows();
        if(failure.equals("audit"))jdbc.execute("CREATE OR REPLACE FUNCTION r311_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='ai_data_cleared' THEN RAISE EXCEPTION 'fixture rejects cleanup audit'; END IF; RETURN NEW; END $$; CREATE TRIGGER r311_audit_failure BEFORE INSERT ON ticket_ai_scoped_events FOR EACH ROW EXECUTE FUNCTION r311_fail_audit()");
        else jdbc.execute("CREATE OR REPLACE FUNCTION r311_skip_state() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NULL; END $$; CREATE TRIGGER r311_state_skip BEFORE DELETE ON ticket_ai_scoped_state FOR EACH ROW EXECUTE FUNCTION r311_skip_state()");
        assertThat(service.clearHistoryExpected(SCOPE,auth(7,"caller"),expected)).isFalse();assertThat(rows()).isEqualTo(before);
    }
    @Test void invalidAuthenticationAndMalformedSnapshotsNeverReachJdbc() {
        var observed=mock(NamedParameterJdbcOperations.class);var guarded=new ScopedDialogAiPersistenceService(observed,flags);
        var empty=new ScopedDialogAiPersistenceService.CleanupSnapshot("","",0);
        var unbound=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());
        assertThat(guarded.clearHistoryExpected(SCOPE,unbound,empty)).isFalse();
        assertThat(guarded.clearHistoryExpected(null,auth(7,"caller"),empty)).isFalse();
        assertThat(guarded.clearHistoryExpected(SCOPE,auth(7,"caller"),null)).isFalse();
        assertThat(guarded.clearHistoryExpected(SCOPE,auth(7,"caller"),new ScopedDialogAiPersistenceService.CleanupSnapshot(null,"",0))).isFalse();
        assertThat(guarded.clearHistoryExpected(SCOPE,auth(7,"caller"),new ScopedDialogAiPersistenceService.CleanupSnapshot("","",-1))).isFalse();
        assertThat(guarded.clearHistoryExpected(SCOPE,auth(7,"caller"),new ScopedDialogAiPersistenceService.CleanupSnapshot("x".repeat(101),"",0))).isFalse();
        verifyNoInteractions(observed);
    }
    @ParameterizedTest @ValueSource(strings={"state","event"})
    void concurrentNewDataIsNeverSilentlyErased(String change) throws Exception {
        history(true);var expected=snapshot();
        jdbc.execute("CREATE OR REPLACE FUNCTION r311_wait() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_advisory_xact_lock(311810); RETURN NEW; END $$; CREATE TRIGGER r311_control_wait BEFORE UPDATE ON ticket_ai_scoped_control FOR EACH ROW EXECUTE FUNCTION r311_wait()");
        jdbc.execute("SELECT pg_advisory_lock(311810)");
        var executor=Executors.newSingleThreadExecutor();
        try {
            var future=executor.submit(()->new ScopedDialogAiPersistenceService(new JdbcTemplate(independent),flags).clearHistoryExpected(SCOPE,auth(7,"caller"),expected));
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                while(System.nanoTime()<deadline){waiting=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND objid=311810 AND NOT granted)",Boolean.class));if(waiting)break;Thread.sleep(10);}
                assertThat(waiting).as("cleanup reached its frozen-snapshot write barrier").isTrue();
                if(change.equals("state"))assertThat(service.recordHandoff(SCOPE)).isTrue();
                else jdbc.update("INSERT INTO ticket_ai_scoped_events(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,event_type,decision_reason) VALUES(42,?,?,'BUSINESS',11,'new_event','new retained metadata')",RAW,PUBLIC);
            } finally {jdbc.execute("SELECT pg_advisory_unlock(311810)");}
            assertThat(future.get(10,TimeUnit.SECONDS)).isEqualTo(change.equals("event"));
            if(change.equals("state")) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ai_scoped_events WHERE ticket_public_id=?",Long.class,PUBLIC)).isEqualTo(2);
                assertThat(service.findControl(SCOPE,auth(7,"caller")).orElseThrow().aiDisabled()).isFalse();
            } else assertThat(jdbc.queryForList("SELECT event_type FROM ticket_ai_scoped_events WHERE ticket_public_id=? ORDER BY id",String.class,PUBLIC)).containsExactly("new_event","ai_data_cleared");
        } finally {executor.shutdownNow();}
    }
}
