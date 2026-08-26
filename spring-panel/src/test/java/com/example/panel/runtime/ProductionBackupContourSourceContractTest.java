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
            .contains("\n  files-backup:\n")
            .contains("\n  postgres-restore-target:\n")
            .contains("\n  minio-restore-target:\n")
            .contains("\n  files-restore-rehearsal:\n")
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
    void recoveryPackagesArePortableTarGzAndRestoreToIsolatedTargets() throws IOException {
        String postgresBackup = read("docker/backup/postgres-backup.sh");
        String postgresRestore = read("docker/backup/postgres-restore-rehearsal.sh");
        String minioBackup = read("docker/backup/minio-backup.sh");
        String minioRestore = read("docker/backup/minio-restore-rehearsal.sh");
        String filesBackup = read("docker/backup/files-backup.sh");
        String filesRestore = read("docker/backup/files-restore-rehearsal.sh");

        assertThat(postgresBackup)
            .contains("pg_dump")
            .contains("--format=custom")
            .contains("tar -czf")
            .contains("tar -tzf")
            .contains("base=\"iguana-postgres-${stamp}\"")
            .contains("${base}.tar.gz")
            .contains("sha256sum");

        assertThat(postgresRestore)
            .contains("iguana-postgres-*.tar.gz")
            .contains("tar -xzf")
            .contains("sha256sum -c")
            .contains("pg_restore")
            .contains(".iguana-restore-evidence.properties");

        assertThat(minioBackup)
            .contains("base=\"iguana-minio-${stamp}\"")
            .contains("${base}.tar.gz")
            .contains("An empty object bucket is a valid production state")
            .contains("if [ \"${source_objects}\" -gt 0 ]")
            .contains("mc cp --recursive")
            .contains("tar -czf")
            .contains("checksums.sha256");

        assertThat(minioRestore)
            .contains("iguana-minio-*.tar.gz")
            .contains("if [ \"${source_count}\" -gt 0 ]")
            .contains("minio-restore-target")
            .contains(".iguana-restore-evidence.properties");

        assertThat(filesBackup)
            .contains("shared-config")
            .contains("templates")
            .contains("static-js")
            .contains("static-css")
            .contains("backup.properties")
            .contains("*.json")
            .doesNotContain("monitoring-credentials.key")
            .contains("tar -czf");

        assertThat(filesRestore)
            .contains("IGUANA_BACKUP_RESTORE_COMPONENTS")
            .contains("tar -xzf")
            .contains("sha256sum -c")
            .contains("/restore-work/package");
    }

    @Test
    void helpersSupportCriticalFullCustomAndSelectiveRestore() throws IOException {
        String backupPs = read("scripts/docker-production-backup.ps1");
        String backupSh = read("scripts/docker-production-backup.sh");
        String runnerPs = read("scripts/run-backup-policy.ps1");
        String runnerSh = read("scripts/run-backup-policy.sh");

        assertThat(backupPs)
            .contains("critical")
            .contains("custom")
            .contains("RestoreComponents")
            .contains("files-backup")
            .contains("files-restore-rehearsal")
            .contains("IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN");

        assertThat(backupSh)
            .contains("--mode")
            .contains("--restore-components")
            .contains("files-backup")
            .contains("files-restore-rehearsal");

        assertThat(runnerPs)
            .contains("Get-Env \"IGUANA_BACKUP_${Prefix}_ENABLED\"")
            .contains("Get-Env \"IGUANA_BACKUP_${Prefix}_FREQUENCY\"")
            .contains("Get-Env \"IGUANA_BACKUP_${Prefix}_TIME\"")
            .contains("Is-Due \"CRITICAL\"")
            .contains("Is-Due \"FULL\"")
            .contains("-Action backup -Mode critical")
            .contains("-Action full -Mode full");

        assertThat(runnerSh)
            .contains("--action backup --mode critical")
            .contains("--action full --mode full");
    }

    @Test
    void backupPolicyIsAdminManagedAndPreparedForPortableArchiveRuntime() throws IOException {
        String settingsPage = read("spring-panel/src/main/resources/templates/settings/index.html");
        String runtime = read("spring-panel/src/main/resources/static/js/settings-backup-runtime.js");
        String service = read("spring-panel/src/main/java/com/example/panel/service/BackupSettingsService.java");
        String psLibrary = read("scripts/lib/backup-config.ps1");

        assertThat(settingsPage)
            .contains("data-settings-overview-target=\"backupSettingsModal\"")
            .contains("id=\"backupCriticalEnabled\"")
            .contains("id=\"backupFullEnabled\"")
            .contains("value=\"tar.gz\"");

        assertThat(runtime)
            .contains("critical_frequency")
            .contains("full_frequency")
            .contains("custom_components")
            .contains("restore_components");

        assertThat(service)
            .contains("IGUANA_BACKUP_ARCHIVE_FORMAT")
            .contains("\"tar.gz\"")
            .contains("\"shared-config\"")
            .contains("\"templates\"")
            .contains("\"static-js\"");

        assertThat(psLibrary)
            .contains("Import-IguanaBackupSettings")
            .contains("IGUANA_BACKUP_FULL_WEEKDAY");
    }

    @Test
    void backupReadinessTracksPortablePackagesIncludingFiles() throws IOException {
        String service = read("spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringService.java");

        assertThat(service)
            .contains("iguana-postgresql-production-backup")
            .contains("iguana-minio-production-backup")
            .contains("iguana-files-production-backup")
            .contains("packages")
            .contains("iguana-postgres-*.tar.gz")
            .contains("iguana-minio-*.tar.gz")
            .contains("iguana-files-*.tar.gz");
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
    void backupSmokeCoversSeededAndEmptyMinioPackages() throws IOException {
        String smoke = read("scripts/docker-production-backup-smoke.ps1");
        assertThat(smoke)
            .contains("Seeded MinIO tar.gz cycle")
            .contains("Empty MinIO tar.gz cycle")
            .contains("objects=1")
            .contains("objects=0")
            .contains("iguana-postgres-*.tar.gz")
            .contains("iguana-minio-*.tar.gz")
            .contains("iguana-files-*.tar.gz");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private String section(String content, String startMarker, String endMarker) {
        int start = content.indexOf(startMarker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = content.indexOf(endMarker, start + startMarker.length());
        if (end < 0) { end = content.length(); }
        return content.substring(start, end);
    }
}
