package com.example.panel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class IikoDepartmentLocationsSyncSchedulerTest {

    @Test
    void refreshSharedLocationsSnapshotDelegatesToSyncService() {
        IikoDepartmentLocationsSyncService syncService = mock(IikoDepartmentLocationsSyncService.class);
        IikoDepartmentLocationsSyncScheduler scheduler = new IikoDepartmentLocationsSyncScheduler(syncService, passthroughCoordinationService());

        scheduler.refreshSharedLocationsSnapshot();

        verify(syncService).runScheduledSyncIfDue();
    }

    @Test
    void refreshSharedLocationsSnapshotSwallowsSchedulerFailures() {
        IikoDepartmentLocationsSyncService syncService = mock(IikoDepartmentLocationsSyncService.class);
        doThrow(new IllegalStateException("boom")).when(syncService).runScheduledSyncIfDue();
        IikoDepartmentLocationsSyncScheduler scheduler = new IikoDepartmentLocationsSyncScheduler(syncService, passthroughCoordinationService());

        scheduler.refreshSharedLocationsSnapshot();

        verify(syncService).runScheduledSyncIfDue();
    }

    private RuntimeCoordinationService passthroughCoordinationService() {
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(coordinationService).runWithLease(anyString(), any(), any(Runnable.class));
        return coordinationService;
    }
}
