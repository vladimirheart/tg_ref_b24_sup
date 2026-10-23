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
}
