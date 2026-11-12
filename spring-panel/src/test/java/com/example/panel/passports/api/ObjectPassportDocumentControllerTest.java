package com.example.panel.passports.api;

import com.example.panel.passports.ObjectPassportDocumentService;
import com.example.panel.security.SecurityConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ObjectPassportDocumentController.class)
@Import(SecurityConfig.class)
class ObjectPassportDocumentControllerTest {
    static final UUID ID = UUID.fromString("df01765d-81f7-4d64-9e6b-fb48dcb80b5d");
    static final String BASE = "/api/object_passports/17/documents";
    @Autowired MockMvc mvc;
    @MockBean ObjectPassportDocumentService documents;
    @MockBean(name = "usersJdbcTemplate") JdbcTemplate users;

    @ParameterizedTest @ValueSource(strings = {"", "/" + "df01765d-81f7-4d64-9e6b-fb48dcb80b5d/revisions", "/" + "df01765d-81f7-4d64-9e6b-fb48dcb80b5d/revisions/1", "/" + "df01765d-81f7-4d64-9e6b-fb48dcb80b5d/revisions/1/file"})
    void pagePermissionProtectsListingHistoryAndFiles(String suffix) throws Exception {
        mvc.perform(get(BASE + suffix).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documents);
    }

    @Test void viewersCanListButCannotMutateDocuments() throws Exception {
        when(documents.list(eq(17L), eq(false), eq(""), isNull(), any()))
                .thenReturn(Map.of("success", true, "items", List.of(), "can_edit", false));
        mvc.perform(get(BASE).with(user("reader").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.can_edit").value(false))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        clearInvocations(documents);
        mvc.perform(multipart(BASE).param("metadata", "{}").with(csrf())
                .with(user("reader").authorities(() -> "PAGE_OBJECT_PASSPORTS"))).andExpect(status().isForbidden());
        mvc.perform(multipart(BASE + "/" + ID + "/revisions").param("metadata", "{}").with(csrf())
                .with(user("reader").authorities(() -> "PAGE_OBJECT_PASSPORTS"))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + ID + "/archive").contentType("application/json").content("{\"base_revision\":1,\"archived\":true}").with(csrf())
                .with(user("reader").authorities(() -> "PAGE_OBJECT_PASSPORTS"))).andExpect(status().isForbidden());
        verifyNoInteractions(documents);
    }

    @Test void editGrantRequiresThePageAndDoesNotBypassCsrfOrIndividualDenial() throws Exception {
        mvc.perform(multipart(BASE).param("metadata", "{}").with(csrf())
                .with(user("editor").authorities(() -> "EDIT_PASSPORT_DOCUMENTS"))).andExpect(status().isForbidden());
        mvc.perform(multipart(BASE).param("metadata", "{}")
                .with(user("editor").authorities(() -> "PAGE_OBJECT_PASSPORTS", () -> "EDIT_PASSPORT_DOCUMENTS")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(BASE).param("metadata", "{}").with(csrf())
                .with(user("editor").authorities(() -> "PAGE_OBJECT_PASSPORTS", () -> "EDIT_PASSPORT_DOCUMENTS", () -> "DENY_EDIT_PASSPORT_DOCUMENTS")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documents);
    }

    @Test void editsPassTheExplicitRevisionStructuredFieldsAndOptionalReplacementFile() throws Exception {
        when(documents.revise(eq(17L), eq(ID), any(), any(), eq("editor"))).thenReturn(Map.of("success", true));
        var file = new MockMultipartFile("file", "Акт.txt", "text/plain", "Касса настроена".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart(BASE + "/" + ID + "/revisions").file(file).param("metadata", """
            {"base_revision":3,"title":"Приёмка оборудования","category":"Акт","event_date":"2026-11-12",
             "description":"Проверено","fields":{"Серийный номер":"ККТ-001"}}
            """).with(csrf()).with(user("editor").authorities(() -> "PAGE_OBJECT_PASSPORTS", () -> "EDIT_PASSPORT_DOCUMENTS")))
                .andExpect(status().isOk());
        var captured = org.mockito.ArgumentCaptor.forClass(ObjectPassportDocumentService.Input.class);
        verify(documents).revise(eq(17L), eq(ID), captured.capture(), any(), eq("editor"));
        assertThat(captured.getValue().baseRevision()).isEqualTo(3);
        assertThat(captured.getValue().eventDate()).isEqualTo(java.time.LocalDate.of(2026,11,12));
        assertThat(captured.getValue().fields()).containsEntry("Серийный номер", "ККТ-001");
    }

    @Test void notesCanBeCreatedWithoutFilesAndDatesAreValidated() throws Exception {
        when(documents.create(eq(17L), any(), isNull(), eq("editor"))).thenReturn(Map.of("success", true));
        mvc.perform(multipart(BASE).param("metadata", "{\"title\":\"История настройки\"}").with(csrf())
                .with(user("editor").authorities(() -> "PAGE_OBJECT_PASSPORTS", () -> "EDIT_PASSPORT_DOCUMENTS")))
                .andExpect(status().isOk());
        mvc.perform(multipart(BASE).param("metadata", "{\"title\":\"Дата\",\"event_date\":\"2026-99-99\"}").with(csrf())
                .with(user("editor").authorities(() -> "PAGE_OBJECT_PASSPORTS", () -> "EDIT_PASSPORT_DOCUMENTS")))
                .andExpect(status().isBadRequest());
        verify(documents, times(1)).create(anyLong(), any(), any(), anyString());
    }

    @Test void pdfResponseKeepsItsSameOriginPreviewHeaderInsideTheActualSecurityChain() throws Exception {
        var body = new ByteArrayResource("%PDF-1.4".getBytes());
        when(documents.download(17L, ID, 2, true)).thenReturn(ResponseEntity.ok()
                .header("X-Frame-Options", "SAMEORIGIN").header("Content-Security-Policy", "frame-ancestors 'self'")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF).body((Resource) body));
        mvc.perform(get(BASE + "/" + ID + "/revisions/2/file?preview=true")
                .with(user("viewer").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
                .andExpect(status().isOk()).andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andExpect(header().stringValues("X-Frame-Options", "SAMEORIGIN"));
        when(documents.download(17L, ID, 99, true)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        mvc.perform(get(BASE + "/" + ID + "/revisions/99/file?preview=true")
                .with(user("viewer").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
                .andExpect(status().isNotFound()).andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
