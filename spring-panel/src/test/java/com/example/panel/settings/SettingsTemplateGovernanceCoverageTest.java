package com.example.panel.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsTemplateGovernanceCoverageTest {

    private static final Path SETTINGS_TEMPLATE = Path.of("src/main/resources/templates/settings/index.html");

    @Test
    void settingsTemplateContainsWorkspaceReviewGovernanceFields() throws IOException {
        String html = Files.readString(SETTINGS_TEMPLATE);

        assertThat(html)
                .contains("id=\"dialogWorkspaceGovernanceReviewDecisionRequired\"")
                .contains("id=\"dialogWorkspaceGovernanceIncidentFollowupRequired\"")
                .contains("id=\"dialogWorkspaceGovernanceLegacyBlockedReasonsReviewRequired\"")
                .contains("id=\"dialogWorkspaceGovernanceLegacyBlockedReasonsTopN\"")
                .contains("id=\"dialogWorkspaceGovernanceReviewDecisionAction\"")
                .contains("id=\"dialogWorkspaceGovernanceReviewIncidentFollowup\"")
                .contains("dialog_workspace_rollout_governance_review_decision_required")
                .contains("dialog_workspace_rollout_governance_incident_followup_required")
                .contains("dialog_workspace_rollout_governance_legacy_blocked_reasons_review_required")
                .contains("dialog_workspace_rollout_governance_legacy_blocked_reasons_top_n")
                .contains("dialog_workspace_rollout_governance_review_decision_action")
                .contains("dialog_workspace_rollout_governance_review_incident_followup");
    }

    @Test
    void settingsTemplateContainsMacroGovernanceBaselineFields() throws IOException {
        String html = Files.readString(SETTINGS_TEMPLATE);

        assertThat(html)
                .contains("id=\"dialogMacroGovernanceRequireOwner\"")
                .contains("id=\"dialogMacroGovernanceRequireNamespace\"")
                .contains("id=\"dialogMacroGovernanceRequireReview\"")
                .contains("id=\"dialogMacroGovernanceReviewTtlHours\"")
                .contains("id=\"dialogMacroGovernanceDeprecationRequiresReason\"")
                .contains("id=\"dialogMacroGovernanceUnusedDays\"")
                .contains("id=\"dialogMacroGovernanceRedListEnabled\"")
                .contains("id=\"dialogMacroGovernanceRedListUsageMax\"")
                .contains("id=\"dialogMacroGovernanceOwnerActionRequired\"")
                .contains("id=\"dialogMacroGovernanceCleanupCadenceDays\"")
                .contains("id=\"dialogMacroGovernanceAliasCleanupRequired\"")
                .contains("id=\"dialogMacroGovernanceVariableCleanupRequired\"")
                .contains("id=\"dialogMacroGovernanceUsageTierSlaRequired\"")
                .contains("id=\"dialogMacroGovernanceUsageTierLowMax\"")
                .contains("id=\"dialogMacroGovernanceUsageTierMediumMax\"")
                .contains("id=\"dialogMacroGovernanceCleanupSlaLowDays\"")
                .contains("id=\"dialogMacroGovernanceCleanupSlaMediumDays\"")
                .contains("id=\"dialogMacroGovernanceCleanupSlaHighDays\"")
                .contains("id=\"dialogMacroGovernanceDeprecationSlaLowDays\"")
                .contains("id=\"dialogMacroGovernanceDeprecationSlaMediumDays\"")
                .contains("id=\"dialogMacroGovernanceDeprecationSlaHighDays\"")
                .contains("dialog_macro_governance_require_owner")
                .contains("dialog_macro_governance_require_namespace")
                .contains("dialog_macro_governance_require_review")
                .contains("dialog_macro_governance_review_ttl_hours")
                .contains("dialog_macro_governance_deprecation_requires_reason")
                .contains("dialog_macro_governance_unused_days")
                .contains("dialog_macro_governance_red_list_enabled")
                .contains("dialog_macro_governance_red_list_usage_max")
                .contains("dialog_macro_governance_owner_action_required")
                .contains("dialog_macro_governance_cleanup_cadence_days")
                .contains("dialog_macro_governance_alias_cleanup_required")
                .contains("dialog_macro_governance_variable_cleanup_required")
                .contains("dialog_macro_governance_usage_tier_sla_required")
                .contains("dialog_macro_governance_usage_tier_low_max")
                .contains("dialog_macro_governance_usage_tier_medium_max")
                .contains("dialog_macro_governance_cleanup_sla_low_days")
                .contains("dialog_macro_governance_cleanup_sla_medium_days")
                .contains("dialog_macro_governance_cleanup_sla_high_days")
                .contains("dialog_macro_governance_deprecation_sla_low_days")
                .contains("dialog_macro_governance_deprecation_sla_medium_days")
                .contains("dialog_macro_governance_deprecation_sla_high_days");
    }

    @Test
    void settingsTemplateContainsSlaPolicyGovernanceBaselineFields() throws IOException {
        String html = Files.readString(SETTINGS_TEMPLATE);

        assertThat(html)
                .contains("id=\"dialogSlaPolicyAuditRequireLayers\"")
                .contains("id=\"dialogSlaPolicyAuditRequireOwner\"")
                .contains("id=\"dialogSlaPolicyAuditRequireReview\"")
                .contains("id=\"dialogSlaPolicyAuditReviewTtlHours\"")
                .contains("id=\"dialogSlaPolicyAuditBroadRuleCoveragePct\"")
                .contains("id=\"dialogSlaPolicyAuditBlockOnConflicts\"")
                .contains("id=\"dialogSlaPolicyGovernanceReviewRequired\"")
                .contains("id=\"dialogSlaPolicyGovernanceReviewPath\"")
                .contains("id=\"dialogSlaPolicyGovernanceReviewTtlHours\"")
                .contains("id=\"dialogSlaPolicyGovernanceDryRunTicketRequired\"")
                .contains("id=\"dialogSlaPolicyGovernanceDecisionRequired\"")
                .contains("dialog_sla_critical_auto_assign_audit_require_layers")
                .contains("dialog_sla_critical_auto_assign_audit_require_owner")
                .contains("dialog_sla_critical_auto_assign_audit_require_review")
                .contains("dialog_sla_critical_auto_assign_audit_review_ttl_hours")
                .contains("dialog_sla_critical_auto_assign_audit_broad_rule_coverage_pct")
                .contains("dialog_sla_critical_auto_assign_audit_block_on_conflicts")
                .contains("dialog_sla_critical_auto_assign_governance_review_required")
                .contains("dialog_sla_critical_auto_assign_governance_review_path")
                .contains("dialog_sla_critical_auto_assign_governance_review_ttl_hours")
                .contains("dialog_sla_critical_auto_assign_governance_dry_run_ticket_required")
                .contains("dialog_sla_critical_auto_assign_governance_decision_required");
    }

    @Test
    void settingsTemplateContainsItEquipmentSerialAndAccessoriesFields() throws IOException {
        String html = Files.readString(SETTINGS_TEMPLATE);

        assertThat(html)
                .contains("id=\"itEquipmentSerialNumberInput\"")
                .contains("data-it-equipment-field=\"serial_number\"")
                .contains("id=\"itEquipmentAccessoriesInput\"")
                .contains("data-it-equipment-field=\"accessories\"")
                .contains("<th>Серийный номер</th>")
                .contains("<th>Комплектация</th>")
                .contains("data-field=\"serial_number\"")
                .contains("data-field=\"accessories\"");
    }
}
