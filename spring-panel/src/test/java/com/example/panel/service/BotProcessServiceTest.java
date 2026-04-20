package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.SqliteDataSourceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotProcessServiceTest {

    @TempDir
    Path tempDir;

    private Process process;

    @AfterEach
    void tearDown() throws Exception {
        if (process != null) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            process.waitFor(10, TimeUnit.SECONDS);
            Thread.sleep(100);
            process = null;
        }
    }

    @Test
    void awaitProcessReadinessUsesFreshLogSegmentAndAcceptsStartedMarker() throws Exception {
        TestableBotProcessService service = new TestableBotProcessService(Duration.ofSeconds(3), Duration.ofMillis(50));
        Path processLog = tempDir.resolve("bot-process.log");
        Files.writeString(processLog, "2026-04-10 Started OldApplication in 0.1 seconds\n");
        long startOffset = Files.size(processLog);

        process = launchProbe("success", processLog);
        OffsetDateTime startedAt = OffsetDateTime.now();

        BotProcessService.BotProcessStatus status =
            service.awaitProcessReadiness(process, processLog, startOffset, 42L, startedAt);

        assertThat(status.running()).isTrue();
        assertThat(status.message()).isEqualTo("running");
        assertThat(status.startedAt()).isEqualTo(startedAt);
    }

    @Test
    void awaitProcessReadinessReturnsDescriptionFromSpringFailureBanner() throws Exception {
        TestableBotProcessService service = new TestableBotProcessService(Duration.ofSeconds(3), Duration.ofMillis(50));
        Path processLog = tempDir.resolve("bot-process.log");

        process = launchProbe("failure", processLog);

        BotProcessService.BotProcessStatus status =
            service.awaitProcessReadiness(process, processLog, 0L, 77L, OffsetDateTime.now());

        assertThat(status.running()).isFalse();
        assertThat(status.message()).contains("required a bean of type 'demo.Repository' that could not be found");
    }

    @Test
    void awaitProcessReadinessFailsWhenStartedMarkerDoesNotAppearInTime() throws Exception {
        TestableBotProcessService service = new TestableBotProcessService(Duration.ofMillis(400), Duration.ofMillis(50));
        Path processLog = tempDir.resolve("bot-process.log");

        process = launchProbe("hang", processLog);

        BotProcessService.BotProcessStatus status =
            service.awaitProcessReadiness(process, processLog, 0L, 99L, OffsetDateTime.now());

        assertThat(status.running()).isFalse();
        assertThat(status.message()).contains("Не удалось подтвердить готовность бота");
    }

    @Test
    void resolveLaunchPlanPrefersJarInAutoModeWhenArtifactExists() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path jar = botWorkingDir.resolve("bot-telegram").resolve("target").resolve("bot-telegram-0.0.1-SNAPSHOT.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "fake");

        BotProcessService service = createRuntimeService("auto");

        BotRuntimeContractService.BotLaunchPlan plan = service.resolveLaunchPlan(botWorkingDir, "bot-telegram");

        assertThat(plan.description()).startsWith("jar:");
        assertThat(plan.command()).contains("-jar");
        assertThat(plan.command()).contains(jar.toAbsolutePath().normalize().toString());
    }

    @Test
    void resolveExecutableJarPrefersConfiguredArtifactBeforeTargetScan() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path configuredJar = botWorkingDir.resolve("dist").resolve("bot-telegram-runtime.jar");
        Path scannedJar = botWorkingDir.resolve("bot-telegram").resolve("target").resolve("bot-telegram-0.0.1-SNAPSHOT.jar");
        Files.createDirectories(configuredJar.getParent());
        Files.createDirectories(scannedJar.getParent());
        Files.writeString(configuredJar, "configured");
        Files.writeString(scannedJar, "scanned");

        BotProcessService service = createRuntimeService("auto", Map.of(
            "bot-telegram", "dist/bot-telegram-runtime.jar"
        ));

        Path resolved = service.resolveExecutableJar(botWorkingDir, "bot-telegram");

        assertThat(resolved).isEqualTo(configuredJar.toAbsolutePath().normalize());
    }

    @Test
    void resolveExecutableJarFallsBackToTargetScanWhenConfiguredArtifactIsMissing() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path scannedJar = botWorkingDir.resolve("bot-telegram").resolve("target").resolve("bot-telegram-0.0.1-SNAPSHOT.jar");
        Files.createDirectories(scannedJar.getParent());
        Files.writeString(scannedJar, "scanned");

        BotProcessService service = createRuntimeService("auto", Map.of(
            "bot-telegram", "dist/missing-runtime.jar"
        ));

        Path resolved = service.resolveExecutableJar(botWorkingDir, "bot-telegram");

        assertThat(resolved).isEqualTo(scannedJar.toAbsolutePath().normalize());
    }

    @Test
    void resolveLaunchPlanHonorsExplicitMavenMode() throws Exception {
        Path botWorkingDir = tempDir.resolve("java-bot");
        Path jar = botWorkingDir.resolve("bot-telegram").resolve("target").resolve("bot-telegram-0.0.1-SNAPSHOT.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "fake");

        BotProcessService service = createRuntimeService("maven");

        BotRuntimeContractService.BotLaunchPlan plan = service.resolveLaunchPlan(botWorkingDir, "bot-telegram");

        assertThat(plan.description()).isEqualTo("maven:spring-boot-run:bot-telegram");
        assertThat(plan.command().get(0)).endsWith("mvnw.cmd");
        assertThat(plan.command()).contains("spring-boot:run");
    }

    @Test
    void resolveLaunchPlanFailsInJarModeWithoutArtifact() {
        Path botWorkingDir = tempDir.resolve("java-bot");
        BotProcessService service = createRuntimeService("jar");

        assertThatThrownBy(() -> service.resolveLaunchPlan(botWorkingDir, "bot-telegram"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Не найден собранный jar");
    }

    private Process launchProbe(String mode, Path processLog) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
            javaCommand(),
            "-cp",
            System.getProperty("java.class.path"),
            ProcessProbe.class.getName(),
            mode
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(processLog.toFile()));
        return builder.start();
    }

    private String javaCommand() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return javaHome.resolve("bin").resolve(executable).toString();
    }

    private BotProcessService createRuntimeService(String launchMode) {
        return createRuntimeService(launchMode, Map.of());
    }

    private BotProcessService createRuntimeService(String launchMode, Map<String, String> executableJars) {
        BotProcessProperties properties = new BotProcessProperties();
        properties.setLaunchMode(launchMode);
        properties.setExecutableJars(executableJars);
        SqliteDataSourceProperties sqliteProperties = new SqliteDataSourceProperties();
        sqliteProperties.setPath(tempDir.resolve("tickets.db").toString());
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        IntegrationNetworkService integrationNetworkService = new IntegrationNetworkService(sharedConfigService, new ObjectMapper());
        BotRuntimeContractService botRuntimeContractService = new BotRuntimeContractService(
            sqliteProperties,
            properties,
            integrationNetworkService,
            new ObjectMapper()
        );
        return new BotProcessService(
                null,
                properties,
                botRuntimeContractService
        );
    }

    private static final class TestableBotProcessService extends BotProcessService {

        private final Duration readinessTimeout;
        private final Duration pollInterval;

        private TestableBotProcessService(Duration readinessTimeout, Duration pollInterval) {
            super(null, configureProperties(readinessTimeout, pollInterval), createRuntimeContractService(readinessTimeout, pollInterval));
            this.readinessTimeout = readinessTimeout;
            this.pollInterval = pollInterval;
        }

        @Override
        Duration startupReadinessTimeout() {
            return readinessTimeout;
        }

        @Override
        Duration startupPollInterval() {
            return pollInterval;
        }
    }

    private static BotProcessProperties configureProperties(Duration readinessTimeout, Duration pollInterval) {
        BotProcessProperties properties = new BotProcessProperties();
        properties.setStartupReadinessTimeout(readinessTimeout);
        properties.setStartupPollInterval(pollInterval);
        return properties;
    }

    private static BotRuntimeContractService createRuntimeContractService(Duration readinessTimeout, Duration pollInterval) {
        BotProcessProperties properties = configureProperties(readinessTimeout, pollInterval);
        SqliteDataSourceProperties sqliteProperties = new SqliteDataSourceProperties();
        sqliteProperties.setPath(Path.of(System.getProperty("java.io.tmpdir")).resolve("bot-process-test.db").toString());
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        IntegrationNetworkService integrationNetworkService = new IntegrationNetworkService(sharedConfigService, new ObjectMapper());
        return new BotRuntimeContractService(sqliteProperties, properties, integrationNetworkService, new ObjectMapper());
    }

    public static final class ProcessProbe {

        public static void main(String[] args) throws Exception {
            String mode = args.length > 0 ? args[0] : "success";
            switch (mode) {
                case "success" -> {
                    System.out.println("Bootstrapping probe");
                    Thread.sleep(100);
                    System.out.println("Started ProbeApplication in 0.321 seconds");
                    System.out.flush();
                    Thread.sleep(5_000);
                }
                case "failure" -> {
                    System.out.println("***************************");
                    System.out.println("APPLICATION FAILED TO START");
                    System.out.println("***************************");
                    System.out.println();
                    System.out.println("Description:");
                    System.out.println();
                    System.out.println("Parameter 0 of constructor in demo.Service required a bean of type 'demo.Repository' that could not be found.");
                    System.out.flush();
                    System.exit(1);
                }
                case "hang" -> {
                    System.out.println("Bootstrapping probe");
                    System.out.flush();
                    Thread.sleep(5_000);
                }
                default -> throw new IllegalArgumentException("Unknown mode: " + mode);
            }
        }
    }
}
