package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc
class DashboardControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private DialogService dialogService;

    @MockBean
    private NavigationService navigationService;

    @Test
    void dashboardIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(dialogService.loadSummary()).thenReturn(new DialogSummary(0, 0, 0, Collections.emptyList()));
        when(dialogService.loadDialogs(anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/dashboard").with(user("operator").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"dashboard\"")));
    }
}
