package com.example.panel.controller;

import com.example.panel.service.UiPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileApiController.class)
@AutoConfigureMockMvc
class ProfileApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    @Qualifier("usersJdbcTemplate")
    private JdbcTemplate usersJdbcTemplate;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UiPreferenceService uiPreferenceService;

    @Test
    void loadUiPreferencesReturnsServerBackedPayload() throws Exception {
        when(uiPreferenceService.loadForUser("operator"))
                .thenReturn(Map.of("theme", "dark", "themePalette", "catppuccin"));

        mockMvc.perform(get("/profile/ui-preferences")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.preferences.theme").value("dark"))
                .andExpect(jsonPath("$.preferences.themePalette").value("catppuccin"));
    }

    @Test
    void updateUiPreferencesPersistsNormalizedPayload() throws Exception {
        when(uiPreferenceService.saveForUser(eq("operator"), anyMap()))
                .thenReturn(Map.of(
                        "theme", "dark",
                        "themePalette", "amber-minimal",
                        "sidebarNavOrder", List.of("dialogs", "settings")
                ));

        mockMvc.perform(put("/profile/ui-preferences")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "theme": "dark",
                                  "themePalette": "amber-minimal",
                                  "sidebarNavOrder": ["dialogs", "settings"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.preferences.themePalette").value("amber-minimal"))
                .andExpect(jsonPath("$.preferences.sidebarNavOrder[0]").value("dialogs"));
    }
}
