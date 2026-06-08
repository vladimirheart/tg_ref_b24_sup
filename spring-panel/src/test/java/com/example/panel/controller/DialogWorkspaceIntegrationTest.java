package com.example.panel.controller;

import com.example.panel.service.SharedConfigService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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

    @Autowired
    private SharedConfigService sharedConfigService;

    @Autowired
    private DialogQuickActionService dialogQuickActionService;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void clean() {
        ensureChatHistoryMutationColumns();
        ensureUsersDirectoryColumns();
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM ticket_categories");
        jdbcTemplate.update("DELETE FROM ticket_participants");
        jdbcTemplate.update("DELETE FROM ticket_active");
        jdbcTemplate.update("DELETE FROM ticket_responsibles");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM notifications");
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
        sharedConfigService.saveSettings(new LinkedHashMap<>());
    }

    private void ensureChatHistoryMutationColumns() {
        ensureColumn("chat_history", "original_message", "TEXT");
        ensureColumn("chat_history", "edited_at", "TEXT");
        ensureColumn("chat_history", "deleted_at", "TEXT");
        ensureColumn("chat_history", "forwarded_from", "TEXT");
    }

    private void ensureColumn(String tableName, String columnName, String definition) {
        if (loadColumns(tableName).contains(columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private void ensureUsersDirectoryColumns() {
        ensureUsersColumn("enabled", "BOOLEAN NOT NULL DEFAULT 1");
        ensureUsersColumn("role_id", "INTEGER");
        ensureUsersColumn("role", "TEXT");
        ensureUsersColumn("department", "TEXT");
        ensureUsersColumn("full_name", "TEXT");
        ensureUsersColumn("photo", "TEXT");
        ensureUsersColumn("is_blocked", "BOOLEAN NOT NULL DEFAULT 0");
        ensureUsersColumn("last_portal_activity_at", "TEXT");
    }

    private void ensureUsersColumn(String columnName, String definition) {
        if (loadUsersColumns().contains(columnName)) {
            return;
        }
        usersJdbcTemplate.execute("ALTER TABLE users ADD COLUMN " + columnName + " " + definition);
    }

    private Set<String> loadColumns(String tableName) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                "PRAGMA table_info(" + tableName + ")",
                (rs, rowNum) -> rs.getString("name")
        ));
    }

    private Set<String> loadUsersColumns() {
        return new LinkedHashSet<>(usersJdbcTemplate.query(
                "PRAGMA table_info(users)",
                (rs, rowNum) -> rs.getString("name")
        ));
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

    @Test
    void workspaceApiProjectsSettingsDrivenContextContractViolationsAndPlaybooks() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.ofEntries(
                        Map.entry("workspace_required_client_attributes", List.of("phone")),
                        Map.entry("workspace_client_attribute_labels", Map.of("phone", "Телефон")),
                        Map.entry("workspace_client_context_required_sources", List.of("crm")),
                        Map.entry("workspace_client_context_source_priority", List.of("crm", "local")),
                        Map.entry("workspace_client_context_source_labels", Map.of("crm", "CRM")),
                        Map.entry("workspace_client_crm_profile_url_template", "https://crm.example.local/profile/{user_id}"),
                        Map.entry("workspace_context_block_priority", List.of("customer_profile", "context_sources", "history")),
                        Map.entry("workspace_context_block_required", List.of("customer_profile", "context_sources")),
                        Map.entry("workspace_rollout_context_contract_required", true),
                        Map.entry("workspace_rollout_context_contract_scenarios", List.of("billing")),
                        Map.entry("workspace_rollout_context_contract_mandatory_fields", List.of("phone")),
                        Map.entry("workspace_rollout_context_contract_source_of_truth", List.of("phone:crm")),
                        Map.entry("workspace_rollout_context_contract_priority_blocks", List.of("customer_profile", "context_sources")),
                        Map.entry("workspace_rollout_context_contract_playbooks", Map.of(
                                "mandatory_field:phone", Map.of(
                                        "label", "Phone recovery",
                                        "url", "https://wiki.example.local/context/phone",
                                        "summary", "Запросить телефон у клиента и обновить CRM"),
                                "source_of_truth", Map.of(
                                        "label", "Source guide",
                                        "url", "https://wiki.example.local/context/source",
                                        "summary", "Как проверить source-of-truth"),
                                "priority_block:customer_profile", Map.of(
                                        "label", "Profile block guide",
                                        "url", "https://wiki.example.local/context/profile-block",
                                        "summary", "Как вернуть блок customer profile")))
                )));
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (83, 'token83', 'Workspace Contract', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910093L, "T-WS-CONTRACT", 83L, "contract_user", "Клиент Contract", "B2B", "Самара", "Офис", "Нужен context contract", "2026-05-25T12:00:00Z", 8301L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-CONTRACT", "watcher_owner", "dispatcher", "2026-05-25T11:59:00Z");
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-WS-CONTRACT", "billing");
        insertHistoryRow("T-WS-CONTRACT", 910093L, "user", "Клиент просит помощь", "2026-05-25T12:01:00Z", "text", 931L, null, 83L, null);

        mockMvc.perform(get("/api/dialogs/T-WS-CONTRACT/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.context.contract.enabled").value(true))
                .andExpect(jsonPath("$.context.contract.ready").value(false))
                .andExpect(jsonPath("$.context.contract.active_scenarios[0]").value("billing"))
                .andExpect(jsonPath("$.context.contract.missing_mandatory_fields[0]").value("phone"))
                .andExpect(jsonPath("$.context.contract.source_of_truth_violations[0]").value("phone:crm:field_not_matched"))
                .andExpect(jsonPath("$.context.contract.missing_priority_blocks[0]").value("customer_profile"))
                .andExpect(jsonPath("$.context.contract.operator_summary").value("Сначала заполните обязательные поля клиента."))
                .andExpect(jsonPath("$.context.contract.next_step_summary").value("Сначала дозаполните поля: phone."))
                .andExpect(jsonPath("$.context.contract.primary_violation_details.length()").value(2))
                .andExpect(jsonPath("$.context.contract.deferred_violation_count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.context.contract.violation_details[*].code", hasItem("mandatory_field:phone")))
                .andExpect(jsonPath("$.context.contract.violation_details[*].code", hasItem("source_of_truth:phone:crm:field_not_matched")))
                .andExpect(jsonPath("$.context.contract.violation_details[*].code", hasItem("priority_block:customer_profile")))
                .andExpect(jsonPath("$.context.contract.violation_details[0].playbook.label").value("Phone recovery"))
                .andExpect(jsonPath("$.context.contract.violation_details[1].playbook.label").value("Source guide"))
                .andExpect(jsonPath("$.context.contract.violation_details[*].playbook.label", hasItem("Profile block guide")))
                .andExpect(jsonPath("$.context.blocks_health.ready").value(false))
                .andExpect(jsonPath("$.context.context_sources[*].key", hasItem("crm")))
                .andExpect(jsonPath("$.context.context_sources[*].status", hasItem("invalid_utc")))
                .andExpect(jsonPath("$.context.blocks[*].key", hasItem("customer_profile")))
                .andExpect(jsonPath("$.context.blocks[*].ready", hasItem(false)));
    }

    @Test
    void workspaceApiAppliesRelatedProjectionLimitsAndParityDegradationForPartialInclude() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.of(
                        "workspace_inline_navigation", false,
                        "workspace_context_history_limit", 1,
                        "workspace_context_related_events_limit", 2
                )
        ));
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (84, 'token84', 'Workspace Parity', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910094L, "T-WS-PARITY", 84L, "parity_user", "Клиент Parity", "Retail", "Тула", "Флагман", "Нужен runtime parity", "2026-05-25T13:00:00Z", 8401L);
        insertDialogTicket(910094L, "T-WS-PREV-2", 84L, "parity_user", "Клиент Parity", "Retail", "Тула", "Архив 2", "Более новый прошлый диалог", "2026-05-24T13:00:00Z", 8402L);
        insertDialogTicket(910094L, "T-WS-PREV-1", 84L, "parity_user", "Клиент Parity", "Retail", "Тула", "Архив 1", "Старый прошлый диалог", "2026-05-23T13:00:00Z", 8403L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-PARITY", "watcher_owner", "dispatcher", "2026-05-25T12:59:00Z");
        insertHistoryRow("T-WS-PARITY", 910094L, "user", "Первое сообщение parity", "2026-05-25T13:01:00Z", "text", 941L, null, 84L, null);
        insertHistoryRow("T-WS-PARITY", 910094L, "operator", "Ответ для parity", "2026-05-25T13:02:00Z", "text", 942L, 941L, 84L, null);
        insertHistoryRow("T-WS-PARITY", 910094L, "system", "Системное событие parity", "2026-05-25T13:03:00Z", "event", 943L, null, 84L, null);
        jdbcTemplate.update("""
                INSERT INTO dialog_action_audit(ticket_id, actor, action, result, detail, created_at)
                VALUES (?,?,?,?,?,?)
                """,
                "T-WS-PARITY", "watcher_owner", "reply", "success", "audit_tail", "2026-05-25T13:05:00Z");
        jdbcTemplate.update("""
                INSERT INTO tasks(id, seq, title, assignee, creator, status, last_activity_at, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                84001L, 1L, "Parity task", "watcher_owner", "dispatcher", "Новая", "2026-05-25T13:04:00Z", "2026-05-25T13:00:30Z");
        jdbcTemplate.update("""
                INSERT INTO task_links(user_id, ticket_id, task_id)
                VALUES (?,?,?)
                """,
                910094L, "T-WS-PARITY", 84001L);
        jdbcTemplate.update("""
                INSERT INTO task_history(id, task_id, at, text)
                VALUES (?,?,?,?)
                """,
                84011L, 84001L, "2026-05-25T13:04:00Z", "Workflow escalation note");

        mockMvc.perform(get("/api/dialogs/T-WS-PARITY/workspace")
                        .param("include", "context,permissions")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.unavailable").value(true))
                .andExpect(jsonPath("$.messages.items.length()").value(0))
                .andExpect(jsonPath("$.context.history.length()").value(1))
                .andExpect(jsonPath("$.context.history[0].ticket_id").value("T-WS-PREV-2"))
                .andExpect(jsonPath("$.context.related_events.length()").value(2))
                .andExpect(jsonPath("$.context.related_events[*].type", hasItem("audit")))
                .andExpect(jsonPath("$.context.related_events[*].type", hasItem("workflow")))
                .andExpect(jsonPath("$.permissions.can_reply").value(true))
                .andExpect(jsonPath("$.composer.reply_supported").value(true))
                .andExpect(jsonPath("$.composer.reply_target_supported").value(true))
                .andExpect(jsonPath("$.sla.unavailable").value(true))
                .andExpect(jsonPath("$.sla.state").value("unknown"))
                .andExpect(jsonPath("$.meta.navigation.enabled").value(false))
                .andExpect(jsonPath("$.meta.navigation.summary").value("Inline navigation отключена текущей настройкой rollout."))
                .andExpect(jsonPath("$.meta.parity.status").value("attention"))
                .andExpect(jsonPath("$.meta.parity.missing_capabilities", hasItem("messages_timeline")))
                .andExpect(jsonPath("$.meta.parity.missing_capabilities", hasItem("sla_visibility")))
                .andExpect(jsonPath("$.meta.parity.checks[*].key", hasItem("history_context")))
                .andExpect(jsonPath("$.meta.parity.checks[*].key", hasItem("related_events")));
    }

    @Test
    void workspaceApiBlocksOperatorParityWhenDialogPermissionsAreMissing() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (85, 'token85', 'Workspace Blocked', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910095L, "T-WS-BLOCKED", 85L, "blocked_user", "Клиент Blocked", "Retail", "Курск", "Точка", "Нужен blocked parity contract", "2026-05-26T09:00:00Z", 8501L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-BLOCKED", "viewer_only", "dispatcher", "2026-05-26T08:59:00Z");
        insertHistoryRow("T-WS-BLOCKED", 910095L, "user", "Сообщение без thread target", "2026-05-26T09:01:00Z", "text", 951L, null, 85L, null);
        insertHistoryRow("T-WS-BLOCKED", 910095L, "operator", "Ответ без tg target", "2026-05-26T09:02:00Z", "text", 0L, null, 85L, null);

        mockMvc.perform(get("/api/dialogs/T-WS-BLOCKED/workspace")
                        .param("include", "messages,context,permissions,sla")
                        .principal(new TestingAuthenticationToken("viewer_only", "n/a", "PAGE_USERS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.permissions.can_reply").value(false))
                .andExpect(jsonPath("$.permissions.can_assign").value(false))
                .andExpect(jsonPath("$.permissions.can_close").value(false))
                .andExpect(jsonPath("$.permissions.can_snooze").value(false))
                .andExpect(jsonPath("$.composer.reply_supported").value(false))
                .andExpect(jsonPath("$.composer.media_supported").value(false))
                .andExpect(jsonPath("$.composer.reply_target_supported").value(false))
                .andExpect(jsonPath("$.messages.items.length()").value(2))
                .andExpect(jsonPath("$.meta.parity.status").value("attention"))
                .andExpect(jsonPath("$.meta.parity.missing_capabilities", hasItem("reply_threading")))
                .andExpect(jsonPath("$.meta.parity.missing_capabilities", hasItem("media_reply")))
                .andExpect(jsonPath("$.meta.parity.checks[*].status", hasItem("attention")));
    }

    @Test
    void workspaceApiProjectsRichTimelineMutationAndAttachmentFields() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (86, 'token86', 'Workspace Rich Timeline', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910096L, "T-WS-RICH", 86L, "rich_user", "Клиент Rich", "Retail", "Тверь", "Шоурум", "Нужен timeline payload", "2026-05-26T10:00:00Z", 8601L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-RICH", "watcher_owner", "dispatcher", "2026-05-26T09:59:00Z");
        insertHistoryRow("T-WS-RICH", 910096L, "user", "", "2026-05-26T10:01:00Z", "image", 961L, null, 86L,
                "attachments/T-WS-RICH/client photo.jpg", null, null, null, null);
        insertHistoryRow("T-WS-RICH", 910096L, "operator", "Обновлённый ответ", "2026-05-26T10:02:00Z", "image", 962L, 961L, 86L,
                "reply.png", "Изначальный ответ", "2026-05-26T10:02:30Z", null, "lead");
        insertHistoryRow("T-WS-RICH", 910096L, "user", "Скрытое удалённое сообщение", "2026-05-26T10:03:00Z", "text", 963L, 962L, 86L,
                null, "Скрытое удалённое сообщение", null, "2026-05-26T10:03:30Z", null);

        mockMvc.perform(get("/api/dialogs/T-WS-RICH/workspace")
                        .param("include", "messages,context,permissions,sla")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages.items.length()").value(3))
                .andExpect(jsonPath("$.messages.items[0].attachment").value("/api/attachments/tickets/by-path?path=attachments/T-WS-RICH/client%20photo.jpg"))
                .andExpect(jsonPath("$.messages.items[1].replyPreview").value("Изображение"))
                .andExpect(jsonPath("$.messages.items[1].originalMessage").value("Изначальный ответ"))
                .andExpect(jsonPath("$.messages.items[1].attachment").value("/api/attachments/tickets/T-WS-RICH/reply.png"))
                .andExpect(jsonPath("$.messages.items[1].editedAt").value("2026-05-26T10:02:30Z"))
                .andExpect(jsonPath("$.messages.items[1].forwardedFrom").value("lead"))
                .andExpect(jsonPath("$.messages.items[2].message").value(""))
                .andExpect(jsonPath("$.messages.items[2].originalMessage").value("Скрытое удалённое сообщение"))
                .andExpect(jsonPath("$.messages.items[2].deletedAt").value("2026-05-26T10:03:30Z"))
                .andExpect(jsonPath("$.messages.items[2].replyPreview").value("Обновлённый ответ"))
                .andExpect(jsonPath("$.permissions.can_reply").value(true))
                .andExpect(jsonPath("$.composer.media_supported").value(true))
                .andExpect(jsonPath("$.meta.parity.status").value("ok"));
    }

    @Test
    void workspaceApiProjectsRolloutGovernanceAndLegacyManualFallbackPolicy() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.ofEntries(
                        Map.entry("workspace_v1", true),
                        Map.entry("workspace_rollout_governance_packet_required", true),
                        Map.entry("workspace_rollout_governance_owner_signoff_required", true),
                        Map.entry("workspace_rollout_governance_review_cadence_days", 7),
                        Map.entry("workspace_rollout_governance_review_decision_required", true),
                        Map.entry("workspace_rollout_governance_incident_followup_required", true),
                        Map.entry("workspace_rollout_governance_followup_for_non_go_required", true),
                        Map.entry("workspace_rollout_governance_parity_exit_days", 14),
                        Map.entry("workspace_rollout_governance_parity_critical_reasons", List.of("attachments_edit", "inline_reopen")),
                        Map.entry("workspace_rollout_governance_legacy_only_scenarios", List.of("attachments_edit")),
                        Map.entry("workspace_ab_enabled", true),
                        Map.entry("workspace_ab_rollout_percent", 35),
                        Map.entry("workspace_ab_experiment_name", "workspace-q2"),
                        Map.entry("workspace_ab_operator_segment", "night_shift"),
                        Map.entry("workspace_rollout_legacy_manual_open_policy_enabled", true),
                        Map.entry("workspace_rollout_legacy_manual_open_block_on_stale_review", true),
                        Map.entry("workspace_rollout_legacy_manual_open_review_ttl_hours", 24),
                        Map.entry("workspace_rollout_governance_legacy_usage_reviewed_by", "ops.lead"),
                        Map.entry("workspace_rollout_governance_legacy_usage_review_note", "Weekly review"),
                        Map.entry("workspace_rollout_governance_legacy_usage_reviewed_at", "2020-01-01T00:00:00Z"),
                        Map.entry("workspace_rollout_governance_legacy_usage_decision_required", true),
                        Map.entry("workspace_rollout_legacy_manual_open_reason_catalog_required", true),
                        Map.entry("workspace_rollout_legacy_manual_open_allowed_reasons", List.of("attachments_edit", "inline_reopen")),
                        Map.entry("workspace_rollout_external_kpi_gate_enabled", true),
                        Map.entry("workspace_rollout_external_kpi_omnichannel_ready", true),
                        Map.entry("workspace_rollout_external_kpi_finance_ready", false),
                        Map.entry("workspace_rollout_external_kpi_reviewed_by", "release-oncall"),
                        Map.entry("workspace_rollout_external_kpi_reviewed_at", "2026-05-26T08:00:00Z"),
                        Map.entry("workspace_rollout_external_kpi_review_ttl_hours", 72),
                        Map.entry("workspace_rollout_external_kpi_note", "finance pipeline pending"),
                        Map.entry("workspace_rollout_external_kpi_datamart_program_status", "blocked"),
                        Map.entry("workspace_rollout_external_kpi_datamart_program_note", "awaiting contract sync")
                )));
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (87, 'token87', 'Workspace Rollout', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910097L, "T-WS-ROLLOUT", 87L, "rollout_user", "Клиент Rollout", "Retail", "Пермь", "Смена", "Нужен rollout contract", "2026-05-26T11:00:00Z", 8701L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-ROLLOUT", "watcher_owner", "dispatcher", "2026-05-26T10:59:00Z");
        insertHistoryRow("T-WS-ROLLOUT", 910097L, "user", "Сообщение для rollout", "2026-05-26T11:01:00Z", "text", 971L, null, 87L, null);

        mockMvc.perform(get("/api/dialogs/T-WS-ROLLOUT/workspace")
                        .param("include", "messages,permissions")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.meta.rollout.mode").value("cohort_rollout"))
                .andExpect(jsonPath("$.meta.rollout.banner_tone").value("info"))
                .andExpect(jsonPath("$.meta.rollout.summary").value("Workspace включён в cohort-rollout; legacy modal остаётся fallback-механизмом."))
                .andExpect(jsonPath("$.meta.rollout.rollout_percent").value(35))
                .andExpect(jsonPath("$.meta.rollout.experiment_name").value("workspace-q2"))
                .andExpect(jsonPath("$.meta.rollout.operator_segment").value("night_shift"))
                .andExpect(jsonPath("$.meta.rollout.legacy_fallback_available").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.enabled").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.blocked").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.block_reason").value("stale_review"))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.reviewed_by").value("ops.lead"))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.review_note").value("Weekly review"))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.reason_catalog_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.allowed_reasons[0]").value("attachments_edit"))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.allowed_reasons[1]").value("inline_reopen"))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.review_age_hours").isNotEmpty())
                .andExpect(jsonPath("$.meta.rollout.external_kpi_signal.enabled").value(true))
                .andExpect(jsonPath("$.meta.rollout.external_kpi_signal.ready_for_decision").value(false))
                .andExpect(jsonPath("$.meta.rollout.external_kpi_signal.reviewed_by").value("release-oncall"))
                .andExpect(jsonPath("$.meta.rollout.external_kpi_signal.reviewed_at").value("2026-05-26T08:00Z"))
                .andExpect(jsonPath("$.meta.rollout.external_kpi_signal.note").value("finance pipeline pending"))
                .andExpect(jsonPath("$.meta.rollout.external_kpi_signal.datamart_program_status").value("blocked"))
                .andExpect(jsonPath("$.meta.rollout.external_kpi_signal.datamart_risk_level").isNotEmpty())
                .andExpect(jsonPath("$.meta.rollout.governance.packet_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.governance.owner_signoff_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.governance.review_cadence_days").value(7))
                .andExpect(jsonPath("$.meta.rollout.governance.review_decision_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.governance.incident_followup_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.governance.followup_after_non_go_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.governance.parity_exit_days").value(14))
                .andExpect(jsonPath("$.meta.rollout.governance.parity_critical_reasons[0]").value("attachments_edit"))
                .andExpect(jsonPath("$.meta.rollout.governance.parity_critical_reasons[1]").value("inline_reopen"))
                .andExpect(jsonPath("$.meta.rollout.governance.legacy_only_scenarios[0]").value("attachments_edit"))
                .andExpect(jsonPath("$.meta.rollout.governance.legacy_manual_allowed_reasons[0]").value("attachments_edit"))
                .andExpect(jsonPath("$.meta.rollout.governance.legacy_manual_allowed_reasons[1]").value("inline_reopen"))
                .andExpect(jsonPath("$.meta.rollout.governance.legacy_manual_reason_catalog_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.governance.legacy_usage_decision_required").value(true))
                .andExpect(jsonPath("$.meta.parity.status").value("attention"));
    }

    @Test
    void workspaceApiProjectsOperatorWorkflowSnapshotWithCollaborationAndTriagePreferences() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.of(
                        "workspace_triage_preferences_by_operator", Map.of(
                                "watcher_owner", Map.of(
                                        "view", "SLA_CRITICAL",
                                        "sort_mode", "unknown",
                                        "sla_window_minutes", 30,
                                        "page_size", "all",
                                        "updated_at_utc", "2026-05-26T15:00:00+03:00"
                                )
                        )
                )
        ));
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("watcher_peer", true, false, 1L, "Support", "Watcher Peer", "Ops", "/img/peer.png");
        insertDirectoryUser("watcher_observer", true, false, 1L, "Support", "Watcher Observer", "Backoffice", "/img/observer.png");
        insertDirectoryUser("watcher_new", true, false, 1L, "Support", "Watcher New", "Ops", "/img/new.png");
        insertDirectoryUser("watcher_disabled", false, false, 1L, "Support", "Watcher Disabled", "Ops", "/img/disabled.png");
        insertDirectoryUser("watcher_blocked", true, true, 1L, "Support", "Watcher Blocked", "Ops", "/img/blocked.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (88, 'token88', 'Workspace Workflow', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910098L, "T-WS-WORKFLOW", 88L, "workflow_user", "Клиент Workflow", "Retail", "Рязань", "Филиал", "Нужен operator workflow snapshot", "2026-05-26T12:00:00Z", 8801L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-WORKFLOW", "watcher_owner", "dispatcher", "2026-05-26T11:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-WS-WORKFLOW", "watcher_peer", "watcher_owner");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-WS-WORKFLOW", "watcher_observer", "watcher_owner");
        insertHistoryRow("T-WS-WORKFLOW", 910098L, "user", "Сообщение для workflow snapshot", "2026-05-26T12:01:00Z", "text", 981L, null, 88L, null);

        mockMvc.perform(get("/api/dialogs/T-WS-WORKFLOW/workspace")
                        .param("include", "messages,permissions")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.workflow.responsible.assigned").value(true))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_owner"))
                .andExpect(jsonPath("$.workflow.responsible.display_name").value("Watcher Owner"))
                .andExpect(jsonPath("$.workflow.responsible.avatar_url").value("/img/owner.png"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(2))
                .andExpect(jsonPath("$.workflow.participants[*].username", hasItem("watcher_peer")))
                .andExpect(jsonPath("$.workflow.participants[*].username", hasItem("watcher_observer")))
                .andExpect(jsonPath("$.workflow.reassign_candidates[*].username", hasItem("watcher_peer")))
                .andExpect(jsonPath("$.workflow.reassign_candidates[*].username", hasItem("watcher_new")))
                .andExpect(jsonPath("$.workflow.reassign_candidates[*].username", org.hamcrest.Matchers.not(hasItem("watcher_owner"))))
                .andExpect(jsonPath("$.workflow.participant_candidates.length()").value(1))
                .andExpect(jsonPath("$.workflow.participant_candidates[0].username").value("watcher_new"))
                .andExpect(jsonPath("$.workflow.triage_preferences.view").value("sla_critical"))
                .andExpect(jsonPath("$.workflow.triage_preferences.sort_mode").value("default"))
                .andExpect(jsonPath("$.workflow.triage_preferences.sla_window_minutes").value(30))
                .andExpect(jsonPath("$.workflow.triage_preferences.page_size").value("all"))
                .andExpect(jsonPath("$.workflow.triage_preferences.updated_at_utc").isNotEmpty())
                .andExpect(jsonPath("$.workflow.actions.reply.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reply_media.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.take.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.take.disabled_reason").value("already_assigned_to_operator"))
                .andExpect(jsonPath("$.workflow.actions.resolve.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reopen.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.reopen.disabled_reason").value("not_closed"))
                .andExpect(jsonPath("$.workflow.actions.categories.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.spam.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reassign.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.participants_add.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.participants_remove.enabled").value(true))
                .andExpect(jsonPath("$.workflow.collaboration.assigned").value(true))
                .andExpect(jsonPath("$.workflow.collaboration.participant_count").value(2))
                .andExpect(jsonPath("$.workflow.collaboration.can_reassign").value(true))
                .andExpect(jsonPath("$.workflow.collaboration.can_manage_participants").value(true))
                .andExpect(jsonPath("$.workflow.collaboration.reassign_candidate_count").value(3))
                .andExpect(jsonPath("$.workflow.collaboration.participant_candidate_count").value(1))
                .andExpect(jsonPath("$.meta.parity.checks[*].key", hasItem("operator_workflow_projection")))
                .andExpect(jsonPath("$.meta.parity.checks[*].key", hasItem("operator_action_guards")))
                .andExpect(jsonPath("$.meta.parity.status").value("attention"));
    }

    @Test
    void workspaceApiRefreshesWorkflowActionsAcrossReassignResolveReopenAndParticipantRemovalLifecycle() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("watcher_new", true, false, 1L, "Support", "Watcher New", "Ops", "/img/new.png");
        insertDirectoryUser("watcher_peer", true, false, 1L, "Support", "Watcher Peer", "Ops", "/img/peer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (89, 'token89', 'Workspace Action Runtime', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910099L, "T-WS-ACTION", 89L, "action_user", "Клиент Action", "Retail", "Орел", "Офис", "Нужен action continuity", "2026-05-26T13:00:00Z", 8901L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-ACTION", "watcher_owner", "dispatcher", "2026-05-26T12:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-WS-ACTION", "watcher_peer", "watcher_owner");
        insertHistoryRow("T-WS-ACTION", 910099L, "user", "Сообщение для action continuity", "2026-05-26T13:01:00Z", "text", 991L, null, 89L, null);

        dialogQuickActionService.reassignTicket("T-WS-ACTION", "watcher_new", "watcher_owner");
        dialogQuickActionService.resolveTicket("T-WS-ACTION", "watcher_new", List.of("billing"));

        mockMvc.perform(get("/api/dialogs/T-WS-ACTION/workspace")
                        .param("include", "messages,permissions,sla")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_new"))
                .andExpect(jsonPath("$.workflow.responsible.display_name").value("Watcher New"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(1))
                .andExpect(jsonPath("$.workflow.participants[0].username").value("watcher_peer"))
                .andExpect(jsonPath("$.workflow.actions.take.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.take.disabled_reason").value("closed_dialog"))
                .andExpect(jsonPath("$.workflow.actions.resolve.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.resolve.disabled_reason").value("already_closed"))
                .andExpect(jsonPath("$.workflow.actions.reopen.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reopen.disabled_reason").doesNotExist())
                .andExpect(jsonPath("$.workflow.actions.categories.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.spam.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.snooze.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.snooze.disabled_reason").value("closed_dialog"))
                .andExpect(jsonPath("$.workflow.actions.reassign.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.reassign.disabled_reason").value("closed_dialog"))
                .andExpect(jsonPath("$.workflow.actions.participants_add.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.participants_add.disabled_reason").value("closed_dialog"))
                .andExpect(jsonPath("$.workflow.actions.participants_remove.enabled").value(true))
                .andExpect(jsonPath("$.workflow.collaboration.can_reassign").value(false))
                .andExpect(jsonPath("$.workflow.collaboration.can_manage_participants").value(false))
                .andExpect(jsonPath("$.workflow.reassign_candidates[*].username", hasItem("watcher_owner")))
                .andExpect(jsonPath("$.workflow.participant_candidates[*].username", hasItem("watcher_owner")))
                .andExpect(jsonPath("$.conversation.statusKey").value("closed"))
                .andExpect(jsonPath("$.meta.parity.checks[*].key", hasItem("operator_action_guards")));

        dialogQuickActionService.reopenTicket("T-WS-ACTION", "watcher_new");
        dialogQuickActionService.removeParticipant("T-WS-ACTION", "watcher_peer", "watcher_new");

        mockMvc.perform(get("/api/dialogs/T-WS-ACTION/workspace")
                        .param("include", "messages,permissions,sla")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_new"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(0))
                .andExpect(jsonPath("$.workflow.actions.resolve.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reopen.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.reopen.disabled_reason").value("not_closed"))
                .andExpect(jsonPath("$.workflow.actions.categories.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.spam.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.reassign.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.participants_add.enabled").value(true))
                .andExpect(jsonPath("$.workflow.actions.participants_remove.enabled").value(false))
                .andExpect(jsonPath("$.workflow.actions.participants_remove.disabled_reason").value("no_participants"))
                .andExpect(jsonPath("$.workflow.collaboration.participant_count").value(0))
                .andExpect(jsonPath("$.workflow.collaboration.can_reassign").value(true))
                .andExpect(jsonPath("$.workflow.collaboration.can_manage_participants").value(true))
                .andExpect(jsonPath("$.workflow.participant_candidates[*].username", hasItem("watcher_owner")))
                .andExpect(jsonPath("$.workflow.participant_candidates[*].username", hasItem("watcher_peer")))
                .andExpect(jsonPath("$.workflow.reassign_candidates[*].username", hasItem("watcher_owner")))
                .andExpect(jsonPath("$.workflow.reassign_candidates[*].username", hasItem("watcher_peer")))
                .andExpect(jsonPath("$.conversation.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.meta.parity.checks[*].key", hasItem("operator_action_guards")));
    }

    @Test
    void workspaceApiProjectsAuditRelatedEventsAfterHttpQuickActionLifecycle() throws Exception {
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");
        insertDirectoryUser("watcher_new", true, false, 1L, "Support", "Watcher New", "Ops", "/img/new.png");
        insertDirectoryUser("watcher_peer", true, false, 1L, "Support", "Watcher Peer", "Ops", "/img/peer.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (90, 'token90', 'Workspace Audit Trail', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910100L, "T-WS-AUDIT", 90L, "audit_user", "Клиент Audit", "Retail", "Тверь", "Филиал", "Проверка audit trail", "2026-05-26T14:00:00Z", 9001L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-AUDIT", "watcher_owner", "dispatcher", "2026-05-26T13:59:00Z");
        jdbcTemplate.update("""
                INSERT INTO ticket_participants(ticket_id, username, added_at, added_by)
                VALUES (?,?,CURRENT_TIMESTAMP,?)
                """,
                "T-WS-AUDIT", "watcher_peer", "watcher_owner");
        insertHistoryRow("T-WS-AUDIT", 910100L, "user", "Сообщение для audit continuity", "2026-05-26T14:01:00Z", "text", 1001L, null, 90L, null);

        mockMvc.perform(post("/api/dialogs/T-WS-AUDIT/reassign")
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

        mockMvc.perform(post("/api/dialogs/T-WS-AUDIT/resolve")
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

        mockMvc.perform(delete("/api/dialogs/T-WS-AUDIT/participants/watcher_peer")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(true));

        mockMvc.perform(get("/api/dialogs/T-WS-AUDIT/workspace")
                        .principal(new TestingAuthenticationToken("watcher_new", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.conversation.statusKey").value("closed"))
                .andExpect(jsonPath("$.workflow.responsible.username").value("watcher_new"))
                .andExpect(jsonPath("$.workflow.participants.length()").value(0))
                .andExpect(jsonPath("$.context.related_events[*].type", hasItem("audit")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("reassign: success (responsible_redirected)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("quick_close: success (updated)")))
                .andExpect(jsonPath("$.context.related_events[*].detail", hasItem("participants_remove: success (participant_removed)")));
    }

    @Test
    void workspaceApiRefreshesDialogUnreadLoopWithoutImplicitlyAckingBellNotifications() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.of("sla_target_minutes", 1000000)
        ));
        usersJdbcTemplate.update("INSERT INTO roles(id, name) VALUES (?, ?)", 1L, "Support");
        insertDirectoryUser("watcher_owner", true, false, 1L, "Support", "Watcher Owner", "Ops", "/img/owner.png");

        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at)
                VALUES (91, 'token91w', 'Workspace Notify', 'telegram', 1, CURRENT_TIMESTAMP)
                """);
        insertDialogTicket(910101L, "T-WS-NOTIFY", 91L, "workspace_notify_user", "Клиент Workspace Notify", "Retail", "Тверь", "Точка", "Проверка workspace notification loop", "2026-05-27T09:40:00Z", 9101L);
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at)
                VALUES (?,?,?,?)
                """,
                "T-WS-NOTIFY", "watcher_owner", "dispatcher", "2026-05-27T09:39:00Z");
        insertHistoryRow("T-WS-NOTIFY", 910101L, "user", "Follow-up для workspace route", "2026-05-27T09:43:00Z", "text", 911L, null, 91L, null);

        notificationService.notifyDialogParticipants(
                "T-WS-NOTIFY",
                "Новое сообщение в обращении T-WS-NOTIFY",
                "/dialogs?ticketId=T-WS-NOTIFY",
                null
        );

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-WS-NOTIFY"))
                .andExpect(jsonPath("$.dialogs[0].unreadCount").value(1));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(get("/api/dialogs/T-WS-NOTIFY/workspace")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.conversation.ticketId").value("T-WS-NOTIFY"))
                .andExpect(jsonPath("$.conversation.statusKey").value("waiting_operator"))
                .andExpect(jsonPath("$.messages.items[0].message").value("Follow-up для workspace route"));

        String lastReadAt = jdbcTemplate.queryForObject(
                "SELECT last_read_at FROM ticket_responsibles WHERE ticket_id = ? AND responsible = ?",
                String.class,
                "T-WS-NOTIFY",
                "watcher_owner"
        );
        assertThat(lastReadAt).isEqualTo("2026-05-27T09:43:00Z");

        mockMvc.perform(get("/api/dialogs")
                        .principal(new TestingAuthenticationToken("watcher_owner", "n/a", "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-WS-NOTIFY"))
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
                "2026-05-26T12:00:00Z");
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
        insertHistoryRow(ticketId, userId, sender, message, timestamp, messageType, telegramMessageId, replyToTelegramId, channelId,
                attachment, null, null, null, null);
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
