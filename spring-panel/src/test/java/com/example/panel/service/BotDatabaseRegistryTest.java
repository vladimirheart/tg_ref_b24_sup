package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.BotSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class BotDatabaseRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureSettingsSchemaSkipsBootstrapOutsideSqliteMode() {
        SqliteSchemaBootstrapSupport schemaBootstrapSupport = mock(SqliteSchemaBootstrapSupport.class);

        BotDatabaseRegistry registry = new BotDatabaseRegistry(
                botProcessProperties(),
                botSqliteProperties(),
                settingsSqliteProperties(),
                schemaBootstrapSupport,
                new PanelDatabaseRuntimeMode(externalEnvironment())
        );

        registry.ensureSettingsSchema();

        verify(schemaBootstrapSupport, never()).initializeSchema(org.mockito.ArgumentMatchers.any(), anyList(), eq("settings.db"));
    }

    @Test
    void ensureSettingsSchemaKeepsLegacyBootstrapInSqliteMode() {
        SqliteSchemaBootstrapSupport schemaBootstrapSupport = mock(SqliteSchemaBootstrapSupport.class);

        BotDatabaseRegistry registry = new BotDatabaseRegistry(
                botProcessProperties(),
                botSqliteProperties(),
                settingsSqliteProperties(),
                schemaBootstrapSupport,
                new PanelDatabaseRuntimeMode(new MockEnvironment())
        );

        registry.ensureSettingsSchema();

        verify(schemaBootstrapSupport).initializeSchema(org.mockito.ArgumentMatchers.any(DataSource.class), anyList(), eq("settings.db"));
    }

    @Test
    void ensureBotDatabaseSkipsPerChannelSqliteBootstrapOutsideSqliteMode() {
        SqliteSchemaBootstrapSupport schemaBootstrapSupport = mock(SqliteSchemaBootstrapSupport.class);
        BotProcessProperties botProcessProperties = botProcessProperties();

        BotDatabaseRegistry registry = new BotDatabaseRegistry(
                botProcessProperties,
                botSqliteProperties(),
                settingsSqliteProperties(),
                schemaBootstrapSupport,
                new PanelDatabaseRuntimeMode(externalEnvironment())
        );

        Path expected = botProcessProperties.resolveDatabaseDir().resolve("bot-7.db").toAbsolutePath().normalize();

        Path resolved = registry.ensureBotDatabase(7L, "telegram");

        assertThat(resolved).isEqualTo(expected);
        assertThat(Files.exists(expected)).isFalse();
        verify(schemaBootstrapSupport, never()).initializeSchema(org.mockito.ArgumentMatchers.any(), anyList(), eq(expected.toString()));
    }

    private BotProcessProperties botProcessProperties() {
        BotProcessProperties properties = new BotProcessProperties();
        properties.setDatabaseDir(tempDir.resolve("bot_databases").toString());
        return properties;
    }

    private BotSqliteDataSourceProperties botSqliteProperties() {
        BotSqliteDataSourceProperties properties = new BotSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("bot_runtime.db").toString());
        return properties;
    }

    private com.example.panel.config.SettingsSqliteDataSourceProperties settingsSqliteProperties() {
        com.example.panel.config.SettingsSqliteDataSourceProperties properties =
                new com.example.panel.config.SettingsSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("settings.db").toString());
        return properties;
    }

    private MockEnvironment externalEnvironment() {
        return new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana");
    }
}
