package com.example.panel.controller;

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

}
