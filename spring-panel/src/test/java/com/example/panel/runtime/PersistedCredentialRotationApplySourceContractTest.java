package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersistedCredentialRotationApplySourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void applyWorkflowCoversPowerShellAndBashIncludingGrafanaRotation() throws IOException {
        String ps = read("scripts/docker-production-credential-migration-apply.ps1");
        String sh = read("scripts/docker-production-credential-migration-apply.sh");
        String runbook = read("docs/runbooks/persisted-credential-rotation-apply.md");

        assertThat(ps)
            .contains("[ValidateSet(\"postgresql\", \"rabbitmq\", \"redis\", \"minio\", \"grafana\", \"all\")]")
            .contains("[string[]]$Components")
            .contains("[switch]$Rehearsal")
            .contains("[string]$BackupDirectory")
            .contains("Resolve-CurrentGrafanaPassword")
            .contains("Update-GrafanaPassword")
            .contains("Test-GrafanaCredential")
            .contains("New-PreApplyBackupSnapshot")
            .contains("Invoke-RotationOrchestration")
            .contains("Credential rotation orchestration mode:")
            .contains("Bulk credential rotation rehearsal completed successfully.")
            .contains("Best-effort Grafana rollback failed")
            .contains("Best-effort Grafana rollback recreate failed")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD")
            .contains("\"grafana\"")
            .contains("\"cli\"")
            .contains("reset-admin-password")
            .contains("docker-compose.production-observability.yml");

        assertThat(sh)
            .contains("SUPPORTED_COMPONENTS=(postgresql rabbitmq redis minio grafana)")
            .contains("--components")
            .contains("--rehearsal")
            .contains("--backup-dir")
            .contains("resolve_current_grafana_password")
            .contains("update_grafana_password")
            .contains("test_grafana_credential")
            .contains("resolve_selected_components")
            .contains("create_preapply_snapshot")
            .contains("run_orchestration")
            .contains("Bulk credential rotation rehearsal completed successfully.")
            .contains("Best-effort Grafana rollback failed")
            .contains("Best-effort Grafana rollback recreate failed")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD")
            .contains("grafana cli --homepath /usr/share/grafana --config /etc/grafana/grafana.ini admin reset-admin-password")
            .contains("docker-compose.production-observability.yml");

        assertThat(runbook)
            .contains("scripts/docker-production-credential-migration-apply.ps1")
            .contains("scripts/docker-production-credential-migration-apply.sh")
            .contains("grafana")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD")
            .contains("-Components")
            .contains("--components")
            .contains("-Rehearsal")
            .contains("--rehearsal")
            .contains("-BackupDirectory")
            .contains("--backup-dir")
            .contains("component-order.txt")
            .contains("reset-admin-password")
            .contains("api/user")
            .contains("rehearsal")
            .contains("01-212")
            .contains("01-220");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
