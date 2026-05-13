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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogAiAssistantEventServiceTest {

    @Test
    void encodeSourceHitsSerializesCandidates() {
        DialogAiAssistantEventService service = new DialogAiAssistantEventService(mock(JdbcTemplate.class), new ObjectMapper());

        String json = service.encodeSourceHits(List.of(
                new DialogAiAssistantSuggestionCandidate("knowledge", "VPN", "snippet", 0.91d, "mem-1", "approved", "medium", "kb", "normal", "ref-1", "vpn.reset", "slot", "canon", 2, "trace", "2026-05-13T10:00:00Z", false)
        ));

        assertThat(json).contains("\"source\":\"knowledge\"");
        assertThat(json).contains("\"memory_key\":\"mem-1\"");
    }

    @Test
    void buildRetrievalPayloadIncludesRewriteAndTopCandidateFields() {
        DialogAiAssistantEventService service = new DialogAiAssistantEventService(mock(JdbcTemplate.class), new ObjectMapper());
        AiRetrievalService.RetrievalResult retrievalResult = new AiRetrievalService.RetrievalResult(
                new AiRetrievalService.RetrievalContext("T-1", "vpn", java.util.Set.of("vpn"),
                        new AiIntentService.IntentMatch("vpn.reset", Map.of(), "{}", null, true, java.util.List.of(), 0.88d),
                        new AiIntentService.IntentPolicy("vpn.reset", true, false, false, "normal", null),
                        "telegram", null, null),
                List.of(),
                new AiRetrievalService.ConsistencyCheck(true, false, 2, "confirmed")
        );

        Map<String, Object> payload = service.buildRetrievalPayload(
                "[]",
                new DialogAiAssistantSuggestionCandidate("knowledge", "VPN", "snippet", 0.91d, "mem-1", "approved", "medium", "kb", "normal", "ref-1", "vpn.reset", "slot", "canon", 2, "trace", "2026-05-13T10:00:00Z", false),
                retrievalResult,
                true,
                new AiControlledLlmService.RewriteResult("vpn", "vpn reset", true, "p", "m", "rewrite", null, "v")
        );

        assertThat(payload)
                .containsEntry("sensitive_topic", 1)
                .containsEntry("llm_rewrite_used", 1)
                .containsEntry("top_candidate_source_ref", "ref-1")
                .containsEntry("intent_key", "vpn.reset");
    }

    @Test
    void recordAiEventWritesPrimarySchemaWhenAvailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        DialogAiAssistantEventService service = new DialogAiAssistantEventService(jdbcTemplate, new ObjectMapper());

        service.recordAiEvent("T-1", "ai_event", "operator", "review", "approved", "knowledge", 0.91d, "detail", Map.of(
                "policy_stage", "stage",
                "policy_outcome", "ok",
                "intent_key", "vpn.reset",
                "sensitive_topic", 1
        ));

        verify(jdbcTemplate).update(anyString(), eq("T-1"), eq("ai_event"), eq("operator"), eq("review"), eq("approved"), eq("knowledge"), eq(0.91d), eq("detail"), any(), eq("stage"), eq("ok"), eq("vpn.reset"), eq(1), eq(null), eq(null));
    }
}
