package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopedAssignmentCandidatePostgresTest {
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private ScopedAssignmentCandidateService candidates;
    private final UiEventTicketScope scope=new UiEventTicketScope(10L," T/?=root ","a".repeat(32),"BUSINESS",1L);

    @BeforeEach void setup() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_assignment_pool");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN,department TEXT)");
        jdbc.execute("ALTER TABLE users ADD COLUMN full_name TEXT,ADD COLUMN photo TEXT,ADD COLUMN role TEXT,ADD COLUMN role_id BIGINT");
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT)");
        jdbc.execute("CREATE TABLE user_authorities(user_id BIGINT,authority TEXT)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT)");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN,channel_id BIGINT,status TEXT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,last_read_at TIMESTAMPTZ)");
        jdbc.update("INSERT INTO channels VALUES(7,TRUE),(8,TRUE)");
        jdbc.update("INSERT INTO businesses VALUES(1,'active'),(2,'active')");
        jdbc.update("INSERT INTO users(id,username,enabled,is_blocked,department) VALUES(1,'Alice',TRUE,FALSE,'Support'),(2,'bob',TRUE,FALSE,'Support'),(3,'foreign',TRUE,FALSE,'Support'),(4,'viewer',TRUE,FALSE,'Support'),(5,'all',TRUE,FALSE,'Support'),(6,'system-admin',TRUE,FALSE,'Support'),(7,'ai_agent',TRUE,FALSE,'Support')");
        jdbc.update("INSERT INTO business_memberships VALUES(1,1,'OPERATOR',TRUE),(2,1,'MANAGER',TRUE),(3,2,'ADMIN',TRUE),(4,1,'VIEWER',TRUE),(7,1,'OPERATOR',TRUE)");
        jdbc.update("INSERT INTO business_access_grants VALUES(5,'ALL_BUSINESSES','OPERATOR',TRUE)");
        jdbc.update("INSERT INTO user_authorities VALUES(6,'ROLE_ADMIN'),(6,'PAGE_DIALOGS')");
        jdbc.update("INSERT INTO tickets VALUES(10,?,?,1,FALSE,7,'pending')",scope.ticketId(),scope.ticketPublicId());
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setStrictReadScope(true);
        candidates=new ScopedAssignmentCandidateService(jdbc,flags);
    }

    @ParameterizedTest @CsvSource({"membership,OPERATOR","membership,MANAGER","membership,ADMIN","all,OPERATOR","all,MANAGER","all,ADMIN"})
    void onlyCurrentCanonicalOperateAccountsEnterThePool(String source,String role) {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
        if(source.equals("membership"))jdbc.update("INSERT INTO business_memberships VALUES(1,1,?,TRUE)",role);
        else jdbc.update("INSERT INTO business_access_grants VALUES(1,'ALL_BUSINESSES',?,TRUE)",role);
        assertThat(candidates.capture(scope,7L).orElseThrow().openCounts()).containsOnlyKeys("alice","bob","all");
        assertThat(candidates.capture(scope,7L).orElseThrow().legacyUnresolved()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","null-enabled","null-blocked","duplicate","foreign","viewer","revoked","inactive","unknown-role","archived","missing-business","all-viewer","all-revoked","all-unknown-scope","system-only","padded"})
    void invalidAccountOrGrantNeverReachesTheStrategy(String mutation) {
        switch(mutation) {
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "null-enabled"->jdbc.update("UPDATE users SET enabled=NULL WHERE id=1");
            case "null-blocked"->jdbc.update("UPDATE users SET is_blocked=NULL WHERE id=1");
            case "duplicate"->jdbc.update("INSERT INTO users(id,username,enabled,is_blocked,department) VALUES(8,'ALICE',FALSE,FALSE,'Support')");
            case "foreign"->jdbc.update("UPDATE business_memberships SET business_id=2 WHERE user_id=1");
            case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=1");
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            case "inactive"->jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1");
            case "unknown-role"->jdbc.update("UPDATE business_memberships SET role_code='CUSTOM' WHERE user_id=1");
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
            case "missing-business"->jdbc.update("DELETE FROM businesses WHERE id=1");
            case "padded"->jdbc.update("UPDATE users SET username=' Alice ' WHERE id=1");
            default->{
                jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
                if(mutation.equals("system-only"))jdbc.update("INSERT INTO user_authorities VALUES(1,'ROLE_ADMIN'),(1,'PAGE_DIALOGS')");
                jdbc.update("INSERT INTO business_access_grants VALUES(1,?,?,?)",mutation.equals("all-unknown-scope")?"ROLE_ADMIN":"ALL_BUSINESSES",
                    mutation.equals("all-viewer")?"VIEWER":"OPERATOR",!mutation.equals("all-revoked")&&!mutation.equals("system-only"));
            }
        }
        assertThat(candidates.capture(scope,7L).orElseThrow().openCounts()).doesNotContainKey("alice");
    }
    @ParameterizedTest @ValueSource(strings={"owner","tuple","public","channel","inactive-channel","missing","collision","general"})
    void staleOrAmbiguousRootCannotSupplyAnyCandidates(String mutation) {
        switch(mutation) {
            case "owner"->jdbc.update("UPDATE tickets SET business_id=2");
            case "tuple"->jdbc.update("UPDATE tickets SET user_id=11");
            case "public"->jdbc.update("UPDATE tickets SET ticket_public_id=?", "b".repeat(32));
            case "channel"->jdbc.update("UPDATE tickets SET channel_id=8");
            case "inactive-channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=7");
            case "missing"->jdbc.update("DELETE FROM tickets");
            case "collision"->jdbc.update("INSERT INTO tickets VALUES(11,?,?,2,FALSE,8,'open')",scope.ticketId(),"b".repeat(32));
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE");
        }
        assertThat(candidates.capture(scope,7L)).isEmpty();
    }
    private void load(long user,String id,Long business,boolean general,String status,String operator) {
        jdbc.update("INSERT INTO tickets VALUES(?,?,?, ?,?,7,?)",user,id,UUID.randomUUID().toString().replace("-",""),business,general,status);
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES(?,?)",id,operator);
    }
    @Test void workloadsCountOnlyOpenUniqueRootsOfTheSameBusinessNotGeneralForeignOrAmbiguousRows() {
        load(21,"own",1L,false,"in_progress","ALICE");load(22,"foreign",2L,false,"open","Alice");
        load(23,"general",null,true,"open","Alice");load(24,"legacy",null,false,"open","Alice");
        load(25,"closed",1L,false," Closed ","Alice");load(26,"resolved",1L,false,"resolved_by_operator","Alice");
        load(27,"ambiguous",1L,false,"open","Alice");
        jdbc.update("INSERT INTO tickets VALUES(28,'ambiguous',?,2,FALSE,7,'open')","b".repeat(32));
        load(29,"null-status",1L,false,null,"bob");
        var counts=candidates.capture(scope,7L).orElseThrow().openCounts();
        assertThat(counts).containsEntry("alice",1L).containsEntry("bob",1L).containsEntry("all",0L);
    }
    @Test void grantsAndRootAreRecheckedOnEveryCaptureWithoutCachedMembership() {
        assertThat(candidates.capture(scope,7L).orElseThrow().openCounts()).containsKey("alice");
        jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
        assertThat(candidates.capture(scope,7L).orElseThrow().openCounts()).doesNotContainKey("alice");
        jdbc.update("DELETE FROM business_access_grants");
        assertThat(candidates.capture(scope,7L).orElseThrow().openCounts()).doesNotContainKey("all");
    }
    @Test void unresolvedIsAnExplicitPreStrictCompatibilityProofAndGeneralNeverUsesIt() {
        jdbc.update("UPDATE tickets SET business_id=NULL");
        var unresolved=new UiEventTicketScope(10L,scope.ticketId(),scope.ticketPublicId(),"UNRESOLVED",null);
        assertThat(candidates.capture(unresolved,7L)).isEmpty();flags.setStrictReadScope(false);
        assertThat(candidates.capture(unresolved,7L).orElseThrow().legacyUnresolved()).isTrue();
        jdbc.update("UPDATE tickets SET is_general=TRUE");assertThat(candidates.capture(unresolved,7L)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"flags","schema","null","wrong-channel"})
    void unavailableAdmissionDoesNotReturnAPartialOrGlobalPool(String reason) {
        switch(reason) {
            case "flags"->flags.getFoundation().setEnabled(false);
            case "schema"->jdbc.execute("DROP TABLE business_access_grants");
        }
        assertThat(candidates.capture(reason.equals("null")?null:scope,reason.equals("wrong-channel")?8L:7L)).isEmpty();
    }
    @Test void scopedDirectoryNeverTurnsAnEmptySetIntoTheGlobalDirectory() {
        var directory=new DialogParticipantService(jdbc,jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        assertThat(directory.loadAssignableOperators(Set.of())).isEmpty();
        assertThat(directory.loadAssignableOperators(Set.of(" ",""))).isEmpty();
        assertThat(directory.loadAssignableOperators(Set.of("alice"))).extracting(item->item.username()).containsExactly("Alice");
    }
    @ParameterizedTest @ValueSource(strings={"revoked","owner","ambiguous"})
    void finalAssignmentGuardStillRejectsChangesAfterAPositivePoolSnapshot(String mutation) {
        assertThat(candidates.capture(scope,7L).orElseThrow().openCounts()).containsKey("alice");
        switch(mutation) {
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            case "owner"->jdbc.update("UPDATE tickets SET business_id=2");
            case "ambiguous"->jdbc.update("INSERT INTO users(id,username,enabled,is_blocked,department) VALUES(8,'ALICE',TRUE,FALSE,'Support')");
        }
        var writer=new DialogResponsibilityService(jdbc,new LegacyTicketIdJdbcGuard(jdbc),flags);
        assertThat(writer.assignScopedResponsibleIfMissing(scope,"alice","routing")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Integer.class)).isZero();
    }
    @Test void fullV76SchemaExercisesCaptureDirectoryRoutingAndFinalConditionalInsert() throws Exception {
        var actual=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_assignment_full");
        org.flywaydb.core.Flyway.configure().dataSource(actual.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        long business=actual.queryForObject("SELECT id FROM businesses WHERE code='sushi'",Long.class);
        long alice=actual.queryForObject("INSERT INTO users(username,password) VALUES('alice','unused') RETURNING id",Long.class);
        long bob=actual.queryForObject("INSERT INTO users(username,password) VALUES('bob','unused') RETURNING id",Long.class);
        actual.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR'),(?,?,'MANAGER')",alice,business,bob,business);
        long channelId=actual.queryForObject("INSERT INTO channels(token,channel_name) VALUES('r157-fixture','Routing test') RETURNING id",Long.class);
        actual.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id) VALUES(10,?,?,?,?),(11,'workload',?,?,?)",
            scope.ticketId(),scope.ticketPublicId(),business,channelId,"b".repeat(32),business,channelId);
        actual.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES('workload','alice','fixture')");
        var proof=new UiEventTicketScope(10L,scope.ticketId(),scope.ticketPublicId(),"BUSINESS",business);
        var pool=new ScopedAssignmentCandidateService(actual,flags);
        assertThat(pool.capture(proof,channelId).orElseThrow().openCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("alice",1L,"bob",0L));
        var guard=new LegacyTicketIdJdbcGuard(actual);
        var participantDirectory=new DialogParticipantService(actual,actual,guard);
        var routing=new ChannelAssignmentRoutingService(new com.fasterxml.jackson.databind.ObjectMapper(),mock(SharedConfigService.class),participantDirectory,
            mock(DialogLookupReadService.class),null,pool);
        var channel=new com.example.panel.entity.Channel();channel.setId(channelId);
        channel.setQuestionsCfg("{\"assignmentRouting\":{\"enabled\":true,\"mode\":\"operator_pool\",\"operatorUsernames\":[\"alice\",\"bob\"],\"strategy\":\"least_loaded\",\"assignResponsibleOnCreate\":true}}");
        var decision=routing.resolveScoped(channel,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,proof);
        assertThat(decision.assignee()).isEqualTo("bob");
        var writer=new DialogResponsibilityService(actual,guard,flags);
        assertThat(writer.assignScopedResponsibleIfMissing(proof,decision.assignee(),"routing")).isEqualTo("bob");
        assertThat(pool.capture(proof,channelId).orElseThrow().openCounts()).containsEntry("bob",1L);
    }
}
