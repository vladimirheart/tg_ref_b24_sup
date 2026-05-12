package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DialogAiAssistantReviewServiceTest {

    @Test
    void loadPendingReviewReturnsPendingFalseForBlankTicket() {
        DialogAiAssistantReviewService service =
                new DialogAiAssistantReviewService(mock(JdbcTemplate.class), mock(DialogAiAssistantPersistenceService.class));

        assertThat(service.loadPendingReview("   ")).containsEntry("pending", false);
    }

    @Test
    void loadPendingReviewsQueueClampsLimitToTwoHundred() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = mock(DialogAiAssistantPersistenceService.class);
        when(jdbcTemplate.queryForList(anyString(), eq(200))).thenReturn(List.of(Map.of("query_key", "k1")));

        DialogAiAssistantReviewService service =
                new DialogAiAssistantReviewService(jdbcTemplate, persistenceService);

        List<Map<String, Object>> queue = service.loadPendingReviewsQueue(999);

        assertThat(queue).hasSize(1);
        verify(jdbcTemplate).queryForList(anyString(), eq(200));
    }

    @Test
    void approvePendingReviewByKeyClearsProcessingAndRecordsEvent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = mock(DialogAiAssistantPersistenceService.class);
        when(persistenceService.trim("key-1")).thenReturn("key-1");
        when(persistenceService.trim("operator")).thenReturn("operator");
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("key-1"))).thenReturn("T-1");
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Object>eq("operator"), org.mockito.ArgumentMatchers.<Object>eq("key-1"))).thenReturn(1);

        DialogAiAssistantReviewService service =
                new DialogAiAssistantReviewService(jdbcTemplate, persistenceService);

        boolean updated = service.approvePendingReviewByKey("key-1", "operator");

        assertThat(updated).isTrue();
        verify(persistenceService).applyMemoryGovernance("key-1", "approved", "medium", null, null, null, "normal", "operator", true, "operator");
        verify(persistenceService).syncFromMemory("key-1");
        verify(persistenceService).clearProcessing("T-1", "operator_correction_approved", null);
        verify(persistenceService).recordAiEvent(eq("T-1"), eq("ai_agent_correction_approved"), eq("operator"), eq("review"), eq("approved"), eq(null), eq(null), eq(null), any());
    }

    @Test
    void rejectPendingReviewByKeyReturnsFalseWhenTrimmedKeyMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = mock(DialogAiAssistantPersistenceService.class);
        when(persistenceService.trim("   ")).thenReturn(null);

        DialogAiAssistantReviewService service =
                new DialogAiAssistantReviewService(jdbcTemplate, persistenceService);

        assertThat(service.rejectPendingReviewByKey("   ", "operator")).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }
}
