package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BusinessTicketClosurePostgresTest {
    private static final String RAW=" Close/Ю?%#+ ",PUBLIC="a".repeat(32);
    private final UiEventTicketScope scope=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    private JdbcTemplate jdbc;
    private BusinessTicketWorkService work;
    private BusinessTicketClosureNotificationService notifications;
    private DialogReplyTransportService transport;
    private ProviderDeliveryLedgerService ledger;
    private Channel channel;
    private MultibusinessFeatureFlags flags;

    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_closure");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT,resolved_at TIMESTAMPTZ,resolved_by TEXT,closed_count INT DEFAULT 0,
                reopen_count INT DEFAULT 0,last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,
                ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            CREATE TABLE chat_history(id BIGSERIAL PRIMARY KEY,user_id BIGINT,sender TEXT,message TEXT,timestamp TIMESTAMPTZ,
                ticket_id TEXT,message_type TEXT,channel_id BIGINT,ticket_owner_user_id BIGINT,ticket_public_id TEXT);
            CREATE TABLE channels(id BIGINT PRIMARY KEY,token TEXT,platform TEXT,is_active BOOLEAN DEFAULT TRUE,
                platform_config TEXT,delivery_settings TEXT,credential_id BIGINT);
            INSERT INTO users(id,username) VALUES(1,'Operator'),(2,'viewer');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR'),(2,11,'VIEWER');
            INSERT INTO channels(id,token,platform) VALUES(9,'test-only-token','telegram');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V97__business_ticket_categories_after_closure.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES(42,?,?,11,9,'pending')",RAW,PUBLIC);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);flags.setStrictReadScope(true);
        transport=mock(DialogReplyTransportService.class);ledger=mock(ProviderDeliveryLedgerService.class);
        channel=new Channel();channel.setId(9L);channel.setToken("test-only-token");channel.setPlatform("telegram");channel.setActive(true);
        when(transport.loadChannel(9L)).thenReturn(java.util.Optional.of(channel));
        when(transport.sendTextGuarded(any(),eq(42L),anyString(),isNull(),any())).thenAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // A different connection sees committed status, history, audit and fanout before provider I/O.
            assertThat(new JdbcTemplate(jdbc.getDataSource()).queryForObject("SELECT status FROM tickets WHERE ticket_id=?",String.class,RAW)).isEqualTo("resolved");
            assertThat(count("chat_history")).isPositive();assertThat(count("business_ticket_work_events")).isPositive();
            assertThat(((java.util.function.BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(123L,1L);
        });
        notifications=new BusinessTicketClosureNotificationService(jdbc,flags,transport,ledger);
        work=new BusinessTicketWorkService(jdbc,flags,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)),new ObjectMapper(),notifications);
    }
    private org.springframework.security.core.Authentication actor(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(name.equals("viewer")?2:1,name,"unused",true,List.of()),null,List.of());
    }
    private DialogResolveResult close() { return work.closeByTicketId(RAW,actor("Operator"),List.of("Консультация")); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    private void assertHistory(long expected) {
        assertThat(count("chat_history")).isEqualTo(expected);
        assertThat(jdbc.queryForList("SELECT * FROM chat_history")).allSatisfy(row->assertThat(row)
            .containsEntry("user_id",42L).containsEntry("ticket_owner_user_id",42L).containsEntry("ticket_id",RAW)
            .containsEntry("ticket_public_id",PUBLIC).containsEntry("channel_id",9L).containsEntry("sender","system")
            .containsEntry("message_type","system_notification").containsEntry("message",BusinessTicketClosureNotificationService.MESSAGE));
    }
    @ParameterizedTest @ValueSource(strings={"telegram","max","vk","webform"})
    void successfulCloseCommitsHistoryThenNotifiesTheCapturedClientOnce(String platform) {
        channel.setPlatform(platform);jdbc.update("UPDATE channels SET platform=? WHERE id=9",platform);
        assertThat(close().updated()).isTrue();assertHistory(1);
        assertThat(close().updated()).isFalse();assertHistory(1);
        assertThat(jdbc.queryForObject("SELECT timestamp=(SELECT resolved_at FROM tickets WHERE ticket_id=?) FROM chat_history",Boolean.class,RAW)).isTrue();
        if(platform.equals("webform"))verify(transport,never()).sendTextGuarded(any(),any(),any(),any(),any());
        else {
            verify(transport,times(1)).sendTextGuarded(eq(channel),eq(42L),eq(BusinessTicketClosureNotificationService.MESSAGE),isNull(),any());
            verify(ledger,times(1)).recordAttempt(eq(channel),eq(RAW),eq(42L),eq("system"),eq("text"),isNull(),any());
        }
    }
    @Test void scopedPageCloseHasTheSameEffectsAndCategoryEditsDoNotSendAgain() {
        assertThat(work.change(scope,actor("Operator"),0,"pending","close",List.of("Категория"))).isTrue();
        assertThat(work.change(scope,actor("Operator"),1,"resolved","categories",List.of("Другая"))).isTrue();
        assertHistory(1);verify(transport,times(1)).sendTextGuarded(any(),any(),any(),any(),any());
        assertThat(work.change(scope,actor("Operator"),2,"resolved","reopen",List.of())).isTrue();
        assertThat(close().updated()).isTrue();assertHistory(2);
        verify(transport,times(2)).sendTextGuarded(any(),any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"history","outbox"})
    void appendFailureRollsBackTheWholeCloseAndCannotSend(String table) {
        jdbc.execute("CREATE FUNCTION reject_append() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_append BEFORE INSERT ON "+(table.equals("history")?"chat_history":"ui_event_outbox")+" FOR EACH ROW EXECUTE FUNCTION reject_append()");
        assertThat(close().updated()).isFalse();assertHistory(0);
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE ticket_id=?",String.class,RAW)).isEqualTo("pending");
        assertThat(count("business_ticket_work_events")).isZero();assertThat(count("ui_event_outbox")).isZero();
        verifyNoInteractions(transport,ledger);
    }
    @ParameterizedTest @ValueSource(strings={"timeout","rejected","audit"})
    void deliveryOrAuditFailureCannotUndoTheCommittedCloseOrLoseTheHistory(String failure) {
        if(failure.equals("timeout"))when(transport.sendTextGuarded(any(),any(),any(),any(),any())).thenThrow(new IllegalStateException("test timeout"));
        if(failure.equals("rejected"))when(transport.sendTextGuarded(any(),any(),any(),any(),any())).thenReturn(new DialogReplyTransportService.DialogReplyTransportResult("test rejected",null));
        if(failure.equals("audit"))doThrow(new IllegalStateException("test audit failure")).when(ledger).recordAttempt(any(),any(),any(),any(),any(),any(),any());
        assertThat(close().updated()).isTrue();assertHistory(1);
        assertThat(close().updated()).isFalse();verify(transport,times(1)).sendTextGuarded(any(),any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"business","user","generation","collision","general","channel","status","revision","archived","inactive","credentials","wrong-channel","foundation","selector"})
    void changedOwnerOrDestinationBeforeDeliveryCancelsTheSend(String mutation) {
        when(transport.loadChannel(9L)).thenAnswer(call->{
            switch(mutation) {
                case "business" -> jdbc.update("UPDATE tickets SET business_id=22");
                case "user" -> jdbc.update("UPDATE tickets SET user_id=99");
                case "generation" -> jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));
                case "collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(99,?,?,11,'pending')",RAW,"b".repeat(32));
                case "general" -> jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE");
                case "channel" -> jdbc.update("UPDATE tickets SET channel_id=19");
                case "status" -> jdbc.update("UPDATE tickets SET status='pending'");
                case "revision" -> jdbc.update("UPDATE tickets SET business_categories='[\"Other\"]'");
                case "archived" -> jdbc.update("UPDATE businesses SET status='archived'");
                case "inactive" -> jdbc.update("UPDATE channels SET is_active=FALSE");
                case "credentials" -> jdbc.update("UPDATE channels SET token='rotated-test-only-token'");
                case "wrong-channel" -> channel.setId(19L);
                case "foundation" -> flags.getFoundation().setEnabled(false);
                case "selector" -> flags.setUiSelector(false);
            }
            return java.util.Optional.of(channel);
        });
        assertThat(close().updated()).isTrue();assertHistory(1);
        verify(transport,never()).sendTextGuarded(any(),any(),any(),any(),any());verifyNoInteractions(ledger);
    }
    @Test void transportGuardRepeatsTheOwnerCheckAtTheSendBoundary() {
        when(transport.sendTextGuarded(any(),any(),any(),any(),any())).thenAnswer(call->{
            jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));
            assertThat(((java.util.function.BooleanSupplier)call.getArgument(4)).getAsBoolean()).isFalse();
            return new DialogReplyTransportService.DialogReplyTransportResult("scope denied",null);
        });
        assertThat(close().updated()).isTrue();assertHistory(1);
    }
    @Test void invalidOrReadOnlyCloseHasNoSideEffects() {
        assertThat(work.closeByTicketId(RAW,actor("viewer"),List.of("Категория")).updated()).isFalse();
        assertThat(work.closeByTicketId(RAW,actor("Operator"),List.of()).updated()).isFalse();
        assertHistory(0);assertThat(count("business_ticket_work_events")).isZero();verifyNoInteractions(transport,ledger);
    }
    @Test void closedNoticeIsReadableFromTheFullyMigratedProductionSchema() {
        var migrated=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_closure_migrated");
        org.flywaydb.core.Flyway.configure().dataSource(migrated.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        migrated.update("INSERT INTO users(id,username,password) OVERRIDING SYSTEM VALUE VALUES(1,'Operator','test-only')");
        migrated.update("INSERT INTO businesses(id,code,display_name) OVERRIDING SYSTEM VALUE VALUES(11,'closure-test','Test')");
        migrated.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR')");
        migrated.update("INSERT INTO channels(id,token,channel_name,platform) OVERRIDING SYSTEM VALUE VALUES(9,'test-only-token','Test','telegram')");
        migrated.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES(42,?,?,11,9,'pending')",RAW,PUBLIC);
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(((java.util.function.BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(123L,1L);
        }).when(transport).sendTextGuarded(any(),any(),any(),any(),any());
        var migratedNotifications=new BusinessTicketClosureNotificationService(migrated,flags,transport,ledger);
        var migratedWork=new BusinessTicketWorkService(migrated,flags,
            new UiEventOutboxAppendService(migrated,new LegacyTicketIdJdbcGuard(migrated)),new ObjectMapper(),migratedNotifications);
        assertThat(migratedWork.closeByTicketId(RAW,actor("Operator"),List.of("Консультация")).updated()).isTrue();
        var read=new DialogConversationReadService(migrated,mock(com.example.panel.storage.AttachmentService.class),
            new com.example.panel.support.PanelTimestampSqlSupport(),new LegacyTicketIdJdbcGuard(migrated));
        assertThat(read.loadHistory(RAW,9L)).singleElement().satisfies(message->{
            assertThat(message.sender()).isEqualTo("system");assertThat(message.message()).isEqualTo(BusinessTicketClosureNotificationService.MESSAGE);
            assertThat(message.messageType()).isEqualTo("system_notification");assertThat(message.timestamp()).isNotBlank();
        });
        verify(transport,times(1)).sendTextGuarded(any(),eq(42L),eq(BusinessTicketClosureNotificationService.MESSAGE),isNull(),any());
    }
}
