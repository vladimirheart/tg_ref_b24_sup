package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.SettingsParameterService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettingsParametersController.class)
@AutoConfigureMockMvc
class SettingsParametersControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsParameterService settingsParameterService;

    @Test
    void listParametersReturnsGroupedPayload() throws Exception {
        when(settingsParameterService.listParameters(true))
            .thenReturn(Map.of("city", List.of(Map.of("id", 1, "value", "Москва"))));

        mockMvc.perform(get("/api/settings/parameters").with(user("admin").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.city[0].value").value("Москва"));
    }

    @Test
    void createParameterDelegatesPayloadParsing() throws Exception {
        when(settingsParameterService.createParameter(anyMap()))
            .thenReturn(Map.of("success", true, "data", Map.of("city", List.of())));

        mockMvc.perform(post("/api/settings/parameters")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "param_type": "city",
                      "value": "Сочи",
                      "state": "Активен"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateParameterSupportsPutRoute() throws Exception {
        when(settingsParameterService.updateParameter(15L, Map.of("value", "Казань")))
            .thenReturn(Map.of("success", true, "data", Map.of("city", List.of(Map.of("value", "Казань")))));

        mockMvc.perform(put("/api/settings/parameters/15")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    { "value": "Казань" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.city[0].value").value("Казань"));
    }

    @Test
    void deleteParameterReturnsServicePayload() throws Exception {
        when(settingsParameterService.deleteParameter(27L))
            .thenReturn(Map.of("success", true, "data", Map.of()));

        mockMvc.perform(delete("/api/settings/parameters/27")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createParameterSupportsTrailingSlashRoute() throws Exception {
        when(settingsParameterService.createParameter(anyMap()))
            .thenReturn(Map.of("success", true, "data", Map.of("city", List.of(Map.of("value", "Тула")))));

        mockMvc.perform(post("/api/settings/parameters/")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "param_type": "city",
                      "value": "Тула"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.city[0].value").value("Тула"));
    }

    @Test
    void updateParameterSupportsPatchRoute() throws Exception {
        when(settingsParameterService.updateParameter(eq(33L), anyMap()))
            .thenReturn(Map.of("success", true, "data", Map.of("city", List.of(Map.of("value", "Пермь")))));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/settings/parameters/33")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    { "value": "Пермь" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.city[0].value").value("Пермь"));
    }
}
