package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogMacroGovernanceAuditServiceTest {

    @Test
    void delegatesAuditBuildToDialogService() {
        DialogService dialogService = mock(DialogService.class);
        DialogMacroGovernanceAuditService service = new DialogMacroGovernanceAuditService(dialogService);
        Map<String, Object> settings = Map.of("dialog_config", Map.of("macro_templates", java.util.List.of()));
        when(dialogService.buildMacroGovernanceAudit(settings))
                .thenReturn(Map.of("status", "ok", "issues_total", 0));

        Map<String, Object> audit = service.buildAudit(settings);

        assertThat(audit).containsEntry("status", "ok");
        verify(dialogService).buildMacroGovernanceAudit(settings);
    }
}
