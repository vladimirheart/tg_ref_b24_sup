package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotProcessLifecycleContractTest {

    @TempDir
    Path tempDir;

    @Test
    void startAndStopRespectLifecycleContractWithRunnableJar() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Files.createDirectories(botWorkingDir);
        Path executableJar = botWorkingDir.resolve("dist").resolve("bot-telegram-runtime.jar");
        Files.createDirectories(executableJar.getParent());
        createRunnableProbeJar(executableJar);

        BotProcessProperties properties = new BotProcessProperties();
        properties.setLaunchMode("auto");
        properties.setExecutableJars(Map.of("bot-telegram", "dist/bot-telegram-runtime.jar"));
        properties.setStartupReadinessTimeout(Duration.ofSeconds(5));
        properties.setStartupPollInterval(Duration.ofMillis(50));

        SqliteDataSourceProperties sqliteProperties = new SqliteDataSourceProperties();
        sqliteProperties.setPath(tempDir.resolve("tickets.db").toString());

        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        IntegrationNetworkService integrationNetworkService = new IntegrationNetworkService(sharedConfigService, new ObjectMapper());
        BotRuntimeContractService contractService = new BotRuntimeContractService(
            sqliteProperties,
            properties,
            integrationNetworkService,
            new ObjectMapper()
        );

        LifecycleTestBotProcessService service = new LifecycleTestBotProcessService(
            sharedConfigService,
            properties,
            contractService,
            botWorkingDir
        );

        Channel channel = new Channel();
        channel.setId(310L);
        channel.setPlatform("telegram");
        channel.setToken("telegram-test-token");
        channel.setBotUsername("probe_bot");
        channel.setSupportChatId("0");

        BotProcessService.BotProcessStatus startStatus = service.start(channel);

        assertThat(startStatus.running()).isTrue();
        assertThat(service.status(310L).running()).isTrue();
        assertThat(Files.exists(tempDir.resolve("run").resolve("bot-310.pid"))).isTrue();

        BotProcessService.BotProcessStatus stopStatus = service.stop(310L);

        assertThat(stopStatus.message()).isEqualTo("stopped");
        assertThat(service.status(310L).running()).isFalse();
        assertThat(Files.exists(tempDir.resolve("run").resolve("bot-310.pid"))).isFalse();
    }

    private void createRunnableProbeJar(Path jarPath) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, BotRuntimeLifecycleProbeApp.class.getName());
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            addClassEntry(jarOutputStream, BotRuntimeLifecycleProbeApp.class);
        }
    }

    private void addClassEntry(JarOutputStream jarOutputStream, Class<?> clazz) throws Exception {
        String entryName = clazz.getName().replace('.', '/') + ".class";
        JarEntry entry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(entry);
        try (InputStream inputStream = clazz.getClassLoader().getResourceAsStream(entryName)) {
            assertThat(inputStream).isNotNull();
            inputStream.transferTo(jarOutputStream);
        }
        jarOutputStream.closeEntry();
    }

    private static final class LifecycleTestBotProcessService extends BotProcessService {

        private final Path botWorkingDir;

        private LifecycleTestBotProcessService(SharedConfigService sharedConfigService,
                                               BotProcessProperties botProcessProperties,
                                               BotRuntimeContractService botRuntimeContractService,
                                               Path botWorkingDir) {
            super(sharedConfigService, botProcessProperties, botRuntimeContractService);
            this.botWorkingDir = botWorkingDir;
        }

        @Override
        Path resolveBotWorkingDir() {
            return botWorkingDir;
        }
    }
}
