package com.example.panel.service;

import com.example.panel.controller.SettingsBridgeController;
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
    private SettingsBridgeController settingsBridgeController;

    @Autowired
    private SharedConfigService sharedConfigService;

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
        jdbcTemplate.update("DELETE FROM app_settings WHERE setting_key = ?", "dialog_config");
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
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dialog_action_audit WHERE ticket_id = ? AND action = ?",
                Integer.class,
                session.ticketId(),
                "public_form_submit"
        );
        assertThat(ticketCount).isEqualTo(1);
        assertThat(messageCount).isEqualTo(1);
        assertThat(auditCount).isEqualTo(1);
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
    void publicFormSessionTokenRotatesOnReadWhenEnabled() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (35, 'web-rotate', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-rotate')");
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_session_token_rotate_on_read\":true}");

        PublicFormSubmission submission = new PublicFormSubmission("Нужна помощь", "Анна", "+79991234567", "anna", null, Map.of(), null);
        PublicFormSessionDto created = publicFormService.createSession("web-rotate", submission, "ip-rotate");

        PublicFormSessionDto firstRead = publicFormService.findSession("web-rotate", created.token()).orElseThrow();
        assertThat(firstRead.token()).isNotEqualTo(created.token());

        PublicFormSessionDto secondRead = publicFormService.findSession("web-rotate", firstRead.token()).orElseThrow();
        assertThat(secondRead.token()).isNotEqualTo(firstRead.token());

        assertThat(publicFormService.findSession("web-rotate", created.token())).isEmpty();
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
    void publicFormServiceStripsHtmlTagsWhenEnabledAndCanBeDisabledFromSettings() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (33, 'web-strip', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-strip', ?)",
                "[{\"id\":\"details\",\"text\":\"Детали\",\"type\":\"textarea\",\"required\":true,\"maxLength\":1200}]");
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_strip_html_tags\":true}");

        PublicFormSubmission sanitized = new PublicFormSubmission("<b>Нужна</b> помощь <script>alert(1)</script>", "<b>Анна</b>", "+79991234567", "anna", null,
                Map.of("details", "<i>Срочно</i> нужна <u>помощь</u>"), null);
        PublicFormSessionDto sanitizedSession = publicFormService.createSession("web-strip", sanitized, "ip-strip-on");

        String sanitizedHistory = jdbcTemplate.queryForObject(
                "SELECT message FROM chat_history WHERE ticket_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                sanitizedSession.ticketId());
        assertThat(sanitizedHistory).contains("Нужна помощь alert(1)").doesNotContain("<b>").doesNotContain("<script>");
        String sanitizedClientName = jdbcTemplate.queryForObject(
                "SELECT client_name FROM web_form_sessions WHERE ticket_id = ?",
                String.class,
                sanitizedSession.ticketId());
        assertThat(sanitizedClientName).isEqualTo("Анна");

        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (34, 'web-strip-off', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-strip-off', ?)",
                "[{\"id\":\"details\",\"text\":\"Детали\",\"type\":\"textarea\",\"required\":true,\"maxLength\":1200}]");
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_strip_html_tags\":false}");

        PublicFormSubmission raw = new PublicFormSubmission("<b>Нужна</b> помощь", "<b>Олег</b>", "+79990000000", "oleg", null,
                Map.of("details", "<i>Не трогать HTML</i>"), null);
        PublicFormSessionDto rawSession = publicFormService.createSession("web-strip-off", raw, "ip-strip-off");

        String rawHistory = jdbcTemplate.queryForObject(
                "SELECT message FROM chat_history WHERE ticket_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                rawSession.ticketId());
        assertThat(rawHistory).contains("<b>Нужна</b>").contains("<i>Не трогать HTML</i>");
    }

    @Test
    void publicFormServiceCollectsRuntimeMetricsWhenEnabled() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id) VALUES (31, 'web-metrics', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-metrics')");
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"public_form_alert_min_views\":1,\"public_form_alert_error_rate_threshold\":0.5,\"public_form_alert_captcha_failure_rate_threshold\":0.3,\"public_form_alert_rate_limit_rejection_rate_threshold\":0.3}");

        publicFormService.recordConfigView(31L);
        publicFormService.recordConfigView(31L);
        publicFormService.recordSubmitSuccess(31L);
        publicFormService.recordSubmitError(31L, "CAPTCHA token is invalid");
        publicFormService.recordSubmitError(31L, "Слишком много запросов. Попробуйте чуть позже.");

        Map<String, Object> snapshot = publicFormService.loadMetricsSnapshot(31L);
        assertThat(snapshot).containsEntry("enabled", true);
        assertThat(snapshot).containsEntry("alertsEnabled", true);
        assertThat(snapshot.get("channelsWithAlerts")).isEqualTo(1L);
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
        assertThat(row.get("submitErrorRateByAttempts")).isEqualTo(2d / 3d);
        assertThat(row.get("captchaFailureRateByAttempts")).isEqualTo(1d / 3d);
        assertThat(row.get("rateLimitRejectionRateByAttempts")).isEqualTo(1d / 3d);
        assertThat(row.get("alerts")).asList().contains("high_submit_error_rate", "high_captcha_failure_rate", "high_rate_limit_rejection_rate");
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
    void workspaceTelemetrySummaryIncludesCustomerContextGapRates() {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op1', 'workspace_open_ms', 'performance', 'T-CONTEXT-1', NULL, NULL, 'workspace.v1', 900, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-2 hour')),
                    ('op2', 'workspace_open_ms', 'performance', 'T-CONTEXT-2', NULL, NULL, 'workspace.v1', 950, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour')),
                    ('op2', 'workspace_context_profile_gap', 'workspace', 'T-CONTEXT-2', 'last_message_at', NULL, 'workspace.v1', 1, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour')),
                    ('op2', 'workspace_context_source_gap', 'workspace', 'T-CONTEXT-2', 'contract:invalid_utc', NULL, 'workspace.v1', 1, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour')),
                    ('op3', 'workspace_context_profile_gap', 'workspace', 'T-CONTEXT-3', '', NULL, 'workspace.v1', 0, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-30 minute'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");
        Map<String, Object> gapBreakdown = (Map<String, Object>) summary.get("gap_breakdown");
        List<Map<String, Object>> profileRows = (List<Map<String, Object>>) gapBreakdown.get("profile");
        List<Map<String, Object>> sourceRows = (List<Map<String, Object>>) gapBreakdown.get("source");

        assertThat(totals).containsEntry("workspace_open_events", 2L);
        assertThat(totals).containsEntry("context_profile_gap_events", 2L);
        assertThat(totals).containsEntry("context_source_gap_events", 1L);
        assertThat((double) totals.get("context_profile_gap_rate")).isEqualTo(1.0d);
        assertThat((double) totals.get("context_profile_ready_rate")).isEqualTo(0.0d);
        assertThat((double) totals.get("context_source_gap_rate")).isEqualTo(0.5d);
        assertThat((double) totals.get("context_source_ready_rate")).isEqualTo(0.5d);
        assertThat(profileRows).anySatisfy(row -> {
            assertThat(row).containsEntry("reason", "last_message_at");
            assertThat(row).containsEntry("events", 1L);
            assertThat(String.valueOf(row.get("last_seen_at"))).endsWith("Z");
        });
        assertThat(profileRows).anySatisfy(row -> assertThat(row).containsEntry("reason", "unspecified"));
        assertThat(sourceRows).anySatisfy(row -> assertThat(row).containsEntry("reason", "contract:invalid_utc"));
    }

    @Test
    void workspaceTelemetrySummaryTracksContextBlockGaps() {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op1', 'workspace_open_ms', 'performance', 'T-BLOCK-1', NULL, NULL, 'workspace.v1', 910, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-2 hour')),
                    ('op2', 'workspace_open_ms', 'performance', 'T-BLOCK-2', NULL, NULL, 'workspace.v1', 940, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour')),
                    ('op2', 'workspace_context_block_gap', 'workspace', 'T-BLOCK-2', 'context_sources,customer_profile', NULL, 'workspace.v1', 2, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour')),
                    ('op2', 'workspace_parity_gap', 'workspace', 'T-BLOCK-2', 'attachments', NULL, 'workspace.v1', 75, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-45 minute'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");
        Map<String, Object> scorecard = (Map<String, Object>) summary.get("rollout_scorecard");
        Map<String, Object> gapBreakdown = (Map<String, Object>) summary.get("gap_breakdown");
        List<Map<String, Object>> items = (List<Map<String, Object>>) scorecard.get("items");
        List<Map<String, Object>> blockRows = (List<Map<String, Object>>) gapBreakdown.get("block");
        List<Map<String, Object>> parityRows = (List<Map<String, Object>>) gapBreakdown.get("parity");

        assertThat(totals).containsEntry("context_block_gap_events", 1L);
        assertThat(totals).containsEntry("workspace_parity_gap_events", 1L);
        assertThat((double) totals.get("context_block_gap_rate")).isEqualTo(0.5d);
        assertThat((double) totals.get("context_block_ready_rate")).isEqualTo(0.5d);
        assertThat((double) totals.get("workspace_parity_gap_rate")).isEqualTo(0.5d);
        assertThat((double) totals.get("workspace_parity_ready_rate")).isEqualTo(0.5d);
        assertThat(items).anySatisfy(item -> {
            assertThat(item).containsEntry("key", "customer_context_blocks");
            assertThat(item).containsEntry("status", "attention");
        });
        assertThat(blockRows).anySatisfy(row -> assertThat(row).containsEntry("reason", "context_sources"));
        assertThat(blockRows).anySatisfy(row -> assertThat(row).containsEntry("reason", "customer_profile"));
        assertThat(parityRows).anySatisfy(row -> assertThat(row).containsEntry("reason", "attachments"));
    }

    @Test
    void workspaceTelemetrySummaryTracksSlaPolicyGaps() {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op1', 'workspace_open_ms', 'performance', 'T-SLA-1', NULL, NULL, 'workspace.v1', 910, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-2 hour')),
                    ('op2', 'workspace_open_ms', 'performance', 'T-SLA-2', NULL, NULL, 'workspace.v1', 940, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour')),
                    ('op2', 'workspace_sla_policy_gap', 'workspace', 'T-SLA-2', 'fallback_assignee_missing', NULL, 'workspace.v1', 5, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");
        Map<String, Object> gapBreakdown = (Map<String, Object>) summary.get("gap_breakdown");
        List<Map<String, Object>> policyRows = (List<Map<String, Object>>) gapBreakdown.get("sla_policy");

        assertThat(totals).containsEntry("workspace_sla_policy_gap_events", 1L);
        assertThat((double) totals.get("workspace_sla_policy_gap_rate")).isEqualTo(0.5d);
        assertThat((double) totals.get("workspace_sla_policy_ready_rate")).isEqualTo(0.5d);
        assertThat(policyRows).anySatisfy(row -> {
            assertThat(row).containsEntry("reason", "fallback_assignee_missing");
            assertThat(row).containsEntry("events", 1L);
            assertThat(String.valueOf(row.get("last_seen_at"))).endsWith("Z");
        });
    }

    @Test
    void workspaceTelemetrySummaryBuildsRolloutScorecardWithUtcTimestamps() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,
                         "workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,
                         "workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2026-02-03T04:05:06",
                         "workspace_rollout_external_kpi_review_ttl_hours":999}
                        """);

        for (int i = 0; i < 70; i++) {
            String cohort = i < 35 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-score-open-" + i, "T-SCORE-OPEN-" + i, openMs, cohort);
        }
        for (int i = 0; i < 16; i++) {
            String cohort = i < 8 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-score-frt-" + i, "T-SCORE-FRT-" + i, cohort.equals("test") ? 900L : 1000L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-score-ttr-" + i, "T-SCORE-TTR-" + i, cohort.equals("test") ? 1900L : 2000L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-score-sla-" + i, "T-SCORE-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> scorecard = (Map<String, Object>) summary.get("rollout_scorecard");
        List<Map<String, Object>> items = (List<Map<String, Object>>) scorecard.get("items");

        assertThat(scorecard.get("decision_action")).isEqualTo("scale_up");
        assertThat(items).extracting(item -> item.get("key"))
                .contains("sample_size", "guardrails", "primary_kpi_signal", "outcome_frt", "outcome_ttr", "outcome_sla_breach",
                        "external_kpi_gate", "external_review", "external_data_freshness", "external_dashboards",
                        "external_datamart_health", "external_datamart_program", "external_dependency_ticket",
                        "external_datamart_contract");
        assertThat(items).anySatisfy(item -> {
            if ("external_kpi_gate".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo("2026-02-03T04:05:06Z");
            }
            if ("external_review".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo("2026-02-03T04:05:06Z");
            }
            if ("external_data_freshness".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("off");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryBuildsGovernancePacketWithOwnerSignoffInUtc() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_owner_signoff_required":true,
                         "workspace_rollout_governance_owner_signoff_by":"ops-director",
                         "workspace_rollout_governance_owner_signoff_at":"2026-02-03T04:05:06",
                         "workspace_rollout_governance_owner_signoff_ttl_hours":999}
                        """);

        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op1', 'workspace_open_ms', 'performance', 'T-PACKET-1', NULL, NULL, 'workspace.v1', 880, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-2 hour')),
                    ('op1', 'workspace_parity_gap', 'workspace', 'T-PACKET-1', 'attachments', NULL, 'workspace.v1', 20, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-90 minute'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");
        Map<String, Object> ownerSignoff = (Map<String, Object>) packet.get("owner_signoff");
        Map<String, Object> paritySnapshot = (Map<String, Object>) packet.get("parity_snapshot");

        assertThat(packet.get("required")).isEqualTo(true);
        assertThat(packet.get("packet_ready")).isEqualTo(true);
        assertThat(packet.get("status")).isEqualTo("ok");
        assertThat(items).anySatisfy(item -> {
            if ("owner_signoff".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo("2026-02-03T04:05:06Z");
                assertThat(item.get("current_value")).isEqualTo("signed_by=ops-director");
            }
        });
        assertThat(ownerSignoff).containsEntry("required", true);
        assertThat(ownerSignoff).containsEntry("ready", true);
        assertThat(ownerSignoff).containsEntry("signed_by", "ops-director");
        assertThat(ownerSignoff).containsEntry("signed_at", "2026-02-03T04:05:06Z");
        assertThat(paritySnapshot).containsEntry("ready", true);
        assertThat(paritySnapshot).containsEntry("workspace_open_events", 1L);
        assertThat(paritySnapshot).containsEntry("parity_gap_events", 1L);
    }

    @Test
    void workspaceTelemetrySummaryFlagsGovernancePacketInvalidOwnerSignoffDate() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_owner_signoff_required":true,
                         "workspace_rollout_governance_owner_signoff_by":"ops-director",
                         "workspace_rollout_governance_owner_signoff_at":"bad-owner-ts"}
                        """);

        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op1', 'workspace_open_ms', 'performance', 'T-PACKET-INVALID', NULL, NULL, 'workspace.v1', 910, 'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");
        Map<String, Object> ownerSignoff = (Map<String, Object>) packet.get("owner_signoff");

        assertThat(packet.get("packet_ready")).isEqualTo(false);
        assertThat(packet.get("status")).isEqualTo("hold");
        assertThat((List<String>) packet.get("missing_items")).contains("owner_signoff");
        assertThat(items).anySatisfy(item -> {
            if ("owner_signoff".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(item.get("current_value")).isEqualTo("invalid_utc");
            }
        });
        assertThat(ownerSignoff).containsEntry("timestamp_invalid", true);
        assertThat(ownerSignoff).containsEntry("signed_at", "");
    }

    @Test
    void workspaceTelemetrySummaryExpandsExternalCheckpointScorecardItems() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,
                         "workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,
                         "workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00",
                         "workspace_rollout_external_kpi_data_freshness_required":true,
                         "workspace_rollout_external_kpi_data_updated_at":"bad-data-ts",
                         "workspace_rollout_external_kpi_dashboard_links_required":true,
                         "workspace_rollout_external_kpi_dashboard_status_required":true,
                         "workspace_rollout_external_kpi_dashboard_status":"degraded",
                         "workspace_rollout_external_kpi_datamart_health_required":true,
                         "workspace_rollout_external_kpi_datamart_health_status":"healthy",
                         "workspace_rollout_external_kpi_datamart_health_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_health_updated_at":"2026-02-03T04:05:06",
                         "workspace_rollout_external_kpi_datamart_program_blocker_required":true,
                         "workspace_rollout_external_kpi_datamart_program_status":"blocked",
                         "workspace_rollout_external_kpi_datamart_program_note":"blocked by vendor",
                         "workspace_rollout_external_kpi_datamart_program_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_program_updated_at":"bad-program-ts",
                         "workspace_rollout_external_kpi_datamart_timeline_required":true,
                         "workspace_rollout_external_kpi_datamart_target_ready_at":"",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_url":"https://tracker.example.com/BI-42",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner":"bi-platform",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact":"BI owner",
                         "workspace_rollout_external_kpi_datamart_contract_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_mandatory_fields":"frt,ttr,sla_breach",
                         "workspace_rollout_external_kpi_datamart_contract_available_fields":"frt,ttr",
                         "cross_product_omnichannel_dashboard_url":"https://dash.example.com/omni",
                         "cross_product_finance_dashboard_url":"https://dash.example.com/finance"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> scorecard = (Map<String, Object>) summary.get("rollout_scorecard");
        List<Map<String, Object>> items = (List<Map<String, Object>>) scorecard.get("items");

        assertThat(items).anySatisfy(item -> {
            if ("external_review".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo("2099-01-01T00:00Z");
            }
            if ("external_data_freshness".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(item.get("current_value")).isEqualTo("invalid_utc");
            }
            if ("external_dashboards".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(item.get("current_value")).isEqualTo("links=ready, status=degraded");
            }
            if ("external_datamart_health".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo("2026-02-03T04:05:06Z");
            }
            if ("external_datamart_program".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(String.valueOf(item.get("note"))).contains("blocked by vendor");
            }
            if ("external_dependency_ticket".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(String.valueOf(item.get("note"))).contains("url=https://tracker.example.com/BI-42");
            }
            if ("external_datamart_contract".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(String.valueOf(item.get("note"))).contains("missing_mandatory=sla_breach");
            }
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
    void workspaceTelemetrySummaryAggregatesInlineNavigationSignals() {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES
                    ('op-nav-1', 'workspace_open_ms', 'performance', 'T-NAV-1', NULL, NULL, 'workspace.v1', 810,
                     'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour')),
                    ('op-nav-2', 'workspace_inline_navigation', 'workspace', 'T-NAV-2', 'next', NULL, 'workspace.v1', 2,
                     'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-50 minute')),
                    ('op-nav-3', 'workspace_inline_navigation', 'workspace', 'T-NAV-3', 'previous', NULL, 'workspace.v1', 3,
                     'workspace_v1_rollout', 'control', 'team=ops;shift=night', NULL, NULL, NULL, NULL, datetime('now', '-40 minute'))
                """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");

        assertThat(totals).containsEntry("workspace_open_events", 1L);
        assertThat(totals).containsEntry("workspace_inline_navigation_events", 2L);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenExternalKpiReviewIsStale() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", "{\"workspace_rollout_external_kpi_gate_enabled\":true,\"workspace_rollout_external_kpi_omnichannel_ready\":true,\"workspace_rollout_external_kpi_finance_ready\":true,\"workspace_rollout_external_kpi_reviewed_by\":\"release-oncall\",\"workspace_rollout_external_kpi_reviewed_at\":\"2024-01-01T00:00:00Z\",\"workspace_rollout_external_kpi_review_ttl_hours\":24}");

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-external-open-" + i, "T-EXTERNAL-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-external-frt-" + i, "T-EXTERNAL-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-external-ttr-" + i, "T-EXTERNAL-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-external-sla-" + i, "T-EXTERNAL-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
        assertThat(externalSignal).containsEntry("review_present", true);
        assertThat(externalSignal).containsEntry("review_fresh", false);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenOwnerRunbookGateEnabledWithoutContext() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_owner_runbook_required":true}
                        """);

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-owner-open-" + i, "T-OWNER-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-owner-frt-" + i, "T-OWNER-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-owner-ttr-" + i, "T-OWNER-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-owner-sla-" + i, "T-OWNER-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("owner_runbook_required", true);
        assertThat(externalSignal).containsEntry("owner_runbook_present", false);
        assertThat(externalSignal).containsEntry("owner_runbook_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenOwnerRunbookGateHasInvalidRunbookUrl() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_owner_runbook_required":true,
                         "workspace_rollout_external_kpi_datamart_owner":"bi-platform",
                         "workspace_rollout_external_kpi_datamart_runbook_url":"slack://bi-runbook"}
                        """);

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-runbook-open-" + i, "T-RUNBOOK-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-runbook-frt-" + i, "T-RUNBOOK-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-runbook-ttr-" + i, "T-RUNBOOK-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-runbook-sla-" + i, "T-RUNBOOK-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("owner_runbook_required", true);
        assertThat(externalSignal).containsEntry("owner_runbook_present", true);
        assertThat(externalSignal).containsEntry("datamart_runbook_url_valid", false);
        assertThat(externalSignal).containsEntry("owner_runbook_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }


    @Test
    void workspaceRolloutDecisionHoldsWhenDatamartHealthGateIsRequiredAndDegraded() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_health_required":true,
                         "workspace_rollout_external_kpi_datamart_health_status":"degraded",
                         "workspace_rollout_external_kpi_datamart_health_note":"incident-42"}
                        """);

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-open-" + i, "T-HEALTH-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-frt-" + i, "T-HEALTH-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-ttr-" + i, "T-HEALTH-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-sla-" + i, "T-HEALTH-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_health_required", true);
        assertThat(externalSignal).containsEntry("datamart_health_status", "degraded");
        assertThat(externalSignal).containsEntry("datamart_health_ready", false);
        assertThat(externalSignal).containsEntry("datamart_health_note", "incident-42");
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }


    @Test
    void workspaceRolloutDecisionHoldsWhenDatamartHealthFreshnessRequiredAndStatusStale() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_health_required":true,
                         "workspace_rollout_external_kpi_datamart_health_status":"healthy",
                         "workspace_rollout_external_kpi_datamart_health_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_health_updated_at":"2020-01-01T00:00:00Z",
                         "workspace_rollout_external_kpi_datamart_health_ttl_hours":24}
                        """);

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-fresh-open-" + i, "T-HEALTH-FRESH-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-fresh-frt-" + i, "T-HEALTH-FRESH-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-fresh-ttr-" + i, "T-HEALTH-FRESH-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-health-fresh-sla-" + i, "T-HEALTH-FRESH-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_health_required", true);
        assertThat(externalSignal).containsEntry("datamart_health_status", "healthy");
        assertThat(externalSignal).containsEntry("datamart_health_ready", true);
        assertThat(externalSignal).containsEntry("datamart_health_freshness_required", true);
        assertThat(externalSignal).containsEntry("datamart_health_fresh", false);
        assertThat(externalSignal).containsEntry("datamart_health_freshness_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }


    @Test
    void workspaceRolloutDecisionHoldsWhenDatamartProgramFreshnessRequiredAndStatusStale() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_program_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_program_status":"in_progress",
                         "workspace_rollout_external_kpi_datamart_program_updated_at":"2020-01-01T00:00:00Z",
                         "workspace_rollout_external_kpi_datamart_program_ttl_hours":24}
                        """);

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-program-fresh-open-" + i, "T-PROGRAM-FRESH-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-program-fresh-frt-" + i, "T-PROGRAM-FRESH-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-program-fresh-ttr-" + i, "T-PROGRAM-FRESH-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-program-fresh-sla-" + i, "T-PROGRAM-FRESH-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_program_freshness_required", true);
        assertThat(externalSignal).containsEntry("datamart_program_fresh", false);
        assertThat(externalSignal).containsEntry("datamart_program_freshness_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenDatamartProgramBlockedAndBlockerUrlMissing() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_program_blocker_required":true,
                         "workspace_rollout_external_kpi_datamart_program_status":"blocked"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_program_blocked", true);
        assertThat(externalSignal).containsEntry("datamart_program_blocker_url_present", false);
        assertThat(externalSignal).containsEntry("datamart_program_blocker_ready", false);
        assertThat(externalSignal).containsEntry("datamart_program_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }

    @Test
    void workspaceRolloutDecisionAllowsBlockedDatamartWhenProgramBlockerGateDisabled() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_program_blocker_required":false,
                         "workspace_rollout_external_kpi_datamart_program_status":"blocked",
                         "workspace_rollout_external_kpi_datamart_program_blocker_url":"https://jira.example.com/browse/BI-1234"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(externalSignal).containsEntry("datamart_program_blocked", true);
        assertThat(externalSignal).containsEntry("datamart_program_blocker_url_present", true);
        assertThat(externalSignal).containsEntry("datamart_program_blocker_url_valid", true);
        assertThat(externalSignal).containsEntry("datamart_program_ready", true);
        assertThat(externalSignal).containsEntry("ready_for_decision", true);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenDependencyTicketUrlIsInvalid() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_url":"mailto:bi-owner@example.com"}
                        """);

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-open-" + i, "T-DEPENDENCY-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-frt-" + i, "T-DEPENDENCY-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-ttr-" + i, "T-DEPENDENCY-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-sla-" + i, "T-DEPENDENCY-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_required", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_present", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_valid", false);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenDependencyTicketUpdateIsStale() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_url":"https://jira.example.com/browse/BI-1234",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at":"2020-01-01T00:00:00Z",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours":24}
                        """);

        for (int i = 0; i < 40; i++) {
            String cohort = i < 20 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'workspace_open_ms', 'performance', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', NULL, NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-stale-open-" + i, "T-DEPENDENCY-STALE-OPEN-" + i, openMs, cohort);
        }

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_frt_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-stale-frt-" + i, "T-DEPENDENCY-STALE-FRT-" + i, cohort.equals("test") ? 1100L : 1200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_ttr_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', ?,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-stale-ttr-" + i, "T-DEPENDENCY-STALE-TTR-" + i, cohort.equals("test") ? 2000L : 2200L, cohort);
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, 'kpi_sla_breach_recorded', 'kpi', ?, NULL, NULL, 'workspace.v1', NULL,
                              'workspace_v1_rollout', ?, 'team=ops;shift=day', 'frt,ttr,sla_breach', NULL, NULL, NULL, datetime('now', '-1 hour'))
                    """, "op-dependency-stale-sla-" + i, "T-DEPENDENCY-STALE-SLA-" + i, cohort);
        }

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_freshness_required", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_updated_present", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_fresh", false);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_freshness_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenDependencyTicketOwnerContactIsNotActionable() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact":"BI owner"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_owner_contact_actionable_required", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_owner_contact_actionable", false);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_owner_contact_actionable_ready", false);
        assertThat(externalSignal).containsEntry("ready_for_decision", false);
    }

    @Test
    void workspaceRolloutDecisionAllowsWhenDependencyTicketOwnerContactIsActionable() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact":"@bi-oncall"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_owner_contact_actionable_required", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_owner_contact_actionable", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_owner_contact_actionable_ready", true);
        assertThat(externalSignal).containsEntry("ready_for_decision", true);
        assertThat(externalSignal).containsEntry("datamart_risk_level", "low");
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons")).isEmpty();
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenDatamartContractMandatoryFieldMissing() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_contract_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_mandatory_fields":"frt,ttr,sla_breach,cost_per_contact",
                         "workspace_rollout_external_kpi_datamart_contract_available_fields":"frt,ttr,sla_breach"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_contract_required", true);
        assertThat(externalSignal).containsEntry("datamart_contract_ready", false);
        assertThat((java.util.List<String>) externalSignal.get("datamart_contract_missing_mandatory_fields"))
                .containsExactly("cost_per_contact");
        assertThat((java.util.List<String>) externalSignal.get("datamart_contract_missing_optional_fields")).isEmpty();
        assertThat(externalSignal).containsEntry("datamart_contract_mandatory_coverage_pct", 75);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_pct", 100);
        assertThat(externalSignal).containsEntry("datamart_contract_blocking_gap_count", 1);
        assertThat(externalSignal).containsEntry("datamart_contract_non_blocking_gap_count", 0);
        assertThat(externalSignal).containsEntry("datamart_contract_gap_severity", "blocking");
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("datamart_contract_missing_mandatory_fields");
    }

    @Test
    void workspaceRolloutDecisionAllowsWhenDatamartContractMandatoryFieldsCovered() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_contract_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_version":"v2",
                         "workspace_rollout_external_kpi_datamart_contract_mandatory_fields":"frt,ttr,sla_breach,cost_per_contact",
                         "workspace_rollout_external_kpi_datamart_contract_optional_fields":"dialogs_per_shift,csat",
                         "workspace_rollout_external_kpi_datamart_contract_available_fields":"frt,ttr,sla_breach,cost_per_contact,dialogs_per_shift"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(externalSignal).containsEntry("datamart_contract_required", true);
        assertThat(externalSignal).containsEntry("datamart_contract_ready", true);
        assertThat(externalSignal).containsEntry("datamart_contract_version", "v2");
        assertThat((java.util.List<String>) externalSignal.get("datamart_contract_missing_mandatory_fields")).isEmpty();
        assertThat((java.util.List<String>) externalSignal.get("datamart_contract_missing_optional_fields"))
                .containsExactly("csat");
        assertThat(externalSignal).containsEntry("datamart_contract_mandatory_coverage_pct", 100);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_pct", 50);
        assertThat(externalSignal).containsEntry("datamart_contract_blocking_gap_count", 0);
        assertThat(externalSignal).containsEntry("datamart_contract_non_blocking_gap_count", 1);
        assertThat(externalSignal).containsEntry("datamart_contract_gap_severity", "non_blocking");
        assertThat(externalSignal).containsEntry("ready_for_decision", true);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenDatamartOptionalCoverageThresholdIsNotMet() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_contract_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_mandatory_fields":"frt,ttr,sla_breach,cost_per_contact",
                         "workspace_rollout_external_kpi_datamart_contract_optional_fields":"dialogs_per_shift,csat",
                         "workspace_rollout_external_kpi_datamart_contract_available_fields":"frt,ttr,sla_breach,cost_per_contact,dialogs_per_shift",
                         "workspace_rollout_external_kpi_datamart_contract_optional_coverage_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct":80}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_contract_mandatory_coverage_pct", 100);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_pct", 50);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_required", true);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_min_coverage_pct", 80);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_ready", false);
        assertThat(externalSignal).containsEntry("datamart_contract_ready", false);
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("datamart_contract_optional_coverage_below_threshold");
    }

    @Test
    void workspaceRolloutDecisionUsesSafeDefaultForOptionalCoverageThreshold() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_contract_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_mandatory_fields":"frt,ttr,sla_breach,cost_per_contact",
                         "workspace_rollout_external_kpi_datamart_contract_optional_fields":"dialogs_per_shift,csat",
                         "workspace_rollout_external_kpi_datamart_contract_available_fields":"frt,ttr,sla_breach,cost_per_contact,dialogs_per_shift",
                         "workspace_rollout_external_kpi_datamart_contract_optional_coverage_required":true}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_required", true);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_min_coverage_pct", 80);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_pct", 50);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_ready", false);
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("datamart_contract_optional_coverage_below_threshold");
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenDatamartContractHasOverlappingFields() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_contract_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_mandatory_fields":"frt,ttr",
                         "workspace_rollout_external_kpi_datamart_contract_optional_fields":"ttr,csat",
                         "workspace_rollout_external_kpi_datamart_contract_available_fields":"frt,ttr,csat,cost_per_contact"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_contract_required", true);
        assertThat(externalSignal).containsEntry("datamart_contract_configuration_conflict", true);
        assertThat(externalSignal).containsEntry("datamart_contract_ready", false);
        assertThat((java.util.List<String>) externalSignal.get("datamart_contract_overlapping_fields"))
                .containsExactly("ttr");
        assertThat((java.util.List<String>) externalSignal.get("datamart_contract_available_outside_fields"))
                .containsExactly("cost_per_contact");
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("datamart_contract_configuration_conflict");
    }

    @Test
    void workspaceRolloutDecisionFlagsInvalidUtcTimestampsFromExternalKpiConfig() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"bad-value",
                         "workspace_rollout_external_kpi_data_freshness_required":true,
                         "workspace_rollout_external_kpi_data_updated_at":"2026-99-99T25:61"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("review_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("data_updated_timestamp_invalid", true);
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("review_timestamp_invalid", "data_updated_timestamp_invalid");
    }

    @Test
    void workspaceRolloutDecisionTreatsLegacyDatetimeLocalAsUtc() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00",
                         "workspace_rollout_external_kpi_review_ttl_hours":24}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "scale_up");
        assertThat(externalSignal).containsEntry("review_timestamp_invalid", false);
        assertThat(externalSignal).containsEntry("reviewed_at", "2099-01-01T00:00Z");
        assertThat(externalSignal).containsEntry("review_present", true);
    }

    @Test
    void settingsBridgeNormalizesExternalKpiUtcTimestampsAndPreservesInvalidValuesForAnalytics() {
        Map<String, Object> response = settingsBridgeController.updateSettings(Map.of(
                "dialog_workspace_rollout_external_kpi_gate_enabled", true,
                "dialog_workspace_rollout_external_kpi_omnichannel_ready", true,
                "dialog_workspace_rollout_external_kpi_finance_ready", true,
                "dialog_workspace_rollout_external_kpi_reviewed_by", "release-oncall",
                "dialog_workspace_rollout_external_kpi_reviewed_at", "2099-01-01T00:00",
                "dialog_workspace_rollout_external_kpi_data_updated_at", "2099-01-01T03:00:00+03:00",
                "dialog_workspace_rollout_external_kpi_datamart_program_updated_at", "bad-value",
                "dialog_workspace_rollout_external_kpi_datamart_target_ready_at", ""
        ), null);

        assertThat(response).containsEntry("success", true);
        assertThat((List<String>) response.get("warnings"))
                .anyMatch(message -> message.contains("программного статуса data-mart"));

        Map<String, Object> dialogConfig = (Map<String, Object>) sharedConfigService.loadSettings().get("dialog_config");
        assertThat(dialogConfig).containsEntry("workspace_rollout_external_kpi_reviewed_at", "2099-01-01T00:00Z");
        assertThat(dialogConfig).containsEntry("workspace_rollout_external_kpi_data_updated_at", "2099-01-01T00:00Z");
        assertThat(dialogConfig).containsEntry("workspace_rollout_external_kpi_datamart_program_updated_at", "bad-value");
        assertThat(dialogConfig).containsEntry("workspace_rollout_external_kpi_datamart_target_ready_at", "");

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(externalSignal).containsEntry("review_timestamp_invalid", false);
        assertThat(externalSignal).containsEntry("reviewed_at", "2099-01-01T00:00Z");
        assertThat(externalSignal).containsEntry("data_updated_timestamp_invalid", false);
        assertThat(externalSignal).containsEntry("data_updated_at", "2099-01-01T00:00Z");
        assertThat(externalSignal).containsEntry("datamart_program_updated_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("datamart_program_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("datamart_target_timestamp_invalid", false);
        assertThat(externalSignal).containsEntry("dependency_ticket_timestamp_invalid", false);
        assertThat(externalSignal).containsEntry("datamart_health_timestamp_invalid", false);
        assertThat((List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("datamart_program_timestamp_invalid");
    }

    @Test
    void settingsBridgeRejectsOptionalCoverageGateWithoutOptionalFields() {
        Map<String, Object> response = settingsBridgeController.updateSettings(Map.of(
                "dialog_workspace_rollout_external_kpi_datamart_contract_required", true,
                "dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields", "frt,ttr,sla_breach",
                "dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields", "",
                "dialog_workspace_rollout_external_kpi_datamart_contract_optional_coverage_required", true,
                "dialog_workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct", 80
        ), null);

        assertThat(response).containsEntry("success", false);
        assertThat(String.valueOf(response.get("error")))
                .contains("optional coverage gate")
                .contains("optional KPI-поле");
        assertThat(sharedConfigService.loadSettings()).doesNotContainKey("dialog_config");
    }

    @Test
    void settingsBridgeRejectsDatamartContractFieldOverlap() {
        Map<String, Object> response = settingsBridgeController.updateSettings(Map.of(
                "dialog_workspace_rollout_external_kpi_datamart_contract_required", true,
                "dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields", "frt,ttr",
                "dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields", "ttr,csat",
                "dialog_workspace_rollout_external_kpi_datamart_contract_available_fields", "frt,ttr,csat"
        ), null);

        assertThat(response).containsEntry("success", false);
        assertThat(String.valueOf(response.get("error")))
                .contains("mandatory и optional")
                .contains("ttr");
        assertThat(sharedConfigService.loadSettings()).doesNotContainKey("dialog_config");
    }

    @Test
    void settingsBridgeRejectsOptionalCoverageThresholdOutsideRange() {
        Map<String, Object> response = settingsBridgeController.updateSettings(Map.of(
                "dialog_workspace_rollout_external_kpi_datamart_contract_required", true,
                "dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields", "frt,ttr,sla_breach",
                "dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields", "csat",
                "dialog_workspace_rollout_external_kpi_datamart_contract_optional_coverage_required", true,
                "dialog_workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct", 120
        ), null);

        assertThat(response).containsEntry("success", false);
        assertThat(String.valueOf(response.get("error")))
                .contains("0..100%");
        assertThat(sharedConfigService.loadSettings()).doesNotContainKey("dialog_config");
    }

    @Test
    void workspaceRolloutDecisionPublishesCanonicalTimestampInvalidAliases() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_url":"https://tracker.example.com/BI-1",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at":"bad-ticket-ts",
                         "workspace_rollout_external_kpi_datamart_health_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_health_updated_at":"bad-health-ts",
                         "workspace_rollout_external_kpi_datamart_program_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_program_updated_at":"bad-program-ts"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("dependency_ticket_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("datamart_dependency_ticket_updated_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("datamart_health_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("datamart_health_updated_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("datamart_program_timestamp_invalid", true);
        assertThat(externalSignal).containsEntry("datamart_program_updated_timestamp_invalid", true);
        assertThat((List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("dependency_ticket_timestamp_invalid", "datamart_health_timestamp_invalid", "datamart_program_timestamp_invalid");
    }

    @Test
    void workspaceRolloutDecisionDoesNotApplyOptionalCoverageGateWhenContractIsDisabled() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_contract_required":false,
                         "workspace_rollout_external_kpi_datamart_contract_optional_fields":"dialogs_per_shift,csat",
                         "workspace_rollout_external_kpi_datamart_contract_available_fields":"dialogs_per_shift",
                         "workspace_rollout_external_kpi_datamart_contract_optional_coverage_required":true,
                         "workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct":80}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "scale_up");
        assertThat(externalSignal).containsEntry("datamart_contract_required", false);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_required", true);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_gate_active", false);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_pct", 50);
        assertThat(externalSignal).containsEntry("datamart_contract_optional_coverage_ready", true);
        assertThat(externalSignal).containsEntry("datamart_contract_ready", true);
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .doesNotContain("datamart_contract_optional_coverage_below_threshold");
    }

    @Test
    void workspaceRolloutDecisionMarksMediumRiskWhenSingleGateFails() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact":"BI owner"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_risk_level", "medium");
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .containsExactly("dependency_ticket_owner_contact_not_actionable");
    }

    @Test
    void workspaceRolloutDecisionMarksHighRiskWhenDatamartProgramBlocked() {
        jdbcTemplate.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                "dialog_config", """
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_program_blocker_required":true,
                         "workspace_rollout_external_kpi_datamart_program_status":"blocked"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> rolloutDecision = (Map<String, Object>) summary.get("rollout_decision");
        Map<String, Object> externalSignal = (Map<String, Object>) rolloutDecision.get("external_kpi_signal");

        assertThat(rolloutDecision).containsEntry("action", "hold");
        assertThat(externalSignal).containsEntry("datamart_program_blocked", true);
        assertThat(externalSignal).containsEntry("datamart_risk_level", "high");
        assertThat((java.util.List<String>) externalSignal.get("datamart_risk_reasons"))
                .contains("datamart_program_blocked");
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
