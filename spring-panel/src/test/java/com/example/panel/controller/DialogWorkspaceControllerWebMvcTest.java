package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogWorkspaceService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogWorkspaceController.class)
@AutoConfigureMockMvc
class DialogWorkspaceControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogWorkspaceService dialogWorkspaceService;

    @Test
    void workspaceDelegatesRequestEnvelopeToService() throws Exception {
        doReturn(ResponseEntity.ok(Map.of(
                "success", true,
                "ticketId", "T-300",
                "workspace", Map.of("permissions", Map.of("can_reply", true))
        )))
            .when(dialogWorkspaceService)
            .workspace(
                eq("T-300"),
                eq(44L),
                eq("messages,sla"),
                eq(25),
                eq("cursor-1"),
                any());

        mockMvc.perform(get("/api/dialogs/T-300/workspace")
                .param("channelId", "44")
                .param("include", "messages,sla")
                .param("limit", "25")
                .param("cursor", "cursor-1")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.ticketId").value("T-300"))
            .andExpect(jsonPath("$.workspace.permissions.can_reply").value(true));
    }
}
