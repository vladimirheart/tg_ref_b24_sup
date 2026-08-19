package com.example.panel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.BotSqliteDataSourceProperties;
import com.example.panel.config.ClientsSqliteDataSourceProperties;
import com.example.panel.config.KnowledgeSqliteDataSourceProperties;
import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SettingsSqliteDataSourceProperties;
import com.example.panel.repository.ChannelRepository;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class DatabaseBootstrapServiceRuntimeModeTest {

    @TempDir
    Path tempDir;

    @Test
    void runSkipsCompatibilityBootstrapOutsideSqliteMode() {
        SqliteSchemaBootstrapSupport schemaBootstrapSupport = mock(SqliteSchemaBootstrapSupport.class);
        BotDatabaseRegistry botDatabaseRegistry = mock(BotDatabaseRegistry.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);

        DatabaseBootstrapService service = new DatabaseBootstrapService(
            botProperties(),
            clientsProperties(),
            knowledgeProperties(),
            objectsProperties(),
            settingsProperties(),
            channelRepository,
            botDatabaseRegistry,
            botProcessProperties(),
            schemaBootstrapSupport,
            new PanelDatabaseRuntimeMode(new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana"))
        );

        service.run(null);

        verify(schemaBootstrapSupport, never()).initializeSchema(any(), anyList(), any());
        verify(botDatabaseRegistry, never()).ensureSettingsSchema();
        verify(channelRepository, never()).findAll();
    }

    @Test
    void runBootstrapsSharedBotRuntimeDatabaseInSqliteMode() {
        SqliteSchemaBootstrapSupport schemaBootstrapSupport = mock(SqliteSchemaBootstrapSupport.class);
        BotDatabaseRegistry botDatabaseRegistry = mock(BotDatabaseRegistry.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        when(channelRepository.findAll()).thenReturn(List.of());

        DatabaseBootstrapService service = new DatabaseBootstrapService(
            botProperties(),
            clientsProperties(),
            knowledgeProperties(),
            objectsProperties(),
            settingsProperties(),
            channelRepository,
            botDatabaseRegistry,
            botProcessProperties(),
            schemaBootstrapSupport,
            new PanelDatabaseRuntimeMode(new MockEnvironment())
        );

        service.run(null);

        verify(schemaBootstrapSupport).initializeSchema(any(), anyList(), eq("bot_runtime.db"));
        verify(botDatabaseRegistry).ensureSettingsSchema();
        verify(channelRepository, never()).findAll();
    }

    private BotProcessProperties botProcessProperties() {
        BotProcessProperties properties = new BotProcessProperties();
        properties.setDatabaseDir(tempDir.resolve("bot_databases").toString());
        return properties;
    }

    private BotSqliteDataSourceProperties botProperties() {
        BotSqliteDataSourceProperties properties = new BotSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("bot_runtime.db").toString());
        return properties;
    }

    private ClientsSqliteDataSourceProperties clientsProperties() {
        ClientsSqliteDataSourceProperties properties = new ClientsSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("clients.db").toString());
        return properties;
    }

    private KnowledgeSqliteDataSourceProperties knowledgeProperties() {
        KnowledgeSqliteDataSourceProperties properties = new KnowledgeSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("knowledge_base.db").toString());
        return properties;
    }

    private ObjectsSqliteDataSourceProperties objectsProperties() {
        ObjectsSqliteDataSourceProperties properties = new ObjectsSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("objects.db").toString());
        return properties;
    }

    private SettingsSqliteDataSourceProperties settingsProperties() {
        SettingsSqliteDataSourceProperties properties = new SettingsSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("settings.db").toString());
        return properties;
    }
}
