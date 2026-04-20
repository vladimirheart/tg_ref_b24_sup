package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceClientProfileServiceTest {

    private final DialogWorkspaceClientProfileService service = new DialogWorkspaceClientProfileService();

    @Test
    void buildClientSegmentsCombinesDialogSignalsAndProfileThresholds() {
        List<String> segments = service.buildClientSegments(
                sampleDialog(),
                Map.of(
                        "total_dialogs", 8,
                        "open_dialogs", 4,
                        "resolved_30d", 0
                ),
                Map.of(
                        "dialog_config", Map.of(
                                "workspace_segment_high_lifetime_volume_min_dialogs", 5,
                                "workspace_segment_multi_open_dialogs_min_open", 2,
                                "workspace_segment_reactivation_risk_min_dialogs", 3,
                                "workspace_segment_open_backlog_min_open", 3
                        )
                )
        );

        assertThat(segments).contains(
                "needs_reply",
                "unassigned",
                "low_csat_risk",
                "new_dialog",
                "high_lifetime_volume",
                "multi_open_dialogs",
                "reactivation_risk",
                "open_backlog_pressure"
        );
    }

    @Test
    void buildProfileHealthUsesSegmentSpecificRequiredFields() {
        Map<String, Object> profileHealth = service.buildProfileHealth(
                Map.of(
                        "dialog_config", Map.of(
                                "workspace_required_client_attributes", List.of("business"),
                                "workspace_required_client_attributes_by_segment", Map.of(
                                        "vip", List.of("location", "crm_id")
                                )
                        )
                ),
                Map.of(
                        "segments", List.of("vip"),
                        "business", "Retail",
                        "location", "Moscow"
                ),
                Map.of("crm_id", "CRM ID")
        );

        assertThat(profileHealth.get("enabled")).isEqualTo(true);
        assertThat(profileHealth.get("ready")).isEqualTo(false);
        assertThat(profileHealth.get("required_fields")).isEqualTo(List.of("business", "location", "crm_id"));
        assertThat(profileHealth.get("missing_fields")).isEqualTo(List.of("crm_id"));
        assertThat(profileHealth.get("missing_field_labels")).isEqualTo(List.of("CRM ID"));
    }

    private DialogListItem sampleDialog() {
        return new DialogListItem(
                "T-777",
                777L,
                1001L,
                "client_username",
                "Клиент",
                "sales",
                44L,
                "Telegram",
                "Moscow",
                "Moscow",
                "message preview",
                "2026-04-20T10:00:00Z",
                "new",
                false,
                null,
                "",
                "20.04.2026",
                "10:00:00",
                "vip",
                "client",
                "2026-04-20T10:01:00Z",
                2,
                1,
                "billing"
        );
    }
}
