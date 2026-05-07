package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceRolloutLegacyInventoryServiceTest {

    @Test
    void buildLegacyInventoryTracksQueueAndActionItemsForMissingOwners() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        DialogWorkspaceRolloutGovernanceConfigService configService =
                new DialogWorkspaceRolloutGovernanceConfigService(sharedConfigService);
        DialogWorkspaceRolloutLegacyInventoryService service =
                new DialogWorkspaceRolloutLegacyInventoryService(configService);

        DialogWorkspaceRolloutGovernanceConfig config = new DialogWorkspaceRolloutGovernanceConfig(
                true, false, "", "", 168,
                7, "", "", "", null, "",
                List.of(), List.of(), false, false, false, null, "",
                0, List.of(),
                List.of("attachments_edit", "inline_reopen"),
                Map.of(),
                "", "", "",
                "", "", "", null, 168,
                null, null, null, null,
                List.of(), false, false, 3, List.of(), "", false,
                false, List.of(), List.of(), Map.of(), List.of(), Map.of(),
                List.of(), Map.of(), Map.of(), "", "", "", 168
        );

        DialogWorkspaceRolloutSectionResult result = service.buildLegacyInventory(config, "");

        assertThat(result.status()).isEqualTo("hold");
        assertThat(result.currentValue()).contains("open=2");
        assertThat((Map<String, Object>) result.payload()).containsEntry("review_queue_count", 2);
        assertThat((Map<String, Object>) result.payload()).containsEntry("review_queue_followup_required", true);
        assertThat((Map<String, Object>) result.payload()).containsEntry("review_queue_consolidation_count", 2);
        assertThat((List<String>) ((Map<String, Object>) result.payload()).get("action_items"))
                .contains("Назначьте owner для всех legacy-only сценариев.");
    }
}
