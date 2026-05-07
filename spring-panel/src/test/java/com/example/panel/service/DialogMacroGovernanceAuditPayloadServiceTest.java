package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DialogMacroGovernanceAuditPayloadServiceTest {

    @Test
    void buildCalculatesWeeklyPriorityFromCheckpointAndAdvisoryState() {
        DialogMacroGovernanceConfigService.AuditConfig config = new DialogMacroGovernanceConfigService.AuditConfig(
                Map.of(),
                OffsetDateTime.now(ZoneOffset.UTC),
                List.of(Map.of("id", "macro-1")),
                true,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                24L,
                30,
                0,
                7,
                0,
                5,
                7,
                30,
                90,
                14,
                45,
                120,
                Set.of("client_name")
        );
        DialogMacroGovernanceTemplateAuditService.TemplateAuditBundle templateBundle =
                new DialogMacroGovernanceTemplateAuditService.TemplateAuditBundle(
                        List.of(Map.of("template_id", "macro-1")),
                        List.of(Map.of("type", "unused_recently", "classification", "backlog_candidate", "status", "attention")),
                        1, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 0,
                        List.of("macro-1")
                );
        DialogMacroGovernanceCheckpointService.CheckpointBundle checkpoints =
                new DialogMacroGovernanceCheckpointService.CheckpointBundle(
                        List.of(),
                        Map.of("required", false),
                        Map.of("required", false),
                        Map.of("required", false),
                        List.of(),
                        List.of("red_list"),
                        0L,
                        0L,
                        100L,
                        true,
                        0L,
                        0L,
                        100L
                );

        Map<String, Object> audit = new DialogMacroGovernanceAuditPayloadService().build(config, templateBundle, checkpoints);

        assertThat(audit).containsEntry("status", "attention");
        assertThat(audit).containsEntry("weekly_review_priority", "monitor_low_signal_advisories");
        assertThat(audit).containsEntry("red_list_total", 1);
        assertThat(audit).containsEntry("issues_total", 1);
    }

    @Test
    void buildTreatsAdvisoryLoadPerActiveTemplateAsNoiseRatio() {
        DialogMacroGovernanceConfigService.AuditConfig config = new DialogMacroGovernanceConfigService.AuditConfig(
                Map.of(),
                OffsetDateTime.now(ZoneOffset.UTC),
                List.of(Map.of("id", "macro-1"), Map.of("id", "macro-2"), Map.of("id", "macro-3")),
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                24L,
                30,
                0,
                7,
                0,
                5,
                7,
                30,
                90,
                14,
                45,
                120,
                Set.of("client_name")
        );
        List<Map<String, Object>> advisoryIssues = List.of(
                Map.of("type", "unused_recently", "classification", "backlog_candidate", "status", "attention"),
                Map.of("type", "alias_cleanup_required", "classification", "backlog_candidate", "status", "attention"),
                Map.of("type", "owner_action_required", "classification", "backlog_candidate", "status", "attention"),
                Map.of("type", "unknown_variables_detected", "classification", "backlog_candidate", "status", "attention"),
                Map.of("type", "deprecation_reason_missing", "classification", "backlog_candidate", "status", "attention"),
                Map.of("type", "unused_recently", "classification", "backlog_candidate", "status", "attention")
        );
        DialogMacroGovernanceTemplateAuditService.TemplateAuditBundle templateBundle =
                new DialogMacroGovernanceTemplateAuditService.TemplateAuditBundle(
                        List.of(Map.of("template_id", "macro-1")),
                        advisoryIssues,
                        3, 0, 0, 0, 0, 0, 1, 1, 1, 0, 3, 1, 1, 1, 0,
                        List.of()
                );
        DialogMacroGovernanceCheckpointService.CheckpointBundle checkpoints =
                new DialogMacroGovernanceCheckpointService.CheckpointBundle(
                        List.of(),
                        Map.of("required", false),
                        Map.of("required", false),
                        Map.of("required", false),
                        List.of(),
                        List.of("red_list", "owner_action"),
                        0L,
                        0L,
                        100L,
                        true,
                        0L,
                        0L,
                        100L
                );

        Map<String, Object> audit = new DialogMacroGovernanceAuditPayloadService().build(config, templateBundle, checkpoints);

        assertThat(audit).containsEntry("noise_ratio_pct", 100L);
        assertThat(audit).containsEntry("noise_level", "high");
    }
}
