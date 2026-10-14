package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessScopedTicketReadService;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessScopeReadController.class)
@Import(BusinessScopeReadControllerWebMvcTest.MethodSecurity.class)
class BusinessScopeReadControllerWebMvcTest {

    private static final String PUBLIC_ID = "0123456789abcdef0123456789abcdef";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BusinessScopedTicketReadService service;

    @MockBean
    private MultibusinessFeatureFlags flags;

    @Test
    void requiresDialogsPageAndEnabledFoundation() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void directLookupReturnsNonDisclosingNotFoundOrAuthorizedTicket() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId("operator")).thenReturn(1L);
        when(service.findByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.empty());
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());

        when(service.findByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketRow(
                        8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши")));
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessCode").value("sushi"))
                .andExpect(jsonPath("$.ticketId").value("raw-ticket"));
    }

    @Test
    void historyRequiresPageAndReturnsOnlyScopedSnapshot() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId("operator")).thenReturn(1L);
        String path = "/api/business-scope/tickets/" + PUBLIC_ID + "/history";
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        var ticket = new BusinessScopedTicketReadService.TicketRow(
                8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши");
        when(service.findHistoryByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketHistory(ticket, List.of(
                        new BusinessScopedTicketReadService.HistoryEntry(
                                3L, "client", "Здравствуйте", null, "text", false)))));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.businessCode").value("sushi"))
                .andExpect(jsonPath("$.entries[0].message").value("Здравствуйте"));
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }
}
