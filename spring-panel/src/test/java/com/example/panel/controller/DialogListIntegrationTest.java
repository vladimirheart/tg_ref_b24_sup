package com.example.panel.controller;

import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.NotificationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("sqlite")
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DialogListIntegrationTest {

    private static Path dbFile;
    private static Path usersDbFile;
    private static Path sharedConfigDir;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-dialog-list", ".db");
        usersDbFile = Files.createTempFile("panel-dialog-list-users", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-dialog-list-shared-config");
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

    @Autowired
    private DialogQuickActionService dialogQuickActionService;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM ticket_categories");
        jdbcTemplate.update("DELETE FROM ticket_active");
        jdbcTemplate.update("DELETE FROM ticket_participants");
        jdbcTemplate.update("DELETE FROM ticket_responsibles");
        jdbcTemplate.update("DELETE FROM ticket_ai_agent_state");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM client_statuses");
        jdbcTemplate.update("DELETE FROM channels");
        usersJdbcTemplate.update("DELETE FROM users");
        usersJdbcTemplate.update("DELETE FROM roles");
        ensureUsersDirectoryColumns();
    }

    @Test
    void listApiReturnsDialogsButKeepsMyDialogsEmptyWithoutAuthentication() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (61, 'token61', 'Dialog List Anonymous', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id, created_at)
                VALUES (?,?,?,?,?)
                """,
                920061L, "T-LIST-ANON", "open", 61L, "2026-05-28T09:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                6101L,
                920061L,
                "Retail",
                "Москва",
                "Точка Anonymous",
                "Проверка list route без auth",
                "2026-05-28T09:00:00Z",
                "list_anon_user",
                "T-LIST-ANON",
                "2026-05-28",
                "09:00:00",
                "Клиент Anonymous",
                61L,
                "2026-05-28T09:00:00Z",
                "seed");
        insertHistoryRow("T-LIST-ANON", 920061L, "user", "Anonymous list request", "2026-05-28T09:01:00Z", "text", 611L, null, 61L);

        mockMvc.perform(get("/api/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-ANON"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());
    }

    @Test
    void listApiProjectsOwnerHandoffAndAutoProcessingBucketsForNewOwner() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        insertDirectoryUser("watcher_new", "Watcher New", "/img/watcher-new.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (62, 'token62', 'Dialog List Handoff', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id, created_at)
                VALUES (?,?,?,?,?)
                """,
                920062L, "T-LIST-HANDOFF", "open", 62L, "2026-05-28T09:10:00Z");
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                6201L,
                920062L,
                "Retail",
                "Казань",
                "Точка Handoff",
                "Проверка list handoff + auto_processing",
                "2026-05-28T09:10:00Z",
                "list_handoff_user",
                "T-LIST-HANDOFF",
                "2026-05-28",
                "09:10:00",
                "Клиент Handoff",
                62L,
                "2026-05-28T09:10:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-LIST-HANDOFF", "watcher_owner", "dispatcher", "2026-05-28T09:12:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, updated_at)
                VALUES (?, 1, ?, ?, CURRENT_TIMESTAMP)
                """,
                "T-LIST-HANDOFF", "assist", "drafting");
        insertHistoryRow("T-LIST-HANDOFF", 920062L, "user", "Первое сообщение до handoff", "2026-05-28T09:11:00Z", "text", 621L, null, 62L);
        insertHistoryRow("T-LIST-HANDOFF", 920062L, "operator", "Ответ до handoff", "2026-05-28T09:12:00Z", "operator_message", 622L, 621L, 62L);

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("auto_processing"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-LIST-HANDOFF"));

        mockMvc.perform(post("/api/dialogs/T-LIST-HANDOFF/reassign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "watcher_new"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responsible").value("watcher_new"));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-LIST-HANDOFF"));

        insertHistoryRow("T-LIST-HANDOFF", 920062L, "user", "Follow-up уже у нового owner", "2026-05-28T09:14:00Z", "text", 623L, 622L, 62L);
        notificationService.notifyDialogParticipants(
                "T-LIST-HANDOFF",
                "Новое сообщение в обращении T-LIST-HANDOFF",
                "/dialogs?ticketId=T-LIST-HANDOFF",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-LIST-HANDOFF"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-LIST-HANDOFF"
        )).isEqualTo("watcher_new");
    }

    @Test
    void listApiRefreshesMyDialogsAcrossHttpResolveAndReopenLifecycle() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (63, 'token63', 'Dialog List Close Reopen', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id, created_at)
                VALUES (?,?,?,?,?)
                """,
                920063L, "T-LIST-RESOLVE", "open", 63L, "2026-05-28T09:20:00Z");
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                6301L,
                920063L,
                "Retail",
                "Самара",
                "Точка Resolve",
                "Проверка list resolve/reopen через HTTP",
                "2026-05-28T09:20:00Z",
                "list_resolve_user",
                "T-LIST-RESOLVE",
                "2026-05-28",
                "09:20:00",
                "Клиент Resolve",
                63L,
                "2026-05-28T09:20:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-LIST-RESOLVE", "watcher_owner", "dispatcher", "2026-05-28T09:21:00Z");
        insertHistoryRow("T-LIST-RESOLVE", 920063L, "user", "Диалог уже в работе", "2026-05-28T09:21:00Z", "text", 631L, null, 63L);
        insertHistoryRow("T-LIST-RESOLVE", 920063L, "operator", "Оператор ответил перед закрытием", "2026-05-28T09:22:00Z", "operator_message", 632L, 631L, 63L);

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-RESOLVE"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-LIST-RESOLVE"));

        mockMvc.perform(post("/api/dialogs/T-LIST-RESOLVE/resolve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["billing"]
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-RESOLVE"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("closed"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(post("/api/dialogs/T-LIST-RESOLVE/reopen")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-LIST-RESOLVE"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("waiting_client"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-LIST-RESOLVE"));
    }

    private void insertDirectoryUser(String username, String fullName, String photo) {
        usersJdbcTemplate.update("""
                INSERT INTO users(username, password, enabled, full_name, photo, is_blocked)
                VALUES (?, ?, 1, ?, ?, 0)
                """,
                username,
                "n/a",
                fullName,
                photo);
    }

    private void ensureUsersDirectoryColumns() {
        ensureColumn(usersJdbcTemplate, "users", "department", "TEXT");
        ensureColumn(usersJdbcTemplate, "users", "full_name", "TEXT");
        ensureColumn(usersJdbcTemplate, "users", "photo", "TEXT");
        ensureColumn(usersJdbcTemplate, "users", "is_blocked", "BOOLEAN NOT NULL DEFAULT 0");
        ensureColumn(usersJdbcTemplate, "users", "enabled", "BOOLEAN NOT NULL DEFAULT 1");
        ensureColumn(usersJdbcTemplate, "users", "role_id", "INTEGER");
        ensureColumn(usersJdbcTemplate, "users", "role", "TEXT");
    }

    private void ensureColumn(JdbcTemplate template, String tableName, String columnName, String definition) {
        if (loadColumns(template, tableName).contains(columnName)) {
            return;
        }
        template.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private java.util.Set<String> loadColumns(JdbcTemplate template, String tableName) {
        return new java.util.LinkedHashSet<>(template.query(
                "PRAGMA table_info(" + tableName + ")",
                (rs, rowNum) -> rs.getString("name")
        ));
    }

    private void insertHistoryRow(String ticketId,
                                  long userId,
                                  String sender,
                                  String message,
                                  String timestamp,
                                  String messageType,
                                  long telegramMessageId,
                                  Long replyToTelegramId,
                                  long channelId) {
        jdbcTemplate.update("""
                INSERT INTO chat_history (
                    user_id, sender, message, timestamp, ticket_id, message_type,
                    channel_id, tg_message_id, reply_to_tg_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                sender,
                message,
                timestamp,
                ticketId,
                messageType,
                channelId,
                telegramMessageId,
                replyToTelegramId);
    }
}
