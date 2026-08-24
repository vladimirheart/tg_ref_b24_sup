package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogAiSolutionMemoryServiceTest {

    @Test
    void loadSolutionMemoryClampsLimitAndReturnsRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = mock(DialogAiAssistantPersistenceService.class);
        when(persistenceService.trim(null)).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), eq(500))).thenReturn(List.of(Map.of("query_key", "k1")));

        DialogAiSolutionMemoryService service =
                new DialogAiSolutionMemoryService(jdbcTemplate, persistenceService);

        List<Map<String, Object>> items = service.loadSolutionMemory(999, null);

        assertThat(items).hasSize(1);
        verify(jdbcTemplate).queryForList(anyString(), eq(500));
    }

    @Test
    void updateSolutionMemoryReturnsFalseWhenExistingRecordMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = mock(DialogAiAssistantPersistenceService.class);
        when(persistenceService.trim("key")).thenReturn("key");
        when(persistenceService.trim("query")).thenReturn("query");
        when(persistenceService.trim("solution")).thenReturn("solution");
        when(persistenceService.loadSolutionMemoryByKey("key")).thenReturn(null);

        DialogAiSolutionMemoryService service =
                new DialogAiSolutionMemoryService(jdbcTemplate, persistenceService);

        assertThat(service.updateSolutionMemory("key", "query", "solution", false, false, "operator")).isFalse();
        verify(jdbcTemplate, never()).update(anyString(), eq("solution"), eq(0), eq(0), eq(0), eq("solution"), eq("operator"), eq("key"));
    }

    @Test
    void updateRejectsQuestionIdentityChange() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = mock(DialogAiAssistantPersistenceService.class);
        when(persistenceService.trim("key")).thenReturn("key");
        when(persistenceService.trim("changed query")).thenReturn("changed query");
        when(persistenceService.trim("solution")).thenReturn("solution");
        when(persistenceService.safe("original query")).thenReturn("original query");
        when(persistenceService.trim("original query")).thenReturn("original query");
        when(persistenceService.loadSolutionMemoryByKey("key")).thenReturn(Map.of(
                "query_text", "original query",
                "solution_text", "old solution",
                "review_required", 0
        ));

        DialogAiSolutionMemoryService service =
                new DialogAiSolutionMemoryService(jdbcTemplate, persistenceService);

        assertThat(service.updateSolutionMemory("key", "changed query", "solution", false, false, "operator")).isFalse();
        verify(jdbcTemplate, never()).update(anyString(), eq("solution"), eq(0), eq(0), eq(0), eq("solution"), eq("operator"), eq("key"));
    }

    @Test
    void deleteEndpointSoftDisablesMemoryAndKeepsHistory() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = mock(DialogAiAssistantPersistenceService.class);
        when(persistenceService.trim("key")).thenReturn("key");
        when(persistenceService.trim("operator")).thenReturn("operator");
        when(persistenceService.loadSolutionMemoryByKey("key")).thenReturn(Map.of(
                "query_text", "old query",
                "solution_text", "old solution",
                "review_required", 0
        ));
        when(persistenceService.safe("old query")).thenReturn("old query");
        when(persistenceService.safe("old solution")).thenReturn("old solution");
        when(persistenceService.isTrue(0)).thenReturn(false);
        when(jdbcTemplate.update(anyString(), eq("operator"), eq("key"))).thenReturn(1);

        DialogAiSolutionMemoryService service =
                new DialogAiSolutionMemoryService(jdbcTemplate, persistenceService);

        boolean disabled = service.deleteSolutionMemory("key", "operator");

        assertThat(disabled).isTrue();
        verify(persistenceService).forgetMemory("key");
        verify(persistenceService).insertSolutionMemoryHistory(
                eq("key"),
                eq("operator"),
                eq("manual"),
                eq("disable"),
                eq("old query"),
                eq("old solution"),
                eq(false),
                eq("old query"),
                eq("old solution"),
                eq(false),
                eq("manual_disable")
        );
    }

    @Test
    void rollbackSolutionMemoryReturnsFalseForInvalidArguments() {
        DialogAiSolutionMemoryService service =
                new DialogAiSolutionMemoryService(mock(JdbcTemplate.class), mock(DialogAiAssistantPersistenceService.class));

        assertThat(service.rollbackSolutionMemory(" ", 0L, "operator")).isFalse();
    }
}