package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class GeneralTicketReplyChangePostgresTest {
    private static JdbcTemplate jdbc;
    private static final AtomicLong IDS=new AtomicLong(9750000);
    private GeneralTicketReplyChangeService changes;
    private GeneralTicketReplyService replies;
    private GeneralTicketAccessService access;
    private DialogReplyTransportService transport;
    private MultibusinessFeatureFlags flags;
    private GeneralTicketEventScope scope;
    private String actor,sourceKey,key,raw,expected;
    private long accountId,channelId,owner,historyId;
    @BeforeAll static void migrate() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_reply_change");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
    }
    @BeforeEach void setup() {
        owner=IDS.incrementAndGet();actor="change-worker-"+owner;raw="private change "+owner;sourceKey=key();key=key();
        accountId=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,actor);
        channelId=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform,is_active) VALUES(?,'test','telegram',TRUE) RETURNING id",Long.class,"fake-token-"+owner);
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id,problem) VALUES(?,?,?,?,'test')",owner,owner,raw,channelId);
        scope=new GeneralTicketEventScope(owner,raw,key());
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,?,?,TRUE,'GENERAL_CHANNEL','pending')",
            owner,raw,owner,channelId,scope.ticketPublicId());
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        access=new GeneralTicketAccessService(jdbc);access.grantRead(accountId);assertThat(access.grantOperate(accountId)).isTrue();
        transport=mock(DialogReplyTransportService.class);
        when(transport.sendTextGuarded(any(),anyLong(),anyString(),isNull(),any())).thenReturn(DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L));
        when(transport.editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any())).thenAnswer(call->{
            assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        });
        when(transport.deleteTelegramMessageGuarded(any(),anyLong(),anyLong(),any())).thenAnswer(call->{
            assertThat(((BooleanSupplier)call.getArgument(3)).getAsBoolean()).isTrue();return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        });
        replies=new GeneralTicketReplyService(jdbc,flags,transport,ids());changes=service(transport);
        assertThat(replies.submit(scope,actor,0,sourceKey,"Original",replies.destinationFingerprint(scope,actor,0).orElseThrow())).isTrue();
        historyId=jdbc.queryForObject("SELECT chat_history_id FROM general_ticket_text_replies WHERE request_key=?",Long.class,sourceKey);
        expected=changes.targets(scope,actor,0).get(0).fingerprint();clearInvocations(transport);
    }
    @AfterEach void restoreUniqueTestToken() {
        jdbc.update("UPDATE channels SET token=? WHERE id=?","fake-token-"+owner,channelId);
    }
    private UiEventOutboxAppendService ids() {return new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));}
    private GeneralTicketReplyChangeService service(DialogReplyTransportService provider) {return new GeneralTicketReplyChangeService(jdbc,flags,provider,ids());}
    private static String key() {return UUID.randomUUID().toString().replace("-","");}
    private boolean submit(String action,String text) {return changes.submit(scope,actor,0,key,sourceKey,expected,action,text);}
    private String state() {return jdbc.queryForObject("SELECT state FROM general_ticket_reply_changes WHERE request_key=?",String.class,key);}
    private String text() {return jdbc.queryForObject("SELECT message FROM chat_history WHERE id=?",String.class,historyId);}
    @ParameterizedTest @ValueSource(strings={"EDIT","DELETE"})
    void oneConfirmedChangeUpdatesCanonicalHistoryAndMetadataWithoutRewritingOriginal(String action) {
        String message="EDIT".equals(action)?"Исправлено <script> 😀":"";
        var original=jdbc.queryForMap("SELECT * FROM general_ticket_text_replies WHERE request_key=?",sourceKey);
        assertThat(submit(action,message)).isTrue();assertThat(state()).isEqualTo("APPLIED");
        assertThat(submit(action,message)).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM general_ticket_text_replies WHERE request_key=?",sourceKey)).isEqualTo(original);
        assertThat(jdbc.queryForList("SELECT state FROM general_ticket_reply_change_events WHERE request_key=? ORDER BY id",String.class,key))
            .containsExactly("QUEUED","SENDING","APPLIED");
        if ("EDIT".equals(action)) {
            assertThat(text()).isEqualTo(message);verify(transport,times(1)).editTelegramMessageGuarded(any(),eq(owner),eq(77L),eq(message),any());
            assertThat(jdbc.queryForObject("SELECT edited_at IS NOT NULL FROM chat_history WHERE id=?",Boolean.class,historyId)).isTrue();
            assertThat(changes.targets(scope,actor,0)).singleElement().extracting(GeneralTicketReplyChangeService.Target::message).isEqualTo(message);
        } else {
            verify(transport,times(1)).deleteTelegramMessageGuarded(any(),eq(owner),eq(77L),any());
            assertThat(changes.targets(scope,actor,0)).isEmpty();
        }
        var history=new GeneralTicketReadService(jdbc,access).history(accountId,scope.ticketPublicId(),null,50).orElseThrow().entries();
        if ("EDIT".equals(action)) assertThat(history).singleElement().extracting(GeneralTicketReadService.HistoryEntry::message).isEqualTo(message);
        else assertThat(history).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox WHERE ticket_public_id=?",Integer.class,scope.ticketPublicId())).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT ticket_scope_kind,ticket_business_id,message_text,attachment FROM ui_event_outbox WHERE ticket_public_id=?",scope.ticketPublicId()))
            .allSatisfy(row->assertThat(row).containsEntry("ticket_scope_kind","GENERAL").containsEntry("ticket_business_id",null).containsEntry("message_text",null).containsEntry("attachment",null));
        assertThat(changes.recent(scope,actor)).singleElement().extracting(GeneralTicketReplyChangeService.Change::state).isEqualTo("APPLIED");
        assertThat(submit(action,"other")).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","unresolved","business","generation","owner","collision","root","root-channel","channel","token","vk","max","closed","revision","foundation","flag","history-owner","history-generation","history-user","history-ticket","history-channel","client","note","attachment","deleted","provider","duplicate-provider","content","destination-config","token-rotation"})
    void prepareProvesCanonicalReplyCurrentOwnerRightsTextAndDestination(String mutation) {
        mutate(mutation);assertThat(submit("EDIT","Changed")).isFalse();verifyNoInteractions(transport);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_reply_changes WHERE request_key=?",Integer.class,key)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","unresolved","business","generation","owner","collision","root","root-channel","channel","token","vk","max","closed","revision","foundation","flag","history-owner","history-generation","history-user","history-ticket","history-channel","client","note","attachment","deleted","provider","duplicate-provider","content","destination-config","token-rotation"})
    void queuedChangeCannotRecaptureChangedDataOrDeliveryTarget(String mutation) {
        assertThat(changes.prepare(scope,actor,0,key,sourceKey,expected,"DELETE","")).isTrue();
        mutate(mutation);changes.dispatch(key);assertThat(state()).isEqualTo("CANCELLED");verifyNoInteractions(transport);
    }
    private void mutate(String mutation) {
        switch(mutation) {
            case "read"->access.revokeRead(accountId);
            case "operate"->access.revokeOperate(accountId);
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",accountId);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",accountId);
            case "duplicate"->jdbc.update("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE)",actor.toUpperCase());
            case "missing-account"->{
                jdbc.update("DELETE FROM general_ticket_operate_grants WHERE user_id=?",accountId);
                jdbc.update("DELETE FROM general_ticket_read_grants WHERE user_id=?",accountId);
                jdbc.update("DELETE FROM users WHERE id=?",accountId);
            }
            case "unresolved"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",scope.ticketPublicId());
            case "business"-> {
                long business=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'other') RETURNING id",Long.class,"change-"+owner);
                jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL,business_id=? WHERE ticket_public_id=?",business,scope.ticketPublicId());
            }
            case "generation"->{
                // Keep FK and public-ID immutability enabled: replace the root, retain the old child generation.
                jdbc.update("UPDATE chat_history SET ticket_owner_user_id=NULL WHERE id=?",historyId);
                jdbc.update("DELETE FROM tickets WHERE ticket_public_id=?",scope.ticketPublicId());
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,?,?,TRUE,'GENERAL_CHANNEL','pending')",
                    owner,raw,owner,channelId,key());
                jdbc.update("UPDATE chat_history SET ticket_owner_user_id=? WHERE id=?",owner,historyId);
            }
            case "owner"->{
                jdbc.update("UPDATE chat_history SET ticket_owner_user_id=NULL WHERE id=?",historyId);
                jdbc.update("UPDATE tickets SET user_id=user_id+100000 WHERE ticket_public_id=?",scope.ticketPublicId());
                jdbc.update("UPDATE chat_history SET ticket_owner_user_id=? WHERE id=?",owner+100000,historyId);
            }
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(?,?,'pending')",owner+100000,raw);
            case "root"->jdbc.update("DELETE FROM messages WHERE user_id=?",owner);
            case "root-channel"->jdbc.update("UPDATE messages SET channel_id=NULL WHERE user_id=?",owner);
            case "channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channelId);
            case "token"->jdbc.update("UPDATE channels SET token='' WHERE id=?",channelId);
            case "vk","max"->jdbc.update("UPDATE channels SET platform=? WHERE id=?",mutation,channelId);
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_public_id=?",scope.ticketPublicId());
            case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",scope.ticketPublicId());
            case "foundation"->flags.getFoundation().setEnabled(false);
            case "flag"->flags.setGeneralTicketQueue(false);
            case "history-owner"->jdbc.update("UPDATE chat_history SET ticket_owner_user_id=NULL WHERE id=?",historyId);
            case "history-generation"->jdbc.update("UPDATE chat_history SET ticket_public_id=? WHERE id=?",key(),historyId);
            case "history-user"->jdbc.update("UPDATE chat_history SET user_id=user_id+1 WHERE id=?",historyId);
            case "history-ticket"->{
                String foreign="foreign-"+owner;
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(?,?,'pending')",owner,foreign);
                jdbc.update("UPDATE chat_history SET ticket_id=? WHERE id=?",foreign,historyId);
            }
            case "history-channel"->jdbc.update("UPDATE chat_history SET channel_id=NULL WHERE id=?",historyId);
            case "client"->jdbc.update("UPDATE chat_history SET sender='client' WHERE id=?",historyId);
            case "note"->jdbc.update("UPDATE chat_history SET message_type='operator_note' WHERE id=?",historyId);
            case "attachment"->jdbc.update("UPDATE chat_history SET attachment='private/key' WHERE id=?",historyId);
            case "deleted"->jdbc.update("UPDATE chat_history SET deleted_at=clock_timestamp() WHERE id=?",historyId);
            case "provider"->jdbc.update("UPDATE chat_history SET tg_message_id=78 WHERE id=?",historyId);
            case "duplicate-provider"->jdbc.update("INSERT INTO chat_history(user_id,channel_id,tg_message_id) VALUES(?,?,77)",owner,channelId);
            case "content"->jdbc.update("UPDATE chat_history SET message='different' WHERE id=?",historyId);
            case "destination-config"->jdbc.update("UPDATE channels SET delivery_settings='{}' WHERE id=?",channelId);
            case "token-rotation"->jdbc.update("UPDATE channels SET token='rotated' WHERE id=?",channelId);
        }
    }
    @Test void newCommandCanFollowConfirmedEditButStaleContentAndReusedKeyCannotOverwrite() {
        assertThat(submit("EDIT","First")).isTrue();key=key();
        assertThat(submit("EDIT","Second")).isFalse();
        expected=changes.targets(scope,actor,0).get(0).fingerprint();assertThat(submit("EDIT","Second")).isTrue();
        assertThat(text()).isEqualTo("Second");
    }
    @Test void keyAndSnapshotAreBoundToCanonicalActorAndAction() {
        assertThat(changes.prepare(scope,actor,0,key,sourceKey,expected,"EDIT","Changed")).isTrue();
        String other=actor+"-other";long otherId=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,other);
        access.grantRead(otherId);access.grantOperate(otherId);
        assertThat(changes.prepare(scope,other,0,key,sourceKey,expected,"EDIT","Changed")).isFalse();
        assertThat(submit("DELETE","")).isFalse();verifyNoInteractions(transport);
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_reply_changes SET message='replacement',state='SENDING' WHERE request_key=?",key)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM general_ticket_reply_changes WHERE request_key=?",key)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM general_ticket_reply_change_events WHERE request_key=?",key)).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(strings={"EDIT","DELETE"})
    void ambiguousFailureBlocksEveryLaterCommandAndNeverRetries(String action) {
        doReturn(null).when(transport).editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any());
        doThrow(new IllegalStateException("synthetic timeout")).when(transport).deleteTelegramMessageGuarded(any(),anyLong(),anyLong(),any());
        String message="EDIT".equals(action)?"Changed":"";
        assertThat(submit(action,message)).isTrue();assertThat(state()).isEqualTo("UNKNOWN");assertThat(text()).isEqualTo("Original");
        assertThat(submit(action,message)).isTrue();assertThat(changes.targets(scope,actor,0)).isEmpty();
        key=key();assertThat(submit("EDIT","Other")).isFalse();
        if("EDIT".equals(action))verify(transport,times(1)).editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any());
        else verify(transport,times(1)).deleteTelegramMessageGuarded(any(),anyLong(),anyLong(),any());
    }
    @ParameterizedTest @ValueSource(strings={"history","audit","outbox","terminal"})
    void failureAfterProviderSuccessRollsBackCanonicalDataButKeepsReconciliationGate(String stage) {
        String name="r141_fail_"+owner;
        String table=switch(stage){case "history"->"chat_history";case "audit"->"general_ticket_reply_change_events";case "outbox"->"ui_event_outbox";default->"general_ticket_reply_changes";};
        String condition=switch(stage){case "history"->"NEW.id="+historyId;case "outbox"->"NEW.ticket_public_id='"+scope.ticketPublicId()+"'";default->"NEW.request_key='"+key+"' AND NEW.state='APPLIED'";};
        jdbc.execute("CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "+condition+" THEN RAISE EXCEPTION 'synthetic failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER "+name+" BEFORE INSERT OR UPDATE ON "+table+" FOR EACH ROW EXECUTE FUNCTION "+name+"()");
        try {
            assertThat(submit("EDIT","Changed")).isTrue();assertThat(text()).isEqualTo("Original");assertThat(state()).isIn("UNKNOWN","SENDING");
            changes.dispatch(key);key=key();assertThat(submit("DELETE","")).isFalse();
            verify(transport,times(1)).editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any());
        } finally {jdbc.execute("DROP TRIGGER "+name+" ON "+table);jdbc.execute("DROP FUNCTION "+name+"()");}
    }
    @Test void concurrentDuplicateFormsClaimExactlyOneProviderOperation() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return submit("EDIT","Concurrent");});
            var b=pool.submit(()->{start.await();return submit("EDIT","Concurrent");});start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isTrue();assertThat(b.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(state()).isEqualTo("APPLIED");verify(transport,times(1)).editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any());
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentDifferentCommandsForOneSnapshotCannotBothReachProvider() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);String otherKey=key();
        try {
            var a=pool.submit(()->{start.await();return submit("EDIT","Concurrent A");});
            var b=pool.submit(()->{start.await();return changes.submit(scope,actor,0,otherKey,sourceKey,expected,"DELETE","");});
            start.countDown();assertThat(java.util.List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_reply_changes WHERE source_request_key=?",Integer.class,sourceKey)).isEqualTo(1);
            long calls=mockingDetails(transport).getInvocations().stream().filter(call->call.getMethod().getName().endsWith("TelegramMessageGuarded")).count();
            assertThat(calls).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void grantIsLockedAcrossProviderCallAndRevocationBlocksNextIntent() throws Exception {
        var pool=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);final Future<?>[] revocation=new Future<?>[1];
        doAnswer(call->{
            revocation[0]=pool.submit(()->{started.countDown();access.revokeOperate(accountId);});
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->revocation[0].get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        }).when(transport).editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any());
        try {assertThat(submit("EDIT","Locked")).isTrue();revocation[0].get(10,TimeUnit.SECONDS);key=key();assertThat(submit("DELETE","")).isFalse();}
        finally {pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"EDIT","DELETE"})
    void actualTransportRechecksGuardAfterNetworkClientConstruction(String action) throws Exception {
        var network=mock(IntegrationNetworkService.class);var client=mock(HttpClient.class);
        when(network.createChannelHttpClient(any(),any())).thenAnswer(call->{access.revokeOperate(accountId);return client;});
        changes=service(new DialogReplyTransportService(mock(ChannelRepository.class),network,new ObjectMapper()));
        assertThat(submit(action,"EDIT".equals(action)?"Changed":"")).isTrue();assertThat(state()).isEqualTo("CANCELLED");
        verify(client,never()).send(any(),any(HttpResponse.BodyHandler.class));assertThat(text()).isEqualTo("Original");
    }
    @ParameterizedTest @ValueSource(strings={"EDIT","DELETE"})
    void actualTransportUsesFrozenProviderMethodAndDestination(String action) throws Exception {
        var network=mock(IntegrationNetworkService.class);var client=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);
        when(network.createChannelHttpClient(any(),any())).thenReturn(client);
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"ok\":true,\"result\":{\"message_id\":77}}");
        when(client.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        changes=service(new DialogReplyTransportService(mock(ChannelRepository.class),network,new ObjectMapper()));
        assertThat(submit(action,"EDIT".equals(action)?"Changed":"")).isTrue();assertThat(state()).isEqualTo("APPLIED");
        verify(client).send(argThat(request->request.uri().toString().endsWith("EDIT".equals(action)?"/editMessageText":"/deleteMessage")),any(HttpResponse.BodyHandler.class));
    }
    @Test void invalidInputAndNoOpNeverTouchProvider() {
        for(String text:new String[]{""," ","x".repeat(4001),"bad\u0000","\ud800"})assertThat(submit("EDIT",text)).isFalse();
        assertThat(submit("EDIT","Original")).isFalse();assertThat(submit("DELETE","not empty")).isFalse();
        assertThat(submit("OTHER","")).isFalse();verifyNoInteractions(transport);
    }
    @Test void missingSchemaFailsClosed() {
        jdbc.execute("ALTER TABLE general_ticket_reply_changes RENAME TO hidden_general_changes");
        try {assertThat(submit("EDIT","Changed")).isFalse();assertThat(changes.targets(scope,actor,0)).isEmpty();assertThat(changes.recent(scope,actor)).isEmpty();}
        finally {jdbc.execute("ALTER TABLE hidden_general_changes RENAME TO general_ticket_reply_changes");}
    }
    @Test void upgradeFromV70PreservesExistingTicketsHistoryAndGrants() {
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("change_upgrade");
        Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("70").load().migrate();
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,status,is_general,general_selection_source) VALUES(701,'old-general','open',TRUE,'GENERAL_CHANNEL')");
        upgrade.update("INSERT INTO chat_history(user_id,ticket_id,ticket_owner_user_id,sender,message) VALUES(701,'old-general',701,'client','old text')");
        var before=upgrade.queryForList("SELECT * FROM tickets");var history=upgrade.queryForList("SELECT * FROM chat_history");
        Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT * FROM tickets")).isEqualTo(before);assertThat(upgrade.queryForList("SELECT * FROM chat_history")).isEqualTo(history);
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_reply_changes",Integer.class)).isZero();
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_operate_grants",Integer.class)).isZero();
    }
}
