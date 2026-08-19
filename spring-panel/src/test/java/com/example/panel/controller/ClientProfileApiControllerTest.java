package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ClientPhoneRepository;
import com.example.panel.repository.ClientStatusRepository;
import com.example.panel.service.BotDatabaseRegistry;
import com.example.panel.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class ClientProfileApiControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveCompatibilityBotDatabasePathSkipsRegistryOutsideSqliteMode() throws Exception {
        BotDatabaseRegistry botDatabaseRegistry = mock(BotDatabaseRegistry.class);
        ClientProfileApiController controller = controller(
            botDatabaseRegistry,
            new PanelDatabaseRuntimeMode(
                new MockEnvironment()
                    .withProperty("app.datasource.mode", "postgresql")
                    .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")
            )
        );

        assertThat(controller.resolveCompatibilityBotDatabasePath(17L)).isEmpty();
        verifyNoInteractions(botDatabaseRegistry);
    }

    @Test
    void resolveCompatibilityBotDatabasePathReturnsExistingSqlitePathInSqliteMode() throws Exception {
        Path sqlitePath = Files.createFile(tempDir.resolve("bot-17.db"));
        BotDatabaseRegistry botDatabaseRegistry = mock(BotDatabaseRegistry.class);
        when(botDatabaseRegistry.resolveBotDatabasePath(17L)).thenReturn(sqlitePath);
        ClientProfileApiController controller = controller(
            botDatabaseRegistry,
            new PanelDatabaseRuntimeMode(new MockEnvironment())
        );

        assertThat(controller.resolveCompatibilityBotDatabasePath(17L)).contains(sqlitePath);
    }

    @Test
    void resolveCompatibilityBotDatabasePathReturnsEmptyWhenSqliteFileIsMissing() throws Exception {
        Path sqlitePath = tempDir.resolve("missing-bot-17.db");
        BotDatabaseRegistry botDatabaseRegistry = mock(BotDatabaseRegistry.class);
        when(botDatabaseRegistry.resolveBotDatabasePath(17L)).thenReturn(sqlitePath);
        ClientProfileApiController controller = controller(
            botDatabaseRegistry,
            new PanelDatabaseRuntimeMode(new MockEnvironment())
        );

        assertThat(controller.resolveCompatibilityBotDatabasePath(17L)).isEmpty();
    }

    private ClientProfileApiController controller(BotDatabaseRegistry botDatabaseRegistry,
                                                  PanelDatabaseRuntimeMode databaseRuntimeMode) throws Exception {
        return new ClientProfileApiController(
            mock(JdbcTemplate.class),
            mock(ClientStatusRepository.class),
            mock(ClientPhoneRepository.class),
            botDatabaseRegistry,
            mock(ChannelRepository.class),
            new ObjectMapper(),
            mock(NotificationService.class),
            databaseRuntimeMode,
            tempDir.resolve("avatars").toString()
        );
    }
}
