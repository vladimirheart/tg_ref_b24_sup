package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeStartupOwnershipSourceContractTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/example/panel");

    @Test
    void everyPanelApplicationRunnerClassHasExplicitRuntimeOwnership() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            if (!content.contains("implements ApplicationRunner")) {
                continue;
            }
            if (!content.contains("@RuntimeWorkload(")) {
                violations.add(SOURCE_ROOT.relativize(source).toString());
            }
        }

        assertThat(violations)
            .as("Every panel ApplicationRunner must declare an explicit runtime owner")
            .isEmpty();
    }

    @Test
    void panelApplicationRunnerFactoriesAreIndividuallyClassified() throws IOException {
        Path panelApplication = SOURCE_ROOT.resolve("PanelApplication.java");
        String content = Files.readString(panelApplication, StandardCharsets.UTF_8);

        long runnerFactories = content.lines()
            .filter(line -> line.contains("ApplicationRunner "))
            .count();
        long workloadAnnotations = content.lines()
            .filter(line -> line.contains("@RuntimeWorkload("))
            .count();

        assertThat(runnerFactories).isGreaterThan(0);
        assertThat(workloadAnnotations)
            .as("PanelApplication @Bean ApplicationRunner factories must be role-classified")
            .isGreaterThanOrEqualTo(runnerFactories);
    }

    @Test
    void migrationAndLegacyStartupMutationsAreOwnedByMigratorOrCompatibilityRole() throws IOException {
        assertMigrator("service/LegacySqliteImportService.java");
        assertMigrator("service/PostgresImportedDataReconciliationService.java");
        assertMigrator("service/PostgresLegacyCriticalDataRecoveryService.java");
        assertMigrator("service/LegacyBotShardConsolidationService.java");
        assertMigrator("service/LegacyMonitoringHistoryCompactionService.java");
        assertMigrator("service/RmsMonitoringSeedImportService.java");
        assertMigrator("service/LocationsSharedConfigRepairService.java");

        assertCompatibilityOnly("service/DatabaseBootstrapService.java");
        assertCompatibilityOnly("service/MonitoringDatabaseBootstrapService.java");
    }

    @Test
    void flywayOwnerContractExcludesWebAndWorker() throws IOException {
        String source = Files.readString(
            SOURCE_ROOT.resolve("config/FlywayConfig.java"),
            StandardCharsets.UTF_8
        );

        assertThat(source)
            .contains("RuntimeRole.WEB")
            .contains("RuntimeRole.WORKER")
            .contains("flyway.migrate()");
    }

    private void assertMigrator(String relativePath) throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
        assertThat(source)
            .as(relativePath)
            .contains("@RuntimeWorkload(")
            .contains("RuntimeRole.MIGRATOR")
            .contains("RuntimeReplicaPolicy.SINGLETON");
    }

    private void assertCompatibilityOnly(String relativePath) throws IOException {
        String source = Files.readString(SOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
        assertThat(source)
            .as(relativePath)
            .contains("@RuntimeWorkload(")
            .contains("roles = {}")
            .contains("RuntimeReplicaPolicy.SINGLETON");
    }

    private List<Path> javaSources() throws IOException {
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            return stream
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .toList();
        }
    }
}
