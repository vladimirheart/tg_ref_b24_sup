package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeWorkloadSourceContractTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/example/panel");

    @Test
    void everyScheduledOrRabbitListenerClassHasExplicitRuntimeClassification() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            boolean backgroundEntryPoint = content.contains("@Scheduled") || content.contains("@RabbitListener");
            if (!backgroundEntryPoint) {
                continue;
            }
            if (!content.contains("@RuntimeWorkload(")) {
                violations.add(SOURCE_ROOT.relativize(source).toString());
            }
            if (!content.contains("replicaPolicy = RuntimeReplicaPolicy.")) {
                violations.add(SOURCE_ROOT.relativize(source) + " (missing replica policy)");
            }
        }

        assertThat(violations)
            .as("Every background entry point must declare RuntimeWorkload role and replica policy")
            .isEmpty();
    }

    @Test
    void panelRabbitListenersAreWorkerOwned() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            if (!content.contains("@RabbitListener")) {
                continue;
            }
            if (!content.contains("RuntimeRole.WORKER")) {
                violations.add(SOURCE_ROOT.relativize(source).toString());
            }
        }
        assertThat(violations)
            .as("Panel RabbitMQ consumers are backend worker workloads, not web workloads")
            .isEmpty();
    }

    @Test
    void sseHeartbeatIsSeparatedFromSharedUiEventService() throws IOException {
        Path service = SOURCE_ROOT.resolve("service/UiEventStreamService.java");
        Path heartbeat = SOURCE_ROOT.resolve("service/UiEventStreamHeartbeatScheduler.java");

        assertThat(Files.readString(service, StandardCharsets.UTF_8)).doesNotContain("@Scheduled");
        assertThat(Files.readString(heartbeat, StandardCharsets.UTF_8))
            .contains("@Scheduled")
            .contains("RuntimeRole.WEB")
            .contains("RuntimeReplicaPolicy.PROCESS_LOCAL");
    }

    private List<Path> javaSources() throws IOException {
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            return stream
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .toList();
        }
    }
}
