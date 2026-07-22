package com.example.panel.controller;

import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.UnblockRequestService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UnblockRequestsController.class)
@AutoConfigureMockMvc
@Import(NavigationService.class)
class UnblockRequestsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnblockRequestService unblockRequestService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private PanelUserRepository panelUserRepository;

    @MockBean
    private PanelUserPhotoService panelUserPhotoService;

    @Test
    void unblockRequestsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        when(unblockRequestService.loadRequests(null)).thenReturn(List.of());
        when(permissionService.hasAuthority(any(), any())).thenReturn(false);
        when(panelUserRepository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.empty());
        when(panelUserPhotoService.resolveUrl(any(), any())).thenReturn("/avatar_default.svg");

        mockMvc.perform(get("/unblock-requests").with(user("operator").authorities(() -> "PAGE_CLIENTS")))
                .andExpect(status().isOk())
                .andExpect(view().name("clients/unblock_requests"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"clients\"")));
    }
}
