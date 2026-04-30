package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlaEscalationWebhookDeliveryServiceTest {

    private final SlaEscalationWebhookDeliveryService service =
            new SlaEscalationWebhookDeliveryService(new ObjectMapper());

    @Test
    void resolvesStructuredAndLegacyWebhookEndpointsWithDeduplication() {
        List<SlaEscalationWebhookNotifier.WebhookEndpoint> endpoints = service.resolveWebhookEndpoints(Map.of(
                "sla_critical_escalation_webhooks", List.of(
                        Map.of("url", "https://hooks.example/a", "enabled", true, "headers", Map.of("X-Token", "a")),
                        Map.of("url", "https://hooks.example/b", "enabled", false)
                ),
                "sla_critical_escalation_webhook_urls", List.of("https://hooks.example/a", "https://hooks.example/c"),
                "sla_critical_escalation_webhook_url", "https://hooks.example/c"
        ));

        assertThat(endpoints).hasSize(2);
        assertThat(endpoints.get(0).url()).isEqualTo("https://hooks.example/a");
        assertThat(endpoints.get(0).headers()).containsEntry("X-Token", "a");
        assertThat(endpoints.get(1).url()).isEqualTo("https://hooks.example/c");
    }

    @Test
    void sendWebhookFanoutReturnsFalseForInvalidEndpoint() {
        boolean sent = service.sendWebhookFanout(
                List.of(new SlaEscalationWebhookNotifier.WebhookEndpoint("not a url", Map.of())),
                Map.of("event", "sla"),
                1000,
                1,
                10
        );

        assertThat(sent).isFalse();
    }
}
