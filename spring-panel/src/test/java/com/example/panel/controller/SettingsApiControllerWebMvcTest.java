package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.SettingsClientStatusService;
import com.example.panel.service.SettingsIntegrationNetworkProbeService;
import com.example.panel.service.SettingsItConnectionCategoryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettingsApiController.class)
@AutoConfigureMockMvc
class SettingsApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsClientStatusService settingsClientStatusService;

    @MockBean
    private SettingsItConnectionCategoryService settingsItConnectionCategoryService;

    @MockBean
    private SettingsIntegrationNetworkProbeService settingsIntegrationNetworkProbeService;

    @Test
    void updateClientStatusesDelegatesToService() throws Exception {
        when(settingsClientStatusService.updateClientStatuses(anyMap()))
                .thenReturn(Map.of("ok", true));

        mockMvc.perform(post("/api/settings/client-statuses")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_statuses": ["vip"],
                                  "client_status_colors": {"vip": "#fff000"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void createItConnectionCategorySupportsTrailingSlash() throws Exception {
        when(settingsItConnectionCategoryService.createCategory(anyMap()))
                .thenReturn(Map.of(
                        "success", true,
                        "data", Map.of("key", "corp_vpn", "label", "Corp VPN")
                ));

        mockMvc.perform(post("/api/settings/it-connection-categories/")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "label": "Corp VPN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("corp_vpn"));
    }

    @Test
    void probeIntegrationNetworkProfilesDelegatesToService() throws Exception {
        when(settingsIntegrationNetworkProbeService.probeProfiles(anyMap()))
                .thenReturn(Map.of(
                        "success", true,
                        "items", List.of(Map.of("id", "corp-proxy", "reachable", true))
                ));

        mockMvc.perform(post("/api/settings/integration-network/profiles/probe")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "profile": {
                                    "id": "corp-proxy"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.items[0].id").value("corp-proxy"));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/settings/client-statuses")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_statuses": ["vip"]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
