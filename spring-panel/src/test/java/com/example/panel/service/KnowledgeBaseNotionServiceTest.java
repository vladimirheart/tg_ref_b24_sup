package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseNotionServiceTest {

    private final KnowledgeBaseNotionService service = new KnowledgeBaseNotionService(
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
}
