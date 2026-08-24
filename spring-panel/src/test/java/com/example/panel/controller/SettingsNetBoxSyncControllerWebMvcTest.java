package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.NetBoxObjectPassportSyncService;
import com.example.panel.service.NetBoxSyncSettingsService;
import com.example.panel.service.SettingsTopLevelUpdateService;
import com.example.panel.service.SharedConfigService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettingsNetBoxSyncController.class)
@AutoConfigureMockMvc
class SettingsNetBoxSyncControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NetBoxObjectPassportSyncService syncService;

    @MockBean
    private NetBoxSyncSettingsService netBoxSyncSettingsService;

    @MockBean
    private SharedConfigService sharedConfigService;

    @MockBean
    private SettingsTopLevelUpdateService settingsTopLevelUpdateService;

    @Test
    void loadSitesSupportsPostPayload() throws Exception {
        NetBoxSyncSettingsService.NetBoxSyncSettings settings =
                new NetBoxSyncSettingsService.NetBoxSyncSettings(
                        "https://netbox.example.com",
                        "secret",
                        false,
                        60,
                        false,
                        List.of("160")
                );
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(netBoxSyncSettingsService.load(anyMap())).thenReturn(settings);
        when(syncService.loadAvailableSites(settings)).thenReturn(List.of(
                new NetBoxObjectPassportSyncService.NetBoxSiteOption("160", "Main site", "active")
        ));

        mockMvc.perform(post("/api/settings/netbox-sync/sites")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "netbox_sync": {
                                    "base_url": "https://netbox.example.com",
                                    "api_token": "secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sites[0].id").value("160"))
                .andExpect(jsonPath("$.sites[0].name").value("Main site"))
                .andExpect(jsonPath("$.sites[0].status").value("active"))
                .andExpect(jsonPath("$.selectedSiteIds[0]").value("160"));
    }

    @Test
    void loadSitesSupportsTrailingSlashGet() throws Exception {
        NetBoxSyncSettingsService.NetBoxSyncSettings settings =
                new NetBoxSyncSettingsService.NetBoxSyncSettings(
                        "https://netbox.example.com",
                        "secret",
                        false,
                        60,
                        false,
                        List.of()
                );
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(netBoxSyncSettingsService.load(anyMap())).thenReturn(settings);
        when(syncService.loadAvailableSites(settings)).thenReturn(List.of());

        mockMvc.perform(get("/api/settings/netbox-sync/sites/")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void loadSitesReturnsStructuredErrorBody() throws Exception {
        NetBoxSyncSettingsService.NetBoxSyncSettings settings =
                new NetBoxSyncSettingsService.NetBoxSyncSettings(
                        "https://netbox.example.com",
                        "secret",
                        false,
                        60,
                        false,
                        List.of()
                );
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(netBoxSyncSettingsService.load(anyMap())).thenReturn(settings);
        when(syncService.loadAvailableSites(settings))
                .thenThrow(new IllegalStateException("NetBox вернул HTTP 500 для /api/extras/image-attachments/..."));

        mockMvc.perform(post("/api/settings/netbox-sync/sites")
                        .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "netbox_sync": {
                                    "base_url": "https://netbox.example.com",
                                    "api_token": "secret"
                                  }
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("NetBox вернул HTTP 500 для /api/extras/image-attachments/..."));
    }
}
