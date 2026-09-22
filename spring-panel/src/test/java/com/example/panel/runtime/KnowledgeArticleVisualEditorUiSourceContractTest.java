package com.example.panel.runtime;

import com.example.panel.entity.KnowledgeArticle;
import jakarta.persistence.Convert;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeArticleVisualEditorUiSourceContractTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/knowledge/editor.html");
    private static final Path EDITOR_JS = Path.of("src/main/resources/static/js/markdown-visual-editor.js");
    private static final Path KNOWLEDGE_SCSS = Path.of("src/main/resources/scss/app/_knowledge.scss");

    @Test
    void knowledgeArticleUsesInlineVisualEditorInsteadOfRawOnlyMarkdownUi() throws IOException {
        String template = read(TEMPLATE);

        assertThat(template)
            .contains("data-markdown-editor")
            .contains("data-markdown-surface")
            .contains("data-markdown-source")
            .contains("data-markdown-toolbar")
            .contains("data-editor-block-select")
            .contains("data-editor-bubble")
            .contains("data-editor-slash-menu")
            .contains("data-editor-slash-type=\"h2\"")
            .contains("markdown-visual-editor.js(v='20260922-01-272-s6-r3')")
            .contains("data-editor-command=\"bold\"")
            .contains("data-editor-command=\"insertUnorderedList\"")
            .contains("data-editor-command=\"createLink\"")
            .contains("kb-article-facts")
            .contains("kb-editor-side--sticky")
            .doesNotContain("Редактировать markdown")
            .doesNotContain("Markdown source");
    }

    @Test
    void reusableVisualEditorSerializesBackToMarkdownAndPreservesKnowledgeBlocks() throws IOException {
        String editor = read(EDITOR_JS);

        assertThat(editor)
            .contains("function serializeMarkdown(surface)")
            .contains("document.execCommand")
            .contains("knowledge-callout")
            .contains("<table_of_contents />")
            .contains("<empty-block />")
            .contains("data-markdown-editor-form")
            .contains("beforeunload")
            .contains("function syncFormattingState(root, surface)")
            .contains("function openSlashMenu(context)")
            .contains("function updateSelectionUi()")
            .contains("queryCommandState")
            .doesNotContain("fetch(")
            .doesNotContain("XMLHttpRequest");
    }

    @Test
    void normalSubmitSerializesContentAndDoesNotTriggerUnsavedChangesPrompt() throws IOException {
        String editor = read(EDITOR_JS);

        assertThat(editor)
            .contains("let submitting = false;")
            .contains("function prepareSubmit()")
            .contains("submitting = true;")
            .contains("form.addEventListener('submit', prepareSubmit)")
            .contains("if (submitting || !editing || !hasUnsavedChanges())")
            .contains("prepareSubmit();\n          form.submit();");
    }

    @Test
    void notionStyleEditingKeepsStickyActionsAndShowsFormattingContext() throws IOException {
        String scss = read(KNOWLEDGE_SCSS);

        assertThat(scss)
            .contains("/* 01-272 knowledge article visual editor S6 */")
            .contains("/* 01-272 knowledge article Notion-style editing follow-up S6 R2 */")
            .contains(".kb-editor-main.ops-section-card")
            .contains("overflow: visible")
            .contains(".kb-markdown-editor-toolbar")
            .contains("position: sticky")
            .contains(".kb-editor-tool.is-active")
            .contains(".kb-editor-bubble")
            .contains("position: fixed")
            .contains(".kb-editor-slash-menu")
            .contains(".kb-editor-block-select")
            .contains(".kb-markdown-editor-surface[contenteditable='true']");
    }

    @Test
    void knowledgeArticleUsesNativeOffsetDateTimeBindingForTemporalColumns() throws Exception {
        for (String fieldName : List.of("externalUpdatedAt", "createdAt", "updatedAt")) {
            Field field = KnowledgeArticle.class.getDeclaredField(fieldName);
            assertThat(field.getType())
                .as("%s must stay OffsetDateTime", fieldName)
                .isEqualTo(OffsetDateTime.class);
            assertThat(field.getAnnotation(Convert.class))
                .as("%s must use Hibernate/JDBC native temporal binding, not String AttributeConverter", fieldName)
                .isNull();
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}
