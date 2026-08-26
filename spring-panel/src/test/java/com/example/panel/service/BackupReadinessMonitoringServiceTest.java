package com.example.panel.service;

import com.example.panel.entity.BackupReadinessMonitor;
import com.example.panel.repository.BackupReadinessMonitorRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupReadinessMonitoringServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void refreshByIdUsesNewestFileFromDirectoryAndMarksMonitorHealthy() throws Exception {
        Path oldDump = Files.writeString(tempDir.resolve("old.dump"), "old");
        Path newDump = Files.writeString(tempDir.resolve("new.dump"), "new");
        Files.setLastModifiedTime(oldDump, FileTime.from(Instant.now().minusSeconds(6 * 3600)));
        Files.setLastModifiedTime(newDump, FileTime.from(Instant.now().minusSeconds(15 * 60)));

        BackupReadinessMonitorRepository repository = mock(BackupReadinessMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        BackupReadinessMonitor monitor = monitor(42L, tempDir.toString(), 6, 14);
        monitor.setLastRestoreVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));

        when(repository.findById(42L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(BackupReadinessMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackupReadinessMonitoringService service = new BackupReadinessMonitoringService(repository, historyRepository);

        BackupReadinessMonitor result = service.refreshById(42L);

        assertThat(result.getLastBackupPath()).isEqualTo(newDump.toAbsolutePath().normalize().toString());
        assertThat(result.getLastStatus()).isEqualTo(BackupReadinessMonitoringService.STATUS_OK);
        assertThat(result.getLastErrorMessage()).isNull();
        verify(historyRepository).record(
            eq("backup_readiness"),
            eq(42L),
            eq("backup_probe"),
            eq("ok"),
            contains("backup fresh"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void refreshByIdMarksMonitorCriticalWhenBackupStaleAndRestoreEvidenceMissing() throws Exception {
        Path dump = Files.writeString(tempDir.resolve("stale.dump"), "payload");
        Files.setLastModifiedTime(dump, FileTime.from(Instant.now().minusSeconds(48 * 3600)));

        BackupReadinessMonitorRepository repository = mock(BackupReadinessMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        BackupReadinessMonitor monitor = monitor(7L, dump.toString(), 24, 14);

        when(repository.findById(7L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(BackupReadinessMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackupReadinessMonitoringService service = new BackupReadinessMonitoringService(repository, historyRepository);

        BackupReadinessMonitor result = service.refreshById(7L);

        assertThat(result.getLastStatus()).isEqualTo(BackupReadinessMonitoringService.STATUS_CRITICAL);
        assertThat(result.getLastSummary()).contains("restore evidence отсутствует");
        verify(historyRepository).record(
            eq("backup_readiness"),
            eq(7L),
            eq("backup_probe"),
            eq("critical"),
            contains("restore evidence отсутствует"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void confirmRestoreEvidenceStoresNoteAndRecomputesStatus() throws Exception {
        Path dump = Files.writeString(tempDir.resolve("fresh.dump"), "payload");
        Files.setLastModifiedTime(dump, FileTime.from(Instant.now().minusSeconds(45 * 60)));

        BackupReadinessMonitorRepository repository = mock(BackupReadinessMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        BackupReadinessMonitor monitor = monitor(9L, dump.toString(), 6, 7);

        when(repository.findById(9L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(BackupReadinessMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackupReadinessMonitoringService service = new BackupReadinessMonitoringService(repository, historyRepository);
        OffsetDateTime verifiedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        BackupReadinessMonitor result = service.confirmRestoreEvidence(
            9L,
            new BackupReadinessMonitoringService.RestoreEvidenceDraft(verifiedAt, "Restore drill completed")
        );

        assertThat(result.getLastRestoreVerifiedAt()).isEqualTo(verifiedAt.withOffsetSameInstant(ZoneOffset.UTC));
        assertThat(result.getLastRestoreNote()).isEqualTo("Restore drill completed");
        assertThat(result.getLastStatus()).isEqualTo(BackupReadinessMonitoringService.STATUS_OK);
        verify(historyRepository).record(
            eq("backup_readiness"),
            eq(9L),
            eq("restore_evidence"),
            eq("ok"),
            contains("Restore evidence"),
            any(),
            isNull(),
            isNull(),
            eq(verifiedAt.withOffsetSameInstant(ZoneOffset.UTC))
        );
        verify(historyRepository, atLeastOnce()).record(
            eq("backup_readiness"),
            eq(9L),
            eq("backup_probe"),
            eq("ok"),
            contains("backup fresh"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void refreshByIdImportsAutomatedRestoreSuccessEvidence() throws Exception {
        Path dump = Files.writeString(tempDir.resolve("fresh.dump"), "payload");
        Files.setLastModifiedTime(dump, FileTime.from(Instant.now().minusSeconds(30 * 60)));
        OffsetDateTime verifiedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0);
        Files.writeString(
            tempDir.resolve(".iguana-restore-evidence.properties"),
            "status=ok\nattempt_at=" + verifiedAt.minusMinutes(3) + "\nverified_at=" + verifiedAt + "\nnote=Automated restore passed\n"
        );

        BackupReadinessMonitorRepository repository = mock(BackupReadinessMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        BackupReadinessMonitor monitor = monitor(11L, tempDir.toString(), 6, 14);
        when(repository.findById(11L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(BackupReadinessMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackupReadinessMonitoringService service = new BackupReadinessMonitoringService(repository, historyRepository);
        BackupReadinessMonitor result = service.refreshById(11L);

        assertThat(result.getLastRestoreVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(result.getLastRestoreNote()).isEqualTo("Automated restore passed");
        assertThat(result.getLastStatus()).isEqualTo(BackupReadinessMonitoringService.STATUS_OK);
        verify(historyRepository).record(
            eq("backup_readiness"),
            eq(11L),
            eq("restore_evidence"),
            eq("ok"),
            contains("автоматически"),
            any(),
            isNull(),
            isNull(),
            eq(verifiedAt)
        );
    }

    @Test
    void refreshByIdTreatsNewerAutomatedRestoreFailureAsError() throws Exception {
        Path dump = Files.writeString(tempDir.resolve("fresh.dump"), "payload");
        Files.setLastModifiedTime(dump, FileTime.from(Instant.now().minusSeconds(30 * 60)));
        OffsetDateTime lastSuccess = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2).withNano(0);
        OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0);
        Files.writeString(
            tempDir.resolve(".iguana-restore-failure.properties"),
            "status=error\nattempt_at=" + failedAt + "\nnote=Automated restore failed\n"
        );

        BackupReadinessMonitorRepository repository = mock(BackupReadinessMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        BackupReadinessMonitor monitor = monitor(12L, tempDir.toString(), 6, 14);
        monitor.setLastRestoreVerifiedAt(lastSuccess);
        when(repository.findById(12L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(BackupReadinessMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackupReadinessMonitoringService service = new BackupReadinessMonitoringService(repository, historyRepository);
        BackupReadinessMonitor result = service.refreshById(12L);

        assertThat(result.getLastStatus()).isEqualTo(BackupReadinessMonitoringService.STATUS_ERROR);
        assertThat(result.getLastErrorMessage()).contains("restore rehearsal failed");
        assertThat(result.getLastSummary()).contains("restore rehearsal failed");
        verify(historyRepository).record(
            eq("backup_readiness"),
            eq(12L),
            eq("restore_evidence"),
            eq("error"),
            contains("Automated restore rehearsal failed"),
            any(),
            isNull(),
            isNull(),
            eq(failedAt)
        );
    }

    private BackupReadinessMonitor monitor(long id, String pathPattern, int freshnessHours, int restoreDays) {
        BackupReadinessMonitor monitor = new BackupReadinessMonitor();
        monitor.setId(id);
        monitor.setMonitorName("postgresql-prod");
        monitor.setBackupKind("postgresql");
        monitor.setPathPattern(pathPattern);
        monitor.setEnabled(true);
        monitor.setFreshnessThresholdHours(freshnessHours);
        monitor.setRestoreThresholdDays(restoreDays);
        monitor.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        return monitor;
    }
}
