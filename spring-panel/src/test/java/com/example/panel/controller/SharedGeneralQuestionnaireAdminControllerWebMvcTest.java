package com.example.panel.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.SharedGeneralQuestionnaireService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SharedGeneralQuestionnaireAdminController.class)
@Import(SharedGeneralQuestionnaireAdminControllerWebMvcTest.MethodSecurity.class)
class SharedGeneralQuestionnaireAdminControllerWebMvcTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean SharedGeneralQuestionnaireService service;
    @MockBean MultibusinessFeatureFlags flags;
    private static final String PATH = "/api/business-admin/shared-channels/10/general-question-template";
    private static final String PAYLOAD = "{\"templateId\":\"q-general\"}";

    @ParameterizedTest @ValueSource(strings={"PAGE_SETTINGS", "ROLE_ADMIN", "BUSINESS_ADMIN", "ALL_BUSINESSES"})
    void neitherBusinessAdminNorPartialSystemAuthorityCanConfigure(String authority) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get(PATH).with(user("limited").authorities(() -> authority))).andExpect(status().isForbidden());
        mvc.perform(put(PATH).with(user("limited").authorities(() -> authority)).with(csrf())
            .contentType("application/json").content(PAYLOAD)).andExpect(status().isForbidden());
        mvc.perform(delete(PATH).with(user("limited").authorities(() -> authority)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void csrfAndFoundationAreRequiredBeforeAnyServiceWrite() throws Exception {
        var admin = user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN");
        mvc.perform(put(PATH).with(admin).with(csrf()).contentType("application/json").content(PAYLOAD)).andExpect(status().isNotFound());
        mvc.perform(get(PATH).with(admin)).andExpect(status().isNotFound());
        mvc.perform(delete(PATH).with(admin).with(csrf())).andExpect(status().isNotFound());
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(put(PATH).with(admin).contentType("application/json").content(PAYLOAD)).andExpect(status().isForbidden());
        mvc.perform(delete(PATH).with(admin)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void systemAdministratorCanSaveReadAndDeactivateSeparateBinding() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        var admin = user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN");
        var binding = new SharedGeneralQuestionnaireService.Binding(10, "q-general", "a".repeat(64), true);
        when(service.bind(10, "q-general")).thenReturn(Optional.of(binding));
        when(service.findBinding(10)).thenReturn(Optional.of(binding));
        when(service.deactivate(10)).thenReturn(true);
        mvc.perform(put(PATH).with(admin).with(csrf()).contentType("application/json").content(PAYLOAD))
            .andExpect(status().isOk()).andExpect(jsonPath("$.templateId").value("q-general"));
        mvc.perform(get(PATH).with(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(delete(PATH).with(admin).with(csrf())).andExpect(status().isNoContent());
        verify(service).bind(10, "q-general"); verify(service).deactivate(10);
    }

    @ParameterizedTest @ValueSource(strings={"invalid", "conflict", "duplicate", "missing"})
    void invalidConflictingAndMissingConfigurationUseExplicitErrors(String reason) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        var admin = user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN");
        int statusCode;
        switch (reason) {
            case "invalid" -> { when(service.bind(10, "q-general")).thenThrow(new IllegalArgumentException("Invalid")); statusCode=400; }
            case "conflict" -> { when(service.bind(10, "q-general")).thenThrow(new IllegalStateException("Reserved")); statusCode=409; }
            case "duplicate" -> { when(service.bind(10, "q-general")).thenThrow(new DuplicateKeyException("Reserved")); statusCode=409; }
            default -> { when(service.bind(10, "q-general")).thenReturn(Optional.empty()); statusCode=404; }
        }
        mvc.perform(put(PATH).with(admin).with(csrf()).contentType("application/json").content(PAYLOAD))
            .andExpect(status().is(statusCode));
    }

    @Test void missingBodyAndAbsentReadOrDeleteDoNotSucceed() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        var admin = user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN");
        mvc.perform(put(PATH).with(admin).with(csrf()).contentType("application/json")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(admin)).andExpect(status().isNotFound());
        mvc.perform(delete(PATH).with(admin).with(csrf())).andExpect(status().isNotFound());
        verify(service, never()).bind(anyLong(), any());
    }
}
