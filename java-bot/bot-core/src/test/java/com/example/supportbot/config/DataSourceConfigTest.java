package com.example.supportbot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class DataSourceConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveSqlitePathPrefersRicherWorkspaceRuntimeOverNearbyCompatibilityCopy() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path panelDir = workspaceRoot.resolve("spring-panel");
        Path javaBotDir = workspaceRoot.resolve("java-bot");
        Path botCoreDir = javaBotDir.resolve("bot-core");
        Files.createDirectories(panelDir);
        Files.createDirectories(botCoreDir);
        Files.createDirectories(workspaceRoot.resolve("ai-context"));

        Files.writeString(javaBotDir.resolve("panel_runtime.db"), "tiny");
        Files.writeString(workspaceRoot.resolve("panel_runtime.db"), "root-runtime-is-bigger");
        Path panelRuntime = Files.writeString(panelDir.resolve("panel_runtime.db"), "panel-runtime-is-the-richest-copy");

        Path resolved = DataSourceConfig.resolveSqlitePath("../panel_runtime.db", botCoreDir);

        assertEquals(panelRuntime.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void resolveSqlitePathWithoutConfiguredPathPrefersLargestExistingCandidate() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path panelDir = workspaceRoot.resolve("spring-panel");
        Path botCoreDir = workspaceRoot.resolve("java-bot").resolve("bot-core");
        Files.createDirectories(panelDir);
        Files.createDirectories(botCoreDir);
        Files.createDirectories(workspaceRoot.resolve(".git"));

        Files.writeString(botCoreDir.getParent().resolve("panel_runtime.db"), "tiny");
        Files.writeString(workspaceRoot.resolve("panel_runtime.db"), "root-runtime");
        Path panelRuntime = Files.writeString(panelDir.resolve("panel_runtime.db"), "panel-runtime-is-bigger-than-root");

        Path resolved = DataSourceConfig.resolveSqlitePath("", botCoreDir);

        assertEquals(panelRuntime.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void externalRuntimePropertiesOverrideLegacyDialectFlags() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect")
                .withProperty("spring.jpa.hibernate.ddl-auto", "create");

        DataSourceConfig.applyExternalRuntimeProperties(
                environment,
                new ExternalDatabaseSettings(
                        "jdbc:postgresql://localhost:5432/supportbot",
                        "bot",
                        "secret",
                        "org.postgresql.Driver",
                        "org.hibernate.dialect.PostgreSQLDialect",
                        "postgres"
                )
        );

        assertEquals("org.hibernate.dialect.PostgreSQLDialect", environment.getProperty("spring.jpa.database-platform"));
        assertEquals("none", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void sqliteRuntimePropertiesForceSqliteDialectFlags() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect")
                .withProperty("spring.jpa.hibernate.ddl-auto", "create-drop");

        DataSourceConfig.applySqliteRuntimeProperties(environment);

        assertEquals("org.hibernate.community.dialect.SQLiteDialect", environment.getProperty("spring.jpa.database-platform"));
        assertEquals("none", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
}
