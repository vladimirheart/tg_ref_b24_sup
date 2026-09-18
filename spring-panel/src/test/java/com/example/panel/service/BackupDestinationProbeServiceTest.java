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

class BackupDestinationProbeServiceTest {

    @TempDir
    Path tempDir;

    private SharedConfigService sharedConfig() {
        SharedConfigService shared = mock(SharedConfigService.class);
        when(shared.resolvePath(anyString())).thenAnswer(invocation ->
                tempDir.resolve(invocation.getArgument(0, String.class))
        );
        return shared;
    }

    private Map<String, Object> settings(String signature) {
        return Map.ofEntries(
                Map.entry("configured", true),
                Map.entry("destination_signature", signature),
                Map.entry("destination_type", "smb-unc"),
                Map.entry("destination_path", "\\\\fileserver\\backup\\iguana"),
                Map.entry("destination_server", "fileserver"),
                Map.entry("destination_share", "backup"),
                Map.entry("destination_subpath", "iguana"),
                Map.entry("destination_auth_mode", "credential-ref"),
                Map.entry("destination_username", "DOMAIN\\backup-user"),
                Map.entry("destination_credential_ref", "backup-destination-main")
        );
    }

    @Test
    void queuesNonSecretSnapshotWithExplicitWriteFlag() throws Exception {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(settings("sig-1"));
        BackupDestinationProbeService service = new BackupDestinationProbeService(shared, settings);

        Map<String, Object> result = service.enqueue(Map.of("write_test", true), "admin");

        assertThat(result)
                .containsEntry("operation_status", "queued")
                .containsEntry("write_test", true);
        String request = Files.readString(tempDir.resolve("backup-destination-probe-request.properties"));
        assertThat(request)
                .contains("destination_signature=sig-1")
                .contains("destination_credential_ref=backup-destination-main")
                .contains("write_test=true")
                .doesNotContain("password")
                .doesNotContain("private_key")
                .doesNotContain("access_key");
    }

    @Test
    void secondProbeRequestIsRejectedUntilRunnerClaimsFirst() {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(settings("sig-1"));
        BackupDestinationProbeService service = new BackupDestinationProbeService(shared, settings);

        service.enqueue(Map.of(), "admin");
        assertThatThrownBy(() -> service.enqueue(Map.of(), "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("очеред");
    }

    @Test
    void completedProbeVerifiesOnlyMatchingDestinationSignature() throws Exception {
        SharedConfigService shared = sharedConfig();
        BackupSettingsService settings = mock(BackupSettingsService.class);
        when(settings.load()).thenReturn(settings("sig-current"));
        Files.writeString(
                tempDir.resolve("backup-destination-probe-status.properties"),
                "request_id=r1\n"
                        + "status=success\n"
                        + "destination_signature=sig-current\n"
                        + "step=free_space\n"
                        + "completed_steps=host,tcp_445,authentication,share,path,read,free_space\n"
                        + "free_bytes=1024\n"
        );
        Files.writeString(
                tempDir.resolve("backup-policy-runner.status"),
                "status=online\nlast_seen_at=" + OffsetDateTime.now(ZoneOffset.UTC) + "\nplatform=windows\n"
        );

        BackupDestinationProbeService service = new BackupDestinationProbeService(shared, settings);
        assertThat(service.status())
                .containsEntry("operation_status", "success")
                .containsEntry("destination_verified", true)
                .containsEntry("runner_active", true)
                .containsEntry("free_bytes", "1024");

        when(settings.load()).thenReturn(settings("sig-other"));
        assertThat(service.status()).containsEntry("destination_verified", false);
    }
}
