package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class LegacyBotShardConsolidationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void runSkipsOutsidePostgresqlMode() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        LegacyBotShardConsolidationService service = new LegacyBotShardConsolidationService(
            dataSource,
            new PanelDatabaseRuntimeMode(new MockEnvironment().withProperty("app.datasource.mode", "sqlite")),
            botProcessProperties(tempDir.resolve("bot_databases"))
        );

        service.run(null);

        verifyNoInteractions(dataSource);
    }

    @Test
    void findShardFilesReturnsOnlyLegacyBotShardDatabases() throws Exception {
        Path shardDir = Files.createDirectories(tempDir.resolve("bot_databases"));
        Path first = Files.createFile(shardDir.resolve("bot-2.db"));
        Path second = Files.createFile(shardDir.resolve("bot-10.db"));
        Files.createFile(shardDir.resolve("bot-runtime.db"));
        Files.createFile(shardDir.resolve("notes.txt"));

        LegacyBotShardConsolidationService service = new LegacyBotShardConsolidationService(
            mock(DataSource.class),
            new PanelDatabaseRuntimeMode(new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")),
            botProcessProperties(shardDir)
        );

        List<Path> shardFiles = service.findShardFiles(shardDir);

        assertThat(shardFiles).containsExactly(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize());
    }

    private BotProcessProperties botProcessProperties(Path shardDir) {
        BotProcessProperties properties = new BotProcessProperties();
        properties.setDatabaseDir(shardDir.toString());
        return properties;
    }
}
