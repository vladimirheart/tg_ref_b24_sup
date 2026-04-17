package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private ObjectNode page(String id) {
        return new ObjectMapper().createObjectNode().put("id", id);
    }
}
