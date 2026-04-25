package com.example.panel.service;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeContractService {

    private static final Logger log = LoggerFactory.getLogger(BotRuntimeContractService.class);
    private static final String STARTED_SIGNAL = "Spring Boot started marker";
    private static final String FAILURE_SIGNAL = "APPLICATION FAILED TO START banner";

    private final SqliteDataSourceProperties ticketsDbProperties;
    private final BotProcessProperties botProcessProperties;
    private final IntegrationNetworkService integrationNetworkService;
    private final ObjectMapper objectMapper;

    public BotRuntimeContractService(SqliteDataSourceProperties ticketsDbProperties,
                                     BotProcessProperties botProcessProperties,
                                     IntegrationNetworkService integrationNetworkService,
                                     ObjectMapper objectMapper) {
        this.ticketsDbProperties = ticketsDbProperties;
        this.botProcessProperties = botProcessProperties;
        this.integrationNetworkService = integrationNetworkService;
        this.objectMapper = objectMapper;
    }

    public String resolveBotModule(Channel channel) {
        if (channel == null || channel.getPlatform() == null) {
            return "bot-telegram";
        }
        if ("vk".equalsIgnoreCase(channel.getPlatform())) {
            return "bot-vk";
        }
        if ("max".equalsIgnoreCase(channel.getPlatform())) {
            return "bot-max";
        }
        return "bot-telegram";
    }

    public BotRuntimeContract describe(Channel channel, Path botWorkingDir) {
        String botModule = resolveBotModule(channel);
        BotProcessProperties.LaunchMode launchMode = botProcessProperties.resolveLaunchMode();
        ResolvedExecutableJar executableJar = resolveExecutableJarDetailed(botWorkingDir, botModule);
        BotLaunchPlan launchPlan = resolveLaunchPlan(botWorkingDir, botModule);
        List<String> warnings = new ArrayList<>();
        if (launchMode == BotProcessProperties.LaunchMode.JAR && executableJar == null) {
            warnings.add("В режиме jar собранный артефакт не найден, запуск завершится ошибкой.");
        }
        if (launchMode == BotProcessProperties.LaunchMode.AUTO && executableJar == null) {
            warnings.add("Для модуля не найден jar, будет использован fallback на Maven launcher.");
        }
        if (executableJar != null && !"explicit-config".equals(executableJar.source())) {
            warnings.add("Для production лучше явно задать app.bots.executable-jars вместо target scan.");
        }
        BotProductionContract production = productionContract(botWorkingDir, botModule, launchMode, executableJar, launchPlan);
        BotLifecycleContract lifecycle = lifecycleContract();
        return new BotRuntimeContract(
            channel != null ? channel.getId() : null,
            normalizePlatform(channel),
            botModule,
            launchMode.name().toLowerCase(),
            launchPlan.launcherKind(),
            production.preferredLauncher(),
            executableJar != null ? executableJar.source() : "none",
            executableJar != null ? executableJar.path().toString() : null,
            requiredEnvironmentKeys(channel),
            optionalEnvironmentKeys(channel),
            warnings,
            readinessContract(),
            production,
            lifecycle
        );
    }

    public BotLaunchPlan resolveLaunchPlan(Path botWorkingDir, String botModule) {
        BotProcessProperties.LaunchMode launchMode = botProcessProperties.resolveLaunchMode();
        ResolvedExecutableJar executableJar = resolveExecutableJarDetailed(botWorkingDir, botModule);
        if (launchMode == BotProcessProperties.LaunchMode.JAR) {
            if (executableJar == null) {
                throw new IllegalStateException("Не найден собранный jar для модуля " + botModule
                    + ". Соберите java-bot или переключите app.bots.launch-mode в auto/maven.");
            }
            return jarLaunchPlan(executableJar);
        }
        if (launchMode == BotProcessProperties.LaunchMode.AUTO && executableJar != null) {
            return jarLaunchPlan(executableJar);
        }
        return mavenLaunchPlan(botWorkingDir, botModule);
    }

    public Path resolveExecutableJar(Path botWorkingDir, String botModule) {
        ResolvedExecutableJar executableJar = resolveExecutableJarDetailed(botWorkingDir, botModule);
        return executableJar != null ? executableJar.path() : null;
    }

    public Map<String, String> buildEnvironment(Channel channel, BotCredential credential, Path logFile) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("APP_DB_TICKETS", ticketsDbProperties.getNormalizedPath().toString());
        env.put("TELEGRAM_BOT_TOKEN", credential.token());
        env.put("TELEGRAM_BOT_USERNAME", Objects.toString(channel.getBotUsername(), ""));
        env.put("GROUP_CHAT_ID", Objects.toString(channel.getSupportChatId(), "0"));
        String platform = normalizePlatform(channel);
        Map<String, Object> platformConfig = parsePlatformConfig(channel);
        env.put("VK_BOT_ENABLED", "vk".equals(platform) ? "true" : "false");
        if ("vk".equals(platform)) {
            env.put("VK_BOT_TOKEN", credential.token());
            env.put("VK_OPERATOR_CHAT_ID", Objects.toString(channel.getSupportChatId(), "0"));
            Integer groupId = readInteger(platformConfig, "group_id", "groupId");
            String confirmationToken = readString(platformConfig, "confirmation_token", "confirmationToken");
            String secret = readString(platformConfig, "secret", "callback_secret", "callbackSecret");
            if (groupId != null && groupId > 0) {
                env.put("VK_GROUP_ID", String.valueOf(groupId));
            }
            env.put("VK_WEBHOOK_ENABLED", Boolean.toString(groupId != null && groupId > 0));
            if (!confirmationToken.isBlank()) {
                env.put("VK_CONFIRMATION_TOKEN", confirmationToken);
            }
            if (!secret.isBlank()) {
                env.put("VK_WEBHOOK_SECRET", secret);
            }
        }
        env.put("MAX_BOT_ENABLED", "max".equals(platform) ? "true" : "false");
        if ("max".equals(platform)) {
            env.put("MAX_BOT_TOKEN", credential.token());
            env.put("MAX_CHANNEL_ID", Objects.toString(channel.getId(), "0"));
            env.put("MAX_SUPPORT_CHAT_ID", Objects.toString(channel.getSupportChatId(), ""));
            env.put("SERVER_PORT", String.valueOf(botProcessProperties.resolveMaxPort(channel.getId())));
            env.put("SERVER_ADDRESS", botProcessProperties.getHost());
            env.put("SPRING_MAIN_WEB_APPLICATION_TYPE", "servlet");
            String secret = readString(platformConfig, "secret", "webhook_secret", "webhookSecret");
            if (!secret.isBlank()) {
                env.put("MAX_WEBHOOK_SECRET", secret);
            }
        }
        env.putIfAbsent("SPRING_PROFILES_ACTIVE", "default");
        env.put("APP_BOT_LOG_PATH", logFile.toString());
        appendEnvOption(env, "JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8");
        appendEnvOption(env, "JAVA_TOOL_OPTIONS", "-Dsun.jnu.encoding=UTF-8");
        appendEnvOption(env, "JAVA_TOOL_OPTIONS", "-Dsun.stdout.encoding=UTF-8");
        appendEnvOption(env, "JAVA_TOOL_OPTIONS", "-Dsun.stderr.encoding=UTF-8");
        Map<String, String> networkEnv = integrationNetworkService.buildProcessEnvironment(
            integrationNetworkService.resolveBotRoute(channel)
        );
        for (Map.Entry<String, String> entry : networkEnv.entrySet()) {
            if ("JAVA_TOOL_OPTIONS".equals(entry.getKey())) {
                appendEnvOption(env, "JAVA_TOOL_OPTIONS", entry.getValue());
                continue;
            }
            env.put(entry.getKey(), entry.getValue());
        }
        return env;
    }

    public BotReadinessContract readinessContract() {
        return new BotReadinessContract(
            botProcessProperties.resolveStartupReadinessTimeout().toMillis(),
            botProcessProperties.resolveStartupPollInterval().toMillis(),
            STARTED_SIGNAL,
            FAILURE_SIGNAL
        );
    }

    public BotLifecycleContract lifecycleContract() {
        return new BotLifecycleContract(
            "running",
            "stopped",
            "error",
            "panel waits for readiness signal after process start",
            "panel terminates process when readiness is not confirmed in time"
        );
    }

    private String normalizePlatform(Channel channel) {
        return Objects.toString(channel != null ? channel.getPlatform() : null, "telegram").toLowerCase();
    }

    private List<String> requiredEnvironmentKeys(Channel channel) {
        List<String> keys = new ArrayList<>(List.of(
            "APP_DB_TICKETS",
            "TELEGRAM_BOT_TOKEN",
            "TELEGRAM_BOT_USERNAME",
            "GROUP_CHAT_ID",
            "APP_BOT_LOG_PATH",
            "SPRING_PROFILES_ACTIVE",
            "JAVA_TOOL_OPTIONS"
        ));
        String platform = normalizePlatform(channel);
        if ("vk".equals(platform)) {
            keys.addAll(List.of("VK_BOT_ENABLED", "VK_BOT_TOKEN", "VK_OPERATOR_CHAT_ID", "VK_WEBHOOK_ENABLED"));
        }
        if ("max".equals(platform)) {
            keys.addAll(List.of(
                "MAX_BOT_ENABLED",
                "MAX_BOT_TOKEN",
                "MAX_CHANNEL_ID",
                "MAX_SUPPORT_CHAT_ID",
                "SERVER_PORT",
                "SERVER_ADDRESS",
                "SPRING_MAIN_WEB_APPLICATION_TYPE"
            ));
        }
        return keys;
    }

    private List<String> optionalEnvironmentKeys(Channel channel) {
        List<String> keys = new ArrayList<>();
        String platform = normalizePlatform(channel);
        if ("vk".equals(platform)) {
            keys.addAll(List.of("VK_GROUP_ID", "VK_CONFIRMATION_TOKEN", "VK_WEBHOOK_SECRET"));
        }
        if ("max".equals(platform)) {
            keys.add("MAX_WEBHOOK_SECRET");
        }
        keys.addAll(integrationNetworkService.buildProcessEnvironment(integrationNetworkService.resolveBotRoute(channel)).keySet());
        return keys;
    }

    private BotProductionContract productionContract(Path botWorkingDir,
                                                     String botModule,
                                                     BotProcessProperties.LaunchMode configuredLaunchMode,
                                                     ResolvedExecutableJar executableJar,
                                                     BotLaunchPlan launchPlan) {
        String preferredLauncher = botProcessProperties.resolvePreferredProductionLauncher().name().toLowerCase();
        String recommendedArtifactPath = resolveRecommendedArtifactPath(botWorkingDir, botModule);
        List<String> blockers = new ArrayList<>();
        if (!"jar".equals(preferredLauncher)) {
            blockers.add("Для production пока рекомендуется launcher jar, а не " + preferredLauncher + ".");
        }
        if ("maven".equals(launchPlan.launcherKind())) {
            blockers.add("Текущий runtime contract использует Maven launcher; для production нужен prebuilt jar.");
        }
        if (executableJar == null) {
            blockers.add("Не найден executable jar для production запуска.");
        } else if (!"explicit-config".equals(executableJar.source())) {
            blockers.add("Executable jar найден через target scan; для production нужен explicit app.bots.executable-jars contract.");
        }
        if (configuredLaunchMode == BotProcessProperties.LaunchMode.MAVEN) {
            blockers.add("app.bots.launch-mode=maven подходит только как controlled dev fallback.");
        }
        boolean readyForProduction = blockers.isEmpty();
        return new BotProductionContract(
            preferredLauncher,
            recommendedArtifactPath,
            readyForProduction,
            blockers
        );
    }

    private String resolveRecommendedArtifactPath(Path botWorkingDir, String botModule) {
        if (botWorkingDir == null || !StringUtils.hasText(botModule)) {
            return botProcessProperties.resolveRecommendedExecutableJar(botModule);
        }
        String relative = botProcessProperties.resolveRecommendedExecutableJar(botModule);
        if (!StringUtils.hasText(relative)) {
            return null;
        }
        return botWorkingDir.resolve(relative).normalize().toString();
    }

    private ResolvedExecutableJar resolveExecutableJarDetailed(Path botWorkingDir, String botModule) {
        if (botWorkingDir == null || !StringUtils.hasText(botModule)) {
            return null;
        }
        ResolvedExecutableJar configuredExecutableJar = resolveConfiguredExecutableJar(botWorkingDir, botModule);
        if (configuredExecutableJar != null) {
            return configuredExecutableJar;
        }
        return resolveExecutableJarByScan(botWorkingDir, botModule);
    }

    private ResolvedExecutableJar resolveConfiguredExecutableJar(Path botWorkingDir, String botModule) {
        String configuredPath = botProcessProperties.resolveExecutableJar(botModule);
        if (!StringUtils.hasText(configuredPath)) {
            return null;
        }
        Path candidate = Paths.get(configuredPath.trim());
        if (!candidate.isAbsolute()) {
            candidate = botWorkingDir.resolve(candidate).normalize();
        } else {
            candidate = candidate.toAbsolutePath().normalize();
        }
        if (Files.isRegularFile(candidate)) {
            return new ResolvedExecutableJar(candidate, "explicit-config");
        }
        log.warn("Configured executable jar for module {} not found at {}", botModule, candidate);
        return null;
    }

    private ResolvedExecutableJar resolveExecutableJarByScan(Path botWorkingDir, String botModule) {
        Path targetDir = botWorkingDir.resolve(botModule).resolve("target").normalize();
        if (!Files.isDirectory(targetDir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(targetDir)) {
            Path candidate = files
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return !name.startsWith("original-") && !name.contains("-plain");
                })
                .sorted((left, right) -> {
                    try {
                        return Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left));
                    } catch (Exception ex) {
                        return right.getFileName().toString().compareTo(left.getFileName().toString());
                    }
                })
                .findFirst()
                .orElse(null);
            return candidate != null ? new ResolvedExecutableJar(candidate, "target-scan") : null;
        } catch (Exception ex) {
            log.warn("Failed to resolve executable jar for module {}", botModule, ex);
            return null;
        }
    }

    private BotLaunchPlan jarLaunchPlan(ResolvedExecutableJar executableJar) {
        return new BotLaunchPlan(
            List.of(javaCommand(), "-jar", executableJar.path().toAbsolutePath().normalize().toString()),
            "jar:" + executableJar.path().getFileName(),
            "jar",
            executableJar.source(),
            executableJar.path().toAbsolutePath().normalize().toString()
        );
    }

    private BotLaunchPlan mavenLaunchPlan(Path botWorkingDir, String botModule) {
        List<String> command = new ArrayList<>();
        command.add(mvnwCommand());
        command.add("-Dmaven.repo.local=" + resolveMavenRepoDir(botWorkingDir));
        command.add("-q");
        command.add("-Dorg.slf4j.simpleLogger.showDateTime=true");
        command.add("-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSSXXX");
        command.add("-pl");
        command.add(botModule);
        command.add("-am");
        command.add("spring-boot:run");
        return new BotLaunchPlan(command, "maven:spring-boot-run:" + botModule, "maven", "maven-fallback", null);
    }

    private Path resolveMavenRepoDir(Path botWorkingDir) {
        return botWorkingDir.resolve("../spring-panel/.m2/repository").toAbsolutePath().normalize();
    }

    private String mvnwCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "mvnw.cmd" : "./mvnw";
    }

    private String javaCommand() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return javaHome.resolve("bin").resolve(executable).toString();
    }

    private Map<String, Object> parsePlatformConfig(Channel channel) {
        if (channel == null) {
            return Map.of();
        }
        String raw = channel.getPlatformConfig();
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse platform_config for channel {}: {}", channel.getId(), ex.getMessage());
            return Map.of();
        }
    }

    private String readString(Map<String, Object> values, String... keys) {
        if (values == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private Integer readInteger(Map<String, Object> values, String... keys) {
        String raw = readString(values, keys);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void appendEnvOption(Map<String, String> env, String key, String option) {
        if (env == null || !StringUtils.hasText(key) || !StringUtils.hasText(option)) {
            return;
        }
        String existing = Objects.toString(env.get(key), "").trim();
        if (existing.contains(option)) {
            return;
        }
        String updated = existing.isBlank() ? option.trim() : existing + " " + option.trim();
        env.put(key, updated);
    }

    private record ResolvedExecutableJar(Path path, String source) {}

    public record BotLaunchPlan(List<String> command,
                                String description,
                                String launcherKind,
                                String artifactSource,
                                String executableJarPath) {}

    public record BotReadinessContract(long timeoutMillis,
                                       long pollIntervalMillis,
                                       String successSignal,
                                       String failureSignal) {}

    public record BotProductionContract(String preferredLauncher,
                                        String recommendedArtifactPath,
                                        boolean readyForProduction,
                                        List<String> blockingReasons) {}

    public record BotLifecycleContract(String runningStatus,
                                       String stoppedStatus,
                                       String errorStatus,
                                       String startupExpectation,
                                       String timeoutBehavior) {}

    public record BotRuntimeContract(Long channelId,
                                     String platform,
                                     String botModule,
                                     String configuredLaunchMode,
                                     String resolvedLauncherKind,
                                     String preferredProductionLauncher,
                                     String artifactSource,
                                     String executableJarPath,
                                     List<String> requiredEnvironmentKeys,
                                     List<String> optionalEnvironmentKeys,
                                     List<String> warnings,
                                     BotReadinessContract readiness,
                                     BotProductionContract production,
                                     BotLifecycleContract lifecycle) {}
}
