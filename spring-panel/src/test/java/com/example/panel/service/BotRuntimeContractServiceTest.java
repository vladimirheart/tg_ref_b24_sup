package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void describeWarnsWhenAutoModeUsesTargetScanArtifact() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path jar = botWorkingDir.resolve("bot-telegram").resolve("target").resolve("bot-telegram-0.0.1-SNAPSHOT.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "fake");

        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(26L);
        channel.setPlatform("telegram");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, botWorkingDir);

        assertThat(contract.resolvedLauncherKind()).isEqualTo("jar");
        assertThat(contract.artifactSource()).isEqualTo("target-scan");
        assertThat(contract.warnings()).anyMatch(item -> item.contains("app.bots.executable-jars"));
        assertThat(contract.production().readyForProduction()).isFalse();
        assertThat(contract.production().blockingReasons()).anyMatch(item -> item.contains("target scan"));
    }

    @Test
    void describeWarnsWhenJarModeHasNoArtifact() {
        BotRuntimeContractService service = createService("jar", Map.of());
        Channel channel = new Channel();
        channel.setId(27L);
        channel.setPlatform("telegram");

        assertThatThrownBy(() -> service.describe(channel, tempDir.resolve("java-bot")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Не найден собранный jar");
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
    void buildEnvironmentIncludesPlatformSpecificContractForVk() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(19L);
        channel.setPlatform("vk");
        channel.setSupportChatId("ops-room");
        channel.setPlatformConfig("""
            {
              "group_id": 12345,
              "confirmation_token": "vk-confirm",
              "secret": "vk-secret"
            }
            """);

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(2L, "vk", "vk", "vk-token", true),
            tempDir.resolve("vk.log")
        );

        assertThat(env)
            .containsEntry("VK_BOT_ENABLED", "true")
            .containsEntry("VK_BOT_TOKEN", "vk-token")
            .containsEntry("VK_OPERATOR_CHAT_ID", "ops-room")
            .containsEntry("VK_GROUP_ID", "12345")
            .containsEntry("VK_WEBHOOK_ENABLED", "true")
            .containsEntry("VK_CONFIRMATION_TOKEN", "vk-confirm")
            .containsEntry("VK_WEBHOOK_SECRET", "vk-secret");
    }

    @Test
    void buildEnvironmentForVkOmitsOptionalWebhookKeysWhenPlatformConfigMissing() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(29L);
        channel.setPlatform("vk");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(4L, "vk", "vk", "vk-token", true),
            tempDir.resolve("vk-minimal.log")
        );

        assertThat(env)
            .containsEntry("VK_BOT_ENABLED", "true")
            .containsEntry("VK_BOT_TOKEN", "vk-token")
            .containsEntry("VK_OPERATOR_CHAT_ID", "0")
            .containsEntry("VK_WEBHOOK_ENABLED", "false")
            .doesNotContainKeys("VK_GROUP_ID", "VK_CONFIRMATION_TOKEN", "VK_WEBHOOK_SECRET");
    }

    @Test
    void buildEnvironmentForTelegramIncludesBaseAndProxyContract() {
        BotRuntimeContractService service = createService("auto", Map.of(), Map.of(
            "integration_network", Map.of(
                "bots", Map.of(
                    "mode", "proxy",
                    "proxy", Map.of(
                        "scheme", "http",
                        "host", "proxy.internal",
                        "port", 3128,
                        "username", "svc_bot",
                        "password", "pwd"
                    )
                )
            )
        ));
        Channel channel = new Channel();
        channel.setId(28L);
        channel.setPlatform("telegram");
        channel.setSupportChatId("ops-room");
        channel.setBotUsername("support_bot");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(3L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram.log")
        );

        assertThat(env)
            .containsEntry("APP_DB_TICKETS", tempDir.resolve("tickets.db").toString())
            .containsEntry("TELEGRAM_BOT_TOKEN", "tg-token")
            .containsEntry("TELEGRAM_BOT_USERNAME", "support_bot")
            .containsEntry("GROUP_CHAT_ID", "ops-room")
            .containsEntry("APP_NETWORK_MODE", "proxy")
            .containsEntry("HTTP_PROXY", "http://svc_bot:pwd@proxy.internal:3128");
        assertThat(env.get("JAVA_TOOL_OPTIONS"))
            .contains("-Dfile.encoding=UTF-8")
            .contains("-Dhttp.proxyHost=proxy.internal");
    }

    @Test
    void buildEnvironmentForTelegramIncludesVpnContractWithoutProxyVariables() {
        BotRuntimeContractService service = createService("auto", Map.of(), Map.of(
            "integration_network", Map.of(
                "bots", Map.of(
                    "mode", "vpn",
                    "vpn", Map.of(
                        "name", "corp-vpn",
                        "endpoint", "vpn.internal:7443"
                    )
                )
            )
        ));
        Channel channel = new Channel();
        channel.setId(30L);
        channel.setPlatform("telegram");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(5L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-vpn.log")
        );

        assertThat(env)
            .containsEntry("APP_NETWORK_MODE", "vpn")
            .containsEntry("APP_NETWORK_VPN_NAME", "corp-vpn")
            .doesNotContainKeys("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY");
    }

    @Test
    void buildEnvironmentForTelegramIncludesVlessProxyContract() {
        BotRuntimeContractService service = createService("auto", Map.of(), Map.of(
            "integration_network", Map.of(
                "bots", Map.of(
                    "mode", "proxy",
                    "proxy", Map.of(
                        "scheme", "vless",
                        "host", "vless.internal",
                        "port", 7443,
                        "token", "vless-token"
                    )
                )
            )
        ));
        Channel channel = new Channel();
        channel.setId(32L);
        channel.setPlatform("telegram");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(7L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-vless.log")
        );

        assertThat(env)
            .containsEntry("APP_NETWORK_MODE", "proxy")
            .containsEntry("APP_NETWORK_PROXY_SCHEME", "vless")
            .containsEntry("APP_NETWORK_PROXY_TOKEN", "vless-token")
            .containsEntry("ALL_PROXY", "vless://vless-token@vless.internal:7443")
            .containsEntry("all_proxy", "vless://vless-token@vless.internal:7443");
        assertThat(env.get("JAVA_TOOL_OPTIONS"))
            .contains("-Dfile.encoding=UTF-8")
            .contains("-DsocksProxyHost=vless.internal")
            .contains("-DsocksProxyPort=7443");
    }

    @Test
    void buildEnvironmentDefaultsTelegramSupportChatIdToZero() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(31L);
        channel.setPlatform("telegram");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(6L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-default.log")
        );

        assertThat(env)
            .containsEntry("GROUP_CHAT_ID", "0")
            .containsEntry("VK_BOT_ENABLED", "false")
            .containsEntry("MAX_BOT_ENABLED", "false");
    }

    @Test
    void describeResolvesVkBotModule() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(20L);
        channel.setPlatform("vk");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, tempDir.resolve("java-bot"));

        assertThat(contract.botModule()).isEqualTo("bot-vk");
        assertThat(contract.platform()).isEqualTo("vk");
        assertThat(contract.requiredEnvironmentKeys()).contains("VK_BOT_ENABLED", "VK_BOT_TOKEN", "VK_OPERATOR_CHAT_ID");
        assertThat(contract.optionalEnvironmentKeys()).contains("VK_GROUP_ID", "VK_CONFIRMATION_TOKEN", "VK_WEBHOOK_SECRET");
    }

    @Test
    void describeResolvesMaxBotModuleAndRequiredKeys() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(21L);
        channel.setPlatform("max");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, tempDir.resolve("java-bot"));

        assertThat(contract.botModule()).isEqualTo("bot-max");
        assertThat(contract.platform()).isEqualTo("max");
        assertThat(contract.requiredEnvironmentKeys()).contains("MAX_BOT_ENABLED", "MAX_BOT_TOKEN", "SERVER_PORT");
        assertThat(contract.optionalEnvironmentKeys()).contains("MAX_WEBHOOK_SECRET");
    }

    @Test
    void describeForTelegramIncludesIntegrationNetworkOptionalKeysWhenProxyRouteConfigured() {
        BotRuntimeContractService service = createService("auto", Map.of(), Map.of(
            "integration_network", Map.of(
                "bots", Map.of(
                    "mode", "proxy",
                    "proxy", Map.of(
                        "scheme", "http",
                        "host", "proxy.internal",
                        "port", 3128,
                        "username", "svc_bot",
                        "password", "pwd"
                    )
                )
            )
        ));
        Channel channel = new Channel();
        channel.setId(33L);
        channel.setPlatform("telegram");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, tempDir.resolve("java-bot"));

        assertThat(contract.optionalEnvironmentKeys())
            .contains("APP_NETWORK_MODE", "APP_NETWORK_PROXY_HOST", "HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "JAVA_TOOL_OPTIONS");
    }

    @Test
    void describeForTelegramIncludesVpnOptionalKeysWhenVpnRouteConfigured() {
        BotRuntimeContractService service = createService("auto", Map.of(), Map.of(
            "integration_network", Map.of(
                "bots", Map.of(
                    "mode", "vpn",
                    "vpn", Map.of(
                        "name", "corp-vpn",
                        "endpoint", "vpn.internal:7443"
                    )
                )
            )
        ));
        Channel channel = new Channel();
        channel.setId(34L);
        channel.setPlatform("telegram");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, tempDir.resolve("java-bot"));

        assertThat(contract.optionalEnvironmentKeys()).contains("APP_NETWORK_MODE", "APP_NETWORK_VPN_NAME");
    }

    @Test
    void resolveBotModuleFallsBackToTelegramForUnknownPlatform() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setPlatform("whatsapp");

        assertThat(service.resolveBotModule(channel)).isEqualTo("bot-telegram");
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
        return createService(launchMode, executableJars, Map.of());
    }

    private BotRuntimeContractService createService(String launchMode,
                                                    Map<String, String> executableJars,
                                                    Map<String, Object> settings) {
        SqliteDataSourceProperties sqliteProperties = new SqliteDataSourceProperties();
        sqliteProperties.setPath(tempDir.resolve("tickets.db").toString());
        BotProcessProperties properties = new BotProcessProperties();
        properties.setLaunchMode(launchMode);
        properties.setExecutableJars(executableJars);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(settings);
        IntegrationNetworkService integrationNetworkService = new IntegrationNetworkService(sharedConfigService, new ObjectMapper());
        return new BotRuntimeContractService(sqliteProperties, properties, integrationNetworkService, new ObjectMapper());
    }
}
