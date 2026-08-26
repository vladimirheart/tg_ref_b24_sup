package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionBackupContourSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void backupOverlayKeepsWriteAccessOutOfPanelWeb() throws IOException {
        String compose = read("docker-compose.production-backup.yml");

        assertThat(compose)
            .contains("\n  postgres-backup:\n")
            .contains("\n  minio-backup:\n")
            .contains("\n  postgres-restore-target:\n")
            .contains("\n  minio-restore-target:\n")
            .contains("profiles: [\"backup\"]")
            .contains("IGUANA_BACKUP_DESTINATION_DIR")
            .contains("target: /opt/iguana/backups/offhost")
            .contains("read_only: true")
            .contains("tmpfs:")
            .doesNotContain("container_name:");

        String worker = section(compose, "  ops-worker:", "  postgres-backup:");
        assertThat(worker)
            .contains("read_only: true")
            .contains("/opt/iguana/backups/offhost");

        assertThat(compose).doesNotContain("\n  panel-web:\n");
    }

    @Test
    void backupScriptsPublishValidatedArtifactsAndRestoreEvidence() throws IOException {
        String postgresBackup = read("docker/backup/postgres-backup.sh");
        String postgresRestore = read("docker/backup/postgres-restore-rehearsal.sh");
        String minioBackup = read("docker/backup/minio-backup.sh");
        String minioRestore = read("docker/backup/minio-restore-rehearsal.sh");

        assertThat(postgresBackup)
            .contains("pg_dump")
            .contains("--format=custom")
            .contains("pg_restore --list")
            .contains("sha256sum")
            .contains("mv \"${tmp_dump}\" \"${final_dump}\"");

        assertThat(postgresRestore)
            .contains("sha256sum -c")
            .contains("pg_restore")
            .contains("flyway_schema_history")
            .contains("tickets")
            .contains(".iguana-restore-evidence.properties")
            .contains(".iguana-restore-failure.properties");

        assertThat(minioBackup)
            .contains("mc cp --recursive")
            .contains("source_object_count")
            .contains("MinIO snapshot object count mismatch")
            .contains("inventory.jsonl")
            .contains("restore-sentinel.txt")
            .doesNotContain("mc mirror \"primary/${bucket}\"");

        assertThat(minioRestore)
            .contains("minio-restore-target")
            .contains("source_count")
            .contains("actual_sha")
            .contains(".iguana-restore-evidence.properties")
            .contains(".iguana-restore-failure.properties");
    }

    @Test
    void helpersEnforceOffHostProductionDestinationAndExposeBackupOverlay() throws IOException {
        String backupPs = read("scripts/docker-production-backup.ps1");
        String backupSh = read("scripts/docker-production-backup.sh");
        String upPs = read("scripts/docker-production-up.ps1");
        String downPs = read("scripts/docker-production-down.ps1");
        String env = read(".env.example");

        assertThat(backupPs)
            .contains("IGUANA_BACKUP_DESTINATION_DIR")
            .contains("must be an absolute off-host path")
            .contains("AllowLocalDestination")
            .contains("postgres-backup")
            .contains("minio-backup")
            .contains("postgres-restore-rehearsal")
            .contains("minio-restore-rehearsal");

        assertThat(backupSh)
            .contains("IGUANA_BACKUP_DESTINATION_DIR")
            .contains("--allow-local-destination")
            .contains("--action");

        assertThat(upPs)
            .contains("[switch]$Backup")
            .contains("docker-compose.production-backup.yml")
            .contains("Backup enabled: $Backup");
        assertThat(downPs)
            .contains("[switch]$Backup")
            .contains("docker-compose.production-backup.yml");
        assertThat(env)
            .contains("IGUANA_BACKUP_DESTINATION_DIR=")
            .contains("IGUANA_BACKUP_RETENTION_DAYS=30")
            .contains("IGUANA_MINIO_BACKUP_RETENTION_DAYS=14");
    }

    @Test
    void backupReadinessImportsAutomatedRestoreEvidenceInWorkerBoundary() throws IOException {
        String service = read("spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringService.java");
        String scheduler = read("spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringScheduler.java");

        assertThat(service)
            .contains("AUTOMATED_RESTORE_SUCCESS_FILE")
            .contains("AUTOMATED_RESTORE_FAILURE_FILE")
            .contains("ensureManagedProductionMonitors")
            .contains("loadAutomatedRestoreEvidence")
            .contains("iguana-postgresql-production-backup")
            .contains("iguana-minio-production-backup");

        assertThat(scheduler)
            .contains("roles = {RuntimeRole.WORKER}")
            .contains("ensureManagedProductionMonitors()");
    }

    @Test
    void minioBackupToolingImageContainsMcAndUnixApplets() throws IOException {
        String compose = read("docker-compose.production-backup.yml");
        String dockerfile = read("docker/backup/minio-tools.Dockerfile");

        assertThat(compose)
            .contains("IGUANA_MINIO_BACKUP_TOOLS_IMAGE")
            .contains("dockerfile: docker/backup/minio-tools.Dockerfile");

        assertThat(dockerfile)
            .contains("FROM minio/mc:RELEASE.2025-07-21T05-28-08Z AS mc")
            .contains("FROM busybox:1.36.1")
            .contains("COPY --from=mc /usr/bin/mc /usr/bin/mc");
    }
    @Test
    void backupSmokeUsesFileBackedMinioLifecycleVerification() throws IOException {
        String smoke = read("scripts/docker-production-backup-smoke.ps1");
        String minioBackup = read("docker/backup/minio-backup.sh");

        assertThat(smoke)
            .contains(".smoke-seed.sh")
            .contains(".smoke-fresh-verify.sh")
            .contains("Preflight exact production minio-backup service")
            .contains("source_object_count=1")
            .doesNotContain("$seedCommand")
            .doesNotContain("$cleanupCommand");

        assertThat(minioBackup)
            .contains("[BACKUP] MinIO source objects:")
            .contains("MinIO source bucket is empty before snapshot copy")
            .contains("mc cp --recursive \"primary/${bucket}/\"");
    }
    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private String section(String content, String startMarker, String endMarker) {
        int start = content.indexOf(startMarker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = content.indexOf(endMarker, start + startMarker.length());
        if (end < 0) {
            end = content.length();
        }
        return content.substring(start, end);
    }
}
