package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupManualOperationServiceTest {

    @TempDir
    Path tempDir;

    private SharedConfigService sharedConfig() {
        SharedConfigService shared = mock(SharedConfigService.class);
        when(shared.resolvePath(anyString())).thenAnswer(invocation ->
                tempDir.resolve(invocation.getArgument(0, String.class))
        );
        return shared;
    }

    @Test
    void queuesLocalTestRequestWithoutGivingPanelHostExecutionRights() throws Exception {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(Map.of("configured", true, "external_failure_domain", false));
        BackupManualOperationService service = new BackupManualOperationService(shared, settings);

        Map<String, Object> result = service.enqueue(Map.of(
                "mode", "critical",
                "verify_restore", true,
                "allow_local_test", true
        ), "admin");

        assertThat(result)
                .containsEntry("operation_status", "queued")
                .containsEntry("mode", "critical")
                .containsEntry("verify_restore", true)
                .containsEntry("allow_local_test", true);

        String request = Files.readString(tempDir.resolve("backup-manual-request.properties"));
        assertThat(request)
                .contains("mode=critical")
                .contains("verify_restore=true")
                .contains("allow_local_test=true")
                .contains("requested_by=admin");
    }

    @Test
    void localManualRunRequiresExplicitTestAcknowledgement() {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(Map.of("configured", true, "external_failure_domain", false));
        BackupManualOperationService service = new BackupManualOperationService(shared, settings);

        assertThatThrownBy(() -> service.enqueue(Map.of(
                "mode", "critical",
                "verify_restore", false,
                "allow_local_test", false
        ), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("локальной проверки");
    }

    @Test
    void secondManualRequestIsRejectedUntilRunnerClaimsFirst() {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(Map.of("configured", true, "external_failure_domain", true));
        BackupManualOperationService service = new BackupManualOperationService(shared, settings);

        service.enqueue(Map.of("mode", "full"), "admin");

        assertThatThrownBy(() -> service.enqueue(Map.of("mode", "critical"), "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("очеред");
    }

    @Test
    void runnerHeartbeatIsReportedAsActive() throws Exception {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(Map.of("configured", true, "external_failure_domain", true));

        Files.writeString(
                tempDir.resolve("backup-policy-runner.status"),
                "status=online\n"
                        + "last_seen_at=" + OffsetDateTime.now(ZoneOffset.UTC) + "\n"
                        + "platform=windows\n"
                        + "schedule_ready=true\n"
        );

        BackupManualOperationService service = new BackupManualOperationService(shared, settings);
        assertThat(service.status())
                .containsEntry("runner_active", true)
                .containsEntry("schedule_ready", true)
                .containsEntry("runner_platform", "windows");
    }
@Test
    void offlineHeartbeatIsNotReportedAsActive() throws Exception {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(Map.of("configured", true, "external_failure_domain", true));

        Files.writeString(
                tempDir.resolve("backup-policy-runner.status"),
                "status=offline\n"
                        + "last_seen_at=" + OffsetDateTime.now(ZoneOffset.UTC) + "\n"
                        + "platform=windows\n"
                        + "schedule_ready=true\n"
        );

        BackupManualOperationService service = new BackupManualOperationService(shared, settings);
        assertThat(service.status())
                .containsEntry("runner_active", false);
    }
}
