package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopedSlaAssignmentPostgresTest {
    private static JdbcTemplate jdbc;
    private long business,other,channel,operator;
    private String raw,publicId,username;
    private MultibusinessFeatureFlags flags;
    private DialogLookupReadService read;
    private ScopedSlaAssignmentService writer;
    @BeforeAll static void schema() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_sla");
        org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
    }
    @BeforeEach void fixture() {
        jdbc.update("UPDATE users SET enabled=FALSE");
        String suffix=UUID.randomUUID().toString().replace("-","");
        business=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'Same label') RETURNING id",Long.class,"sla-a-"+suffix);
        other=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'Same label') RETURNING id",Long.class,"sla-b-"+suffix);
        channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES(?,'Telegram','telegram') RETURNING id",Long.class,"sla-"+suffix);
        username="sla-op-"+suffix;
        operator=jdbc.queryForObject("INSERT INTO users(username,password) VALUES(?,'unused') RETURNING id",Long.class,username);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR')",operator,business);
        raw=" SLA/?="+suffix+" ";publicId=suffix;
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES(901,?,?,?,?,'open')",raw,publicId,business,channel);
        long message=jdbc.queryForObject("SELECT COALESCE(max(group_msg_id),0)+1 FROM messages",Long.class);
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id,business,problem,created_at) VALUES(?,901,?,?,'Same label','Help',CURRENT_TIMESTAMP-INTERVAL '2 days')",message,raw,channel);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setStrictReadScope(true);
        var guard=new LegacyTicketIdJdbcGuard(jdbc);
        read=new DialogLookupReadService(jdbc,jdbc,mock(PanelUserPhotoService.class),new PanelTimestampSqlSupport(),guard);
        writer=new ScopedSlaAssignmentService(jdbc,flags);
    }
    private DialogLookupReadService.ScopedSlaDialog capture() {
        return read.loadScopedSlaDialogs().stream().filter(row->row.scope().ticketId().equals(raw)).findFirst().orElseThrow();
    }
    private long events() {return jdbc.queryForObject("SELECT count(*) FROM scoped_sla_assignment_events WHERE ticket_id=?",Long.class,raw);}
    private long revision() {return jdbc.queryForObject("SELECT responsibility_revision FROM tickets WHERE user_id=901 AND ticket_id=?",Long.class,raw);}
    private void assignLegacy(String name) {
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES(?,?,'manual') ON CONFLICT(ticket_id) DO UPDATE SET responsible=EXCLUDED.responsible,assigned_by='manual'",raw,name);
    }
    @Test void sourceFreezesOwnerRevisionAndExactResponsibleThenOneAtomicAssignmentAndProvenance() {
        var source=capture();assertThat(source.scope().businessId()).isEqualTo(business);assertThat(source.revision()).isZero();
        assertThat(source.expectedResponsible()).isNull();assertThat(source.dialog().requestNumber()).isNotBlank();
        assertThat(writer.assign(source,username,"scheduler","rules","rule-a")).isTrue();
        assertThat(revision()).isEqualTo(1);assertThat(events()).isEqualTo(1);
        var event=jdbc.queryForMap("SELECT * FROM scoped_sla_assignment_events WHERE ticket_id=?",raw);
        assertThat(event).containsEntry("ticket_user_id",901L).containsEntry("ticket_public_id",publicId).containsEntry("business_id",business)
            .containsEntry("revision",1L).containsEntry("previous_responsible",null).containsEntry("assignee",username);
        assertThat(writer.assign(source,username,"scheduler","rules","rule-a")).isFalse();assertThat(events()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"foreign","viewer","revoked","disabled","blocked","duplicate","archived","owner","recreated","channel","inactive","closed","general","strict-flag","collision"})
    void lateOwnerOrPermissionChangeCannotWriteOrAuditSuccess(String mutation) {
        var source=capture();
        switch(mutation) {
            case "foreign"->jdbc.update("UPDATE business_memberships SET business_id=? WHERE user_id=?",other,operator);
            case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",operator);
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",operator);
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",operator);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",operator);
            case "duplicate"->jdbc.update("INSERT INTO users(username,password) VALUES(?,'unused')",username.toUpperCase(Locale.ROOT));
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",business);
            case "owner"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
            case "recreated"->{
                jdbc.update("DELETE FROM tickets WHERE user_id=901 AND ticket_id=?",raw);
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES(901,?,?,?,?,'open')",
                    raw,UUID.randomUUID().toString().replace("-",""),business,channel);
            }
            case "channel"->jdbc.update("UPDATE tickets SET channel_id=NULL WHERE ticket_id=?",raw);
            case "inactive"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channel);
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",raw);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
            case "strict-flag"->flags.getFoundation().setEnabled(false);
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(902,?,?,?)",raw,UUID.randomUUID().toString().replace("-",""),other);
        }
        assertThat(writer.assign(source,username,"scheduler","fallback",null)).isFalse();
        assertThat(events()).isZero();assertThat(revision()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"update-aba","delete-aba","concurrent-assign"})
    void revisionRejectsAbaEvenWhenResponsibleReturnsToTheSameValue(String kind) {
        if(!kind.equals("concurrent-assign"))assignLegacy("original");
        var source=capture();long initial=revision();
        if(kind.equals("delete-aba")) {jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=?",raw);assignLegacy("original");}
        else {assignLegacy("changed");if(kind.equals("update-aba"))assignLegacy("original");}
        assertThat(revision()).isGreaterThan(initial);
        assertThat(writer.assign(source,username,"scheduler","rules","r")).isFalse();assertThat(events()).isZero();
    }
    @Test void redirectUsesExactStoredPaddingAndReadMarksDoNotInvalidateAnAssignmentSnapshot() {
        assignLegacy(" Original Owner ");var source=capture();
        assertThat(source.expectedResponsible()).isEqualTo(" Original Owner ");
        jdbc.update("UPDATE ticket_responsibles SET last_read_at=CURRENT_TIMESTAMP WHERE ticket_id=?",raw);
        assertThat(revision()).isEqualTo(source.revision());
        assertThat(writer.assign(source,username,"scheduler","rules","r")).isTrue();
        assertThat(jdbc.queryForObject("SELECT previous_responsible FROM scoped_sla_assignment_events WHERE ticket_id=?",String.class,raw)).isEqualTo(" Original Owner ");
    }
    @Test void auditFailureRollsBackResponsibleAndRevisionThenExactRetrySucceeds() {
        var source=capture();long old=revision();
        jdbc.execute("CREATE FUNCTION reject_r158_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic audit failure'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_r158_audit BEFORE INSERT ON scoped_sla_assignment_events FOR EACH ROW EXECUTE FUNCTION reject_r158_audit()");
        try {assertThat(writer.assign(source,username,"scheduler","rules","r")).isFalse();assertThat(revision()).isEqualTo(old);assertThat(events()).isZero();}
        finally{jdbc.execute("DROP TRIGGER reject_r158_audit ON scoped_sla_assignment_events");jdbc.execute("DROP FUNCTION reject_r158_audit()");}
        assertThat(writer.assign(source,username,"scheduler","rules","r")).isTrue();assertThat(events()).isEqualTo(1);
    }
    @Test void revisionCannotBeResetAndScopedAuditCannotBeRewrittenOrDeleted() {
        var source=capture();assertThat(writer.assign(source,username,"scheduler","rules","r")).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE tickets SET responsibility_revision=0 WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE scoped_sla_assignment_events SET actor='other' WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM scoped_sla_assignment_events WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
    }
    @Test void sourceNeverLendsAReplacedOwnerAndRejectsMessageTupleOrChannelMismatch() {
        var source=capture();
        jdbc.update("UPDATE messages SET user_id=902 WHERE ticket_id=?",raw);
        assertThat(read.loadScopedSlaDialogs()).noneMatch(row->row.scope().ticketId().equals(raw));
        jdbc.update("UPDATE messages SET user_id=901,channel_id=NULL WHERE ticket_id=?",raw);
        assertThat(capture().scope()).isEqualTo(source.scope());
        long foreignChannel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES(?,'Other','telegram') RETURNING id",Long.class,UUID.randomUUID().toString());
        jdbc.update("UPDATE messages SET channel_id=? WHERE ticket_id=?",foreignChannel,raw);
        assertThat(read.loadScopedSlaDialogs()).noneMatch(row->row.scope().ticketId().equals(raw));
    }
    @Test void onlyTypedPreStrictUnresolvedMayUseLegacyAssignmentAndNotGeneral() {
        jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);
        var source=capture();assertThat(source.scope().scopeKind()).isEqualTo("UNRESOLVED");
        assertThat(writer.assign(source,"legacy","scheduler","fallback",null)).isFalse();
        flags.setStrictReadScope(false);assertThat(writer.assign(source,"legacy","scheduler","fallback",null)).isTrue();
        assertThat(jdbc.queryForObject("SELECT business_id FROM scoped_sla_assignment_events WHERE ticket_id=?",Long.class,raw)).isNull();
    }
    @Test void competingWorkersCommitOneTransitionAndOneScopedEvent() throws Exception {
        var source=capture();
        var start=new java.util.concurrent.CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt=()->{
            try {start.await();} catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
            return writer.assign(source,username,"scheduler","rules","r");
        };
        var first=java.util.concurrent.CompletableFuture.supplyAsync(attempt);
        var second=java.util.concurrent.CompletableFuture.supplyAsync(attempt);start.countDown();
        assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(true,false);
        assertThat(revision()).isEqualTo(1);assertThat(events()).isEqualTo(1);
    }
    @Test void upgradePreservesEveryExistingRootAndAssignmentFieldThenTracksLegacyWriters() {
        var previous=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("sla_upgrade");
        var config=org.flywaydb.core.Flyway.configure().dataSource(previous.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql");
        config.target("76").load().migrate();
        previous.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(808,'old-sla','open')");
        previous.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,last_read_at) VALUES('old-sla',' A ','human',CURRENT_TIMESTAMP)");
        var root=previous.queryForMap("SELECT * FROM tickets WHERE ticket_id='old-sla'");
        var assignment=previous.queryForMap("SELECT * FROM ticket_responsibles WHERE ticket_id='old-sla'");
        org.flywaydb.core.Flyway.configure().dataSource(previous.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        var after=previous.queryForMap("SELECT * FROM tickets WHERE ticket_id='old-sla'");
        assertThat(after.remove("responsibility_revision")).isEqualTo(0L);assertThat(after).isEqualTo(root);
        assertThat(previous.queryForMap("SELECT * FROM ticket_responsibles WHERE ticket_id='old-sla'")).isEqualTo(assignment);
        previous.update("UPDATE ticket_responsibles SET last_read_at=CURRENT_TIMESTAMP WHERE ticket_id='old-sla'");
        assertThat(previous.queryForObject("SELECT responsibility_revision FROM tickets WHERE ticket_id='old-sla'",Long.class)).isZero();
        previous.update("UPDATE ticket_responsibles SET responsible='B' WHERE ticket_id='old-sla'");
        previous.update("DELETE FROM ticket_responsibles WHERE ticket_id='old-sla'");
        previous.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES('old-sla',' A ')");
        assertThat(previous.queryForObject("SELECT responsibility_revision FROM tickets WHERE ticket_id='old-sla'",Long.class)).isEqualTo(3L);
        assertThat(previous.queryForObject("SELECT count(*) FROM scoped_sla_assignment_events",Long.class)).isZero();
    }
}
