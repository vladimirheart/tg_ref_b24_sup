package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogWorkspaceTelemetrySummaryServiceTest {

    @Test
    void delegatesDefaultWindowSummaryToAssemblerService() {
        DialogWorkspaceTelemetrySummaryAssemblerService assemblerService = mock(DialogWorkspaceTelemetrySummaryAssemblerService.class);
        DialogWorkspaceTelemetrySummaryService service = new DialogWorkspaceTelemetrySummaryService(assemblerService);
        when(assemblerService.loadSummary(7, "exp-a"))
                .thenReturn(Map.of("success", true, "totals", Map.of("workspace_open_ms_count", 5)));

        Map<String, Object> payload = service.loadSummary(7, "exp-a");

        assertThat(payload).containsEntry("success", true);
        verify(assemblerService).loadSummary(7, "exp-a");
    }

    @Test
    void delegatesExplicitWindowSummaryToAssemblerService() {
        DialogWorkspaceTelemetrySummaryAssemblerService assemblerService = mock(DialogWorkspaceTelemetrySummaryAssemblerService.class);
        DialogWorkspaceTelemetrySummaryService service = new DialogWorkspaceTelemetrySummaryService(assemblerService);
        Instant fromUtc = Instant.parse("2026-04-01T00:00:00Z");
        Instant toUtc = Instant.parse("2026-04-07T00:00:00Z");
        when(assemblerService.loadSummary(14, "exp-b", fromUtc, toUtc))
                .thenReturn(Map.of("success", true, "window_days", 14));

        Map<String, Object> payload = service.loadSummary(14, "exp-b", fromUtc, toUtc);

        assertThat(payload).containsEntry("window_days", 14);
        verify(assemblerService).loadSummary(14, "exp-b", fromUtc, toUtc);
    }
}
