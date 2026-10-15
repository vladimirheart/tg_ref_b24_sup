package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.NavigationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({BusinessAdministrationPageController.class, BusinessScopePageController.class})
@Import(BusinessPagesWebMvcTest.MethodSecurity.class)
class BusinessPagesWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NavigationService navigationService;

    @MockBean
    private MultibusinessFeatureFlags flags;

    @Test
    void pagesRenderOnlyInsideTheirFlagsAndGlobalPageAuthorities() throws Exception {
        mvc.perform(get("/business-dialogs")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(flags.isUiSelector()).thenReturn(true);
        mvc.perform(get("/business-dialogs")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(view().name("businesses/dialogs"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"businessScopeSelect\"")));
        mvc.perform(get("/business-dialogs")
                .with(user("wrong-page").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/businesses/manage")
                .with(user("tenant-admin").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/businesses/manage")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("businesses/manage"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"businessCreateForm\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"businessLocationForm\"")));
    }
}
