package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceRolloutGovernanceConfigServiceTest {

    @Test
    void loadConfigNormalizesDecisionActionsAndCollections() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "workspace_rollout_governance_review_decision_action", "GO",
                        "workspace_rollout_governance_previous_decision_action", "ROLLBACK",
                        "workspace_rollout_governance_legacy_usage_decision", "hold",
                        "workspace_rollout_governance_legacy_only_scenarios", "attachments_edit,\ninline_reopen",
                        "workspace_rollout_context_contract_priority_blocks_by_scenario", Map.of("billing", List.of("profile_card", "SLA")),
                        "workspace_rollout_context_contract_playbooks", Map.of(
                                "mandatory_field:billing:phone", Map.of("label", "Phone guide", "url", "https://wiki.example.local/context/phone")
                        )
                )
        ));

        DialogWorkspaceRolloutGovernanceConfigService service =
                new DialogWorkspaceRolloutGovernanceConfigService(sharedConfigService);

        DialogWorkspaceRolloutGovernanceConfig config = service.loadConfig();

        assertThat(config.reviewDecisionAction()).isEqualTo("go");
        assertThat(config.previousDecisionAction()).isEqualTo("rollback");
        assertThat(config.legacyUsageDecision()).isEqualTo("hold");
        assertThat(config.legacyOnlyScenarios()).containsExactly("attachments_edit", "inline_reopen");
        assertThat(config.contextContractPriorityBlocksByScenario()).containsEntry("billing", List.of("profile_card", "sla"));
        assertThat(config.contextContractPlaybooks()).containsKey("mandatory_field:billing:phone");
        assertThat(config.contextContractPlaybooks().get("mandatory_field:billing:phone"))
                .containsEntry("url", "https://wiki.example.local/context/phone");
    }

    @Test
    void contextContractPlaybookCoverageSupportsScopedAndGenericLegacyKeys() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        DialogWorkspaceRolloutGovernanceConfigService service =
                new DialogWorkspaceRolloutGovernanceConfigService(sharedConfigService);

        Map<String, Map<String, String>> playbooks = Map.of(
                "mandatory_field:full_name", Map.of("url", "https://wiki.example.local/context/name"),
                "source_of_truth", Map.of("url", "https://wiki.example.local/context/source"),
                "priority_block", Map.of("url", "https://wiki.example.local/context/priority")
        );

        assertThat(service.hasContextContractPlaybookCoverage(playbooks, "mandatory_field:full_name")).isTrue();
        assertThat(service.hasContextContractPlaybookCoverage(playbooks, "source_of_truth:crm_tier:crm")).isTrue();
        assertThat(service.hasContextContractPlaybookCoverage(playbooks, "priority_block:customer")).isTrue();
    }
}
