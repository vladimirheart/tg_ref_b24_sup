package com.example.panel.service;

import static com.example.panel.support.GeneralTicketAdminTestSupport.administrator;
import static com.example.panel.support.GeneralTicketActorTestSupport.auth;
import static org.assertj.core.api.Assertions.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class GeneralTicketAssignmentPostgresTest {
    private static final String RAW=" assignment/Ю?% ",PUBLIC="a".repeat(32);
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private GeneralTicketAccessService access;
    private GeneralTicketAssignmentService assignments;
    private GeneralTicketWorkService work;
    private final GeneralTicketEventScope scope=new GeneralTicketEventScope(10L,RAW,PUBLIC);
    @BeforeEach void setup() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_assignment");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE general_ticket_read_grants(user_id BIGINT PRIMARY KEY,is_active BOOLEAN,updated_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,is_general BOOLEAN,business_id BIGINT,status TEXT,channel_id BIGINT,"
            +"resolved_at TIMESTAMPTZ,resolved_by TEXT,closed_count INT DEFAULT 0,reopen_count INT DEFAULT 0,last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,message_text TEXT,message_type TEXT,attachment TEXT,rating INT,created_at TIMESTAMPTZ)");
        for(String migration:new String[]{"V61__ui_event_outbox_ticket_scope.sql","V62__scoped_ai_records.sql","V65__general_ticket_operate.sql",
                "V67__general_ticket_manual_close.sql","V68__general_ticket_work_intervals.sql","V69__general_ticket_assignments.sql"})
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",migration)));
        jdbc.execute("CREATE TABLE ticket_responsibles(ticket_id TEXT,responsible TEXT)");
        jdbc.update("INSERT INTO ticket_responsibles VALUES(?,'legacy-staff')",RAW);
        jdbc.update("INSERT INTO users VALUES(1,'worker',TRUE,FALSE),(2,'reader',TRUE,FALSE),(3,'candidate',TRUE,FALSE),(4,'admin',TRUE,FALSE)");
        jdbc.update("INSERT INTO general_ticket_read_grants VALUES(1,TRUE,NULL),(2,TRUE,NULL),(3,TRUE,NULL)");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,status,channel_id) VALUES(10,?,?,TRUE,'pending',9)",RAW,PUBLIC);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        access=new GeneralTicketAccessService(jdbc);access.grantOperate(administrator(jdbc),1);access.grantOperate(administrator(jdbc),3);
        var ids=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        assignments=new GeneralTicketAssignmentService(jdbc,flags,ids);work=new GeneralTicketWorkService(jdbc,ids,flags);
    }
    private boolean assign(long revision,Long target){return assignments.assign(scope,auth(jdbc,"worker"),0,revision,target);}
    @Test void explicitAssignmentReplacementAndRemovalAreVersionedAndDoNotTouchLegacyOrRoot() {
        var root=jdbc.queryForList("SELECT * FROM tickets");
        assertThat(assignments.current(scope,auth(jdbc,"reader")).orElseThrow().revision()).isZero();
        assertThat(assignments.current(scope,auth(jdbc,"reader")).orElseThrow().assignee()).isNull();
        assertThat(assignments.candidates(scope,auth(jdbc,"worker"))).extracting(GeneralTicketAssignmentService.Candidate::userId).containsExactly(3L,1L);
        assertThat(assignments.candidates(scope,auth(jdbc,"reader"))).isEmpty();assertThat(assignments.candidates(scope,auth(jdbc,"admin"))).isEmpty();
        assertThat(assign(0,4L)).isFalse();assertThat(assign(0,2L)).isFalse();assertThat(assign(0,null)).isFalse();
        assertThat(assign(0,1L)).isTrue();assertThat(assign(0,3L)).isFalse();assertThat(assign(1,1L)).isFalse();
        assertThat(assign(1,3L)).isTrue();assertThat(assign(2,null)).isTrue();assertThat(assign(3,null)).isFalse();
        assertThat(assignments.recent(scope,auth(jdbc,"reader"))).extracting(GeneralTicketAssignmentService.Event::assignee).containsExactly(null,"candidate","worker");
        assertThat(jdbc.queryForList("SELECT * FROM tickets")).isEqualTo(root);
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles",String.class)).isEqualTo("legacy-staff");
        assertThat(jdbc.queryForList("SELECT event_type,ticket_scope_kind,ticket_business_id,message_text,ticket_id,ticket_user_id,ticket_public_id FROM ui_event_outbox"))
            .hasSize(3).allSatisfy(row->assertThat(row).containsEntry("event_type","assignment_changed").containsEntry("ticket_scope_kind","GENERAL")
                .containsEntry("ticket_business_id",null).containsEntry("message_text",null).containsEntry("ticket_id",RAW)
                .containsEntry("ticket_user_id",10L).containsEntry("ticket_public_id",PUBLIC));
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","business","unresolved","tuple","generation","collision","missing-owner","flag","principal"})
    void everyReadAndWriteReprovesOwnerAndCurrentAccount(String mutation) {
        assertThat(assign(0,1L)).isTrue();String identity="worker";
        switch(mutation) {
            case "read"->access.revokeRead(administrator(jdbc),1);
            case "operate"->access.revokeOperate(administrator(jdbc),1);
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "duplicate"->jdbc.update("INSERT INTO users VALUES(5,'WORKER',FALSE,FALSE)");
            case "missing-account"->jdbc.update("DELETE FROM users WHERE id=1");
            case "business"->jdbc.update("UPDATE tickets SET is_general=FALSE,business_id=1");
            case "unresolved"->jdbc.update("UPDATE tickets SET is_general=FALSE");
            case "tuple"->jdbc.update("UPDATE tickets SET user_id=99");
            case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,status) VALUES(20,?,?,FALSE,'pending')",RAW,"b".repeat(32));
            case "missing-owner"->jdbc.update("DELETE FROM tickets");
            case "flag"->flags.setGeneralTicketQueue(false);
            case "principal"->identity=" worker ";
        }
        assertThat(assignments.assign(scope,auth(jdbc,identity),0,1,3L)).isFalse();
        assertThat(assignments.candidates(scope,auth(jdbc,identity))).isEmpty();
        if(mutation.equals("operate")) {assertThat(assignments.current(scope,auth(jdbc,identity))).isPresent();assertThat(assignments.recent(scope,auth(jdbc,identity))).hasSize(1);}
        else {assertThat(assignments.current(scope,auth(jdbc,identity))).isEmpty();assertThat(assignments.recent(scope,auth(jdbc,identity))).isEmpty();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing","blank"})
    void candidateEligibilityIsRecheckedInsideTheMutation(String mutation) {
        assertThat(assignments.candidates(scope,auth(jdbc,"worker"))).hasSize(2);
        switch(mutation) {
            case "read"->access.revokeRead(administrator(jdbc),3);
            case "operate"->access.revokeOperate(administrator(jdbc),3);
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=3");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=3");
            case "duplicate"->jdbc.update("INSERT INTO users VALUES(5,'CANDIDATE',FALSE,FALSE)");
            case "missing"->jdbc.update("DELETE FROM users WHERE id=3");
            case "blank"->jdbc.update("UPDATE users SET username=' ' WHERE id=3");
        }
        assertThat(assign(0,3L)).isFalse();assertThat(assignments.candidates(scope,auth(jdbc,"worker")))
            .extracting(GeneralTicketAssignmentService.Candidate::userId).containsExactly(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"negative-owner","negative-assignment","max-assignment","negative-target","zero-target"})
    void invalidVersionsAndTargetIdentifiersCannotWrite(String input) {
        long ownerVersion=input.equals("negative-owner")?-1:0;
        long version=input.equals("negative-assignment")?-1:input.equals("max-assignment")?Long.MAX_VALUE:0;
        Long target=input.equals("negative-target")?-1L:input.equals("zero-target")?0L:3L;
        assertThat(assignments.assign(scope,auth(jdbc,"worker"),ownerVersion,version,target)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isZero();
    }
    @Test void negativeTicketUserIdsKeepTheirExactOwner() {
        jdbc.update("UPDATE tickets SET user_id=-10");
        var negative=new GeneralTicketEventScope(-10L,RAW,PUBLIC);
        assertThat(assignments.assign(negative,auth(jdbc,"worker"),1,0,3L)).isTrue();
        assertThat(assignments.current(negative,auth(jdbc,"reader")).orElseThrow().assignee()).isEqualTo("candidate");
        assertThat(assignments.current(scope,auth(jdbc,"reader"))).isEmpty();
    }
    @Test void assignmentAbaRejectsAnOldFormEvenAfterReturningToTheSameAssignee() {
        assertThat(assign(0,1L)).isTrue();assertThat(assign(1,3L)).isTrue();assertThat(assign(2,1L)).isTrue();
        assertThat(assign(1,3L)).isFalse();assertThat(assignments.current(scope,auth(jdbc,"reader")).orElseThrow().revision()).isEqualTo(3);
    }
    @Test void revokedAssigneeStaysHistoricalAndCanBeReplacedWithoutRestoringRights() {
        assertThat(assign(0,3L)).isTrue();access.revokeRead(administrator(jdbc),3);
        var state=assignments.current(scope,auth(jdbc,"reader")).orElseThrow();
        assertThat(state.assignee()).isEqualTo("candidate");assertThat(state.eligible()).isFalse();
        assertThat(assign(1,1L)).isTrue();assertThat(access.canRead(3L)).isFalse();
        assertThat(assignments.recent(scope,auth(jdbc,"reader")).get(1).assignee()).isEqualTo("candidate");
    }
    @ParameterizedTest @ValueSource(strings={"closed","resolved","aba"})
    void closedAndStatusChangedFormsCannotAssign(String mutation) {
        jdbc.update("UPDATE tickets SET status=?",mutation.equals("aba")?"open":mutation);
        if(mutation.equals("aba"))jdbc.update("UPDATE tickets SET status='pending'");
        assertThat(assign(0,3L)).isFalse();
        if(!mutation.equals("aba"))assertThat(assignments.assign(scope,auth(jdbc,"worker"),1,0,3L)).isFalse();
        assertThat(assignments.current(scope,auth(jdbc,"reader"))).isPresent();
    }
    @ParameterizedTest @ValueSource(strings={"tuple","generation","raw"})
    void aNewOwnerOrGenerationDoesNotInheritAssignment(String mutation) {
        assertThat(assign(0,3L)).isTrue();long owner=10;String raw=RAW,id=PUBLIC;
        switch(mutation) {
            case "tuple"->{owner=99;jdbc.update("UPDATE tickets SET user_id=99");}
            case "generation"->{id="b".repeat(32);jdbc.update("UPDATE tickets SET ticket_public_id=?",id);}
            case "raw"->{raw=RAW.trim();jdbc.update("UPDATE tickets SET ticket_id=?",raw);}
        }
        var next=new GeneralTicketEventScope(owner,raw,id);
        assertThat(assignments.current(scope,auth(jdbc,"reader"))).isEmpty();
        assertThat(assignments.current(next,auth(jdbc,"reader")).orElseThrow().revision()).isZero();
        assertThat(assignments.recent(next,auth(jdbc,"reader"))).isEmpty();
        assertThat(assignments.assign(next,auth(jdbc,"worker"),1,0,1L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isEqualTo(2);
    }
    @Test void historyAndCurrentSelectionFilterFullOwnerBeforeLimit() {
        for(int i=1;i<=13;i++)seed(10,RAW,PUBLIC,i);
        for(int i=1;i<=20;i++){seed(99,RAW,PUBLIC,i);seed(10,RAW,"b".repeat(32),i);seed(10,RAW.trim(),PUBLIC,i);}
        assertThat(assignments.current(scope,auth(jdbc,"reader")).orElseThrow().revision()).isEqualTo(13);
        assertThat(assignments.recent(scope,auth(jdbc,"reader"))).extracting(GeneralTicketAssignmentService.Event::revision)
            .containsExactly(13L,12L,11L,10L,9L,8L,7L,6L,5L,4L);
    }
    private void seed(long owner,String raw,String id,int revision) {
        jdbc.update("INSERT INTO general_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,revision,owner_revision,assignee_user_id,assignee,actor_user_id,actor) VALUES(?,?,?,?,0,3,'candidate',1,'worker')",owner,raw,id,revision);
    }
    @ParameterizedTest @ValueSource(strings={"event","outbox"})
    void anyFailureRollsBackAssignmentAndOutboxTogether(String failure) {
        var before=jdbc.queryForList("SELECT * FROM tickets");
        if(failure.equals("event"))jdbc.execute("ALTER TABLE general_ticket_assignment_events ADD CONSTRAINT reject_assignment CHECK(revision<0)");
        else jdbc.execute("ALTER TABLE ui_event_outbox ADD CONSTRAINT reject_assignment_event CHECK(id<0)");
        assertThat(assign(0,3L)).isFalse();
        assertThat(jdbc.queryForList("SELECT * FROM tickets")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox",Integer.class)).isZero();
    }
    @Test void assignmentEventsAreImmutableAndVersionsCannotBeDuplicated() {
        assertThat(assign(0,3L)).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_assignment_events SET actor='other'" )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->seed(10,RAW,PUBLIC,1)).isInstanceOf(DataAccessException.class);
    }
    @Test void simultaneousFormsHaveOneWinner() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CyclicBarrier(2);
        try {
            var first=pool.submit(()->{ready.await(5,TimeUnit.SECONDS);return assign(0,1L);});
            var second=pool.submit(()->{ready.await(5,TimeUnit.SECONDS);return assign(0,3L);});
            assertThat(java.util.List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox",Integer.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","account","root"})
    void aWriteWaitingForLocksRechecksCommittedRevocationOrRootChange(String mutation) throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try(var connection=jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            String sql=switch(mutation) {
                case "read"->"UPDATE general_ticket_read_grants SET is_active=FALSE WHERE user_id=3";
                case "operate"->"UPDATE general_ticket_operate_grants SET is_active=FALSE WHERE user_id=3";
                case "account"->"UPDATE users SET enabled=FALSE WHERE id=3";
                default->"UPDATE tickets SET status='closed'";
            };
            try(var update=connection.prepareStatement(sql)){update.executeUpdate();}
            var started=new CountDownLatch(1);var pending=pool.submit(()->{started.countDown();return assign(0,3L);});
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->pending.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            connection.commit();assertThat(pending.get(10,TimeUnit.SECONDS)).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isZero();
        } finally {pool.shutdownNow();}
    }
    @Test void assignmentDoesNotInterruptAnObservedWorkInterval() {
        assertThat(work.transition(scope,auth(jdbc,"worker"),0,"pending","open")).isTrue();
        var before=jdbc.queryForList("SELECT * FROM general_ticket_work_intervals");
        assertThat(assignments.assign(scope,auth(jdbc,"worker"),1,0,3L)).isTrue();
        assertThat(work.find(PUBLIC,auth(jdbc,"worker")).orElseThrow().revision()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM general_ticket_work_intervals")).isEqualTo(before);
        assertThat(work.transition(scope,auth(jdbc,"worker"),1,"open","closed")).isTrue();
        assertThat(jdbc.queryForObject("SELECT outcome FROM general_ticket_work_intervals",String.class)).isEqualTo("COMPLETED");
    }
    @Test void missingAssignmentSchemaCannotFallBackToLegacyResponsibility() {
        jdbc.execute("DROP TABLE general_ticket_assignment_events");
        assertThat(assign(0,3L)).isFalse();assertThat(assignments.current(scope,auth(jdbc,"reader"))).isEmpty();
        assertThat(assignments.recent(scope,auth(jdbc,"reader"))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles",String.class)).isEqualTo("legacy-staff");
    }
    @Test void upgradeFromV68PreservesRootsLegacyResponsibilityAndGrants() {
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_assignment_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("68").load().migrate();
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,status,is_general,general_selection_source) VALUES(901,'prior-owner',?,'open',TRUE,'GENERAL_CHANNEL')",PUBLIC);
        upgrade.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES('prior-owner','legacy-staff','prior')");
        var roots=upgrade.queryForList("SELECT * FROM tickets");var legacy=upgrade.queryForList("SELECT * FROM ticket_responsibles");
        var grants=upgrade.queryForList("SELECT * FROM general_ticket_read_grants");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("69").load().migrate();
        assertThat(upgrade.queryForList("SELECT * FROM tickets")).isEqualTo(roots);
        assertThat(upgrade.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(legacy);
        assertThat(upgrade.queryForList("SELECT * FROM general_ticket_read_grants")).isEqualTo(grants);
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events",Integer.class)).isZero();
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_operate_grants",Integer.class)).isZero();
        // Keep the exact V68 -> V69 golden above. Later migrations legitimately add root columns.
        String oldColumns=roots.get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"")
            .collect(java.util.stream.Collectors.joining(","));
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("79").load().migrate();
        assertThat(upgrade.queryForList("SELECT "+oldColumns+" FROM tickets")).isEqualTo(roots);
        assertThat(upgrade.queryForMap("SELECT responsibility_revision,business_selection_source,questionnaire_catalog_sha256,questionnaire_version FROM tickets"))
            .containsEntry("responsibility_revision",0L).containsEntry("business_selection_source",null)
            .containsEntry("questionnaire_catalog_sha256",null).containsEntry("questionnaire_version",null);
        var v79Roots=upgrade.queryForList("SELECT * FROM tickets");
        assertThat(v79Roots).isNotEqualTo(roots); // The original SELECT * comparison already fails at V79, before this feature.
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("80").load().migrate();
        assertThat(upgrade.queryForList("SELECT * FROM tickets")).isEqualTo(v79Roots);
        String v80Columns=v79Roots.get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(","));
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT "+v80Columns+" FROM tickets")).isEqualTo(v79Roots);
        assertThat(upgrade.queryForMap("SELECT business_work_revision,business_categories::text AS categories FROM tickets"))
            .containsEntry("business_work_revision",0L).containsEntry("categories","[]");
        assertThat(upgrade.queryForObject("SELECT count(*) FROM business_ticket_work_events",Integer.class)).isZero();
        assertThat(upgrade.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(legacy);
        assertThat(upgrade.queryForList("SELECT * FROM general_ticket_read_grants")).isEqualTo(grants);
        for(String table:new String[]{"general_ticket_assignment_events","general_ticket_operate_grants","general_ticket_participants","general_ticket_participant_events"})
            assertThat(upgrade.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
    }
}
