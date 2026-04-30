package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AnalyticsMacroGovernancePolicyServiceTest {

    private final SharedConfigService sharedConfigService = mock(SharedConfigService.class);
    private final DialogAuditService dialogAuditService = mock(DialogAuditService.class);
    private final AnalyticsMacroGovernancePolicyService service =
            new AnalyticsMacroGovernancePolicyService(sharedConfigService, dialogAuditService);

    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("ops.lead", "n/a");

    @Test
    void updateMacroGovernanceReviewPersistsUtcReview() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>()
        )));

        ResponseEntity<?> response = service.updateMacroGovernanceReview(
                authentication,
                "ops.lead",
                "2026-03-24T23:50:00Z",
                "Namespace cleanup reviewed, owner follow-up planned.",
                "MACRO-101",
                "hold"
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(sharedConfigService).saveSettings(org.mockito.ArgumentMatchers.argThat(settings -> {
            Map<?, ?> dialogConfig = (Map<?, ?>) settings.get("dialog_config");
            return "ops.lead".equals(dialogConfig.get("macro_governance_reviewed_by"))
                    && "2026-03-24T23:50:00Z".equals(dialogConfig.get("macro_governance_reviewed_at"))
                    && "MACRO-101".equals(dialogConfig.get("macro_governance_cleanup_ticket_id"))
                    && "hold".equals(dialogConfig.get("macro_governance_review_decision"));
        }));
        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"), eq("workspace_macro_governance_review_updated"), eq("experiment"),
                eq(null), eq("analytics_macro_governance_review"), eq(null), eq("workspace.v1"),
                eq(null), eq("workspace_v1_rollout"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void updateMacroGovernanceReviewRejectsInvalidUtc() {
        ResponseEntity<?> response = service.updateMacroGovernanceReview(
                authentication,
                "ops.lead",
                "2026-03-25T02:10:00+03:00",
                null,
                null,
                null
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "success", false,
                "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"
        ));
    }

    @Test
    void updateMacroExternalCatalogPolicyPersistsUtcValues() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>()
        )));

        ResponseEntity<?> response = service.updateMacroExternalCatalogPolicy(
                authentication,
                "ops.lead",
                "2026-03-25T01:10:00Z",
                "2026.03.25",
                "2026.03.25",
                "External catalog contract validated.",
                "go",
                72
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(sharedConfigService).saveSettings(org.mockito.ArgumentMatchers.argThat(settings -> {
            Map<?, ?> dialogConfig = (Map<?, ?>) settings.get("dialog_config");
            return "ops.lead".equals(dialogConfig.get("macro_external_catalog_verified_by"))
                    && "2026-03-25T01:10:00Z".equals(dialogConfig.get("macro_external_catalog_verified_at"))
                    && "2026.03.25".equals(dialogConfig.get("macro_external_catalog_expected_version"))
                    && Long.valueOf(72L).equals(dialogConfig.get("macro_external_catalog_contract_ttl_hours"));
        }));
        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"), eq("workspace_macro_external_catalog_policy_updated"), eq("experiment"),
                eq(null), eq("analytics_macro_external_catalog_policy"), eq(null), eq("workspace.v1"),
                eq(null), eq("workspace_v1_rollout"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void updateMacroExternalCatalogPolicyRejectsInvalidUtc() {
        ResponseEntity<?> response = service.updateMacroExternalCatalogPolicy(
                authentication,
                "ops.lead",
                "2026-03-25T03:10:00+03:00",
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "success", false,
                "error", "verified_at_utc must be a valid UTC timestamp (ISO-8601)"
        ));
    }

    @Test
    void updateMacroDeprecationPolicyPersistsUtcValues() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>()
        )));

        ResponseEntity<?> response = service.updateMacroDeprecationPolicy(
                authentication,
                "ops.lead",
                "2026-03-25T01:40:00Z",
                "MACRO-DEP-42",
                "Deprecated templates scheduled for cleanup window.",
                "hold",
                96
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(sharedConfigService).saveSettings(org.mockito.ArgumentMatchers.argThat(settings -> {
            Map<?, ?> dialogConfig = (Map<?, ?>) settings.get("dialog_config");
            return "ops.lead".equals(dialogConfig.get("macro_deprecation_policy_reviewed_by"))
                    && "2026-03-25T01:40:00Z".equals(dialogConfig.get("macro_deprecation_policy_reviewed_at"))
                    && "MACRO-DEP-42".equals(dialogConfig.get("macro_deprecation_policy_ticket_id"))
                    && Long.valueOf(96L).equals(dialogConfig.get("macro_deprecation_policy_ttl_hours"));
        }));
        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"), eq("workspace_macro_deprecation_policy_updated"), eq("experiment"),
                eq(null), eq("analytics_macro_deprecation_policy"), eq(null), eq("workspace.v1"),
                eq(null), eq("workspace_v1_rollout"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void updateMacroDeprecationPolicyRejectsInvalidUtc() {
        ResponseEntity<?> response = service.updateMacroDeprecationPolicy(
                authentication,
                "ops.lead",
                "2026-03-25T04:40:00+03:00",
                null,
                null,
                null,
                null
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "success", false,
                "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"
        ));
    }
}
