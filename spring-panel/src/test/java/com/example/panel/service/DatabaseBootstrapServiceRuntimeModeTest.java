package com.example.panel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.panel.config.BotSqliteDataSourceProperties;
import com.example.panel.config.ClientsSqliteDataSourceProperties;
import com.example.panel.config.KnowledgeSqliteDataSourceProperties;
import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class DatabaseBootstrapServiceRuntimeModeTest {

    @TempDir
    Path tempDir;

    @Test
    void runSkipsCompatibilityBootstrapOutsideSqliteMode() {
        SqliteSchemaBootstrapSupport schemaBootstrapSupport = mock(SqliteSchemaBootstrapSupport.class);

        DatabaseBootstrapService service = new DatabaseBootstrapService(
            botProperties(),
            clientsProperties(),
            knowledgeProperties(),
            objectsProperties(),
            schemaBootstrapSupport,
            new PanelDatabaseRuntimeMode(new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana"))
        );

        service.run(null);

        verify(schemaBootstrapSupport, never()).initializeSchema(any(), anyList(), any());
    }

    @Test
    void runBootstrapsSharedBotRuntimeDatabaseInSqliteMode() {
        SqliteSchemaBootstrapSupport schemaBootstrapSupport = mock(SqliteSchemaBootstrapSupport.class);

        DatabaseBootstrapService service = new DatabaseBootstrapService(
            botProperties(),
            clientsProperties(),
            knowledgeProperties(),
            objectsProperties(),
            schemaBootstrapSupport,
            new PanelDatabaseRuntimeMode(new MockEnvironment())
        );

        service.run(null);

        verify(schemaBootstrapSupport).initializeSchema(any(), anyList(), eq("bot_runtime.db"));
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
}
