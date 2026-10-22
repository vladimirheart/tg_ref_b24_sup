package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.repository.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Real PostgreSQL writer; provider, participant and transport collaborators cannot contact the working stack. */
class LegacyAssignmentBoundaryPostgresTest {
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private DialogResponsibilityService service;
    private LegacyTicketIdJdbcGuard guard;
    private final String raw=" Raw/ ?&%#+ Ю ";
    @BeforeEach void fixture() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r160_legacy_assignment");flags=new MultibusinessFeatureFlags();
        jdbc.execute("""
            CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id TEXT NOT NULL,ticket_public_id TEXT DEFAULT md5(random()::text),
              business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,channel_id BIGINT,status TEXT DEFAULT 'open',
              responsibility_revision BIGINT NOT NULL DEFAULT 0,resolved_at TIMESTAMPTZ,resolved_by TEXT,reopen_count INT DEFAULT 0,
              last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ DEFAULT now(),last_read_at TIMESTAMPTZ);
            CREATE TABLE chat_history(user_id BIGINT,sender TEXT,message TEXT,timestamp TIMESTAMPTZ,ticket_id TEXT,message_type TEXT,channel_id BIGINT,ticket_owner_user_id BIGINT);
            CREATE TABLE dialog_action_audit(ticket_id TEXT,actor TEXT,action TEXT,result TEXT,detail TEXT,created_at TIMESTAMPTZ);
            """);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id) VALUES(901,?,77)",raw);
        guard=new LegacyTicketIdJdbcGuard(jdbc);service=new DialogResponsibilityService(jdbc,guard,flags);
    }
    private void scope(String kind) {
        switch(kind) {
            case "business"->jdbc.update("UPDATE tickets SET business_id=7 WHERE ticket_id=?",raw);
            case "general"->jdbc.update("UPDATE tickets SET is_general=TRUE WHERE ticket_id=?",raw);
            case "strict"->flags.setStrictReadScope(true);
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(902,?)",raw);
            case "closed"->jdbc.update("UPDATE tickets SET status='resolved' WHERE ticket_id=?",raw);
            case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=NULL WHERE ticket_id=?",raw);
            case "schema"->jdbc.execute("ALTER TABLE tickets DROP COLUMN business_id");
        }
    }
    static Stream<Arguments> denied() {return Stream.of("business","general","strict","collision","generation","schema")
        .flatMap(kind->Stream.of(false,true).map(existing->Arguments.of(kind,existing)));}
    @ParameterizedTest @MethodSource("denied")
    void directAndImplicitEntryPointsCannotAssignOrLeakExistingScopedResponsible(String kind,boolean existing) {
        if(existing)jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES(?,'old','lead')",raw);
        var before=jdbc.queryForList("SELECT * FROM ticket_responsibles");scope(kind);
        assertThat(service.assignResponsibleIfMissing(raw,"new","actor")).isNull();
        assertThat(service.assignResponsibleIfMissingOrRedirected(raw,"new","actor")).isFalse();
        service.withLegacyRedirect(raw,"new","actor",result->{assertThat(result.allowed()).isFalse();assertThat(result.changed()).isFalse();assertThat(result.responsible()).isNull();return null;});
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void closedLegacyRootCanOnlyReturnItsExistingOwnerWithoutWriting(boolean existing) {
        if(existing)jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES(?,'old','lead')",raw);
        scope("closed");var before=jdbc.queryForList("SELECT * FROM ticket_responsibles");
        assertThat(service.assignResponsibleIfMissing(raw,"new","actor")).isEqualTo(existing?"old":null);
        assertThat(service.assignResponsibleIfMissingOrRedirected(raw,"new","actor")).isFalse();
        service.withLegacyRedirect(raw,"new","actor",result->{assertThat(result.allowed()).isFalse();assertThat(result.changed()).isFalse();assertThat(result.responsible()).isNull();return null;});
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"business","general","strict"})
    void capabilityIsNotAuthorityWhenScopeChangesBeforeTheCommand(String kind) {
        assertThat(service.canAssignLegacyResponsible(raw)).isTrue();scope(kind);
        assertThat(service.assignResponsibleIfMissingOrRedirected(raw,"new","actor")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"business","general","generation","channel","revision","strict"})
    void finalSqlRejectsChangesAfterTheLockedSnapshotAndRollsBackTheAttempt(String mutation) {
        var intercepted=spy(jdbc);var once=new AtomicBoolean();
        doAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("INSERT INTO ticket_responsibles")&&once.compareAndSet(false,true)) {
                switch(mutation) {
                    case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=md5(random()::text) WHERE ticket_id=?",raw);
                    case "channel"->jdbc.update("UPDATE tickets SET channel_id=78 WHERE ticket_id=?",raw);
                    case "revision"->jdbc.update("UPDATE tickets SET responsibility_revision=1 WHERE ticket_id=?",raw);
                    default->scope(mutation);
                }
            }
            return call.callRealMethod();
        }).when(intercepted).update(anyString(),any(Object[].class));
        var subject=new DialogResponsibilityService(intercepted,guard,flags);
        assertThat(subject.assignResponsibleIfMissingOrRedirected(raw,"new","actor")).isFalse();assertThat(once).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isZero();
    }
    @Test void acceptedCallbackAndAssignmentRollbackTogetherOnFailure() {
        assertThatThrownBy(()->service.withLegacyRedirect(raw,"new","actor",result->{assertThat(result.allowed()&&result.changed()).isTrue();
            jdbc.update("INSERT INTO dialog_action_audit(ticket_id,action,result) VALUES(?,'take','success')",raw);throw new IllegalStateException("callback failure");})).hasMessage("callback failure");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit",Long.class)).isZero();
    }
    @Test void rootRemainsLockedUntilTheAcceptedWorkflowCallbackCompletes() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var writer=pool.submit(()->service.withLegacyRedirect(raw,"new","actor",result->{assertThat(result.allowed()&&result.changed()).isTrue();entered.countDown();
                try{assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException ex){throw new IllegalStateException(ex);}return true;}));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var competitor=pool.submit(()->{try(var connection=jdbc.getDataSource().getConnection();var statement=connection.createStatement()) {
                statement.execute("SET lock_timeout='300ms'");statement.executeUpdate("UPDATE tickets SET business_id=7 WHERE user_id=901");return false;
            }catch(java.sql.SQLException ex){return "55P03".equals(ex.getSQLState());}});
            assertThat(competitor.get(10,TimeUnit.SECONDS)).isTrue();release.countDown();assertThat(writer.get(10,TimeUnit.SECONDS)).isTrue();
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void scopedCallbackWaitsForTheSharedAdvisoryLockBeforeTouchingResponsibility() throws Exception {
        jdbc.execute("""
            CREATE TABLE users(id BIGINT,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN);
            CREATE TABLE businesses(id BIGINT,status TEXT);
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN);
            """);
        var original=(DriverManagerDataSource)jdbc.getDataSource();
        var contenderDataSource=new DriverManagerDataSource(original.getUrl(),original.getUsername(),original.getPassword());
        String marker="r160_assignment_lock_"+UUID.randomUUID();var properties=new Properties();properties.setProperty("ApplicationName",marker);
        contenderDataSource.setConnectionProperties(properties);var contenderJdbc=new JdbcTemplate(contenderDataSource);
        var contender=new DialogResponsibilityService(contenderJdbc,new LegacyTicketIdJdbcGuard(contenderJdbc),flags);
        var scope=new UiEventTicketScope(901L,raw,jdbc.queryForObject("SELECT ticket_public_id FROM tickets",String.class),"UNRESOLVED",null);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var legacy=pool.submit(()->service.withLegacyRedirect(raw,"legacy","actor",result->{
                assertThat(result.allowed()&&result.changed()).isTrue();entered.countDown();
                try{assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException ex){throw new IllegalStateException(ex);}return true;}));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var scoped=pool.submit(()->contender.assignScopedResponsibleIfMissing(scope,"scoped","actor"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean advisoryWait=false;
            while(System.nanoTime()<deadline) {
                if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE application_name=? AND wait_event='advisory'",Long.class,marker)==1L){advisoryWait=true;break;}
                Thread.sleep(20);
            }
            assertThat(advisoryWait).as("scoped writer must wait before acquiring a child/root lock").isTrue();
            release.countDown();assertThat(legacy.get(10,TimeUnit.SECONDS)).isTrue();assertThat(scoped.get(10,TimeUnit.SECONDS)).isEqualTo("legacy");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isEqualTo(1L);
        }finally{release.countDown();pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"business","general","strict"})
    void implicitReplyAndBotOwnershipNeverBypassTheSameWriter(String kind) {
        scope(kind);
        var target=mock(DialogReplyTargetService.class);var transport=mock(DialogReplyTransportService.class);var ledger=mock(ProviderDeliveryLedgerService.class);
        var channel=new Channel();channel.setId(77L);channel.setToken("test-only-no-provider");channel.setPlatform("telegram");
        var recipient=new DialogReplyTarget(901L,77L);when(target.loadReplyTarget(raw)).thenReturn(Optional.of(recipient));
        when(target.hasReplyTargetOwner(raw,recipient)).thenReturn(true);when(target.hasOperatorMessageOwner(raw,recipient,33L)).thenReturn(true);
        when(target.markOperatorMessageEdited(raw,33L,"changed",recipient)).thenReturn(1);when(target.markOperatorMessageDeleted(raw,33L,recipient)).thenReturn(1);
        when(transport.loadChannel(77L)).thenReturn(Optional.of(channel));
        when(transport.sendText(channel,901L,"hello",null)).thenReturn(DialogReplyTransportService.DialogReplyTransportResult.success(33L,1L));
        when(transport.editTelegramMessage(channel,901L,33L,"changed")).thenReturn(DialogReplyTransportService.DialogReplyTransportResult.success(33L,1L));
        when(transport.deleteTelegramMessage(channel,901L,33L)).thenReturn(DialogReplyTransportService.DialogReplyTransportResult.success(33L,1L));
        var replies=new DialogReplyService(target,transport,service,ledger);
        assertThat(replies.sendReply(raw,"hello",null,"actor").success()).isTrue();
        assertThat(replies.editOperatorMessage(raw,33L,"changed","actor").success()).isTrue();
        assertThat(replies.deleteOperatorMessage(raw,33L,"actor").success()).isTrue();
        var participants=mock(DialogParticipantService.class);var bots=new BotRuntimeTicketWriteService(jdbc,target,service,participants,
            mock(UiEventOutboxAppendService.class),ledger,mock(PendingFeedbackRequestRepository.class),mock(FeedbackRepository.class),guard);
        assertThat(bots.recordOperatorRelay(raw,"hello",34L,null,"actor").updated()).isTrue();
        jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",raw);assertThat(bots.reopenTicket(raw,"actor").updated()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isZero();verifyNoInteractions(participants);
    }
    @ParameterizedTest @ValueSource(strings={"business","general","strict"})
    void rawAssignmentAuditCannotBeAddedToAScopedOrStrictRoot(String kind) {
        var audit=new DialogAuditService(jdbc,guard,flags);scope(kind);
        audit.logDialogActionAudit(raw,"actor","take","success","claimed");audit.logDialogActionAudit(raw,"actor","reassign","error","denied");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit",Long.class)).isZero();
    }
}
