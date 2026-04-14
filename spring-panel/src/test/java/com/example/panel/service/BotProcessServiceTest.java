package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotProcessServiceTest {

    @TempDir
    Path tempDir;

    private Process process;

    @AfterEach
    void tearDown() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
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

    private static final class TestableBotProcessService extends BotProcessService {

        private final Duration readinessTimeout;
        private final Duration pollInterval;

        private TestableBotProcessService(Duration readinessTimeout, Duration pollInterval) {
            super(mock(SharedConfigService.class), mock(com.example.panel.config.SqliteDataSourceProperties.class),
                mock(com.example.panel.config.BotProcessProperties.class),
                mock(IntegrationNetworkService.class), mock(com.fasterxml.jackson.databind.ObjectMapper.class));
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
