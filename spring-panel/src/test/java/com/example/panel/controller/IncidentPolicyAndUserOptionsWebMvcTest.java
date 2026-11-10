package com.example.panel.controller;

import com.example.panel.entity.PanelUser;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.IncidentMonitoringPolicyService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({IncidentMonitoringPolicyController.class, PanelUserOptionsController.class})
@org.springframework.context.annotation.Import(IncidentPolicyAndUserOptionsWebMvcTest.MethodSecurity.class)
class IncidentPolicyAndUserOptionsWebMvcTest {
    // Routing slices mock only account eligibility; the PostgreSQL/HTTP suite uses the real guard.
    @MockBean(name="sharedSystemAccessService") com.example.panel.service.SharedSystemAccessService sharedSystemAccessService;
    @org.junit.jupiter.api.BeforeEach void currentAccount() {
        org.mockito.Mockito.when(sharedSystemAccessService.canAccess(org.mockito.ArgumentMatchers.nullable(org.springframework.security.core.Authentication.class))).thenReturn(true);
    }

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean IncidentMonitoringPolicyService policy;
    @MockBean PanelUserRepository users;

    @Test void commonPolicyCanBeReadAndChangedWithoutPageRightsButRequiresCsrf() throws Exception {
        when(policy.overview()).thenReturn(Map.of("items", List.of()));
        mvc.perform(get("/api/incidents/monitoring").with(user("operator").authorities(List.of())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.can_edit").value(true));
        mvc.perform(patch("/api/incidents/monitoring/smtp").with(user("operator").authorities(List.of()))
            .contentType("application/json").content("{\"mode\":\"disabled\"}"))
            .andExpect(status().isForbidden());
        verify(policy, never()).update(anyString(), anyMap(), anyString());
        when(policy.update(eq("smtp"), anyMap(), eq("operator"))).thenReturn(Map.of("items", List.of()));
        mvc.perform(patch("/api/incidents/monitoring/smtp").with(user("operator").authorities(List.of())).with(csrf())
            .contentType("application/json").content("{\"mode\":\"disabled\"}"))
            .andExpect(status().isOk());
    }

    @Test void commonRosterIsMinimalAndIncludesOnlyActiveUnambiguousAccounts() throws Exception {
        PanelUser active = new PanelUser(); active.setUsername("operator"); active.setFullName("Оператор"); active.setPasswordHash("never-serialize"); active.setEmail("private@example.test");
        PanelUser blocked = new PanelUser(); blocked.setUsername("blocked"); blocked.setBlocked(true);
        when(users.findEmployeeOptions()).thenReturn(List.of(
            new PanelUserRepository.EmployeeOptionRow(active.getUsername(), active.getFullName(), true, false, 1),
            new PanelUserRepository.EmployeeOptionRow(blocked.getUsername(), blocked.getFullName(), true, true, 1)));
        mvc.perform(get("/api/panel-users/options").with(user("reader").authorities(List.of())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].value").value("operator"))
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist()).andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test void granularTransportPolicyUsesTheSameCommonAccessAndCsrfBoundary() throws Exception {
        String endpoint = "/api/incidents/monitoring/integration_transport.ui_updates";
        when(policy.update(eq("integration_transport.ui_updates"), anyMap(), eq("reader"))).thenReturn(Map.of("items", List.of()));
        mvc.perform(patch(endpoint).with(user("reader").authorities(List.of())).contentType("application/json").content("{\"mode\":\"disabled\"}"))
            .andExpect(status().isForbidden());
        verify(policy, never()).update(anyString(), anyMap(), anyString());
        mvc.perform(patch(endpoint).with(user("reader").authorities(List.of())).with(csrf()).contentType("application/json").content("{\"mode\":\"disabled\"}"))
            .andExpect(status().isOk());
        verify(policy).update(eq("integration_transport.ui_updates"), eq(Map.of("mode", "disabled")), eq("reader"));
    }
}
