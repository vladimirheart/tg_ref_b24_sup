package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.service.DialogConversationReadService;
import com.example.panel.service.PublicFormService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicFormApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicFormApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicFormService publicFormService;

    @MockBean
    private DialogConversationReadService dialogConversationReadService;

    @Test
    void configReturnsGoneWhenChannelFormDisabledWith410Policy() throws Exception {
        PublicFormConfig disabledConfig = new PublicFormConfig(
                7L,
                "web-gone",
                "Веб-форма",
                1,
                false,
                false,
                410,
                null,
                null,
                List.of()
        );
        when(publicFormService.loadConfigRaw("web-gone")).thenReturn(Optional.of(disabledConfig));
        when(publicFormService.resolveUiLocale()).thenReturn("ru");

        mockMvc.perform(get("/api/public/forms/web-gone/config"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Форма канала отключена"))
                .andExpect(jsonPath("$.errorCode").value("FORM_DISABLED"));
    }

    @Test
    void configReturnsNotFoundWhenChannelDoesNotExist() throws Exception {
        when(publicFormService.loadConfigRaw("missing-channel")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/forms/missing-channel/config"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CHANNEL_NOT_FOUND"));
    }

    @Test
    void configFallsBackToNotFoundWhenDisabledStatusIsInvalid() throws Exception {
        PublicFormConfig disabledConfig = new PublicFormConfig(
                17L,
                "web-invalid-status",
                "Web Form",
                1,
                false,
                false,
                999,
                null,
                null,
                List.of()
        );
        when(publicFormService.loadConfigRaw("web-invalid-status")).thenReturn(Optional.of(disabledConfig));

        mockMvc.perform(get("/api/public/forms/web-invalid-status/config"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORM_DISABLED"));
    }

    @Test
    void createSessionUsesForwardedHeadersAndPassesRequestId() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                8L,
                "web-enabled",
                "Веб-форма",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of(new PublicFormQuestion("topic", "Тема", "text", 10, Map.of("required", true)))
        );
        PublicFormSessionDto createdSession = new PublicFormSessionDto(
                "token-1",
                "T-101",
                8L,
                "web-enabled",
                "Анна",
                "+79991234567",
                "anna",
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );

        when(publicFormService.loadConfigRaw("web-enabled")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.resolveUiLocale()).thenReturn("en");
        when(publicFormService.buildRequesterKey("203.0.113.1", "fp-001")).thenReturn("ip+fp-key");
        when(publicFormService.buildContinuationOptions("web-enabled", "token-1")).thenReturn(Map.of(
                "enabled", true,
                "platform", "telegram",
                "platformLabel", "Telegram",
                "command", "/continue token-1",
                "token", "token-1",
                "openUrl", "https://t.me/support_test_bot?start=web_token-1",
                "hint", "Open bot"
        ));
        when(publicFormService.createSession(eq("web-enabled"), any(PublicFormSubmission.class), eq("ip+fp-key")))
                .thenReturn(createdSession);

        mockMvc.perform(post("/api/public/forms/web-enabled/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.1, 10.0.0.2")
                        .header("X-Public-Form-Fingerprint", "fp-001")
                        .content("""
                                {
                                  "message": "Нужна помощь",
                                  "clientName": "Анна",
                                  "clientContact": "+79991234567",
                                  "username": "anna",
                                  "answers": {"topic": "billing"},
                                  "requestId": "req-42"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ticketId").value("T-101"))
                .andExpect(jsonPath("$.token").value("token-1"))
                .andExpect(jsonPath("$.continuation.enabled").value(true))
                .andExpect(jsonPath("$.continuation.command").value("/continue token-1"))
                .andExpect(jsonPath("$.continuation.openUrl").value("https://t.me/support_test_bot?start=web_token-1"));

        ArgumentCaptor<PublicFormSubmission> submissionCaptor = ArgumentCaptor.forClass(PublicFormSubmission.class);
        verify(publicFormService).createSession(eq("web-enabled"), submissionCaptor.capture(), eq("ip+fp-key"));
        assertThat(submissionCaptor.getValue().requestId()).isEqualTo("req-42");
    }

    @Test
    void createSessionUsesRemoteAddrWhenProxyHeadersAreAbsent() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                27L,
                "web-remote",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );
        PublicFormSessionDto createdSession = new PublicFormSessionDto(
                "token-remote",
                "T-505",
                27L,
                "web-remote",
                null,
                null,
                null,
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );

        when(publicFormService.loadConfigRaw("web-remote")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey("127.0.0.1", null)).thenReturn("remote-key");
        when(publicFormService.createSession(eq("web-remote"), any(PublicFormSubmission.class), eq("remote-key")))
                .thenReturn(createdSession);
        when(publicFormService.buildContinuationOptions("web-remote", "token-remote")).thenReturn(Map.of("enabled", false));

        mockMvc.perform(post("/api/public/forms/web-remote/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").value("token-remote"));
    }

    @Test
    void configReturnsUiLocaleFromSettings() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                9L,
                "web-locale",
                "Web Form",
                1,
                true,
                false,
                404,
                "Ответим в течение дня",
                120,
                List.of()
        );
        when(publicFormService.loadConfigRaw("web-locale")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.resolveAnswersPayloadMaxLength()).thenReturn(6000);
        when(publicFormService.isSessionPollingEnabled()).thenReturn(true);
        when(publicFormService.resolveSessionPollingIntervalSeconds()).thenReturn(15);
        when(publicFormService.resolveUiLocale()).thenReturn("en");
        when(publicFormService.buildContinuationOptions("web-locale", null)).thenReturn(Map.of(
                "enabled", true,
                "platform", "vk",
                "platformLabel", "VK",
                "command", "/continue <token>",
                "token", "",
                "openUrl", "",
                "hint", "Send command"
        ));

        mockMvc.perform(get("/api/public/forms/web-locale/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.uiLocale").value("en"))
                .andExpect(jsonPath("$.successInstruction").value("Ответим в течение дня"))
                .andExpect(jsonPath("$.responseEtaMinutes").value(120))
                .andExpect(jsonPath("$.continuation.platform").value("vk"))
                .andExpect(jsonPath("$.continuation.command").value("/continue <token>"));
    }

    @Test
    void configReturnsQuestionsAndRecordsConfigView() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                10L,
                "web-questions",
                "Support Form",
                2,
                true,
                true,
                404,
                "Instruction",
                30,
                List.of(
                        new PublicFormQuestion("topic", "Тема", "select", 1, Map.of("required", true, "placeholder", "Выберите тему")),
                        new PublicFormQuestion("details", "Описание", "textarea", 2, Map.of("maxLength", 500))
                )
        );
        when(publicFormService.loadConfigRaw("web-questions")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.resolveAnswersPayloadMaxLength()).thenReturn(4096);
        when(publicFormService.isSessionPollingEnabled()).thenReturn(false);
        when(publicFormService.resolveSessionPollingIntervalSeconds()).thenReturn(20);
        when(publicFormService.resolveUiLocale()).thenReturn("ru");
        when(publicFormService.buildContinuationOptions("web-questions", null)).thenReturn(Map.of("enabled", false));

        mockMvc.perform(get("/api/public/forms/web-questions/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel.id").value(10))
                .andExpect(jsonPath("$.channel.publicId").value("web-questions"))
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.captchaEnabled").value(true))
                .andExpect(jsonPath("$.questions[0].id").value("topic"))
                .andExpect(jsonPath("$.questions[0].required").value(true))
                .andExpect(jsonPath("$.questions[1].maxLength").value(500));

        verify(publicFormService).recordConfigView(10L);
    }

    @Test
    void createSessionReturnsServerErrorOnUnexpectedException() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                12L,
                "web-err",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-err")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-err"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/api/public/forms/web-err/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

        verify(publicFormService, times(1)).recordSubmitError(12L, "internal_error");
    }


    @Test
    void createSessionReturnsStructuredValidationErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                13L,
                "web-limit",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-limit")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-limit"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("Слишком много запросов. Попробуйте позже"));

        mockMvc.perform(post("/api/public/forms/web-limit/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));

        verify(publicFormService, times(1)).recordSubmitError(13L, "Слишком много запросов. Попробуйте позже");
    }

    @Test
    void createSessionMapsValidationEmailErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                19L,
                "web-email",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-email")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-email"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("Введите корректный email"));

        mockMvc.perform(post("/api/public/forms/web-email/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_EMAIL"));
    }

    @Test
    void createSessionMapsValidationPhoneErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                20L,
                "web-phone",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-phone")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-phone"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("Введите корректный телефон"));

        mockMvc.perform(post("/api/public/forms/web-phone/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_PHONE"));
    }

    @Test
    void createSessionMapsCaptchaErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                21L,
                "web-captcha",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-captcha")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-captcha"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("captcha failed"));

        mockMvc.perform(post("/api/public/forms/web-captcha/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CAPTCHA_FAILED"));
    }

    @Test
    void createSessionMapsIdempotencyConflictErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                22L,
                "web-idempotency",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-idempotency")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-idempotency"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("requestId idempotency conflict"));

        mockMvc.perform(post("/api/public/forms/web-idempotency/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void createSessionMapsValidationRequiredErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                24L,
                "web-required",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-required")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-required"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("required field missing"));

        mockMvc.perform(post("/api/public/forms/web-required/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_REQUIRED"));
    }

    @Test
    void createSessionMapsValidationMaxLengthErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                25L,
                "web-max",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-max")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-max"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("message exceeds max length"));

        mockMvc.perform(post("/api/public/forms/web-max/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_MAX_LENGTH"));
    }

    @Test
    void createSessionMapsValidationMinLengthErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                26L,
                "web-min",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-min")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-min"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("minimum 3 symbols"));

        mockMvc.perform(post("/api/public/forms/web-min/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_MIN_LENGTH"));
    }

    @Test
    void createSessionFallsBackToGenericValidationErrorCode() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                31L,
                "web-generic-validation",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );

        when(publicFormService.loadConfigRaw("web-generic-validation")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey(any(), any())).thenReturn("ip-key");
        when(publicFormService.createSession(eq("web-generic-validation"), any(PublicFormSubmission.class), eq("ip-key")))
                .thenThrow(new IllegalArgumentException("validation failed"));

        mockMvc.perform(post("/api/public/forms/web-generic-validation/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void createSessionUsesXRealIpWhenForwardedHeaderIsAbsent() throws Exception {
        PublicFormConfig enabledConfig = new PublicFormConfig(
                23L,
                "web-real-ip",
                "Web Form",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of()
        );
        PublicFormSessionDto createdSession = new PublicFormSessionDto(
                "token-real",
                "T-404",
                23L,
                "web-real-ip",
                null,
                null,
                null,
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );

        when(publicFormService.loadConfigRaw("web-real-ip")).thenReturn(Optional.of(enabledConfig));
        when(publicFormService.buildRequesterKey("198.51.100.7", null)).thenReturn("real-ip-key");
        when(publicFormService.createSession(eq("web-real-ip"), any(PublicFormSubmission.class), eq("real-ip-key")))
                .thenReturn(createdSession);
        when(publicFormService.buildContinuationOptions("web-real-ip", "token-real"))
                .thenReturn(Map.of("enabled", false));

        mockMvc.perform(post("/api/public/forms/web-real-ip/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.51.100.7")
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").value("token-real"));
    }

    @Test
    void createSessionReturnsDisabledStatusWhenFormDisabled() throws Exception {
        PublicFormConfig disabledConfig = new PublicFormConfig(
                14L,
                "web-disabled",
                "Web Form",
                1,
                false,
                false,
                404,
                null,
                null,
                List.of()
        );
        when(publicFormService.loadConfigRaw("web-disabled")).thenReturn(Optional.of(disabledConfig));

        mockMvc.perform(post("/api/public/forms/web-disabled/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORM_DISABLED"));
    }

    @Test
    void createSessionReturnsGoneWhenFormDisabledWith410Policy() throws Exception {
        PublicFormConfig disabledConfig = new PublicFormConfig(
                28L,
                "web-disabled-gone",
                "Web Form",
                1,
                false,
                false,
                410,
                null,
                null,
                List.of()
        );
        when(publicFormService.loadConfigRaw("web-disabled-gone")).thenReturn(Optional.of(disabledConfig));

        mockMvc.perform(post("/api/public/forms/web-disabled-gone/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORM_DISABLED"));
    }

    @Test
    void createSessionReturnsNotFoundWhenChannelDoesNotExist() throws Exception {
        when(publicFormService.loadConfigRaw("web-no-channel")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/public/forms/web-no-channel/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CHANNEL_NOT_FOUND"));
    }

    @Test
    void sessionTracksLookupMetrics() throws Exception {
        PublicFormSessionDto session = new PublicFormSessionDto(
                "token-2",
                "T-202",
                15L,
                "web-session",
                "Иван",
                null,
                "web_form",
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );
        when(publicFormService.resolveChannelId("web-session")).thenReturn(Optional.of(15L));
        when(publicFormService.findSession("web-session", "token-2")).thenReturn(Optional.of(session));
        when(publicFormService.buildContinuationOptions("web-session", "token-2")).thenReturn(Map.of(
                "enabled", true,
                "platform", "max",
                "platformLabel", "MAX",
                "command", "/continue token-2",
                "token", "token-2",
                "openUrl", "",
                "hint", "Send command"
        ));
        when(dialogConversationReadService.loadHistory("T-202", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/public/forms/web-session/sessions/token-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.continuation.platform").value("max"))
                .andExpect(jsonPath("$.continuation.command").value("/continue token-2"));

        verify(publicFormService).recordSessionLookup(15L, true);
    }

    @Test
    void sessionReturnsMappedMessagesPayload() throws Exception {
        PublicFormSessionDto session = new PublicFormSessionDto(
                "token-4",
                "T-404",
                29L,
                "web-history",
                "Олег",
                "+79990000000",
                "oleg",
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );
        when(publicFormService.resolveChannelId("web-history")).thenReturn(Optional.of(29L));
        when(publicFormService.findSession("web-history", "token-4")).thenReturn(Optional.of(session));
        when(publicFormService.buildContinuationOptions("web-history", "token-4")).thenReturn(Map.of("enabled", false));
        when(dialogConversationReadService.loadHistory("T-404", null)).thenReturn(List.of(
                new ChatMessageDto(
                        "support",
                        "Готовы помочь",
                        "Готовы помочь",
                        "2026-01-01T10:20:00+03:00",
                        "text",
                        null,
                        null,
                        null,
                        "preview",
                        null,
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/api/public/forms/web-history/sessions/token-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.session.ticketId").value("T-404"))
                .andExpect(jsonPath("$.messages[0].sender").value("support"))
                .andExpect(jsonPath("$.messages[0].messageType").value("text"))
                .andExpect(jsonPath("$.messages[0].replyPreview").value("preview"));
    }

    @Test
    void sessionReturnsNotFoundAndTracksMissedLookup() throws Exception {
        when(publicFormService.resolveChannelId("web-missing-session")).thenReturn(Optional.of(16L));
        when(publicFormService.findSession("web-missing-session", "token-missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/forms/web-missing-session/sessions/token-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));

        verify(publicFormService).recordSessionLookup(16L, false);
    }

    @Test
    void sessionDoesNotLoadHistoryWhenSessionIsMissing() throws Exception {
        when(publicFormService.resolveChannelId("web-no-history")).thenReturn(Optional.of(17L));
        when(publicFormService.findSession("web-no-history", "token-none")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/forms/web-no-history/sessions/token-none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));

        verify(publicFormService).recordSessionLookup(17L, false);
        verify(dialogConversationReadService, times(0)).loadHistory(any(), any());
    }

    @Test
    void sessionReturnsClientFieldsAndCreatedAtFromSessionPayload() throws Exception {
        PublicFormSessionDto session = new PublicFormSessionDto(
                "token-5",
                "T-505",
                30L,
                "web-client",
                "Елена",
                "+79991112233",
                "elena",
                OffsetDateTime.parse("2026-01-01T12:15:30+03:00")
        );
        when(publicFormService.resolveChannelId("web-client")).thenReturn(Optional.of(30L));
        when(publicFormService.findSession("web-client", "token-5")).thenReturn(Optional.of(session));
        when(publicFormService.buildContinuationOptions("web-client", "token-5")).thenReturn(Map.of("enabled", false));
        when(dialogConversationReadService.loadHistory("T-505", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/public/forms/web-client/sessions/token-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.session.clientName").value("Елена"))
                .andExpect(jsonPath("$.session.clientContact").value("+79991112233"))
                .andExpect(jsonPath("$.session.username").value("elena"))
                .andExpect(jsonPath("$.session.createdAt").value("2026-01-01T12:15:30+03:00"));
    }

    @Test
    void sessionPassesChannelFilterIntoHistoryLoader() throws Exception {
        PublicFormSessionDto session = new PublicFormSessionDto(
                "token-3",
                "T-303",
                18L,
                "web-filter",
                "Мария",
                null,
                "web_form",
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );
        when(publicFormService.resolveChannelId("web-filter")).thenReturn(Optional.of(18L));
        when(publicFormService.findSession("web-filter", "token-3")).thenReturn(Optional.of(session));
        when(publicFormService.buildContinuationOptions("web-filter", "token-3")).thenReturn(Map.of("enabled", false));
        when(dialogConversationReadService.loadHistory("T-303", 44L)).thenReturn(List.of());

        mockMvc.perform(get("/api/public/forms/web-filter/sessions/token-3")
                        .param("channel", "44"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(dialogConversationReadService).loadHistory("T-303", 44L);
    }

    @Test
    void sessionDoesNotRecordLookupWhenChannelIdCannotBeResolved() throws Exception {
        when(publicFormService.resolveChannelId("web-unresolved")).thenReturn(Optional.empty());
        when(publicFormService.findSession("web-unresolved", "token-none")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/forms/web-unresolved/sessions/token-none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

}
