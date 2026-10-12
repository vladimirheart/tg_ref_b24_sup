package com.example.panel.controller;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

@WebMvcTest(DialogTicketQueryReadController.class)
@AutoConfigureMockMvc
class DialogTicketQueryReadControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogReadService dialogReadService;

    @Test
    void detailsKeepsExactOpaqueTicketIdChannelAndOperator() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        doReturn(ResponseEntity.ok(Map.of("ticketId", ticketId, "success", true)))
                .when(dialogReadService).loadDetails(ticketId, 81L, "operator");

        mockMvc.perform(get("/api/dialog-ticket")
                        .param("ticketId", ticketId)
                        .param("channelId", "81")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId));

        verify(dialogReadService).loadDetails(ticketId, 81L, "operator");
    }

    @Test
    void detailsPreservesNotFoundResponseWithoutChannel() throws Exception {
        doReturn(ResponseEntity.status(404).body(Map.of("success", false, "error", "Диалог не найден")))
                .when(dialogReadService).loadDetails(" null ", null, "operator");

        mockMvc.perform(get("/api/dialog-ticket")
                        .param("ticketId", " null ")
                        .with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        verify(dialogReadService).loadDetails(" null ", null, "operator");
    }

    @Test
    void detailsRequiresTicketId() throws Exception {
        mockMvc.perform(get("/api/dialog-ticket").with(user("operator")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dialogReadService);
    }

    @Test
    void historyKeepsExactOpaqueTicketIdChannelAndOperator() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        doReturn(Map.of("success", true, "messages", List.of(Map.of("text", "hello"))))
                .when(dialogReadService).loadHistory(ticketId, 81L, "operator");

        mockMvc.perform(get("/api/dialog-ticket/history")
                        .param("ticketId", ticketId)
                        .param("channelId", "81")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].text").value("hello"));

        verify(dialogReadService).loadHistory(ticketId, 81L, "operator");
    }

    @Test
    void historyDoesNotAliasLiteralNullPaddedAndPlainIdentity() throws Exception {
        doReturn(Map.of("success", true, "messages", List.of()))
                .when(dialogReadService).loadHistory(" null ", null, "operator");
        doReturn(Map.of("success", true, "messages", List.of()))
                .when(dialogReadService).loadHistory("null", null, "operator");

        mockMvc.perform(get("/api/dialog-ticket/history")
                        .param("ticketId", " null ")
                        .with(user("operator")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dialog-ticket/history")
                        .param("ticketId", "null")
                        .with(user("operator")))
                .andExpect(status().isOk());

        verify(dialogReadService).loadHistory(" null ", null, "operator");
        verify(dialogReadService).loadHistory("null", null, "operator");
    }

    @Test
    void historyRejectsMissingTicketIdWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/dialog-ticket/history")
                        .with(user("operator")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dialogReadService);
    }

    @Test
    void historyDoesNotInventChannelWhenAbsent() throws Exception {
        doReturn(Map.of("success", true, "messages", List.of()))
                .when(dialogReadService).loadHistory(" T-100 ", null, "operator");

        mockMvc.perform(get("/api/dialog-ticket/history")
                        .param("ticketId", " T-100 ")
                        .with(user("operator")))
                .andExpect(status().isOk());

        verify(dialogReadService).loadHistory(" T-100 ", null, "operator");
    }

    @Test
    void previousHistoryKeepsExactTicketIdAndOffset() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        doReturn(ResponseEntity.ok(Map.of("success", true, "has_more", false, "next_offset", 20)))
                .when(dialogReadService).loadPreviousHistory(ticketId, 20);

        mockMvc.perform(get("/api/dialog-ticket/history/previous")
                        .param("ticketId", ticketId)
                        .param("offset", "20")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_offset").value(20));

        verify(dialogReadService).loadPreviousHistory(ticketId, 20);
    }

    @Test
    void previousHistoryDefaultsOffsetToZeroWithoutTrimmingTicketId() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("success", true, "next_offset", 0)))
                .when(dialogReadService).loadPreviousHistory(" null ", 0);

        mockMvc.perform(get("/api/dialog-ticket/history/previous")
                        .param("ticketId", " null ")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_offset").value(0));

        verify(dialogReadService).loadPreviousHistory(" null ", 0);
    }

    @Test
    void previousHistoryRejectsMissingTicketIdWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/dialog-ticket/history/previous").with(user("operator")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dialogReadService);
    }

    @Test
    void participantsPreservesExactTicketId() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        doReturn(Map.of("success", true, "participants", List.of(Map.of("username", "peer"))))
                .when(dialogReadService).loadParticipants(ticketId);

        mockMvc.perform(get("/api/dialog-ticket/participants")
                        .param("ticketId", ticketId)
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].username").value("peer"));
        verify(dialogReadService).loadParticipants(ticketId);
    }

    @Test
    void participantsRequiresTicketId() throws Exception {
        mockMvc.perform(get("/api/dialog-ticket/participants").with(user("operator")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dialogReadService);
    }
}
