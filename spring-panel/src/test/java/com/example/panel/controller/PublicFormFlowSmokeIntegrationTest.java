package com.example.panel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.SharedConfigService;

import java.io.IOException;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
class PublicFormFlowSmokeIntegrationTest {

    private static Path dbFile;
    private static Path sharedConfigDir;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-public-form-smoke", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-public-form-shared-config");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
        registry.add("shared-config.dir", () -> sharedConfigDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SharedConfigService sharedConfigService;

    @Autowired
    private DialogReplyService dialogReplyService;

    @Autowired
    private DialogNotificationService dialogNotificationService;

    @Autowired
    private DialogQuickActionService dialogQuickActionService;

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
        sharedConfigService.saveSettings(new LinkedHashMap<>());
    }

    @Test
    void publicFormSubmitCreatesDialogVisibleInDialogsApi() throws Exception {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (41, 'web-smoke', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-smoke', ?)",
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":410,\"fields\":[{\"id\":\"topic\",\"text\":\"Тема\",\"type\":\"text\",\"required\":true,\"order\":10}]}");

        mockMvc.perform(get("/api/public/forms/web-smoke/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.questions[0].id").value("topic"));

        MvcResult createSessionResult = mockMvc.perform(post("/api/public/forms/web-smoke/sessions")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь по оплате",
                                  "clientName": "Ирина",
                                  "clientContact": "+79990000000",
                                  "username": "irina",
                                  "answers": {
                                    "topic": "Оплата"
                                  },
                                  "requestId": "smoke-req-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ticketId").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode createSessionPayload = objectMapper.readTree(createSessionResult.getResponse().getContentAsString());
        String ticketId = createSessionPayload.path("ticketId").asText();
        String token = createSessionPayload.path("token").asText();
        assertThat(ticketId).startsWith("web-");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/public/forms/web-smoke/sessions/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.session.ticketId").value(ticketId));

        mockMvc.perform(get("/api/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value(ticketId));
    }

    @Test
    void publicFormConfigReflectsLiveSessionPollingRuntimeSettings() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.of(
                        "public_form_session_polling_enabled", false,
                        "public_form_session_polling_interval_seconds", 45
                )
        ));
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg)
                VALUES (40, 'web-polling', 'Polling Form', 1, CURRENT_TIMESTAMP, 'web-polling', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        mockMvc.perform(get("/api/public/forms/web-polling/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionPollingEnabled").value(false))
                .andExpect(jsonPath("$.sessionPollingIntervalSeconds").value(45));
    }

    @Test
    void publicFormMissingChannelReturnsStructuredConfigAndSessionErrors() throws Exception {
        mockMvc.perform(get("/api/public/forms/web-missing/config"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CHANNEL_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-missing/config"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mockMvc.perform(get("/api/public/forms/web-missing/sessions/token-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-missing/sessions/token-missing"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void publicFormDisabledChannelReturnsConfiguredStatusForConfigAndSubmit() throws Exception {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (42, 'web-disabled', 'Отключенная форма', 1, CURRENT_TIMESTAMP, 'web-disabled', ?)",
                "{\"schemaVersion\":1,\"enabled\":false,\"captchaEnabled\":false,\"disabledStatus\":410,\"fields\":[]}");

        mockMvc.perform(get("/api/public/forms/web-disabled/config"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORM_DISABLED"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-disabled/config"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mockMvc.perform(post("/api/public/forms/web-disabled/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORM_DISABLED"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-disabled/sessions"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void publicFormMalformedBodyAndMissingSessionReturnStructuredTransportErrors() throws Exception {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (43, 'web-runtime', 'Runtime Form', 1, CURRENT_TIMESTAMP, 'web-runtime', ?)",
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        mockMvc.perform(post("/api/public/forms/web-runtime/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_BODY"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-runtime/sessions"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mockMvc.perform(get("/api/public/forms/web-runtime/sessions/token-none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-runtime/sessions/token-none"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void publicFormContinuationContractSupportsTelegramAndMaxPlatforms() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, platform, bot_username, bot_name, questions_cfg)
                VALUES (44, 'web-telegram', 'Telegram Form', 1, CURRENT_TIMESTAMP, 'web-telegram', 'telegram', '@support_test_bot', 'Support Bot', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, platform, bot_username, bot_name, questions_cfg)
                VALUES (45, 'web-max', 'MAX Form', 1, CURRENT_TIMESTAMP, 'web-max', 'max', 'max_support_bot', 'MAX Bot', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        mockMvc.perform(get("/api/public/forms/web-telegram/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.continuation.enabled").value(true))
                .andExpect(jsonPath("$.continuation.platform").value("telegram"))
                .andExpect(jsonPath("$.continuation.platformLabel").value("Telegram"))
                .andExpect(jsonPath("$.continuation.command").value("/continue <token>"))
                .andExpect(jsonPath("$.continuation.openUrl").value(""));

        mockMvc.perform(get("/api/public/forms/web-max/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.continuation.enabled").value(true))
                .andExpect(jsonPath("$.continuation.platform").value("max"))
                .andExpect(jsonPath("$.continuation.platformLabel").value("MAX"))
                .andExpect(jsonPath("$.continuation.command").value("/continue <token>"))
                .andExpect(jsonPath("$.continuation.openUrl").value(""));

        MvcResult telegramCreate = mockMvc.perform(post("/api/public/forms/web-telegram/sessions")
                        .header("X-Forwarded-For", "203.0.113.44")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь в Telegram"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.continuation.enabled").value(true))
                .andExpect(jsonPath("$.continuation.platform").value("telegram"))
                .andExpect(jsonPath("$.continuation.platformLabel").value("Telegram"))
                .andExpect(jsonPath("$.continuation.command").isNotEmpty())
                .andExpect(jsonPath("$.continuation.openUrl").value(org.hamcrest.Matchers.startsWith("https://t.me/support_test_bot?start=web_")))
                .andReturn();

        JsonNode telegramPayload = objectMapper.readTree(telegramCreate.getResponse().getContentAsString());
        String telegramToken = telegramPayload.path("token").asText();

        mockMvc.perform(get("/api/public/forms/web-telegram/sessions/{token}", telegramToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.continuation.enabled").value(true))
                .andExpect(jsonPath("$.continuation.platform").value("telegram"))
                .andExpect(jsonPath("$.continuation.command").value("/continue " + telegramToken))
                .andExpect(jsonPath("$.continuation.openUrl").value(org.hamcrest.Matchers.startsWith("https://t.me/support_test_bot?start=web_")));

        mockMvc.perform(post("/api/public/forms/web-max/sessions")
                        .header("X-Forwarded-For", "203.0.113.45")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь в MAX"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.continuation.enabled").value(true))
                .andExpect(jsonPath("$.continuation.platform").value("max"))
                .andExpect(jsonPath("$.continuation.platformLabel").value("MAX"))
                .andExpect(jsonPath("$.continuation.command").isNotEmpty())
                .andExpect(jsonPath("$.continuation.openUrl").value(""));
    }

    @Test
    void publicFormSessionTokenRotationInvalidatesOriginalTokenAndPromotesNewOne() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.of("public_form_session_token_rotate_on_read", true)
        ));
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, platform, bot_username, questions_cfg)
                VALUES (46, 'web-rotate', 'Rotating Form', 1, CURRENT_TIMESTAMP, 'web-rotate', 'telegram', '@rotate_test_bot', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        MvcResult createResult = mockMvc.perform(post("/api/public/forms/web-rotate/sessions")
                        .header("X-Forwarded-For", "203.0.113.46")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь с продолжением"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode createPayload = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String originalToken = createPayload.path("token").asText();

        MvcResult firstRead = mockMvc.perform(get("/api/public/forms/web-rotate/sessions/{token}", originalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode firstReadPayload = objectMapper.readTree(firstRead.getResponse().getContentAsString());
        String rotatedToken = firstReadPayload.path("session").path("token").asText();
        assertThat(rotatedToken).isNotBlank();
        assertThat(rotatedToken).isNotEqualTo(originalToken);

        mockMvc.perform(get("/api/public/forms/web-rotate/sessions/{token}", originalToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));

        mockMvc.perform(get("/api/public/forms/web-rotate/sessions/{token}", rotatedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.session.token").isNotEmpty())
                .andExpect(jsonPath("$.session.token").value(org.hamcrest.Matchers.not(rotatedToken)));
    }

    @Test
    void publicFormSessionHistoryReflectsOperatorReplySystemNotificationsAndReplyPreview() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at, public_id, questions_cfg)
                VALUES (50, 'web-lifecycle', 'Lifecycle Form', 'vk', 1, CURRENT_TIMESTAMP, 'web-lifecycle', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        MvcResult createResult = mockMvc.perform(post("/api/public/forms/web-lifecycle/sessions")
                        .header("X-Forwarded-For", "203.0.113.50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Need help with order"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode createPayload = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String ticketId = createPayload.path("ticketId").asText();
        String token = createPayload.path("token").asText();
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM messages WHERE ticket_id = ? ORDER BY created_at DESC LIMIT 1",
                Long.class,
                ticketId
        );
        assertThat(userId).isNotNull();

        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, channel_id, tg_message_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                "support",
                "Please share order number",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).toString(),
                ticketId,
                "text",
                50L,
                7001L
        );

        DialogReplyService.DialogReplyResult reply = dialogReplyService.sendReply(
                ticketId,
                "Order number 12345, checking details",
                7001L,
                "operator"
        );
        assertThat(reply.success()).isTrue();

        dialogNotificationService.notifyResolved(ticketId);

        MvcResult sessionResult = mockMvc.perform(get("/api/public/forms/web-lifecycle/sessions/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode sessionPayload = objectMapper.readTree(sessionResult.getResponse().getContentAsByteArray());
        List<JsonNode> messages = sessionPayload.path("messages").findValues("message");
        assertThat(messages.stream().map(JsonNode::asText).toList())
                .contains("Need help with order")
                .contains("Please share order number")
                .contains("Order number 12345, checking details");

        long systemMessageCount = 0;
        JsonNode operatorReply = null;
        for (JsonNode node : sessionPayload.path("messages")) {
            if ("system".equals(node.path("sender").asText())) {
                systemMessageCount++;
            }
            if ("Order number 12345, checking details".equals(node.path("message").asText())) {
                operatorReply = node;
            }
        }

        assertThat(operatorReply).isNotNull();
        assertThat(operatorReply.path("sender").asText()).isEqualTo("operator");
        assertThat(operatorReply.path("replyPreview").asText()).isEqualTo("Please share order number");
        assertThat(systemMessageCount).isEqualTo(2);
    }

    @Test
    void publicFormPreviousHistoryBridgesEarlierSessionForSameRequester() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at, public_id, questions_cfg)
                VALUES (51, 'web-previous', 'Previous Form', 'vk', 1, CURRENT_TIMESTAMP, 'web-previous', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        MvcResult firstCreate = mockMvc.perform(post("/api/public/forms/web-previous/sessions")
                        .header("X-Forwarded-For", "203.0.113.51")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "First request"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String firstTicketId = objectMapper.readTree(firstCreate.getResponse().getContentAsString()).path("ticketId").asText();
        assertThat(dialogQuickActionService.resolveTicket(firstTicketId, "operator", List.of("billing")).updated()).isTrue();

        MvcResult secondCreate = mockMvc.perform(post("/api/public/forms/web-previous/sessions")
                        .header("X-Forwarded-For", "203.0.113.51")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Second request"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String secondTicketId = objectMapper.readTree(secondCreate.getResponse().getContentAsString()).path("ticketId").asText();

        mockMvc.perform(get("/api/dialogs/{ticketId}/history/previous", secondTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.batch.ticketId").value(firstTicketId))
                .andExpect(jsonPath("$.batch.status").value("resolved"))
                .andExpect(jsonPath("$.batch.sourceKey").value("web_form"))
                .andExpect(jsonPath("$.batch.sourceLabel").value("Внешняя форма"))
                .andExpect(jsonPath("$.batch.messages[0].message").value("First request"))
                .andExpect(jsonPath("$.batch.messages[1].sender").value("system"))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void publicFormSessionHistoryReflectsResolveAndReopenLifecycleNotifications() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, platform, is_active, created_at, public_id, questions_cfg)
                VALUES (52, 'web-reopen', 'Reopen Form', 'vk', 1, CURRENT_TIMESTAMP, 'web-reopen', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        MvcResult createResult = mockMvc.perform(post("/api/public/forms/web-reopen/sessions")
                        .header("X-Forwarded-For", "203.0.113.52")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Need reopen lifecycle"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode createPayload = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String ticketId = createPayload.path("ticketId").asText();
        String token = createPayload.path("token").asText();

        assertThat(dialogQuickActionService.resolveTicket(ticketId, "operator", List.of("support")).updated()).isTrue();
        assertThat(dialogQuickActionService.reopenTicket(ticketId, "operator").updated()).isTrue();

        mockMvc.perform(get("/api/public/forms/web-reopen/sessions/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messages[*].sender").isArray())
                .andExpect(jsonPath("$.messages[1].sender").value("system"))
                .andExpect(jsonPath("$.messages[2].sender").value("system"))
                .andExpect(jsonPath("$.messages[3].sender").value("system"))
                .andExpect(jsonPath("$.messages[0].message").value("Need reopen lifecycle"))
                .andExpect(jsonPath("$.messages[3].message").value("Ваше обращение снова открыто. Мы продолжаем работу."));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tickets WHERE ticket_id = ?",
                String.class,
                ticketId
        )).isEqualTo("pending");
    }

    @Test
    void publicFormHttpIdempotencyReturnsSameSessionAndRejectsPayloadConflict() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg)
                VALUES (47, 'web-idempotent-http', 'Idempotent Form', 1, CURRENT_TIMESTAMP, 'web-idempotent-http', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        String firstPayload = """
                {
                  "message": "Нужна помощь по заказу",
                  "clientName": "Анна",
                  "clientContact": "+79990000001",
                  "username": "anna",
                  "answers": {
                    "topic": "billing"
                  },
                  "requestId": "req-http-1"
                }
                """;

        MvcResult firstCreate = mockMvc.perform(post("/api/public/forms/web-idempotent-http/sessions")
                        .header("X-Forwarded-For", "203.0.113.47")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        MvcResult duplicateCreate = mockMvc.perform(post("/api/public/forms/web-idempotent-http/sessions")
                        .header("X-Forwarded-For", "203.0.113.47")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode firstCreatePayload = objectMapper.readTree(firstCreate.getResponse().getContentAsString());
        JsonNode duplicatePayload = objectMapper.readTree(duplicateCreate.getResponse().getContentAsString());
        assertThat(duplicatePayload.path("ticketId").asText()).isEqualTo(firstCreatePayload.path("ticketId").asText());
        assertThat(duplicatePayload.path("token").asText()).isEqualTo(firstCreatePayload.path("token").asText());

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_history WHERE ticket_id = ?",
                Integer.class,
                firstCreatePayload.path("ticketId").asText()
        );
        assertThat(historyCount).isEqualTo(1);

        mockMvc.perform(post("/api/public/forms/web-idempotent-http/sessions")
                        .header("X-Forwarded-For", "203.0.113.47")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Другой текст",
                                  "clientName": "Анна",
                                  "clientContact": "+79990000001",
                                  "username": "anna",
                                  "answers": {
                                    "topic": "billing"
                                  },
                                  "requestId": "req-http-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-idempotent-http/sessions"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void publicFormHttpRateLimitRejectsBurstRequestsWithStructuredError() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.of(
                        "public_form_rate_limit_max_requests", 2,
                        "public_form_rate_limit_window_seconds", 3600
                )
        ));
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg)
                VALUES (48, 'web-rate-http', 'Rate Limit Form', 1, CURRENT_TIMESTAMP, 'web-rate-http', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        String requestPayload = """
                {
                  "message": "Нужна помощь"
                }
                """;

        mockMvc.perform(post("/api/public/forms/web-rate-http/sessions")
                        .header("X-Forwarded-For", "203.0.113.48")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/public/forms/web-rate-http/sessions")
                        .header("X-Forwarded-For", "203.0.113.48")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/public/forms/web-rate-http/sessions")
                        .header("X-Forwarded-For", "203.0.113.48")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-rate-http/sessions"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM web_form_sessions WHERE channel_id = ?",
                Integer.class,
                48L
        );
        assertThat(sessionCount).isEqualTo(2);
    }

    @Test
    void publicFormExpiredSessionReturnsStructuredNotFoundAfterTtlCutoff() throws Exception {
        sharedConfigService.saveSettings(Map.of(
                "dialog_config", Map.of("public_form_session_ttl_hours", 1)
        ));
        jdbcTemplate.update("""
                INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg)
                VALUES (49, 'web-ttl', 'TTL Form', 1, CURRENT_TIMESTAMP, 'web-ttl', ?)
                """,
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":404,\"fields\":[]}");

        MvcResult createResult = mockMvc.perform(post("/api/public/forms/web-ttl/sessions")
                        .header("X-Forwarded-For", "203.0.113.49")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь с TTL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String token = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("token").asText();
        jdbcTemplate.update(
                "UPDATE web_form_sessions SET created_at = ? WHERE token = ?",
                Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3).toInstant()),
                token
        );

        mockMvc.perform(get("/api/public/forms/web-ttl/sessions/{token}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/public/forms/web-ttl/sessions/" + token))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
