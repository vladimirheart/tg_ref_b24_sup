package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IncidentMonitoringPolicyServiceTest {
    @TempDir Path directory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T09:00:00Z"), ZoneOffset.UTC);
    private IncidentMonitoringPolicyService service(Clock time) {
        return new IncidentMonitoringPolicyService(directory.resolve("policy.json"), new ObjectMapper(), time);
    }

    @Test void defaultsDoNotWriteOrDisableAnything() {
        var policy = service(clock);
        IncidentMonitoringPolicyService.DIRECTIONS.keySet().forEach(id -> assertThat(policy.isSuppressed(id)).isFalse());
        assertThat(directory.resolve("policy.json")).doesNotExist();
        assertThat(policy.isSuppressed(null)).isFalse();
        assertThat(policy.isSuppressed("manual")).isFalse();
    }

    @Test void pausePersistsAcrossInstancesExpiresAndPreservesOtherDirections() {
        var policy = service(clock);
        policy.update("provider_health", Map.of("mode", "paused", "until", "2026-10-30T12:00:00+03:00"), "admin");
        policy.update("smtp", Map.of("mode", "disabled"), "admin");
        assertThat(service(clock).isSuppressed("provider_health")).isTrue();
        var later = service(Clock.fixed(Instant.parse("2026-10-30T09:00:00Z"), ZoneOffset.UTC));
        assertThat(later.isSuppressed("provider_health")).isFalse();
        assertThat(later.isSuppressed("smtp")).isTrue();
        policy.update("smtp", Map.of("mode", "enabled"), "admin");
        assertThat(service(clock).isSuppressed("smtp")).isFalse();
        assertThat(service(clock).isSuppressed("provider_health")).isTrue();
    }

    @Test void invalidRequestsDoNotChangeSavedPolicy() throws Exception {
        var policy = service(clock); policy.update("rms", Map.of("mode", "disabled"), "admin");
        String before = Files.readString(directory.resolve("policy.json"));
        for (var payload : java.util.List.of(Map.of("mode", "bad"), Map.of("mode", "paused"), Map.of("mode", "paused", "until", "2020-01-01T00:00:00Z"))) {
            assertThatThrownBy(() -> policy.update("rms", Map.copyOf(payload), "admin")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        assertThatThrownBy(() -> policy.update("unknown", Map.of("mode", "disabled"), "admin")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(Files.readString(directory.resolve("policy.json"))).isEqualTo(before);
    }

    @Test void weekPresetUsesServerClockNotBrowserClock() throws Exception {
        var policy = service(clock);
        policy.update("provider_health", Map.of("mode", "paused", "duration_minutes", 10080), "admin");
        var saved = new ObjectMapper().readTree(directory.resolve("policy.json").toFile());
        assertThat(saved.path("provider_health").path("until").asText()).isEqualTo("2026-10-30T09:00Z");
    }

    @Test void corruptFileDoesNotSilentlyEnableChecks() throws Exception {
        Files.writeString(directory.resolve("policy.json"), "{broken");
        assertThatThrownBy(() -> service(clock).isSuppressed("provider_health")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(clock).update("smtp", Map.of("mode", "enabled"), "admin")).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(directory.resolve("policy.json"))).isEqualTo("{broken");
    }

    @Test void workerPauseIsIndependentPersistsAndExpiresWithoutAffectingQueueOrOtherWorker() {
        var policy = service(clock);
        policy.update("integration_transport.ui_updates", Map.of("mode", "paused", "duration_minutes", 10080), "admin");
        assertThat(service(clock).isSignalSuppressed("integration_transport", "panel-runtime-checkpoints/ui-event-outbox-watch")).isTrue();
        assertThat(service(clock).isSuppressed("integration_transport.operator_notifications")).isFalse();
        assertThat(service(clock).isSignalSuppressed("integration_transport", "panel-rabbitmq-bridge")).isFalse();
        assertThat(service(Clock.offset(clock, java.time.Duration.ofDays(7))).isSuppressed("integration_transport.ui_updates")).isFalse();
    }

    @Test void parentSwitchStopsAllThreeChecksAndChildCannotOverrideIt() {
        var policy = service(clock);
        policy.update("integration_transport", Map.of("mode", "disabled"), "admin");
        for (String child : java.util.List.of("queue", "ui_updates", "operator_notifications")) {
            policy.update("integration_transport." + child, Map.of("mode", "enabled"), "admin");
            assertThat(service(clock).isSuppressed("integration_transport." + child)).isTrue();
        }
        var items = (java.util.List<?>) policy.overview().get("items");
        assertThat(items.stream().map(item -> (Map<?, ?>) item).filter(item -> Boolean.TRUE.equals(item.get("parent_suppressed"))).count()).isEqualTo(3);
        policy.update("integration_transport", Map.of("mode", "enabled"), "admin");
        assertThat(service(clock).isSuppressed("integration_transport.ui_updates")).isFalse();
    }

    @Test void queuePauseDoesNotDisableWorkersAndRetiredSignalsCannotNotify() {
        var policy = service(clock);
        policy.update("integration_transport.queue", Map.of("mode", "disabled"), "admin");
        assertThat(policy.isSignalSuppressed("integration_transport", "panel-transport-sustained-pressure")).isTrue();
        assertThat(policy.isSignalSuppressed("integration_transport", "panel-runtime-checkpoints/ui-event-outbox-watch")).isFalse();
        assertThat(policy.isSignalSuppressed("integration_transport", "panel-runtime-checkpoints/operator-notification-watch.feedbacks")).isTrue();
        assertThat(policy.isSignalSuppressed("integration_transport", "panel-runtime-checkpoints")).isTrue();
        assertThat(policy.isSignalSuppressed("manual", "anything")).isFalse();
    }
}
