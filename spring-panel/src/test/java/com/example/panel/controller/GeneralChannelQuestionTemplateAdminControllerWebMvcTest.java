package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralChannelQuestionTemplateService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GeneralChannelQuestionTemplateAdminController.class)
@Import(GeneralChannelQuestionTemplateAdminControllerWebMvcTest.MethodSecurity.class)
class GeneralChannelQuestionTemplateAdminControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired MockMvc mvc;
    @MockBean GeneralChannelQuestionTemplateService service;
    @MockBean MultibusinessFeatureFlags flags;

    @Test
    void bindingRequiresSystemAdminFoundationAndCsrf() throws Exception {
        String path = "/api/business-admin/general-channels/10/question-template";
        String payload = "{\"templateId\":\"q-general\"}";
        mvc.perform(put(path).with(user("operator").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isNotFound());
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        verify(service, never()).bind(10L, "q-general");

        when(service.bind(10L, "q-general")).thenReturn(Optional.of(
                new GeneralChannelQuestionTemplateService.Binding(10L, "q-general", "a".repeat(64), true)));
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value("q-general"));
        verify(service).bind(10L, "q-general");
    }

    @Test
    void conflictingBusinessBindingAndMissingChannelFailClosed() throws Exception {
        String path = "/api/business-admin/general-channels/10/question-template";
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.bind(10L, "q-general")).thenThrow(new IllegalStateException("business bindings"));
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json").content("{\"templateId\":\"q-general\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf())).andExpect(status().isNotFound());
        when(service.deactivate(10L)).thenReturn(true);
        mvc.perform(delete(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf())).andExpect(status().isNoContent());
    }
}
