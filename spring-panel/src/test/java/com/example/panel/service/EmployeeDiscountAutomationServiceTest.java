package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.AutomationRun;
import com.example.panel.repository.AutomationRunItemRepository;
import com.example.panel.repository.AutomationRunRepository;
import com.example.panel.service.EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;

class EmployeeDiscountAutomationServiceTest {

    @Test
    void executeCompletesChecklistOnlyAfterSuccessfulIikoMutation() {
        Fixture fixture = fixture();
        stubSelectedCandidate(fixture);
        when(fixture.iikoDirectoryService.disableCorporateDiscount("alice", "+79991234567"))
            .thenReturn(new IikoDirectoryService.MutationResult(true, "iiko updated"));

        Map<String, Object> result = fixture.service.run(false, "alice");

        InOrder order = inOrder(fixture.iikoDirectoryService, fixture.bitrix24RestService);
        order.verify(fixture.iikoDirectoryService).disableCorporateDiscount("alice", "+79991234567");
        order.verify(fixture.bitrix24RestService).completeChecklistItem("alice", "101", "501");
        assertEquals("success", runMap(result).get("status"));
        assertEquals("success", firstItem(result).get("status"));
    }

    @Test
    void executeDoesNotCompleteChecklistWhenIikoMutationFails() {
        Fixture fixture = fixture();
        stubSelectedCandidate(fixture);
        when(fixture.iikoDirectoryService.disableCorporateDiscount("alice", "+79991234567"))
            .thenReturn(new IikoDirectoryService.MutationResult(false, "guest not found"));

        Map<String, Object> result = fixture.service.run(false, "alice");

        verify(fixture.bitrix24RestService, never()).completeChecklistItem(any(), any(), any());
        assertEquals("error", runMap(result).get("status"));
        assertEquals("success=0, error=1, skipped=0", runMap(result).get("summary"));
        assertEquals("error", firstItem(result).get("status"));
    }

    @Test
    void extractionFailureRemainsErrorInsteadOfBeingDowngradedToSkipped() {
        Fixture fixture = fixture();
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L)).thenReturn(List.of(Map.of(
            "id", "101",
            "title", "Увольнение сотрудника",
            "description", "Телефон в задаче не указан",
            "status", "2",
            "closed_date", ""
        )));
        when(fixture.bitrix24RestService.listChecklistItems("alice", "101")).thenReturn(List.of(Map.of(
            "id", "501",
            "title", "отключение корпоративной скидки",
            "is_complete", false
        )));

        Map<String, Object> result = fixture.service.run(false, "alice");

        verify(fixture.iikoDirectoryService, never()).disableCorporateDiscount(any(), any());
        verify(fixture.bitrix24RestService, never()).completeChecklistItem(any(), any(), any());
        assertEquals("error", runMap(result).get("status"));
        assertEquals("success=0, error=1, skipped=0", runMap(result).get("summary"));
        assertEquals("error", firstItem(result).get("status"));
    }

    @Test
    void runHistoryIsScopedToCurrentActor() {
        Fixture fixture = fixture();
        AutomationRun aliceRun = new AutomationRun();
        aliceRun.setId(9L);
        aliceRun.setAutomationKey(EmployeeDiscountAutomationService.AUTOMATION_KEY);
        aliceRun.setMode("dry_run");
        aliceRun.setStatus("success");
        aliceRun.setActor("alice");
        aliceRun.setSummary("success=1, error=0, skipped=0");
        aliceRun.setStartedAt(OffsetDateTime.parse("2026-08-24T08:00:00Z"));
        aliceRun.setCreatedAt(OffsetDateTime.parse("2026-08-24T08:00:00Z"));

        when(fixture.automationRunRepository
            .findTop20ByAutomationKeyAndActorIgnoreCaseOrderByStartedAtDesc(
                EmployeeDiscountAutomationService.AUTOMATION_KEY,
                "alice"
            )).thenReturn(List.of(aliceRun));
        when(fixture.automationRunRepository
            .findByIdAndAutomationKeyAndActorIgnoreCase(
                9L,
                EmployeeDiscountAutomationService.AUTOMATION_KEY,
                "alice"
            )).thenReturn(Optional.of(aliceRun));
        when(fixture.automationRunRepository
            .findByIdAndAutomationKeyAndActorIgnoreCase(
                9L,
                EmployeeDiscountAutomationService.AUTOMATION_KEY,
                "bob"
            )).thenReturn(Optional.empty());
        when(fixture.automationRunItemRepository.findByRunIdOrderByCreatedAtAsc(9L)).thenReturn(List.of());

        List<Map<String, Object>> runs = fixture.service.listRuns(" alice ");
        Map<String, Object> details = fixture.service.getRun(9L, "alice");

        assertEquals(1, runs.size());
        assertEquals(9L, runs.get(0).get("id"));
        assertEquals(9L, runMap(details).get("id"));
        assertThrows(ResponseStatusException.class, () -> fixture.service.getRun(9L, "bob"));
    }

    private void stubSelectedCandidate(Fixture fixture) {
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L)).thenReturn(List.of(Map.of(
            "id", "101",
            "title", "Увольнение сотрудника",
            "description", "Тел. сотрудника: +7 (999) 123-45-67",
            "status", "2",
            "closed_date", ""
        )));
        when(fixture.bitrix24RestService.listChecklistItems("alice", "101")).thenReturn(List.of(Map.of(
            "id", "501",
            "title", "отключение корпоративной скидки",
            "is_complete", false
        )));
    }

    private Fixture fixture() {
        EmployeeDiscountAutomationSettingsService settingsService = mock(EmployeeDiscountAutomationSettingsService.class);
        EmployeeDiscountAutomationCredentialService credentialService = mock(EmployeeDiscountAutomationCredentialService.class);
        Bitrix24RestService bitrix24RestService = mock(Bitrix24RestService.class);
        IikoDirectoryService iikoDirectoryService = mock(IikoDirectoryService.class);
        AutomationRunRepository automationRunRepository = mock(AutomationRunRepository.class);
        AutomationRunItemRepository automationRunItemRepository = mock(AutomationRunItemRepository.class);

        when(settingsService.load()).thenReturn(new EmployeeDiscountAutomationSettings(
            77L,
            List.of(),
            List.of("отключение корпоративной скидки", "Отключение корпоративных скидок"),
            "(?iu)тел\\.?\\s*сотрудника\\s*[:\\-]?\\s*([+\\d\\s()\\-]{10,})",
            List.of(),
            List.of(),
            true
        ));
        when(automationRunRepository.save(any(AutomationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(automationRunItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeDiscountAutomationService service = new EmployeeDiscountAutomationService(
            settingsService,
            credentialService,
            bitrix24RestService,
            iikoDirectoryService,
            automationRunRepository,
            automationRunItemRepository
        );
        return new Fixture(
            service,
            bitrix24RestService,
            iikoDirectoryService,
            automationRunRepository,
            automationRunItemRepository
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runMap(Map<String, Object> result) {
        return (Map<String, Object>) result.get("run");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstItem(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("items")).get(0);
    }

    private record Fixture(EmployeeDiscountAutomationService service,
                           Bitrix24RestService bitrix24RestService,
                           IikoDirectoryService iikoDirectoryService,
                           AutomationRunRepository automationRunRepository,
                           AutomationRunItemRepository automationRunItemRepository) {
    }
}
