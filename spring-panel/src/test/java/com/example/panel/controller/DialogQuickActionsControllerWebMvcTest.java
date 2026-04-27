package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogAuthorizationService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogResolveResult;
import com.example.panel.service.DialogReplyService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogQuickActionsController.class)
@AutoConfigureMockMvc
class DialogQuickActionsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogQuickActionService dialogQuickActionService;

    @MockBean
    private DialogAuthorizationService dialogAuthorizationService;

    @Test
    void replyReturnsTimestampAndResponsibleOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("reply"), eq("T-600")))
            .thenReturn(null);
        when(dialogQuickActionService.sendReply("T-600", "Принято", 123L, "operator"))
            .thenReturn(new DialogReplyService.DialogReplyResult(true, null, "2026-04-20T08:10:00Z", 456L));

        mockMvc.perform(post("/api/dialogs/T-600/reply")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "Принято",
                      "replyToTelegramId": 123
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.timestamp").value("2026-04-20T08:10:00Z"))
            .andExpect(jsonPath("$.responsible").value("operator"))
            .andExpect(jsonPath("$.telegramMessageId").value(456));
    }

    @Test
    void resolveReturnsNotFoundWhenDialogDoesNotExist() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("quick_close"), eq("T-601")))
            .thenReturn(null);
        when(dialogQuickActionService.resolveTicket("T-601", "operator", List.of("billing")))
            .thenReturn(new DialogResolveResult(false, false, null));

        mockMvc.perform(post("/api/dialogs/T-601/resolve")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "categories": ["billing"]
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Диалог не найден"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-601", "quick_close", "not_found", "Диалог не найден");
    }

    @Test
    void takeReturnsForbiddenWhenPermissionDenied() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_assign"), eq("take"), eq("T-602")))
            .thenReturn(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "error", "Недостаточно прав для выполнения действия")));

        mockMvc.perform(post("/api/dialogs/T-602/take")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для выполнения действия"));
    }

    @Test
    void resolveReturnsBadRequestWhenServiceReturnsDomainError() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("quick_close"), eq("T-603")))
            .thenReturn(null);
        when(dialogQuickActionService.resolveTicket("T-603", "operator", null))
            .thenReturn(new DialogResolveResult(false, true, "already_closed"));

        mockMvc.perform(post("/api/dialogs/T-603/resolve")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("already_closed"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-603", "quick_close", "error", "already_closed");
    }

    @Test
    void categoriesAcceptsNullRequestBodyAndUsesEmptyList() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("categories"), eq("T-604")))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/T-604/categories")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(dialogQuickActionService).updateCategories("T-604", "operator", List.of());
    }

    @Test
    void snoozeRejectsMissingOrInvalidDuration() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_snooze"), eq("snooze"), eq("T-605")))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/T-605/snooze")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Некорректная длительность snooze"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-605", "snooze", "error", "Некорректная длительность snooze");
    }

    @Test
    void mediaReplyReturnsBadRequestWhenPayloadSignalsFailure() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("reply_media"), eq("T-606")))
            .thenReturn(null);
        when(dialogQuickActionService.sendMediaReply(eq("T-606"), org.mockito.ArgumentMatchers.any(), eq("caption"), eq("operator"), org.mockito.ArgumentMatchers.any()))
            .thenReturn(Map.of("success", false, "error", "file_too_large"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/dialogs/T-606/media")
                .file("file", "hello".getBytes())
                .param("message", "caption")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("file_too_large"));
    }
}
