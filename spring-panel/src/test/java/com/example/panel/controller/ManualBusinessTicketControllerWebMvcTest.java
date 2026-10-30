package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessScopedTicketReadService;
import com.example.panel.service.ManualBusinessTicketCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManualBusinessTicketController.class)
@Import(ManualBusinessTicketControllerWebMvcTest.MethodSecurity.class)
class ManualBusinessTicketControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ManualBusinessTicketCreationService service;

    @MockBean
    private BusinessScopedTicketReadService readService;

    @MockBean
    private MultibusinessFeatureFlags flags;

    @Test
    void createsOnlyWithPagePermissionAndBothExplicitFlags() throws Exception {
        String body = "{\"businessId\":2,\"clientName\":\"Анна\",\"problem\":\"Вопрос\"}";
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(flags.isManualTicketCreation()).thenReturn(true);
        when(readService.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(7L);
        when(service.create(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class), org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.eq("Анна"), org.mockito.ArgumentMatchers.eq("Вопрос"), org.mockito.ArgumentMatchers.eq(null))).thenReturn(
                new ManualBusinessTicketCreationService.CreatedTicket(
                        "0123456789abcdef0123456789abcdef", "manual-ticket", -10L, 2L));

        mvc.perform(post("/api/business-scope/tickets/manual").with(csrf())
                        .with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.clientUserId").value(-10))
                .andExpect(jsonPath("$.businessId").value(2));
        mvc.perform(post("/api/business-scope/tickets/manual").with(csrf())
                        .with(user("operator").authorities(() -> "PAGE_SETTINGS"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        when(flags.isManualTicketCreation()).thenReturn(false);
        mvc.perform(post("/api/business-scope/tickets/manual").with(csrf())
                        .with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesMissingBusinessBeforeInvokingWriteService() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(flags.isManualTicketCreation()).thenReturn(true);
        mvc.perform(post("/api/business-scope/tickets/manual").with(csrf())
                        .with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                        .contentType("application/json").content("{\"clientName\":\"Анна\",\"problem\":\"Вопрос\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
