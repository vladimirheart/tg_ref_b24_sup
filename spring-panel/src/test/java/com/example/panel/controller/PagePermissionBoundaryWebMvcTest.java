package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.security.SecurityConfig;
import com.example.panel.service.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({DialogsController.class, DialogAiOpsController.class})
@Import({SecurityConfig.class, PermissionService.class, NavigationService.class, DialogAuthorizationService.class})
class PagePermissionBoundaryWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean(name = "usersJdbcTemplate") JdbcTemplate users;
    @MockBean SharedConfigService config;
    @MockBean MultibusinessFeatureFlags flags;
    @MockBean DialogListReadService dialogs;
    @MockBean DialogAiOpsService aiOps;
    @MockBean DialogAuditService audit;
    @MockBean DialogResponsibilityService responsibility;
    @MockBean LegacyTicketIdJdbcGuard tickets;
    @MockBean PanelUserRepository accounts;
    @MockBean PanelUserPhotoService photos;
    @MockBean UnblockRequestService unblock;

    @Test void aiOpsGrantOpensOnlyItsPageAndOnlyItsSidebarLink() throws Exception {
        when(accounts.findByUsernameIgnoreCase("operator")).thenReturn(Optional.empty());
        mvc.perform(get("/ai-ops").with(user("operator").authorities(() -> "PAGE_AI_OPS")))
                .andExpect(status().isOk()).andExpect(view().name("dialogs/ai-ops"))
                .andExpect(content().string(containsString("data-can-view-dialogs=\"false\"")))
                .andExpect(content().string(containsString("data-page-key=\"ai-ops\"")))
                .andExpect(content().string(not(containsString("data-page-key=\"dialogs\""))));
        mvc.perform(get("/dialogs").with(user("operator").authorities(() -> "PAGE_AI_OPS")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dialogs);
    }

    @Test void dialogsAndAdminRoleAloneDoNotOpenAiOps() throws Exception {
        mvc.perform(get("/ai-ops").with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ai-ops").with(user("operator").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aiOps, dialogs);
    }

    @ParameterizedTest @ValueSource(strings = {"ai-reviews", "ai-solution-memory", "ai-solution-memory/key/history",
            "ai-intents", "ai-knowledge-units", "ai-monitoring/summary", "ai-monitoring/events", "ai-monitoring/offline-eval"})
    void dialogsPageDoesNotGrantTheGlobalAiOpsApis(String endpoint) throws Exception {
        mvc.perform(get("/api/dialogs/" + endpoint).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aiOps);
    }

    @ParameterizedTest @ValueSource(strings = {"ai-reviews/key/approve", "ai-reviews/key/reject", "ai-solution-memory/key",
            "ai-solution-memory/key/rollback", "ai-intents", "ai-knowledge-units", "ai-monitoring/offline-eval/run"})
    void dialogsPageDoesNotGrantGlobalAiOpsMutations(String endpoint) throws Exception {
        mvc.perform(post("/api/dialogs/" + endpoint).with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aiOps);
    }

    @Test void globalAiOpsApiWorksWithoutTheDialogsGrant() throws Exception {
        when(aiOps.loadMonitoringSummary(7)).thenReturn(Map.of("success", true));
        mvc.perform(get("/api/dialogs/ai-monitoring/summary").param("days", "7")
                .with(user("operator").authorities(() -> "PAGE_AI_OPS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(aiOps).loadMonitoringSummary(7);
    }

    @Test void reviewApprovalUsesTheAiOpsGrantAndMemoryDeletionCannotUseTheDialogsGrant() throws Exception {
        when(aiOps.approveReviewByKey("key", "operator")).thenReturn(Map.of("success", true));
        mvc.perform(post("/api/dialogs/ai-reviews/key/approve").with(csrf())
                .with(user("operator").authorities(() -> "PAGE_AI_OPS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(aiOps).approveReviewByKey("key", "operator");
        mvc.perform(delete("/api/dialogs/ai-solution-memory/key").with(csrf())
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isForbidden());
        verify(aiOps, never()).deleteSolutionMemory(anyString(), anyString());
    }

    @Test void dialogIncidentExportKeepsItsExactTicketGuard() throws Exception {
        when(tickets.hasUniqueNonGeneralTicket(" T/1 Ю ")).thenReturn(true);
        mvc.perform(get("/api/dialogs/ai-monitoring/events").param("ticketId", " T/1 Ю ")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk());
        verify(aiOps).loadMonitoringEvents(null, null, " T/1 Ю ", null, null);
        mvc.perform(get("/api/dialogs/ai-monitoring/events").param("ticketId", "GENERAL")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        verify(aiOps, never()).loadMonitoringEvents(any(), any(), eq("GENERAL"), any(), any());
    }

    @Test void aiOpsDoesNotGrantDialogRepliesOrBypassCsrf() throws Exception {
        mvc.perform(get("/api/dialog-ticket/ai-suggestions").param("ticketId", "T-1")
                .with(user("operator").authorities(() -> "PAGE_AI_OPS")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dialogs/ai-monitoring/offline-eval/run")
                .with(user("operator").authorities(() -> "PAGE_AI_OPS")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aiOps, tickets);
    }
}
