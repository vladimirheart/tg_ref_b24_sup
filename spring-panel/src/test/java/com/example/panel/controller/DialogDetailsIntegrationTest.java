package com.example.panel.controller;

import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.NotificationService;
import com.example.panel.service.SharedConfigService;
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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
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

    @Autowired
    private SharedConfigService sharedConfigService;

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
        sharedConfigService.saveSettings(new java.util.LinkedHashMap<>());
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

        mockMvc.perform(post("/api/dialogs/T-DETAIL-QA/reassign")
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

        mockMvc.perform(post("/api/dialogs/T-DETAIL-QA/resolve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["billing"]
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(post("/api/dialogs/T-DETAIL-QA/reopen")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(get("/api/dialogs/T-DETAIL-QA")
                        .param("channelId", "92")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-DETAIL-QA"))
                .andExpect(jsonPath("$.summary.clientName").value("Клиент Детали QA"))
                .andExpect(jsonPath("$.summary.clientStatus").value("VIP"))
                .andExpect(jsonPath("$.summary.channelId").value(92))
                .andExpect(jsonPath("$.summary.statusKey").value(anyOf(is("waiting_operator"), is("auto_processing"))))
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
        sharedConfigService.saveSettings(java.util.Map.of(
                "dialog_config", java.util.Map.of("sla_target_minutes", 1000000)
        ));
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

    @Test
    void detailsApiRearmsDialogUnreadAndBellBadgeAfterExplicitAckAndNextFollowUp() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (94, 'token94', 'Dialog Details Rearm', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910094L, "T-DETAIL-REARM", "open", 94L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9401L,
                910094L,
                "Retail",
                "Кострома",
                "Точка Rearm",
                "Проверка повторного follow-up после ack",
                "2026-05-27T10:00:00Z",
                "details_rearm_user",
                "T-DETAIL-REARM",
                "2026-05-27",
                "10:00:00",
                "Клиент Rearm",
                94L,
                "2026-05-27T10:00:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-REARM", "watcher_owner", "dispatcher", "2026-05-27T09:59:00Z");
        insertHistoryRow("T-DETAIL-REARM", 910094L, "user", "Первый follow-up", "2026-05-27T10:03:00Z", "text", 941L, null, 94L, null, null, null, null, null);

        notificationService.notifyDialogParticipants(
                "T-DETAIL-REARM",
                "Новое сообщение в обращении T-DETAIL-REARM",
                "/dialogs?ticketId=T-DETAIL-REARM",
                null
        );

        mockMvc.perform(get("/api/dialogs/T-DETAIL-REARM")
                        .param("channelId", "94")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.unreadCount").value(0))
                .andExpect(jsonPath("$.history[0].message").value("Первый follow-up"));

        Long firstNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "watcher_owner"
        );

        mockMvc.perform(post("/api/notifications/" + firstNotificationId + "/read")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));

        insertHistoryRow("T-DETAIL-REARM", 910094L, "user", "Второй follow-up после ack", "2026-05-27T10:05:00Z", "text", 942L, null, 94L, null, null, null, null, null);
        notificationService.notifyDialogParticipants(
                "T-DETAIL-REARM",
                "Новое сообщение в обращении T-DETAIL-REARM",
                "/dialogs?ticketId=T-DETAIL-REARM",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-REARM"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("Новое сообщение в обращении T-DETAIL-REARM"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[1].read").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(get("/api/dialogs/T-DETAIL-REARM")
                        .param("channelId", "94")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.unreadCount").value(0))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].message").value("Второй follow-up после ack"));

        String lastReadAt = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-DETAIL-REARM",
                "watcher_owner"
        );
        assertThat(lastReadAt).isEqualTo("2026-05-27T10:05:00Z");
    }

    @Test
    void notificationReadAllDoesNotHideUnreadDialogBeforeDetailsReread() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (97, 'token97', 'Dialog Details Read All', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910097L, "T-DETAIL-READ-ALL", "open", 97L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9701L,
                910097L,
                "Retail",
                "Рязань",
                "Точка Read All",
                "Проверка details read-all separation",
                "2026-05-27T11:00:00Z",
                "details_read_all_user",
                "T-DETAIL-READ-ALL",
                "2026-05-27",
                "11:00:00",
                "Клиент Details Read All",
                97L,
                "2026-05-27T11:00:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-READ-ALL", "watcher_owner", "dispatcher", "2026-05-27T10:59:00Z");
        insertHistoryRow("T-DETAIL-READ-ALL", 910097L, "user", "Follow-up до details reread", "2026-05-27T11:03:00Z", "text", 971L, null, 97L, null, null, null, null, null);

        notificationService.notifyDialogParticipants(
                "T-DETAIL-READ-ALL",
                "Новое сообщение в обращении T-DETAIL-READ-ALL",
                "/dialogs?ticketId=T-DETAIL-READ-ALL",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-READ-ALL"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-DETAIL-READ-ALL"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(post("/api/notifications/read-all")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));

        String lastReadAtBeforeDetails = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-DETAIL-READ-ALL",
                "watcher_owner"
        );
        assertThat(lastReadAtBeforeDetails).isEqualTo("2026-05-27T10:59:00Z");

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-READ-ALL"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-DETAIL-READ-ALL"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs/T-DETAIL-READ-ALL")
                        .param("channelId", "97")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-DETAIL-READ-ALL"))
                .andExpect(jsonPath("$.summary.unreadCount").value(0))
                .andExpect(jsonPath("$.history[0].message").value("Follow-up до details reread"));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-READ-ALL"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-DETAIL-READ-ALL"));
    }

    @Test
    void notificationReadAllRereadStillAllowsNextDetailsFollowUpToRearmUnreadAndBell() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (98, 'token98', 'Dialog Details Read All Rearm', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910098L, "T-DETAIL-READ-ALL-REARM", "open", 98L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9801L,
                910098L,
                "Retail",
                "Калуга",
                "Точка Rearm",
                "Проверка details read-all rearm",
                "2026-05-27T11:10:00Z",
                "details_read_all_rearm_user",
                "T-DETAIL-READ-ALL-REARM",
                "2026-05-27",
                "11:10:00",
                "Клиент Details Read All Rearm",
                98L,
                "2026-05-27T11:10:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-READ-ALL-REARM", "watcher_owner", "dispatcher", "2026-05-27T11:09:00Z");
        insertHistoryRow("T-DETAIL-READ-ALL-REARM", 910098L, "user", "Первый follow-up до mass-ack", "2026-05-27T11:13:00Z", "text", 981L, null, 98L, null, null, null, null, null);

        notificationService.notifyDialogParticipants(
                "T-DETAIL-READ-ALL-REARM",
                "Новое сообщение в обращении T-DETAIL-READ-ALL-REARM",
                "/dialogs?ticketId=T-DETAIL-READ-ALL-REARM",
                null
        );

        mockMvc.perform(post("/api/notifications/read-all")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/dialogs/T-DETAIL-READ-ALL-REARM")
                        .param("channelId", "98")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.unreadCount").value(0))
                .andExpect(jsonPath("$.history[0].message").value("Первый follow-up до mass-ack"));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-READ-ALL-REARM"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-DETAIL-READ-ALL-REARM"));

        insertHistoryRow("T-DETAIL-READ-ALL-REARM", 910098L, "user", "Второй follow-up после reread", "2026-05-27T11:15:00Z", "text", 982L, 981L, 98L, null, null, null, null, null);
        notificationService.notifyDialogParticipants(
                "T-DETAIL-READ-ALL-REARM",
                "Новое сообщение в обращении T-DETAIL-READ-ALL-REARM",
                "/dialogs?ticketId=T-DETAIL-READ-ALL-REARM",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-READ-ALL-REARM"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-DETAIL-READ-ALL-REARM"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void dialogsListRearmsMyDialogsBucketsAcrossReplyAckAndNextFollowUp() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (95, 'token95', 'Dialog Details Buckets', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910095L, "T-DETAIL-BUCKETS", "open", 95L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9501L,
                910095L,
                "Retail",
                "Иваново",
                "Точка Buckets",
                "Проверка my_dialogs rearm loop",
                "2026-05-27T10:20:00Z",
                "details_bucket_user",
                "T-DETAIL-BUCKETS",
                "2026-05-27",
                "10:20:00",
                "Клиент Buckets",
                95L,
                "2026-05-27T10:20:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-BUCKETS", "watcher_owner", "dispatcher", "2026-05-27T10:19:00Z");
        insertHistoryRow("T-DETAIL-BUCKETS", 910095L, "user", "Первый follow-up для unanswered", "2026-05-27T10:22:00Z", "text", 951L, null, 95L, null, null, null, null, null);

        notificationService.notifyDialogParticipants(
                "T-DETAIL-BUCKETS",
                "Новое сообщение в обращении T-DETAIL-BUCKETS",
                "/dialogs?ticketId=T-DETAIL-BUCKETS",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-BUCKETS"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value(anyOf(is("waiting_operator"), is("auto_processing"))))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-DETAIL-BUCKETS"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs/T-DETAIL-BUCKETS")
                        .param("channelId", "95")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.unreadCount").value(0));

        Long firstNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "watcher_owner"
        );
        mockMvc.perform(post("/api/notifications/" + firstNotificationId + "/read")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        insertHistoryRow("T-DETAIL-BUCKETS", 910095L, "operator", "Ответ оператора переводит в in_work", "2026-05-27T10:24:00Z", "operator_message", 952L, 951L, 95L, null, null, null, null, null);

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-BUCKETS"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-DETAIL-BUCKETS"));

        insertHistoryRow("T-DETAIL-BUCKETS", 910095L, "user", "Второй follow-up возвращает unanswered", "2026-05-27T10:26:00Z", "text", 953L, 952L, 95L, null, null, null, null, null);
        notificationService.notifyDialogParticipants(
                "T-DETAIL-BUCKETS",
                "Новое сообщение в обращении T-DETAIL-BUCKETS",
                "/dialogs?ticketId=T-DETAIL-BUCKETS",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-BUCKETS"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value(anyOf(is("waiting_operator"), is("auto_processing"))))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-DETAIL-BUCKETS"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void dialogsListTransfersMyDialogsOwnershipAcrossReassignAndNextFollowUp() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        insertDirectoryUser("watcher_new", "Watcher New", "/img/watcher-new.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (96, 'token96', 'Dialog Details Handoff', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910096L, "T-DETAIL-HANDOFF", "open", 96L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9601L,
                910096L,
                "Retail",
                "Рязань",
                "Точка Handoff",
                "Проверка my_dialogs ownership handoff",
                "2026-05-27T11:00:00Z",
                "details_handoff_user",
                "T-DETAIL-HANDOFF",
                "2026-05-27",
                "11:00:00",
                "Клиент Handoff",
                96L,
                "2026-05-27T11:00:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-HANDOFF", "watcher_owner", "dispatcher", "2026-05-27T11:02:00Z");
        insertHistoryRow("T-DETAIL-HANDOFF", 910096L, "user", "Клиент ждёт handoff", "2026-05-27T11:01:00Z", "text", 961L, null, 96L, null, null, null, null, null);
        insertHistoryRow("T-DETAIL-HANDOFF", 910096L, "operator", "Оператор ответил до reassign", "2026-05-27T11:02:00Z", "operator_message", 962L, 961L, 96L, null, null, null, null, null);

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-DETAIL-HANDOFF"));

        dialogQuickActionService.reassignTicket("T-DETAIL-HANDOFF", "watcher_new", "watcher_owner");

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-DETAIL-HANDOFF"));

        insertHistoryRow("T-DETAIL-HANDOFF", 910096L, "user", "Follow-up уже для нового owner", "2026-05-27T11:04:00Z", "text", 963L, 962L, 96L, null, null, null, null, null);
        notificationService.notifyDialogParticipants(
                "T-DETAIL-HANDOFF",
                "Новое сообщение в обращении T-DETAIL-HANDOFF",
                "/dialogs?ticketId=T-DETAIL-HANDOFF",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-DETAIL-HANDOFF"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-HANDOFF"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());
    }

    @Test
    void dialogsListDropsResolvedTicketFromMyDialogsAndRestoresItAfterReopen() throws Exception {
        insertDirectoryUser("watcher_owner", "Watcher Owner", "/img/watcher-owner.png");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (97, 'token97', 'Dialog Details Resolve', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO tickets (user_id, ticket_id, status, channel_id)
                VALUES (?,?,?,?)
                """,
                910097L, "T-DETAIL-RESOLVE", "open", 97L);
        jdbcTemplate.update("""
                INSERT INTO messages (
                    group_msg_id, user_id, business, city, location_name, problem, created_at,
                    username, ticket_id, created_date, created_time, client_name, channel_id, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9701L,
                910097L,
                "Retail",
                "Тверь",
                "Точка Resolve",
                "Проверка my_dialogs resolve/reopen",
                "2026-05-27T11:10:00Z",
                "details_resolve_user",
                "T-DETAIL-RESOLVE",
                "2026-05-27",
                "11:10:00",
                "Клиент Resolve",
                97L,
                "2026-05-27T11:10:00Z",
                "seed");
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-DETAIL-RESOLVE", "watcher_owner", "dispatcher", "2026-05-27T11:12:00Z");
        insertHistoryRow("T-DETAIL-RESOLVE", 910097L, "user", "Клиент пишет до закрытия", "2026-05-27T11:11:00Z", "text", 971L, null, 97L, null, null, null, null, null);
        insertHistoryRow("T-DETAIL-RESOLVE", 910097L, "operator", "Ответ перед закрытием", "2026-05-27T11:12:00Z", "operator_message", 972L, 971L, 97L, null, null, null, null, null);

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-RESOLVE"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-DETAIL-RESOLVE"));

        dialogQuickActionService.resolveTicket("T-DETAIL-RESOLVE", "watcher_owner", List.of("billing"));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-RESOLVE"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("closed"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        dialogQuickActionService.reopenTicket("T-DETAIL-RESOLVE", "watcher_owner");

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-DETAIL-RESOLVE"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-DETAIL-RESOLVE"));
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
