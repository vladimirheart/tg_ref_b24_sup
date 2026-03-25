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
                .contains("id=\"dialogWorkspaceGovernanceReviewDecisionAction\"")
                .contains("id=\"dialogWorkspaceGovernanceReviewIncidentFollowup\"")
                .contains("dialog_workspace_rollout_governance_review_decision_required")
                .contains("dialog_workspace_rollout_governance_incident_followup_required")
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
                .contains("dialog_macro_governance_require_owner")
                .contains("dialog_macro_governance_require_namespace")
                .contains("dialog_macro_governance_require_review")
                .contains("dialog_macro_governance_review_ttl_hours")
                .contains("dialog_macro_governance_deprecation_requires_reason")
                .contains("dialog_macro_governance_unused_days");
    }
}
