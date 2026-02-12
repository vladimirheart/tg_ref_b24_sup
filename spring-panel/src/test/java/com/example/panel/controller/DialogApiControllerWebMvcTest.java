package com.example.panel.controller;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogService;
import com.example.panel.storage.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DialogApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class DialogApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogService dialogService;

    @MockBean
    private DialogReplyService dialogReplyService;

    @MockBean
    private DialogNotificationService dialogNotificationService;

    @MockBean
    private AttachmentService attachmentService;

    @Test
    void snoozeRejectsInvalidDuration() throws Exception {
        mockMvc.perform(post("/api/dialogs/T-1/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void snoozeAcceptsValidDuration() throws Exception {
        mockMvc.perform(post("/api/dialogs/T-1/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void takeReturnsNotFoundForUnknownTicket() throws Exception {
        when(dialogService.findDialog(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/dialogs/T-404/take").with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void takeAssignsDialogWhenTicketExists() throws Exception {
        when(dialogService.findDialog("T-1", "operator"))
                .thenReturn(Optional.of(new DialogListItem(
                        "T-1",
                        1L,
                        42L,
                        "user",
                        "Клиент",
                        "biz",
                        1L,
                        "demo",
                        "city",
                        "location",
                        "problem",
                        "2024-01-01T10:00:00Z",
                        "pending",
                        null,
                        null,
                        "operator",
                        "2024-01-01",
                        "10:00",
                        "label",
                        "user",
                        "2024-01-01T10:00:00Z",
                        0,
                        null,
                        "category"
                )));

        mockMvc.perform(post("/api/dialogs/T-1/take").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responsible").value("operator"));
    }
}
