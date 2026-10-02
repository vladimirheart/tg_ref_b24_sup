package com.example.panel.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeNotesSourceContractTest {

    @Test
    void knowledgeNotesAreSeparateFromArticlesAndSupportCustomFieldsAndLinks() throws IOException {
        String migration = read("src/main/resources/db/migration/postgresql/V45__knowledge_notes.sql");
        String controller = read("src/main/java/com/example/panel/controller/KnowledgeNoteController.java");
        String service = read("src/main/java/com/example/panel/service/KnowledgeNoteService.java");
        String articleList = read("src/main/resources/templates/knowledge/list.html");
        String notes = read("src/main/resources/templates/knowledge/notes.html");
        String editor = read("src/main/resources/templates/knowledge/note-editor.html");
        String runtime = read("src/main/resources/static/js/knowledge-notes.js");

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS knowledge_notes")
            .contains("custom_fields JSONB")
            .contains("CREATE TABLE IF NOT EXISTS knowledge_note_links")
            .contains("'knowledge_article', 'object_passport'")
            .contains("UNIQUE(note_id, target_type, target_id)");

        assertThat(controller)
            .contains("@RequestMapping(\"/knowledge-base/notes\")")
            .contains("hasAuthority('PAGE_KNOWLEDGE_BASE')")
            .contains("knowledgeBaseService.listArticles()")
            .contains("objectPassportService.listPassports()")
            .contains("PAGE_OBJECT_PASSPORTS")
            .contains("note_saved");

        assertThat(service)
            .contains("TARGET_KNOWLEDGE_ARTICLE = \"knowledge_article\"")
            .contains("TARGET_OBJECT_PASSPORT = \"object_passport\"")
            .contains("buildCustomFields")
            .contains("replaceLinks")
            .contains("CAST(? AS jsonb)");

        assertThat(articleList)
            .contains("data-knowledge-tabs")
            .contains("@{/knowledge-base/notes}")
            .contains(">Заметки</a>");

        assertThat(notes)
            .contains("Новая заметка")
            .contains("data-note-time")
            .contains("knowledge-notes.js(v='20261002-01-278-notes-r13')");

        assertThat(editor)
            .contains("name=\"customFieldKey\"")
            .contains("name=\"customFieldValue\"")
            .contains("name=\"knowledgeArticleIds\"")
            .contains("name=\"objectPassportIds\"")
            .contains("data-note-add-field")
            .contains("data-note-time");

        assertThat(runtime)
            .contains("window.iguanaUiTime")
            .contains("uiTime.formatDateTime")
            .contains("data-note-custom-fields")
            .contains("data-note-remove-field");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), UTF_8);
    }
}
