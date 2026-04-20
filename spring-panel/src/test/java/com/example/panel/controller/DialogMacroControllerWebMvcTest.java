package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogMacroService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogMacroController.class)
@AutoConfigureMockMvc
class DialogMacroControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogMacroService dialogMacroService;

    @Test
    void dryRunRequiresTemplateText() throws Exception {
        mockMvc.perform(post("/api/dialogs/macro/dry-run")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("template_text is required"));
    }

    @Test
    void dryRunReturnsRenderedPayload() throws Exception {
        when(dialogMacroService.dryRun(
                eq("T-500"),
                eq("Здравствуйте, {{client_name}}"),
                eq("operator"),
                eq(Map.of("client_name", "Иван"))))
            .thenReturn(new DialogMacroService.MacroDryRunResponse(
                    "Здравствуйте, Иван",
                    List.of("client_name"),
                    List.of()
            ));

        mockMvc.perform(post("/api/dialogs/macro/dry-run")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ticket_id": "T-500",
                      "template_text": "Здравствуйте, {{client_name}}",
                      "variables": {
                        "client_name": "Иван"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.rendered_text").value("Здравствуйте, Иван"))
            .andExpect(jsonPath("$.used_variables[0]").value("client_name"));
    }

    @Test
    void macroVariablesReturnsServicePayload() throws Exception {
        when(dialogMacroService.loadVariables("T-501", "operator"))
            .thenReturn(List.of(Map.of("key", "client_name", "label", "Имя клиента")));

        mockMvc.perform(get("/api/dialogs/macro/variables")
                .param("ticketId", "T-501")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.variables[0].key").value("client_name"));
    }
}
