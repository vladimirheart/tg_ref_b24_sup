package com.example.panel.controller;

import com.example.panel.entity.Task;
import com.example.panel.entity.TaskComment;
import com.example.panel.repository.*;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TaskApiController.class, TaskAttachmentController.class})
@Import(TaskAttachmentsWebMvcTest.MethodSecurity.class)
class TaskAttachmentsWebMvcTest {
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean TaskRepository tasks; @MockBean TaskSequenceRepository sequences;
    @MockBean TaskCommentRepository comments; @MockBean TaskHistoryRepository history; @MockBean TaskPersonRepository people;
    @MockBean NotificationRoutingService notifications; @MockBean IncidentService incidents;
    @MockBean TaskDomainFoundationService domain; @MockBean TaskQueryService query;
    @MockBean DialogTaskDisplayService display; @MockBean SharedWorkAccessService access;
    @MockBean TaskAttachmentService attachments;
    private Task task;
    private final String storedName = "12345678-1234-1234-1234-123456789abc_plan.txt";
    @BeforeEach void prepare() {
        task = new Task(); task.setId(41L); task.setTitle("Задача"); task.setCreator("anna"); task.setBodyHtml("<p>Описание</p>");
        when(tasks.findById(41L)).thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(any())).thenAnswer(call -> { Task saved = call.getArgument(0); if (saved.getId() == null) saved.setId(41L); return saved; });
        when(comments.save(any())).thenAnswer(call -> { TaskComment saved = call.getArgument(0); saved.setId(9L); return saved; });
        when(access.requireCreation(any(), any())).thenReturn(1L);
        when(attachments.appendFiles(eq(41L), anyString(), anyList())).thenAnswer(call -> call.<String>getArgument(1) + "<p><a data-task-attachment=\"true\" href=\"/api/tasks/41/attachments/" + storedName + "\">plan.txt</a></p>");
    }
    private MockMultipartFile file() { return new MockMultipartFile("files", "plan.txt", "text/plain", new byte[]{1,2,3}); }
    @Test void newTaskPersistsFilesOnlyAfterItHasAnId() throws Exception {
        mvc.perform(multipart("/api/tasks").file(file()).param("title", "Новая задача").param("body_html", "<p>Описание</p>")
            .param("assignee", "anna").with(user("anna").authorities(() -> "PAGE_TASKS")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(41)).andExpect(jsonPath("$.body_html").value(org.hamcrest.Matchers.containsString("plan.txt")));
        verify(access).requireCreation(eq(SharedWorkAccessService.Kind.TASK), any());
        verify(attachments).appendFiles(eq(41L), eq("<p>Описание</p>"), argThat(files -> files.size() == 1));
    }
    @Test void filesCanBeTheOnlyContentOfAComment() throws Exception {
        mvc.perform(multipart("/api/tasks/41/comments").file(file()).param("html", "")
            .with(user("anna").authorities(() -> "PAGE_TASKS")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.html").value(org.hamcrest.Matchers.containsString("plan.txt")));
        verify(access).require(eq(SharedWorkAccessService.Kind.TASK), eq(41L), eq(SharedWorkAccessService.Access.WRITE), any());
        assertThat(task.getBodyHtml()).isEqualTo("<p>Описание</p>");
    }
    @Test void writeDenialAndCsrfFailureDoNotUploadAnything() throws Exception {
        mvc.perform(multipart("/api/tasks/41/comments").file(file()).param("html", "test").with(user("anna").authorities(() -> "PAGE_TASKS")))
            .andExpect(status().isForbidden());
        doThrow(new AccessDeniedException("read only")).when(access).require(eq(SharedWorkAccessService.Kind.TASK), eq(41L), eq(SharedWorkAccessService.Access.WRITE), any());
        mvc.perform(multipart("/api/tasks/41/comments").file(file()).param("html", "test").with(user("anna").authorities(() -> "PAGE_TASKS")).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(attachments);
    }
    @Test void downloadChecksCurrentTaskReadAccessAndReturnsActualBytes() throws Exception {
        when(attachments.open(41L, storedName)).thenReturn(new AttachmentObjectStorageService.StoredBinary("41/" + storedName, "application/octet-stream", 3, new ByteArrayInputStream(new byte[]{1,2,3})));
        mvc.perform(get("/api/tasks/41/attachments/" + storedName).with(user("anna").authorities(() -> "PAGE_TASKS")))
            .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3})).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        verify(access).require(eq(SharedWorkAccessService.Kind.TASK), eq(41L), eq(SharedWorkAccessService.Access.READ), any());
    }
    @Test void revokedReadAndMissingPageCannotOpenBinary() throws Exception {
        mvc.perform(get("/api/tasks/41/attachments/" + storedName).with(user("anna"))).andExpect(status().isForbidden());
        doThrow(new AccessDeniedException("revoked")).when(access).require(eq(SharedWorkAccessService.Kind.TASK), eq(41L), eq(SharedWorkAccessService.Access.READ), any());
        mvc.perform(get("/api/tasks/41/attachments/" + storedName).with(user("anna").authorities(() -> "PAGE_TASKS"))).andExpect(status().isForbidden());
        verifyNoInteractions(attachments);
    }
    @Test void htmlCannotBeOpenedInlineEvenWithPreviewFlag() throws Exception {
        when(attachments.open(41L, storedName)).thenReturn(new AttachmentObjectStorageService.StoredBinary("41/" + storedName, "text/html", 1, new ByteArrayInputStream(new byte[]{1})));
        mvc.perform(get("/api/tasks/41/attachments/" + storedName).param("preview", "true").with(user("anna").authorities(() -> "PAGE_TASKS")))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/octet-stream"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }
}
