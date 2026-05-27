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
import java.util.LinkedHashSet;
import java.util.Set;

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
class DialogReadIntegrationTest {

    private static Path dbFile;
    private static Path usersDbFile;
    private static Path sharedConfigDir;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-dialog-read", ".db");
        usersDbFile = Files.createTempFile("panel-dialog-read-users", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-dialog-read-shared-config");
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
        ensureChatHistoryMutationColumns();
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM ticket_participants");
        jdbcTemplate.update("DELETE FROM ticket_active");
        jdbcTemplate.update("DELETE FROM ticket_responsibles");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM channels");
        usersJdbcTemplate.update("DELETE FROM users");
        usersJdbcTemplate.update("DELETE FROM roles");
        ensureUsersDirectoryColumns();
    }

    @Test
    void historyApiProjectsReplyPreviewMutationMarkersAndReadReceipt() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (71, 'token71', 'Dialog Read', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-READ-1", "watcher_owner", "dispatcher", "2026-05-22T10:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "T-READ-1", "user", "Первое сообщение", "2026-05-22T10:01:00Z", "text", null,
                501L, null, 71L, "Первое сообщение", null, null, null);
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "T-READ-1", "operator", "Уточнённый ответ", "2026-05-22T10:02:00Z", "image", "reply.png",
                502L, 501L, 71L, "Изначальный ответ", "2026-05-22T10:03:00Z", null, "manager");
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "T-READ-1", "operator", "Скрытое удалённое сообщение", "2026-05-22T10:04:00Z", "text", null,
                503L, null, 71L, "Скрытое удалённое сообщение", null, "2026-05-22T10:05:00Z", null);

        mockMvc.perform(get("/api/dialogs/T-READ-1/history")
                        .param("channelId", "71")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages[0].message").value("Первое сообщение"))
                .andExpect(jsonPath("$.messages[1].replyPreview").value("Первое сообщение"))
                .andExpect(jsonPath("$.messages[1].originalMessage").value("Изначальный ответ"))
                .andExpect(jsonPath("$.messages[1].attachment").value("/api/attachments/tickets/T-READ-1/reply.png"))
                .andExpect(jsonPath("$.messages[1].editedAt").value("2026-05-22T10:03:00Z"))
                .andExpect(jsonPath("$.messages[1].forwardedFrom").value("manager"))
                .andExpect(jsonPath("$.messages[2].message").value(""))
                .andExpect(jsonPath("$.messages[2].originalMessage").value("Скрытое удалённое сообщение"))
                .andExpect(jsonPath("$.messages[2].deletedAt").value("2026-05-22T10:05:00Z"));

        String lastReadAt = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-READ-1",
                "watcher_owner"
        );
        assertThat(lastReadAt).isEqualTo("2026-05-22T10:04:00Z");
    }

    @Test
    void previousHistoryApiReturnsNestedMutationProjectionAndWebFormSourceLabel() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (72, 'token72', 'Web History', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id, status, channel_id) VALUES (?,?,?,?)",
                9001L, "T-READ-CUR", "pending", 72L);
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id, status, channel_id) VALUES (?,?,?,?)",
                9001L, "T-READ-OLD", "resolved", 72L);
        jdbcTemplate.update("""
                INSERT INTO web_form_sessions(
                    token, ticket_id, channel_id, user_id, answers_json,
                    client_name, client_contact, username, created_at, last_active_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                "token-old-1",
                "T-READ-OLD",
                72L,
                9001L,
                "{}",
                "Исторический клиент",
                "+79990000000",
                "history_user",
                "2026-05-20T09:00:00Z",
                "2026-05-20T09:03:00Z");
        jdbcTemplate.update("""
                INSERT INTO messages(group_msg_id, ticket_id, user_id, created_at, problem, channel_id)
                VALUES (?,?,?,?,?,?)
                """,
                7201L, "T-READ-CUR", 9001L, "2026-05-22T11:00:00Z", "Текущий диалог", 72L);
        jdbcTemplate.update("""
                INSERT INTO messages(group_msg_id, ticket_id, user_id, created_at, problem, channel_id)
                VALUES (?,?,?,?,?,?)
                """,
                7101L, "T-READ-OLD", 9001L, "2026-05-20T09:00:00Z", "Предыдущий диалог", 72L);
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "T-READ-OLD", "user", "Историческое первое сообщение", "2026-05-20T09:01:00Z", "text",
                601L, null, 72L, "Историческое первое сообщение", null, null, null);
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "T-READ-OLD", "operator", "Удалённый ответ", "2026-05-20T09:02:00Z", "text",
                602L, 601L, 72L, "Исходный ответ", null, "2026-05-20T09:03:00Z", "lead");

        mockMvc.perform(get("/api/dialogs/T-READ-CUR/history/previous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.batch.ticketId").value("T-READ-OLD"))
                .andExpect(jsonPath("$.batch.sourceKey").value("web_form"))
                .andExpect(jsonPath("$.batch.sourceLabel").value("Внешняя форма"))
                .andExpect(jsonPath("$.batch.messages[1].replyPreview").value("Историческое первое сообщение"))
                .andExpect(jsonPath("$.batch.messages[1].message").value(""))
                .andExpect(jsonPath("$.batch.messages[1].originalMessage").value("Исходный ответ"))
                .andExpect(jsonPath("$.batch.messages[1].deletedAt").value("2026-05-20T09:03:00Z"))
                .andExpect(jsonPath("$.batch.messages[1].forwardedFrom").value("lead"));
    }

    @Test
    void participantsAndOperatorsApisReflectRuntimeDirectoryProjection() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("watcher_peer", true, false, 1L, "Support", "Watcher Peer", "Ops", "/img/peer.png");
        insertDirectoryUser("watcher_observer", true, false, 1L, "Support", "Watcher Observer", "Backoffice", "/img/observer.png");
        insertDirectoryUser("watcher_disabled", false, false, 1L, "Support", "Watcher Disabled", "Ops", "/img/disabled.png");
        insertDirectoryUser("watcher_blocked", true, true, 1L, "Support", "Watcher Blocked", "Ops", "/img/blocked.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (72, 'token72b', 'Participants Channel', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910080L, "T-READ-2", "open", 72L);
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-READ-2", "watcher_peer", "watcher_owner");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-READ-2", "watcher_observer", "watcher_owner");

        mockMvc.perform(get("/api/dialogs/T-READ-2/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.participants[0].username").value("watcher_observer"))
                .andExpect(jsonPath("$.participants[0].department").value("Backoffice"))
                .andExpect(jsonPath("$.participants[1].username").value("watcher_peer"))
                .andExpect(jsonPath("$.participants[1].displayName").value("Watcher Peer"))
                .andExpect(jsonPath("$.participants[1].role").value("Support"));

        mockMvc.perform(get("/api/dialogs/operators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.operators[0].username").value("watcher_observer"))
                .andExpect(jsonPath("$.operators[1].username").value("watcher_owner"))
                .andExpect(jsonPath("$.operators[2].username").value("watcher_peer"))
                .andExpect(jsonPath("$.operators[*].username").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("watcher_disabled"))))
                .andExpect(jsonPath("$.operators[*].username").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("watcher_blocked"))));
    }

    @Test
    void participantsApiRefreshesAfterReassignAndParticipantRemovalLifecycle() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("watcher_new", true, false, 1L, "Support", "Watcher New", "Ops", "/img/new.png");
        insertDirectoryUser("watcher_peer", true, false, 1L, "Support", "Watcher Peer", "Ops", "/img/peer.png");
        insertDirectoryUser("watcher_observer", true, false, 1L, "Support", "Watcher Observer", "Backoffice", "/img/observer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (73, 'token73c', 'Participants Lifecycle', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910081L, "T-READ-QA", "open", 73L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, problem, created_at, username, ticket_id,
                    created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                7301L,
                910081L,
                "Нужно обновить участников",
                "2026-05-26T18:05:00Z",
                "read_lifecycle_user",
                "T-READ-QA",
                "2026-05-26",
                "18:05:00",
                "Клиент Read QA",
                73L,
                "2026-05-26T18:05:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-READ-QA", "watcher_owner", "dispatcher", "2026-05-26T18:04:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-READ-QA", "watcher_peer", "watcher_owner");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-READ-QA", "watcher_observer", "watcher_owner");

        dialogQuickActionService.reassignTicket("T-READ-QA", "watcher_new", "watcher_owner");
        dialogQuickActionService.removeParticipant("T-READ-QA", "watcher_observer", "watcher_new");

        mockMvc.perform(get("/api/dialogs/T-READ-QA/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].username").value("watcher_peer"))
                .andExpect(jsonPath("$.participants[0].displayName").value("Watcher Peer"))
                .andExpect(jsonPath("$.participants[0].department").value("Ops"))
                .andExpect(jsonPath("$.participants[0].role").value("Support"));
    }

    @Test
    void historyApiRefreshesDialogUnreadLoopWithoutImplicitlyAckingBellNotifications() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (74, 'token74d', 'History Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910082L, "T-READ-NOTIFY", "open", 74L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, problem, created_at, username, ticket_id,
                    created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                7401L,
                910082L,
                "Проверка history notification loop",
                "2026-05-27T09:20:00Z",
                "read_notify_user",
                "T-READ-NOTIFY",
                "2026-05-27",
                "09:20:00",
                "Клиент History Notify",
                74L,
                "2026-05-27T09:20:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-READ-NOTIFY", "watcher_owner", "dispatcher", "2026-05-27T09:19:00Z");
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, channel_id, tg_message_id
                ) VALUES (?,?,?,?,?,?,?)
                """,
                "T-READ-NOTIFY", "user", "Follow-up для history route", "2026-05-27T09:23:00Z", "text", 74L, 741L);

        notificationService.notifyDialogParticipants(
                "T-READ-NOTIFY",
                "Новое сообщение в обращении T-READ-NOTIFY",
                "/dialogs?ticketId=T-READ-NOTIFY",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-READ-NOTIFY"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(get("/api/dialogs/T-READ-NOTIFY/history")
                        .param("channelId", "74")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages[0].message").value("Follow-up для history route"));

        String lastReadAt = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-READ-NOTIFY",
                "watcher_owner"
        );
        assertThat(lastReadAt).isEqualTo("2026-05-27T09:23:00Z");

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-READ-NOTIFY"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        Long notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "watcher_owner"
        );

        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    private void ensureChatHistoryMutationColumns() {
        ensureColumn(jdbcTemplate, "chat_history", "original_message", "TEXT");
        ensureColumn(jdbcTemplate, "chat_history", "edited_at", "TEXT");
        ensureColumn(jdbcTemplate, "chat_history", "deleted_at", "TEXT");
        ensureColumn(jdbcTemplate, "chat_history", "forwarded_from", "TEXT");
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

    private Set<String> loadColumns(JdbcTemplate template, String tableName) {
        return new LinkedHashSet<>(template.query(
                "PRAGMA table_info(" + tableName + ")",
                (rs, rowNum) -> rs.getString("name")
        ));
    }

    private void insertDirectoryUser(String username,
                                     boolean enabled,
                                     boolean blocked,
                                     Long roleId,
                                     String roleName,
                                     String fullName,
                                     String department,
                                     String photo) {
        Set<String> columns = loadColumns(usersJdbcTemplate, "users");
        StringBuilder sql = new StringBuilder("INSERT INTO users(");
        StringBuilder values = new StringBuilder(" VALUES (");
        java.util.List<Object> params = new java.util.ArrayList<>();

        appendUserColumn(sql, values, params, "username", username);
        appendUserColumn(sql, values, params, "password", "n/a");
        if (columns.contains("enabled")) {
            appendUserColumn(sql, values, params, "enabled", enabled ? 1 : 0);
        }
        if (columns.contains("role_id")) {
            appendUserColumn(sql, values, params, "role_id", roleId);
        } else if (columns.contains("role")) {
            appendUserColumn(sql, values, params, "role", roleName);
        }
        if (columns.contains("department")) {
            appendUserColumn(sql, values, params, "department", department);
        }
        if (columns.contains("full_name")) {
            appendUserColumn(sql, values, params, "full_name", fullName);
        }
        if (columns.contains("photo")) {
            appendUserColumn(sql, values, params, "photo", photo);
        }
        if (columns.contains("is_blocked")) {
            appendUserColumn(sql, values, params, "is_blocked", blocked ? 1 : 0);
        }

        sql.append(")");
        values.append(")");
        usersJdbcTemplate.update(sql.append(values).toString(), params.toArray());
    }

    private void appendUserColumn(StringBuilder sql,
                                  StringBuilder values,
                                  java.util.List<Object> params,
                                  String column,
                                  Object value) {
        if (!params.isEmpty()) {
            sql.append(", ");
            values.append(", ");
        }
        sql.append(column);
        values.append("?");
        params.add(value);
    }
}
