package com.example.panel.service;

import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeArticleSummary;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("sqlite")
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupportPanelIntegrationTests {

    private static Path dbFile;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-test", ".db");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DialogService dialogService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private PublicFormService publicFormService;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM task_history");
        jdbcTemplate.update("DELETE FROM task_links");
        jdbcTemplate.update("DELETE FROM task_people");
        jdbcTemplate.update("DELETE FROM task_comments");
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM task_seq");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM client_statuses");
        jdbcTemplate.update("DELETE FROM channels");
        jdbcTemplate.update("DELETE FROM knowledge_article_files");
        jdbcTemplate.update("DELETE FROM knowledge_articles");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM workspace_telemetry_audit");
    }

    @Test
    void dialogServiceAggregatesStatsAndHistory() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at) VALUES (1, 'token', 'Demo', 1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (?,?,?,?)",
                1001L, "T-1", "pending", 1);
        jdbcTemplate.update("INSERT INTO messages (group_msg_id, user_id, business, city, location_name, problem, created_at, username, ticket_id, created_date, created_time, client_name, client_status, updated_at, updated_by, channel_id) " +
                        "VALUES (NULL, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, DATE('now'), TIME('now'), ?, ?, CURRENT_TIMESTAMP, 'tester', ?)",
                1001L, "Food", "Москва", "Пиццерия", "Не работает терминал", "ivan", "T-1", "Иван", "VIP", 1);
        jdbcTemplate.update("INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, channel_id) VALUES (?,?,?,?,?,?,?)",
                1001L, "user", "Добрый день", OffsetDateTime.now().toString(), "T-1", "text", 1);

        DialogSummary summary = dialogService.loadSummary();
        assertThat(summary.totalTickets()).isEqualTo(1);
        assertThat(summary.pendingTickets()).isEqualTo(1);
        assertThat(summary.channelStats()).extracting("name").contains("Demo");

        DialogDetails details = dialogService.loadDialogDetails("T-1", 1L, null).orElseThrow();
        assertThat(details.summary().clientName()).isEqualTo("Иван");
        assertThat(details.history()).hasSize(1);
    }

    @Test
    void dialogListIncludesTicketsWithoutMessages() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at) VALUES (3, 'token-3', 'Fallback', 1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id, status, channel_id, created_at) VALUES (?,?,?,?,?)",
                2002L, "T-NOMSG", "open", 3, "2026-01-01T08:30:00");

        DialogSummary summary = dialogService.loadSummary();
        assertThat(summary.totalTickets()).isEqualTo(1);

        assertThat(dialogService.loadDialogs(null))
                .extracting("ticketId")
                .contains("T-NOMSG");

        DialogDetails details = dialogService.loadDialogDetails("T-NOMSG", 3L, null).orElseThrow();
        assertThat(details.summary().ticketId()).isEqualTo("T-NOMSG");
        assertThat(details.summary().channelId()).isEqualTo(3L);
    }

    @Test
    void knowledgeBaseServiceSavesAndListsArticles() {
        KnowledgeArticleCommand command = new KnowledgeArticleCommand(null, "Инструкция", "Поддержка",
                "guide", "draft", "Оператор", "IT", "Сеть", "Кратко", "Подробное описание");
        KnowledgeArticleDetails saved = knowledgeBaseService.saveArticle(command);
        assertThat(saved.id()).isNotNull();

        List<KnowledgeArticleSummary> summaries = knowledgeBaseService.listArticles();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).title()).isEqualTo("Инструкция");
    }

    @Test
    void publicFormServiceCreatesSessionsAndHistory() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (2, 'web', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-demo')");
        PublicFormSubmission submission = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of(), null);
        PublicFormSessionDto session = publicFormService.createSession("web-demo", submission, "test-ip");
        assertThat(session.token()).isNotBlank();
        assertThat(session.ticketId()).startsWith("web-");

        PublicFormSessionDto loaded = publicFormService.findSession("web-demo", session.token()).orElseThrow();
        assertThat(loaded.clientName()).isEqualTo("Анна");

        assertThat(dialogService.loadHistory(session.ticketId(), null)).isNotEmpty();
        assertThat(dialogService.loadDialogs(null)).extracting("ticketId").contains(session.ticketId());

        Integer ticketCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = ?", Integer.class, session.ticketId());
        Integer messageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages WHERE ticket_id = ?", Integer.class, session.ticketId());
        assertThat(ticketCount).isEqualTo(1);
        assertThat(messageCount).isEqualTo(1);
    }



    @Test
    void publicFormServiceValidatesRequiredDynamicField() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (21, 'web-required', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-required', ?)",
                "[{\"id\":\"email\",\"text\":\"Email\",\"type\":\"email\",\"required\":true}]");
        PublicFormSubmission submission = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of(), null);

        assertThatThrownBy(() -> publicFormService.createSession("web-required", submission, "ip-required"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Заполните поле");
    }


    @Test
    void publicFormServiceValidatesRequiredCheckboxField() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (25, 'web-checkbox', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-checkbox', ?)",
                "[{\"id\":\"consent\",\"text\":\"Согласие\",\"type\":\"checkbox\",\"required\":true}]");

        PublicFormSubmission invalid = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of("consent", "false"), null);
        assertThatThrownBy(() -> publicFormService.createSession("web-checkbox", invalid, "ip-checkbox"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Подтвердите поле");

        PublicFormSubmission valid = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of("consent", "true"), null);
        PublicFormSessionDto session = publicFormService.createSession("web-checkbox", valid, "ip-checkbox-ok");
        assertThat(session.ticketId()).startsWith("web-");
    }

    @Test
    void publicFormServiceAppliesRateLimitByRequester() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (22, 'web-rate', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-rate')");
        PublicFormSubmission submission = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of(), null);

        for (int i = 0; i < 5; i++) {
            PublicFormSessionDto session = publicFormService.createSession("web-rate", submission, "same-ip");
            assertThat(session.ticketId()).startsWith("web-");
        }

        assertThatThrownBy(() -> publicFormService.createSession("web-rate", submission, "same-ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Слишком много запросов");
    }

    @Test
    void publicFormServiceBuildsRateLimitRequesterKeyWithFingerprintToggle() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_rate_limit_use_fingerprint\":true}");

        String keyA = publicFormService.buildRequesterKey("same-ip", "browser-A");
        String keyB = publicFormService.buildRequesterKey("same-ip", "browser-B");
        assertThat(keyA).contains("same-ip|fp:");
        assertThat(keyA).isNotEqualTo(keyB);

        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_rate_limit_use_fingerprint\":false}");

        String keyWithoutFingerprint = publicFormService.buildRequesterKey("same-ip", "browser-A");
        assertThat(keyWithoutFingerprint).isEqualTo("same-ip");
    }


    @Test
    void publicFormServiceReturnsSameSessionForSameRequestId() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (26, 'web-idempotent', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-idempotent')");
        PublicFormSubmission first = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of("topic", "billing"), "req-1");

        PublicFormSessionDto created = publicFormService.createSession("web-idempotent", first, "same-ip-idem");
        PublicFormSessionDto duplicated = publicFormService.createSession("web-idempotent", first, "same-ip-idem");

        assertThat(duplicated.ticketId()).isEqualTo(created.ticketId());
        assertThat(duplicated.token()).isEqualTo(created.token());
        Integer historyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_history WHERE ticket_id = ?", Integer.class, created.ticketId());
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void publicFormServiceRejectsDifferentPayloadWithSameRequestId() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (27, 'web-idempotent-2', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-idempotent-2')");
        PublicFormSubmission first = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of("topic", "billing"), "req-2");
        PublicFormSubmission changed = new PublicFormSubmission("Другой текст", "Анна", "+79991234567", "anna", null, Map.of("topic", "billing"), "req-2");

        publicFormService.createSession("web-idempotent-2", first, "same-ip-idem-2");
        assertThatThrownBy(() -> publicFormService.createSession("web-idempotent-2", changed, "same-ip-idem-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId");
    }


    @Test
    void publicFormServiceRejectsWhenFormDisabledInConfig() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (23, 'web-disabled', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-disabled', ?)",
                "{\"schemaVersion\":1,\"enabled\":false,\"fields\":[]}");
        PublicFormSubmission submission = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of(), null);

        assertThatThrownBy(() -> publicFormService.createSession("web-disabled", submission, "ip-disabled"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("временно отключена");
    }

    @Test
    void publicFormServiceRequiresCaptchaWhenEnabled() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (24, 'web-captcha', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-captcha', ?)",
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":true,\"fields\":[]}");
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_captcha_shared_secret\":\"captcha-123\"}");

        PublicFormSubmission bad = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", "wrong", Map.of(), null);
        assertThatThrownBy(() -> publicFormService.createSession("web-captcha", bad, "ip-captcha"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAPTCHA");

        PublicFormSubmission good = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", "captcha-123", Map.of(), null);
        PublicFormSessionDto session = publicFormService.createSession("web-captcha", good, "ip-captcha-ok");
        assertThat(session.ticketId()).startsWith("web-");
    }


    @Test
    void publicFormServiceLimitsTotalAnswersPayload() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (32, 'web-payload', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-payload', ?)",
                "[{\"id\":\"details\",\"text\":\"Детали\",\"type\":\"textarea\",\"required\":true,\"maxLength\":1200}]");
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_answers_total_max_length\":200}");

        PublicFormSubmission oversized = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null,
                Map.of("details", "x".repeat(205)), null);

        assertThatThrownBy(() -> publicFormService.createSession("web-payload", oversized, "ip-payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Суммарный объём ответов формы превышает лимит");

        PublicFormSubmission valid = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null,
                Map.of("details", "x".repeat(150)), null);
        PublicFormSessionDto session = publicFormService.createSession("web-payload", valid, "ip-payload-ok");
        assertThat(session.ticketId()).startsWith("web-");
    }

    @Test
    void publicFormServiceCollectsRuntimeMetricsWhenEnabled() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (31, 'web-metrics', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-metrics')");

        publicFormService.recordConfigView(31L);
        publicFormService.recordConfigView(31L);
        publicFormService.recordSubmitSuccess(31L);
        publicFormService.recordSubmitError(31L, "CAPTCHA token is invalid");
        publicFormService.recordSubmitError(31L, "Слишком много запросов. Попробуйте чуть позже.");

        Map<String, Object> snapshot = publicFormService.loadMetricsSnapshot(31L);
        assertThat(snapshot).containsEntry("enabled", true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> channels = (List<Map<String, Object>>) snapshot.get("channels");
        assertThat(channels).hasSize(1);
        Map<String, Object> row = channels.get(0);
        assertThat(row.get("channelId")).isEqualTo(31L);
        assertThat(row.get("views")).isEqualTo(2L);
        assertThat(row.get("submits")).isEqualTo(1L);
        assertThat(row.get("submitErrors")).isEqualTo(2L);
        assertThat(row.get("captchaFailures")).isEqualTo(1L);
        assertThat(row.get("rateLimitRejections")).isEqualTo(1L);
    }

    @Test
    void notificationServiceCountsAndMarksAsRead() {
        jdbcTemplate.update("INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                "operator", "Новое сообщение", "/tickets/T-1", 0);
        jdbcTemplate.update("INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                "operator", "Резерв", "/tickets/T-2", 0);

        NotificationSummary summary = notificationService.summary("operator");
        assertThat(summary.unreadCount()).isEqualTo(2);

        List<NotificationDto> notifications = notificationService.findForUser("operator");
        assertThat(notifications).hasSize(2);

        notificationService.markAsRead("operator", notifications.get(0).id());
        NotificationSummary after = notificationService.summary("operator");
        assertThat(after.unreadCount()).isEqualTo(1);
    }

    @Test
    void loadRelatedEventsIncludesWorkflowHistoryFromTaskLinks() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at) VALUES (3, 'token3', 'Ops', 1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (?,?,?,?)",
                1002L, "T-WF-1", "pending", 3);
        jdbcTemplate.update("INSERT INTO messages (group_msg_id, user_id, business, city, location_name, problem, created_at, username, ticket_id, created_date, created_time, client_name, client_status, updated_at, updated_by, channel_id) " +
                        "VALUES (NULL, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, DATE('now'), TIME('now'), ?, ?, CURRENT_TIMESTAMP, 'tester', ?)",
                1002L, "IT", "Москва", "Офис", "Нет доступа", "petrov", "T-WF-1", "Пётр", "Новый", 3);

        jdbcTemplate.update("INSERT INTO task_seq (id, val) VALUES (1, 1)");
        jdbcTemplate.update("INSERT INTO tasks (id, seq, title, creator, assignee, status, created_at, last_activity_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                500L, 1L, "Разобрать обращение", "lead", "operator", "В работе");
        jdbcTemplate.update("INSERT INTO task_links (user_id, task_id, ticket_id) VALUES (?,?,?)", 1002L, 500L, "T-WF-1");
        jdbcTemplate.update("INSERT INTO task_history (task_id, at, text) VALUES (?,?,?)", 500L, OffsetDateTime.now().plusMinutes(1).toString(), "Назначен дежурному инженеру");

        List<Map<String, Object>> events = dialogService.loadRelatedEvents("T-WF-1", 10);
        assertThat(events).isNotEmpty();
        assertThat(events).anySatisfy(event -> {
            assertThat(event.get("type")).isEqualTo("workflow");
            assertThat(String.valueOf(event.get("detail"))).contains("Назначен дежурному инженеру");
        });
    }

    @Test
    void workspaceTelemetrySummaryIncludesPeriodComparisonAndRegressionAlerts() {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op1', 'workspace_open_ms', 'performance', 'T-1', NULL, NULL, 'workspace.v1', 1200, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-1 day')),
                    ('op1', 'workspace_render_error', 'quality', 'T-1', NULL, 'render_failed', 'workspace.v1', NULL, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-1 day')),
                    ('op2', 'workspace_open_ms', 'performance', 'T-2', NULL, NULL, 'workspace.v1', 2300, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-1 day')),
                    ('op2', 'workspace_fallback_to_legacy', 'fallback', 'T-2', 'timeout', NULL, 'workspace.v1', NULL, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-1 day')),

                    ('op1', 'workspace_open_ms', 'performance', 'T-3', NULL, NULL, 'workspace.v1', 900, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op2', 'workspace_open_ms', 'performance', 'T-4', NULL, NULL, 'workspace.v1', 950, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op3', 'workspace_open_ms', 'performance', 'T-5', NULL, NULL, 'workspace.v1', 980, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op4', 'workspace_open_ms', 'performance', 'T-6', NULL, NULL, 'workspace.v1', 1020, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op5', 'workspace_open_ms', 'performance', 'T-7', NULL, NULL, 'workspace.v1', 1000, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op6', 'workspace_open_ms', 'performance', 'T-8', NULL, NULL, 'workspace.v1', 970, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op7', 'workspace_open_ms', 'performance', 'T-9', NULL, NULL, 'workspace.v1', 1030, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op8', 'workspace_open_ms', 'performance', 'T-10', NULL, NULL, 'workspace.v1', 1010, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op9', 'workspace_open_ms', 'performance', 'T-11', NULL, NULL, 'workspace.v1', 990, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op10', 'workspace_open_ms', 'performance', 'T-12', NULL, NULL, 'workspace.v1', 1015, 'workspace_v1_rollout', 'test', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),

                    ('op11', 'workspace_open_ms', 'performance', 'T-13', NULL, NULL, 'workspace.v1', 1110, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op12', 'workspace_open_ms', 'performance', 'T-14', NULL, NULL, 'workspace.v1', 1090, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op13', 'workspace_open_ms', 'performance', 'T-15', NULL, NULL, 'workspace.v1', 1080, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op14', 'workspace_open_ms', 'performance', 'T-16', NULL, NULL, 'workspace.v1', 1100, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op15', 'workspace_open_ms', 'performance', 'T-17', NULL, NULL, 'workspace.v1', 1130, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op16', 'workspace_open_ms', 'performance', 'T-18', NULL, NULL, 'workspace.v1', 1075, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op17', 'workspace_open_ms', 'performance', 'T-19', NULL, NULL, 'workspace.v1', 1060, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op18', 'workspace_open_ms', 'performance', 'T-20', NULL, NULL, 'workspace.v1', 1050, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op19', 'workspace_open_ms', 'performance', 'T-21', NULL, NULL, 'workspace.v1', 1140, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day')),
                    ('op20', 'workspace_open_ms', 'performance', 'T-22', NULL, NULL, 'workspace.v1', 1120, 'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-8 day'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");
        Map<String, Object> previousTotals = (Map<String, Object>) summary.get("previous_totals");
        Map<String, Object> comparison = (Map<String, Object>) summary.get("period_comparison");
        Map<String, Object> cohortComparison = (Map<String, Object>) summary.get("cohort_comparison");
        Map<String, Object> guardrails = (Map<String, Object>) summary.get("guardrails");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) guardrails.get("alerts");

        assertThat(totals.get("events")).isEqualTo(4L);
        assertThat(previousTotals.get("events")).isEqualTo(20L);
        assertThat(comparison).containsKeys("render_error_rate_delta", "fallback_rate_delta", "avg_open_ms_delta");
        assertThat(cohortComparison).containsKeys("control", "test", "sample_size_ok", "winner");
        assertThat(cohortComparison.get("sample_size_ok")).isEqualTo(false);
        assertThat(cohortComparison.get("winner")).isEqualTo("insufficient_data");
        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(rolloutDecision).containsEntry("sample_size_ok", false);
        assertThat(alerts).anySatisfy(alert -> {
            assertThat(alert.get("metric")).isEqualTo("render_error");
            assertThat(alert).containsKey("previous_value");
            assertThat(alert).containsKey("delta");
        });
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenKpiCoverageIsTooLow() {
        for (int i = 0; i < 300; i++) {
            String cohort = i < 150 ? "control" : "test";
            String ticketId = "T-COV-" + i;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-cov-" + i, ticketId, cohort.equals("test") ? 940L : 980L, cohort);
        }

        for (int i = 0; i < 10; i++) {
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_macro_apply', 'macro', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', 'control', 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-cov-kpi-c-" + i, "T-COV-KPI-C-" + i);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_macro_apply', 'macro', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', 'test', 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-cov-kpi-t-" + i, "T-COV-KPI-T-" + i);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> cohortComparison = (Map<String, Object>) summary.get("cohort_comparison");
        Map<String, Object> kpiSignal = (Map<String, Object>) cohortComparison.get("kpi_signal");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> frtMetric = (Map<String, Object>) ((Map<String, Object>) kpiSignal.get("metrics")).get("frt");

        assertThat(cohortComparison.get("sample_size_ok")).isEqualTo(true);
        assertThat(kpiSignal.get("ready_for_decision")).isEqualTo(false);
        assertThat(kpiSignal.get("min_coverage_rate_per_cohort")).isEqualTo(0.05d);
        assertThat(frtMetric.get("events_ready")).isEqualTo(true);
        assertThat(frtMetric.get("coverage_ready")).isEqualTo(false);
        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(rolloutDecision).containsEntry("kpi_signal_ready", false);
    }

    @Test
    void workspaceTelemetrySummaryAggregatesSecondaryKpiSignals() {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op-sec-1', 'workspace_open_ms', 'performance', 'T-SEC-1', NULL, NULL, 'workspace.v1', 980,
                     'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, 'dialogs_per_shift,csat', NULL, NULL, datetime('now', '-1 hour')),
                    ('op-sec-2', 'kpi_dialogs_per_shift_recorded', 'kpi', 'T-SEC-2', NULL, NULL, 'workspace.v1', NULL,
                     'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, 'dialogs_per_shift', NULL, NULL, datetime('now', '-1 hour')),
                    ('op-sec-3', 'kpi_csat_recorded', 'kpi', 'T-SEC-3', NULL, NULL, 'workspace.v1', NULL,
                     'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, 'csat', NULL, NULL, datetime('now', '-1 hour'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");

        assertThat(totals).containsEntry("kpi_dialogs_per_shift_events", 2L);
        assertThat(totals).containsEntry("kpi_csat_events", 2L);
        assertThat(totals).containsEntry("kpi_dialogs_per_shift_recorded_events", 1L);
        assertThat(totals).containsEntry("kpi_csat_recorded_events", 1L);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenPrimaryKpiOutcomesRegressInTestCohort() {
        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-outcome-open-" + i, "T-OUTCOME-OPEN-" + i, cohort.equals("test") ? 970L : 980L, cohort);
        }

        for (int i = 0; i < 12; i++) {
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-outcome-frt-" + i, "T-OUTCOME-FRT-" + i, i < 6 ? 1200L : 1700L, i < 6 ? "control" : "test");

            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-outcome-ttr-" + i, "T-OUTCOME-TTR-" + i, i < 6 ? 2400L : 3600L, i < 6 ? "control" : "test");

            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-outcome-sla-" + i, "T-OUTCOME-SLA-" + i, i < 6 ? "control" : "test");
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> cohortComparison = (Map<String, Object>) summary.get("cohort_comparison");
        Map<String, Object> kpiOutcome = (Map<String, Object>) cohortComparison.get("kpi_outcome_signal");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");

        assertThat(kpiOutcome.get("ready_for_decision")).isEqualTo(true);
        assertThat(kpiOutcome.get("has_regression")).isEqualTo(true);
        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(rolloutDecision).containsEntry("kpi_outcome_ready", true);
        assertThat(rolloutDecision).containsEntry("kpi_outcome_regressions", true);
    }

}
