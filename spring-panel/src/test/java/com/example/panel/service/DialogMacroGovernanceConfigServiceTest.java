package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogMacroGovernanceConfigServiceTest {

    @Test
    void resolveNormalizesTierThresholdsAndTemplates() {
        DialogMacroGovernanceSupportService supportService = mock(DialogMacroGovernanceSupportService.class);
        when(supportService.resolveKnownMacroVariableKeys(any())).thenReturn(Set.of("client_name"));
        DialogMacroGovernanceConfigService service = new DialogMacroGovernanceConfigService(supportService);

        DialogMacroGovernanceConfigService.AuditConfig config = service.resolve(Map.of(
                "dialog_config", Map.of(
                        "macro_governance_usage_tier_low_max", 5,
                        "macro_governance_usage_tier_medium_max", 2,
                        "macro_templates", List.of(Map.of("id", "macro-1"))
                )
        ));

        assertThat(config.templates()).hasSize(1);
        assertThat(config.usageTierLowMax()).isEqualTo(5);
        assertThat(config.usageTierMediumMax()).isEqualTo(5);
        assertThat(config.knownMacroVariables()).contains("client_name");
    }

    @Test
    void parseReviewTimestampAcceptsEpochMillis() {
        DialogMacroGovernanceSupportService supportService = mock(DialogMacroGovernanceSupportService.class);
        DialogMacroGovernanceConfigService service = new DialogMacroGovernanceConfigService(supportService);

        String raw = String.valueOf(Instant.parse("2026-03-28T10:15:30Z").toEpochMilli());

        assertThat(service.parseReviewTimestamp(raw)).isNotNull();
        assertThat(service.parseReviewTimestamp(raw).toInstant()).isEqualTo(Instant.parse("2026-03-28T10:15:30Z"));
    }
}
