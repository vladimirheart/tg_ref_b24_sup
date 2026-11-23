package com.example.panel.controller;

import com.example.panel.passports.ObjectPassportService;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({KnowledgeNoteController.class,AttachmentController.class})
@AutoConfigureMockMvc
class KnowledgeNotePhotoWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean KnowledgeNoteService notes;
    @MockBean KnowledgeNoteLocationService locations;
    @MockBean KnowledgeBaseService articles;
    @MockBean ObjectPassportService passports;
    @MockBean NavigationService navigation;
    @MockBean PermissionService permissions;
    @MockBean NotificationRoutingService notifications;
    @MockBean AttachmentService attachments;
    @MockBean KnowledgeNotePhotoService photos;
    @MockBean KnowledgeAccessService access;

    @Test void notesAndEditorRenderPhotoControlsAndFreshScript() throws Exception {
        when(notes.listNotes()).thenReturn(List.of());
        when(articles.listArticles(any())).thenReturn(List.of());
        mvc.perform(get("/knowledge-base/notes").with(user("reader").authorities(() -> "PAGE_KNOWLEDGE_BASE")))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("knowledge-notes.js?v=20261123-note-photo-access")));
        String html=mvc.perform(get("/knowledge-base/notes/new").with(user("reader").authorities(() -> "PAGE_KNOWLEDGE_BASE")))
            .andExpect(status().isOk()).andExpect(view().name("knowledge/note-editor"))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("data-note-photo-input","data-note-body-preview","knowledge-notes.js?v=20261123-note-photo-access");
        Path capture=Path.of("target/298-rendered-note-editor.html");
        Files.createDirectories(capture.getParent()); Files.writeString(capture,html);
    }

    @Test void photoUploadRequiresCsrfAndUsesTheNotePhotoService() throws Exception {
        var file=new MockMultipartFile("file","photo.jpg","image/jpeg",new byte[]{1,2,3});
        mvc.perform(multipart("/api/attachments/knowledge-base/note-photos").file(file)
            .with(user("reader").authorities(() -> "PAGE_KNOWLEDGE_BASE"))).andExpect(status().isForbidden());
        verifyNoInteractions(photos);
        when(photos.upload(any(),any())).thenReturn(new AttachmentService.AttachmentUploadMetadata("photo.jpg","stored.jpg","image/jpeg",3,OffsetDateTime.now()));
        mvc.perform(multipart("/api/attachments/knowledge-base/note-photos").file(file).with(csrf())
            .with(user("reader").authorities(() -> "PAGE_KNOWLEDGE_BASE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.storedName").value("stored.jpg"));
        verify(photos).upload(any(),any()); verifyNoInteractions(attachments);
    }
}
