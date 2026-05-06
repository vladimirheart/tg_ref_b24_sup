package com.example.panel.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class IikoDepartmentLocationsSyncSchedulerTest {

    @Test
    void refreshSharedLocationsSnapshotDelegatesToSyncService() {
        IikoDepartmentLocationsSyncService syncService = mock(IikoDepartmentLocationsSyncService.class);
        IikoDepartmentLocationsSyncScheduler scheduler = new IikoDepartmentLocationsSyncScheduler(syncService);

        scheduler.refreshSharedLocationsSnapshot();

        verify(syncService).runScheduledSyncIfDue();
    }

    @Test
    void refreshSharedLocationsSnapshotSwallowsSchedulerFailures() {
        IikoDepartmentLocationsSyncService syncService = mock(IikoDepartmentLocationsSyncService.class);
        doThrow(new IllegalStateException("boom")).when(syncService).runScheduledSyncIfDue();
        IikoDepartmentLocationsSyncScheduler scheduler = new IikoDepartmentLocationsSyncScheduler(syncService);

        scheduler.refreshSharedLocationsSnapshot();

        verify(syncService).runScheduledSyncIfDue();
    }
}
