package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MonitoringCheckHistoryRetentionServiceTest {

    @Test
    void sqliteStartupCleanupUsesThirtyDayCutoffWithoutLease() {
        MonitoringCheckHistoryRepository repository = mock(MonitoringCheckHistoryRepository.class);
        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        when(runtimeMode.isExternalDatabaseEnabled()).thenReturn(false);
        when(runtimeMode.modeLabel()).thenReturn("sqlite");
        when(repository.deleteOlderThan(any())).thenReturn(3);

        MonitoringCheckHistoryRetentionService service = new MonitoringCheckHistoryRetentionService(
            repository,
            runtimeMode,
            coordinationService
        );
        OffsetDateTime lowerBound = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).minusSeconds(2);
        service.run(null);
        OffsetDateTime upperBound = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).plusSeconds(2);

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteOlderThan(cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(lowerBound));
        assertFalse(cutoff.getValue().isAfter(upperBound));
        verify(coordinationService, never()).runWithLease(any(), any(Duration.class), any(Runnable.class));
    }

    @Test
    void externalScheduledCleanupRunsUnderSharedLease() {
        MonitoringCheckHistoryRepository repository = mock(MonitoringCheckHistoryRepository.class);
        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        when(runtimeMode.isExternalDatabaseEnabled()).thenReturn(true);
        when(runtimeMode.modeLabel()).thenReturn("postgresql");
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(coordinationService).runWithLease(
            eq("monitoring-history-retention"),
            eq(Duration.ofMinutes(10)),
            any(Runnable.class)
        );

        MonitoringCheckHistoryRetentionService service = new MonitoringCheckHistoryRetentionService(
            repository,
            runtimeMode,
            coordinationService
        );
        service.scheduledCleanup();

        verify(coordinationService).runWithLease(
            eq("monitoring-history-retention"),
            eq(Duration.ofMinutes(10)),
            any(Runnable.class)
        );
        verify(repository).deleteOlderThan(any(OffsetDateTime.class));
    }
}