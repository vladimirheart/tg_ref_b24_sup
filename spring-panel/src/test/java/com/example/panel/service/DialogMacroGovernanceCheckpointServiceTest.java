package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogMacroGovernanceCheckpointServiceTest {

    @Test
    void evaluateBuildsRequiredCheckpointIssues() {
        DialogMacroGovernanceSupportService supportService = mock(DialogMacroGovernanceSupportService.class);
        when(supportService.resolveKnownMacroVariableKeys(any())).thenReturn(Set.of());
        when(supportService.buildMacroGovernanceIssue(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Map.of("type", invocation.getArgument(0), "status", invocation.getArgument(3)));
        DialogMacroGovernanceConfigService configService = new DialogMacroGovernanceConfigService(supportService);
        DialogMacroGovernanceCheckpointService service = new DialogMacroGovernanceCheckpointService(supportService, configService);
        DialogMacroGovernanceConfigService.AuditConfig config = configService.resolve(Map.of(
                "dialog_config", Map.of(
                        "macro_governance_review_required", true,
                        "macro_external_catalog_contract_required", true,
                        "macro_deprecation_policy_required", true,
                        "macro_templates", List.of(Map.of("id", "macro-1"))
                )
        ));

        DialogMacroGovernanceCheckpointService.CheckpointBundle bundle = service.evaluate(config, 0);

        assertThat(bundle.minimumRequiredCheckpoints()).containsExactly("governance_review", "external_catalog");
        assertThat(bundle.requiredCheckpointTotal()).isEqualTo(2L);
        assertThat(bundle.freshnessCheckpointTotal()).isEqualTo(3L);
        assertThat(bundle.issues()).extracting(item -> item.get("type"))
                .contains("governance_review_missing", "external_catalog_expected_version_missing", "deprecation_policy_review_missing");
    }

    @Test
    void evaluateFallsBackToGovernanceAndExternalCatalogPathWhenReviewIsRequired() {
        DialogMacroGovernanceSupportService supportService = mock(DialogMacroGovernanceSupportService.class);
        when(supportService.resolveKnownMacroVariableKeys(any())).thenReturn(Set.of());
        when(supportService.buildMacroGovernanceIssue(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Map.of("type", invocation.getArgument(0), "status", invocation.getArgument(3)));
        DialogMacroGovernanceConfigService configService = new DialogMacroGovernanceConfigService(supportService);
        DialogMacroGovernanceCheckpointService service = new DialogMacroGovernanceCheckpointService(supportService, configService);
        DialogMacroGovernanceConfigService.AuditConfig config = configService.resolve(Map.of(
                "dialog_config", Map.of(
                        "macro_governance_require_review", true,
                        "macro_templates", List.of(Map.of("id", "macro-1"))
                )
        ));

        DialogMacroGovernanceCheckpointService.CheckpointBundle bundle = service.evaluate(config, 0);

        assertThat(bundle.minimumRequiredCheckpoints()).containsExactly("governance_review", "external_catalog");
    }

    @Test
    void evaluateCountsTemplateOwnerInsideFreshnessCheckpointMetrics() {
        DialogMacroGovernanceSupportService supportService = mock(DialogMacroGovernanceSupportService.class);
        when(supportService.resolveKnownMacroVariableKeys(any())).thenReturn(Set.of());
        when(supportService.buildMacroGovernanceIssue(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Map.of("type", invocation.getArgument(0), "status", invocation.getArgument(3)));
        DialogMacroGovernanceConfigService configService = new DialogMacroGovernanceConfigService(supportService);
        DialogMacroGovernanceCheckpointService service = new DialogMacroGovernanceCheckpointService(supportService, configService);
        DialogMacroGovernanceConfigService.AuditConfig config = configService.resolve(Map.of(
                "dialog_config", Map.of(
                        "macro_governance_require_owner", true,
                        "macro_governance_require_review", true,
                        "macro_templates", List.of(Map.of("id", "macro-1"))
                )
        ));

        DialogMacroGovernanceCheckpointService.CheckpointBundle bundle = service.evaluate(config, 1);

        assertThat(bundle.requiredCheckpointTotal()).isEqualTo(2L);
        assertThat(bundle.freshnessCheckpointTotal()).isEqualTo(3L);
        assertThat(bundle.freshnessCheckpointReadyTotal()).isZero();
    }
}
