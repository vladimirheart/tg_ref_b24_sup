package com.example.panel.controller;

import com.example.panel.service.SettingsUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsBridgeController.class)
@AutoConfigureMockMvc
class SettingsBridgeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsUpdateService settingsUpdateService;

    @Test
    void updateSettingsDelegatesToService() throws Exception {
        when(settingsUpdateService.updateSettings(anyMap(), any()))
                .thenReturn(Map.of("success", true, "warnings", java.util.List.of("warn")));

        mockMvc.perform(post("/settings")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["vip"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings[0]").value("warn"));
    }

    @Test
    void updateSettingsRejectsAnonymousUser() throws Exception {
        mockMvc.perform(post("/settings")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "categories": ["vip"]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateSettingsSupportsPutRoute() throws Exception {
        when(settingsUpdateService.updateSettings(anyMap(), any()))
                .thenReturn(Map.of("success", true, "saved", true));

        mockMvc.perform(put("/settings")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_statuses": ["new"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.saved").value(true));
    }

    @Test
    void updateSettingsSupportsPatchRouteAndTrailingSlash() throws Exception {
        when(settingsUpdateService.updateSettings(anyMap(), any()))
                .thenReturn(Map.of("success", true, "updated", true));

        mockMvc.perform(patch("/settings/")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "reporting": {"enabled": true}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(true));
    }

    @Test
    void updateSettingsSupportsTrailingSlashPostRoute() throws Exception {
        when(settingsUpdateService.updateSettings(anyMap(), any()))
                .thenReturn(Map.of("success", true, "applied", true));

        mockMvc.perform(post("/settings/")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "integration": {"enabled": true}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.applied").value(true));
    }
}
