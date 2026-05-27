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
import java.util.List;
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
class DialogDetailsIntegrationTest {

    private static Path dbFile;
    private static Path usersDbFile;
    private static Path sharedConfigDir;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-dialog-details", ".db");
        usersDbFile = Files.createTempFile("panel-dialog-details-users", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-dialog-details-shared-config");
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
        jdbcTemplate.update("DELETE FROM ticket_categories");
        jdbcTemplate.update("DELETE FROM ticket_participants");
        jdbcTemplate.update("DELETE FROM ticket_active");
        jdbcTemplate.update("DELETE FROM ticket_responsibles");
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
    void detailsApiProjectsSummaryHistoryCategoriesAndReadReceipt() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (91, 'token91', 'Dialog Details', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910091L, "T-DETAIL-1", "open", 91L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9101L,
                910091L,
                "Retail",
                "Москва",
                "Флагман",
                "Нужна проверка details route",
                "2026-05-25T10:00:00Z",
                "details_user",
                "T-DETAIL-1",
                "2026-05-25",
                "10:00:00",
                "Клиент Детали",
                91L,
                "2026-05-25T10:00:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO client_statuses(user_id, status, updated_at)
                VALUES (?,?,?)
                """,
                910091L, "VIP", "2026-05-25T10:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-1", "watcher_owner", "dispatcher", "2026-05-25T09:59:00Z");
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-DETAIL-1", "billing");
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-DETAIL-1", "support");
        insertHistoryRow("T-DETAIL-1", 910091L, "user", "Первое сообщение", "2026-05-25T10:01:00Z", "text", 901L, null, 91L, null, null, null, null, null);
        insertHistoryRow("T-DETAIL-1", 910091L, "operator", "Обновлённый ответ", "2026-05-25T10:02:00Z", "image", 902L, 901L, 91L, "reply.png", "Изначальный ответ", "2026-05-25T10:02:30Z", null, "lead");
        insertHistoryRow("T-DETAIL-1", 910091L, "user", "Нужно ещё уточнение", "2026-05-25T10:03:00Z", "text", 903L, 902L, 91L, null, "Нужно ещё уточнение", null, null, null);

        mockMvc.perform(get("/api/dialogs/T-DETAIL-1")
                        .param("channelId", "91")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-DETAIL-1"))
                .andExpect(jsonPath("$.summary.clientName").value("Клиент Детали"))
                .andExpect(jsonPath("$.summary.clientStatus").value("VIP"))
                .andExpect(jsonPath("$.summary.channelId").value(91))
                .andExpect(jsonPath("$.summary.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.summary.unreadCount").value(0))
                .andExpect(jsonPath("$.summary.responsible").value("Watcher Owner"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[1].replyPreview").value("Первое сообщение"))
                .andExpect(jsonPath("$.history[1].originalMessage").value("Изначальный ответ"))
                .andExpect(jsonPath("$.history[1].attachment").value("/api/attachments/tickets/T-DETAIL-1/reply.png"))
                .andExpect(jsonPath("$.history[1].editedAt").value("2026-05-25T10:02:30Z"))
                .andExpect(jsonPath("$.history[1].forwardedFrom").value("lead"))
                .andExpect(jsonPath("$.history[2].replyPreview").value("Обновлённый ответ"))
                .andExpect(jsonPath("$.categories[0]").value("billing"))
                .andExpect(jsonPath("$.categories[1]").value("support"));

        String lastReadAt = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-DETAIL-1",
                "watcher_owner"
        );
        assertThat(lastReadAt).isEqualTo("2026-05-25T10:03:00Z");
    }

    @Test
    void detailsApiReturnsNotFoundPayloadForUnknownTicket() throws Exception {
        mockMvc.perform(get("/api/dialogs/T-DETAIL-404")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Диалог не найден"));
    }

    @Test
    void detailsApiRefreshesResponsibleAndStatusAfterReassignResolveAndReopenLifecycle() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        insertDirectoryUser("watcher_new", "Watcher New", "/img/watcher-new.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (92, 'token92', 'Dialog Details Lifecycle', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910092L, "T-DETAIL-QA", "open", 92L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9201L,
                910092L,
                "Retail",
                "Тула",
                "Точка QA",
                "Проверка lifecycle details",
                "2026-05-26T18:10:00Z",
                "details_lifecycle_user",
                "T-DETAIL-QA",
                "2026-05-26",
                "18:10:00",
                "Клиент Детали QA",
                92L,
                "2026-05-26T18:10:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO client_statuses(user_id, status, updated_at)
                VALUES (?,?,?)
                """,
                910092L, "VIP", "2026-05-26T18:10:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-QA", "watcher_owner", "dispatcher", "2026-05-26T18:09:00Z");
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-DETAIL-QA", "billing");
        insertHistoryRow("T-DETAIL-QA", 910092L, "user", "Lifecycle first message", "2026-05-26T18:11:00Z", "text", 921L, null, 92L, null, null, null, null, null);

        dialogQuickActionService.reassignTicket("T-DETAIL-QA", "watcher_new", "watcher_owner");
        dialogQuickActionService.resolveTicket("T-DETAIL-QA", "watcher_new", List.of("billing"));
        dialogQuickActionService.reopenTicket("T-DETAIL-QA", "watcher_new");

        mockMvc.perform(get("/api/dialogs/T-DETAIL-QA")
                        .param("channelId", "92")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-DETAIL-QA"))
                .andExpect(jsonPath("$.summary.clientName").value("Клиент Детали QA"))
                .andExpect(jsonPath("$.summary.clientStatus").value("VIP"))
                .andExpect(jsonPath("$.summary.channelId").value(92))
                .andExpect(jsonPath("$.summary.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.summary.unreadCount").value(0))
                .andExpect(jsonPath("$.summary.responsible").value("Watcher New"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0]").value("billing"))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].message").value("Lifecycle first message"));
    }

    @Test
    void detailsApiRefreshesDialogUnreadLoopWithoutImplicitlyAckingBellNotifications() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (93, 'token93', 'Dialog Details Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910093L, "T-DETAIL-NOTIFY", "open", 93L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9301L,
                910093L,
                "Retail",
                "Ярославль",
                "Точка Notify",
                "Проверка notification/read marker loop",
                "2026-05-27T09:00:00Z",
                "details_notify_user",
                "T-DETAIL-NOTIFY",
                "2026-05-27",
                "09:00:00",
                "Клиент Notify",
                93L,
                "2026-05-27T09:00:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-NOTIFY", "watcher_owner", "dispatcher", "2026-05-27T08:59:00Z");
        insertHistoryRow("T-DETAIL-NOTIFY", 910093L, "user", "Новый клиентский follow-up", "2026-05-27T09:03:00Z", "text", 931L, null, 93L, null, null, null, null, null);

        notificationService.notifyDialogParticipants(
                "T-DETAIL-NOTIFY",
                "Новое сообщение в обращении T-DETAIL-NOTIFY",
                "/dialogs?ticketId=T-DETAIL-NOTIFY",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-NOTIFY"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("waiting_operator"));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Новое сообщение в обращении T-DETAIL-NOTIFY"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-DETAIL-NOTIFY"))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(get("/api/dialogs/T-DETAIL-NOTIFY")
                        .param("channelId", "93")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-DETAIL-NOTIFY"))
                .andExpect(jsonPath("$.summary.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.summary.unreadCount").value(0))
                .andExpect(jsonPath("$.summary.responsible").value("Watcher Owner"))
                .andExpect(jsonPath("$.history[0].message").value("Новый клиентский follow-up"));

        String lastReadAt = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-DETAIL-NOTIFY",
                "watcher_owner"
        );
        assertThat(lastReadAt).isEqualTo("2026-05-27T09:03:00Z");

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-NOTIFY"))
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

    private void insertHistoryRow(String ticketId,
                                  long userId,
                                  String sender,
                                  String message,
                                  String timestamp,
                                  String messageType,
                                  long telegramMessageId,
                                  Long replyToTelegramId,
                                  long channelId,
                                  String attachment,
                                  String originalMessage,
                                  String editedAt,
                                  String deletedAt,
                                  String forwardedFrom) {
        jdbcTemplate.update("""
                INSERT INTO chat_history (
                    user_id, sender, message, timestamp, ticket_id, message_type,
                    channel_id, tg_message_id, reply_to_tg_id, attachment,
                    original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                attachment,
                originalMessage,
                editedAt,
                deletedAt,
                forwardedFrom);
    }
}
