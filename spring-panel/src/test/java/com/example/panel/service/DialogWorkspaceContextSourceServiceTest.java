package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceContextSourceServiceTest {

    private final DialogWorkspaceClientProfileService profileService = new DialogWorkspaceClientProfileService();
    private final DialogWorkspaceContextSourceService service = new DialogWorkspaceContextSourceService(profileService);

    @Test
    void buildContextSourcesAppliesFreshnessAndMissingSourcePolicies() {
        List<Map<String, Object>> sources = service.buildContextSources(
                Map.of(
                        "dialog_config", Map.of(
                                "workspace_client_context_required_sources", List.of("crm", "external"),
                                "workspace_client_context_source_stale_after_hours", 24,
                                "workspace_client_context_source_updated_at_attributes", Map.of(
                                        "crm", List.of("crm_updated_at")
                                )
                        )
                ),
                Map.of(
                        "name", "Клиент",
                        "crm_customer_id", "C-1",
                        "crm_updated_at", "2026-04-01T10:00:00Z"
                ),
                Map.of(),
                Map.of(),
                Map.of()
        );

        Map<String, Object> crm = sources.stream().filter(item -> "crm".equals(item.get("key"))).findFirst().orElseThrow();
        Map<String, Object> external = sources.stream().filter(item -> "external".equals(item.get("key"))).findFirst().orElseThrow();

        assertThat(crm.get("status")).isEqualTo("stale");
        assertThat(crm.get("ready")).isEqualTo(false);
        assertThat(external.get("status")).isEqualTo("missing");
        assertThat(external.get("required")).isEqualTo(true);
    }

    @Test
    void buildContextAttributePoliciesReflectsPreferredSourceState() {
        List<Map<String, Object>> policies = service.buildContextAttributePolicies(
                Map.of(
                        "attribute_labels", Map.of("crm_customer_id", "CRM ID"),
                        "crm_customer_id", "C-1"
                ),
                Map.of(
                        "enabled", true,
                        "required_fields", List.of("crm_customer_id")
                ),
                List.of(
                        Map.of(
                                "key", "crm",
                                "label", "CRM",
                                "status", "stale",
                                "ready", false,
                                "matched_attributes", List.of("crm_customer_id"),
                                "freshness_ttl_hours", 24
                        )
                )
        );

        assertThat(policies).hasSize(1);
        Map<String, Object> policy = policies.get(0);
        assertThat(policy.get("key")).isEqualTo("crm_customer_id");
        assertThat(policy.get("status")).isEqualTo("stale");
        assertThat(policy.get("source_key")).isEqualTo("crm");
        assertThat(policy.get("ready")).isEqualTo(false);
    }
}
