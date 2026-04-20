package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotRuntimeContractServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void describeWarnsWhenAutoModeFallsBackToMaven() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(15L);
        channel.setPlatform("telegram");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, tempDir.resolve("java-bot"));

        assertThat(contract.resolvedLauncherKind()).isEqualTo("maven");
        assertThat(contract.warnings()).anyMatch(item -> item.contains("fallback на Maven"));
        assertThat(contract.requiredEnvironmentKeys()).contains("APP_DB_TICKETS", "TELEGRAM_BOT_TOKEN");
        assertThat(contract.readiness().timeoutMillis()).isEqualTo(45_000L);
    }

    @Test
    void describeUsesExplicitJarContractWhenConfiguredArtifactExists() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path jar = botWorkingDir.resolve("dist").resolve("bot-telegram-runtime.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "fake");

        BotRuntimeContractService service = createService("auto", Map.of(
            "bot-telegram", "dist/bot-telegram-runtime.jar"
        ));
        Channel channel = new Channel();
        channel.setId(16L);
        channel.setPlatform("telegram");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, botWorkingDir);

        assertThat(contract.resolvedLauncherKind()).isEqualTo("jar");
        assertThat(contract.artifactSource()).isEqualTo("explicit-config");
        assertThat(contract.executableJarPath()).isEqualTo(jar.toAbsolutePath().normalize().toString());
        assertThat(contract.warnings()).isEmpty();
        assertThat(contract.production().readyForProduction()).isTrue();
        assertThat(contract.production().recommendedArtifactPath()).isEqualTo(jar.toAbsolutePath().normalize().toString());
        assertThat(contract.lifecycle().runningStatus()).isEqualTo("running");
    }

    @Test
    void buildEnvironmentIncludesPlatformSpecificContractForMax() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(17L);
        channel.setPlatform("max");
        channel.setSupportChatId("support-room");
        channel.setPlatformConfig("{\"secret\":\"max-secret\"}");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(1L, "max", "max", "token", true),
            tempDir.resolve("bot.log")
        );

        assertThat(env)
            .containsEntry("MAX_BOT_ENABLED", "true")
            .containsEntry("MAX_BOT_TOKEN", "token")
            .containsEntry("MAX_CHANNEL_ID", "17")
            .containsEntry("MAX_SUPPORT_CHAT_ID", "support-room")
            .containsEntry("MAX_WEBHOOK_SECRET", "max-secret")
            .containsEntry("SPRING_MAIN_WEB_APPLICATION_TYPE", "servlet");
    }

    @Test
    void describeMarksMavenFallbackAsNotProductionReady() {
        BotRuntimeContractService service = createService("maven", Map.of());
        Channel channel = new Channel();
        channel.setId(18L);
        channel.setPlatform("telegram");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, tempDir.resolve("java-bot"));

        assertThat(contract.production().readyForProduction()).isFalse();
        assertThat(contract.production().blockingReasons())
            .anyMatch(item -> item.contains("Maven launcher"))
            .anyMatch(item -> item.contains("launch-mode=maven"));
    }

    private BotRuntimeContractService createService(String launchMode, Map<String, String> executableJars) {
        SqliteDataSourceProperties sqliteProperties = new SqliteDataSourceProperties();
        sqliteProperties.setPath(tempDir.resolve("tickets.db").toString());
        BotProcessProperties properties = new BotProcessProperties();
        properties.setLaunchMode(launchMode);
        properties.setExecutableJars(executableJars);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        IntegrationNetworkService integrationNetworkService = new IntegrationNetworkService(sharedConfigService, new ObjectMapper());
        return new BotRuntimeContractService(sqliteProperties, properties, integrationNetworkService, new ObjectMapper());
    }
}
