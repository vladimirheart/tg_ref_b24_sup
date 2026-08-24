package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.BotSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

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
        assertThat(contract.requiredEnvironmentKeys()).contains("APP_DB_BOT_RUNTIME", "SUPPORT_BOT_DATABASE_PATH", "TELEGRAM_BOT_TOKEN");
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
        assertThat(contract.warnings()).anyMatch(item -> item.contains("SQLite runtime contract"));
        assertThat(contract.production().readyForProduction()).isFalse();
        assertThat(contract.production().blockingReasons())
            .anyMatch(item -> item.contains("SQLite compatibility mode"))
            .anyMatch(item -> item.contains("app.integration.transport.mode=rabbitmq"));
        assertThat(contract.production().recommendedArtifactPath()).isEqualTo(jar.toAbsolutePath().normalize().toString());
        assertThat(contract.lifecycle().runningStatus()).isEqualTo("running");
    }

    @Test
    void describeMarksExplicitJarAsProductionReadyInExternalPostgresMode() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path jar = botWorkingDir.resolve("dist").resolve("bot-telegram-runtime.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "fake");

        BotRuntimeContractService service = createService(
            "auto",
            Map.of("bot-telegram", "dist/bot-telegram-runtime.jar"),
            Map.of(),
            Map.of(
                "app.datasource.mode", "postgresql",
                "spring.datasource.url", "jdbc:postgresql://db.example.local:5432/iguana",
                "spring.datasource.username", "iguana",
                "spring.datasource.password", "secret",
                "app.integration.transport.mode", "rabbitmq"
            )
        );
        Channel channel = new Channel();
        channel.setId(116L);
        channel.setPlatform("telegram");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, botWorkingDir);

        assertThat(contract.resolvedLauncherKind()).isEqualTo("jar");
        assertThat(contract.artifactSource()).isEqualTo("explicit-config");
        assertThat(contract.requiredEnvironmentKeys()).contains("APP_DB_MODE", "APP_INTEGRATION_TRANSPORT_MODE");
        assertThat(contract.requiredEnvironmentKeys()).doesNotContain("SPRING_DATASOURCE_URL", "APP_DB_PANEL_RUNTIME");
        assertThat(contract.optionalEnvironmentKeys()).doesNotContain(
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "DATABASE_URL"
        );
        assertThat(contract.warnings()).noneMatch(item -> item.contains("SQLite runtime contract"));
        assertThat(contract.production().readyForProduction()).isTrue();
        assertThat(contract.production().blockingReasons()).isEmpty();
    }

    @Test
    void buildEnvironmentUsesIsolatedWorkerModeForRabbitMqAndOmitsCanonicalDatabaseCredentials() {
        BotRuntimeContractService service = createService(
            "auto",
            Map.of(),
            Map.of(),
            Map.of(
                "app.datasource.mode", "postgresql",
                "spring.datasource.url", "jdbc:postgresql://db.example.local:5432/iguana",
                "spring.datasource.username", "iguana",
                "spring.datasource.password", "secret",
                "app.integration.transport.mode", "rabbitmq"
            )
        );
        Channel channel = new Channel();
        channel.setId(117L);
        channel.setPlatform("telegram");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(6L, "tg", "telegram", "token", true),
            tempDir.resolve("worker.log")
        );

        assertThat(env)
            .containsEntry("APP_DB_MODE", "worker")
            .containsEntry("APP_INTEGRATION_TRANSPORT_MODE", "rabbitmq")
            .doesNotContainKeys(
                "SPRING_DATASOURCE_URL",
                "SPRING_DATASOURCE_USERNAME",
                "SPRING_DATASOURCE_PASSWORD",
                "DATABASE_URL",
                "APP_DB_BOT_RUNTIME",
                "SUPPORT_BOT_DATABASE_PATH"
            );
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
    void describeFallsBackToMavenWhenTargetScanArtifactIsOlderThanSources() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path jar = botWorkingDir.resolve("bot-max").resolve("target").resolve("bot-max-0.0.1-SNAPSHOT.jar");
        Path moduleSource = botWorkingDir.resolve("bot-max").resolve("src").resolve("main").resolve("java").resolve("demo").resolve("MaxSource.java");
        Path botCoreSource = botWorkingDir.resolve("bot-core").resolve("src").resolve("main").resolve("java").resolve("demo").resolve("SharedSource.java");
        Files.createDirectories(jar.getParent());
        Files.createDirectories(moduleSource.getParent());
        Files.createDirectories(botCoreSource.getParent());
        Files.writeString(jar, "fake");
        Files.writeString(moduleSource, "class MaxSource {}");
        Files.writeString(botCoreSource, "class SharedSource {}");
        Files.setLastModifiedTime(jar, FileTime.from(Instant.parse("2026-05-20T10:00:00Z")));
        Files.setLastModifiedTime(moduleSource, FileTime.from(Instant.parse("2026-05-20T10:05:00Z")));
        Files.setLastModifiedTime(botCoreSource, FileTime.from(Instant.parse("2026-05-20T10:06:00Z")));

        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(38L);
        channel.setPlatform("max");

        BotRuntimeContractService.BotRuntimeContract contract = service.describe(channel, botWorkingDir);

        assertThat(contract.resolvedLauncherKind()).isEqualTo("maven");
        assertThat(contract.artifactSource()).isEqualTo("target-scan");
        assertThat(contract.executableJarPath()).isEqualTo(jar.toAbsolutePath().normalize().toString());
        assertThat(contract.warnings()).anyMatch(item -> item.contains("Maven fallback"));
        assertThat(contract.production().readyForProduction()).isFalse();
        assertThat(contract.production().blockingReasons()).anyMatch(item -> item.contains("Maven launcher"));
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
            .containsEntry("APP_PANEL_INTERNAL_API_BASE_URL", "http://127.0.0.1:8080")
            .containsEntry("APP_PANEL_INTERNAL_API_TOKEN", "iguana-internal-bot-token")
            .containsEntry("APP_DB_MODE", "sqlite")
            .containsEntry("APP_DB_BOT_RUNTIME", tempDir.resolve("bot_runtime.db").toString())
            .containsEntry("APP_DB_BOT", tempDir.resolve("bot_runtime.db").toString())
            .containsEntry("SUPPORT_BOT_DATABASE_PATH", tempDir.resolve("panel_runtime.db").toString())
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
            .containsEntry("APP_DB_MODE", "sqlite")
            .containsEntry("APP_DB_BOT_RUNTIME", tempDir.resolve("bot_runtime.db").toString())
            .containsEntry("APP_DB_BOT", tempDir.resolve("bot_runtime.db").toString())
            .containsEntry("SUPPORT_BOT_DATABASE_PATH", tempDir.resolve("panel_runtime.db").toString())
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
            .containsEntry("APP_DB_MODE", "sqlite")
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
            .containsEntry("APP_DB_MODE", "sqlite")
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
    void buildEnvironmentRejectsUnsupportedProxyScheme() {
        BotRuntimeContractService service = createService("auto", Map.of(), Map.of(
            "integration_network", Map.of(
                "bots", Map.of(
                    "mode", "proxy",
                    "proxy", Map.of(
                        "scheme", "mtproto",
                        "host", "proxy.internal",
                        "port", 853
                    )
                )
            )
        ));
        Channel channel = new Channel();
        channel.setId(35L);
        channel.setPlatform("telegram");

        assertThatThrownBy(() -> service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(8L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-mtproto.log")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("mtproto")
            .hasMessageContaining("not supported");
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
            .containsEntry("APP_DB_MODE", "sqlite")
            .containsEntry("GROUP_CHAT_ID", "0")
            .containsEntry("VK_BOT_ENABLED", "false")
            .containsEntry("MAX_BOT_ENABLED", "false");
    }

    @Test
    void buildEnvironmentForExternalPostgresUsesDatasourceContract() {
        BotRuntimeContractService service = createService(
            "auto",
            Map.of(),
            Map.of(),
            Map.of(
                "app.datasource.mode", "postgresql",
                "spring.datasource.url", "jdbc:postgresql://db.example.local:5432/iguana",
                "spring.datasource.username", "iguana",
                "spring.datasource.password", "secret"
            )
        );
        Channel channel = new Channel();
        channel.setId(41L);
        channel.setPlatform("telegram");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(11L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-postgres.log")
        );

        assertThat(env)
            .containsEntry("APP_DB_MODE", "postgresql")
            .containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://db.example.local:5432/iguana")
            .containsEntry("SPRING_DATASOURCE_USERNAME", "iguana")
            .containsEntry("SPRING_DATASOURCE_PASSWORD", "secret")
            .doesNotContainKeys("APP_DB_PANEL_RUNTIME", "APP_DB_TICKETS", "SUPPORT_BOT_DATABASE_PATH", "APP_DB_BOT_RUNTIME", "APP_DB_BOT");
    }

    @Test
    void buildEnvironmentForRabbitModeIncludesOutboundFeedbackPromptContract() {
        BotRuntimeContractService service = createService(
            "auto",
            Map.of(),
            Map.of(),
            Map.of(
                "app.integration.transport.mode", "rabbitmq",
                "app.integration.rabbitmq.outbound-exchange", "iguana.integration.outbound",
                "app.integration.rabbitmq.outbound-dlx", "iguana.integration.outbound.dlx"
            )
        );
        Channel channel = new Channel();
        channel.setId(42L);
        channel.setPlatform("telegram");

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(12L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-rabbit.log")
        );

        assertThat(env)
            .containsEntry("APP_INTEGRATION_RABBITMQ_OUTBOUND_EXCHANGE", "iguana.integration.outbound")
            .containsEntry("APP_INTEGRATION_RABBITMQ_OUTBOUND_DLX", "iguana.integration.outbound.dlx")
            .containsEntry("APP_INTEGRATION_RABBITMQ_OUTBOUND_QUEUE", "iguana.integration.outbound.feedback-prompt.telegram.channel.42.bot")
            .containsEntry("APP_INTEGRATION_RABBITMQ_OUTBOUND_DLQ", "iguana.integration.outbound.feedback-prompt.telegram.channel.42.bot.dlq")
            .containsEntry("APP_INTEGRATION_RABBITMQ_OUTBOUND_ROUTING_KEY", "integration.outbound.feedback.prompt.telegram.channel.42");
    }

    @Test
    void buildEnvironmentForTelegramIncludesCustomBotApiBaseUrl() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(36L);
        channel.setPlatform("telegram");
        channel.setPlatformConfig("""
            {
              "base_url": "https://telegram.ftl-dev.ru/"
            }
            """);

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(9L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-base-url.log")
        );

        assertThat(env).containsEntry("TELEGRAM_BOT_API_BASE_URL", "https://telegram.ftl-dev.ru");
    }

    @Test
    void buildEnvironmentForTelegramTreatsLegacyProxyMirrorAsBaseUrl() {
        BotRuntimeContractService service = createService("auto", Map.of());
        Channel channel = new Channel();
        channel.setId(37L);
        channel.setPlatform("telegram");
        channel.setDeliverySettings("""
            {
              "network_route": {
                "mode": "proxy",
                "proxy": {
                  "scheme": "https",
                  "host": "telegram.ftl-dev.ru",
                  "port": 443
                }
              }
            }
            """);

        Map<String, String> env = service.buildEnvironment(
            channel,
            new com.example.panel.model.channel.BotCredential(10L, "tg", "telegram", "tg-token", true),
            tempDir.resolve("telegram-legacy-mirror.log")
        );

        assertThat(env)
            .containsEntry("TELEGRAM_BOT_API_BASE_URL", "https://telegram.ftl-dev.ru")
            .containsEntry("APP_NETWORK_MODE", "direct")
            .doesNotContainKeys("APP_NETWORK_PROXY_HOST", "HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy");
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
        return createService(launchMode, executableJars, settings, Map.of());
    }

    private BotRuntimeContractService createService(String launchMode,
                                                    Map<String, String> executableJars,
                                                    Map<String, Object> settings,
                                                    Map<String, String> environmentOverrides) {
        SqliteDataSourceProperties sqliteProperties = new SqliteDataSourceProperties();
        sqliteProperties.setPath(tempDir.resolve("panel_runtime.db").toString());
        BotSqliteDataSourceProperties botSqliteProperties = new BotSqliteDataSourceProperties();
        botSqliteProperties.setPath(tempDir.resolve("bot_runtime.db").toString());
        BotProcessProperties properties = new BotProcessProperties();
        properties.setLaunchMode(launchMode);
        properties.setExecutableJars(executableJars);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(settings);
        IntegrationNetworkService integrationNetworkService = new IntegrationNetworkService(sharedConfigService, new ObjectMapper());
        MockEnvironment environment = new MockEnvironment();
        environmentOverrides.forEach(environment::setProperty);
        PanelDatabaseRuntimeMode databaseRuntimeMode = new PanelDatabaseRuntimeMode(environment);
        return new BotRuntimeContractService(
            sqliteProperties,
            botSqliteProperties,
            properties,
            integrationNetworkService,
            new ObjectMapper(),
            databaseRuntimeMode,
            environment
        );
    }
}
