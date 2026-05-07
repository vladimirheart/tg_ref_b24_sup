package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceRolloutContextContractServiceTest {

    @Test
    void buildContextContractFlagsInvalidReviewTimestamp() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        DialogWorkspaceRolloutGovernanceConfigService configService =
                new DialogWorkspaceRolloutGovernanceConfigService(sharedConfigService);
        DialogWorkspaceRolloutContextContractService service =
                new DialogWorkspaceRolloutContextContractService(configService);

        DialogWorkspaceRolloutGovernanceConfig config = new DialogWorkspaceRolloutGovernanceConfig(
                true, false, "", "", 168,
                0, "", "", "", null, "",
                List.of(), List.of(), false, false, false, null, "",
                0, List.of(),
                List.of(), Map.of(),
                "", "", "",
                "", "", "", null, 168,
                null, null, null, null,
                List.of(), false, false, 3, List.of(), "", false,
                true, List.of("incident"), List.of("full_name"), Map.of(),
                List.of("full_name:crm"), Map.of(), List.of("customer"), Map.of(),
                Map.of("field:full_name", Map.of("owner", "ops", "summary", "guide")),
                "ops-owner", "bad-ts", "", 168
        );

        DialogWorkspaceRolloutSectionResult result = service.buildContextContract(config, Map.of());

        assertThat(result.status()).isEqualTo("hold");
        assertThat(result.currentValue()).isEqualTo("invalid_utc");
        assertThat((Map<String, Object>) result.payload()).containsEntry("review_timestamp_invalid", true);
        assertThat((Map<String, Object>) result.payload()).containsEntry("operator_summary", "Review checkpoint содержит невалидный UTC timestamp.");
    }
}
