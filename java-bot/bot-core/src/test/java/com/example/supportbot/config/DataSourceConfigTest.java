package com.example.supportbot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void workerModeUsesTemporarySqliteScaffoldAndIgnoresCanonicalPostgresCredentials() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("support-bot.database.mode", "worker")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.local:5432/iguana")
                .withProperty("spring.datasource.username", "iguana")
                .withProperty("spring.datasource.password", "secret");

        javax.sql.DataSource dataSource = new DataSourceConfig().dataSource(environment);
        String workerPath = environment.getProperty("support-bot.database.worker-path");

        assertNotNull(workerPath);
        assertTrue(Path.of(workerPath).isAbsolute());
        assertTrue(Path.of(workerPath).getFileName().toString().startsWith("iguana-worker-runtime-"));
        assertEquals("org.hibernate.community.dialect.SQLiteDialect", environment.getProperty("spring.jpa.database-platform"));

        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery(
                 "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'channels'"
             )) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:sqlite:"));
            assertTrue(result.next());
            assertEquals(0L, result.getLong(1));
        }

        Files.deleteIfExists(Path.of(workerPath));
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
