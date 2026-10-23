package com.example.panel.controller;

import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.DashboardAnalyticsService;
import com.example.panel.service.DialogLookupReadService;
import com.example.panel.service.IncidentOpsMetricsService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
class HomeControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean NavigationService navigation;
    @MockBean PermissionService permissions;
    @MockBean DialogLookupReadService dialogs;
    @MockBean DashboardAnalyticsService analytics;
    @MockBean IncidentOpsMetricsService incidents;
    @MockBean ChannelRepository channels;
    @MockBean SharedConfigService sharedConfig;

    @Test void rootRendersPulseAndSectionLinksWithoutLoadingCustomerRows() throws Exception {
        navigationFlags(true);
        when(permissions.hasAuthority(any(), anyString())).thenReturn(true);
        mvc.perform(get("/").with(csrf())).andExpect(status().isOk())
                .andExpect(view().name("home/index"))
                .andExpect(content().string(containsString("data-home-pulse")))
                .andExpect(content().string(containsString("/css/app.css?v=20261023-neutral-ops-r10")))
                .andExpect(content().string(containsString("href=\"/dialogs\"")))
                .andExpect(content().string(containsString("data-page-key=\"home\"")))
                .andExpect(content().string(not(containsString("id=\"dialogsTable\""))));
        verifyNoInteractions(dialogs, analytics, incidents, channels, sharedConfig);
    }

    @Test void homeDoesNotExposeRestrictedSectionBubbles() throws Exception {
        navigationFlags(false);
        mvc.perform(get("/").with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"home-section-link\""))));
    }

    private void navigationFlags(boolean value) {
        doAnswer(invocation -> {
            org.springframework.ui.Model model = invocation.getArgument(0);
            for (String flag : List.of("canManageTasks", "canViewIncidents", "canViewClients", "canViewPassports",
                    "canEditKnowledgeBase", "canViewScopedAiSources", "canViewAnalytics", "canViewSettings",
                    "canManageChannels", "canManageUsers")) model.addAttribute(flag, value);
            model.addAttribute("sidebarUserDisplayName", "Оператор");
            model.addAttribute("sidebarUserUsername", "operator");
            return null;
        }).when(navigation).enrich(any(), any());
    }
}
