package com.example.panel.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogListReadService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogListController.class)
@AutoConfigureMockMvc
class DialogListControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogListReadService dialogListReadService;

    @Test
    void listDelegatesOperatorIdentity() throws Exception {
        when(dialogListReadService.loadListPayload("operator"))
            .thenReturn(Map.of(
                    "success", true,
                    "dialogs", List.of(Map.of("ticketId", "T-900")),
                    "summary", Map.of("open", 1)
            ));

        mockMvc.perform(get("/api/dialogs").with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-900"))
            .andExpect(jsonPath("$.summary.open").value(1));
    }
}
