package com.example.panel.storage;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.service.DialogLookupReadService;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import com.example.panel.service.KnowledgeNoteLocationService;
import com.example.panel.service.KnowledgeNoteService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.PermissionService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Isolated Testcontainers PostgreSQL + temporary media root; never the working stack. */
class KnowledgeNotePhotoPersistencePostgresqlTest {
    @TempDir Path directory;

    @Test void photoAndNoteSurviveServiceRecreationWithBytesLinksLocationAndPermissionsIntact() throws Exception {
        var jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("note_photo_r10");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V45__knowledge_notes.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V46__knowledge_note_location_links.sql")));
        var permitted = new TestingAuthenticationToken("operator", "", "PAGE_KNOWLEDGE_BASE", "PAGE_DIALOGS");
        var denied = new TestingAuthenticationToken("reader", "", "PAGE_DIALOGS");
        var media = attachmentService();
        byte[] bytes = Files.readAllBytes(Path.of("src/main/resources/static/images/iguana-brand.png"));
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
        assertThatThrownBy(() -> media.storeKnowledgeBaseFile(denied, file)).isInstanceOf(SecurityException.class);
        var metadata = media.storeKnowledgeBaseFile(permitted, file);
        String body = "Текст заметки\r\n![Фото](/api/attachments/knowledge-base/" + metadata.storedName() + ")\r\n";
        assertThat(metadata.size()).isEqualTo(bytes.length);

        var lookup = mock(DialogLookupReadService.class);
        String ticket = " T/1?&%# Ю ";
        var dialog = new DialogListItem(ticket, "1", null, null, null, null, null,
                null, null, "Центральная", null, null, null, false, null, null,
                "operator", null, null, null, null, null, 0, null, null, null, null);
        when(lookup.findDialog(ticket, "operator")).thenReturn(Optional.of(dialog));
        var notes = new KnowledgeNoteService(jdbc, new ObjectMapper());
        var locationNotes = new KnowledgeNoteLocationService(jdbc,
                mock(IikoDepartmentLocationCatalogService.class), notes, lookup);
        long id = locationNotes.saveNote(null, "С фото", body, List.of(), List.of(),
                List.of(), List.of(), List.of("Центральная"), "operator");

        // Recreate readers and storage facade, not only an in-memory browser preview.
        var reloaded = new KnowledgeNoteService(jdbc, new ObjectMapper()).findNote(id).orElseThrow();
        assertThat(reloaded.body()).isEqualTo(body);
        var inDialog = locationNotes.listDialogNotes(ticket, "operator");
        assertThat(inDialog).singleElement().satisfies(note -> {
            assertThat(note.body()).isEqualTo(body);
            assertThat(note.locationMatched()).isTrue();
        });
        var reopenedMedia = attachmentService();
        var response = reopenedMedia.downloadKnowledgeBaseFile(permitted, metadata.storedName());
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
        try (var stream = response.getBody().getInputStream()) {
            assertThat(stream.readAllBytes()).containsExactly(bytes);
        }
        assertThatThrownBy(() -> reopenedMedia.downloadKnowledgeBaseFile(denied, metadata.storedName()))
                .isInstanceOf(SecurityException.class);
        assertThat(lookup.findDialog(ticket, "operator")).isPresent();
    }

    private AttachmentService attachmentService() throws Exception {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs"); // Test-only media backend, canonical SQL remains PostgreSQL.
        Path attachments = directory.resolve("attachments"), knowledge = directory.resolve("knowledge");
        var storage = new AttachmentObjectStorageService(properties, attachments.toString(), knowledge.toString(),
                directory.resolve("passport").toString(), directory.resolve("avatars").toString());
        return new AttachmentService(new PermissionService(), storage,
                mock(LegacyTicketIdJdbcGuard.class), mock(TicketAttachmentOwnerJdbcGuard.class),
                attachments.toString(), knowledge.toString());
    }
}
