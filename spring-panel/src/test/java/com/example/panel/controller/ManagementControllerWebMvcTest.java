package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.panel.repository.AppSettingRepository;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ItEquipmentCatalogRepository;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.repository.SettingsParameterRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SharedConfigService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagementController.class)
@AutoConfigureMockMvc
class ManagementControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NavigationService navigationService;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private ChannelRepository channelRepository;

    @MockBean
    private PanelUserRepository panelUserRepository;

    @MockBean
    private AppSettingRepository appSettingRepository;

    @MockBean
    private SettingsParameterRepository settingsParameterRepository;

    @MockBean
    private ItEquipmentCatalogRepository equipmentRepository;

    @MockBean
    private SharedConfigService sharedConfigService;

    @MockBean
    private SettingsCatalogService settingsCatalogService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void settingsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(appSettingRepository.findAll()).thenReturn(List.of());
        when(settingsParameterRepository.findAll()).thenReturn(List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(sharedConfigService.loadLocations()).thenReturn(null);
        when(settingsCatalogService.collectCities(Map.of())).thenReturn(List.of());
        when(settingsCatalogService.getParameterTypes()).thenReturn(Map.of());
        when(settingsCatalogService.getParameterDependencies()).thenReturn(Map.of());
        when(settingsCatalogService.getItConnectionCategories(Map.of())).thenReturn(Map.of());
        when(settingsCatalogService.getItConnectionCategoryFields()).thenReturn(Map.of());
        when(settingsCatalogService.buildLocationPresets(Map.of(), Map.of())).thenReturn(Map.of());
        when(permissionService.hasAuthority(any(), any())).thenReturn(false);

        mockMvc.perform(get("/settings").with(user("operator").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(view().name("settings/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"settings\"")));
    }

    @Test
    void tasksPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(taskRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(panelUserRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/tasks").with(user("operator").authorities(() -> "PAGE_TASKS")))
            .andExpect(status().isOk())
            .andExpect(view().name("tasks/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"tasks\"")));
    }

    @Test
    void channelsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(channelRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/channels").with(user("operator").authorities(() -> "PAGE_CHANNELS")))
            .andExpect(status().isOk())
            .andExpect(view().name("channels/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"channels\"")));
    }

    @Test
    void usersPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(panelUserRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users").with(user("operator").authorities(() -> "PAGE_USERS")))
            .andExpect(status().isOk())
            .andExpect(view().name("users/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"users\"")));
    }

    @Test
    void objectPassportsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(equipmentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/object-passports").with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
            .andExpect(status().isOk())
            .andExpect(view().name("passports/list"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"passports\"")));
    }
}
