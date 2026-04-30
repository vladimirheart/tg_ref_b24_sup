package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogServiceTest {

    @Test
    void summarizeDataAccessExceptionPrefersRootCauseWithoutSqlText() {
        SQLException sqlException = new SQLException("[SQLITE_ERROR] SQL error or missing database (no such column: t.created_at)");
        UncategorizedSQLException exception = new UncategorizedSQLException(
                "PreparedStatementCallback",
                "SELECT * FROM tickets WHERE created_at IS NOT NULL",
                sqlException
        );

        assertThat(DialogDataAccessSupport.summarizeDataAccessException(exception))
                .isEqualTo("[SQLITE_ERROR] SQL error or missing database (no such column: t.created_at)");
    }

    @Test
    void summarizeDataAccessExceptionFallsBackToTrimmedMessage() {
        InvalidDataAccessResourceUsageException exception = new InvalidDataAccessResourceUsageException(
                "PreparedStatementCallback; uncategorized SQLException for SQL [SELECT * FROM tickets]"
        );

        assertThat(DialogDataAccessSupport.summarizeDataAccessException(exception))
                .isEqualTo("PreparedStatementCallback; uncategorized SQLException for SQL [SELECT * FROM tickets]");
    }

    @Test
    void delegatesMacroGovernanceAuditToDedicatedService() {
        DialogMacroGovernanceAuditService macroGovernanceAuditService = mock(DialogMacroGovernanceAuditService.class);
        DialogWorkspaceTelemetrySummaryAssemblerService telemetrySummaryAssemblerService = mock(DialogWorkspaceTelemetrySummaryAssemblerService.class);
        DialogService dialogService = new DialogService(
                telemetrySummaryAssemblerService,
                macroGovernanceAuditService,
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogClientContextReadService.class),
                mock(DialogConversationReadService.class),
                mock(DialogDetailsReadService.class),
                mock(DialogAuditService.class),
                mock(DialogTicketLifecycleService.class)
        );
        Map<String, Object> settings = Map.of("dialog_config", Map.of("macro_templates", java.util.List.of()));
        Map<String, Object> expected = Map.of("status", "off", "templates_total", 0);
        when(macroGovernanceAuditService.buildAudit(settings)).thenReturn(expected);

        Map<String, Object> actual = dialogService.buildMacroGovernanceAudit(settings);

        assertThat(actual).isEqualTo(expected);
        verify(macroGovernanceAuditService).buildAudit(settings);
    }

    @Test
    void delegatesWorkspaceTelemetrySummaryToAssemblerService() {
        DialogWorkspaceTelemetrySummaryAssemblerService telemetrySummaryAssemblerService = mock(DialogWorkspaceTelemetrySummaryAssemblerService.class);
        DialogService dialogService = new DialogService(
                telemetrySummaryAssemblerService,
                mock(DialogMacroGovernanceAuditService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogClientContextReadService.class),
                mock(DialogConversationReadService.class),
                mock(DialogDetailsReadService.class),
                mock(DialogAuditService.class),
                mock(DialogTicketLifecycleService.class)
        );
        Map<String, Object> expected = Map.of("window_days", 7, "success", true);
        when(telemetrySummaryAssemblerService.loadSummary(7, "exp-a")).thenReturn(expected);

        Map<String, Object> actual = dialogService.loadWorkspaceTelemetrySummary(7, "exp-a");

        assertThat(actual).isEqualTo(expected);
        verify(telemetrySummaryAssemblerService).loadSummary(7, "exp-a");
    }
}
