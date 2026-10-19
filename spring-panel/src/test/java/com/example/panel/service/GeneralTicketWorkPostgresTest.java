package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
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

class GeneralTicketWorkPostgresTest {
    private JdbcTemplate jdbc;
    private GeneralTicketAccessService access;
    private GeneralTicketWorkService work;
    private MultibusinessFeatureFlags flags;
    private static final String RAW=" general/work Ю?% ";
    private static final String PUBLIC="a".repeat(32);
    private GeneralTicketEventScope scope;
    @BeforeEach void setup() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_work");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE general_ticket_read_grants(user_id BIGINT PRIMARY KEY,is_active BOOLEAN,updated_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,is_general BOOLEAN,business_id BIGINT,status TEXT,channel_id BIGINT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,message_text TEXT,message_type TEXT,attachment TEXT,rating INT,created_at TIMESTAMPTZ)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V61__ui_event_outbox_ticket_scope.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V62__scoped_ai_records.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V65__general_ticket_operate.sql")));
        jdbc.update("INSERT INTO users VALUES(1,'worker',TRUE,FALSE),(2,'reader',TRUE,FALSE),(3,'other',TRUE,FALSE)");
        jdbc.update("INSERT INTO general_ticket_read_grants VALUES(1,TRUE,NULL),(2,TRUE,NULL)");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,business_id,status,channel_id) VALUES(10,?,?,TRUE,NULL,'pending',9)",RAW,PUBLIC);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        access=new GeneralTicketAccessService(jdbc);
        assertThat(access.grantOperate(1)).isTrue();
        work=new GeneralTicketWorkService(jdbc,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)),flags);
        scope=new GeneralTicketEventScope(10L,RAW,PUBLIC);
    }
    private boolean change(long revision,String from,String to) { return work.transition(scope,"worker",revision,from,to); }
    @Test void triageIsAtomicVersionedAndOnlyProducesGeneralMetadataOutbox() {
        assertThat(work.find(PUBLIC,"WORKER").orElseThrow().canOperate()).isTrue();
        assertThat(change(0,"pending","open")).isTrue();
        assertThat(work.find(PUBLIC,"worker").orElseThrow().revision()).isEqualTo(1);
        assertThat(change(0,"pending","open")).isFalse();
        assertThat(change(1,"open","pending")).isTrue();
        assertThat(change(0,"pending","open")).isFalse(); // ABA status is not sufficient.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT event_type,ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,message_text,attachment FROM ui_event_outbox"))
            .allSatisfy(row->assertThat(row).containsEntry("event_type","ticket_status_changed").containsEntry("ticket_user_id",10L)
                .containsEntry("ticket_id",RAW).containsEntry("ticket_public_id",PUBLIC).containsEntry("ticket_scope_kind","GENERAL")
                .containsEntry("ticket_business_id",null).containsEntry("message_text",null).containsEntry("attachment",null));
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","business","unresolved","tuple","generation","collision","missing-owner","flag"})
    void currentOwnerAndBothRightsAreRequiredAtMutation(String mutation) {
        switch(mutation) {
            case "read"->jdbc.update("UPDATE general_ticket_read_grants SET is_active=FALSE WHERE user_id=1");
            case "operate"->jdbc.update("UPDATE general_ticket_operate_grants SET is_active=FALSE WHERE user_id=1");
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "duplicate"->jdbc.update("INSERT INTO users VALUES(4,'WORKER',FALSE,FALSE)");
            case "missing-account"->jdbc.update("DELETE FROM users WHERE id=1");
            case "business"->jdbc.update("UPDATE tickets SET is_general=FALSE,business_id=1 WHERE user_id=10");
            case "unresolved"->jdbc.update("UPDATE tickets SET is_general=FALSE WHERE user_id=10");
            case "tuple"->jdbc.update("UPDATE tickets SET user_id=99 WHERE user_id=10");
            case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=10","b".repeat(32));
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,status) VALUES(20,?,?,FALSE,'pending')",RAW,"b".repeat(32));
            case "missing-owner"->jdbc.update("DELETE FROM tickets");
            case "flag"->flags.setGeneralTicketQueue(false);
        }
        assertThat(change(0,"pending","open")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox",Integer.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"closed","resolved","unknown","same","negative"})
    void unsupportedLifecycleAndMalformedRevisionDoNotInvokeLegacyWork(String state) {
        String from="pending",to=state;long revision=0;
        if(state.equals("same"))to="pending";
        if(state.equals("negative")) {to="open";revision=-1;}
        assertThat(change(revision,from,to)).isFalse();
        assertThat(work.find(PUBLIC,"worker").orElseThrow().revision()).isZero();
    }
    @Test void readersAndSystemBusinessPrincipalsNeverInheritOperate() {
        assertThat(work.find(PUBLIC,"reader").orElseThrow().canOperate()).isFalse();
        assertThat(work.transition(scope,"reader",0,"pending","open")).isFalse();
        assertThat(work.find(PUBLIC,"other")).isEmpty();
        assertThat(access.grantOperate(3)).isFalse();
        jdbc.update("INSERT INTO users VALUES(4,' worker ',TRUE,FALSE)");
        assertThat(work.find(PUBLIC," worker ")).isEmpty();
    }
    @Test void revokingReadRevokesOperateAndReadRestoreDoesNotRestoreWrite() {
        assertThat(access.revokeRead(1)).isTrue();
        assertThat(access.grantRead(1)).isTrue();
        assertThat(work.find(PUBLIC,"worker").orElseThrow().canOperate()).isFalse();
        assertThat(change(0,"pending","open")).isFalse();
        assertThat(access.grantOperate(1)).isTrue();
        assertThat(change(0,"pending","open")).isTrue();
        assertThat(access.revokeOperate(1)).isTrue();
        assertThat(access.canRead(1L)).isTrue();
    }
    @Test void concurrentOperateGrantWaitsForReadRevocationAndCannotResurrectWrite() throws Exception {
        var executor=Executors.newSingleThreadExecutor();
        try(var connection=jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var update=connection.prepareStatement("UPDATE general_ticket_read_grants SET is_active=FALSE WHERE user_id=2")) {update.executeUpdate();}
            var started=new CountDownLatch(1);
            var grant=executor.submit(()->{started.countDown();return access.grantOperate(2);});
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->grant.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            connection.commit();
            assertThat(grant.get(5,TimeUnit.SECONDS)).isFalse();
        } finally {executor.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"audit","outbox"})
    void anyDownstreamFailureRollsBackStatusRevisionAuditAndOutbox(String failure) {
        if(failure.equals("audit"))jdbc.execute("ALTER TABLE general_ticket_work_events ADD CONSTRAINT reject_audit CHECK(revision<0)");
        else jdbc.execute("ALTER TABLE ui_event_outbox ADD CONSTRAINT reject_event CHECK(id<0)");
        assertThat(change(0,"pending","open")).isFalse();
        var state=work.find(PUBLIC,"worker").orElseThrow();
        assertThat(state.status()).isEqualTo("pending");assertThat(state.revision()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox",Integer.class)).isZero();
    }
    @Test void revisionIsDatabaseMaintainedForLegacyWritersAndAuditCannotChange() {
        jdbc.update("UPDATE tickets SET status='open'");
        jdbc.update("UPDATE tickets SET status='pending'");
        assertThat(work.find(PUBLIC,"worker").orElseThrow().revision()).isEqualTo(2);
        assertThat(change(0,"pending","open")).isFalse();
        assertThatThrownBy(()->jdbc.update("UPDATE tickets SET general_work_revision=0")).isInstanceOf(DataAccessException.class);
        assertThat(change(2,"pending","open")).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_work_events SET actor='forged'")).isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE tickets SET is_general=FALSE,business_id=1");
        jdbc.update("UPDATE tickets SET is_general=TRUE,business_id=NULL");
        assertThat(work.find(PUBLIC,"worker").orElseThrow().revision()).isEqualTo(5);
    }
    @Test void upgradeFromV64DoesNotGrantWriteOrReclassifyExistingTickets() {
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_work_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("64").load().migrate();
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,status,is_general,general_selection_source) VALUES(701,'general-old','open',TRUE,'GENERAL_CHANNEL'),(702,'unresolved-old','pending',FALSE,NULL)");
        var before=upgrade.queryForList("SELECT user_id,ticket_id,status,is_general,business_id,ticket_public_id FROM tickets ORDER BY user_id");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT user_id,ticket_id,status,is_general,business_id,ticket_public_id FROM tickets ORDER BY user_id")).isEqualTo(before);
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_operate_grants",Integer.class)).isZero();
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events",Integer.class)).isZero();
        assertThat(upgrade.queryForList("SELECT general_work_revision FROM tickets")).allSatisfy(row->assertThat(row.get("general_work_revision")).isEqualTo(0L));
    }
}
