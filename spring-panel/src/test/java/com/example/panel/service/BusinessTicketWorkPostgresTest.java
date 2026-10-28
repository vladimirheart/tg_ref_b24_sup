package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessTicketWorkPostgresTest {
    private static final String RAW=" R167/Ю?%#+ ",PUBLIC="a".repeat(32);
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private BusinessTicketWorkService service;
    private UiEventTicketScope scope;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_work");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT,resolved_at TIMESTAMPTZ,resolved_by TEXT,closed_count INT DEFAULT 4,
                reopen_count INT DEFAULT 7,last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,
                ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            INSERT INTO users(id,username) VALUES(1,'Operator'),(2,'viewer'),(3,'foreign');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR'),(2,11,'VIEWER'),(3,22,'ADMIN');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES(42,?,?,11,9,'pending'),(88,?,?,22,19,'pending')",
            RAW,PUBLIC,RAW.trim(),"b".repeat(32));
        for(String table:List.of("ticket_categories","ticket_active","ticket_spans","ticket_responsibles","ticket_participants","pending_feedback_requests","dialog_action_audit","notifications"))
            jdbc.execute("CREATE TABLE "+table+"(ticket_id TEXT,payload TEXT); INSERT INTO "+table+" VALUES ('"+RAW.replace("'","''")+"','legacy')");
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);flags.setStrictReadScope(true);
        service=new BusinessTicketWorkService(jdbc,flags,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)),new ObjectMapper());
        scope=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    }
    private Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("tickets","ticket_categories","ticket_active","ticket_spans","ticket_responsibles","ticket_participants","pending_feedback_requests","dialog_action_audit","notifications","ui_event_outbox","business_ticket_work_events"))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY ticket_id"));
        return result;
    }
    /** Fixture identity captured without re-resolving a possibly renamed account. */
    private org.springframework.security.core.Authentication boundActor(String name){
        long id=name.equals("viewer")?2:name.equals("foreign")?3:1;
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            new com.example.panel.security.PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());
    }
    private boolean command(String action) {
        return service.change(scope,boundActor("Operator"),0,action.equals("reopen")?"resolved":"pending",action,action.equals("reopen")?List.of():List.of(" Billing ","Billing","Payment"));
    }
    private void prepare(String action){if(action.equals("reopen"))jdbc.update("UPDATE tickets SET status='resolved' WHERE ticket_id=?",RAW);}
    private long revision(){return jdbc.queryForObject("SELECT business_work_revision FROM tickets WHERE ticket_id=?",Long.class,RAW);}
    private boolean currentCommand(String action) {
        return service.change(scope,boundActor("Operator"),revision(),action.equals("reopen")?"resolved":"pending",action,action.equals("reopen")?List.of():List.of(" Billing ","Billing","Payment"));
    }
    @Test void closeReopenAndCategoriesAreAtomicVersionedAndDoNotEnterLegacyWorkflows() {
        var before=snapshot();
        assertThat(command("close")).isTrue();
        assertThat(service.find(PUBLIC,"Operator").orElseThrow()).satisfies(v->{
            assertThat(v.status()).isEqualTo("resolved");assertThat(v.revision()).isEqualTo(1);assertThat(v.categories()).containsExactly("Billing","Payment");
        });
        assertThat(jdbc.queryForMap("SELECT closed_count,reopen_count,resolved_by,resolved_at FROM tickets WHERE ticket_id=?",RAW))
            .containsEntry("closed_count",5).containsEntry("reopen_count",7).containsEntry("resolved_by","Operator").doesNotContainValue(null);
        assertThat(service.change(scope,boundActor("Operator"),1,"resolved","reopen",List.of())).isTrue();
        assertThat(jdbc.queryForMap("SELECT status,closed_count,reopen_count,resolved_by,resolved_at FROM tickets WHERE ticket_id=?",RAW))
            .containsEntry("status","pending").containsEntry("closed_count",5).containsEntry("reopen_count",8).containsEntry("resolved_by",null).containsEntry("resolved_at",null);
        assertThat(service.change(scope,boundActor("Operator"),2,"pending","categories",List.of())).isTrue();
        assertThat(revision()).isEqualTo(3);
        assertThat(service.change(scope,boundActor("Operator"),3,"pending","categories",List.of())).isFalse();
        var events=service.recent(scope,"viewer");assertThat(events).extracting(BusinessTicketWorkService.Event::action).containsExactly("categories","reopen","close");
        assertThat(events.get(1).categories()).containsExactly("Billing","Payment");
        assertThat(jdbc.queryForList("SELECT ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,event_type FROM ui_event_outbox"))
            .hasSize(3).allSatisfy(row->assertThat(row).containsEntry("ticket_user_id",42L).containsEntry("ticket_id",RAW)
                .containsEntry("ticket_public_id",PUBLIC).containsEntry("ticket_scope_kind","BUSINESS").containsEntry("ticket_business_id",11L).containsEntry("event_type","business_work_changed"));
        var after=snapshot();
        for(String table:before.keySet())if(!Set.of("tickets","business_ticket_work_events","ui_event_outbox").contains(table))assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        assertThat(jdbc.queryForMap("SELECT * FROM tickets WHERE ticket_id=?",RAW.trim())).isEqualTo(before.get("tickets").stream().filter(row->RAW.trim().equals(row.get("ticket_id"))).findFirst().orElseThrow());
        assertThatThrownBy(()->jdbc.update("UPDATE business_ticket_work_events SET actor='changed'")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM business_ticket_work_events")).isInstanceOf(RuntimeException.class);
    }
    private void mutate(String kind) {
        switch(kind) {
            case "business"->jdbc.update("UPDATE tickets SET business_id=22 WHERE ticket_id=?",RAW);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE ticket_id=?",RAW);
            case "unresolved"->jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",RAW);
            case "missing"->jdbc.update("DELETE FROM tickets WHERE ticket_id=?",RAW);
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(99,?,? ,11,'pending')",RAW,"c".repeat(32));
            case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE ticket_id=?","d".repeat(32),RAW);
            case "user"->jdbc.update("UPDATE tickets SET user_id=99 WHERE ticket_id=?",RAW);
            case "channel"->jdbc.update("UPDATE tickets SET channel_id=10 WHERE ticket_id=?",RAW);
            case "status-aba"->{jdbc.update("UPDATE tickets SET status='open' WHERE ticket_id=?",RAW);jdbc.update("UPDATE tickets SET status='pending' WHERE ticket_id=?",RAW);}
            case "category-aba"->{jdbc.update("UPDATE tickets SET business_categories='[\"A\"]' WHERE ticket_id=?",RAW);jdbc.update("UPDATE tickets SET business_categories='[]' WHERE ticket_id=?",RAW);}
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=1");
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "renamed"->jdbc.update("UPDATE users SET username='changed' WHERE id=1");
            case "duplicate-identity"->jdbc.update("INSERT INTO users(id,username) VALUES(9,'OPERATOR')");
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
            case "foundation"->flags.getFoundation().setEnabled(false);
            case "selector"->flags.setUiSelector(false);
            default->throw new AssertionError(kind);
        }
    }
    static Stream<Arguments> denials() {
        return Stream.of("close","reopen","categories").flatMap(action->Stream.of("business","general","unresolved","missing","collision","generation","user","channel",
            "status-aba","category-aba","revoked","viewer","disabled","blocked","renamed","duplicate-identity","archived","foundation","selector").map(kind->Arguments.of(action,kind)));
    }
    @ParameterizedTest @MethodSource("denials")
    void staleOrUnauthorizedCommandsNeverProduceWorkEvents(String action,String kind) {
        prepare(action);long expected=revision();mutate(kind);var before=snapshot();
        assertThat(service.change(scope,boundActor("Operator"),expected,action.equals("reopen")?"resolved":"pending",action,action.equals("reopen")?List.of():List.of("New"))).isFalse();
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @MethodSource("denials")
    void finalUpdateRepeatsOwnerRightsAndCasAfterTheLockedSnapshot(String action,String kind) {
        prepare(action);long expected=revision();var before=snapshot();var named=spy(new NamedParameterJdbcTemplate(jdbc));var once=new AtomicBoolean();
        doAnswer(call->{String sql=call.getArgument(0);if(sql.startsWith("UPDATE tickets owner SET")&&once.compareAndSet(false,true))mutate(kind);return call.callRealMethod();})
            .when(named).query(anyString(),any(SqlParameterSource.class),any(RowMapper.class));
        ReflectionTestUtils.setField(service,"jdbc",named);
        assertThat(service.change(scope,boundActor("Operator"),expected,action.equals("reopen")?"resolved":"pending",action,action.equals("reopen")?List.of():List.of("New"))).isFalse();
        assertThat(once).isTrue();assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"audit","outbox","trigger"})
    void failureRollsBackRootCategoriesAuditAndOutbox(String site) {
        var before=snapshot();
        if(site.equals("trigger"))jdbc.execute("DROP TRIGGER advance_business_work_revision ON tickets");
        else {
            jdbc.execute("CREATE FUNCTION fail_work() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END; $$");
            jdbc.execute("CREATE TRIGGER fail_work BEFORE INSERT ON "+(site.equals("audit")?"business_ticket_work_events":"ui_event_outbox")+" FOR EACH ROW EXECUTE FUNCTION fail_work()");
        }
        assertThat(command("close")).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"empty","long","too-many","null","control"})
    void invalidCategoriesCannotCloseOrChangeAnything(String kind) {
        List<String> values=switch(kind){case "empty"->List.of(" ");case "long"->List.of("x".repeat(161));case "too-many"->Collections.nCopies(51,"a");case "null"->Arrays.asList((String)null);default->List.of("bad\u0001value");};
        var before=snapshot();assertThat(service.change(scope,boundActor("Operator"),0,"pending","close",values)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"viewer","foreign"," Operator ","ai_agent","missing"})
    void readAndOperateAreSeparateAndPaddedPrincipalsCannotBorrowGrants(String actor) {
        assertThat(service.find(PUBLIC,actor).isPresent()).isEqualTo(actor.equals("viewer"));
        assertThat(service.change(scope,boundActor(actor),0,"pending","close",List.of("New"))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_work_events",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"business","general","user","generation"})
    void categoryProjectionCannotBeInheritedByANewFullOwner(String mutation) {
        assertThat(command("categories")).isTrue();
        var history=jdbc.queryForList("SELECT * FROM business_ticket_work_events");
        mutate(mutation);
        assertThat(jdbc.queryForObject("SELECT business_categories::text FROM tickets WHERE ticket_id=?",String.class,RAW)).isEqualTo("[]");
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_work_events")).isEqualTo(history);
        assertThat(service.recent(scope,"Operator")).isEmpty();
    }
    @Test void allBusinessOperateWorksButViewerGrantCannotWrite() {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(1,'ALL_BUSINESSES','VIEWER')");
        assertThat(command("close")).isFalse();
        jdbc.update("UPDATE business_access_grants SET role_code='MANAGER' WHERE user_id=1");
        assertThat(command("close")).isTrue();
    }
    @Test void staleRevisionAndAFullStatusCycleCannotBeReplayed() {
        assertThat(command("close")).isTrue();
        assertThat(service.change(scope,boundActor("Operator"),1,"resolved","reopen",List.of())).isTrue();
        assertThat(command("close")).isFalse();assertThat(revision()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"membership","all-grant","account","business","root"})
    void committedRevocationOrOwnerChangeIsRecheckedAfterARealLockWait(String site) throws Exception {
        if(site.equals("all-grant")) {
            jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(1,'ALL_BUSINESSES','OPERATOR')");
        }
        var ds=(org.springframework.jdbc.datasource.DriverManagerDataSource)jdbc.getDataSource();
        String tag="r167-lock-"+UUID.randomUUID();
        var observed=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(ds.getUrl()+"&ApplicationName="+tag,ds.getUsername(),ds.getPassword()));
        var work=new BusinessTicketWorkService(observed,flags,new UiEventOutboxAppendService(observed,new LegacyTicketIdJdbcGuard(observed)),new ObjectMapper());
        try(var connection=ds.getConnection()) {
            connection.setAutoCommit(false);
            String sql=switch(site) {
                case "membership"->"UPDATE business_memberships SET is_active=FALSE WHERE user_id=1";
                case "all-grant"->"UPDATE business_access_grants SET is_active=FALSE WHERE user_id=1";
                case "account"->"UPDATE users SET enabled=FALSE WHERE id=1";
                case "business"->"UPDATE businesses SET status='archived' WHERE id=11";
                default->"UPDATE tickets SET business_id=22 WHERE user_id=42";
            };
            try(var statement=connection.createStatement()){assertThat(statement.executeUpdate(sql)).isEqualTo(1);}
            var future=CompletableFuture.supplyAsync(()->work.change(scope,boundActor("Operator"),0,"pending","close",List.of("New")));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')",Boolean.class,tag))) {
                if(System.nanoTime()>deadline)throw new AssertionError("No real PostgreSQL lock wait for "+site);
                Thread.sleep(20);
            }
            connection.commit();
            assertThat(future.get(10,TimeUnit.SECONDS)).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE ticket_id=?",String.class,RAW)).isEqualTo("pending");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_work_events",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isZero();
        }
    }
    @ParameterizedTest @ValueSource(strings={"business_categories","business_work_revision","business_ticket_work_events"})
    void unavailableWorkSchemaFailsClosedWithoutRawFallback(String missing) {
        if(missing.equals("business_ticket_work_events"))jdbc.execute("DROP TABLE "+missing);
        else jdbc.execute("ALTER TABLE tickets DROP COLUMN "+missing);
        var roots=jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id");var legacy=jdbc.queryForList("SELECT * FROM ticket_categories");
        assertThat(command("close")).isFalse();
        assertThat(jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(roots);
        assertThat(jdbc.queryForList("SELECT * FROM ticket_categories")).isEqualTo(legacy);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"null","{}","[1]","[\"\"]","[\" untrimmed\"]","[\"A\",\"A\"]"})
    void databaseRejectsNoncanonicalCategoryStateWithoutAdvancingRevision(String invalid) {
        assertThatThrownBy(()->jdbc.update("UPDATE tickets SET business_categories=CAST(? AS JSONB) WHERE ticket_id=?",invalid,RAW)).isInstanceOf(RuntimeException.class);
        assertThat(revision()).isZero();assertThat(service.find(PUBLIC,"Operator").orElseThrow().categories()).isEmpty();
    }
    @Test void upgradeV80ToV81PreservesOldRootAndChildRowsWithoutImportingCategoriesOrGrants() {
        var old=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_work_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("80").load().migrate();
        long business=old.queryForObject("INSERT INTO businesses(code,display_name) VALUES('r167-upgrade','Same') RETURNING id",Long.class);
        old.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(42,'old-business',?,?,'open')",PUBLIC,business);
        old.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,status,is_general,general_selection_source) VALUES(43,'old-general',?,'closed',TRUE,'MANUAL'),(44,'old-unresolved',?,'pending',FALSE,NULL)","b".repeat(32),"c".repeat(32));
        old.update("INSERT INTO ticket_categories(ticket_id,category) VALUES('old-business','Legacy'),('old-general','General'),('old-unresolved','Unresolved')");
        old.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES('old-business','Old','Prior')");
        var roots=old.queryForList("SELECT * FROM tickets ORDER BY user_id");var children=old.queryForList("SELECT * FROM ticket_categories ORDER BY ticket_id");
        var assigned=old.queryForList("SELECT * FROM ticket_responsibles");
        String oldColumns=roots.get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(","));
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(old.queryForList("SELECT "+oldColumns+" FROM tickets ORDER BY user_id")).isEqualTo(roots);
        assertThat(old.queryForList("SELECT business_work_revision,business_categories::text AS categories FROM tickets"))
            .hasSize(3).allSatisfy(row->assertThat(row).containsEntry("business_work_revision",0L).containsEntry("categories","[]"));
        assertThat(old.queryForList("SELECT * FROM ticket_categories ORDER BY ticket_id")).isEqualTo(children);
        assertThat(old.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(assigned);
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_work_events",Long.class)).isZero();
        var current=old.queryForList("SELECT * FROM tickets ORDER BY user_id");
        assertThat(org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate().migrationsExecuted).isZero();
        assertThat(old.queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(current);
    }
    @Test void concurrentCommandsHaveOneWinnerAndOneAuditOutbox() throws Exception {
        var start=new CountDownLatch(1);
        java.util.function.Supplier<Boolean> run=()->{try{start.await();return command("close");}catch(InterruptedException ex){throw new IllegalStateException(ex);}};
        var a=CompletableFuture.supplyAsync(run);var b=CompletableFuture.supplyAsync(run);start.countDown();
        assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(revision()).isEqualTo(1);assertThat(service.recent(scope,"Operator")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isEqualTo(1);
    }
}
