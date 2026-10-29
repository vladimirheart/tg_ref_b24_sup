package com.example.panel.service;

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

class GeneralManualTicketCreationPostgresTest {
    private static final String KEY="a".repeat(32);
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private GeneralTicketAccessService access;
    private GeneralManualTicketCreationService service;
    @BeforeEach void setup() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_manual");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE general_ticket_read_grants(user_id BIGINT PRIMARY KEY,is_active BOOLEAN,updated_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,group_msg_id BIGINT,ticket_public_id TEXT UNIQUE,business_id BIGINT,status TEXT,channel_id BIGINT,"
            +"created_at TIMESTAMPTZ,resolved_at TIMESTAMPTZ,resolved_by TEXT,closed_count INT DEFAULT 0,reopen_count INT DEFAULT 0,work_time_total_sec BIGINT,last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE messages(group_msg_id BIGINT PRIMARY KEY,user_id BIGINT,ticket_id TEXT UNIQUE,client_name TEXT,problem TEXT,created_at TIMESTAMPTZ,created_date TEXT,created_time TEXT,channel_id BIGINT)");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,user_id BIGINT,sender TEXT,message TEXT,timestamp TIMESTAMPTZ,ticket_id TEXT,message_type TEXT,ticket_owner_user_id BIGINT,ticket_public_id TEXT,channel_id BIGINT,deleted_at TIMESTAMPTZ,attachment TEXT)");
        jdbc.execute("CREATE TABLE bot_users(user_id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,message_text TEXT,message_type TEXT,attachment TEXT,rating INT,created_at TIMESTAMPTZ)");
        for(String file:new String[]{"V54__manual_business_clients.sql","V56__explicit_general_ticket_scope.sql","V61__ui_event_outbox_ticket_scope.sql",
                "V62__scoped_ai_records.sql","V65__general_ticket_operate.sql","V67__general_ticket_manual_close.sql","V68__general_ticket_work_intervals.sql",
                "V69__general_ticket_assignments.sql","V70__manual_general_tickets.sql"})jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",file)));
        jdbc.update("INSERT INTO users VALUES(1,'worker',TRUE,FALSE),(2,'reader',TRUE,FALSE),(3,'other',TRUE,FALSE),(4,'admin',TRUE,FALSE)");
        jdbc.update("INSERT INTO general_ticket_read_grants VALUES(1,TRUE,NULL),(2,TRUE,NULL),(3,TRUE,NULL)");
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);flags.setManualTicketCreation(true);
        access=new GeneralTicketAccessService(jdbc);access.grantOperate(1);access.grantOperate(3);
        service=new GeneralManualTicketCreationService(jdbc,flags,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
    }
    private java.util.Optional<String> create(){return service.create("worker",KEY,"  Анна  ","  Помощь\nСрочно  ");}
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private void emptyWrites(){for(String table:new String[]{"manual_general_clients","general_manual_ticket_requests","tickets","messages","chat_history","ui_event_outbox"})assertThat(count(table)).as(table).isZero();}
    @Test void createsOneLocalGeneralRootNoteAndFrozenOutboxWithoutBusinessOrChannel() {
        assertThat(access.canOperate(1L)).isTrue();assertThat(access.canOperate(2L)).isFalse();assertThat(access.canOperate(4L)).isFalse();
        String id=create().orElseThrow();assertThat(id).matches("[0-9a-f]{32}");
        var row=jdbc.queryForMap("SELECT * FROM tickets");long owner=((Number)row.get("user_id")).longValue();
        assertThat(owner).isNegative();assertThat(row).containsEntry("business_id",null).containsEntry("channel_id",null)
            .containsEntry("is_general",true).containsEntry("general_selection_source","MANUAL").containsEntry("status","pending")
            .containsEntry("general_work_revision",0L).containsEntry("ticket_public_id",id).containsEntry("group_msg_id",owner);
        assertThat(jdbc.queryForMap("SELECT client_name,problem,channel_id FROM messages"))
            .containsEntry("client_name","Анна").containsEntry("problem","Помощь\nСрочно").containsEntry("channel_id",null);
        assertThat(jdbc.queryForMap("SELECT sender,message_type,ticket_owner_user_id,ticket_public_id,channel_id FROM chat_history"))
            .containsEntry("sender","operator").containsEntry("message_type","operator_note").containsEntry("ticket_owner_user_id",owner)
            .containsEntry("ticket_public_id",id).containsEntry("channel_id",null);
        assertThat(jdbc.queryForMap("SELECT event_type,ticket_scope_kind,ticket_business_id,channel_id,message_text FROM ui_event_outbox"))
            .containsEntry("event_type","ticket_created").containsEntry("ticket_scope_kind","GENERAL").containsEntry("ticket_business_id",null)
            .containsEntry("channel_id",null).containsEntry("message_text",null);
        assertThat(count("manual_business_clients")).isZero();assertThat(count("bot_users")).isZero();assertThat(count("general_ticket_work_intervals")).isZero();
        var read=new GeneralTicketReadService(jdbc,access);
        assertThat(read.find(2,id).orElseThrow().clientName()).isEqualTo("Анна");
        assertThat(read.history(2,id,null,20).orElseThrow().entries()).singleElement().satisfies(note->assertThat(note.message()).isEqualTo("Помощь\nСрочно"));
        assertThat(read.find(4,id)).isEmpty();
        var scope=new GeneralTicketEventScope(owner,(String)row.get("ticket_id"),id);
        var work=new GeneralTicketWorkService(jdbc,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)),flags);
        assertThat(work.transition(scope,"worker",0,"pending","open")).isTrue();assertThat(count("general_ticket_work_intervals")).isEqualTo(1);
    }
    @Test void duplicateKeyReturnsOnlyTheSameActorsSameInputAndCurrentOwner() {
        String id=create().orElseThrow();long sequence=jdbc.queryForObject("SELECT last_value FROM manual_client_user_id_seq",Long.class);
        assertThat(create()).contains(id);assertThat(service.create("WORKER",KEY,"Анна","Помощь\nСрочно")).contains(id);
        assertThat(service.create("other",KEY,"Анна","Помощь\nСрочно")).isEmpty();
        assertThat(service.create("worker",KEY,"Другое имя","Помощь\nСрочно")).isEmpty();
        assertThat(service.create("worker",KEY,"Анна","Другая проблема")).isEmpty();
        assertThat(count("tickets")).isEqualTo(1);assertThat(count("manual_general_clients")).isEqualTo(1);assertThat(count("ui_event_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_value FROM manual_client_user_id_seq",Long.class)).isEqualTo(sequence);
        assertThatThrownBy(()->jdbc.update("UPDATE general_manual_ticket_requests SET actor='changed'" )).isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE tickets SET status='closed'");assertThat(create()).contains(id);
        access.revokeRead(1);assertThat(create()).isEmpty();assertThat(count("tickets")).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing","principal","foundation","queue","manual"})
    void currentActorAndAllFlagsAreRequiredBeforeAllocation(String mutation) {
        String identity="worker";
        switch(mutation) {
            case "read"->access.revokeRead(1);
            case "operate"->access.revokeOperate(1);
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "duplicate"->jdbc.update("INSERT INTO users VALUES(5,'WORKER',FALSE,FALSE)");
            case "missing"->jdbc.update("DELETE FROM users WHERE id=1");
            case "principal"->identity=" worker ";
            case "foundation"->flags.getFoundation().setEnabled(false);
            case "queue"->flags.setGeneralTicketQueue(false);
            case "manual"->flags.setManualTicketCreation(false);
        }
        assertThat(service.canCreate(identity)).isFalse();assertThat(service.create(identity,KEY,"Клиент","Проблема")).isEmpty();emptyWrites();
        assertThat(jdbc.queryForObject("SELECT is_called FROM manual_client_user_id_seq",Boolean.class)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"key","name-empty","name-long","name-control","problem-empty","problem-long","problem-control","surrogate"})
    void malformedInputCannotAllocateOrWrite(String input) {
        String key=KEY,name="Клиент",problem="Проблема";
        switch(input) {
            case "key"->key="bad";case "name-empty"->name=" ";case "name-long"->name="я".repeat(201);
            case "name-control"->name="a\nb";case "problem-empty"->problem=" ";case "problem-long"->problem="я".repeat(10001);
            case "problem-control"->problem="a\u0000b";case "surrogate"->problem="\ud800";
        }
        assertThat(GeneralManualTicketCreationService.validInput(key,name,problem)).isFalse();
        assertThat(service.create("worker",key,name,problem)).isEmpty();emptyWrites();
        assertThat(jdbc.queryForObject("SELECT is_called FROM manual_client_user_id_seq",Boolean.class)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"client","message","root","history","request","outbox"})
    void everyDownstreamFailureRollsBackTheEntireCreation(String failure) {
        String table=switch(failure){case "client"->"manual_general_clients";case "message"->"messages";case "root"->"tickets";
            case "history"->"chat_history";case "request"->"general_manual_ticket_requests";default->"ui_event_outbox";};
        jdbc.execute("CREATE FUNCTION reject_manual_create() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic rejection'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_manual_create BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION reject_manual_create()");
        assertThat(create()).isEmpty();emptyWrites();
    }
    @ParameterizedTest @ValueSource(strings={"tuple","generation","business","source","missing-root","missing-message","collision"})
    void aSavedKeyCannotRecreateOrRecaptureAChangedRoot(String mutation) {
        String id=create().orElseThrow();String raw=jdbc.queryForObject("SELECT ticket_id FROM tickets",String.class);
        switch(mutation) {
            case "tuple"->jdbc.update("UPDATE tickets SET user_id=99");
            case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));
            case "business"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL,business_id=1");
            case "source"->jdbc.update("UPDATE tickets SET general_selection_source='GENERAL_CHANNEL'");
            case "missing-root"->jdbc.update("DELETE FROM tickets");
            case "missing-message"->jdbc.update("DELETE FROM messages");
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,general_selection_source,status) VALUES(99,?,?,FALSE,NULL,'pending')",raw,"b".repeat(32));
        }
        var before=jdbc.queryForList("SELECT * FROM tickets");
        assertThat(create()).isEmpty();assertThat(jdbc.queryForList("SELECT * FROM tickets")).isEqualTo(before);
        assertThat(count("manual_general_clients")).isEqualTo(1);assertThat(count("general_manual_ticket_requests")).isEqualTo(1);
        assertThat(count("ui_event_outbox")).isEqualTo(1);
    }
    @Test void concurrentDuplicateFormsCreateExactlyOneLocalClientAndTicket() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CyclicBarrier(2);
        try {
            Callable<String> submit=()->{ready.await(5,TimeUnit.SECONDS);return create().orElseThrow();};
            var first=pool.submit(submit);var second=pool.submit(submit);
            assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
            for(String table:new String[]{"tickets","messages","manual_general_clients","chat_history","general_manual_ticket_requests","ui_event_outbox"})assertThat(count(table)).as(table).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","account"})
    void waitingActorLocksReproveCommittedRevocation(String mutation) throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try(var connection=jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            String sql=switch(mutation){case "read"->"UPDATE general_ticket_read_grants SET is_active=FALSE WHERE user_id=1";
                case "operate"->"UPDATE general_ticket_operate_grants SET is_active=FALSE WHERE user_id=1";default->"UPDATE users SET enabled=FALSE WHERE id=1";};
            try(var update=connection.prepareStatement(sql)){update.executeUpdate();}
            var started=new CountDownLatch(1);var pending=pool.submit(()->{started.countDown();return create();});
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->pending.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            connection.commit();assertThat(pending.get(10,TimeUnit.SECONDS)).isEmpty();emptyWrites();
        } finally {pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"table","function"})
    void missingV70FailsClosedWithoutChangingExistingData(String missing) {
        if(missing.equals("table"))jdbc.execute("DROP TABLE general_manual_ticket_requests");
        else jdbc.execute("DROP FUNCTION general_ticket_selection_source_allowed(TEXT) CASCADE");
        assertThat(create()).isEmpty();assertThat(count("tickets")).isZero();assertThat(count("messages")).isZero();assertThat(count("manual_general_clients")).isZero();
    }
    @Test void unspecifiedJdbcStringsUseTheSameTypedFingerprintAndIdempotency() {
        var original=(org.springframework.jdbc.datasource.DriverManagerDataSource)jdbc.getDataSource();
        var alternate=new org.springframework.jdbc.datasource.DriverManagerDataSource(original.getUrl(),original.getUsername(),original.getPassword());
        var options=new java.util.Properties();options.setProperty("stringtype","unspecified");alternate.setConnectionProperties(options);
        var untyped=new JdbcTemplate(alternate);
        var other=new GeneralManualTicketCreationService(untyped,flags,new UiEventOutboxAppendService(untyped,new LegacyTicketIdJdbcGuard(untyped)));
        String id=other.create("worker",KEY,"Анна","Проблема").orElseThrow();
        assertThat(service.create("worker",KEY,"Анна","Проблема")).contains(id);
        assertThat(count("tickets")).isEqualTo(1);assertThat(count("manual_general_clients")).isEqualTo(1);
    }
    @Test void occupiedNegativeIdsAreSkippedAndAllocationFailureCannotLeaveRows() {
        long first=jdbc.queryForObject("SELECT last_value FROM manual_client_user_id_seq",Long.class);
        for(int i=0;i<32;i++)jdbc.update("INSERT INTO bot_users VALUES(?)",first+i);
        assertThat(create()).isEmpty();emptyWrites();
        String id=service.create("worker","b".repeat(32),"Новый","Новая проблема").orElseThrow();
        assertThat(jdbc.queryForObject("SELECT user_id FROM tickets WHERE ticket_public_id=?",Long.class,id)).isEqualTo(first+32);
    }
    @Test void upgradeFromV69PreservesExistingSourcesAndBothManualNamespacesStayDistinct() {
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_manual_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("69").load().migrate();
        upgrade.update("INSERT INTO users(username,password,enabled) VALUES('operator','unused',TRUE)");
        long actor=upgrade.queryForObject("SELECT id FROM users WHERE username='operator'",Long.class);
        long business=upgrade.queryForObject("SELECT id FROM businesses WHERE code='sushi'",Long.class);
        upgrade.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR')",actor,business);
        var businessService=new ManualBusinessTicketCreationService(upgrade,new BusinessAccessService(upgrade));
        var first=businessService.create(actor,business,"Бизнес-клиент","Бизнес-вопрос",null);
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,status,is_general,general_selection_source) VALUES(99,'prior-general','pending',TRUE,'GENERAL_CHANNEL')");
        var before=upgrade.queryForList("SELECT * FROM tickets ORDER BY user_id");
        // Compare every pre-upgrade column; verify every added column separately below.
        String oldColumns=before.get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"")
            .collect(java.util.stream.Collectors.joining(","));
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT "+oldColumns+" FROM tickets ORDER BY user_id")).isEqualTo(before);
        var expectedColumns=new java.util.HashSet<>(before.get(0).keySet());
        expectedColumns.addAll(java.util.List.of("business_selection_source","questionnaire_catalog_sha256","questionnaire_version","responsibility_revision","business_work_revision","business_categories"));
        assertThat(upgrade.queryForList("SELECT * FROM tickets")).allSatisfy(row->
            assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(expectedColumns));
        assertThat(upgrade.queryForList("SELECT business_selection_source,questionnaire_catalog_sha256,questionnaire_version,"
            +"responsibility_revision,business_work_revision,business_categories::text AS categories FROM tickets"))
            .allSatisfy(row->assertThat(row).containsEntry("business_selection_source",null).containsEntry("questionnaire_catalog_sha256",null)
                .containsEntry("questionnaire_version",null).containsEntry("responsibility_revision",0L)
                .containsEntry("business_work_revision",0L).containsEntry("categories","[]"));
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM manual_general_clients",Integer.class)).isZero();
        var grants=new GeneralTicketAccessService(upgrade);grants.grantRead(actor);grants.grantOperate(actor);
        var general=new GeneralManualTicketCreationService(upgrade,flags,new UiEventOutboxAppendService(upgrade,new LegacyTicketIdJdbcGuard(upgrade)));
        String id=general.create("operator",KEY,"Общий клиент","Общий вопрос").orElseThrow();
        long generalId=upgrade.queryForObject("SELECT user_id FROM tickets WHERE ticket_public_id=?",Long.class,id);
        var last=businessService.create(actor,business,"Ещё бизнес-клиент","Вопрос",null);
        assertThat(java.util.Set.of(first.clientUserId(),generalId,last.clientUserId())).hasSize(3);
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM manual_business_clients",Integer.class)).isEqualTo(2);
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM manual_general_clients",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->upgrade.update("UPDATE tickets SET general_selection_source='UNKNOWN' WHERE ticket_public_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->upgrade.update("UPDATE tickets SET business_id=? WHERE ticket_public_id=?",business,id)).isInstanceOf(DataAccessException.class);
    }
}
