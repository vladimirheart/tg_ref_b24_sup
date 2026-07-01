package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.Mockito.when;

class OperatorNotificationWatcherTest {

    private JdbcTemplate jdbcTemplate;
    private NotificationService notificationService;
    private DialogAiAssistantService dialogAiAssistantService;
    private AlertQueueService alertQueueService;
    private ChannelRepository channelRepository;
    private DialogAuditService dialogAuditService;
    private SharedConfigService sharedConfigService;
    private OperatorNotificationWatcher watcher;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("operator-watcher-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();

        notificationService = mock(NotificationService.class);
        when(notificationService.buildDialogUrl(anyString())).thenAnswer(invocation -> "/dialogs/" + invocation.getArgument(0, String.class).trim());
        dialogAiAssistantService = mock(DialogAiAssistantService.class);
        alertQueueService = mock(AlertQueueService.class);
        channelRepository = mock(ChannelRepository.class);
        dialogAuditService = mock(DialogAuditService.class);
        sharedConfigService = mock(SharedConfigService.class);

        watcher = new OperatorNotificationWatcher(
                jdbcTemplate,
                notificationService,
                dialogAiAssistantService,
                alertQueueService,
                channelRepository,
                dialogAuditService,
                sharedConfigService
        );
        watcher.initialize();
    }

    @Test
    void watchUsesAlertQueueForIncomingClientMessageWithoutFallbackWhenHandled() {
        Channel channel = channel(7L, "Support Queue");
        when(channelRepository.findById(7L)).thenReturn(Optional.of(channel));
        when(alertQueueService.notifyIncomingClientMessage(channel, "T-101", "Клиент прислал лог")).thenReturn(true);

        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "T-101", "user", "Клиент прислал лог", "text", null, 7L, OffsetDateTime.now(ZoneOffset.UTC).toString());

        watcher.watch();

        verify(alertQueueService).notifyIncomingClientMessage(channel, "T-101", "Клиент прислал лог");
        verify(notificationService, never()).notifyAllOperators(eq("Новое сообщение в обращении T-101: Клиент прислал лог"), eq("/dialogs/T-101"), isNull());
        verify(dialogAiAssistantService).processIncomingClientMessage("T-101", "Клиент прислал лог", "text", null);
    }

    @Test
    void watchFallsBackToAllOperatorsWhenIncomingClientMessageIsNotHandledByAlertQueue() {
        Channel channel = channel(8L, "Fallback Queue");
        when(channelRepository.findById(8L)).thenReturn(Optional.of(channel));
        when(alertQueueService.notifyIncomingClientMessage(channel, "T-102", "Есть уточнение")).thenReturn(false);

        jdbcTemplate.update("""
                INSERT INTO chat_history(id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "T-102", "user", "Есть уточнение", "text", null, 8L, OffsetDateTime.now(ZoneOffset.UTC).toString());

        watcher.watch();

        verify(alertQueueService).notifyIncomingClientMessage(channel, "T-102", "Есть уточнение");
        verify(notificationService).notifyAllOperators(
                "Новое сообщение в обращении T-102: Есть уточнение",
                "/dialogs/T-102",
                null
        );
        verify(dialogAiAssistantService).processIncomingClientMessage("T-102", "Есть уточнение", "text", null);
    }

    @Test
    void watchIgnoresHistoricalClientMessagesOutsideLiveReplayWindow() {
        Channel channel = channel(9L, "History Replay Guard");
        when(channelRepository.findById(9L)).thenReturn(Optional.of(channel));

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
        verify(notificationService).notifyUsers(
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
                    timestamp TEXT,
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
                    ticket_id TEXT PRIMARY KEY,
                    channel_id INTEGER,
                    status TEXT,
                    created_at TEXT,
                    user_id INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER,
                    ticket_id TEXT,
                    created_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE dialog_action_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    actor TEXT,
                    action TEXT,
                    result TEXT,
                    detail TEXT,
                    created_at TEXT
                )
                """);
    }
}
