package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessQuestionTemplateRegistryService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
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

@WebMvcTest(BusinessQuestionTemplateAdminController.class)
@Import(BusinessQuestionTemplateAdminControllerWebMvcTest.MethodSecurity.class)
class BusinessQuestionTemplateAdminControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired MockMvc mvc;
    @MockBean BusinessQuestionTemplateRegistryService registry;
    @MockBean MultibusinessFeatureFlags flags;

    @Test
    void bindingRequiresSystemAdminFoundationAndCsrf() throws Exception {
        String path = "/api/business-admin/question-templates/businesses/7";
        String payload = "{\"templateId\":\"q-a\"}";
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
        verify(registry, never()).bind(7L, "q-a");

        when(registry.bind(7L, "q-a")).thenReturn(Optional.of(
                new BusinessQuestionTemplateRegistryService.Binding(7L, "q-a", "a".repeat(64), true)));
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value("q-a"));
    }

    @Test
    void duplicateTemplateAndMissingBusinessFailClosed() throws Exception {
        String path = "/api/business-admin/question-templates/businesses/7";
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(registry.bind(7L, "q-a")).thenThrow(new DuplicateKeyException("duplicate"));
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json").content("{\"templateId\":\"q-a\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json").content("{\"templateId\":\"q-b\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()))
                .andExpect(status().isNotFound());
        when(registry.deactivate(7L)).thenReturn(true);
        mvc.perform(delete(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
