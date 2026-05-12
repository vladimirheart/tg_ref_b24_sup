package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogAiAssistantStateServiceTest {

    @Test
    void loadDialogControlStateReturnsPersistedFlags() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                jdbcTemplate,
                mock(SharedConfigService.class),
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        when(jdbcTemplate.queryForList(anyString(), eq("T-1"))).thenReturn(List.of(Map.of(
                "ai_disabled", 1,
                "auto_reply_blocked", 0,
                "reason", "manual",
                "updated_by", "operator",
                "updated_at", "2026-05-12T10:00:00Z"
        )));

        DialogAiAssistantStateService service = new DialogAiAssistantStateService(jdbcTemplate, persistenceService);

        Map<String, Object> payload = service.loadDialogControlState("T-1");

        assertThat(payload)
                .containsEntry("ticket_id", "T-1")
                .containsEntry("ai_disabled", true)
                .containsEntry("auto_reply_blocked", false)
                .containsEntry("reason", "manual")
                .containsEntry("updated_by", "operator");
    }

    @Test
    void updateDialogControlStatePersistsAndRecordsEvent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                jdbcTemplate,
                mock(SharedConfigService.class),
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        doNothing().when(persistenceService).recordAiEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
        when(jdbcTemplate.queryForList(anyString(), eq("T-1"))).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        DialogAiAssistantStateService service = new DialogAiAssistantStateService(jdbcTemplate, persistenceService);

        boolean updated = service.updateDialogControlState("T-1", true, null, "manual", "operator", "assist_only");

        assertThat(updated).isTrue();
        verify(jdbcTemplate).update(anyString(), eq("T-1"), eq(1), eq(0), eq("manual"), eq("operator"));
        verify(persistenceService).recordAiEvent(eq("T-1"), eq("ai_agent_control_changed"), eq("operator"), eq("control_update"), eq("dialog_control_updated"), eq(null), eq(null), eq("manual"), any());
    }

    @Test
    void isProcessingReturnsTrueWhenStateIsPositive() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                jdbcTemplate,
                mock(SharedConfigService.class),
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("T-1"))).thenReturn(1);

        DialogAiAssistantStateService service = new DialogAiAssistantStateService(jdbcTemplate, persistenceService);

        assertThat(service.isProcessing("T-1")).isTrue();
    }
}
