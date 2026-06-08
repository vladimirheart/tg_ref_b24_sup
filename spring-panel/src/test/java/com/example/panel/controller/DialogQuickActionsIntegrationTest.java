package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.service.DialogReplyTransportService;
import com.example.panel.service.SharedConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class DialogQuickActionsIntegrationTest {

    private static Path dbFile;
    private static Path usersDbFile;
    private static Path sharedConfigDir;
    private static Path attachmentsDir;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-dialog-quick-actions", ".db");
        usersDbFile = Files.createTempFile("panel-dialog-quick-actions-users", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-dialog-quick-actions-shared-config");
        attachmentsDir = Files.createTempDirectory("panel-dialog-quick-actions-attachments");
        initializeUsersDb(usersDbFile);
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
        registry.add("app.datasource.users-sqlite.path", () -> usersDbFile.toString());
        registry.add("shared-config.dir", () -> sharedConfigDir.toString());
        registry.add("app.storage.attachments", () -> attachmentsDir.toString());
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
    private SharedConfigService sharedConfigService;

    @SpyBean
    private DialogReplyTransportService dialogReplyTransportService;

    @BeforeEach
    void clean() {
        ensureChatHistoryMutationColumns();
        ensureUsersDirectoryColumns();
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM ticket_categories");
        jdbcTemplate.update("DELETE FROM ticket_participants");
        jdbcTemplate.update("DELETE FROM ticket_active");
        jdbcTemplate.update("DELETE FROM ticket_ai_agent_state");
        jdbcTemplate.update("DELETE FROM ticket_responsibles");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM dialog_action_audit");
        jdbcTemplate.update("DELETE FROM client_blacklist_history");
        jdbcTemplate.update("DELETE FROM client_blacklist");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM client_statuses");
        jdbcTemplate.update("DELETE FROM channels");
        usersJdbcTemplate.update("DELETE FROM users");
        usersJdbcTemplate.update("DELETE FROM roles");
        sharedConfigService.saveSettings(new java.util.LinkedHashMap<>());
    }

    @Test
    void quickActionsApiReassignsAndMutatesParticipantsAcrossReadAndListConsumers() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("watcher_new", true, false, 1L, "Support", "Watcher New", "Ops", "/img/new.png");
        insertDirectoryUser("watcher_peer", true, false, 1L, "Support", "Watcher Peer", "Ops", "/img/peer.png");
        insertDirectoryUser("watcher_observer", true, false, 1L, "Support", "Watcher Observer", "Backoffice", "/img/observer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (101, 'token101', 'Quick Actions Handoff', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920101L, "T-QA-LIVE", 101L, "quick_action_user", "Клиент Quick Action", "Retail", "Калуга", "Точка QA", "Проверка reassign + participants", "2026-06-03T09:00:00Z", 10101L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-LIVE", "watcher_owner", "dispatcher", "2026-06-03T08:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-LIVE", "watcher_peer", "watcher_owner");
        insertHistoryRow("T-QA-LIVE", 920101L, "user", "Клиент пишет до handoff", "2026-06-03T09:01:00Z", "text", 1101L, null, 101L);
        insertHistoryRow("T-QA-LIVE", 920101L, "operator", "Оператор уже ответил", "2026-06-03T09:02:00Z", "operator_message", 1102L, 1101L, 101L);

        mockMvc.perform(post("/api/dialogs/T-QA-LIVE/reassign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "watcher_new"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responsible").value("watcher_new"))
                .andExpect(jsonPath("$.displayResponsible").value("Watcher New"))
                .andExpect(jsonPath("$.avatarUrl").value("/img/new.png"))
                .andExpect(jsonPath("$.participants[*].username", hasItem("watcher_peer")));

        mockMvc.perform(post("/api/dialogs/T-QA-LIVE/participants")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "watcher_observer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[*].username", hasItem("watcher_peer")))
                .andExpect(jsonPath("$.participants[*].username", hasItem("watcher_observer")));

        mockMvc.perform(delete("/api/dialogs/T-QA-LIVE/participants/watcher_peer")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].username").value("watcher_observer"));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-LIVE"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-LIVE"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-QA-LIVE"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-LIVE/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].username").value("watcher_observer"))
                .andExpect(jsonPath("$.participants[0].displayName").value("Watcher Observer"))
                .andExpect(jsonPath("$.participants[0].department").value("Backoffice"));

        mockMvc.perform(get("/api/dialogs/T-QA-LIVE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-LIVE"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_new"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(1))
                .andExpect(jsonPath("$.workflow.participants[0].username").value("watcher_observer"))
                .andExpect(jsonPath("$.workflow.actions.participants_remove.enabled").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-LIVE"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-LIVE"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_new"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-LIVE"));

        mockMvc.perform(get("/api/dialogs/T-QA-LIVE/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].username").value("watcher_observer"))
                .andExpect(jsonPath("$.participants[0].displayName").value("Watcher Observer"))
                .andExpect(jsonPath("$.participants[0].department").value("Backoffice"));

        mockMvc.perform(get("/api/dialogs/T-QA-LIVE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-LIVE"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_new"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(1))
                .andExpect(jsonPath("$.workflow.participants[0].username").value("watcher_observer"))
                .andExpect(jsonPath("$.workflow.actions.participants_remove.enabled").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-QA-LIVE"
        )).isEqualTo("watcher_new");
        assertThat(countAuditRows("T-QA-LIVE", "reassign", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-LIVE", "participants_add", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-LIVE", "participants_remove", "success")).isEqualTo(1);
    }

    @Test
    void quickActionsApiResolveAndReopenRefreshListDetailsAndWorkspaceConsumers() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (102, 'token102', 'Quick Actions Close', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920102L, "T-QA-CLOSE", 102L, "quick_close_user", "Клиент Close", "Retail", "Тула", "Точка Close", "Проверка resolve + reopen", "2026-06-03T10:00:00Z", 10201L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-CLOSE", "watcher_owner", "dispatcher", "2026-06-03T09:59:00Z");
        insertHistoryRow("T-QA-CLOSE", 920102L, "user", "Клиент ожидает решения", "2026-06-03T10:01:00Z", "text", 1201L, null, 102L);

        mockMvc.perform(post("/api/dialogs/T-QA-CLOSE/resolve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["billing", "support"]
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("closed"))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-CLOSE")
                        .param("channelId", "102")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.summary.statusKey").value("closed"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0]").value("billing"))
                .andExpect(jsonPath("$.categories[1]").value("support"));

        mockMvc.perform(get("/api/dialogs/T-QA-CLOSE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.conversation.statusKey").value("closed"))
                .andExpect(jsonPath("$.workflow.actions.resolve.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.resolve.disabled_reason").value("already_closed"))
                .andExpect(jsonPath("$.workflow.actions.reopen.enabled").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-CLOSE/reopen")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.dialogs[0].statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.my_dialogs.in_work").isEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-CLOSE")
                        .param("channelId", "102")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.summary.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0]").value("billing"))
                .andExpect(jsonPath("$.categories[1]").value("support"));

        mockMvc.perform(get("/api/dialogs/T-QA-CLOSE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.conversation.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.workflow.actions.resolve.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reopen.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.reopen.disabled_reason").value("not_closed"))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("quick_close: success (updated)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reopen: success (updated)")));

        mockMvc.perform(get("/api/dialogs/T-QA-CLOSE")
                        .param("channelId", "102")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.summary.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0]").value("billing"))
                .andExpect(jsonPath("$.categories[1]").value("support"));

        mockMvc.perform(get("/api/dialogs/T-QA-CLOSE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-CLOSE"))
                .andExpect(jsonPath("$.conversation.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.workflow.actions.resolve.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reopen.enabled").value(false))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("quick_close: success (updated)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reopen: success (updated)")));

        List<String> categories = jdbcTemplate.query(
                "SELECT category FROM ticket_categories WHERE ticket_id = ? ORDER BY category",
                (rs, rowNum) -> rs.getString(1),
                "T-QA-CLOSE"
        );
        assertThat(categories).containsExactly("billing", "support");
        assertThat(countAuditRows("T-QA-CLOSE", "quick_close", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-CLOSE", "reopen", "success")).isEqualTo(1);
    }

    @Test
    void quickActionsApiReplyRefreshesDetailsWorkspaceAndAuditTrailForWebFormDialog() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (104, 'token104', 'Quick Actions Reply', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920104L, "T-QA-REPLY", 104L, "quick_reply_user", "Клиент Reply", "Retail", "Орёл", "Точка Reply", "Проверка operator reply", "2026-06-04T08:00:00Z", 10401L);
        jdbcTemplate.update("""
                INSERT INTO web_form_sessions(
                    token, ticket_id, channel_id, user_id, answers_json,
                    client_name, client_contact, username, created_at, last_active_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                "qa-reply-token",
                "T-QA-REPLY",
                104L,
                920104L,
                "{}",
                "Клиент Reply",
                "+79990000000",
                "quick_reply_user",
                "2026-06-04T08:00:00Z",
                "2026-06-04T08:01:00Z");
        insertHistoryRow("T-QA-REPLY", 920104L, "user", "Клиент написал первым", "2026-06-04T08:01:00Z", "text", 1401L, null, 104L);

        mockMvc.perform(post("/api/dialogs/T-QA-REPLY/reply")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "Здравствуйте, уточните номер заказа"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.telegramMessageId").isNumber())
                .andExpect(jsonPath("$.responsible").value("watcher_owner"));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-REPLY"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-REPLY"));

        mockMvc.perform(get("/api/dialogs/T-QA-REPLY")
                        .param("channelId", "104")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-REPLY"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].message").value("Здравствуйте, уточните номер заказа"));

        mockMvc.perform(get("/api/dialogs/T-QA-REPLY/history")
                        .param("channelId", "104")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message").value("Здравствуйте, уточните номер заказа"));

        mockMvc.perform(get("/api/dialogs/T-QA-REPLY/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-REPLY"))
                .andExpect(jsonPath("$.workflow.actions.reply.enabled").value(true))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply: success (message_sent)")));

        mockMvc.perform(get("/api/dialogs/T-QA-REPLY")
                        .param("channelId", "104")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-REPLY"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].message").value("Здравствуйте, уточните номер заказа"));

        mockMvc.perform(get("/api/dialogs/T-QA-REPLY/history")
                        .param("channelId", "104")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message").value("Здравствуйте, уточните номер заказа"));

        mockMvc.perform(get("/api/dialogs/T-QA-REPLY/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-REPLY"))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply: success (message_sent)")));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-REPLY"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-REPLY"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-QA-REPLY"
        )).isEqualTo("watcher_owner");
        assertThat(countAuditRows("T-QA-REPLY", "reply", "success")).isEqualTo(1);
    }

    @Test
    void quickActionsApiWebFormReplyEditDeleteRefreshesDetailsWorkspaceWithoutTelegramTransport() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (108, '', 'Quick Actions Web Form Mutate', 'vk', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920108L, "T-QA-WEB-MUTATE", 108L, "quick_web_mutate_user", "Клиент Web Mutate", "Retail", "Курск", "Точка Web Mutate", "Проверка web_form reply/edit/delete", "2026-06-04T12:00:00Z", 10801L);
        jdbcTemplate.update("""
                INSERT INTO web_form_sessions(
                    token, ticket_id, channel_id, user_id, answers_json,
                    client_name, client_contact, username, created_at, last_active_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                "qa-web-mutate-token",
                "T-QA-WEB-MUTATE",
                108L,
                920108L,
                "{}",
                "Клиент Web Mutate",
                "+79991112233",
                "quick_web_mutate_user",
                "2026-06-04T12:00:00Z",
                "2026-06-04T12:01:00Z");
        insertHistoryRow("T-QA-WEB-MUTATE", 920108L, "user", "Клиент ждёт ответ в форме", "2026-06-04T12:01:00Z", "text", 1801L, null, 108L);

        mockMvc.perform(post("/api/dialogs/T-QA-WEB-MUTATE/reply")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "Первичный ответ через web_form"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.telegramMessageId").isNumber())
                .andExpect(jsonPath("$.responsible").value("watcher_owner"));

        Long localTelegramMessageId = jdbcTemplate.queryForObject(
                "SELECT tg_message_id FROM chat_history WHERE ticket_id = ? AND sender = 'operator' ORDER BY rowid DESC LIMIT 1",
                Long.class,
                "T-QA-WEB-MUTATE"
        );
        assertThat(localTelegramMessageId).isNotNull();

        mockMvc.perform(post("/api/dialogs/T-QA-WEB-MUTATE/edit")
                        .contentType("application/json")
                        .content("""
                                {
                                  "telegramMessageId": %d,
                                  "message": "Уточнённый ответ через web_form"
                                }
                                """.formatted(localTelegramMessageId))
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-WEB-MUTATE/delete")
                        .contentType("application/json")
                        .content("""
                                {
                                  "telegramMessageId": %d
                                }
                                """.formatted(localTelegramMessageId))
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-WEB-MUTATE"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-WEB-MUTATE"));

        mockMvc.perform(get("/api/dialogs/T-QA-WEB-MUTATE")
                        .param("channelId", "108")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-WEB-MUTATE"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].message").value(""))
                .andExpect(jsonPath("$.history[1].originalMessage").value("Первичный ответ через web_form"))
                .andExpect(jsonPath("$.history[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.history[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-WEB-MUTATE/history")
                        .param("channelId", "108")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message").value(""))
                .andExpect(jsonPath("$.messages[1].originalMessage").value("Первичный ответ через web_form"))
                .andExpect(jsonPath("$.messages[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.messages[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-WEB-MUTATE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-WEB-MUTATE"))
                .andExpect(jsonPath("$.workflow.actions.reply.enabled").value(true))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply: success (message_sent)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("edit: success (message_edited)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("delete: success (message_deleted)")));

        mockMvc.perform(get("/api/dialogs/T-QA-WEB-MUTATE")
                        .param("channelId", "108")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-WEB-MUTATE"))
                .andExpect(jsonPath("$.history[1].message").value(""))
                .andExpect(jsonPath("$.history[1].originalMessage").value("Первичный ответ через web_form"))
                .andExpect(jsonPath("$.history[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.history[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-WEB-MUTATE/history")
                        .param("channelId", "108")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message").value(""))
                .andExpect(jsonPath("$.messages[1].originalMessage").value("Первичный ответ через web_form"))
                .andExpect(jsonPath("$.messages[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.messages[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-WEB-MUTATE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-WEB-MUTATE"))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply: success (message_sent)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("edit: success (message_edited)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("delete: success (message_deleted)")));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-WEB-MUTATE"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-WEB-MUTATE"));

        verify(dialogReplyTransportService, never()).sendText(any(Channel.class), eq(920108L), eq("Первичный ответ через web_form"), isNull());
        verify(dialogReplyTransportService, never()).editTelegramMessage(any(Channel.class), eq(920108L), eq(localTelegramMessageId), eq("Уточнённый ответ через web_form"));
        verify(dialogReplyTransportService, never()).deleteTelegramMessage(any(Channel.class), eq(920108L), eq(localTelegramMessageId));

        assertThat(countAuditRows("T-QA-WEB-MUTATE", "reply", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-WEB-MUTATE", "edit", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-WEB-MUTATE", "delete", "success")).isEqualTo(1);
    }

    @Test
    void quickActionsApiReplyEditDeleteRefreshesDetailsWorkspaceAndAuditTrail() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (106, 'token106', 'Quick Actions Edit Delete', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920106L, "T-QA-MUTATE", 106L, "quick_mutate_user", "Клиент Mutate", "Retail", "Тверь", "Точка Mutate", "Проверка reply/edit/delete", "2026-06-04T10:00:00Z", 10601L);
        insertHistoryRow("T-QA-MUTATE", 920106L, "user", "Клиент ждёт уточнение", "2026-06-04T10:01:00Z", "text", 1601L, null, 106L);

        doReturn(new DialogReplyTransportService.DialogReplyTransportResult(null, 1602L))
                .when(dialogReplyTransportService)
                .sendText(any(Channel.class), eq(920106L), eq("Первичный операторский ответ"), isNull());
        doReturn(null)
                .when(dialogReplyTransportService)
                .editTelegramMessage(any(Channel.class), eq(920106L), eq(1602L), eq("Уточнённый операторский ответ"));
        doReturn(null)
                .when(dialogReplyTransportService)
                .deleteTelegramMessage(any(Channel.class), eq(920106L), eq(1602L));

        mockMvc.perform(post("/api/dialogs/T-QA-MUTATE/reply")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "Первичный операторский ответ"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.telegramMessageId").value(1602))
                .andExpect(jsonPath("$.responsible").value("watcher_owner"));

        mockMvc.perform(post("/api/dialogs/T-QA-MUTATE/edit")
                        .contentType("application/json")
                        .content("""
                                {
                                  "telegramMessageId": 1602,
                                  "message": "Уточнённый операторский ответ"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-MUTATE/delete")
                        .contentType("application/json")
                        .content("""
                                {
                                  "telegramMessageId": 1602
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-MUTATE"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-MUTATE"));

        mockMvc.perform(get("/api/dialogs/T-QA-MUTATE")
                        .param("channelId", "106")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-MUTATE"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].message").value(""))
                .andExpect(jsonPath("$.history[1].originalMessage").value("Первичный операторский ответ"))
                .andExpect(jsonPath("$.history[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.history[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-MUTATE/history")
                        .param("channelId", "106")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message").value(""))
                .andExpect(jsonPath("$.messages[1].originalMessage").value("Первичный операторский ответ"))
                .andExpect(jsonPath("$.messages[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.messages[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-MUTATE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-MUTATE"))
                .andExpect(jsonPath("$.workflow.actions.reply.enabled").value(true))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply: success (message_sent)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("edit: success (message_edited)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("delete: success (message_deleted)")));

        mockMvc.perform(get("/api/dialogs/T-QA-MUTATE")
                        .param("channelId", "106")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-MUTATE"))
                .andExpect(jsonPath("$.history[1].message").value(""))
                .andExpect(jsonPath("$.history[1].originalMessage").value("Первичный операторский ответ"))
                .andExpect(jsonPath("$.history[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.history[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-MUTATE/history")
                        .param("channelId", "106")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message").value(""))
                .andExpect(jsonPath("$.messages[1].originalMessage").value("Первичный операторский ответ"))
                .andExpect(jsonPath("$.messages[1].editedAt").isNotEmpty())
                .andExpect(jsonPath("$.messages[1].deletedAt").isNotEmpty());

        mockMvc.perform(get("/api/dialogs/T-QA-MUTATE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-MUTATE"))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply: success (message_sent)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("edit: success (message_edited)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("delete: success (message_deleted)")));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-MUTATE"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-MUTATE"));

        assertThat(countAuditRows("T-QA-MUTATE", "reply", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-MUTATE", "edit", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-MUTATE", "delete", "success")).isEqualTo(1);
    }

    @Test
    void quickActionsApiMediaReplyRefreshesDetailsWorkspaceAndAuditTrail() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (107, 'token107', 'Quick Actions Media', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920107L, "T-QA-MEDIA", 107L, "quick_media_user", "Клиент Media", "Retail", "Кострома", "Точка Media", "Проверка reply media", "2026-06-04T11:00:00Z", 10701L);
        insertHistoryRow("T-QA-MEDIA", 920107L, "user", "Нужна картинка", "2026-06-04T11:01:00Z", "text", 1701L, null, 107L);

        doReturn(new DialogReplyTransportService.DialogReplyTransportResult(null, 1702L))
                .when(dialogReplyTransportService)
                .sendMedia(any(Channel.class), eq(920107L), any(MockMultipartFile.class), eq("Смотрите вложение"), eq("proof.png"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/dialogs/T-QA-MEDIA/media")
                        .file(new MockMultipartFile("file", "proof.png", "image/png", "png".getBytes()))
                        .param("message", "Смотрите вложение")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.telegramMessageId").value(1702))
                .andExpect(jsonPath("$.responsible").value("watcher_owner"))
                .andExpect(jsonPath("$.messageType").value("image"))
                .andExpect(jsonPath("$.attachment", startsWith("/api/attachments/tickets/T-QA-MEDIA/")));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-MEDIA"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-MEDIA"));

        mockMvc.perform(get("/api/dialogs/T-QA-MEDIA")
                        .param("channelId", "107")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-MEDIA"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].message").value("Смотрите вложение"))
                .andExpect(jsonPath("$.history[1].messageType").value("image"))
                .andExpect(jsonPath("$.history[1].attachment", startsWith("/api/attachments/tickets/T-QA-MEDIA/")));

        mockMvc.perform(get("/api/dialogs/T-QA-MEDIA/history")
                        .param("channelId", "107")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message").value("Смотрите вложение"))
                .andExpect(jsonPath("$.messages[1].messageType").value("image"))
                .andExpect(jsonPath("$.messages[1].attachment", startsWith("/api/attachments/tickets/T-QA-MEDIA/")));

        mockMvc.perform(get("/api/dialogs/T-QA-MEDIA/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-MEDIA"))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply_media: success (media_sent)")));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-MEDIA"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(0))
                .andExpect(jsonPath("$.my_dialogs.unanswered").isEmpty())
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-MEDIA"));

        mockMvc.perform(get("/api/dialogs/T-QA-MEDIA")
                        .param("channelId", "107")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-MEDIA"))
                .andExpect(jsonPath("$.history[1].messageType").value("image"))
                .andExpect(jsonPath("$.history[1].attachment", startsWith("/api/attachments/tickets/T-QA-MEDIA/")));

        mockMvc.perform(get("/api/dialogs/T-QA-MEDIA/history")
                        .param("channelId", "107")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].messageType").value("image"))
                .andExpect(jsonPath("$.messages[1].attachment", startsWith("/api/attachments/tickets/T-QA-MEDIA/")));

        mockMvc.perform(get("/api/dialogs/T-QA-MEDIA/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-MEDIA"))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply_media: success (media_sent)")));

        assertThat(countAuditRows("T-QA-MEDIA", "reply_media", "success")).isEqualTo(1);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quickActionsApiReplyEditDeleteNotifiesPeerParticipantsThroughNotificationApi() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("notify_owner", true, false, 1L, "Support", "Notify Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("notify_peer", true, false, 1L, "Support", "Notify Peer", "Ops", "/img/peer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (109, 'token109', 'Quick Actions Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920109L, "T-QA-NOTIFY", 109L, "quick_notify_user", "Клиент Notify", "Retail", "Томск", "Точка Notify", "Проверка reply/edit/delete notifications", "2026-06-05T09:00:00Z", 10901L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-NOTIFY", "notify_owner", "dispatcher", "2026-06-05T08:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-NOTIFY", "notify_peer", "notify_owner");
        insertHistoryRow("T-QA-NOTIFY", 920109L, "user", "Клиент ждёт update", "2026-06-05T09:01:00Z", "text", 1901L, null, 109L);
        insertHistoryRow("T-QA-NOTIFY", 920109L, "operator", "Старый ответ для стабильности", "2026-06-05T09:01:30Z", "operator_message", 1900L, 1901L, 109L);

        doReturn(new DialogReplyTransportService.DialogReplyTransportResult(null, 1902L))
                .when(dialogReplyTransportService)
                .sendText(any(Channel.class), eq(920109L), eq("Первичный ответ для peer"), isNull());
        doReturn(null)
                .when(dialogReplyTransportService)
                .editTelegramMessage(any(Channel.class), eq(920109L), eq(1902L), eq("Уточнённый ответ для peer"));
        doReturn(null)
                .when(dialogReplyTransportService)
                .deleteTelegramMessage(any(Channel.class), eq(920109L), eq(1902L));

        mockMvc.perform(post("/api/dialogs/T-QA-NOTIFY/reply")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "Первичный ответ для peer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-NOTIFY/edit")
                        .contentType("application/json")
                        .content("""
                                {
                                  "telegramMessageId": 1902,
                                  "message": "Уточнённый ответ для peer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-NOTIFY/delete")
                        .contentType("application/json")
                        .content("""
                                {
                                  "telegramMessageId": 1902
                                }
                                """)
                        .principal(new TestingAuthenticationToken("notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].text").value("Сообщение в обращении T-QA-NOTIFY было удалено"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-QA-NOTIFY"))
                .andExpect(jsonPath("$[1].text").value("Сообщение в обращении T-QA-NOTIFY было отредактировано"))
                .andExpect(jsonPath("$[1].url").value("/dialogs/T-QA-NOTIFY"))
                .andExpect(jsonPath("$[2].text").value("Новое сообщение в обращении T-QA-NOTIFY"))
                .andExpect(jsonPath("$[2].url").value("/dialogs/T-QA-NOTIFY"));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(3));

        Long latestNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "notify_peer"
        );
        mockMvc.perform(post("/api/notifications/" + latestNotificationId + "/read")
                        .principal(new TestingAuthenticationToken("notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quickActionsApiMediaReplyNotifiesPeerParticipantsThroughNotificationApi() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("media_notify_owner", true, false, 1L, "Support", "Media Notify Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("media_notify_peer", true, false, 1L, "Support", "Media Notify Peer", "Ops", "/img/peer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (110, 'token110', 'Quick Actions Media Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920110L, "T-QA-MEDIA-NOTIFY", 110L, "quick_media_notify_user", "Клиент Media Notify", "Retail", "Омск", "Точка Media Notify", "Проверка media notifications", "2026-06-05T09:30:00Z", 11001L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-MEDIA-NOTIFY", "media_notify_owner", "dispatcher", "2026-06-05T09:29:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-MEDIA-NOTIFY", "media_notify_peer", "media_notify_owner");
        insertHistoryRow("T-QA-MEDIA-NOTIFY", 920110L, "user", "Клиент просит файл", "2026-06-05T09:31:00Z", "text", 2001L, null, 110L);
        insertHistoryRow("T-QA-MEDIA-NOTIFY", 920110L, "operator", "Ранее уже отвечали клиенту", "2026-06-05T09:31:30Z", "operator_message", 2000L, 2001L, 110L);

        doReturn(new DialogReplyTransportService.DialogReplyTransportResult(null, 2002L))
                .when(dialogReplyTransportService)
                .sendMedia(any(Channel.class), eq(920110L), any(MockMultipartFile.class), eq("Файл для peer"), eq("proof.png"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/dialogs/T-QA-MEDIA-NOTIFY/media")
                        .file(new MockMultipartFile("file", "proof.png", "image/png", "png".getBytes()))
                        .param("message", "Файл для peer")
                        .principal(new TestingAuthenticationToken("media_notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("media_notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Новое медиа-сообщение в обращении T-QA-MEDIA-NOTIFY"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-QA-MEDIA-NOTIFY"));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("media_notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("media_notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quickActionsApiSpamNotifiesPeerParticipantsThroughNotificationApi() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("spam_notify_owner", true, false, 1L, "Support", "Spam Notify Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("spam_notify_peer", true, false, 1L, "Support", "Spam Notify Peer", "Ops", "/img/peer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (113, 'token113', 'Quick Actions Spam Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920113L, "T-QA-SPAM-NOTIFY", 113L, "quick_spam_notify_user", "Клиент Spam Notify", "Retail", "Иваново", "Точка Spam Notify", "Проверка spam notifications", "2026-06-05T11:00:00Z", 11301L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-SPAM-NOTIFY", "spam_notify_owner", "dispatcher", "2026-06-05T10:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-SPAM-NOTIFY", "spam_notify_peer", "spam_notify_owner");
        jdbcTemplate.update("""
                INSERT INTO ticket_ai_agent_dialog_control(ticket_id, ai_disabled, auto_reply_blocked, reason, updated_by)
                VALUES (?,?,?,?,?)
                """,
                "T-QA-SPAM-NOTIFY", 1, 1, "spam notification parity test", "test");
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-QA-SPAM-NOTIFY", "billing");
        insertHistoryRow("T-QA-SPAM-NOTIFY", 920113L, "user", "Клиент прислал spam", "2026-06-05T11:01:00Z", "text", 2301L, null, 113L);

        mockMvc.perform(post("/api/dialogs/T-QA-SPAM-NOTIFY/spam")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason": "Спам-атака через notification parity"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("spam_notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true))
                .andExpect(jsonPath("$.userId").value("920113"))
                .andExpect(jsonPath("$.categories", hasItem("billing")))
                .andExpect(jsonPath("$.categories", hasItem("Спам")));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("spam_notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Обращение T-QA-SPAM-NOTIFY помечено как спам"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-QA-SPAM-NOTIFY"))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("spam_notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        Long latestSpamNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "spam_notify_peer"
        );
        mockMvc.perform(post("/api/notifications/" + latestSpamNotificationId + "/read")
                        .principal(new TestingAuthenticationToken("spam_notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("spam_notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("spam_notify_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(latestSpamNotificationId))
                .andExpect(jsonPath("$[0].read").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("spam_notify_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quickActionsApiLifecycleActionsNotifyPeerParticipantsThroughNotificationApi() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("lifecycle_owner", true, false, 1L, "Support", "Lifecycle Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("lifecycle_peer", true, false, 1L, "Support", "Lifecycle Peer", "Ops", "/img/peer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (111, 'token111', 'Quick Actions Lifecycle Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920111L, "T-QA-LIFECYCLE-NOTIFY", 111L, "quick_lifecycle_notify_user", "Клиент Lifecycle Notify", "Retail", "Курск", "Точка Lifecycle Notify", "Проверка lifecycle notifications", "2026-06-05T10:00:00Z", 11101L);
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-LIFECYCLE-NOTIFY", "lifecycle_peer", "dispatcher");
        jdbcTemplate.update("""
                INSERT INTO ticket_ai_agent_dialog_control(ticket_id, ai_disabled, auto_reply_blocked, reason, updated_by)
                VALUES (?,?,?,?,?)
                """,
                "T-QA-LIFECYCLE-NOTIFY", 1, 1, "notification parity test", "test");
        insertHistoryRow("T-QA-LIFECYCLE-NOTIFY", 920111L, "user", "Клиент ждёт life-cycle update", "2026-06-05T10:01:00Z", "text", 2101L, null, 111L);
        insertHistoryRow("T-QA-LIFECYCLE-NOTIFY", 920111L, "operator", "Первый операторский ответ уже был", "2026-06-05T10:01:30Z", "operator_message", 2100L, 2101L, 111L);

        mockMvc.perform(post("/api/dialogs/T-QA-LIFECYCLE-NOTIFY/take")
                        .principal(new TestingAuthenticationToken("lifecycle_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-LIFECYCLE-NOTIFY/categories")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["vip", "priority"]
                                }
                                """)
                        .principal(new TestingAuthenticationToken("lifecycle_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-LIFECYCLE-NOTIFY/resolve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["vip", "priority"]
                                }
                                """)
                        .principal(new TestingAuthenticationToken("lifecycle_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-LIFECYCLE-NOTIFY/reopen")
                        .principal(new TestingAuthenticationToken("lifecycle_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("lifecycle_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].text").value("Обращение T-QA-LIFECYCLE-NOTIFY снова открыто"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-QA-LIFECYCLE-NOTIFY"))
                .andExpect(jsonPath("$[1].text").value("Обращение T-QA-LIFECYCLE-NOTIFY закрыто"))
                .andExpect(jsonPath("$[1].url").value("/dialogs/T-QA-LIFECYCLE-NOTIFY"))
                .andExpect(jsonPath("$[2].text").value("В обращении T-QA-LIFECYCLE-NOTIFY обновлены категории"))
                .andExpect(jsonPath("$[2].url").value("/dialogs/T-QA-LIFECYCLE-NOTIFY"))
                .andExpect(jsonPath("$[3].text").value("Обращение T-QA-LIFECYCLE-NOTIFY взято в работу оператором lifecycle_owner"))
                .andExpect(jsonPath("$[3].url").value("/dialogs/T-QA-LIFECYCLE-NOTIFY"));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("lifecycle_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(4));

        Long latestLifecycleNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "lifecycle_peer"
        );
        mockMvc.perform(post("/api/notifications/" + latestLifecycleNotificationId + "/read")
                        .principal(new TestingAuthenticationToken("lifecycle_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("lifecycle_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(3));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("lifecycle_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(latestLifecycleNotificationId))
                .andExpect(jsonPath("$[0].read").value(true))
                .andExpect(jsonPath("$[1].read").value(false))
                .andExpect(jsonPath("$[2].read").value(false))
                .andExpect(jsonPath("$[3].read").value(false));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("lifecycle_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quickActionsApiCollaborationActionsNotifyPeerParticipantsThroughNotificationApi() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("collab_owner", true, false, 1L, "Support", "Collab Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("collab_new", true, false, 1L, "Support", "Collab New", "Ops", "/img/new.png");
        insertDirectoryUser("collab_peer", true, false, 1L, "Support", "Collab Peer", "Ops", "/img/peer.png");
        insertDirectoryUser("collab_observer", true, false, 1L, "Support", "Collab Observer", "Backoffice", "/img/observer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (112, 'token112', 'Quick Actions Collab Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920112L, "T-QA-COLLAB-NOTIFY", 112L, "quick_collab_notify_user", "Клиент Collab Notify", "Retail", "Ярославль", "Точка Collab Notify", "Проверка collaboration notifications", "2026-06-05T10:30:00Z", 11201L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-COLLAB-NOTIFY", "collab_owner", "dispatcher", "2026-06-05T10:29:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-COLLAB-NOTIFY", "collab_peer", "collab_owner");
        jdbcTemplate.update("""
                INSERT INTO ticket_ai_agent_dialog_control(ticket_id, ai_disabled, auto_reply_blocked, reason, updated_by)
                VALUES (?,?,?,?,?)
                """,
                "T-QA-COLLAB-NOTIFY", 1, 1, "notification parity test", "test");
        insertHistoryRow("T-QA-COLLAB-NOTIFY", 920112L, "user", "Клиент ждёт handoff", "2026-06-05T10:31:00Z", "text", 2201L, null, 112L);
        insertHistoryRow("T-QA-COLLAB-NOTIFY", 920112L, "operator", "Оператор уже отвечал ранее", "2026-06-05T10:31:30Z", "operator_message", 2200L, 2201L, 112L);

        mockMvc.perform(post("/api/dialogs/T-QA-COLLAB-NOTIFY/reassign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "collab_new"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("collab_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-COLLAB-NOTIFY/participants")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "collab_observer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("collab_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(true));

        mockMvc.perform(delete("/api/dialogs/T-QA-COLLAB-NOTIFY/participants/collab_observer")
                        .principal(new TestingAuthenticationToken("collab_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(true));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("collab_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].text").value("Из обращения T-QA-COLLAB-NOTIFY исключен оператор Collab Observer"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-QA-COLLAB-NOTIFY"))
                .andExpect(jsonPath("$[1].text").value("К обращению T-QA-COLLAB-NOTIFY подключен оператор Collab Observer"))
                .andExpect(jsonPath("$[1].url").value("/dialogs/T-QA-COLLAB-NOTIFY"))
                .andExpect(jsonPath("$[2].text").value("Обращение T-QA-COLLAB-NOTIFY передано оператору Collab New"))
                .andExpect(jsonPath("$[2].url").value("/dialogs/T-QA-COLLAB-NOTIFY"));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("collab_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(3));

        Long latestCollabNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "collab_peer"
        );
        mockMvc.perform(post("/api/notifications/" + latestCollabNotificationId + "/read")
                        .principal(new TestingAuthenticationToken("collab_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("collab_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken("collab_peer", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(latestCollabNotificationId))
                .andExpect(jsonPath("$[0].read").value(true))
                .andExpect(jsonPath("$[1].read").value(false))
                .andExpect(jsonPath("$[2].read").value(false));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("collab_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void quickActionsApiSnoozeKeepsDialogStateAndRefreshesWorkspaceAuditTrail() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (105, 'token105', 'Quick Actions Snooze', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920105L, "T-QA-SNOOZE", 105L, "quick_snooze_user", "Клиент Snooze", "Retail", "Псков", "Точка Snooze", "Проверка snooze audit trail", "2026-06-04T09:00:00Z", 10501L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-SNOOZE", "watcher_owner", "dispatcher", "2026-06-04T08:59:00Z");
        insertHistoryRow("T-QA-SNOOZE", 920105L, "user", "Клиент ждёт ответ", "2026-06-04T09:01:00Z", "text", 1501L, null, 105L);

        mockMvc.perform(post("/api/dialogs/T-QA-SNOOZE/snooze")
                        .contentType("application/json")
                        .content("""
                                {
                                  "minutes": 15
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-SNOOZE"))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-QA-SNOOZE"));

        mockMvc.perform(get("/api/dialogs/T-QA-SNOOZE")
                        .param("channelId", "105")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-SNOOZE"))
                .andExpect(jsonPath("$.summary.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"));

        mockMvc.perform(get("/api/dialogs/T-QA-SNOOZE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-SNOOZE"))
                .andExpect(jsonPath("$.conversation.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.workflow.actions.snooze.enabled").value(true))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("snooze: success (minutes=15)")));

        mockMvc.perform(get("/api/dialogs/T-QA-SNOOZE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-SNOOZE"))
                .andExpect(jsonPath("$.workflow.actions.snooze.enabled").value(true))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("snooze: success (minutes=15)")));

        assertThat(countAuditRows("T-QA-SNOOZE", "snooze", "success")).isEqualTo(1);
    }

    @Test
    void quickActionsApiSnoozeReturnsNotFoundForMissingDialog() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        mockMvc.perform(post("/api/dialogs/T-QA-SNOOZE-MISSING/snooze")
                        .contentType("application/json")
                        .content("""
                                {
                                  "minutes": 15
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Диалог не найден"));

        assertThat(countAuditRows("T-QA-SNOOZE-MISSING", "snooze", "not_found")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-SNOOZE-MISSING", "snooze", "success")).isEqualTo(0);
    }

    @Test
    void quickActionsApiMissingDialogReturnsNotFoundAcrossRemainingOperatorActions() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("watcher_peer", true, false, 1L, "Support", "Watcher Peer", "Ops", "/img/peer.png");

        mockMvc.perform(post("/api/dialogs/T-QA-MISSING-TAKE/take")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Диалог не найден"));

        mockMvc.perform(post("/api/dialogs/T-QA-MISSING-SPAM/spam")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason": "Спам"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Диалог не найден"));

        mockMvc.perform(post("/api/dialogs/T-QA-MISSING-PARTICIPANT/participants")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "watcher_peer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Диалог не найден"));

        mockMvc.perform(post("/api/dialogs/T-QA-MISSING-REASSIGN/reassign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "watcher_peer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Диалог не найден"));

        assertThat(countAuditRows("T-QA-MISSING-TAKE", "take", "not_found")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-MISSING-SPAM", "mark_spam", "not_found")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-MISSING-PARTICIPANT", "participants_add", "not_found")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-MISSING-REASSIGN", "reassign", "not_found")).isEqualTo(1);
    }

    @Test
    void quickActionsApiCollaborationNoopAndErrorSemanticsStayExplicit() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("collab_owner", true, false, 1L, "Support", "Collab Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("collab_peer", true, false, 1L, "Support", "Collab Peer", "Ops", "/img/peer.png");
        insertDirectoryUser("collab_observer", true, false, 1L, "Support", "Collab Observer", "Backoffice", "/img/observer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (114, 'token114', 'Quick Actions Collab Noop', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920114L, "T-QA-COLLAB-NOOP", 114L, "quick_collab_noop_user", "Клиент Collab Noop", "Retail", "Тверь", "Точка Collab Noop", "Проверка collaboration noop semantics", "2026-06-05T12:00:00Z", 11401L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-COLLAB-NOOP", "collab_owner", "dispatcher", "2026-06-05T11:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-COLLAB-NOOP", "collab_peer", "collab_owner");
        insertHistoryRow("T-QA-COLLAB-NOOP", 920114L, "user", "Клиент ждёт handoff", "2026-06-05T12:01:00Z", "text", 2401L, null, 114L);
        insertHistoryRow("T-QA-COLLAB-NOOP", 920114L, "operator", "Оператор уже отвечал ранее", "2026-06-05T12:01:30Z", "operator_message", 2400L, 2401L, 114L);

        mockMvc.perform(post("/api/dialogs/T-QA-COLLAB-NOOP/participants")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "collab_observer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("collab_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.participants.length()").value(2));

        mockMvc.perform(post("/api/dialogs/T-QA-COLLAB-NOOP/participants")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "collab_observer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("collab_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.participants.length()").value(2));

        mockMvc.perform(delete("/api/dialogs/T-QA-COLLAB-NOOP/participants/ghost_operator")
                        .principal(new TestingAuthenticationToken("collab_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.participants.length()").value(2));

        mockMvc.perform(post("/api/dialogs/T-QA-COLLAB-NOOP/reassign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "collab_owner"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("collab_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Диалог уже назначен на этого пользователя"));

        mockMvc.perform(get("/api/dialogs/T-QA-COLLAB-NOOP/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[*].username", hasItem("collab_peer")))
                .andExpect(jsonPath("$.participants[*].username", hasItem("collab_observer")));

        mockMvc.perform(get("/api/dialogs/T-QA-COLLAB-NOOP/workspace")
                        .principal(new TestingAuthenticationToken("collab_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-COLLAB-NOOP"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("collab_owner"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(2))
                .andExpect(jsonPath("$.workflow.participants[*].username", hasItem("collab_peer")))
                .andExpect(jsonPath("$.workflow.participants[*].username", hasItem("collab_observer")));

        assertThat(countAuditRows("T-QA-COLLAB-NOOP", "participants_add", "success")).isEqualTo(2);
        assertThat(countAuditRows("T-QA-COLLAB-NOOP", "participants_remove", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-COLLAB-NOOP", "reassign", "error")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-QA-COLLAB-NOOP"
        )).isEqualTo("collab_owner");
    }

    @Test
    void quickActionsApiClosedDialogCollaborationActionsReturnExplicitErrors() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("closed_owner", true, false, 1L, "Support", "Closed Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("closed_peer", true, false, 1L, "Support", "Closed Peer", "Ops", "/img/peer.png");
        insertDirectoryUser("closed_observer", true, false, 1L, "Support", "Closed Observer", "Backoffice", "/img/observer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (115, 'token115', 'Quick Actions Closed Collab', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920115L, "T-QA-CLOSED-COLLAB", 115L, "quick_closed_collab_user", "Клиент Closed Collab", "Retail", "Кострома", "Точка Closed Collab", "Проверка closed collaboration errors", "2026-06-05T12:30:00Z", 11501L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-CLOSED-COLLAB", "closed_owner", "dispatcher", "2026-06-05T12:29:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-CLOSED-COLLAB", "closed_peer", "closed_owner");
        insertHistoryRow("T-QA-CLOSED-COLLAB", 920115L, "user", "Клиент ждёт handoff", "2026-06-05T12:31:00Z", "text", 2501L, null, 115L);
        insertHistoryRow("T-QA-CLOSED-COLLAB", 920115L, "operator", "Оператор уже отвечал ранее", "2026-06-05T12:31:30Z", "operator_message", 2500L, 2501L, 115L);

        mockMvc.perform(post("/api/dialogs/T-QA-CLOSED-COLLAB/resolve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["vip"]
                                }
                                """)
                        .principal(new TestingAuthenticationToken("closed_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-CLOSED-COLLAB/participants")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "closed_observer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("closed_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("К закрытому диалогу нельзя добавлять новых участников"));

        mockMvc.perform(post("/api/dialogs/T-QA-CLOSED-COLLAB/reassign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "closed_observer"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("closed_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Переадресовать можно только открытый диалог"));

        mockMvc.perform(get("/api/dialogs/T-QA-CLOSED-COLLAB/workspace")
                        .principal(new TestingAuthenticationToken("closed_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-CLOSED-COLLAB"))
                .andExpect(jsonPath("$.conversation.statusKey").value("closed"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("closed_owner"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(1))
                .andExpect(jsonPath("$.workflow.participants[0].username").value("closed_peer"))
                .andExpect(jsonPath("$.workflow.actions.reassign.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.reassign.disabled_reason").value("closed_dialog"))
                .andExpect(jsonPath("$.workflow.actions.participants_add.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.participants_add.disabled_reason").value("closed_dialog"));

        assertThat(countAuditRows("T-QA-CLOSED-COLLAB", "quick_close", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-CLOSED-COLLAB", "participants_add", "error")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-CLOSED-COLLAB", "reassign", "error")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-QA-CLOSED-COLLAB"
        )).isEqualTo("closed_owner");
    }

    @Test
    void quickActionsApiCollaborationUnknownTargetErrorsStayExplicit() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("unknown_owner", true, false, 1L, "Support", "Unknown Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("unknown_peer", true, false, 1L, "Support", "Unknown Peer", "Ops", "/img/peer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (116, 'token116', 'Quick Actions Unknown Target', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920116L, "T-QA-UNKNOWN-COLLAB", 116L, "quick_unknown_collab_user", "Клиент Unknown Collab", "Retail", "Ярославль", "Точка Unknown Collab", "Проверка unknown-target collaboration errors", "2026-06-05T13:00:00Z", 11601L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-QA-UNKNOWN-COLLAB", "unknown_owner", "dispatcher", "2026-06-05T12:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-QA-UNKNOWN-COLLAB", "unknown_peer", "unknown_owner");
        insertHistoryRow("T-QA-UNKNOWN-COLLAB", 920116L, "user", "Клиент ждёт корректного маршрута", "2026-06-05T13:01:00Z", "text", 2601L, null, 116L);
        insertHistoryRow("T-QA-UNKNOWN-COLLAB", 920116L, "operator", "Оператор уже отвечает", "2026-06-05T13:01:30Z", "operator_message", 2600L, 2601L, 116L);

        mockMvc.perform(post("/api/dialogs/T-QA-UNKNOWN-COLLAB/participants")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "ghost_operator"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("unknown_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Пользователь панели не найден"));

        mockMvc.perform(post("/api/dialogs/T-QA-UNKNOWN-COLLAB/reassign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "ghost_operator"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("unknown_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Пользователь панели не найден"));

        mockMvc.perform(get("/api/dialogs/T-QA-UNKNOWN-COLLAB/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].username").value("unknown_peer"));

        mockMvc.perform(get("/api/dialogs/T-QA-UNKNOWN-COLLAB/workspace")
                        .principal(new TestingAuthenticationToken("unknown_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-UNKNOWN-COLLAB"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("unknown_owner"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(1))
                .andExpect(jsonPath("$.workflow.participants[0].username").value("unknown_peer"));

        mockMvc.perform(get("/api/dialogs")
                        .param("scope", "my_dialogs")
                        .param("bucket", "in_work")
                        .principal(new TestingAuthenticationToken("unknown_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs.length()").value(1))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-UNKNOWN-COLLAB"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("unknown_owner"))
                .andExpect(jsonPath("$.my_dialogs.in_work.length()").value(1))
                .andExpect(jsonPath("$.my_dialogs.in_work[0].ticketId").value("T-QA-UNKNOWN-COLLAB"));

        assertThat(countAuditRows("T-QA-UNKNOWN-COLLAB", "participants_add", "error")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-UNKNOWN-COLLAB", "reassign", "error")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications",
                Integer.class
        )).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-QA-UNKNOWN-COLLAB"
        )).isEqualTo("unknown_owner");
    }

    @Test
    void quickActionsApiTakeCategoriesAndSpamRefreshWorkspaceAndDetailsConsumers() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (103, 'token103', 'Quick Actions Take Spam', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(920103L, "T-QA-TAKE", 103L, "quick_take_user", "Клиент Take", "Retail", "Рязань", "Точка Take", "Проверка take/categories/spam", "2026-06-03T11:00:00Z", 10301L);
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-QA-TAKE", "billing");
        insertHistoryRow("T-QA-TAKE", 920103L, "user", "Клиент просит реакции", "2026-06-03T11:01:00Z", "text", 1301L, null, 103L);

        mockMvc.perform(get("/api/dialogs/T-QA-TAKE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-TAKE"))
                .andExpect(jsonPath("$.workflow.actions.take.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.categories.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.spam.enabled").value(true));

        mockMvc.perform(post("/api/dialogs/T-QA-TAKE/take")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responsible").value("Watcher Owner"));

        mockMvc.perform(post("/api/dialogs/T-QA-TAKE/categories")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["vip", "priority"]
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories", hasItem("vip")))
                .andExpect(jsonPath("$.categories", hasItem("priority")));

        mockMvc.perform(post("/api/dialogs/T-QA-TAKE/spam")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason": "Спам-атака через quick action"
                                }
                                """)
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true))
                .andExpect(jsonPath("$.userId").value("920103"))
                .andExpect(jsonPath("$.categories", hasItem("vip")))
                .andExpect(jsonPath("$.categories", hasItem("priority")))
                .andExpect(jsonPath("$.categories", hasItem("Спам")));

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-QA-TAKE"))
                .andExpect(jsonPath("$.dialogs[0].rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-QA-TAKE"));

        mockMvc.perform(get("/api/dialogs/T-QA-TAKE")
                        .param("channelId", "103")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-TAKE"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories", hasItem("vip")))
                .andExpect(jsonPath("$.categories", hasItem("priority")))
                .andExpect(jsonPath("$.categories", hasItem("Спам")));

        mockMvc.perform(get("/api/dialogs/T-QA-TAKE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-TAKE"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_owner"))
                .andExpect(jsonPath("$.workflow.actions.take.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.take.disabled_reason").value("already_assigned_to_operator"))
                .andExpect(jsonPath("$.workflow.actions.categories.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.spam.enabled").value(true))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("take: success (responsible_assigned)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("categories: success (categories_updated)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("mark_spam: success (blacklisted_user=920103)")));

        mockMvc.perform(get("/api/dialogs/T-QA-TAKE")
                        .param("channelId", "103")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value("T-QA-TAKE"))
                .andExpect(jsonPath("$.summary.rawResponsible").value("watcher_owner"))
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories", hasItem("vip")))
                .andExpect(jsonPath("$.categories", hasItem("priority")))
                .andExpect(jsonPath("$.categories", hasItem("Спам")));

        mockMvc.perform(get("/api/dialogs/T-QA-TAKE/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-TAKE"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_owner"))
                .andExpect(jsonPath("$.workflow.actions.take.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.categories.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.spam.enabled").value(true))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("take: success (responsible_assigned)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("categories: success (categories_updated)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("mark_spam: success (blacklisted_user=920103)")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-QA-TAKE"
        )).isEqualTo("watcher_owner");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_blacklisted FROM client_blacklist WHERE user_id = ?",
                Integer.class,
                "920103"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.query(
                "SELECT category FROM ticket_categories WHERE ticket_id = ? ORDER BY category",
                (rs, rowNum) -> rs.getString(1),
                "T-QA-TAKE"
        )).containsExactly("priority", "vip", "Спам");
        assertThat(countAuditRows("T-QA-TAKE", "take", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-TAKE", "categories", "success")).isEqualTo(1);
        assertThat(countAuditRows("T-QA-TAKE", "mark_spam", "success")).isEqualTo(1);
    }

    private long countAuditRows(String ticketId, String action, String result) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dialog_action_audit WHERE ticket_id = ? AND action = ? AND result = ?",
                Long.class,
                ticketId,
                action,
                result
        );
        return count != null ? count : 0L;
    }

    private void ensureChatHistoryMutationColumns() {
        ensureColumn(jdbcTemplate, "chat_history", "original_message", "TEXT");
        ensureColumn(jdbcTemplate, "chat_history", "edited_at", "TEXT");
        ensureColumn(jdbcTemplate, "chat_history", "deleted_at", "TEXT");
        ensureColumn(jdbcTemplate, "chat_history", "forwarded_from", "TEXT");
    }

    private void ensureUsersDirectoryColumns() {
        ensureColumn(usersJdbcTemplate, "users", "enabled", "BOOLEAN NOT NULL DEFAULT 1");
        ensureColumn(usersJdbcTemplate, "users", "role_id", "INTEGER");
        ensureColumn(usersJdbcTemplate, "users", "role", "TEXT");
        ensureColumn(usersJdbcTemplate, "users", "department", "TEXT");
        ensureColumn(usersJdbcTemplate, "users", "full_name", "TEXT");
        ensureColumn(usersJdbcTemplate, "users", "photo", "TEXT");
        ensureColumn(usersJdbcTemplate, "users", "is_blocked", "BOOLEAN NOT NULL DEFAULT 0");
        ensureColumn(usersJdbcTemplate, "users", "last_portal_activity_at", "TEXT");
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

    private void insertDirectoryUser(String username,
                                     boolean enabled,
                                     boolean blocked,
                                     long roleId,
                                     String role,
                                     String fullName,
                                     String department,
                                     String photo) {
        usersJdbcTemplate.update("""
                INSERT INTO users(username, password, enabled, role_id, role, department, full_name, photo, is_blocked, last_portal_activity_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                username,
                "{noop}test",
                enabled ? 1 : 0,
                roleId,
                role,
                department,
                fullName,
                photo,
                blocked ? 1 : 0,
                "2026-06-03T08:00:00Z");
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
                INSERT INTO chat_history(
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
