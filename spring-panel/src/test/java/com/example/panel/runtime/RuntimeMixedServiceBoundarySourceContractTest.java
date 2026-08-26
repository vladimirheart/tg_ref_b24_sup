package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeMixedServiceBoundarySourceContractTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/example/panel");

    private static final Map<String, String> MIXED_SERVICE_TO_SCHEDULER = Map.of(
        "service/AiOfflineEvaluationService.java",
        "service/AiOfflineEvaluationScheduler.java",
        "service/BotRuntimeBlacklistService.java",
        "service/BotRuntimeBlacklistExpiryScheduler.java",
        "service/IncidentOpsEscalationService.java",
        "service/IncidentOpsEscalationScheduler.java",
        "service/IncidentRouteDeliveryOutboxService.java",
        "service/IncidentRouteDeliveryOutboxScheduler.java",
        "service/integration/OutboundFeedbackPromptPublishOutboxService.java",
        "service/integration/OutboundFeedbackPromptPublishOutboxScheduler.java"
    );

    @Test
    void sharedBusinessServicesAreNotRemovedFromWebBySchedulerClassification() throws IOException {
        for (Map.Entry<String, String> entry : MIXED_SERVICE_TO_SCHEDULER.entrySet()) {
            String service = read(entry.getKey());
            String scheduler = read(entry.getValue());

            assertThat(service)
                .as(entry.getKey())
                .doesNotContain("@RuntimeWorkload(")
                .doesNotContain("@Scheduled(");

            assertThat(scheduler)
                .as(entry.getValue())
                .contains("@RuntimeWorkload(")
                .contains("RuntimeRole.WORKER")
                .contains("RuntimeReplicaPolicy.LEASED")
                .contains("@Scheduled(");
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(SOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
