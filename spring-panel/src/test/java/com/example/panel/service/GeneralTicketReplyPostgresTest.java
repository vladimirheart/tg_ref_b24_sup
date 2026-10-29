package com.example.panel.service;

import static com.example.panel.support.GeneralTicketAdminTestSupport.administrator;
import static com.example.panel.support.GeneralTicketActorTestSupport.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class GeneralTicketReplyPostgresTest {
    private static JdbcTemplate jdbc;
    private static final AtomicLong IDS=new AtomicLong(9650000);
    private GeneralTicketReplyService replies;
    private GeneralTicketAccessService access;
    private DialogReplyTransportService transport;
    private MultibusinessFeatureFlags flags;
    private GeneralTicketEventScope scope;
    private String actor,key,raw;
    private long accountId,channelId,owner;
    @BeforeAll static void migrate() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_reply");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
    }
    @BeforeEach void setup() {
        long id=IDS.incrementAndGet();owner=id;actor="reply-worker-"+id;raw="private /reply "+id;key=key();
        accountId=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,actor);
        channelId=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform,is_active) VALUES(?,'test','telegram',TRUE) RETURNING id",Long.class,"fake-token-"+id);
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id,problem) VALUES(?,?,?,?,'test')",id,owner,raw,channelId);
        scope=new GeneralTicketEventScope(owner,raw,key());
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,?,?,TRUE,'GENERAL_CHANNEL','pending')",
            owner,raw,id,channelId,scope.ticketPublicId());
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        access=new GeneralTicketAccessService(jdbc);access.grantRead(administrator(jdbc),accountId);assertThat(access.grantOperate(administrator(jdbc),accountId)).isTrue();
        transport=mock(DialogReplyTransportService.class);
        when(transport.sendTextGuarded(any(),anyLong(),anyString(),isNull(),any())).thenAnswer(call->{
            assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        });
        replies=service(transport);
    }
    @AfterEach void restoreBlankTestToken() {
        jdbc.update("UPDATE channels SET token=? WHERE id=? AND token=''","fake-token-"+owner,channelId);
    }
    private GeneralTicketReplyService service(DialogReplyTransportService provider) {
        return new GeneralTicketReplyService(jdbc,flags,provider,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
    }
    private static String key() { return UUID.randomUUID().toString().replace("-",""); }
    private String fingerprint() { return replies.destinationFingerprint(scope,auth(jdbc,actor),0).orElse("0".repeat(64)); }
    private boolean submit(String text) { return replies.submit(scope,auth(jdbc,actor),0,key,text,fingerprint()); }
    private String state() { return jdbc.queryForObject("SELECT state FROM general_ticket_text_replies WHERE request_key=?",String.class,key); }
    private int history() { return jdbc.queryForObject("SELECT COUNT(*) FROM chat_history WHERE ticket_public_id=?",Integer.class,scope.ticketPublicId()); }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void sendsOneFrozenTextAndPersistsCanonicalGenerationAndMetadata(String platform) {
        jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channelId);
        String text="Ответ <script>не HTML</script> 😀";
        assertThat(submit(text)).isTrue();assertThat(state()).isEqualTo("SENT");
        assertThat(submit(text)).isTrue();
        verify(transport,times(1)).sendTextGuarded(argThat(c->platform.equals(c.getPlatform())),eq(owner),eq(text),isNull(),any());
        assertThat(history()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT sender,message,message_type,ticket_owner_user_id,ticket_public_id FROM chat_history WHERE ticket_public_id=?",scope.ticketPublicId()))
            .containsEntry("sender","operator").containsEntry("message",text).containsEntry("ticket_owner_user_id",owner).containsEntry("ticket_public_id",scope.ticketPublicId());
        assertThat(jdbc.queryForList("SELECT state FROM general_ticket_reply_events WHERE request_key=? ORDER BY id",String.class,key))
            .containsExactly("QUEUED","SENDING","SENT");
        assertThat(jdbc.queryForMap("SELECT ticket_scope_kind,ticket_business_id,message_text,attachment FROM ui_event_outbox WHERE ticket_public_id=?",scope.ticketPublicId()))
            .containsEntry("ticket_scope_kind","GENERAL").containsEntry("ticket_business_id",null).containsEntry("message_text",null).containsEntry("attachment",null);
        assertThat(replies.recent(scope,auth(jdbc,actor))).singleElement().extracting(GeneralTicketReplyService.Reply::message).isEqualTo(text);
        assertThat(submit("different")).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","unresolved","business","generation","owner","collision","root","root-channel","channel","token","platform","null-platform","closed","resolved","revision","flag","principal"})
    void preparationRequiresLiveOwnerAccountBothGrantsAndKnownDestination(String mutation) {
        mutate(mutation);
        assertThat(submit("Ответ")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_text_replies WHERE request_key=?",Integer.class,key)).isZero();
        verifyNoInteractions(transport);
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","unresolved","business","generation","owner","collision","root","root-channel","channel","token","platform","null-platform","closed","resolved","revision","flag","destination-config","token-rotation","platform-rebind","channel-rebind"})
    void queuedReplyNeverRecapturesNewOwnerOrChannelAfterRevocation(String mutation) {
        assertThat(replies.prepare(scope,auth(jdbc,actor),0,key,"Ответ",fingerprint())).isTrue();
        mutate(mutation);replies.dispatch(key);
        assertThat(state()).isEqualTo("CANCELLED");assertThat(history()).isZero();
        verifyNoInteractions(transport);
        assertThat(jdbc.queryForObject("SELECT ticket_public_id FROM general_ticket_text_replies WHERE request_key=?",String.class,key)).isEqualTo(scope.ticketPublicId());
    }
    private void mutate(String mutation) {
        switch(mutation) {
            case "read"->jdbc.update("UPDATE general_ticket_read_grants SET is_active=FALSE WHERE user_id=?",accountId);
            case "operate"->access.revokeOperate(administrator(jdbc),accountId);
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",accountId);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",accountId);
            case "duplicate"->jdbc.update("INSERT INTO users(username,password,enabled) VALUES(?,'unused',FALSE)",actor.toUpperCase(java.util.Locale.ROOT));
            case "missing-account"->jdbc.update("DELETE FROM users WHERE id=?",accountId);
            case "unresolved"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",scope.ticketPublicId());
            case "business"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL,business_id=(SELECT id FROM businesses LIMIT 1) WHERE ticket_public_id=?",scope.ticketPublicId());
            case "generation"->{
                jdbc.update("DELETE FROM tickets WHERE ticket_public_id=?",scope.ticketPublicId());
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,?,?,TRUE,'GENERAL_CHANNEL','pending')",
                    owner,raw,owner,channelId,key());
            }
            case "owner"->jdbc.update("UPDATE tickets SET user_id=user_id+100000 WHERE ticket_public_id=?",scope.ticketPublicId());
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id) VALUES(?,?,?)",owner+100000,raw,key());
            case "root"->jdbc.update("DELETE FROM messages WHERE ticket_id=?",raw);
            case "root-channel"->jdbc.update("UPDATE messages SET channel_id=NULL WHERE ticket_id=?",raw);
            case "channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channelId);
            case "token"->jdbc.update("UPDATE channels SET token='' WHERE id=?",channelId);
            case "platform"->jdbc.update("UPDATE channels SET platform='unknown' WHERE id=?",channelId);
            case "null-platform"->jdbc.update("UPDATE channels SET platform=NULL WHERE id=?",channelId);
            case "closed","resolved"->jdbc.update("UPDATE tickets SET status=? WHERE ticket_public_id=?",mutation,scope.ticketPublicId());
            case "revision"->{jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",scope.ticketPublicId());jdbc.update("UPDATE tickets SET status='pending' WHERE ticket_public_id=?",scope.ticketPublicId());}
            case "flag"->flags.setGeneralTicketQueue(false);
            case "principal"->actor=" "+actor+" ";
            case "destination-config"->jdbc.update("UPDATE channels SET platform_config='{}',delivery_settings='{}' WHERE id=?",channelId);
            case "token-rotation"->jdbc.update("UPDATE channels SET token=? WHERE id=?","rotated-fake-token-"+owner,channelId);
            case "platform-rebind"->jdbc.update("UPDATE channels SET platform='max' WHERE id=?",channelId);
            case "channel-rebind"->{
                long replacement=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform,is_active) VALUES(?,'replacement','telegram',TRUE) RETURNING id",Long.class,"rebound-fake-token-"+owner);
                jdbc.update("UPDATE tickets SET channel_id=? WHERE ticket_public_id=?",replacement,scope.ticketPublicId());
                jdbc.update("UPDATE messages SET channel_id=? WHERE ticket_id=?",replacement,raw);
            }
            default->throw new AssertionError(mutation);
        }
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void actualProviderBoundaryRechecksFlagsAfterHttpClientSetup(String platform) throws Exception {
        jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channelId);
        HttpClient http=mock(HttpClient.class);IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        when(network.createChannelHttpClient(any(),any(Duration.class))).thenAnswer(call->{flags.setGeneralTicketQueue(false);return http;});
        replies=service(new DialogReplyTransportService(mock(ChannelRepository.class),network,new ObjectMapper()));
        assertThat(submit("Ответ")).isTrue();assertThat(state()).isEqualTo("CANCELLED");
        verify(http,never()).send(any(),any(HttpResponse.BodyHandler.class));
        assertThat(history()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"error","throw"})
    void ambiguousProviderOutcomeIsNotAutomaticallyRetried(String failure) {
        if ("throw".equals(failure)) doThrow(new IllegalStateException("synthetic")).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        else doReturn(new DialogReplyTransportService.DialogReplyTransportResult("synthetic timeout",null)).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        assertThat(submit("Ответ")).isTrue();assertThat(state()).isEqualTo("UNKNOWN");assertThat(history()).isZero();
        assertThat(submit("Ответ")).isTrue();verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @Test void databaseFailureAfterProviderAcceptanceDoesNotResend() {
        String constraint="reject_reply_"+owner;
        jdbc.execute("ALTER TABLE chat_history ADD CONSTRAINT "+constraint+" CHECK(ticket_public_id IS DISTINCT FROM '"+scope.ticketPublicId()+"')");
        try {
            assertThat(submit("Ответ")).isTrue();assertThat(state()).isEqualTo("UNKNOWN");assertThat(history()).isZero();
            assertThat(jdbc.queryForList("SELECT state FROM general_ticket_reply_events WHERE request_key=? ORDER BY id",String.class,key))
                .containsExactly("QUEUED","SENDING","UNKNOWN");
            assertThat(submit("Ответ")).isTrue();verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        } finally {jdbc.execute("ALTER TABLE chat_history DROP CONSTRAINT "+constraint);}
    }
    @Test void completeResultPersistenceFailureLeavesDurableSendingWithoutRetry() {
        String constraint="reject_reply_"+owner;
        jdbc.execute("ALTER TABLE ui_event_outbox ADD CONSTRAINT "+constraint+" CHECK(ticket_public_id IS DISTINCT FROM '"+scope.ticketPublicId()+"')");
        try {
            assertThat(submit("Ответ")).isTrue();assertThat(state()).isEqualTo("SENDING");assertThat(history()).isZero();
            assertThat(submit("Ответ")).isTrue();verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        } finally {jdbc.execute("ALTER TABLE ui_event_outbox DROP CONSTRAINT "+constraint);}
    }
    @Test void preparationAuditFailureRollsBackBeforeAnyProviderCall() {
        String constraint="reject_reply_"+owner;
        jdbc.execute("ALTER TABLE general_ticket_reply_events ADD CONSTRAINT "+constraint+" CHECK(request_key<>'"+key+"')");
        try { assertThat(submit("Ответ")).isFalse();verifyNoInteractions(transport);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_text_replies WHERE request_key=?",Integer.class,key)).isZero();
        } finally {jdbc.execute("ALTER TABLE general_ticket_reply_events DROP CONSTRAINT "+constraint);}
    }
    @Test void concurrentDuplicateSubmissionsOnlyDispatchOnce() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        doAnswer(call->{
            entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(91L,1L);
        }).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        try {
            var first=executor.submit(()->submit("Ответ"));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var second=executor.submit(()->submit("Ответ"));assertThat(second.get(5,TimeUnit.SECONDS)).isTrue();
            release.countDown();assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();assertThat(state()).isEqualTo("SENT");
            verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());assertThat(history()).isEqualTo(1);
        } finally {release.countDown();executor.shutdownNow();}
    }
    private GeneralTicketWorkService work() {
        return new GeneralTicketWorkService(jdbc,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)),flags);
    }
    @Test void closingCancelsQueuedReplyAndReopeningDoesNotReviveItsOldRevision() {
        assertThat(replies.prepare(scope,auth(jdbc,actor),0,key,"Old queued reply",fingerprint())).isTrue();
        assertThat(work().transition(scope,auth(jdbc,actor),0,"pending","closed")).isTrue();
        assertThat(replies.destinationFingerprint(scope,auth(jdbc,actor),1)).isEmpty();
        replies.dispatch(key);
        assertThat(state()).isEqualTo("CANCELLED");
        assertThat(work().transition(scope,auth(jdbc,actor),1,"closed","pending")).isTrue();
        replies.dispatch(key);
        verifyNoInteractions(transport);assertThat(history()).isZero();
        assertThat(replies.prepare(scope,auth(jdbc,actor),2,key,"Old queued reply",replies.destinationFingerprint(scope,auth(jdbc,actor),2).orElseThrow())).isFalse();
        assertThat(replies.submit(scope,auth(jdbc,actor),2,key(),"Fresh reviewed reply",replies.destinationFingerprint(scope,auth(jdbc,actor),2).orElseThrow())).isTrue();
        verify(transport,times(1)).sendTextGuarded(any(),eq(owner),eq("Fresh reviewed reply"),isNull(),any());
        assertThat(history()).isEqualTo(1);
    }
    @Test void manualCloseWaitsForAnAlreadyGuardedProviderAttemptAndDoesNotResend() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var closing=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        doAnswer(call->{
            assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            entered.countDown();assertThat(release.await(20,TimeUnit.SECONDS)).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(91L,1L);
        }).when(transport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        try {
            var sent=executor.submit(()->submit("In progress reply"));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var closed=executor.submit(()->{closing.countDown();return work().transition(scope,auth(jdbc,actor),0,"pending","closed");});
            assertThat(closing.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->closed.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();assertThat(sent.get(15,TimeUnit.SECONDS)).isTrue();assertThat(closed.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(state()).isEqualTo("SENT");assertThat(history()).isEqualTo(1);
            assertThat(work().find(scope.ticketPublicId(),auth(jdbc,actor)).orElseThrow().status()).isEqualTo("closed");
            replies.dispatch(key);verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void snapshotsAndTerminalResultsCannotBeRewrittenAndReadsRequireCurrentOwner() {
        assertThat(submit("Ответ")).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_text_replies SET message='forged' WHERE request_key=?",key)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_text_replies SET state='QUEUED',chat_history_id=NULL WHERE request_key=?",key)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_reply_events SET actor_user_id=1 WHERE request_key=?",key)).isInstanceOf(DataAccessException.class);
        assertThat(replies.recent(scope,auth(jdbc,"missing-account"))).isEmpty();
        access.revokeRead(administrator(jdbc),accountId);assertThat(replies.recent(scope,auth(jdbc,actor))).isEmpty();
        access.grantRead(administrator(jdbc),accountId);
        jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",scope.ticketPublicId());
        assertThat(replies.recent(scope,auth(jdbc,actor))).isEmpty();
    }
    @Test void idempotencyKeyCannotBeReusedAcrossActorsOrPublicOwners() {
        assertThat(replies.prepare(scope,auth(jdbc,actor),0,key,"Ответ",fingerprint())).isTrue();
        String secondActor=actor+"-other";
        long second=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,secondActor);
        access.grantRead(administrator(jdbc),second);assertThat(access.grantOperate(administrator(jdbc),second)).isTrue();
        assertThat(replies.submit(scope,auth(jdbc,secondActor),0,key,"Ответ",fingerprint())).isFalse();
        String otherRaw=raw+"-other";var otherScope=new GeneralTicketEventScope(owner,otherRaw,key());
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id) VALUES(?,?,?,?)",owner+100000,owner,otherRaw,channelId);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,?,?,TRUE,'GENERAL_CHANNEL','pending')",
            owner,otherRaw,owner+100000,channelId,otherScope.ticketPublicId());
        assertThat(replies.submit(otherScope,auth(jdbc,actor),0,key,"Ответ",fingerprint())).isFalse();
        verifyNoInteractions(transport);
        assertThat(submit("Ответ")).isTrue();
        verify(transport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    @Test void knownHistoryGenerationCannotLeakIntoAnotherPublicOwner() {
        assertThat(submit("Ответ")).isTrue();
        jdbc.update("UPDATE chat_history SET ticket_public_id=? WHERE ticket_public_id=?",key(),scope.ticketPublicId());
        var reads=new GeneralTicketReadService(jdbc,access);
        assertThat(reads.history(accountId,scope.ticketPublicId(),null,50).orElseThrow().entries()).isEmpty();
    }
    @Test void inputRejectsMalformedUnicodeAndOversizedOrControlText() {
        for (String invalid:new String[]{"","  ","x".repeat(4001),"x\u0000","\ud800","😀\ud800"}) {
            assertThat(GeneralTicketReplyService.validInput(key,invalid)).isFalse();
        }
        assertThat(GeneralTicketReplyService.validInput(key,"😀\nОтвет")).isTrue();
        assertThat(GeneralTicketReplyService.validInput("../bad","Ответ")).isFalse();
    }
    @Test void upgradeFromV65PreservesLegacyHistoryWithoutAssigningGenerationOrGrants() {
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("reply_upgrade");
        Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("65").load().migrate();
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,status,is_general,general_selection_source) VALUES(701,'old-general','open',TRUE,'GENERAL_CHANNEL')");
        upgrade.update("INSERT INTO chat_history(user_id,ticket_id,ticket_owner_user_id,sender,message) VALUES(701,'old-general',701,'client','old text')");
        var before=upgrade.queryForList("SELECT user_id,ticket_id,status,is_general,business_id,ticket_public_id FROM tickets");
        Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT user_id,ticket_id,status,is_general,business_id,ticket_public_id FROM tickets")).isEqualTo(before);
        assertThat(upgrade.queryForMap("SELECT message,ticket_public_id FROM chat_history")).containsEntry("message","old text").containsEntry("ticket_public_id",null);
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_text_replies",Integer.class)).isZero();
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_reply_events",Integer.class)).isZero();
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_operate_grants",Integer.class)).isZero();
    }
}
