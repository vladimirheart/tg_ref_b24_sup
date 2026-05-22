package com.example.panel.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("sqlite")
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DialogWorkspaceIntegrationTest {

    private static Path dbFile;
    private static Path usersDbFile;
    private static Path sharedConfigDir;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-dialog-workspace", ".db");
        usersDbFile = Files.createTempFile("panel-dialog-workspace-users", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-dialog-workspace-shared-config");
        initializeUsersDb(usersDbFile);
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
        registry.add("app.datasource.users-sqlite.path", () -> usersDbFile.toString());
        registry.add("shared-config.dir", () -> sharedConfigDir.toString());
    }

    private static void initializeUsersDb(Path path) {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        password TEXT NOT NULL,
                        enabled BOOLEAN NOT NULL DEFAULT 1,
                        role_id INTEGER,
                        role TEXT,
                        department TEXT,
                        full_name TEXT,
                        photo TEXT,
                        is_blocked BOOLEAN NOT NULL DEFAULT 0,
                        last_portal_activity_at TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS roles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        description TEXT,
                        permissions TEXT NOT NULL DEFAULT '{}'
                    )
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize users test database", ex);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("usersJdbcTemplate")
    private JdbcTemplate usersJdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM ticket_participants");
        jdbcTemplate.update("DELETE FROM ticket_active");
        jdbcTemplate.update("DELETE FROM ticket_responsibles");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM task_history");
        jdbcTemplate.update("DELETE FROM task_links");
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM dialog_action_audit");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM client_statuses");
        jdbcTemplate.update("DELETE FROM channels");
        usersJdbcTemplate.update("DELETE FROM users");
        usersJdbcTemplate.update("DELETE FROM roles");
    }

    @Test
    void workspaceApiSlicesMessagesAndUpdatesReadReceiptForAuthorizedOperator() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (81, 'token81', 'Workspace Telegram', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910081L, "T-WS-1", 81L, "workspace_user", "Клиент Workspace", "Retail", "Москва", "Офис", "Нужна помощь", "2026-05-22T10:00:00Z", 8101L);
        insertDialogTicket(910082L, "T-WS-0", 81L, "workspace_queue", "Клиент Queue", "Retail", "Москва", "Бэк-офис", "Очередь", "2026-05-22T09:00:00Z", 8102L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-1", "watcher_owner", "dispatcher", "2026-05-22T09:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-0", "watcher_owner", "dispatcher", "2026-05-22T08:59:00Z");
        insertHistoryRow("T-WS-1", 910081L, "user", "Первое сообщение", "2026-05-22T10:01:00Z", "text", 801L, null, 81L, null);
        insertHistoryRow("T-WS-1", 910081L, "operator", "Ответ операторa", "2026-05-22T10:02:00Z", "text", 802L, 801L, 81L, null);
        insertHistoryRow("T-WS-1", 910081L, "user", "Нужно ещё уточнение", "2026-05-22T10:04:00Z", "text", 803L, 802L, 81L, null);
        insertHistoryRow("T-WS-0", 910082L, "user", "Сообщение из очереди", "2026-05-22T09:05:00Z", "text", 804L, null, 81L, null);

        mockMvc.perform(get("/api/dialogs/T-WS-1/workspace")
                        .param("include", "messages,permissions,sla")
                        .param("limit", "1")
                        .param("cursor", "1")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.conversation.ticketId").value("T-WS-1"))
                .andExpect(jsonPath("$.messages.items.length()").value(1))
                .andExpect(jsonPath("$.messages.items[0].message").value("Ответ операторa"))
                .andExpect(jsonPath("$.messages.items[0].replyPreview").value("Первое сообщение"))
                .andExpect(jsonPath("$.messages.cursor").value(1))
                .andExpect(jsonPath("$.messages.next_cursor").value(2))
                .andExpect(jsonPath("$.messages.has_more").value(true))
                .andExpect(jsonPath("$.context.unavailable").value(true))
                .andExpect(jsonPath("$.permissions.can_reply").value(true))
                .andExpect(jsonPath("$.composer.reply_supported").value(true))
                .andExpect(jsonPath("$.composer.reply_target_supported").value(true))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.navigation.current_ticket_id").value("T-WS-1"))
                .andExpect(jsonPath("$.meta.navigation.found_in_queue").value(true))
                .andExpect(jsonPath("$.meta.navigation.total").value(2))
                .andExpect(jsonPath("$.meta.navigation.has_next").value(true))
                .andExpect(jsonPath("$.sla.state").isNotEmpty());

        String lastReadAt = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-WS-1",
                "watcher_owner"
        );
        assertThat(lastReadAt).isEqualTo("2026-05-22T10:04:00Z");
    }

    @Test
    void workspaceApiBuildsContextHistoryAndAuditEventsByDefault() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (82, 'token82', 'Workspace Context', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910091L, "T-WS-CTX", 82L, "ctx_user", "Клиент Context", "B2B", "Казань", "Главный офис", "Текущий диалог", "2026-05-22T11:00:00Z", 8201L);
        insertDialogTicket(910091L, "T-WS-CTX-OLD", 82L, "ctx_user", "Клиент Context", "B2B", "Казань", "Архив", "Предыдущий диалог", "2026-05-20T09:00:00Z", 8202L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-CTX", "watcher_owner", "dispatcher", "2026-05-22T10:50:00Z");
        insertHistoryRow("T-WS-CTX", 910091L, "user", "Сообщение клиента", "2026-05-22T11:01:00Z", "text", 901L, null, 82L, null);
        insertHistoryRow("T-WS-CTX", 910091L, "operator", "Ответ для клиента", "2026-05-22T11:02:00Z", "text", 902L, 901L, 82L, null);
        insertHistoryRow("T-WS-CTX", 910091L, "system", "Системное событие", "2026-05-22T11:03:00Z", "event", 903L, null, 82L, null);
        insertHistoryRow("T-WS-CTX-OLD", 910091L, "user", "История старого диалога", "2026-05-20T09:01:00Z", "text", 904L, null, 82L, null);
        jdbcTemplate.update("""
                INSERT INTO dialog_action_audit(ticket_id, actor, action, result, detail, created_at)
                VALUES (?,?,?,?,?,?)
                """,
                "T-WS-CTX", "watcher_owner", "reply", "success", "sent_message", "2026-05-22T11:04:00Z");

        mockMvc.perform(get("/api/dialogs/T-WS-CTX/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.conversation.ticketId").value("T-WS-CTX"))
                .andExpect(jsonPath("$.messages.items.length()").value(3))
                .andExpect(jsonPath("$.context.unavailable").doesNotExist())
                .andExpect(jsonPath("$.context.client.id").value(910091))
                .andExpect(jsonPath("$.context.client.name").value("Клиент Context"))
                .andExpect(jsonPath("$.context.history[0].ticket_id").value("T-WS-CTX-OLD"))
                .andExpect(jsonPath("$.context.related_events[*].type", hasItem("audit")))
                .andExpect(jsonPath("$.context.related_events[*].type", hasItem("event")))
                .andExpect(jsonPath("$.meta.navigation.current_ticket_id").value("T-WS-CTX"))
                .andExpect(jsonPath("$.meta.navigation.found_in_queue").value(true))
                .andExpect(jsonPath("$.meta.parity.checks[*].key", hasItem("messages_timeline")))
                .andExpect(jsonPath("$.permissions.can_reply").value(true))
                .andExpect(jsonPath("$.composer.media_supported").value(true));
    }

    private void insertDialogTicket(long userId,
                                    String ticketId,
                                    long channelId,
                                    String username,
                                    String clientName,
                                    String business,
                                    String city,
                                    String locationName,
                                    String problem,
                                    String createdAt,
                                    long groupMessageId) {
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                userId, ticketId, "open", channelId);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                groupMessageId,
                userId,
                business,
                city,
                locationName,
                problem,
                createdAt,
                username,
                ticketId,
                createdAt.substring(0, 10),
                createdAt.substring(11, 19),
                clientName,
                channelId,
                createdAt,
                "seed");
    }

    private void insertHistoryRow(String ticketId,
                                  long userId,
                                  String sender,
                                  String message,
                                  String timestamp,
                                  String messageType,
                                  long telegramMessageId,
                                  Long replyToTelegramId,
                                  long channelId,
                                  String attachment) {
        jdbcTemplate.update("""
                INSERT INTO chat_history (
                    user_id, sender, message, timestamp, ticket_id, message_type,
                    channel_id, tg_message_id, reply_to_tg_id, attachment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                sender,
                message,
                timestamp,
                ticketId,
                messageType,
                channelId,
                telegramMessageId,
                replyToTelegramId,
                attachment);
    }
}
