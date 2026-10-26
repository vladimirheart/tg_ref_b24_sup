package com.example.panel.controller;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogListReadService;
import com.example.panel.model.dialog.DialogListQuery;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(DialogListController.class)
@AutoConfigureMockMvc
@Import(DialogListControllerWebMvcTest.MethodSecurity.class)
class DialogListControllerWebMvcTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogListReadService dialogListReadService;

    @Test
    void readOnlyQueryPreservesOpaqueSnoozedIdsAndBoundsPageSize() throws Exception {
        var query = new DialogListQuery(3, 100, "заказ", "", "active", 30, "sla_priority", List.of(" T/1,?& Ю "));
        when(dialogListReadService.loadListPayload("operator", query, false)).thenReturn(Map.of(
                "success", true, "dialogs", List.of(), "pagination", Map.of("page", 3, "page_size", 100, "total_items", 203)));
        mockMvc.perform(post("/api/dialogs/query").with(user("operator").authorities(() -> "PAGE_DIALOGS")).with(csrf())
                .contentType("application/json").content("""
                    {"query":{"page":3,"pageSize":999999,"search":"ЗАКАЗ","view":"active",
                        "slaWindowMinutes":30,"sortMode":"sla_priority","snoozedTicketIds":[" T/1,?& Ю "]},"includeOverview":false}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.page_size").value(100));
        verify(dialogListReadService).loadListPayload("operator", query, false);
    }

    @Test
    void queryRequiresPagePermissionAndCsrf() throws Exception {
        mockMvc.perform(post("/api/dialogs/query").with(user("operator")).with(csrf())
                .contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dialogs/query").with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                .contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        verifyNoInteractions(dialogListReadService);
    }

    @Test
    void invalidNumericParametersDoNotReachService() throws Exception {
        mockMvc.perform(get("/api/dialogs").param("page_size", "all").with(user("operator")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dialogs").param("page", "99999999999999999999").with(user("operator")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dialogListReadService);
    }

    @Test
    void listDelegatesOperatorIdentity() throws Exception {
        when(dialogListReadService.loadListPayload(eq("operator"), any(), eq(true)))
            .thenReturn(Map.of(
                    "success", true,
                    "dialogs", List.of(Map.of("ticketId", "T-900")),
                    "summary", Map.of("open", 1),
                    "my_dialogs", Map.of(
                            "unanswered", List.of(Map.of("ticketId", "T-900")),
                            "in_work", List.of()
                    )
            ));

        mockMvc.perform(get("/api/dialogs").with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-900"))
            .andExpect(jsonPath("$.summary.open").value(1))
            .andExpect(jsonPath("$.my_dialogs.unanswered[0].ticketId").value("T-900"));
    }

    @Test
    void listSupportsEmptyDialogsPayload() throws Exception {
        when(dialogListReadService.loadListPayload(eq("operator"), any(), eq(true)))
            .thenReturn(Map.of(
                    "success", true,
                    "dialogs", List.of(),
                    "summary", Map.of("open", 0)
            ));

        mockMvc.perform(get("/api/dialogs").with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.summary.open").value(0));
    }
}
