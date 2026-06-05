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

        mockMvc.perform(get("/api/dialogs/T-QA-MEDIA/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.ticketId").value("T-QA-MEDIA"))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reply_media: success (media_sent)")));

        assertThat(countAuditRows("T-QA-MEDIA", "reply_media", "success")).isEqualTo(1);
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
