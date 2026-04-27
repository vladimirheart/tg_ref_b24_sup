package com.example.panel.controller;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogReadService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogReadController.class)
@AutoConfigureMockMvc
class DialogReadControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogReadService dialogReadService;

    @Test
    void detailsDelegatesToReadServiceWithOperatorIdentity() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("ticketId", "T-100", "success", true)))
            .when(dialogReadService)
            .loadDetails("T-100", 77L, "operator");

        mockMvc.perform(get("/api/dialogs/T-100")
                .param("channelId", "77")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketId").value("T-100"))
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void historyReturnsMessagesPayload() throws Exception {
        when(dialogReadService.loadHistory("T-101", null, "operator"))
            .thenReturn(Map.of("success", true, "messages", List.of(Map.of("id", 1, "text", "hello"))));

        mockMvc.perform(get("/api/dialogs/T-101/history").with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.messages[0].text").value("hello"));
    }

    @Test
    void previousHistorySupportsOffsetParameter() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("success", true, "has_more", false, "next_offset", 20)))
            .when(dialogReadService)
            .loadPreviousHistory("T-102", 20);

        mockMvc.perform(get("/api/dialogs/T-102/history/previous")
                .param("offset", "20")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.next_offset").value(20));
    }

    @Test
    void publicFormMetricsDelegatesOptionalChannelId() throws Exception {
        when(dialogReadService.loadPublicFormMetrics(91L))
            .thenReturn(Map.of("success", true, "channelId", 91, "submitted", 4));

        mockMvc.perform(get("/api/dialogs/public-form-metrics")
                .param("channelId", "91")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.channelId").value(91))
            .andExpect(jsonPath("$.submitted").value(4));
    }

    @Test
    void detailsDelegatesWithoutChannelId() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("ticketId", "T-103", "success", true)))
            .when(dialogReadService)
            .loadDetails("T-103", null, "operator");

        mockMvc.perform(get("/api/dialogs/T-103").with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketId").value("T-103"))
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void previousHistoryDefaultsOffsetToZero() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("success", true, "next_offset", 0)))
            .when(dialogReadService)
            .loadPreviousHistory("T-104", 0);

        mockMvc.perform(get("/api/dialogs/T-104/history/previous")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.next_offset").value(0));

        verify(dialogReadService).loadPreviousHistory("T-104", 0);
    }
}
