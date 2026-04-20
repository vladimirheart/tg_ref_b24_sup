package com.example.panel.service;

import com.example.panel.entity.KnowledgeArticle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseNotionServiceTest {

    private final KnowledgeBaseNotionService service = new KnowledgeBaseNotionService(
        null,
        null,
        null,
        null,
        new ObjectMapper()
    );

    @Test
    void rejectsRootNotionUrlWithoutUuid() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> service.validateSourceReferenceFormat("https://www.notion.so")
        );

        assertTrue(error.getMessage().contains("UUID"));
    }

    @Test
    void acceptsDirectUuidAsSourceReference() {
        assertDoesNotThrow(() -> service.validateSourceReferenceFormat("4c3bfdf0-7d81-47d5-b9ce-695e154a33f6"));
    }

    @Test
    void rewritesMissingDataSourceAccessMessage() {
        String message = service.decorateNotionApiMessage(
            "4c3bfdf0-7d81-47d5-b9ce-695e154a33f6",
            "Database with ID 4c3bfdf0-7d81-47d5-b9ce-695e154a33f6 does not contain any data sources accessible by this API bot."
        );

        assertTrue(message.contains("data source"));
        assertTrue(message.contains("data_source_id"));
        assertTrue(message.contains("4c3bfdf0-7d81-47d5-b9ce-695e154a33f6"));
    }

    @Test
    void keepsAllMatchedPagesWhenImportRunsWithoutManualSelection() {
        List<ObjectNode> pages = List.of(page("page-1"), page("page-2"));

        List<?> selected = service.filterSelectedPages(List.copyOf(pages), null, true);

        assertEquals(2, selected.size());
    }

    @Test
    void importsOnlyExplicitlySelectedPages() {
        List<ObjectNode> pages = List.of(page("page-1"), page("page-2"), page("page-3"));

        List<?> selected = service.filterSelectedPages(List.copyOf(pages), List.of("page-2", "page-3"), false);

        assertEquals(2, selected.size());
        assertEquals("page-2", ((ObjectNode) selected.get(0)).path("id").asText());
        assertEquals("page-3", ((ObjectNode) selected.get(1)).path("id").asText());
    }

    @Test
    void extractsOnlyDownloadableUrlsFromMarkdown() {
        String markdown = """
            [Инструкция](https://example.com/files/manual.pdf)
            Обычная ссылка: https://example.com/page
            Архив: https://example.com/archive/support-kit.zip
            """;

        List<String> urls = service.extractMarkdownAttachmentUrls(markdown);

        assertEquals(
            List.of(
                "https://example.com/files/manual.pdf",
                "https://example.com/archive/support-kit.zip"
            ),
            urls
        );
    }

    @Test
    void selectsOnlyNewOrChangedPagesForChangedSync() {
        ObjectNode unchanged = page("page-1");
        unchanged.put("last_edited_time", "2026-04-20T10:00:00Z");
        ObjectNode changed = page("page-2");
        changed.put("last_edited_time", "2026-04-20T12:00:00Z");
        ObjectNode fresh = page("page-3");
        fresh.put("last_edited_time", "2026-04-20T09:00:00Z");

        KnowledgeArticle current = new KnowledgeArticle();
        current.setExternalId("page-1");
        current.setExternalUpdatedAt(OffsetDateTime.parse("2026-04-20T10:00:00Z"));

        KnowledgeArticle stale = new KnowledgeArticle();
        stale.setExternalId("page-2");
        stale.setExternalUpdatedAt(OffsetDateTime.parse("2026-04-20T11:00:00Z"));

        List<?> changedPages = service.filterChangedPages(
            List.of(unchanged, changed, fresh),
            Map.of("page-1", current, "page-2", stale),
            true
        );

        assertEquals(2, changedPages.size());
        assertEquals("page-2", ((ObjectNode) changedPages.get(0)).path("id").asText());
        assertEquals("page-3", ((ObjectNode) changedPages.get(1)).path("id").asText());
    }

    private ObjectNode page(String id) {
        return new ObjectMapper().createObjectNode().put("id", id);
    }
}
