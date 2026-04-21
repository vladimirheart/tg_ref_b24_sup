package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceContextContractServiceTest {

    private final DialogWorkspaceContextContractService service =
            new DialogWorkspaceContextContractService(new DialogWorkspaceClientProfileService());

    @Test
    void buildContextContractReturnsReadyWhenMandatoryFieldsSourcesAndBlocksArePresent() {
        Map<String, Object> payload = service.buildContextContract(
                Map.of("dialog_config", Map.of(
                        "workspace_rollout_context_contract_required", true,
                        "workspace_rollout_context_contract_scenarios", List.of("billing"),
                        "workspace_rollout_context_contract_mandatory_fields_by_scenario", Map.of("billing", List.of("phone")),
                        "workspace_rollout_context_contract_source_of_truth_by_scenario", Map.of("billing", List.of("phone:crm")),
                        "workspace_rollout_context_contract_priority_blocks_by_scenario", Map.of("billing", List.of("profile_card"))
                )),
                sampleDialog(),
                Map.of(
                        "phone", "+79990000000",
                        "attribute_labels", Map.of("phone", "Телефон")
                ),
                List.of(Map.of(
                        "key", "crm",
                        "label", "CRM",
                        "matched_attributes", List.of("phone"),
                        "ready", true,
                        "status", "ready"
                )),
                List.of(Map.of(
                        "key", "profile_card",
                        "label", "Профиль",
                        "ready", true
                ))
        );

        assertThat(payload.get("enabled")).isEqualTo(true);
        assertThat(payload.get("required")).isEqualTo(true);
        assertThat(payload.get("definition_ready")).isEqualTo(true);
        assertThat(payload.get("ready")).isEqualTo(true);
        assertThat(payload.get("active_scenarios")).asList().containsExactly("billing");
        assertThat(payload.get("violations")).asList().isEmpty();
    }

    @Test
    void buildContextContractBuildsViolationDetailsWhenMandatoryFieldSourceAndBlockAreMissing() {
        Map<String, Object> payload = service.buildContextContract(
                Map.of("dialog_config", Map.of(
                        "workspace_rollout_context_contract_required", true,
                        "workspace_rollout_context_contract_scenarios", List.of("billing"),
                        "workspace_rollout_context_contract_mandatory_fields", List.of("phone"),
                        "workspace_rollout_context_contract_source_of_truth", List.of("phone:crm"),
                        "workspace_rollout_context_contract_priority_blocks", List.of("profile_card"),
                        "workspace_rollout_context_contract_playbooks", Map.of(
                                "mandatory_field", Map.of(
                                        "label", "Карточка клиента",
                                        "url", "https://kb.test/playbooks/mandatory-field",
                                        "summary", "Как дозаполнить карточку"
                                )
                        )
                )),
                sampleDialog(),
                Map.of(
                        "name", "Клиент",
                        "attribute_labels", Map.of("phone", "Телефон")
                ),
                List.of(),
                List.of()
        );

        assertThat(payload.get("ready")).isEqualTo(false);
        assertThat(payload.get("missing_mandatory_fields")).asList().containsExactly("phone");
        assertThat(payload.get("source_of_truth_violations")).asList().containsExactly("phone:crm:source_missing");
        assertThat(payload.get("missing_priority_blocks")).asList().containsExactly("profile_card");
        assertThat(payload.get("operator_summary")).isEqualTo("Сначала заполните обязательные поля клиента.");
        assertThat(payload.get("next_step_summary").toString()).contains("Сначала дозаполните поля");
        assertThat(payload.get("violation_details")).asList().hasSize(3);
        assertThat(((Map<?, ?>) ((List<?>) payload.get("violation_details")).get(0)).get("action_label")).isEqualTo("Открыть playbook");
        assertThat(((List<?>) payload.get("primary_violation_details"))).hasSize(2);
    }

    private DialogListItem sampleDialog() {
        return new DialogListItem(
                "T-501",
                501L,
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
                "open",
                false,
                null,
                "operator",
                "20.04.2026",
                "10:00:00",
                "vip",
                "client",
                "2026-04-20T10:01:00Z",
                1,
                5,
                "billing"
        );
    }
}
