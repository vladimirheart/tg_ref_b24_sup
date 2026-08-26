package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupSettingsServiceTest {

    @TempDir
    Path tempDir;

    private BackupSettingsService serviceFor(Path file) {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.resolvePath("backup.properties")).thenReturn(file);
        return new BackupSettingsService(sharedConfigService);
    }

    @Test
    void savesAndLoadsAdminManagedBackupPolicy() throws Exception {
        Path file = tempDir.resolve("backup.properties");
        BackupSettingsService service = serviceFor(file);

        Map<String, Object> saved = service.save(Map.ofEntries(
                Map.entry("destination_path", "Z:\\IguanaBackup"),
                Map.entry("external_failure_domain", true),
                Map.entry("postgres_retention_days", 45),
                Map.entry("minio_retention_days", 21),
                Map.entry("manual_mode", "custom"),
                Map.entry("custom_components", List.of("postgres", "shared-config", "static-js")),
                Map.entry("restore_components", List.of("postgres", "shared-config")),
                Map.entry("critical_enabled", true),
                Map.entry("critical_frequency", "daily"),
                Map.entry("critical_time", "02:30"),
                Map.entry("critical_weekday", "MON"),
                Map.entry("full_enabled", true),
                Map.entry("full_frequency", "weekly"),
                Map.entry("full_time", "03:15"),
                Map.entry("full_weekday", "SUN")
        ));

        assertThat(saved)
                .containsEntry("destination_path", "Z:\\IguanaBackup")
                .containsEntry("external_failure_domain", true)
                .containsEntry("archive_format", "tar.gz")
                .containsEntry("manual_mode", "custom")
                .containsEntry("critical_enabled", true)
                .containsEntry("full_enabled", true);

        assertThat(saved.get("custom_components"))
                .isEqualTo(List.of("postgres", "shared-config", "static-js"));
        assertThat(service.load()).isEqualTo(saved);

        String raw = Files.readString(file);
        assertThat(raw)
                .contains("IGUANA_BACKUP_DESTINATION_DIR=Z:\\IguanaBackup")
                .contains("IGUANA_BACKUP_ARCHIVE_FORMAT=tar.gz")
                .contains("IGUANA_BACKUP_CRITICAL_FREQUENCY=daily")
                .contains("IGUANA_BACKUP_FULL_FREQUENCY=weekly");
    }

    @Test
    void defaultsAreConservativeWhenPolicyFileDoesNotExist() {
        BackupSettingsService service = serviceFor(tempDir.resolve("missing.properties"));

        assertThat(service.load())
                .containsEntry("destination_path", "")
                .containsEntry("external_failure_domain", false)
                .containsEntry("archive_format", "tar.gz")
                .containsEntry("manual_mode", "critical")
                .containsEntry("critical_enabled", false)
                .containsEntry("full_enabled", false)
                .containsEntry("postgres_retention_days", 30)
                .containsEntry("minio_retention_days", 14);
    }

    @Test
    void rejectsBadPathScheduleAndComponents() {
        BackupSettingsService service = serviceFor(tempDir.resolve("backup.properties"));

        assertThatThrownBy(() -> service.save(Map.of(
                "destination_path", "Z:\\backup\ninjected=true"
        ))).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.save(Map.of(
                "destination_path", "Z:\\backup",
                "critical_time", "25:00"
        ))).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.save(Map.of(
                "destination_path", "Z:\\backup",
                "custom_components", List.of("postgres", "unknown-component")
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
