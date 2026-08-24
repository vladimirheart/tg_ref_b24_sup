package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.AutomationRun;
import com.example.panel.entity.AutomationRunItem;
import com.example.panel.repository.AutomationRunItemRepository;
import com.example.panel.repository.AutomationRunRepository;
import com.example.panel.service.EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EmployeeDiscountAutomationRuntimeAuditTest {

    @Test
    void runIsPersistedBeforeBitrixDiscoveryAndDiscoveryFailureBecomesDurableError() {
        Fixture fixture = fixture(settings(77L));
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Bitrix24 unavailable"));

        Map<String, Object> result = fixture.service.run(false, "alice");

        InOrder order = inOrder(fixture.automationRunRepository, fixture.bitrix24RestService);
        order.verify(fixture.automationRunRepository).save(any(AutomationRun.class));
        order.verify(fixture.bitrix24RestService).listTasksForGroup("alice", 77L);
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(runMap(result)).containsEntry("status", "error");
        assertThat(runMap(result)).containsEntry("summary", "success=0, error=1, skipped=0");
        verify(fixture.automationRunItemRepository).save(any(AutomationRunItem.class));
    }

    @Test
    void executePersistsInFlightItemBeforeCallingExternalMutations() {
        Fixture fixture = fixture(settings(77L));
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
        when(fixture.iikoDirectoryService.disableCorporateDiscount("alice", "+79991234567"))
            .thenReturn(new IikoDirectoryService.MutationResult(true, "done"));

        Map<String, Object> result = fixture.service.run(false, "alice");

        InOrder order = inOrder(
            fixture.automationRunItemRepository,
            fixture.iikoDirectoryService,
            fixture.bitrix24RestService
        );
        order.verify(fixture.automationRunItemRepository).save(any(AutomationRunItem.class));
        order.verify(fixture.iikoDirectoryService).disableCorporateDiscount("alice", "+79991234567");
        order.verify(fixture.bitrix24RestService).completeChecklistItem("alice", "101", "501");
        assertThat(runMap(result)).containsEntry("status", "success");
    }

    @Test
    void deferredBitrixTaskRemainsEligibleBecauseStatusSixIsNotClosed() {
        Fixture fixture = fixture(settings(77L));
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L)).thenReturn(List.of(Map.of(
            "id", "606",
            "title", "Увольнение сотрудника",
            "description", "Тел. сотрудника: +7 (999) 123-45-67",
            "status", "6",
            "closed_date", ""
        )));
        when(fixture.bitrix24RestService.listChecklistItems("alice", "606")).thenReturn(List.of(Map.of(
            "id", "506",
            "title", "отключение корпоративной скидки",
            "is_complete", false
        )));

        Map<String, Object> result = fixture.service.run(true, "alice");

        assertThat(runMap(result)).containsEntry("status", "success");
        assertThat(result.toString()).contains("dry_run");
        verify(fixture.iikoDirectoryService, never()).disableCorporateDiscount(any(), any());
    }

    @Test
    void missingGroupConfigurationCannotProduceFalseSuccessfulExecute() {
        Fixture fixture = fixture(settings(null));

        Map<String, Object> result = fixture.service.run(false, "alice");

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(runMap(result)).containsEntry("status", "error");
        assertThat(result.get("error").toString()).contains("bitrix_group_id");
        verify(fixture.bitrix24RestService, never()).listTasksForGroup(any(), any());
    }

    private Fixture fixture(EmployeeDiscountAutomationSettings settings) {
        EmployeeDiscountAutomationSettingsService settingsService = mock(EmployeeDiscountAutomationSettingsService.class);
        EmployeeDiscountAutomationCredentialService credentialService = mock(EmployeeDiscountAutomationCredentialService.class);
        Bitrix24RestService bitrix24RestService = mock(Bitrix24RestService.class);
        IikoDirectoryService iikoDirectoryService = mock(IikoDirectoryService.class);
        AutomationRunRepository automationRunRepository = mock(AutomationRunRepository.class);
        AutomationRunItemRepository automationRunItemRepository = mock(AutomationRunItemRepository.class);
        when(settingsService.load()).thenReturn(settings);
        when(automationRunRepository.save(any(AutomationRun.class))).thenAnswer(invocation -> {
            AutomationRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(1L);
            }
            return run;
        });
        when(automationRunItemRepository.save(any(AutomationRunItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EmployeeDiscountAutomationService service = new EmployeeDiscountAutomationService(
            settingsService,
            credentialService,
            bitrix24RestService,
            iikoDirectoryService,
            automationRunRepository,
            automationRunItemRepository
        );
        return new Fixture(service, bitrix24RestService, iikoDirectoryService, automationRunRepository, automationRunItemRepository);
    }

    private EmployeeDiscountAutomationSettings settings(Long groupId) {
        return new EmployeeDiscountAutomationSettings(
            groupId,
            List.of(),
            List.of("отключение корпоративной скидки", "Отключение корпоративных скидок"),
            "(?iu)тел\\.?\\s*сотрудника\\s*[:\\-]?\\s*([+\\d\\s()\\-]{10,})",
            List.of(),
            List.of(),
            true
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runMap(Map<String, Object> result) {
        return (Map<String, Object>) result.get("run");
    }

    private record Fixture(EmployeeDiscountAutomationService service,
                           Bitrix24RestService bitrix24RestService,
                           IikoDirectoryService iikoDirectoryService,
                           AutomationRunRepository automationRunRepository,
                           AutomationRunItemRepository automationRunItemRepository) {
    }
}