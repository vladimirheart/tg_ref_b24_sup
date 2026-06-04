package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.model.dialog.DialogParticipantDto;
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
            .thenReturn(new DialogReplyService.DialogReplyResult(true, null, "2026-04-20T08:10:00Z", 456L, "operator"));

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

        verify(dialogAuthorizationService).logDialogAction("operator", "T-600", "reply", "success", "message_sent");
    }

    @Test
    void replyReturnsBadRequestWhenServiceReturnsDomainError() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("reply"), eq("T-600ERR")))
            .thenReturn(null);
        when(dialogQuickActionService.sendReply("T-600ERR", "Не отправилось", null, "operator"))
            .thenReturn(new DialogReplyService.DialogReplyResult(false, "transport_error", null, null, null));

        mockMvc.perform(post("/api/dialogs/T-600ERR/reply")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "Не отправилось"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("transport_error"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-600ERR", "reply", "error", "transport_error");
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
        when(dialogQuickActionService.updateCategories("T-604", "operator", List.of()))
            .thenReturn(new DialogQuickActionService.DialogCategoryUpdateResult(true, List.of()));

        mockMvc.perform(post("/api/dialogs/T-604/categories")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.categories").isArray());

        verify(dialogQuickActionService).updateCategories("T-604", "operator", List.of());
        verify(dialogAuthorizationService).logDialogAction("operator", "T-604", "categories", "success", "categories_cleared");
    }

    @Test
    void categoriesReturnsNotFoundWhenDialogMissing() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("categories"), eq("T-604NF")))
            .thenReturn(null);
        when(dialogQuickActionService.updateCategories("T-604NF", "operator", List.of("billing")))
            .thenReturn(new DialogQuickActionService.DialogCategoryUpdateResult(false, List.of()));

        mockMvc.perform(post("/api/dialogs/T-604NF/categories")
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

        verify(dialogAuthorizationService).logDialogAction("operator", "T-604NF", "categories", "not_found", "Диалог не найден");
    }

    @Test
    void spamReturnsBadRequestWhenClientCannotBeResolved() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("mark_spam"), eq("T-604A")))
            .thenReturn(null);
        when(dialogQuickActionService.markClientAsSpam("T-604A", "operator", "Спам в диалоге"))
            .thenReturn(new DialogQuickActionService.DialogSpamResult(true, false, "Не удалось определить клиента для блокировки", null, List.of()));

        mockMvc.perform(post("/api/dialogs/T-604A/spam")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "Спам в диалоге"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Не удалось определить клиента для блокировки"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-604A", "mark_spam", "error", "Не удалось определить клиента для блокировки");
    }

    @Test
    void spamReturnsCategoriesAndUserIdOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("mark_spam"), eq("T-604B")))
            .thenReturn(null);
        when(dialogQuickActionService.markClientAsSpam("T-604B", "operator", "Спам"))
            .thenReturn(new DialogQuickActionService.DialogSpamResult(true, true, null, "77", List.of("billing", "Спам")));

        mockMvc.perform(post("/api/dialogs/T-604B/spam")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "Спам"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.updated").value(true))
            .andExpect(jsonPath("$.userId").value("77"))
            .andExpect(jsonPath("$.categories[0]").value("billing"))
            .andExpect(jsonPath("$.categories[1]").value("Спам"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-604B", "mark_spam", "success", "blacklisted_user=77");
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
    void snoozeReturnsSuccessForValidDuration() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_snooze"), eq("snooze"), eq("T-605OK")))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/T-605OK/snooze")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "minutes": 15
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-605OK", "snooze", "success", "minutes=15");
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

        verify(dialogAuthorizationService).logDialogAction("operator", "T-606", "reply_media", "error", "file_too_large");
    }

    @Test
    void mediaReplyReturnsPayloadAndAuditOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("reply_media"), eq("T-606OK")))
            .thenReturn(null);
        when(dialogQuickActionService.sendMediaReply(eq("T-606OK"), org.mockito.ArgumentMatchers.any(), eq("caption"), eq("operator"), org.mockito.ArgumentMatchers.any()))
            .thenReturn(Map.of(
                    "success", true,
                    "timestamp", "2026-05-21T18:09:00Z",
                    "telegramMessageId", 812L,
                    "responsible", "operator",
                    "attachment", "/api/attachments/tickets/T-606OK/reply.png",
                    "messageType", "image",
                    "message", "caption"
            ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/dialogs/T-606OK/media")
                .file("file", "hello".getBytes())
                .param("message", "caption")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.timestamp").value("2026-05-21T18:09:00Z"))
            .andExpect(jsonPath("$.responsible").value("operator"))
            .andExpect(jsonPath("$.messageType").value("image"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-606OK", "reply_media", "success", "media_sent");
    }

    @Test
    void editReturnsTimestampOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("edit"), eq("T-607")))
            .thenReturn(null);
        when(dialogQuickActionService.editReply("T-607", 701L, "Уточнили ответ", "operator"))
            .thenReturn(new DialogReplyService.DialogReplyResult(true, null, "2026-05-21T18:10:00Z", 701L, "operator"));

        mockMvc.perform(post("/api/dialogs/T-607/edit")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "telegramMessageId": 701,
                      "message": "Уточнили ответ"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.timestamp").value("2026-05-21T18:10:00Z"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-607", "edit", "success", "message_edited");
    }

    @Test
    void editReturnsBadRequestWhenServiceReturnsError() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("edit"), eq("T-607ERR")))
            .thenReturn(null);
        when(dialogQuickActionService.editReply("T-607ERR", 701L, "Не обновилось", "operator"))
            .thenReturn(new DialogReplyService.DialogReplyResult(false, "message_not_found", null, null, null));

        mockMvc.perform(post("/api/dialogs/T-607ERR/edit")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "telegramMessageId": 701,
                      "message": "Не обновилось"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("message_not_found"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-607ERR", "edit", "error", "message_not_found");
    }

    @Test
    void deleteReturnsBadRequestWhenServiceReturnsError() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("delete"), eq("T-608")))
            .thenReturn(null);
        when(dialogQuickActionService.deleteReply("T-608", 702L, "operator"))
            .thenReturn(new DialogReplyService.DialogReplyResult(false, "message_not_found", null, null, null));

        mockMvc.perform(post("/api/dialogs/T-608/delete")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "telegramMessageId": 702
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("message_not_found"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-608", "delete", "error", "message_not_found");
    }

    @Test
    void deleteReturnsTimestampOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_reply"), eq("delete"), eq("T-608OK")))
            .thenReturn(null);
        when(dialogQuickActionService.deleteReply("T-608OK", 702L, "operator"))
            .thenReturn(new DialogReplyService.DialogReplyResult(true, null, "2026-05-21T18:11:00Z", 702L, "operator"));

        mockMvc.perform(post("/api/dialogs/T-608OK/delete")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "telegramMessageId": 702
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.timestamp").value("2026-05-21T18:11:00Z"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-608OK", "delete", "success", "message_deleted");
    }

    @Test
    void reopenReturnsUpdatedOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("reopen"), eq("T-609")))
            .thenReturn(null);
        when(dialogQuickActionService.reopenTicket("T-609", "operator"))
            .thenReturn(new DialogResolveResult(true, true, null));

        mockMvc.perform(post("/api/dialogs/T-609/reopen")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.updated").value(true));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-609", "reopen", "success", "updated");
    }

    @Test
    void reopenReturnsNotFoundWhenDialogMissing() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("reopen"), eq("T-609NF")))
            .thenReturn(null);
        when(dialogQuickActionService.reopenTicket("T-609NF", "operator"))
            .thenReturn(new DialogResolveResult(false, false, null));

        mockMvc.perform(post("/api/dialogs/T-609NF/reopen")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Диалог не найден"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-609NF", "reopen", "not_found", "Диалог не найден");
    }

    @Test
    void reopenReturnsBadRequestWhenServiceReturnsDomainError() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_close"), eq("reopen"), eq("T-609ERR")))
            .thenReturn(null);
        when(dialogQuickActionService.reopenTicket("T-609ERR", "operator"))
            .thenReturn(new DialogResolveResult(false, true, "not_closed"));

        mockMvc.perform(post("/api/dialogs/T-609ERR/reopen")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("not_closed"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-609ERR", "reopen", "error", "not_closed");
    }

    @Test
    void addParticipantReturnsChangedParticipantsOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_assign"), eq("participants_add"), eq("T-610")))
            .thenReturn(null);
        when(dialogQuickActionService.addParticipant("T-610", "watcher_peer", "operator"))
            .thenReturn(new DialogQuickActionService.DialogParticipantMutationResult(
                    true,
                    true,
                    null,
                    List.of(new DialogParticipantDto("watcher_peer", "Watcher Peer", null, "ops", "operator", "2026-05-21T18:11:00Z", "operator"))
            ));

        mockMvc.perform(post("/api/dialogs/T-610/participants")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "watcher_peer"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.participants[0].username").value("watcher_peer"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-610", "participants_add", "success", "participant_added");
    }

    @Test
    void removeParticipantReturnsNotFoundWhenDialogMissing() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_assign"), eq("participants_remove"), eq("T-611")))
            .thenReturn(null);
        when(dialogQuickActionService.removeParticipant("T-611", "watcher_peer", "operator"))
            .thenReturn(new DialogQuickActionService.DialogParticipantMutationResult(false, false, null, List.of()));

        mockMvc.perform(delete("/api/dialogs/T-611/participants/watcher_peer")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Диалог не найден"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-611", "participants_remove", "not_found", "Диалог не найден");
    }

    @Test
    void reassignReturnsResponsibleProjectionOnSuccess() throws Exception {
        when(dialogAuthorizationService.requirePermission(org.mockito.ArgumentMatchers.any(), eq("can_assign"), eq("reassign"), eq("T-612")))
            .thenReturn(null);
        when(dialogQuickActionService.reassignTicket("T-612", "watcher_peer", "operator"))
            .thenReturn(new DialogQuickActionService.DialogReassignResult(
                    true,
                    null,
                    "watcher_peer",
                    "Watcher Peer",
                    "/avatars/watcher_peer.png",
                    List.of(new DialogParticipantDto("watcher_peer", "Watcher Peer", null, "ops", "operator", "2026-05-21T18:12:00Z", "operator"))
            ));

        mockMvc.perform(post("/api/dialogs/T-612/reassign")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "watcher_peer"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.responsible").value("watcher_peer"))
            .andExpect(jsonPath("$.displayResponsible").value("Watcher Peer"))
            .andExpect(jsonPath("$.avatarUrl").value("/avatars/watcher_peer.png"))
            .andExpect(jsonPath("$.participants[0].username").value("watcher_peer"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-612", "reassign", "success", "responsible_redirected");
    }
}
