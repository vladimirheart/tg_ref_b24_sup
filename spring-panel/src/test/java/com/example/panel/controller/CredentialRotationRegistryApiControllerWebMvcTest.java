package com.example.panel.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.CredentialRotationRegistryEntry;
import com.example.panel.service.CredentialRotationRegistryService;
import com.example.panel.service.IncidentService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CredentialRotationRegistryApiController.class)
@AutoConfigureMockMvc
class CredentialRotationRegistryApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CredentialRotationRegistryService registryService;

    @MockBean
    private IncidentService incidentService;

    @Test
    void listEntriesIncludesIncidentAlertingAndRelatedIncidents() throws Exception {
        CredentialRotationRegistryEntry entry = new CredentialRotationRegistryEntry();
        entry.setId(42L);
        entry.setEntryKey("obsolete.secret");
        entry.setDisplayName("Obsolete secret");
        entry.setIntegrationKind("legacy");
        entry.setCredentialKind("shared_secret");
        entry.setSourceType("settings_json");
        entry.setSourceRef("settings.json#obsolete");
        entry.setSourcePresent(false);
        entry.setSecretPresent(false);
        entry.setLastStatus(CredentialRotationRegistryService.STATUS_SOURCE_REMOVED);
        entry.setStatusLevel(CredentialRotationRegistryService.LEVEL_CRITICAL);
        entry.setStatusReason("Источник секрета больше не обнаружен.");
        entry.setLastCheckedAt(OffsetDateTime.parse("2026-08-25T12:00:00Z"));
        entry.setUpdatedAt(OffsetDateTime.parse("2026-08-25T12:00:00Z"));

        when(registryService.buildSnapshot()).thenReturn(new CredentialRotationRegistryService.RegistrySnapshot(
            OffsetDateTime.parse("2026-08-25T12:00:00Z"),
            new CredentialRotationRegistryService.RegistryOverview(1, 0, 0, 1, 0, 0, 1),
            List.of(entry)
        ));
        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("id", 77L);
        incident.put("incident_key", "INC-77");
        incident.put("title", "Credential rotation critical: Obsolete secret");
        incident.put("summary", "Источник секрета больше не обнаружен.");
        incident.put("status", "open");
        incident.put("severity", "critical");
        incident.put("source", "credential_rotation_registry");
        incident.put("signal_key", "obsolete.secret");
        incident.put("updated_at", "2026-08-25T12:05:00Z");
        incident.put("created_at", "2026-08-25T12:01:00Z");
        incident.put("resolved_at", "");
        incident.put("route_count", 2);
        incident.put("failed_route_count", 1);
        when(incidentService.listIncidentSummariesForSignalType(CredentialRotationRegistryService.INCIDENT_SIGNAL_TYPE))
            .thenReturn(List.of(incident));

        mockMvc.perform(get("/api/monitoring/credential-rotation/entries")
                .with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.incident_alerting.signal_type").value("credential_rotation"))
            .andExpect(jsonPath("$.incident_alerting.escalation_policy").value("critical"))
            .andExpect(jsonPath("$.incident_alerting.active_incident_count").value(1))
            .andExpect(jsonPath("$.incident_alerting.active_entry_count").value(1))
            .andExpect(jsonPath("$.items[0].entry_key").value("obsolete.secret"))
            .andExpect(jsonPath("$.items[0].has_active_incident").value(true))
            .andExpect(jsonPath("$.items[0].related_incidents[0].incident_key").value("INC-77"));
    }
}
