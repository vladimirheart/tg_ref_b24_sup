package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLifecycleBoundarySourceContractTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/example/panel");

    @Test
    void currentPostConstructHooksHaveAuditedScaleSemantics() throws IOException {
        assertSource("observability/ProductionReadinessObservationCache.java",
            "@PostConstruct", "RuntimeRole.WORKER", "RuntimeReplicaPolicy.PROCESS_LOCAL");
        assertSource("security/PanelSecurityRuntimeGuard.java",
            "@PostConstruct", "01-211 lifecycle: process-local security validation");
        assertSource("service/ChatAttachmentMetadataAvailabilityService.java",
            "@PostConstruct", "RuntimeRole.MIGRATOR", "RuntimeReplicaPolicy.SINGLETON");
        assertSource("service/MonitoringCredentialsCryptoService.java",
            "@PostConstruct", "MONITORING_CREDENTIALS_MASTER_KEY", "RuntimeRole.ALL");
        assertSource("service/OperatorNotificationWatcher.java",
            "@PostConstruct", "RuntimeRole.WORKER", "refreshSharedCursors();");
        assertSource("service/SidebarStatusWatcher.java",
            "@PostConstruct", "RuntimeRole.WEB", "RuntimeReplicaPolicy.PROCESS_LOCAL");
        assertSource("service/UiEventOutboxWatcher.java",
            "@PostConstruct", "RuntimeRole.WORKER", "checkpointService.readLongCursor(CHECKPOINT_KEY)");
    }

    @Test
    void applicationReadyHooksHaveExplicitRoleSemantics() throws IOException {
        assertSource("service/BotAutoStartService.java",
            "ApplicationReadyEvent", "@RuntimeWorkload(", "roles = {}");
        assertSource("service/RmsLicenseMonitoringService.java",
            "ApplicationReadyEvent", "RuntimeRole.ALL", "explicit roles use backend_ops_command");
        assertSource("config/PostgresRuntimeReadinessVerifier.java",
            "ApplicationReadyEvent", "RuntimeRole.WEB", "RuntimeRole.WORKER");

    }

@Test
void migrationExitRunsOnlyAfterSpringApplicationReadyLifecycleReturns() throws IOException {
    Path application = SOURCE_ROOT.resolve("PanelApplication.java");
    String applicationSource = Files.readString(application, StandardCharsets.UTF_8);

    assertThat(applicationSource)
        .contains("ConfigurableApplicationContext context = app.run(args);")
        .contains("runtimeRoleProperties.resolvedRole() == RuntimeRole.MIGRATOR")
        .contains("runtimeRoleProperties.isExitAfterMigration()")
        .contains("closing application context after ready publication")
        .contains("context.close();");

    assertThat(SOURCE_ROOT.resolve("runtime/RuntimeMigrationExitListener.java"))
        .as("Migrator shutdown must not close the context from inside ApplicationReadyEvent publication")
        .doesNotExist();
}
    @Test
    void manualExecutorInventoryIsExplicitAndCannotGrowSilently() throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            for (Path source : stream.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String content = Files.readString(source, StandardCharsets.UTF_8);
                if (content.contains("Executors.new")) {
                    actual.add(SOURCE_ROOT.relativize(source).toString().replace('\\', '/'));
                }
            }
        }

        assertThat(actual).containsExactlyInAnyOrder(
            "service/IikoApiMonitoringService.java",
            "service/IikoDepartmentLocationsSyncService.java",
            "service/NetBoxObjectPassportSyncService.java",
            "service/RmsLicenseMonitoringService.java"
        );
    }

    @Test
    void checkpointSchemaMutationIsCompatibilityOnly() throws IOException {
        assertSource("service/RuntimeWorkerCheckpointService.java",
            "RuntimeRole.ALL", "ensureSchema();", "RuntimeRoleProperties");
    }

    @Test
    void leasedCursorWatchersReloadSharedCheckpointInsideLease() throws IOException {
        assertSource("service/OperatorNotificationWatcher.java",
            "runtimeCoordinationService.runWithLease", "refreshSharedCursors();");
        assertSource("service/UiEventOutboxWatcher.java",
            "runtimeCoordinationService.runWithLease", "checkpointService.readLongCursor(CHECKPOINT_KEY)");
    }

    private void assertSource(String relativePath, String... markers) throws IOException {
        String content = Files.readString(SOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
        assertThat(content).as(relativePath).contains(markers);
    }
}
