package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceContextBlockServiceTest {

    private final DialogWorkspaceContextBlockService service = new DialogWorkspaceContextBlockService();

    @Test
    void buildContextBlocksAppliesConfiguredPriorityAndRequiredFlags() {
        List<Map<String, Object>> blocks = service.buildContextBlocks(
                Map.of(
                        "dialog_config", Map.of(
                                "workspace_context_block_priority", List.of("sla", "customer_profile"),
                                "workspace_context_block_required", List.of("customer_profile", "sla")
                        )
                ),
                Map.of(
                        "enabled", true,
                        "ready", false,
                        "missing_field_labels", List.of("CRM ID"),
                        "checked_at", "2026-04-20T10:00:00Z"
                ),
                List.of(
                        Map.of("required", true, "ready", true, "status", "ready", "label", "CRM", "updated_at_utc", "2026-04-20T09:55:00Z")
                ),
                List.of(Map.of("ticket_id", "T-1")),
                List.of(),
                "warning",
                Map.of("crm", Map.of("label", "CRM", "url", "https://example.test"))
        );

        assertThat(blocks).hasSize(6);
        assertThat(blocks.get(0).get("key")).isEqualTo("sla");
        assertThat(blocks.get(1).get("key")).isEqualTo("customer_profile");
        assertThat(blocks.get(1).get("ready")).isEqualTo(false);
        assertThat(blocks.get(1).get("status")).isEqualTo("attention");
    }

    @Test
    void buildBlocksHealthReportsMissingRequiredBlocks() {
        Map<String, Object> health = service.buildBlocksHealth(List.of(
                Map.of("key", "customer_profile", "label", "Профиль", "required", true, "ready", false),
                Map.of("key", "sla", "label", "SLA", "required", true, "ready", true),
                Map.of("key", "history", "label", "История", "required", false, "ready", false)
        ));

        assertThat(health.get("enabled")).isEqualTo(true);
        assertThat(health.get("ready")).isEqualTo(false);
        assertThat(health.get("coverage_pct")).isEqualTo(50);
        assertThat(health.get("missing_required_keys")).isEqualTo(List.of("customer_profile"));
        assertThat(health.get("missing_required_labels")).isEqualTo(List.of("Профиль"));
    }
}
