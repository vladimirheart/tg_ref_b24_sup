package com.example.panel.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DialogKnowledgeNotesSourceContractTest {

    @Test
    void notesAreLinkedByLocationNameOnlyAndRenderedPermissionSafelyInOpenDialog() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/postgresql/V46__knowledge_note_location_links.sql"), UTF_8);
        String locationService = Files.readString(Path.of("src/main/java/com/example/panel/service/KnowledgeNoteLocationService.java"), UTF_8);
        String controller = Files.readString(Path.of("src/main/java/com/example/panel/controller/DialogKnowledgeNotesApiController.java"), UTF_8);
        String noteController = Files.readString(Path.of("src/main/java/com/example/panel/controller/KnowledgeNoteController.java"), UTF_8);
        String noteEditor = Files.readString(Path.of("src/main/resources/templates/knowledge/note-editor.html"), UTF_8);
        String dialogTemplate = Files.readString(Path.of("src/main/resources/templates/dialogs/index.html"), UTF_8);
        String dialogRuntime = Files.readString(Path.of("src/main/resources/static/js/dialogs-details-runtime.js"), UTF_8);

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS knowledge_note_location_links")
            .contains("note_id BIGINT NOT NULL REFERENCES knowledge_notes(id) ON DELETE CASCADE")
            .contains("location_name TEXT NOT NULL")
            .contains("UNIQUE(note_id, location_name)")
            .doesNotContain("business TEXT")
            .doesNotContain("location_type TEXT")
            .doesNotContain("city TEXT");

        assertThat(locationService)
            .contains("dialogLookupReadService.findDialog(normalizedTicketId, operator)")
            .contains("dialog.get().locationName()")
            .contains("AND l.location_name = ?")
            .contains("ORDER BY location_matched DESC, n.updated_at DESC, n.id DESC")
            .contains("ORDER BY n.updated_at DESC, n.id DESC")
            .doesNotContain("l.business = ?")
            .doesNotContain("l.location_type = ?")
            .doesNotContain("l.city = ?");

        assertThat(controller)
            .contains("@GetMapping(\"/{ticketId}/notes\")")
            .contains("hasAuthority('PAGE_DIALOGS') and hasAuthority('PAGE_KNOWLEDGE_BASE')")
            .contains("Authentication authentication")
            .contains("listDialogNotes(ticketId, operator)");

        assertThat(noteController)
            .contains("List<String> locationNames")
            .contains("knowledgeNoteLocationService.saveNote")
            .contains("selectedLocationNames")
            .contains("locationOptions");

        assertThat(noteEditor)
            .contains("name=\"locationNames\"")
            .contains("selectedLocationNames.contains(location.name)")
            .contains("заметки его локации показываются первыми");

        assertThat(dialogTemplate)
            .contains("id=\"dialogDetailsNotesSection\"")
            .contains("id=\"dialogDetailsNotesList\"")
            .contains("dialogs-details-runtime.js(v='20261003-01-279-notes-r6')");

        assertThat(dialogRuntime)
            .contains("/notes")
            .contains("response.status === 401 || response.status === 403")
            .contains("note?.locationMatched === true")
            .contains("loadDialogNotes(ticketId);");
    }
}
