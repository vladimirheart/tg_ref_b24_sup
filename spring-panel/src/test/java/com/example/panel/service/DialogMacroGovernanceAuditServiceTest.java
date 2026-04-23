package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogMacroGovernanceAuditServiceTest {

    @Test
    void returnsOffStatusWhenMacroTemplatesAreMissing() {
        DialogMacroGovernanceSupportService supportService = mock(DialogMacroGovernanceSupportService.class);
        DialogMacroGovernanceAuditService service = new DialogMacroGovernanceAuditService(supportService);

        Map<String, Object> audit = service.buildAudit(Map.of());

        assertThat(audit).containsEntry("status", "off");
        assertThat(audit).containsEntry("templates_total", 0);
        assertThat(audit).containsEntry("issues_total", 0);
    }

    @Test
    void reportsMissingOwnerForPublishedTemplateWhenOwnerIsRequired() {
        DialogMacroGovernanceSupportService supportService = mock(DialogMacroGovernanceSupportService.class);
        DialogMacroGovernanceAuditService service = new DialogMacroGovernanceAuditService(supportService);
        when(supportService.resolveKnownMacroVariableKeys(any())).thenReturn(Set.of("client_name"));
        when(supportService.loadMacroTemplateUsage(eq("macro-1"), eq("Welcome"), eq(30)))
                .thenReturn(Map.of("usage_count", 0L, "preview_count", 0L, "error_count", 0L, "last_used_at", ""));
        when(supportService.resolveMacroTagAliases(any())).thenReturn(List.of());
        when(supportService.extractMacroTemplateVariables(any())).thenReturn(List.of());
        when(supportService.resolveMacroUsageTier(eq(0L), eq(0), eq(5))).thenReturn("low");
        when(supportService.resolveMacroTierSlaDays(eq("low"), eq(7), eq(30), eq(90))).thenReturn(7);
        when(supportService.resolveMacroTierSlaDays(eq("low"), eq(14), eq(45), eq(120))).thenReturn(14);
        when(supportService.buildMacroGovernanceIssue(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> Map.of(
                        "type", invocation.getArgument(0),
                        "status", invocation.getArgument(3),
                        "classification", invocation.getArgument(4)));

        Map<String, Object> settings = Map.of(
                "dialog_config", Map.of(
                        "macro_governance_require_owner", true,
                        "macro_templates", List.of(Map.of(
                                "id", "macro-1",
                                "name", "Welcome",
                                "message", "Hello",
                                "published", true,
                                "deprecated", false
                        ))
                )
        );

        Map<String, Object> audit = service.buildAudit(settings);

        assertThat(audit).containsEntry("status", "hold");
        assertThat(audit).containsEntry("missing_owner_total", 1);
        assertThat((List<?>) audit.get("issues")).hasSizeGreaterThanOrEqualTo(1);
    }
}
