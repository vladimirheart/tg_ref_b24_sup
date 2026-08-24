package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DialogAiAssistantSuggestionServiceTest {

    @Test
    void buildDeterministicReplyFallsBackForBlankSnippet() {
        DialogAiAssistantSuggestionService service =
                new DialogAiAssistantSuggestionService(mock(AiIntentService.class), mock(AiControlledLlmService.class));

        String reply = service.buildDeterministicReply(new DialogAiAssistantSuggestionCandidate(
                "knowledge", "title", null, 0.82d, null, null, null, null, null, null, null, null, null, 1, null, null, false
        ));

        assertThat(reply).contains("Коротко:");
        assertThat(reply).contains("Что сделать:");
    }

    @Test
    void buildSuggestionExplainUsesLlmTextWhenAvailable() {
        AiIntentService aiIntentService = mock(AiIntentService.class);
        AiControlledLlmService aiControlledLlmService = mock(AiControlledLlmService.class);
        when(aiControlledLlmService.explainSuggestion("T-1", "client", "snippet", "knowledge", "medium", "vpn.reset", 2))
                .thenReturn(new AiControlledLlmService.TextResult("LLM explain", true, "p", "m", "v", "ok", null));

        DialogAiAssistantSuggestionService service =
                new DialogAiAssistantSuggestionService(aiIntentService, aiControlledLlmService);

        String explain = service.buildSuggestionExplain(
                "T-1",
                "client",
                new DialogAiAssistantSuggestionCandidate("knowledge", "title", "snippet", 0.91d, null, null, "medium", null, null, null, "vpn.reset", null, null, 2, null, null, false)
        );

        assertThat(explain).isEqualTo("LLM explain");
    }

    @Test
    void memoryOperatorSuggestionUsesExactStoredSolution() {
        AiIntentService intentService = mock(AiIntentService.class);
        AiControlledLlmService llmService = mock(AiControlledLlmService.class);
        DialogAiAssistantSuggestionService service = new DialogAiAssistantSuggestionService(intentService, llmService);
        DialogAiAssistantSuggestionCandidate candidate = memoryCandidate("  тестовый ответ оператора  ");

        String reply = service.buildOperatorReplySuggestion("T-1", "тестовое описание проблемы", candidate);

        assertThat(reply).isEqualTo("тестовый ответ оператора");
        verifyNoInteractions(intentService, llmService);
    }

    @Test
    void memoryAutoReplyUsesExactStoredSolutionWithoutComposer() {
        AiIntentService intentService = mock(AiIntentService.class);
        AiControlledLlmService llmService = mock(AiControlledLlmService.class);
        DialogAiAssistantSuggestionService service = new DialogAiAssistantSuggestionService(intentService, llmService);
        DialogAiAssistantSuggestionCandidate candidate = memoryCandidate("тестовый ответ оператора");

        String reply = service.buildAutoReply("T-2", "тестовое описание проблемы", candidate, null, true);

        assertThat(reply).isEqualTo("тестовый ответ оператора");
        verifyNoInteractions(llmService);
    }

    private DialogAiAssistantSuggestionCandidate memoryCandidate(String snippet) {
        return new DialogAiAssistantSuggestionCandidate(
                "memory",
                "Test memory",
                snippet,
                0.95d,
                "memory-key",
                "approved",
                "high",
                "operator",
                "normal",
                "memory:memory-key",
                "generic.support",
                null,
                "memory:memory-key",
                2,
                "trace",
                "2026-08-24T14:40:00+03:00",
                false
        );
    }
}
