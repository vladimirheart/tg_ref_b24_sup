package com.example.panel.service;

import com.example.panel.controller.SettingsBridgeController;
import com.example.panel.controller.SettingsItEquipmentController;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeArticleSummary;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static Path sharedConfigDir;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-test", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-shared-config");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
        registry.add("shared-config.dir", () -> sharedConfigDir.toString());
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DialogService dialogService;

    @Autowired
    private SettingsBridgeController settingsBridgeController;

    @Autowired
    private SettingsItEquipmentController settingsItEquipmentController;

    @Autowired
    private SharedConfigService sharedConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private PublicFormService publicFormService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DialogReplyService dialogReplyService;

    @Autowired
    private DialogNotificationService dialogNotificationService;

    @BeforeEach
    void clean() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "settings-test",
                "n/a",
                new ArrayList<>(List.of(new SimpleGrantedAuthority("PAGE_SETTINGS")))
        ));
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
        jdbcTemplate.update("DELETE FROM app_settings");
        sharedConfigService.saveSettings(new LinkedHashMap<>());
    }

    private void saveDialogConfig(String rawJson) {
        try {
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("dialog_config", objectMapper.readValue(rawJson, MAP_TYPE));
            sharedConfigService.saveSettings(settings);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist dialog_config fixture", ex);
        }
    }

    private void recordWorkspaceTelemetryEvent(String actor,
                                               String eventType,
                                               String eventGroup,
                                               String ticketId,
                                               Long durationMs,
                                               OffsetDateTime createdAtUtc) {
        recordWorkspaceTelemetryEvent(actor, eventType, eventGroup, ticketId, null, null, durationMs,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, createdAtUtc);
    }

    private void recordWorkspaceTelemetryEvent(String actor,
                                               String eventType,
                                               String eventGroup,
                                               String ticketId,
                                               String reason,
                                               String errorCode,
                                               Long durationMs,
                                               String experimentName,
                                               String experimentCohort,
                                               String operatorSegment,
                                               String primaryKpis,
                                               String secondaryKpis,
                                               OffsetDateTime createdAtUtc) {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'workspace.v1', ?,
                          ?, ?, ?, ?, ?, NULL, NULL, ?)
                """,
                actor,
                eventType,
                eventGroup,
                ticketId,
                reason,
                errorCode,
                durationMs,
                experimentName,
                experimentCohort,
                operatorSegment,
                primaryKpis,
                secondaryKpis,
                Timestamp.from(createdAtUtc.toInstant()));
    }

    private void seedRolloutOpenEvents(String actorPrefix,
                                       String ticketPrefix,
                                       int totalEvents,
                                       long controlDurationMs,
                                       long testDurationMs,
                                       OffsetDateTime createdAtUtc) {
        for (int i = 0; i < totalEvents; i++) {
            String cohort = i < (totalEvents / 2) ? "control" : "test";
            long durationMs = "test".equals(cohort) ? testDurationMs : controlDurationMs;
            recordWorkspaceTelemetryEvent(actorPrefix + i,
                    "workspace_open_ms",
                    "performance",
                    ticketPrefix + i,
                    null,
                    null,
                    durationMs,
                    "workspace_v1_rollout",
                    cohort,
                    "team=ops;shift=day",
                    null,
                    null,
                    createdAtUtc);
        }
    }

    private void seedRolloutPrimaryKpiEvents(String actorPrefix,
                                             String ticketPrefix,
                                             int totalEvents,
                                             long controlFrtMs,
                                             long testFrtMs,
                                             long controlTtrMs,
                                             long testTtrMs,
                                             OffsetDateTime createdAtUtc) {
        for (int i = 0; i < totalEvents; i++) {
            String cohort = i < (totalEvents / 2) ? "control" : "test";
            recordWorkspaceTelemetryEvent(actorPrefix + "-frt-" + i,
                    "kpi_frt_recorded",
                    "kpi",
                    ticketPrefix + "-FRT-" + i,
                    null,
                    null,
                    "test".equals(cohort) ? testFrtMs : controlFrtMs,
                    "workspace_v1_rollout",
                    cohort,
                    "team=ops;shift=day",
                    "frt,ttr,sla_breach",
                    null,
                    createdAtUtc);
            recordWorkspaceTelemetryEvent(actorPrefix + "-ttr-" + i,
                    "kpi_ttr_recorded",
                    "kpi",
                    ticketPrefix + "-TTR-" + i,
                    null,
                    null,
                    "test".equals(cohort) ? testTtrMs : controlTtrMs,
                    "workspace_v1_rollout",
                    cohort,
                    "team=ops;shift=day",
                    "frt,ttr,sla_breach",
                    null,
                    createdAtUtc);
            recordWorkspaceTelemetryEvent(actorPrefix + "-sla-" + i,
                    "kpi_sla_breach_recorded",
                    "kpi",
                    ticketPrefix + "-SLA-" + i,
                    null,
                    null,
                    null,
                    "workspace_v1_rollout",
                    cohort,
                    "team=ops;shift=day",
                    "frt,ttr,sla_breach",
                    null,
                    createdAtUtc);
        }
    }

    private void recordMacroTelemetryEvent(String actor,
                                           String eventType,
                                           String ticketId,
                                           String templateId,
                                           String templateName,
                                           OffsetDateTime createdAtUtc) {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit (
                    actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                    duration_ms, experiment_name, experiment_cohort, operator_segment,
                    primary_kpis, secondary_kpis, template_id, template_name, created_at
                ) VALUES (?, ?, 'macro', ?, NULL, NULL, 'workspace.v1', NULL,
                          'workspace_v1_rollout', 'test', 'team=ops;shift=day', NULL, NULL, ?, ?, ?)
                """,
                actor,
                eventType,
                ticketId,
                templateId,
                templateName,
                Timestamp.from(createdAtUtc.toInstant()));
    }

    @Test
    void dialogServiceAggregatesStatsAndHistory() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at) VALUES (1, 'token', 'Demo', 1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (?,?,?,?)",
                1001L, "T-1", "pending", 1);
        jdbcTemplate.update("INSERT INTO messages (group_msg_id, user_id, business, city, location_name, problem, created_at, username, ticket_id, created_date, created_time, client_name, client_status, updated_at, updated_by, channel_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'tester', ?)",
                7001L, 1001L, "Food", "Москва", "Пиццерия", "Не работает терминал", "2026-03-19T13:40:00", "ivan", "T-1", "2026-03-19", "13:40:00", "Иван", "VIP", 1);
        jdbcTemplate.update("INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, channel_id) VALUES (?,?,?,?,?,?,?)",
                1001L, "user", "Добрый день", OffsetDateTime.now().toString(), "T-1", "text", 1);

        DialogSummary summary = dialogService.loadSummary();
        assertThat(summary.totalTickets()).isEqualTo(1);
        assertThat(summary.pendingTickets()).isEqualTo(1);
        assertThat(summary.channelStats()).extracting("name").contains("Demo");

        assertThat(dialogService.loadDialogs(null))
                .singleElement()
                .satisfies(dialog -> {
                    assertThat(dialog.ticketId()).isEqualTo("T-1");
                    assertThat(dialog.requestNumber()).isEqualTo(7001L);
                    assertThat(dialog.clientName()).isEqualTo("Иван");
                });

        assertThat(dialogService.findDialog("T-1", null))
                .get()
                .satisfies(dialog -> assertThat(dialog.requestNumber()).isEqualTo(7001L));

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
    void publicFormDialogSupportsOperatorRepliesThroughSharedLinkHistory() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, platform, is_active, created_at, public_id) VALUES (52, 'web-shared', 'Внешняя форма', 'vk', 1, CURRENT_TIMESTAMP, 'web-shared')");
        PublicFormSessionDto session = publicFormService.createSession(
                "web-shared",
                new PublicFormSubmission("Нужна помощь с заказом", "Мария", "+79990000001", "maria", null, Map.of("topic", "Заказ"), null),
                "web-shared-ip"
        );

        DialogReplyService.DialogReplyResult reply = dialogReplyService.sendReply(session.ticketId(), "Подскажите номер заказа, пожалуйста.", null, "operator");

        assertThat(reply.success()).isTrue();
        assertThat(reply.telegramMessageId()).isNull();
        assertThat(dialogService.loadHistory(session.ticketId(), null))
                .anySatisfy(message -> {
                    assertThat(message.sender()).isEqualTo("operator");
                    assertThat(message.message()).contains("Подскажите номер заказа");
                });
    }

    @Test
    void publicFormDialogStoresSystemNotificationsInSharedHistory() {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, platform, is_active, created_at, public_id) VALUES (53, 'web-system', 'Внешняя форма', 'max', 1, CURRENT_TIMESTAMP, 'web-system')");
        PublicFormSessionDto session = publicFormService.createSession(
                "web-system",
                new PublicFormSubmission("Нужна помощь", "Олег", "+79990000002", "oleg", null, Map.of(), null),
                "web-system-ip"
        );

        dialogNotificationService.notifyResolved(session.ticketId());

        assertThat(dialogService.loadHistory(session.ticketId(), null))
                .anySatisfy(message -> {
                    assertThat(message.sender()).isEqualTo("system");
                    assertThat(message.message()).contains("Диалог закрыт");
                })
                .anySatisfy(message -> {
                    assertThat(message.sender()).isEqualTo("system");
                    assertThat(message.message()).contains("оцените диалог");
                });
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
        saveDialogConfig("{\"public_form_rate_limit_use_fingerprint\":true}");

        String keyA = publicFormService.buildRequesterKey("same-ip", "browser-A");
        String keyB = publicFormService.buildRequesterKey("same-ip", "browser-B");
        assertThat(keyA).contains("same-ip|fp:");
        assertThat(keyA).isNotEqualTo(keyB);

        saveDialogConfig("{\"public_form_rate_limit_use_fingerprint\":false}");

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
        saveDialogConfig("{\"public_form_session_token_rotate_on_read\":true}");

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
        saveDialogConfig("{\"public_form_captcha_shared_secret\":\"captcha-123\"}");

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
        saveDialogConfig("{\"public_form_answers_total_max_length\":200}");

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
        saveDialogConfig("{\"public_form_strip_html_tags\":true}");

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
        saveDialogConfig("{\"public_form_strip_html_tags\":false}");

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
        saveDialogConfig("{\"public_form_alert_min_views\":1,\"public_form_alert_error_rate_threshold\":0.5,\"public_form_alert_captcha_failure_rate_threshold\":0.3,\"public_form_alert_rate_limit_rejection_rate_threshold\":0.3}");

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
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-1", null, null, 1200L,
                "workspace_v1_rollout", "test", "team=ops;shift=night", null, null, baseTime.minusDays(1));
        recordWorkspaceTelemetryEvent("op1", "workspace_render_error", "quality", "T-1", null, "render_failed", null,
                "workspace_v1_rollout", "test", "team=ops;shift=night", null, null, baseTime.minusDays(1));
        recordWorkspaceTelemetryEvent("op2", "workspace_open_ms", "performance", "T-2", null, null, 2300L,
                "workspace_v1_rollout", "test", "team=ops;shift=night", null, null, baseTime.minusDays(1));
        recordWorkspaceTelemetryEvent("op2", "workspace_fallback_to_legacy", "fallback", "T-2", "timeout", null, null,
                "workspace_v1_rollout", "test", "team=ops;shift=night", null, null, baseTime.minusDays(1));
        for (int i = 3; i <= 12; i++) {
            long duration = switch (i) {
                case 3 -> 900L;
                case 4 -> 950L;
                case 5 -> 980L;
                case 6 -> 1020L;
                case 7 -> 1000L;
                case 8 -> 970L;
                case 9 -> 1030L;
                case 10 -> 1010L;
                case 11 -> 990L;
                default -> 1015L;
            };
            recordWorkspaceTelemetryEvent("op" + (i - 2), "workspace_open_ms", "performance", "T-" + i, null, null, duration,
                    "workspace_v1_rollout", "test", "team=ops;shift=night", null, null, baseTime.minusDays(8));
        }
        for (int i = 13; i <= 22; i++) {
            long duration = switch (i) {
                case 13 -> 1110L;
                case 14 -> 1090L;
                case 15 -> 1080L;
                case 16 -> 1100L;
                case 17 -> 1130L;
                case 18 -> 1075L;
                case 19 -> 1060L;
                case 20 -> 1050L;
                case 21 -> 1140L;
                default -> 1120L;
            };
            recordWorkspaceTelemetryEvent("op" + (i - 2), "workspace_open_ms", "performance", "T-" + i, null, null, duration,
                    "workspace_v1_rollout", "control", "team=ops;shift=night", null, null, baseTime.minusDays(8));
        }

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
            assertThat(alert).containsKey("value");
            assertThat(alert).containsKey("threshold");
        });
    }

    @Test
    void workspaceTelemetrySummaryIncludesCustomerContextGapRates() {
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-CONTEXT-1", null, null, 900L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(2));
        recordWorkspaceTelemetryEvent("op2", "workspace_open_ms", "performance", "T-CONTEXT-2", null, null, 950L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(1));
        recordWorkspaceTelemetryEvent("op2", "workspace_context_profile_gap", "workspace", "T-CONTEXT-2", "last_message_at", null, 1L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(1));
        recordWorkspaceTelemetryEvent("op2", "workspace_context_source_gap", "workspace", "T-CONTEXT-2", "contract:invalid_utc", null, 1L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(1));
        recordWorkspaceTelemetryEvent("op3", "workspace_context_profile_gap", "workspace", "T-CONTEXT-3", "", null, 0L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(30));

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
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-BLOCK-1", null, null, 910L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(2));
        recordWorkspaceTelemetryEvent("op2", "workspace_open_ms", "performance", "T-BLOCK-2", null, null, 940L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(1));
        recordWorkspaceTelemetryEvent("op2", "workspace_context_block_gap", "workspace", "T-BLOCK-2", "context_sources,customer_profile", null, 2L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(1));
        recordWorkspaceTelemetryEvent("op2", "workspace_parity_gap", "workspace", "T-BLOCK-2", "attachments", null, 75L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(45));

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
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-SLA-1", null, null, 910L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(2));
        recordWorkspaceTelemetryEvent("op2", "workspace_open_ms", "performance", "T-SLA-2", null, null, 940L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(1));
        recordWorkspaceTelemetryEvent("op2", "workspace_sla_policy_gap", "workspace", "T-SLA-2", "fallback_assignee_missing", null, 5L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(1));

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
        String reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0).toString();
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,
                         "workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,
                         "workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"%s",
                         "workspace_rollout_external_kpi_review_ttl_hours":999}
                        """.formatted(reviewedAtUtc));

        for (int i = 0; i < 70; i++) {
            String cohort = i < 35 ? "control" : "test";
            long openMs = cohort.equals("test") ? 900L : 1000L;
            recordWorkspaceTelemetryEvent("op-score-open-" + i, "workspace_open_ms", "performance", "T-SCORE-OPEN-" + i,
                    null, null, openMs, "workspace_v1_rollout", cohort, "team=ops;shift=day", null, null,
                    OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        }
        for (int i = 0; i < 16; i++) {
            String cohort = i < 8 ? "control" : "test";
            OffsetDateTime eventTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
            recordWorkspaceTelemetryEvent("op-score-frt-" + i, "kpi_frt_recorded", "kpi", "T-SCORE-FRT-" + i,
                    null, null, cohort.equals("test") ? 900L : 1000L, "workspace_v1_rollout", cohort,
                    "team=ops;shift=day", "frt,ttr,sla_breach", null, eventTime);
            recordWorkspaceTelemetryEvent("op-score-ttr-" + i, "kpi_ttr_recorded", "kpi", "T-SCORE-TTR-" + i,
                    null, null, cohort.equals("test") ? 1900L : 2000L, "workspace_v1_rollout", cohort,
                    "team=ops;shift=day", "frt,ttr,sla_breach", null, eventTime);
            recordWorkspaceTelemetryEvent("op-score-sla-" + i, "kpi_sla_breach_recorded", "kpi", "T-SCORE-SLA-" + i,
                    null, null, null, "workspace_v1_rollout", cohort, "team=ops;shift=day",
                    "frt,ttr,sla_breach", null, eventTime);
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
                assertThat(item.get("measured_at")).isEqualTo(reviewedAtUtc);
            }
            if ("external_review".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo(reviewedAtUtc);
            }
            if ("external_data_freshness".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("off");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryBuildsGovernancePacketWithOwnerSignoffInUtc() {
        String ownerSignoffAtUtc = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0).toString();
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_owner_signoff_required":true,
                         "workspace_rollout_governance_owner_signoff_by":"ops-director",
                         "workspace_rollout_governance_owner_signoff_at":"%s",
                         "workspace_rollout_governance_owner_signoff_ttl_hours":999}
                        """.formatted(ownerSignoffAtUtc));
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-1", null, null, 880L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(2));
        recordWorkspaceTelemetryEvent("op1", "workspace_parity_gap", "workspace", "T-PACKET-1", "attachments", null, 20L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(90));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");
        Map<String, Object> ownerSignoff = (Map<String, Object>) packet.get("owner_signoff");
        Map<String, Object> paritySnapshot = (Map<String, Object>) packet.get("parity_snapshot");

        assertThat(packet.get("required")).isEqualTo(true);
        assertThat(packet.get("packet_ready")).isEqualTo(true);
        assertThat(packet).containsKeys("status", "items", "review_cadence", "parity_exit_criteria");
        assertThat(items).anySatisfy(item -> {
            if ("owner_signoff".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo(ownerSignoffAtUtc);
                assertThat(item.get("current_value")).isEqualTo("signed_by=ops-director");
            }
        });
        assertThat(ownerSignoff).containsEntry("required", true);
        assertThat(ownerSignoff).containsEntry("ready", true);
        assertThat(ownerSignoff).containsEntry("signed_by", "ops-director");
        assertThat(ownerSignoff).containsEntry("signed_at", ownerSignoffAtUtc);
        assertThat(paritySnapshot).containsEntry("ready", true);
        assertThat(paritySnapshot).containsEntry("workspace_open_events", 1L);
        assertThat(paritySnapshot).containsEntry("parity_gap_events", 1L);
    }

    @Test
    void workspaceTelemetrySummaryFlagsGovernancePacketInvalidOwnerSignoffDate() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_owner_signoff_required":true,
                         "workspace_rollout_governance_owner_signoff_by":"ops-director",
                         "workspace_rollout_governance_owner_signoff_at":"bad-owner-ts"}
                        """);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-INVALID", null, null, 910L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

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
    void workspaceTelemetrySummaryBuildsGovernanceCadenceAndParityExitInUtc() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_review_cadence_days":7,
                         "workspace_rollout_governance_reviewed_by":"ops-oncall",
                         "workspace_rollout_governance_reviewed_at":"2099-02-04T10:11:12Z",
                         "workspace_rollout_governance_parity_exit_days":3,
                         "workspace_rollout_governance_parity_critical_reasons":["reply_threading","permissions"],
                         "workspace_rollout_governance_legacy_only_scenarios":[],
                         "workspace_rollout_external_kpi_gate_enabled":true,
                         "workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,
                         "workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-02-04T10:11:12Z",
                         "workspace_rollout_external_kpi_review_ttl_hours":24}
                        """);
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-GOV-1", null, null, 810L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(2));
        recordWorkspaceTelemetryEvent("op1", "workspace_parity_gap", "workspace", "T-PACKET-GOV-1", "attachments", null, 10L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(90));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");
        Map<String, Object> reviewCadence = (Map<String, Object>) packet.get("review_cadence");
        Map<String, Object> parityExitCriteria = (Map<String, Object>) packet.get("parity_exit_criteria");
        Map<String, Object> legacyInventory = (Map<String, Object>) packet.get("legacy_only_inventory");

        assertThat(packet).containsKeys("status", "items", "review_cadence", "parity_exit_criteria");
        assertThat(reviewCadence).containsEntry("enabled", true);
        assertThat(reviewCadence).containsEntry("ready", true);
        assertThat(reviewCadence).containsEntry("reviewed_by", "ops-oncall");
        assertThat(reviewCadence).containsEntry("reviewed_at", "2099-02-04T10:11:12Z");
        assertThat(reviewCadence).containsEntry("decision_go_events_in_window", 0L);
        assertThat(reviewCadence).containsEntry("decision_hold_events_in_window", 0L);
        assertThat(reviewCadence).containsEntry("decision_rollback_events_in_window", 0L);
        assertThat(reviewCadence).containsEntry("incident_followup_linked_events_in_window", 0L);
        assertThat(parityExitCriteria).containsEntry("enabled", true);
        assertThat(parityExitCriteria).containsEntry("ready", false);
        assertThat(parityExitCriteria).containsEntry("critical_gap_events", 0L);
        assertThat(parityExitCriteria).containsEntry("error", "telemetry_unavailable");
        assertThat((List<String>) parityExitCriteria.get("critical_reasons")).containsExactly("reply_threading", "permissions");
        assertThat((List<String>) packet.get("legacy_only_scenarios")).isEmpty();
        assertThat(legacyInventory).containsEntry("reviewed_at", "");
        assertThat(legacyInventory).containsEntry("review_timestamp_invalid", false);
        assertThat(items).anySatisfy(item -> {
            if ("weekly_review".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo("2099-02-04T10:11:12Z");
            }
            if ("parity_exit_criteria".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(item.get("current_value")).isEqualTo("critical_gaps=0");
            }
            if ("legacy_only_inventory".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("current_value")).isEqualTo("none");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryBlocksGovernancePacketForStaleReviewAndLegacyInventory() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_review_cadence_days":7,
                         "workspace_rollout_governance_reviewed_by":"ops-oncall",
                         "workspace_rollout_governance_reviewed_at":"bad-review-ts",
                         "workspace_rollout_governance_legacy_only_scenarios":["attachments_edit","inline_reopen"]}
                        """);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-GOV-2", null, null, 910L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");
        Map<String, Object> reviewCadence = (Map<String, Object>) packet.get("review_cadence");
        Map<String, Object> legacyInventory = (Map<String, Object>) packet.get("legacy_only_inventory");

        assertThat(packet.get("packet_ready")).isEqualTo(false);
        assertThat(packet.get("status")).isEqualTo("hold");
        assertThat((List<String>) packet.get("missing_items")).contains("weekly_review", "legacy_only_inventory");
        assertThat(reviewCadence).containsEntry("timestamp_invalid", true);
        assertThat((List<String>) packet.get("legacy_only_scenarios")).containsExactly("attachments_edit", "inline_reopen");
        assertThat(legacyInventory).containsEntry("review_timestamp_invalid", false);
        assertThat(legacyInventory).containsEntry("reviewed_at", "");
        assertThat(legacyInventory).containsEntry("managed", false);
        assertThat(legacyInventory).containsEntry("review_fresh", false);
        assertThat(legacyInventory).containsEntry("repeat_review_required", true);
        assertThat(legacyInventory).containsEntry("repeat_review_reason", "review_missing");
        assertThat(legacyInventory).containsEntry("repeat_review_due_at_utc", "");
        assertThat(legacyInventory).containsEntry("repeat_review_overdue_days", 0L);
        assertThat(legacyInventory).containsEntry("closure_rate_pct", 0L);
        assertThat(legacyInventory).containsEntry("review_queue_count", 2);
        assertThat(legacyInventory).containsEntry("review_queue_followup_required", true);
        assertThat(legacyInventory).containsEntry("review_queue_repeat_cycles", 1L);
        assertThat(legacyInventory).containsEntry("review_queue_oldest_deadline_at_utc", "");
        assertThat(legacyInventory).containsEntry("review_queue_oldest_overdue_days", 0L);
        assertThat(legacyInventory).containsEntry("review_queue_closure_pressure", "moderate");
        assertThat(legacyInventory).containsEntry("review_queue_escalation_required", false);
        assertThat((List<String>) legacyInventory.get("review_queue_escalated_scenarios")).isEmpty();
        assertThat(legacyInventory).containsEntry("review_queue_consolidation_required", false);
        assertThat(legacyInventory).containsEntry("review_queue_consolidation_count", 2);
        assertThat((List<String>) legacyInventory.get("review_queue_consolidation_candidates"))
                .containsExactly("attachments_edit", "inline_reopen");
        assertThat(legacyInventory).containsEntry("review_queue_next_action_summary", "Назначьте owner для всех legacy-only сценариев.");
        assertThat(String.valueOf(legacyInventory.get("review_queue_summary"))).contains("weekly closure review");
        assertThat((List<String>) legacyInventory.get("review_queue_scenarios")).containsExactly("attachments_edit", "inline_reopen");
        assertThat((List<String>) legacyInventory.get("overdue_scenarios")).isEmpty();
        assertThat(legacyInventory).containsEntry("unmanaged_count", 2L);
        assertThat(items).anySatisfy(item -> {
            if ("weekly_review".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(item.get("current_value")).isEqualTo("invalid_utc");
            }
            if ("legacy_only_inventory".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(String.valueOf(item.get("current_value"))).contains("open=2");
                assertThat(String.valueOf(item.get("current_value"))).contains("managed=0/2");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryMarksManagedLegacyInventoryAsAttention() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_legacy_only_scenarios":["attachments_edit","inline_reopen"],
                         "workspace_rollout_governance_legacy_inventory_reviewed_by":"ops-oncall",
                         "workspace_rollout_governance_legacy_inventory_reviewed_at":"2099-03-04T10:11:12Z",
                         "workspace_rollout_governance_legacy_only_scenario_metadata":{
                           "attachments_edit":{"owner":"workspace-core","deadline_at_utc":"2099-04-01T00:00:00Z","note":"composer parity"},
                           "inline_reopen":{"owner":"workspace-core","deadline_at_utc":"2099-04-05T00:00:00Z","note":"queue controls"}
                         }}
                        """);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-GOV-2A", null, null, 910L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> legacyInventory = (Map<String, Object>) packet.get("legacy_only_inventory");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");

        assertThat(packet.get("status")).isEqualTo("hold");
        assertThat((List<String>) packet.get("missing_items")).contains("legacy_only_inventory");
        assertThat(legacyInventory).containsEntry("managed", true);
        assertThat(legacyInventory).containsEntry("managed_count", 2L);
        assertThat(legacyInventory).containsEntry("review_fresh", true);
        assertThat(legacyInventory).containsEntry("managed_coverage_pct", 100L);
        assertThat(legacyInventory).containsEntry("closure_rate_pct", 100L);
        assertThat(legacyInventory).containsEntry("owner_coverage_pct", 100L);
        assertThat(legacyInventory).containsEntry("deadline_coverage_pct", 100L);
        assertThat(legacyInventory).containsEntry("repeat_review_required", false);
        assertThat(legacyInventory).containsEntry("unmanaged_count", 0L);
        assertThat(legacyInventory).containsEntry("review_queue_count", 0);
        assertThat(legacyInventory).containsEntry("review_queue_followup_required", false);
        assertThat(legacyInventory).containsEntry("review_queue_repeat_cycles", 0L);
        assertThat(legacyInventory).containsEntry("review_queue_oldest_deadline_at_utc", "");
        assertThat(legacyInventory).containsEntry("review_queue_oldest_overdue_days", 0L);
        assertThat(legacyInventory).containsEntry("review_queue_closure_pressure", "none");
        assertThat(legacyInventory).containsEntry("review_queue_escalation_required", false);
        assertThat((List<String>) legacyInventory.get("review_queue_escalated_scenarios")).isEmpty();
        assertThat(legacyInventory).containsEntry("review_queue_consolidation_required", false);
        assertThat(legacyInventory).containsEntry("review_queue_consolidation_count", 0);
        assertThat((List<String>) legacyInventory.get("review_queue_consolidation_candidates")).isEmpty();
        assertThat(legacyInventory).containsEntry("review_queue_next_action_summary", "Legacy review-queue не требует follow-up.");
        assertThat(legacyInventory).containsEntry("review_queue_summary", "");
        assertThat((List<String>) legacyInventory.get("review_queue_scenarios")).isEmpty();
        assertThat((List<String>) legacyInventory.get("overdue_scenarios")).isEmpty();
        assertThat((List<String>) legacyInventory.get("action_items")).isEmpty();
        assertThat(items).anySatisfy(item -> {
            if ("legacy_only_inventory".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("attention");
                assertThat(String.valueOf(item.get("current_value"))).contains("managed=2/2");
                assertThat(String.valueOf(item.get("note"))).contains("sunset_plan=managed");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryRequiresDecisionAndIncidentFollowupWhenConfigured() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_review_cadence_days":7,
                         "workspace_rollout_governance_reviewed_by":"ops-oncall",
                         "workspace_rollout_governance_reviewed_at":"2099-02-04T10:11:12",
                         "workspace_rollout_governance_review_decision_required":true,
                         "workspace_rollout_governance_incident_followup_required":true}
                        """);
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-GOV-3", null, null, 1200L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(20));
        recordWorkspaceTelemetryEvent("op1", "workspace_render_error", "guardrail", "T-PACKET-GOV-3", null, null, 0L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(18));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> reviewCadence = (Map<String, Object>) packet.get("review_cadence");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");

        assertThat(packet.get("status")).isEqualTo("hold");
        assertThat((List<String>) packet.get("missing_items")).contains("weekly_review");
        assertThat(reviewCadence).containsEntry("decision_required", true);
        assertThat(reviewCadence).containsEntry("incident_followup_required", true);
        assertThat(reviewCadence).containsEntry("decision_action", "");
        assertThat(reviewCadence).containsEntry("incident_followup", "");
        assertThat(items).anySatisfy(item -> {
            if ("weekly_review".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(String.valueOf(item.get("threshold"))).contains("decision required");
                assertThat(String.valueOf(item.get("threshold"))).contains("incident follow-up required");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryIncludesWeeklyDecisionTelemetryCounters() {
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("ops", "workspace_rollout_review_confirmed", "experiment", null,
                "analytics_weekly_review", null, null, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, null, baseTime.minusMinutes(20));
        recordWorkspaceTelemetryEvent("ops", "workspace_rollout_review_decision_go", "experiment", null,
                "analytics_weekly_review_decision", null, null, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, null, baseTime.minusMinutes(19));
        recordWorkspaceTelemetryEvent("ops", "workspace_rollout_review_decision_hold", "experiment", null,
                "analytics_weekly_review_decision", null, null, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, null, baseTime.minusMinutes(18));
        recordWorkspaceTelemetryEvent("ops", "workspace_rollout_review_decision_rollback", "experiment", null,
                "analytics_weekly_review_decision", null, null, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, null, baseTime.minusMinutes(17));
        recordWorkspaceTelemetryEvent("ops", "workspace_rollout_review_incident_followup_linked", "experiment", null,
                "analytics_weekly_review_incident_followup", null, null, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, null, baseTime.minusMinutes(16));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> reviewCadence = (Map<String, Object>) packet.get("review_cadence");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");

        assertThat(totals.get("workspace_rollout_review_decision_go_events")).isEqualTo(1L);
        assertThat(totals.get("workspace_rollout_review_decision_hold_events")).isEqualTo(1L);
        assertThat(totals.get("workspace_rollout_review_decision_rollback_events")).isEqualTo(1L);
        assertThat(totals.get("workspace_rollout_review_incident_followup_linked_events")).isEqualTo(1L);
        assertThat(reviewCadence).containsEntry("confirmed_events_in_window", 1L);
        assertThat(reviewCadence).containsEntry("decision_go_events_in_window", 1L);
        assertThat(reviewCadence).containsEntry("decision_hold_events_in_window", 1L);
        assertThat(reviewCadence).containsEntry("decision_rollback_events_in_window", 1L);
        assertThat(reviewCadence).containsEntry("incident_followup_linked_events_in_window", 1L);
    }

    @Test
    void workspaceTelemetrySummaryMarksLegacyInventoryInvalidUtc() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":false,
                         "workspace_rollout_governance_legacy_only_scenarios":["attachments_edit"],
                         "workspace_rollout_governance_legacy_inventory_reviewed_by":"ops-oncall",
                         "workspace_rollout_governance_legacy_inventory_reviewed_at":"bad-legacy-ts"}
                        """);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> legacyInventory = (Map<String, Object>) packet.get("legacy_only_inventory");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");

        assertThat(legacyInventory).containsEntry("review_timestamp_invalid", true);
        assertThat((List<String>) packet.get("invalid_utc_items")).contains("legacy_only_inventory");
        assertThat(items).anySatisfy(item -> {
            if ("legacy_only_inventory".equals(item.get("key"))) {
                assertThat((String) item.get("note")).contains("invalid_utc");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryIncludesContextMinimumProfileContract() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_context_contract_required":true,
                         "workspace_rollout_context_contract_scenarios":["incident","billing"],
                         "workspace_rollout_context_contract_mandatory_fields":["full_name","crm_tier"],
                         "workspace_rollout_context_contract_source_of_truth":["full_name:crm","crm_tier:crm"],
                         "workspace_rollout_context_contract_priority_blocks":["customer","sla"],
                         "workspace_rollout_context_contract_playbooks":{
                           "mandatory_field:full_name":{"label":"Name playbook","url":"https://wiki.example.local/context/name"},
                           "mandatory_field:crm_tier":{"label":"Tier playbook","url":"https://wiki.example.local/context/tier"},
                           "source_of_truth":{"label":"Source guide","url":"https://wiki.example.local/context/source"},
                           "priority_block":{"label":"Priority block guide","url":"https://wiki.example.local/context/priority"}
                         },
                         "workspace_rollout_context_contract_reviewed_by":"ops-context-owner",
                         "workspace_rollout_context_contract_reviewed_at":"2099-03-01T09:10:11Z"}
                        """);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-CONTEXT-1", null, null, 810L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> contextContract = (Map<String, Object>) packet.get("context_contract");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");

        assertThat(contextContract).containsEntry("enabled", true);
        assertThat(contextContract).containsEntry("required", true);
        assertThat(contextContract).containsEntry("ready", true);
        assertThat(contextContract).containsEntry("reviewed_by", "ops-context-owner");
        assertThat(contextContract).containsEntry("reviewed_at", "2099-03-01T09:10:11Z");
        assertThat(contextContract).containsEntry("playbook_count", 4);
        assertThat(contextContract).containsEntry("playbook_expected_count", 6);
        assertThat(contextContract).containsEntry("playbook_covered_count", 6);
        assertThat(contextContract).containsEntry("playbook_coverage_pct", 100L);
        assertThat(contextContract).containsEntry("progressive_disclosure_ready", true);
        assertThat(contextContract).containsEntry("operator_summary", "Minimum profile соблюдён.");
        assertThat(contextContract).containsEntry("next_step_summary", "");
        assertThat(contextContract).containsEntry("secondary_noise_followup_required", false);
        assertThat(contextContract).containsEntry("secondary_noise_management_review_required", false);
        assertThat(contextContract).containsEntry("secondary_noise_usage_level", "rare");
        assertThat(contextContract).containsEntry("secondary_noise_top_section", "");
        assertThat(contextContract).containsEntry("secondary_noise_compaction_summary", "Secondary context pressure остаётся под контролем.");
        assertThat(contextContract).containsEntry("extra_attributes_compaction_candidate", false);
        assertThat(contextContract).containsEntry("extra_attributes_open_rate_pct", 0L);
        assertThat(contextContract).containsEntry("extra_attributes_share_pct_of_secondary", 0L);
        assertThat(contextContract).containsEntry("extra_attributes_usage_level", "rare");
        assertThat(contextContract).containsEntry("extra_attributes_summary", "Extra attributes почти не раскрывались.");
        assertThat((List<String>) contextContract.get("scenarios")).containsExactly("incident", "billing");
        assertThat((List<String>) contextContract.get("mandatory_fields")).containsExactly("full_name", "crm_tier");
        assertThat((List<String>) contextContract.get("operator_focus_blocks")).containsExactly("customer", "sla");
        assertThat((List<String>) contextContract.get("action_items")).isEmpty();
        assertThat(items).anySatisfy(item -> {
            if ("context_minimum_profile".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo("2099-03-01T09:10:11Z");
                assertThat(String.valueOf(item.get("current_value"))).contains("scenarios=2");
                assertThat(String.valueOf(item.get("current_value"))).contains("playbooks=6/6 (100%)");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryRequiresBlockedReasonsReviewForLegacyUsagePolicy() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_governance_legacy_usage_reviewed_by":"ops-lead",
                         "workspace_rollout_governance_legacy_usage_reviewed_at":"2099-03-01T09:10:11Z",
                         "workspace_rollout_governance_legacy_blocked_reasons_review_required":true,
                         "workspace_rollout_governance_legacy_blocked_reasons_top_n":2,
                         "workspace_rollout_governance_legacy_blocked_reasons_reviewed":["policy_hold"],
                         "workspace_rollout_governance_legacy_blocked_reasons_followup":""}
                        """);
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-LEGACY-BLOCK-1", null, null, 810L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusHours(2));
        recordWorkspaceTelemetryEvent("op2", "workspace_open_legacy_blocked", "workspace", "T-LEGACY-BLOCK-2", "policy_hold", null, null,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(90));
        recordWorkspaceTelemetryEvent("op3", "workspace_open_legacy_blocked", "workspace", "T-LEGACY-BLOCK-3", "invalid_review_timestamp", null, null,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null, baseTime.minusMinutes(80));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> legacyUsagePolicy = (Map<String, Object>) packet.get("legacy_usage_policy");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");

        assertThat(packet.get("status")).isEqualTo("hold");
        assertThat((List<String>) packet.get("missing_items")).contains("legacy_usage_policy");
        assertThat(legacyUsagePolicy).containsEntry("blocked_reasons_review_required", true);
        assertThat(legacyUsagePolicy).containsEntry("blocked_reasons_review_ready", false);
        assertThat(legacyUsagePolicy).containsEntry("blocked_reasons_top_n", 2L);
        assertThat((List<String>) legacyUsagePolicy.get("blocked_reasons_missing")).containsExactly("invalid_review_timestamp");
        assertThat(items).anySatisfy(item -> {
            if ("legacy_usage_policy".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(String.valueOf(item.get("current_value"))).contains("blocked_review=1/2");
                assertThat(String.valueOf(item.get("note"))).contains("blocked_missing=invalid_review_timestamp");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryFlagsInvalidContextMinimumProfileReviewTimestamp() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_context_contract_required":true,
                         "workspace_rollout_context_contract_scenarios":["incident"],
                         "workspace_rollout_context_contract_mandatory_fields":["full_name"],
                         "workspace_rollout_context_contract_source_of_truth":["full_name:crm"],
                         "workspace_rollout_context_contract_priority_blocks":["customer"],
                         "workspace_rollout_context_contract_playbooks":{
                           "mandatory_field:full_name":{"label":"Name playbook","url":"https://wiki.example.local/context/name"},
                           "source_of_truth":{"label":"Source guide","url":"https://wiki.example.local/context/source"},
                           "priority_block":{"label":"Priority block guide","url":"https://wiki.example.local/context/priority"}
                         },
                         "workspace_rollout_context_contract_reviewed_by":"ops-context-owner",
                         "workspace_rollout_context_contract_reviewed_at":"bad-context-date"}
                        """);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-PACKET-CONTEXT-INVALID", null, null, 910L,
                "workspace_v1_rollout", "test", "team=ops;shift=day", null, null,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> contextContract = (Map<String, Object>) packet.get("context_contract");
        List<Map<String, Object>> items = (List<Map<String, Object>>) packet.get("items");

        assertThat(packet.get("status")).isEqualTo("hold");
        assertThat((List<String>) packet.get("missing_items")).contains("context_minimum_profile");
        assertThat(contextContract).containsEntry("review_timestamp_invalid", true);
        assertThat(contextContract).containsEntry("operator_summary", "Review checkpoint содержит невалидный UTC timestamp.");
        assertThat(String.valueOf(contextContract.get("next_step_summary"))).contains("Исправьте reviewed_at");
        assertThat(contextContract).containsEntry("secondary_noise_followup_required", false);
        assertThat(contextContract).containsEntry("secondary_noise_management_review_required", false);
        assertThat(contextContract).containsEntry("extra_attributes_compaction_candidate", false);
        assertThat(items).anySatisfy(item -> {
            if ("context_minimum_profile".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("hold");
                assertThat(item.get("current_value")).isEqualTo("invalid_utc");
            }
        });
    }

    @Test
    void workspaceTelemetrySummaryProjectsContextCompactionSignalsIntoPacket() {
        saveDialogConfig("""
                        {"workspace_rollout_governance_packet_required":true,
                         "workspace_rollout_context_contract_required":true,
                         "workspace_rollout_context_contract_scenarios":["incident"],
                         "workspace_rollout_context_contract_mandatory_fields":["full_name"],
                         "workspace_rollout_context_contract_source_of_truth":["full_name:crm"],
                         "workspace_rollout_context_contract_priority_blocks":["customer"],
                         "workspace_rollout_context_contract_reviewed_by":"ops-context-owner",
                         "workspace_rollout_context_contract_reviewed_at":"2099-03-01T09:10:11Z"}
                        """);
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op1", "workspace_open_ms", "performance", "T-CONTEXT-COMPACT-1", 810L, baseTime.minusHours(2));
        recordWorkspaceTelemetryEvent("op2", "workspace_open_ms", "performance", "T-CONTEXT-COMPACT-2", 820L, baseTime.minusMinutes(110));
        recordWorkspaceTelemetryEvent("op3", "workspace_open_ms", "performance", "T-CONTEXT-COMPACT-3", 830L, baseTime.minusMinutes(100));
        recordWorkspaceTelemetryEvent("op4", "workspace_open_ms", "performance", "T-CONTEXT-COMPACT-4", 840L, baseTime.minusMinutes(90));
        recordWorkspaceTelemetryEvent("op5", "workspace_open_ms", "performance", "T-CONTEXT-COMPACT-5", 850L, baseTime.minusMinutes(80));
        recordWorkspaceTelemetryEvent("op6", "workspace_context_extra_attributes_expanded", "workspace", "T-CONTEXT-COMPACT-1", null, baseTime.minusMinutes(70));
        recordWorkspaceTelemetryEvent("op6", "workspace_context_extra_attributes_expanded", "workspace", "T-CONTEXT-COMPACT-2", null, baseTime.minusMinutes(60));
        recordWorkspaceTelemetryEvent("op6", "workspace_context_extra_attributes_expanded", "workspace", "T-CONTEXT-COMPACT-3", null, baseTime.minusMinutes(50));
        recordWorkspaceTelemetryEvent("op6", "workspace_context_extra_attributes_expanded", "workspace", "T-CONTEXT-COMPACT-4", null, baseTime.minusMinutes(40));
        recordWorkspaceTelemetryEvent("op7", "workspace_context_sources_expanded", "workspace", "T-CONTEXT-COMPACT-5", null, baseTime.minusMinutes(30));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> packet = (Map<String, Object>) summary.get("rollout_packet");
        Map<String, Object> contextContract = (Map<String, Object>) packet.get("context_contract");

        assertThat(contextContract).containsEntry("secondary_noise_followup_required", true);
        assertThat(contextContract).containsEntry("secondary_noise_management_review_required", true);
        assertThat(contextContract).containsEntry("secondary_noise_usage_level", "heavy");
        assertThat(contextContract).containsEntry("secondary_noise_top_section", "extra_attributes");
        assertThat(contextContract).containsEntry("extra_attributes_compaction_candidate", true);
        assertThat(contextContract).containsEntry("extra_attributes_open_rate_pct", 80L);
        assertThat(contextContract).containsEntry("extra_attributes_share_pct_of_secondary", 80L);
        assertThat(contextContract).containsEntry("extra_attributes_usage_level", "heavy");
        assertThat(String.valueOf(contextContract.get("secondary_noise_compaction_summary"))).contains("стоит ужать hidden attributes");
    }

    @Test
    void workspaceTelemetrySummaryExpandsExternalCheckpointScorecardItems() {
        String datamartHealthUpdatedAtUtc = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0).toString();
        saveDialogConfig("""
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
                         "workspace_rollout_external_kpi_datamart_health_updated_at":"%s",
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
                        """.formatted(datamartHealthUpdatedAtUtc));

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
                assertThat(item.get("measured_at")).isEqualTo(datamartHealthUpdatedAtUtc);
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
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-cov-", "T-COV-", 300, 980L, 940L, baseTime);
        for (int i = 0; i < 10; i++) {
            recordWorkspaceTelemetryEvent("op-cov-kpi-c-" + i, "workspace_macro_apply", "macro", "T-COV-KPI-C-" + i,
                    null, null, null, "workspace_v1_rollout", "control", "team=ops;shift=day",
                    "frt,ttr,sla_breach", null, baseTime);
            recordWorkspaceTelemetryEvent("op-cov-kpi-t-" + i, "workspace_macro_apply", "macro", "T-COV-KPI-T-" + i,
                    null, null, null, "workspace_v1_rollout", "test", "team=ops;shift=day",
                    "frt,ttr,sla_breach", null, baseTime);
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
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        recordWorkspaceTelemetryEvent("op-sec-1", "workspace_open_ms", "performance", "T-SEC-1",
                null, null, 980L, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, "dialogs_per_shift,csat", baseTime);
        recordWorkspaceTelemetryEvent("op-sec-2", "kpi_dialogs_per_shift_recorded", "kpi", "T-SEC-2",
                null, null, null, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, "dialogs_per_shift", baseTime);
        recordWorkspaceTelemetryEvent("op-sec-3", "kpi_csat_recorded", "kpi", "T-SEC-3",
                null, null, null, "workspace_v1_rollout", "control", "team=ops;shift=night",
                null, "csat", baseTime);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");

        assertThat(totals).containsEntry("kpi_dialogs_per_shift_events", 2L);
        assertThat(totals).containsEntry("kpi_csat_events", 2L);
        assertThat(totals).containsEntry("kpi_dialogs_per_shift_recorded_events", 1L);
        assertThat(totals).containsEntry("kpi_csat_recorded_events", 1L);
    }

    @Test
    void workspaceTelemetrySummaryAggregatesInlineNavigationSignals() {
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC);
        recordWorkspaceTelemetryEvent("op-nav-1", "workspace_open_ms", "performance", "T-NAV-1",
                null, null, 810L, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, null, baseTime.minusHours(1));
        recordWorkspaceTelemetryEvent("op-nav-2", "workspace_inline_navigation", "workspace", "T-NAV-2",
                "next", null, 2L, "workspace_v1_rollout", "test", "team=ops;shift=day",
                null, null, baseTime.minusMinutes(50));
        recordWorkspaceTelemetryEvent("op-nav-3", "workspace_inline_navigation", "workspace", "T-NAV-3",
                "previous", null, 3L, "workspace_v1_rollout", "control", "team=ops;shift=night",
                null, null, baseTime.minusMinutes(40));

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout");
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");

        assertThat(totals).containsEntry("workspace_open_events", 1L);
        assertThat(totals).containsEntry("workspace_inline_navigation_events", 2L);
    }

    @Test
    void workspaceRolloutDecisionHoldsWhenExternalKpiReviewIsStale() {
        saveDialogConfig("{\"workspace_rollout_external_kpi_gate_enabled\":true,\"workspace_rollout_external_kpi_omnichannel_ready\":true,\"workspace_rollout_external_kpi_finance_ready\":true,\"workspace_rollout_external_kpi_reviewed_by\":\"release-oncall\",\"workspace_rollout_external_kpi_reviewed_at\":\"2024-01-01T00:00:00Z\",\"workspace_rollout_external_kpi_review_ttl_hours\":24}");
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-external-open-", "T-EXTERNAL-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-external", "T-EXTERNAL", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_owner_runbook_required":true}
                        """);

        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-owner-open-", "T-OWNER-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-owner", "T-OWNER", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_owner_runbook_required":true,
                         "workspace_rollout_external_kpi_datamart_owner":"bi-platform",
                         "workspace_rollout_external_kpi_datamart_runbook_url":"slack://bi-runbook"}
                        """);

        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-runbook-open-", "T-RUNBOOK-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-runbook", "T-RUNBOOK", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_health_required":true,
                         "workspace_rollout_external_kpi_datamart_health_status":"degraded",
                         "workspace_rollout_external_kpi_datamart_health_note":"incident-42"}
                        """);

        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-health-open-", "T-HEALTH-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-health", "T-HEALTH", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_health_required":true,
                         "workspace_rollout_external_kpi_datamart_health_status":"healthy",
                         "workspace_rollout_external_kpi_datamart_health_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_health_updated_at":"2020-01-01T00:00:00Z",
                         "workspace_rollout_external_kpi_datamart_health_ttl_hours":24}
                        """);

        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-health-fresh-open-", "T-HEALTH-FRESH-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-health-fresh", "T-HEALTH-FRESH", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_program_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_program_status":"in_progress",
                         "workspace_rollout_external_kpi_datamart_program_updated_at":"2020-01-01T00:00:00Z",
                         "workspace_rollout_external_kpi_datamart_program_ttl_hours":24}
                        """);

        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-program-fresh-open-", "T-PROGRAM-FRESH-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-program-fresh", "T-PROGRAM-FRESH", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_url":"mailto:bi-owner@example.com"}
                        """);

        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-dependency-open-", "T-DEPENDENCY-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-dependency", "T-DEPENDENCY", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
                        {"workspace_rollout_external_kpi_gate_enabled":true,"workspace_rollout_external_kpi_omnichannel_ready":true,
                         "workspace_rollout_external_kpi_finance_ready":true,"workspace_rollout_external_kpi_reviewed_by":"release-oncall",
                         "workspace_rollout_external_kpi_reviewed_at":"2099-01-01T00:00:00Z","workspace_rollout_external_kpi_review_ttl_hours":24,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_url":"https://jira.example.com/browse/BI-1234",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required":true,
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at":"2020-01-01T00:00:00Z",
                         "workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours":24}
                        """);

        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-dependency-stale-open-", "T-DEPENDENCY-STALE-OPEN-", 40, 1000L, 900L, baseTime);
        seedRolloutPrimaryKpiEvents("op-dependency-stale", "T-DEPENDENCY-STALE", 12, 1200L, 1100L, 2200L, 2000L, baseTime);

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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
    void settingsBridgeStoresMacroOwnershipNamespaceAndDeprecationFields() {
        Map<String, Object> response = settingsBridgeController.updateSettings(Map.of(
                "dialog_macro_templates", List.of(
                        Map.of(
                                "id", "macro_refund_status",
                                "name", "Статус возврата",
                                "message", "Проверяем возврат по заявке {{ticket_id}}",
                                "owner", "billing-ops",
                                "namespace", "Billing Refund",
                                "approved_for_publish", true,
                                "published", true
                        ),
                        Map.of(
                                "id", "macro_old_refund",
                                "name", "Старый возврат",
                                "message", "Устаревший макрос",
                                "owner", "billing-ops",
                                "namespace", "billing.legacy",
                                "deprecated", true,
                                "deprecation_reason", "merged_into_macro_refund_status"
                        )
                )
        ), null);

        assertThat(response).containsEntry("success", true);
        Map<String, Object> dialogConfig = (Map<String, Object>) sharedConfigService.loadSettings().get("dialog_config");
        List<Map<String, Object>> templates = (List<Map<String, Object>>) dialogConfig.get("macro_templates");

        assertThat(templates).anySatisfy(template -> {
            if ("macro_refund_status".equals(template.get("id"))) {
                assertThat(template).containsEntry("owner", "billing-ops");
                assertThat(template).containsEntry("namespace", "billing-refund");
                assertThat(template).containsEntry("deprecated", false);
            }
        });
        assertThat(templates).anySatisfy(template -> {
            if ("macro_old_refund".equals(template.get("id"))) {
                assertThat(template).containsEntry("deprecated", true);
                assertThat(template).containsEntry("deprecation_reason", "merged_into_macro_refund_status");
                assertThat(String.valueOf(template.get("deprecated_at"))).isNotBlank();
            }
        });
    }

    @Test
    void macroGovernanceAuditHighlightsOwnershipReviewAndUsageGaps() {
        settingsBridgeController.updateSettings(Map.ofEntries(
                Map.entry("dialog_macro_templates", List.of(
                        Map.of(
                                "id", "macro_active_missing_owner",
                                "name", "Активный без owner",
                                "message", "Текст 1",
                                "approved_for_publish", true,
                                "published", true
                        ),
                        Map.of(
                                "id", "macro_review_stale",
                                "name", "Ревью просрочено",
                                "message", "Текст 2",
                                "owner", "ops-core",
                                "namespace", "ops.core",
                                "tags", List.of("refund", "refund", "returns"),
                                "approved_for_publish", true,
                                "published", true
                        ),
                        Map.of(
                                "id", "macro_unknown_variable",
                                "name", "Неизвестная переменная",
                                "message", "Здравствуйте, {{unknown_var}}",
                                "owner", "ops-core",
                                "namespace", "ops.core",
                                "approved_for_publish", true,
                                "published", true
                        ),
                        Map.of(
                                "id", "macro_deprecated",
                                "name", "Deprecated макрос",
                                "message", "Текст 3",
                                "owner", "ops-core",
                                "namespace", "ops.legacy",
                                "deprecated", true,
                                "deprecated_at", "2026-01-01T00:00:00Z"
                        )
                )),
                Map.entry("dialog_macro_governance_require_owner", true),
                Map.entry("dialog_macro_governance_require_namespace", true),
                Map.entry("dialog_macro_governance_require_review", true),
                Map.entry("dialog_macro_governance_review_ttl_hours", 24),
                Map.entry("dialog_macro_governance_deprecation_requires_reason", true),
                Map.entry("dialog_macro_governance_unused_days", 30),
                Map.entry("dialog_macro_governance_red_list_enabled", true),
                Map.entry("dialog_macro_governance_red_list_usage_max", 0),
                Map.entry("dialog_macro_governance_owner_action_required", true),
                Map.entry("dialog_macro_governance_cleanup_cadence_days", 7),
                Map.entry("dialog_macro_governance_alias_cleanup_required", true),
                Map.entry("dialog_macro_governance_variable_cleanup_required", true),
                Map.entry("dialog_macro_governance_usage_tier_sla_required", true),
                Map.entry("dialog_macro_governance_usage_tier_low_max", 0),
                Map.entry("dialog_macro_governance_usage_tier_medium_max", 5),
                Map.entry("dialog_macro_governance_cleanup_sla_low_days", 7),
                Map.entry("dialog_macro_governance_cleanup_sla_medium_days", 30),
                Map.entry("dialog_macro_governance_cleanup_sla_high_days", 90),
                Map.entry("dialog_macro_governance_deprecation_sla_low_days", 14),
                Map.entry("dialog_macro_governance_deprecation_sla_medium_days", 45),
                Map.entry("dialog_macro_governance_deprecation_sla_high_days", 120)
        ), null);

        recordMacroTelemetryEvent("op1", "macro_apply", "T-MACRO-1", "macro_review_stale", "Ревью просрочено",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(40));

        Map<String, Object> audit = dialogService.buildMacroGovernanceAudit(sharedConfigService.loadSettings());
        List<Map<String, Object>> issues = (List<Map<String, Object>>) audit.get("issues");
        List<Map<String, Object>> templates = (List<Map<String, Object>>) audit.get("templates");

        assertThat(audit).containsEntry("status", "hold");
        assertThat(audit).containsEntry("missing_owner_total", 1);
        assertThat(audit).containsEntry("stale_review_total", 3);
        assertThat(audit).containsEntry("unused_published_total", 3);
        assertThat(audit).containsEntry("deprecation_gap_total", 1);
        assertThat(audit).containsEntry("red_list_total", 3);
        assertThat(audit).containsEntry("owner_action_total", 3);
        assertThat(audit).containsEntry("alias_cleanup_total", 1);
        assertThat(audit).containsEntry("variable_cleanup_total", 1);
        assertThat(audit).containsEntry("cleanup_sla_overdue_total", 1);
        assertThat(audit).containsEntry("deprecation_sla_overdue_total", 1);
        assertThat(((Number) audit.get("mandatory_issue_total")).longValue()).isGreaterThan(0L);
        assertThat(((Number) audit.get("advisory_issue_total")).longValue()).isGreaterThan(0L);
        assertThat((List<String>) audit.get("minimum_required_checkpoints"))
                .containsExactly("governance_review", "external_catalog");
        assertThat(audit).containsEntry("required_checkpoint_total", 2L);
        assertThat(audit).containsEntry("required_checkpoint_ready_total", 0L);
        assertThat(audit).containsEntry("required_checkpoint_closure_rate_pct", 0L);
        assertThat(audit).containsEntry("freshness_checkpoint_total", 3L);
        assertThat(audit).containsEntry("freshness_checkpoint_ready_total", 0L);
        assertThat(audit).containsEntry("freshness_closure_rate_pct", 0L);
        assertThat(((Number) audit.get("noise_ratio_pct")).longValue()).isGreaterThan(50L);
        assertThat(audit).containsEntry("noise_level", "high");
        assertThat(audit).containsEntry("weekly_review_priority", "close_required_path");
        assertThat(String.valueOf(audit.get("weekly_review_summary"))).contains("обязательные macro checkpoints");
        assertThat(audit).containsKeys(
                "low_signal_advisory_total",
                "actionable_advisory_total",
                "actionable_advisory_share_pct",
                "low_signal_advisory_share_pct",
                "advisory_noise_excluding_low_signal_pct",
                "advisory_followup_required",
                "low_signal_red_list_templates",
                "low_signal_backlog_dominant",
                "low_signal_backlog_summary",
                "minimum_required_path_controlled");
        assertThat(((Number) audit.get("actionable_advisory_total")).longValue()).isGreaterThanOrEqualTo(0L);
        assertThat(((Number) audit.get("low_signal_advisory_total")).longValue()).isGreaterThanOrEqualTo(0L);
        assertThat(((Number) audit.get("actionable_advisory_share_pct")).longValue()).isBetween(0L, 100L);
        assertThat(((Number) audit.get("low_signal_advisory_share_pct")).longValue()).isBetween(0L, 100L);
        assertThat(audit).containsEntry("minimum_required_path_controlled", false);
        assertThat(String.valueOf(audit.get("low_signal_backlog_summary"))).isNotBlank();
        assertThat((List<String>) audit.get("advisory_signals"))
                .contains("red_list", "owner_action");
        assertThat(issues).anySatisfy(issue -> {
            if ("owner_missing".equals(issue.get("type"))) {
                assertThat(issue.get("status")).isEqualTo("hold");
            }
        });
        assertThat(issues).anySatisfy(issue -> {
            if ("deprecation_reason_missing".equals(issue.get("type"))) {
                assertThat(issue.get("status")).isEqualTo("attention");
            }
        });
        assertThat(issues).anySatisfy(issue -> {
            if ("alias_cleanup_required".equals(issue.get("type"))) {
                assertThat(issue.get("detail")).isEqualTo("duplicate_aliases=1");
            }
        });
        assertThat(issues).anySatisfy(issue -> {
            if ("unknown_variables_detected".equals(issue.get("type"))) {
                assertThat(issue.get("detail")).isEqualTo("unknown_variables=unknown_var");
            }
        });
        assertThat(templates).anySatisfy(template -> {
            if ("macro_active_missing_owner".equals(template.get("template_id"))) {
                assertThat(template.get("status")).isEqualTo("hold");
                assertThat(template.get("usage_count")).isEqualTo(0L);
                assertThat(template.get("red_list_candidate")).isEqualTo(true);
                assertThat(template).containsKey("red_list_low_signal");
                assertThat(template.get("owner_action_required")).isEqualTo(true);
            }
        });
        assertThat(templates).anySatisfy(template -> {
            if ("macro_review_stale".equals(template.get("template_id"))) {
                assertThat(template.get("duplicate_alias_count")).isEqualTo(1);
                assertThat(template.get("usage_tier")).isEqualTo("medium");
                assertThat(template.get("cleanup_sla_status")).isEqualTo("hold");
            }
        });
        assertThat(templates).anySatisfy(template -> {
            if ("macro_unknown_variable".equals(template.get("template_id"))) {
                assertThat(template.get("unknown_variable_count")).isEqualTo(1);
                assertThat(template.get("owner_action_required")).isEqualTo(true);
                assertThat(template.get("usage_tier")).isEqualTo("low");
            }
        });
        assertThat(templates).anySatisfy(template -> {
            if ("macro_deprecated".equals(template.get("template_id"))) {
                assertThat(template.get("status")).isEqualTo("off");
                assertThat(template.get("deprecated")).isEqualTo(true);
                assertThat(template.get("deprecation_sla_status")).isEqualTo("hold");
            }
        });
    }

    @Test
    void settingsBridgeUpdatesItEquipmentSerialNumberAndAccessoriesUsingCompatibilityAlias() {
        jdbcTemplate.update("""
                INSERT INTO it_equipment_catalog (
                    equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))
                """,
                "Router", "MikroTik", "RB4011", "[]", "OLD-SN", "old kit");

        Long itemId = jdbcTemplate.queryForObject("SELECT id FROM it_equipment_catalog LIMIT 1", Long.class);

        Map<String, Object> response = settingsItEquipmentController.updateItEquipment(itemId, Map.of(
                "serial_number", "SN-2026-0001",
                "additional_equipment", "rack ears, power adapter"
        ), SecurityContextHolder.getContext().getAuthentication());

        assertThat(response).containsEntry("success", true);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        assertThat(items).anySatisfy(item -> {
            if (itemId.equals(((Number) item.get("id")).longValue())) {
                assertThat(item).containsEntry("serial_number", "SN-2026-0001");
                assertThat(item).containsEntry("accessories", "rack ears, power adapter");
            }
        });

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "SELECT serial_number, accessories FROM it_equipment_catalog WHERE id = ?",
                itemId
        );
        assertThat(persisted).containsEntry("serial_number", "SN-2026-0001");
        assertThat(persisted).containsEntry("accessories", "rack ears, power adapter");
    }

    @Test
    void settingsBridgePersistsMacroGovernanceQualityLoopFields() {
        settingsBridgeController.updateSettings(Map.ofEntries(
                Map.entry("dialog_macro_governance_red_list_enabled", true),
                Map.entry("dialog_macro_governance_red_list_usage_max", 2),
                Map.entry("dialog_macro_governance_owner_action_required", true),
                Map.entry("dialog_macro_governance_cleanup_cadence_days", 14),
                Map.entry("dialog_macro_governance_alias_cleanup_required", true),
                Map.entry("dialog_macro_governance_variable_cleanup_required", true),
                Map.entry("dialog_macro_governance_usage_tier_sla_required", true),
                Map.entry("dialog_macro_governance_usage_tier_low_max", 1),
                Map.entry("dialog_macro_governance_usage_tier_medium_max", 6),
                Map.entry("dialog_macro_governance_cleanup_sla_low_days", 10),
                Map.entry("dialog_macro_governance_cleanup_sla_medium_days", 20),
                Map.entry("dialog_macro_governance_cleanup_sla_high_days", 40),
                Map.entry("dialog_macro_governance_deprecation_sla_low_days", 15),
                Map.entry("dialog_macro_governance_deprecation_sla_medium_days", 30),
                Map.entry("dialog_macro_governance_deprecation_sla_high_days", 60)
        ), null);

        Map<String, Object> dialogConfig = (Map<String, Object>) sharedConfigService.loadSettings().get("dialog_config");
        assertThat(dialogConfig.get("macro_governance_red_list_enabled")).isEqualTo(true);
        assertThat(dialogConfig.get("macro_governance_red_list_usage_max")).isEqualTo(2L);
        assertThat(dialogConfig.get("macro_governance_owner_action_required")).isEqualTo(true);
        assertThat(dialogConfig.get("macro_governance_cleanup_cadence_days")).isEqualTo(14L);
        assertThat(dialogConfig.get("macro_governance_alias_cleanup_required")).isEqualTo(true);
        assertThat(dialogConfig.get("macro_governance_variable_cleanup_required")).isEqualTo(true);
        assertThat(dialogConfig.get("macro_governance_usage_tier_sla_required")).isEqualTo(true);
        assertThat(dialogConfig.get("macro_governance_usage_tier_low_max")).isEqualTo(1L);
        assertThat(dialogConfig.get("macro_governance_usage_tier_medium_max")).isEqualTo(6L);
        assertThat(dialogConfig.get("macro_governance_cleanup_sla_low_days")).isEqualTo(10L);
        assertThat(dialogConfig.get("macro_governance_cleanup_sla_medium_days")).isEqualTo(20L);
        assertThat(dialogConfig.get("macro_governance_cleanup_sla_high_days")).isEqualTo(40L);
        assertThat(dialogConfig.get("macro_governance_deprecation_sla_low_days")).isEqualTo(15L);
        assertThat(dialogConfig.get("macro_governance_deprecation_sla_medium_days")).isEqualTo(30L);
        assertThat(dialogConfig.get("macro_governance_deprecation_sla_high_days")).isEqualTo(60L);
    }

    @Test
    void settingsBridgePersistsSlaPolicyGovernanceBaselineFields() {
        settingsBridgeController.updateSettings(Map.ofEntries(
                Map.entry("dialog_sla_critical_auto_assign_audit_require_layers", true),
                Map.entry("dialog_sla_critical_auto_assign_audit_require_owner", true),
                Map.entry("dialog_sla_critical_auto_assign_audit_require_review", true),
                Map.entry("dialog_sla_critical_auto_assign_audit_review_ttl_hours", 72),
                Map.entry("dialog_sla_critical_auto_assign_audit_broad_rule_coverage_pct", 55),
                Map.entry("dialog_sla_critical_auto_assign_audit_block_on_conflicts", true),
                Map.entry("dialog_sla_critical_auto_assign_governance_review_required", true),
                Map.entry("dialog_sla_critical_auto_assign_governance_review_path", "strict"),
                Map.entry("dialog_sla_critical_auto_assign_governance_review_ttl_hours", 48),
                Map.entry("dialog_sla_critical_auto_assign_governance_dry_run_ticket_required", true),
                Map.entry("dialog_sla_critical_auto_assign_governance_decision_required", true)
        ), null);

        Map<String, Object> dialogConfig = (Map<String, Object>) sharedConfigService.loadSettings().get("dialog_config");
        assertThat(dialogConfig.get("sla_critical_auto_assign_audit_require_layers")).isEqualTo(true);
        assertThat(dialogConfig.get("sla_critical_auto_assign_audit_require_owner")).isEqualTo(true);
        assertThat(dialogConfig.get("sla_critical_auto_assign_audit_require_review")).isEqualTo(true);
        assertThat(((Number) dialogConfig.get("sla_critical_auto_assign_audit_review_ttl_hours")).longValue()).isEqualTo(72L);
        assertThat(((Number) dialogConfig.get("sla_critical_auto_assign_audit_broad_rule_coverage_pct")).longValue()).isEqualTo(55L);
        assertThat(dialogConfig.get("sla_critical_auto_assign_audit_block_on_conflicts")).isEqualTo(true);
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_review_required")).isEqualTo(true);
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_review_path")).isEqualTo("strict");
        assertThat(((Number) dialogConfig.get("sla_critical_auto_assign_governance_review_ttl_hours")).longValue()).isEqualTo(48L);
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_dry_run_ticket_required")).isEqualTo(true);
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_decision_required")).isEqualTo(true);
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_policy_changed_at")).isNotNull();
    }

    @Test
    void workspaceRolloutDecisionPublishesCanonicalTimestampInvalidAliases() {
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        saveDialogConfig("""
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
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedRolloutOpenEvents("op-outcome-open-", "T-OUTCOME-OPEN-", 40, 980L, 970L, baseTime);

        for (int i = 0; i < 12; i++) {
            String cohort = i < 6 ? "control" : "test";
            recordWorkspaceTelemetryEvent("op-outcome-frt-" + i, "kpi_frt_recorded", "kpi", "T-OUTCOME-FRT-" + i,
                    null, null, "test".equals(cohort) ? 1700L : 1200L,
                    "workspace_v1_rollout", cohort, "team=ops;shift=day",
                    "frt,ttr,sla_breach", null, baseTime);
            recordWorkspaceTelemetryEvent("op-outcome-ttr-" + i, "kpi_ttr_recorded", "kpi", "T-OUTCOME-TTR-" + i,
                    null, null, "test".equals(cohort) ? 3600L : 2400L,
                    "workspace_v1_rollout", cohort, "team=ops;shift=day",
                    "frt,ttr,sla_breach", null, baseTime);
            recordWorkspaceTelemetryEvent("op-outcome-sla-" + i, "kpi_sla_breach_recorded", "kpi", "T-OUTCOME-SLA-" + i,
                    null, null, null, "workspace_v1_rollout", cohort, "team=ops;shift=day",
                    "frt,ttr,sla_breach", null, baseTime);
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

