package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.entity.Channel;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.example.panel.repository.ChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperatorNotificationWatcherTest {

    private JdbcTemplate jdbcTemplate;
    private NotificationService notificationService;
    private DialogAiAssistantService dialogAiAssistantService;
    private AlertQueueService alertQueueService;
    private ChannelRepository channelRepository;
    private DialogAuditService dialogAuditService;
    private SharedConfigService sharedConfigService;
    private RuntimeCoordinationService runtimeCoordinationService;
    private RuntimeWorkerCheckpointService checkpointService;
    private OperatorNotificationWatcher watcher;

    @BeforeEach
    void setUp() {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("operator_notification_watcher");
        createSchema();

        notificationService = mock(NotificationService.class);
        when(notificationService.notifyDialogUsers(anyString(), org.mockito.ArgumentMatchers.anySet(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(notificationService.buildDialogUrl(anyString())).thenAnswer(invocation ->
                "/dialogs/" + URLEncoder.encode(invocation.getArgument(0, String.class), StandardCharsets.UTF_8));
        dialogAiAssistantService = mock(DialogAiAssistantService.class);
        alertQueueService = mock(AlertQueueService.class);
        channelRepository = mock(ChannelRepository.class);
        dialogAuditService = mock(DialogAuditService.class);
        sharedConfigService = mock(SharedConfigService.class);
        runtimeCoordinationService = passthroughCoordinationService();
        checkpointService = new RuntimeWorkerCheckpointService(jdbcTemplate);

        watcher = new OperatorNotificationWatcher(
                jdbcTemplate,
                notificationService,
                dialogAiAssistantService,
                alertQueueService,
                channelRepository,
                dialogAuditService,
                sharedConfigService,
                runtimeCoordinationService,
                checkpointService,
                null,
                new LegacyTicketIdJdbcGuard(jdbcTemplate)
        );
        watcher.initialize();
    }

    @Test
    void watchUsesAlertQueueForIncomingClientMessageWithoutFallbackWhenHandled() {
        Channel channel = channel(7L, "Support Queue");
        when(channelRepository.findById(7L)).thenReturn(Optional.of(channel));
        when(alertQueueService.notifyIncomingClientMessage(channel, "T-101", "Клиент прислал лог")).thenReturn(true);

        insertTicket("T-101", 101L, 7L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "T-101", "user", "Клиент прислал лог", "text", null, 7L, OffsetDateTime.now(ZoneOffset.UTC).toString());

        watcher.watch();

        verify(alertQueueService, never()).notifyIncomingClientMessage(channel, "T-101", "Клиент прислал лог");
        verify(notificationService, never()).notifyAllOperators(eq("Новое сообщение в обращении T-101: Клиент прислал лог"), eq("/dialogs/T-101"), isNull());
        verify(dialogAiAssistantService).processIncomingClientMessage("T-101", "Клиент прислал лог", "text", null);
    }

    @Test
    void watchFallsBackToAllOperatorsWhenIncomingClientMessageIsNotHandledByAlertQueue() {
        Channel channel = channel(8L, "Fallback Queue");
        when(channelRepository.findById(8L)).thenReturn(Optional.of(channel));
        when(alertQueueService.notifyIncomingClientMessage(channel, "T-102", "Есть уточнение")).thenReturn(false);

        insertTicket("T-102", 102L, 8L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "T-102", "user", "Есть уточнение", "text", null, 8L, OffsetDateTime.now(ZoneOffset.UTC).toString());

        watcher.watch();

        verify(alertQueueService, never()).notifyIncomingClientMessage(channel, "T-102", "Есть уточнение");
        verify(notificationService, never()).notifyAllOperators(
                eq("Новое сообщение в обращении T-102: Есть уточнение"),
                eq("/dialogs/T-102"),
                isNull()
        );
        verify(dialogAiAssistantService).processIncomingClientMessage("T-102", "Есть уточнение", "text", null);
    }

    @Test
    void watchIgnoresHistoricalClientMessagesOutsideLiveReplayWindow() {
        Channel channel = channel(9L, "History Replay Guard");
        when(channelRepository.findById(9L)).thenReturn(Optional.of(channel));

        insertTicket("T-103", 103L, 9L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "T-103", "user", "Старое историческое сообщение", "text", null, 9L, OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toString());

        watcher.watch();

        verify(alertQueueService, never()).notifyIncomingClientMessage(channel, "T-103", "Старое историческое сообщение");
        verify(notificationService, never()).notifyAllOperators(
                eq("Новое сообщение в обращении T-103: Старое историческое сообщение"),
                eq("/dialogs/T-103"),
                isNull()
        );
        verify(dialogAiAssistantService, never()).processIncomingClientMessage("T-103", "Старое историческое сообщение", "text", null);
    }

    @Test
    void watchFallsBackToOperatorAudienceWhenFirstResponseOverdueQueueRoutingDeclines() {
        Channel channel = channel(12L, "Escalation Queue");
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("sla_target_minutes", 1)
        ));
        when(dialogAuditService.hasSuccessfulDialogAction("T-OVERDUE-1", "first_response_overdue_notification")).thenReturn(false);
        when(channelRepository.findById(12L)).thenReturn(Optional.of(channel));
        when(alertQueueService.notifyFirstResponseOverdue(eq(channel), eq("T-OVERDUE-1"), org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);
        when(notificationService.findAllOperatorRecipients()).thenReturn(Set.of("alice", "bob"));

        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, channel_id, status, created_at, user_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                "T-OVERDUE-1", 12L, "open", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).toString(), 1L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "T-OVERDUE-1", "user", "Жду ответа", "text", 12L, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).toString());

        watcher.watch();

        verify(alertQueueService).notifyFirstResponseOverdue(eq(channel), eq("T-OVERDUE-1"), org.mockito.ArgumentMatchers.anyLong());
        verify(notificationService).findAllOperatorRecipients();
        verify(notificationService).notifyDialogUsers(
                eq("T-OVERDUE-1"),
                eq(Set.of("alice", "bob")),
                eq("Первая реакция просрочена (Escalation Queue) в обращении T-OVERDUE-1. Просрочка: 120 мин."),
                eq("/dialogs/T-OVERDUE-1")
        );
        verify(dialogAuditService).logDialogActionAudit(
                eq("T-OVERDUE-1"),
                eq("notification_watcher"),
                eq("first_response_overdue_notification"),
                eq("success"),
                org.mockito.ArgumentMatchers.contains("route=fallback_all_operators")
        );
    }

    @Test
    void watchSkipsAmbiguousLegacyTicketIdBeforeAiAndNotifications() {
        insertTicket("T-DUPLICATE", 201L, 21L);
        insertTicket("T-DUPLICATE", 202L, 22L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "T-DUPLICATE", "user", "client message", "text", null, 21L, OffsetDateTime.now(ZoneOffset.UTC).toString());

        watcher.watch();

        verifyNoInteractions(
                notificationService,
                dialogAiAssistantService,
                alertQueueService,
                channelRepository,
                dialogAuditService
        );
    }

    @Test
    void incomingMessageKeepsPaddedTicketIdentityInsteadOfPlainSibling() {
        String padded = " T-EXACT ";
        Channel channel = channel(17L, "Exact Queue");
        when(channelRepository.findById(17L)).thenReturn(Optional.of(channel));
        insertTicket("T-EXACT", 301L, 17L);
        insertTicket(padded, 302L, 17L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, padded, "user", "padded message", "text", null, 17L,
                OffsetDateTime.now(ZoneOffset.UTC).toString());

        watcher.watch();

        verify(dialogAiAssistantService).processIncomingClientMessage(padded, "padded message", "text", null);
        verify(dialogAiAssistantService, never()).processIncomingClientMessage(
                "T-EXACT", "padded message", "text", null);
    }

    @Test
    void firstResponseOverdueKeepsPaddedTicketIdentityThroughQueueAuditAndUrl() {
        String padded = " null ";
        Channel channel = channel(18L, "Exact Queue");
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("sla_target_minutes", 1)));
        when(channelRepository.findById(18L)).thenReturn(Optional.of(channel));
        when(notificationService.findAllOperatorRecipients()).thenReturn(Set.of("alice"));
        insertTicket("null", 401L, 18L);
        insertTicket(padded, 402L, 18L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                1L, padded, "user", "waiting", "text", 18L,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).toString());

        watcher.watch();

        verify(alertQueueService).notifyFirstResponseOverdue(
                eq(channel), eq(padded), org.mockito.ArgumentMatchers.anyLong());
        verify(alertQueueService, never()).notifyFirstResponseOverdue(
                eq(channel), eq("null"), org.mockito.ArgumentMatchers.anyLong());
        verify(notificationService).buildDialogUrl(padded);
        verify(notificationService).notifyDialogUsers(
                eq(padded),
                eq(Set.of("alice")),
                org.mockito.ArgumentMatchers.contains("в обращении  null ."),
                eq("/dialogs/+null+")
        );
        verify(dialogAuditService).logDialogActionAudit(
                eq(padded), eq("notification_watcher"),
                eq("first_response_overdue_notification"), eq("success"), anyString());
    }

    private void insertTicket(String ticketId, long userId, long channelId) {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, channel_id, status, created_at, user_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                ticketId, channelId, "open", OffsetDateTime.now(ZoneOffset.UTC).toString(), userId
        );
    }

    @Test
    void generalHistoryAndOverdueRowsAreSkippedWhileLaterLegacyEventAdvancesCursor() {
        insertTicket("T-GENERAL", 701L, 7L);
        insertTicket("T-LATER", 702L, 7L);
        jdbcTemplate.update("UPDATE tickets SET is_general = TRUE WHERE ticket_id = 'T-GENERAL'");
        jdbcTemplate.update("UPDATE tickets SET status = 'closed' WHERE ticket_id = 'T-LATER'");
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of("sla_target_minutes", 1)));
        when(channelRepository.findById(7L)).thenReturn(Optional.of(channel(7L, "Support")));
        jdbcTemplate.update("""
            INSERT INTO chat_history(id, ticket_id, sender, message, message_type, channel_id, timestamp)
            VALUES (1, 'T-GENERAL', 'user', 'private general text', 'text', 7, ?),
                   (2, 'T-LATER', 'user', 'legacy text', 'text', 7, ?)
            """, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2), OffsetDateTime.now(ZoneOffset.UTC));

        watcher.watch();

        verify(dialogAiAssistantService).processIncomingClientMessage("T-LATER", "legacy text", "text", null);
        org.mockito.Mockito.verifyNoMoreInteractions(dialogAiAssistantService);
        verifyNoInteractions(notificationService, alertQueueService, dialogAuditService);
        assertThat(checkpointService.readLongCursor("operator-notification-watch.chat-history")).contains(2L);
    }

    @Test
    void lateGeneralMarkerAfterChannelLookupStopsLegacyAiProcessing() {
        insertTicket("T-LATE", 703L, 7L);
        when(channelRepository.findById(7L)).thenAnswer(call -> {
            jdbcTemplate.update("UPDATE tickets SET is_general = TRUE WHERE ticket_id = 'T-LATE'");
            return Optional.of(channel(7L, "Support"));
        });
        jdbcTemplate.update("""
            INSERT INTO chat_history(id, ticket_id, sender, message, message_type, channel_id, timestamp)
            VALUES (1, 'T-LATE', 'user', 'private general text', 'text', 7, ?)
            """, OffsetDateTime.now(ZoneOffset.UTC));

        watcher.watch();

        verifyNoInteractions(notificationService, dialogAiAssistantService, alertQueueService, dialogAuditService);
        assertThat(checkpointService.readLongCursor("operator-notification-watch.chat-history")).contains(1L);
    }

    @Test
    void declinedNotificationInsertDoesNotWriteSuccessfulOverdueAudit() {
        insertTicket("T-NO-DELIVERY", 704L, 7L);
        when(channelRepository.findById(7L)).thenReturn(Optional.of(channel(7L, "Support")));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of("sla_target_minutes", 1)));
        when(notificationService.findAllOperatorRecipients()).thenReturn(Set.of("alice"));
        when(notificationService.notifyDialogUsers(eq("T-NO-DELIVERY"), org.mockito.ArgumentMatchers.anySet(), anyString(), anyString()))
            .thenReturn(Set.of());
        jdbcTemplate.update("""
            INSERT INTO chat_history(id, ticket_id, sender, message, message_type, channel_id, timestamp)
            VALUES (1, 'T-NO-DELIVERY', 'user', 'waiting', 'text', 7, ?)
            """, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));

        watcher.watch();

        verify(notificationService).notifyDialogUsers(eq("T-NO-DELIVERY"), eq(Set.of("alice")), anyString(), anyString());
        verify(dialogAuditService, never()).logDialogActionAudit(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private Channel channel(Long id, String channelName) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setChannelName(channelName);
        return channel;
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id INTEGER PRIMARY KEY,
                    ticket_id TEXT,
                    sender TEXT,
                    message TEXT,
                    message_type TEXT,
                    attachment TEXT,
                    channel_id INTEGER,
                    timestamp TIMESTAMPTZ,
                    tg_message_id INTEGER,
                    reply_to_tg_id INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE feedbacks (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER,
                    rating INTEGER,
                    ticket_id TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id TEXT,
                    channel_id INTEGER,
                    status TEXT,
                    created_at TIMESTAMPTZ,
                    user_id INTEGER,
                    is_general BOOLEAN NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER,
                    ticket_id TEXT,
                    created_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE dialog_action_audit (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT,
                    actor TEXT,
                    action TEXT,
                    result TEXT,
                    detail TEXT,
                    created_at TIMESTAMPTZ
                )
                """);
    }

    private RuntimeCoordinationService passthroughCoordinationService() {
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(coordinationService).runWithLease(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        return coordinationService;
    }
}
