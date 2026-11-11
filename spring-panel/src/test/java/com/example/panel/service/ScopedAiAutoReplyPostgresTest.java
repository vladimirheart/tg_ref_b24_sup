package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.AiControlIdentityFixture;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

class ScopedAiAutoReplyPostgresTest {
    private static JdbcTemplate jdbc;
    private static com.zaxxer.hikari.HikariDataSource pool;
    private static final String RESTART_NONCE=UUID.randomUUID().toString().replace("-","");
    private static final AtomicLong IDS=new AtomicLong(9730000);
    private ScopedAiReplyRuleService rules;
    private ScopedAiSourceService sources;
    private ScopedAiAutoReplyService replies;
    private DialogReplyTransportService transport;
    private DialogAiAssistantConfigService config;
    private MultibusinessFeatureFlags flags;
    private UiEventTicketScope scope;
    private Authentication auth;
    private long business,actor,channel,source,input,event;
    private String username,ruleKey,sourceKey;
    private static final String QUESTION="График?",ANSWER="Проверенный ответ без секретов.";
    @BeforeAll static void migrate() {
        var isolated=(org.springframework.jdbc.datasource.DriverManagerDataSource)PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r313_auto_reply").getDataSource();
        pool=new com.zaxxer.hikari.HikariDataSource();pool.setJdbcUrl(isolated.getUrl());pool.setUsername(isolated.getUsername());pool.setPassword(isolated.getPassword());
        pool.setMaximumPoolSize(6);pool.setMinimumIdle(0);pool.setConnectionTimeout(15000);jdbc=new JdbcTemplate(pool);
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        jdbc.execute("CREATE TABLE r314_restart_owner(nonce TEXT NOT NULL)");
        jdbc.update("INSERT INTO r314_restart_owner VALUES(?)",RESTART_NONCE);
    }
    @AfterAll static void closePool(){if(pool!=null)pool.close();}
    @BeforeEach void seed() {
        // Close only previous test QUEUED rows; ambiguous SENDING/UNKNOWN stay intact across the suite.
        for(long pending:jdbc.queryForList("SELECT incoming_event_id FROM ai_scoped_text_replies WHERE state='QUEUED'",Long.class)) {
            jdbc.update("UPDATE ai_scoped_text_replies SET state='SENDING' WHERE incoming_event_id=? AND state='QUEUED'",pending);
            jdbc.update("UPDATE ai_scoped_text_replies SET state='CANCELLED' WHERE incoming_event_id=? AND state='SENDING'",pending);
        }
        long id=IDS.incrementAndGet();username="ai-reviewer-"+id;event=id;ruleKey=UUID.randomUUID().toString();sourceKey="source-"+id;
        business=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'fixture') RETURNING id",Long.class,"ai-auto-"+id);
        actor=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,username);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'ADMIN')",actor,business);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'PAGE_KNOWLEDGE_BASE')",actor);
        auth=AiControlIdentityFixture.auth(actor,username);
        channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform,is_active) VALUES(?,'test','telegram',TRUE) RETURNING id",Long.class,"fake-r313-"+id);
        scope=new UiEventTicketScope(id," exact/Ю ? "+id,UUID.randomUUID().toString().replace("-",""),"BUSINESS",business);
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id,problem) VALUES(?,?,?,?,'fixture')",id,id,scope.ticketId(),channel);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,business_id,is_general,status) VALUES(?,?,?,?,?,?,FALSE,'open')",
            id,scope.ticketId(),id,channel,scope.ticketPublicId(),business);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        rules=new ScopedAiReplyRuleService(jdbc,flags);sources=new ScopedAiSourceService(jdbc,flags);
        assertThat(sources.createKnowledge(business,auth,sourceKey,"График",ANSWER)).isTrue();
        var draft=sources.find(business,auth,ScopedAiSourceService.Kind.KNOWLEDGE,sourceKey).orElseThrow();source=draft.id();
        assertThat(sources.reviseExpected(business,auth,draft.kind(),sourceKey,source,1,"График",ANSWER,ScopedAiSourceService.Status.APPROVED)).isTrue();
        assertThat(rules.create(business,auth,ruleKey,QUESTION,source,2,true)).isTrue();
        input=jdbc.queryForObject("INSERT INTO chat_history(user_id,sender,message,ticket_id,message_type,channel_id,ticket_owner_user_id,ticket_public_id,timestamp)"
            +" VALUES(?,'client',?,?,'text',?,?,?,CURRENT_TIMESTAMP) RETURNING id",Long.class,id,QUESTION,scope.ticketId(),channel,id,scope.ticketPublicId());
        jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,message_text,message_type,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id,incoming_history_id,created_at)"
            +" VALUES(?,'client_message_created',?, ?,?,'text',?,?,'BUSINESS',?,?,CURRENT_TIMESTAMP)",event,scope.ticketId(),channel,QUESTION,id,scope.ticketPublicId(),business,input);
        config=mock(DialogAiAssistantConfigService.class);when(config.isAgentEnabled()).thenReturn(true);
        when(config.resolveAgentMode()).thenReturn("auto_reply");
        when(config.scopedAutoReplyLimits()).thenReturn(new DialogAiAssistantConfigService.ScopedAutoReplyLimits(3,60,0));
        transport=mock(DialogReplyTransportService.class);
        when(transport.sendTextGuarded(any(),anyLong(),anyString(),isNull(),any())).thenAnswer(call->{
            assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        });
        replies=new ScopedAiAutoReplyService(jdbc,flags,config,transport,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
    }
    @Test void sendsOnlyReviewedBodyOnceAndRecordsFrozenOwnerWithoutCopyingInputToAudit() {
        assertThat(replies.process(scope,event)).isTrue();assertThat(replies.process(scope,event)).isTrue();
        verify(transport,times(1)).sendTextGuarded(any(),eq(scope.ticketUserId()),eq(ANSWER),isNull(),any());
        assertThat(state()).isEqualTo("SENT");
        assertThat(jdbc.queryForMap("SELECT sender,message,ticket_owner_user_id,ticket_public_id,channel_id FROM chat_history WHERE sender='ai_agent' AND ticket_public_id=?",scope.ticketPublicId()))
            .containsEntry("message",ANSWER).containsEntry("ticket_owner_user_id",scope.ticketUserId()).containsEntry("ticket_public_id",scope.ticketPublicId()).containsEntry("channel_id",channel);
        assertThat(jdbc.queryForList("SELECT decision_reason FROM ticket_ai_scoped_events WHERE ticket_public_id=? ORDER BY id",String.class,scope.ticketPublicId()))
            .containsExactly("event="+event+";state=QUEUED","event="+event+";state=SENDING","event="+event+";state=SENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox WHERE ticket_public_id=? AND event_type='ai_reply_changed'",Integer.class,scope.ticketPublicId())).isEqualTo(1);
    }
    @Test void queuedRecoveryIsIndependentOfTheUiCursorAndNeverPreparesNewWork() {
        assertThat(replies.prepare(scope,event)).isTrue();
        var callbacks=mock(DialogRealtimeEventService.class);var checkpoint=mock(RuntimeWorkerCheckpointService.class);
        when(checkpoint.readLongCursor(anyString())).thenReturn(Optional.of(Long.MAX_VALUE));
        var coordination=mock(RuntimeCoordinationService.class);
        doAnswer(call->{call.getArgument(2,Runnable.class).run();return null;}).when(coordination).runWithLease(anyString(),any(java.time.Duration.class),any(Runnable.class));
        var watcher=new UiEventOutboxWatcher(jdbc,callbacks,coordination,checkpoint,new LegacyTicketIdJdbcGuard(jdbc),new UiEventTicketAccessService(jdbc,flags),null,replies);
        watcher.watch();assertThat(state()).isEqualTo("QUEUED");verifyNoInteractions(transport);
        watcher.recoverAiReplies();assertThat(state()).isEqualTo("SENT");watcher.recoverAiReplies();
        verify(coordination,times(2)).runWithLease(eq("scoped-ai-reply-recovery"),eq(java.time.Duration.ofSeconds(30)),any(Runnable.class));
        verify(transport,times(1)).sendTextGuarded(any(),anyLong(),eq(ANSWER),isNull(),any());
    }
    @ParameterizedTest @ValueSource(strings={"disabled","grant","source","rule","token-rotation","generation","owner","collision","closed","history-edit","auto-blocked","event-missing","page"})
    void recoveryReprovesTheFrozenScopeAndCancellationReachesTheUi(String change) {
        assertThat(replies.prepare(scope,event)).isTrue();
        if(change.equals("event-missing"))jdbc.update("DELETE FROM ui_event_outbox WHERE id=?",event);
        else if(change.equals("page"))jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='PAGE_KNOWLEDGE_BASE'",actor);
        else mutate(change);
        assertThat(replies.recoverQueued(25)).isEqualTo(1);assertThat(state()).isEqualTo("CANCELLED");verifyNoInteractions(transport);
        var changed=jdbc.queryForMap("SELECT ticket_user_id,ticket_id,ticket_public_id,ticket_business_id,channel_id,message_text FROM ui_event_outbox WHERE event_type='ai_reply_changed' AND ticket_public_id=?",scope.ticketPublicId());
        assertThat(changed).containsEntry("ticket_user_id",scope.ticketUserId()).containsEntry("ticket_id",scope.ticketId())
            .containsEntry("ticket_public_id",scope.ticketPublicId()).containsEntry("ticket_business_id",business).containsEntry("channel_id",channel).containsEntry("message_text","CANCELLED");
    }
    @ParameterizedTest @ValueSource(strings={"foundation","selector","agent","assist_only","escalate_only"})
    void recoveryLeavesQueueUntouchedWhileGloballyDisabled(String setting) {
        assertThat(replies.prepare(scope,event)).isTrue();
        if(setting.endsWith("only"))when(config.resolveAgentMode()).thenReturn(setting);else mutate(setting);
        assertThat(replies.recoverQueued(25)).isZero();assertThat(state()).isEqualTo("QUEUED");verifyNoInteractions(transport);
    }
    @Test void recoveryCannotCreateAReplyFromAnUnqueuedIncomingEvent() {
        assertThat(replies.recoverQueued(25)).isZero();assertThat(state()).isNull();verifyNoInteractions(transport);
    }
    @Test void recoveryUsesABoundedBatchAndDrainsLaterRowsOnTheNextPass() {
        assertThat(replies.prepare(scope,event)).isTrue();queuePeer(100000);queuePeer(200000);
        assertThat(replies.recoverQueued(1)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_text_replies WHERE state='QUEUED'",Integer.class)).isEqualTo(2);
        assertThat(replies.recoverQueued(25)).isEqualTo(2);assertThat(replies.recoverQueued(25)).isZero();
        verify(transport,times(3)).sendTextGuarded(any(),anyLong(),eq(ANSWER),isNull(),any());
    }
    @Test void concurrentRecoveryAndIngressMakeOnlyOneProviderAttempt() throws Exception {
        assertThat(replies.prepare(scope,event)).isTrue();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->{start.await();return replies.recoverQueued(25);});
            var b=executor.submit(()->{start.await();return replies.process(scope,event);});start.countDown();
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);assertThat(state()).isEqualTo("SENT");
            verify(transport,times(1)).sendTextGuarded(any(),anyLong(),eq(ANSWER),isNull(),any());
        } finally {executor.shutdownNow();}
    }
    @Test void unknownResultIsPublishedAndRecoveryNeverRetriesIt() {
        assertThat(replies.prepare(scope,event)).isTrue();
        doThrow(new IllegalStateException("fake ambiguous result")).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        replies.recoverQueued(25);assertThat(state()).isEqualTo("UNKNOWN");assertThat(replies.recoverQueued(25)).isZero();
        assertThat(jdbc.queryForObject("SELECT message_text FROM ui_event_outbox WHERE ticket_public_id=? AND event_type='ai_reply_changed'",String.class,scope.ticketPublicId())).isEqualTo("UNKNOWN");
        verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @ParameterizedTest @ValueSource(strings={"provider","exception"})
    void unknownDiagnosticsExcludePayloadCredentialsAndProviderTextAndNeverRetry(String failure) {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(ScopedAiAutoReplyService.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        String sensitive="secret-token/private-recipient/provider-response/"+ANSWER;
        try {
            if(failure.equals("provider")) doReturn(DialogReplyTransportService.DialogReplyTransportResult.failure(sensitive,sensitive,"critical","transient",403,sensitive,sensitive,sensitive,1L))
                .when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
            else doThrow(new IllegalStateException(sensitive,new IllegalArgumentException(sensitive)))
                .when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
            assertThat(replies.process(scope,event)).isFalse();assertThat(state()).isEqualTo("UNKNOWN");
            assertThat(replies.recoverQueued(25)).isZero();assertThat(replies.process(scope,event)).isFalse();
            verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
            assertThat(appender.list).hasSize(1);
            var diagnostic=appender.list.get(0);
            assertThat(diagnostic.getFormattedMessage()).contains("eventId="+event)
                .contains(failure.equals("provider")?"httpStatus=403":"exceptionType=IllegalStateException, causeType=IllegalArgumentException")
                .doesNotContain(sensitive,ANSWER,QUESTION,username,scope.ticketId(),scope.ticketPublicId());
            assertThat(diagnostic.getThrowableProxy()).isNull();
        } finally {logger.detachAppender(appender);appender.stop();}
    }
    @Test void failedDeliveryEventRefreshesTheCardAndRequestsScopedHandoff() {
        assertThat(replies.prepare(scope,event)).isTrue();
        doThrow(new IllegalStateException("fake ambiguous result")).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        replies.recoverQueued(25);
        long changed=jdbc.queryForObject("SELECT id FROM ui_event_outbox WHERE ticket_public_id=? AND event_type='ai_reply_changed'",Long.class,scope.ticketPublicId());
        var callbacks=mock(DialogRealtimeEventService.class);var checkpoint=mock(RuntimeWorkerCheckpointService.class);
        when(checkpoint.readLongCursor(anyString())).thenReturn(Optional.of(changed-1));
        var coordination=mock(RuntimeCoordinationService.class);
        doAnswer(call->{call.getArgument(2,Runnable.class).run();return null;}).when(coordination).runWithLease(anyString(),any(java.time.Duration.class),any(Runnable.class));
        new UiEventOutboxWatcher(jdbc,callbacks,coordination,checkpoint,new LegacyTicketIdJdbcGuard(jdbc),new UiEventTicketAccessService(jdbc,flags),null,replies).watch();
        verify(callbacks).handleScopedAiReplyChanged(scope,channel);verify(callbacks).handleScopedAiReplyFailed(scope,"UNKNOWN");
        verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @ParameterizedTest @ValueSource(strings={"host","port","database","schema","nonce","point","marker"})
    void restartProbeRejectsRuntimeInputsBeforeConnecting(String unsafe)throws Exception {
        var temp=java.nio.file.Files.createTempDirectory("r314-ai-restart-");
        String[] args={pool.getJdbcUrl(),RESTART_NONCE,Long.toString(event),"QUEUED",temp.resolve("provider.txt").toString()};
        switch(unsafe) {
            case "host" -> args[0]=args[0].replace("127.0.0.1","postgres").replace("localhost","postgres");
            case "port" -> args[0]=args[0].replaceFirst(":([0-9]+)/",":5432/");
            case "database" -> args[0]=args[0].replace("/r290_edit","/runtime").replace("/iguana_test","/runtime");
            case "schema" -> args[0]=args[0].replaceFirst("currentSchema=[a-z0-9_]+","currentSchema=public");
            case "nonce" -> args[1]="not-a-test-nonce";
            case "point" -> args[3]="RETRY";
            case "marker" -> args[4]=java.nio.file.Path.of("runtime-provider.txt").toAbsolutePath().toString();
            default -> throw new IllegalArgumentException(unsafe);
        }
        assertThatIllegalArgumentException().isThrownBy(()->ScopedAiReplyRestartWorker.validate(args));
    }
    @ParameterizedTest @ValueSource(strings={"QUEUED","SENDING"})
    void abruptJvmExitPreservesTheDurableBoundary(String point)throws Exception {
        var temp=java.nio.file.Files.createTempDirectory("r314-ai-restart-");var marker=temp.resolve("provider.txt");
        var executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
        var command=new ProcessBuilder(executable.toString(),"-Xmx128m","-XX:ActiveProcessorCount=2","-cp",
            System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),ScopedAiReplyRestartWorker.class.getName(),
            pool.getJdbcUrl(),RESTART_NONCE,Long.toString(event),point,marker.toString());
        var inherited=new HashMap<>(command.environment());command.environment().clear();
        for(String name:List.of("SystemRoot","WINDIR","TEMP","TMP"))if(inherited.containsKey(name))command.environment().put(name,inherited.get(name));
        var output=temp.resolve("child.log");var child=command.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {assertThat(child.waitFor(30,TimeUnit.SECONDS)).as("owned child finished: %s",output).isTrue();
            assertThat(child.exitValue()).as(java.nio.file.Files.readString(output)).isZero();
        } finally {if(child.isAlive())child.destroyForcibly().waitFor(10,TimeUnit.SECONDS);}
        assertThat(state()).isEqualTo(point);
        var restarted=new ScopedAiAutoReplyService(jdbc,flags,config,transport,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
        if(point.equals("QUEUED")) {
            assertThat(java.nio.file.Files.exists(marker)).isFalse();assertThat(restarted.recoverQueued(25)).isEqualTo(1);assertThat(state()).isEqualTo("SENT");
            verify(transport,times(1)).sendTextGuarded(any(),anyLong(),eq(ANSWER),isNull(),any());
        } else {
            assertThat(java.nio.file.Files.readString(marker)).isEqualTo("provider-boundary");
            assertThat(restarted.recoverQueued(25)).isZero();assertThat(state()).isEqualTo("SENDING");verifyNoInteractions(transport);
        }
    }
    private void queuePeer(long offset) {
        long owner=scope.ticketUserId()+offset;String raw=scope.ticketId()+"-peer-"+offset,publicId=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id,problem) VALUES(?,?,?,?,'fixture')",owner,owner,raw,channel);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,business_id,is_general,status) VALUES(?,?,?,?,?,?,FALSE,'open')",owner,raw,owner,channel,publicId,business);
        long history=jdbc.queryForObject("INSERT INTO chat_history(user_id,sender,message,ticket_id,message_type,channel_id,ticket_owner_user_id,ticket_public_id,timestamp) VALUES(?,'client',?,?,'text',?,?,?,CURRENT_TIMESTAMP) RETURNING id",Long.class,owner,QUESTION,raw,channel,owner,publicId);
        jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,message_text,message_type,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id,incoming_history_id,created_at) VALUES(?,'client_message_created',?,?,?,'text',?,?,'BUSINESS',?,?,CURRENT_TIMESTAMP)",event+offset,raw,channel,QUESTION,owner,publicId,business,history);
        assertThat(replies.prepare(new UiEventTicketScope(owner,raw,publicId,"BUSINESS",business),event+offset)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void knownPlatformsKeepTheCapturedClientDestination(String platform) {
        jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channel);
        assertThat(replies.process(scope,event)).isTrue();
        var captured=org.mockito.ArgumentCaptor.forClass(com.example.panel.entity.Channel.class);
        verify(transport).sendTextGuarded(captured.capture(),eq(scope.ticketUserId()),eq(ANSWER),isNull(),any());
        assertThat(captured.getValue().getPlatform()).isEqualTo(platform);assertThat(captured.getValue().getId()).isEqualTo(channel);
    }
    @Test void watcherDispatchesTypedInputAndSuppressesOnlyTheRedundantAiHandoff() {
        var callbacks=mock(DialogRealtimeEventService.class);var checkpoint=mock(RuntimeWorkerCheckpointService.class);
        when(checkpoint.readLongCursor(anyString())).thenReturn(Optional.of(event-1));
        var coordination=mock(RuntimeCoordinationService.class);
        doAnswer(call->{call.getArgument(2,Runnable.class).run();return null;}).when(coordination).runWithLease(anyString(),any(java.time.Duration.class),any(Runnable.class));
        var watcher=new UiEventOutboxWatcher(jdbc,callbacks,coordination,checkpoint,new LegacyTicketIdJdbcGuard(jdbc),new UiEventTicketAccessService(jdbc,flags),null,replies);
        watcher.watch();assertThat(state()).isEqualTo("SENT");
        verify(callbacks).handleScopedIncomingClientMessage(eq(scope),eq(channel),eq(QUESTION),eq("text"),isNull(),anyBoolean(),eq(true));
        verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","grant","renamed","duplicate-account","business","source","rule","token","platform","channel","generation","owner","collision","closed","root","event-owner","event-generation","event-kind","event-media","history-edit","history-delete","history-generation","history-channel","history-null","later-message","ai-disabled","auto-blocked","foundation","selector","agent"})
    void changedProofBeforePreparationCannotQueueOrCallProvider(String change) {
        mutate(change);assertThat(replies.process(scope,event)).isFalse();assertThat(state()).isNull();verifyNoInteractions(transport);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","grant","renamed","duplicate-account","business","source","rule","token-rotation","channel","generation","owner","collision","closed","root","event-owner","event-generation","event-kind","event-media","history-edit","history-delete","history-generation","history-channel","history-null","later-message","ai-disabled","auto-blocked"})
    void queuedReplyCannotFollowChangedProofOrRecaptureNewDestination(String change) {
        assertThat(replies.prepare(scope,event)).isTrue();mutate(change);replies.dispatch(scope,event);
        assertThat(state()).isEqualTo("CANCELLED");verifyNoInteractions(transport);
        assertThat(jdbc.queryForObject("SELECT ticket_public_id FROM ai_scoped_text_replies WHERE incoming_event_id=?",String.class,event)).isEqualTo(scope.ticketPublicId());
    }
    @Test void lastTransportGuardRechecksAutoReplyBlockAndHandsOff() {
        doAnswer(call->{
            mutate("auto-blocked");assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isFalse();
            return DialogReplyTransportService.DialogReplyTransportResult.failure("denied","scope_denied","warning","terminal",null,null,null,null,null);
        }).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        assertThat(replies.process(scope,event)).isFalse();assertThat(state()).isEqualTo("CANCELLED");
    }
    @Test void unknownProviderResultNeverRetries() {
        doThrow(new IllegalStateException("fake ambiguous provider result")).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        assertThat(replies.process(scope,event)).isFalse();assertThat(replies.process(scope,event)).isFalse();
        assertThat(state()).isEqualTo("UNKNOWN");verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @Test void failureAfterProviderAcceptanceKeepsUnknownReceiptAndRollsBackHistory() {
        jdbc.execute("CREATE FUNCTION r313_fail_sent() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.ticket_public_id='"+scope.ticketPublicId()+"' AND NEW.decision_reason LIKE '%state=SENT' THEN RAISE EXCEPTION 'receipt audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER r313_fail_sent BEFORE INSERT ON ticket_ai_scoped_events FOR EACH ROW EXECUTE FUNCTION r313_fail_sent()");
        try {
            assertThat(replies.process(scope,event)).isFalse();assertThat(state()).isEqualTo("UNKNOWN");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_history WHERE ticket_public_id=? AND sender='ai_agent'",Integer.class,scope.ticketPublicId())).isZero();
            assertThat(replies.process(scope,event)).isFalse();verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        } finally { jdbc.execute("DROP TRIGGER r313_fail_sent ON ticket_ai_scoped_events");jdbc.execute("DROP FUNCTION r313_fail_sent()"); }
    }
    @Test void concurrentDuplicateEventsMakeOnlyOneProviderAttempt() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return replies.process(scope,event);});
            var b=pool.submit(()->{start.await();return replies.process(scope,event);});start.countDown();
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);assertThat(state()).isEqualTo("SENT");
            verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        } finally {pool.shutdownNow();}
    }
    @Test void approvedSourceAloneAndUnreviewedRuleDoNotAuthorizeReply() {
        assertThat(rules.revoke(business,auth,ruleKey,1)).isTrue();
        assertThat(rules.create(business,auth,UUID.randomUUID().toString(),QUESTION,source,2,false)).isFalse();
        assertThat(replies.process(scope,event)).isFalse();verifyNoInteractions(transport);
    }
    @Test void triggerMatchesCaseAndLiteralTextWithOnlyOuterSpacesIgnored() {
        jdbc.update("UPDATE ui_event_outbox SET message_text=? WHERE id=?","  "+QUESTION+"  ",event);
        jdbc.update("UPDATE chat_history SET message=? WHERE id=?","  "+QUESTION+"  ",input);
        assertThat(replies.process(scope,event)).isTrue();
    }
    @Test void ruleDoesNotApplyToEventsPredatingReview() {
        jdbc.update("UPDATE ui_event_outbox SET created_at=created_at-INTERVAL '1 day' WHERE id=?",event);
        assertThat(replies.process(scope,event)).isFalse();verifyNoInteractions(transport);
    }
    @Test void ruleAuthorityUsesBoundNumericAndLiteralAccountAndRejectsGlobalAdminAlone() {
        for(var unbound:List.of(AiControlIdentityFixture.auth(actor+100000,username),AiControlIdentityFixture.auth(actor," "+username+" ")))
            assertThat(rules.create(business,unbound,UUID.randomUUID().toString(),"other",source,2,true)).isFalse();
        jdbc.update("DELETE FROM business_memberships WHERE user_id=?",actor);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'ROLE_ADMIN')",actor);
        assertThat(rules.create(business,auth,UUID.randomUUID().toString(),"other",source,2,true)).isFalse();
        assertThat(rules.revoke(business,auth,ruleKey,1)).isFalse();
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','ADMIN')",actor);
        assertThat(rules.revoke(business,auth,ruleKey,1)).isTrue();
    }
    @Test void uniqueRuleAndRevisionCasCannotSilentlyReplaceConsent() {
        assertThat(rules.create(business,auth,UUID.randomUUID().toString(),QUESTION,source,2,true)).isFalse();
        assertThat(rules.revoke(business,auth,ruleKey,2)).isFalse();
        assertThat(rules.revoke(business,auth,ruleKey,1)).isTrue();assertThat(rules.revoke(business,auth,ruleKey,1)).isFalse();
        assertThatThrownBy(()->jdbc.update("UPDATE ai_scoped_reply_rules SET active=TRUE,revision=revision+1 WHERE rule_key=?",ruleKey)).hasMessageContaining("immutable");
    }
    @Test void typedPublisherBindsNewHistoryAndRepeatedQuestionsUseDifferentChildren() {
        var publisher=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        var context=new UiEventOutboxAppendService.TicketContext(scope.ticketId(),scope.ticketUserId(),channel,scope.ticketPublicId(),business,false);
        jdbc.update("DELETE FROM ui_event_outbox WHERE id=?",event);
        assertThat(publisher.publishClientMessage(context,input)).isTrue();
        long first=jdbc.queryForObject("SELECT id FROM ui_event_outbox WHERE incoming_history_id=?",Long.class,input);
        assertThat(replies.process(scope,first)).isTrue();
        long next=jdbc.queryForObject("INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,message_type,channel_id,ticket_owner_user_id,ticket_public_id)"
            +" VALUES(?,'client',?,CURRENT_TIMESTAMP,?,'text',?,?,?) RETURNING id",Long.class,scope.ticketUserId(),QUESTION,scope.ticketId(),channel,scope.ticketUserId(),scope.ticketPublicId());
        assertThat(publisher.publishClientMessage(context,next)).isTrue();
        long second=jdbc.queryForObject("SELECT id FROM ui_event_outbox WHERE incoming_history_id=?",Long.class,next);
        assertThat(replies.process(scope,second)).isTrue();
        verify(transport,times(2)).sendTextGuarded(any(),anyLong(),eq(ANSWER),isNull(),any());
    }
    @Test void duplicateEventForSameHistoryCannotSendAgain() {
        assertThat(replies.process(scope,event)).isTrue();
        jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,message_text,message_type,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id,incoming_history_id,created_at)"
            +" SELECT ?,event_type,ticket_id,channel_id,message_text,message_type,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id,incoming_history_id,created_at FROM ui_event_outbox WHERE id=?",event+100000,event);
        assertThat(replies.process(scope,event+100000)).isFalse();verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @ParameterizedTest @ValueSource(strings={"history-null","history-generation","history-channel","event-media","collision"})
    void typedPublisherCannotAttributeUnknownOrChangedHistory(String mutation) {
        if(mutation.equals("event-media"))jdbc.update("UPDATE chat_history SET attachment='private' WHERE id=?",input);else mutate(mutation);
        var publisher=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        assertThat(publisher.publishClientMessage(new UiEventOutboxAppendService.TicketContext(scope.ticketId(),scope.ticketUserId(),channel,scope.ticketPublicId(),business,false),input)).isFalse();
    }
    @Test void queuedReplyDoesNotReviveAfterCloseAndReopen() {
        assertThat(replies.prepare(scope,event)).isTrue();
        jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_public_id=?",scope.ticketPublicId());
        jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",scope.ticketPublicId());
        replies.dispatch(scope,event);assertThat(state()).isEqualTo("CANCELLED");verifyNoInteractions(transport);
    }
    @Test void queuedReplyDoesNotSurvivePageRevocation() {
        assertThat(replies.prepare(scope,event)).isTrue();
        jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='PAGE_KNOWLEDGE_BASE'",actor);
        replies.dispatch(scope,event);assertThat(state()).isEqualTo("CANCELLED");verifyNoInteractions(transport);
    }
    @Test void queuedReplyDoesNotSurviveControlRewriteEvenWithNoActiveBlocks() {
        assertThat(replies.prepare(scope,event)).isTrue();
        jdbc.update("INSERT INTO ticket_ai_scoped_control(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,ai_disabled,auto_reply_blocked,updated_by_user_id,updated_by) VALUES(?,?,?,'BUSINESS',?,FALSE,FALSE,?,'fixture')",scope.ticketUserId(),scope.ticketId(),scope.ticketPublicId(),business,actor);
        replies.dispatch(scope,event);assertThat(state()).isEqualTo("CANCELLED");verifyNoInteractions(transport);
    }
    @ParameterizedTest @ValueSource(strings={"assist_only","escalate_only"})
    void globalManualModeCannotSendEvenWithApprovedRule(String mode) {
        when(config.resolveAgentMode()).thenReturn(mode);
        assertThat(replies.process(scope,event)).isFalse();verifyNoInteractions(transport);
    }
    @ParameterizedTest @ValueSource(strings={"count","cooldown"})
    void replyLimitsAreScopedToTheFrozenBusinessOwner(String limit) {
        assertThat(replies.process(scope,event)).isTrue();
        when(config.scopedAutoReplyLimits()).thenReturn(new DialogAiAssistantConfigService.ScopedAutoReplyLimits(limit.equals("count")?1:3,60,limit.equals("cooldown")?60:0));
        long next=jdbc.queryForObject("INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,message_type,channel_id,ticket_owner_user_id,ticket_public_id) VALUES(?,'client',?,CURRENT_TIMESTAMP,?,'text',?,?,?) RETURNING id",Long.class,scope.ticketUserId(),QUESTION,scope.ticketId(),channel,scope.ticketUserId(),scope.ticketPublicId());
        var publisher=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        assertThat(publisher.publishClientMessage(new UiEventOutboxAppendService.TicketContext(scope.ticketId(),scope.ticketUserId(),channel,scope.ticketPublicId(),business,false),next)).isTrue();
        long nextEvent=jdbc.queryForObject("SELECT id FROM ui_event_outbox WHERE incoming_history_id=?",Long.class,next);
        assertThat(replies.process(scope,nextEvent)).isFalse();verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @Test void ruleAuditFailureRollsBackConsent() {
        jdbc.execute("CREATE FUNCTION r313_fail_rule() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'rule audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER r313_fail_rule BEFORE INSERT ON ai_scoped_reply_rule_events FOR EACH ROW EXECUTE FUNCTION r313_fail_rule()");
        String key=UUID.randomUUID().toString();
        try {
            assertThat(rules.create(business,auth,key,"Another question",source,2,true)).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_reply_rules WHERE rule_key=?",Integer.class,key)).isZero();
        } finally {jdbc.execute("DROP TRIGGER r313_fail_rule ON ai_scoped_reply_rule_events");jdbc.execute("DROP FUNCTION r313_fail_rule()");}
    }
    @Test void upgradeFromV89DoesNotActivateRulesOrReattributeExistingMessages() {
        var old=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r313_upgrade");
        var flyway=Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql");
        flyway.target("89").load().migrate();
        old.update("INSERT INTO chat_history(user_id,sender,message,ticket_id) VALUES(123,'client','old test text','old')");
        old.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,created_at) VALUES(1,'client_message_created','old',CURRENT_TIMESTAMP)");
        var before=old.queryForList("SELECT * FROM chat_history");
        Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(old.queryForList("SELECT * FROM chat_history")).isEqualTo(before);
        assertThat(old.queryForObject("SELECT incoming_history_id FROM ui_event_outbox WHERE id=1",Long.class)).isNull();
        assertThat(old.queryForList("SELECT * FROM ai_scoped_reply_rules")).isEmpty();
        assertThat(old.queryForList("SELECT * FROM ai_scoped_text_replies")).isEmpty();
        assertThat(old.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname='idx_ai_text_reply_queued'",String.class))
            .contains("incoming_event_id").contains("WHERE (state = 'QUEUED'::text)");
    }
    private String state() {return jdbc.queryForList("SELECT state FROM ai_scoped_text_replies WHERE incoming_event_id=?",String.class,event).stream().findFirst().orElse(null);}
    private void mutate(String change) {
        switch(change) {
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actor);
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actor);
            case "grant" -> jdbc.update("DELETE FROM business_memberships WHERE user_id=?",actor);
            case "renamed" -> jdbc.update("UPDATE users SET username='renamed-'||id::TEXT WHERE id=?",actor);
            case "duplicate-account" -> jdbc.update("INSERT INTO users(username,password,enabled) VALUES(?,'unused',FALSE)",username.toUpperCase(Locale.ROOT));
            case "business" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",business);
            case "source" -> jdbc.update("UPDATE ai_scoped_sources SET body='Changed',revision=revision+1 WHERE id=?",source);
            case "rule" -> assertThat(rules.revoke(business,auth,ruleKey,1)).isTrue();
            case "token" -> jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channel);
            case "token-rotation" -> jdbc.update("UPDATE channels SET token=? WHERE id=?","other-fake-token-"+scope.ticketPublicId(),channel);
            case "platform" -> jdbc.update("UPDATE channels SET platform='unknown' WHERE id=?",channel);
            case "channel" -> jdbc.update("UPDATE tickets SET channel_id=NULL WHERE ticket_public_id=?",scope.ticketPublicId());
            case "generation" -> {
                jdbc.update("UPDATE chat_history SET ticket_owner_user_id=NULL WHERE id=?",input);
                jdbc.update("DELETE FROM tickets WHERE user_id=? AND ticket_id=?",scope.ticketUserId(),scope.ticketId());
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,business_id,is_general,status) VALUES(?,?,?,?,?,?,FALSE,'open')",
                    scope.ticketUserId(),scope.ticketId(),scope.ticketUserId(),channel,UUID.randomUUID().toString().replace("-",""),business);
            }
            case "owner" -> {
                jdbc.update("UPDATE chat_history SET ticket_owner_user_id=NULL WHERE id=?",input);
                jdbc.update("UPDATE tickets SET user_id=user_id+100000 WHERE ticket_public_id=?",scope.ticketPublicId());
            }
            case "collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id) VALUES(?,?,?)",scope.ticketUserId()+100000,scope.ticketId(),UUID.randomUUID().toString().replace("-",""));
            case "closed" -> jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_public_id=?",scope.ticketPublicId());
            case "root" -> jdbc.update("DELETE FROM messages WHERE ticket_id=?",scope.ticketId());
            case "event-owner","event-generation" -> {
                jdbc.update("DELETE FROM ui_event_outbox WHERE id=?",event);
                jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,message_text,message_type,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id,incoming_history_id,created_at) VALUES(?,'client_message_created',?,?,?,'text',?,?,'BUSINESS',?,?,CURRENT_TIMESTAMP)",event,scope.ticketId(),channel,QUESTION,change.equals("event-owner")?scope.ticketUserId()+1:scope.ticketUserId(),change.equals("event-generation")?"b".repeat(32):scope.ticketPublicId(),business,input);
            }
            case "event-kind" -> jdbc.update("UPDATE ui_event_outbox SET event_type='client_message_edited' WHERE id=?",event);
            case "event-media" -> jdbc.update("UPDATE ui_event_outbox SET attachment='private-key' WHERE id=?",event);
            case "history-edit" -> jdbc.update("UPDATE chat_history SET message='changed' WHERE id=?",input);
            case "history-delete" -> jdbc.update("UPDATE chat_history SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",input);
            case "history-generation" -> jdbc.update("UPDATE chat_history SET ticket_public_id=? WHERE id=?","b".repeat(32),input);
            case "history-channel" -> jdbc.update("UPDATE chat_history SET channel_id=NULL WHERE id=?",input);
            case "history-null" -> jdbc.update("UPDATE chat_history SET ticket_public_id=NULL WHERE id=?",input);
            case "history-duplicate" -> jdbc.update("INSERT INTO chat_history(user_id,sender,message,ticket_id,message_type,channel_id,ticket_owner_user_id,ticket_public_id) SELECT user_id,sender,message,ticket_id,message_type,channel_id,ticket_owner_user_id,ticket_public_id FROM chat_history WHERE id=?",input);
            case "later-message" -> jdbc.update("INSERT INTO chat_history(user_id,sender,message,ticket_id,message_type,channel_id,ticket_owner_user_id,ticket_public_id) SELECT user_id,'operator','Operator intervened',ticket_id,'operator_message',channel_id,ticket_owner_user_id,ticket_public_id FROM chat_history WHERE id=?",input);
            case "ai-disabled","auto-blocked" -> jdbc.update("INSERT INTO ticket_ai_scoped_control(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,ai_disabled,auto_reply_blocked,updated_by_user_id,updated_by) VALUES(?,?,?,'BUSINESS',?,?,?,1,'fixture')",scope.ticketUserId(),scope.ticketId(),scope.ticketPublicId(),business,change.equals("ai-disabled"),change.equals("auto-blocked"));
            case "foundation" -> flags.getFoundation().setEnabled(false);
            case "selector" -> flags.setUiSelector(false);
            case "agent" -> when(config.isAgentEnabled()).thenReturn(false);
            default -> throw new IllegalArgumentException(change);
        }
    }
}
