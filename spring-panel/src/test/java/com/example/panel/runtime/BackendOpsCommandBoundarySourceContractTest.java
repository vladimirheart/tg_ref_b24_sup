package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackendOpsCommandBoundarySourceContractTest {

    private static final Path SOURCE_ROOT =
        Path.of("src/main/java/com/example/panel");
    private static final Path RESOURCE_ROOT =
        Path.of("src/main/resources");

    @Test
    void migrationsOwnDurableBackendCommandLedger() throws IOException {
        assertFile(
            RESOURCE_ROOT.resolve(
                "db/migration/postgresql/V40__backend_ops_command.sql"
            ),
            "backend_ops_command",
            "active_key",
            "UNIQUE",
            "claimed_by",
            "heartbeat_at",
            "progress_percent"
        );
        assertFile(
            RESOURCE_ROOT.resolve(
                "db/migration/sqlite/V51__backend_ops_command.sql"
            ),
            "backend_ops_command",
            "active_key",
            "UNIQUE",
            "claimed_by",
            "heartbeat_at",
            "progress_percent"
        );
    }

    @Test
    void dispatcherIsWorkerOwnedAndDatabaseClaimed() throws IOException {
        assertFile(
            SOURCE_ROOT.resolve("runtime/RuntimeReplicaPolicy.java"),
            "DATABASE_CLAIMED"
        );
        assertFile(
            SOURCE_ROOT.resolve("service/BackendOpsCommandDispatcher.java"),
            "RuntimeRole.WORKER",
            "RuntimeReplicaPolicy.DATABASE_CLAIMED",
            "@Scheduled(",
            "claimNextForLane(",
            "executeBackendOpsLicenseRefresh",
            "executeBackendOpsRefresh",
            "executeBackendOpsSync"
        );
    }

    @Test
    void explicitRoleRequestPathsUseDurableCommandBoundary() throws IOException {
        assertDurableService(
            "service/RmsLicenseMonitoringService.java",
            "RMS_LICENSE_REFRESH",
            "RMS_NETWORK_REFRESH"
        );
        assertDurableService(
            "service/IikoApiMonitoringService.java",
            "IIKO_API_REFRESH"
        );
        assertDurableService(
            "service/IikoDepartmentLocationsSyncService.java",
            "IIKO_LOCATIONS_SYNC"
        );
        assertDurableService(
            "service/NetBoxObjectPassportSyncService.java",
            "NETBOX_PASSPORTS_SYNC"
        );
    }

    @Test
    void legacyProcessLocalExecutorsRemainCompatibilityFallbackOnly() throws IOException {
        for (String relativePath : new String[] {
            "service/RmsLicenseMonitoringService.java",
            "service/IikoApiMonitoringService.java",
            "service/IikoDepartmentLocationsSyncService.java",
            "service/NetBoxObjectPassportSyncService.java"
        }) {
            String content = Files.readString(
                SOURCE_ROOT.resolve(relativePath),
                StandardCharsets.UTF_8
            );
            assertThat(content)
                .as(relativePath)
                .contains("useDurableBackendOps()")
                .contains("RuntimeRole.ALL");
        }
    }

    private void assertDurableService(String relativePath,
                                      String... commandMarkers) throws IOException {
        String content = Files.readString(
            SOURCE_ROOT.resolve(relativePath),
            StandardCharsets.UTF_8
        );
        assertThat(content)
            .as(relativePath)
            .contains("BackendOpsCommandService")
            .contains("useDurableBackendOps()")
            .contains("enqueueExclusive(");
        assertThat(content).contains(commandMarkers);
    }

    private void assertFile(Path path,
                            String... markers) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(content).as(path.toString()).contains(markers);
    }
}
