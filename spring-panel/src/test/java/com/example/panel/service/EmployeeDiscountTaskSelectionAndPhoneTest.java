package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

class EmployeeDiscountTaskSelectionAndPhoneTest {

    @Test
    void previewNormalizesCommonRussianPhoneFormatsAndReportsIgnoredNumber() {
        Fixture fixture = fixture(List.of("+79999999998"));
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L)).thenReturn(List.of(
            task("101", "Тел. сотрудника: 89999999999"),
            task("102", "Телефон сотрудника: 8 999 999 99 99"),
            task("103", "Номер телефона сотрудника: 8 (999) 999 99 99"),
            task("104", "Тел. сотрудника: 8 (999) 999 99 98")
        ));
        for (String taskId : List.of("101", "102", "103", "104")) {
            when(fixture.bitrix24RestService.listChecklistItems("alice", taskId))
                .thenReturn(checklist(taskId));
        }

        Map<String, Object> preview = fixture.service.previewSelection("alice");

        List<Map<String, Object>> items = items(preview);
        assertThat(items).hasSize(4);
        assertThat(items.get(0)).containsEntry("phone", "+79999999999").containsEntry("status", "selected");
        assertThat(items.get(1)).containsEntry("phone", "+79999999999").containsEntry("status", "selected");
        assertThat(items.get(2)).containsEntry("phone", "+79999999999").containsEntry("status", "selected");
        assertThat(items.get(3)).containsEntry("phone", "+79999999998").containsEntry("status", "ignored");
        assertThat(items.get(3).get("message").toString()).contains("ignore-list");
    }

    @Test
    void executeMutatesOnlyExplicitlySelectedTaskAndReportsManualSkip() {
        Fixture fixture = fixture(List.of());
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L)).thenReturn(List.of(
            task("101", "Тел. сотрудника: 8 999 111 11 11"),
            task("102", "Тел. сотрудника: 8 999 222 22 22")
        ));
        when(fixture.bitrix24RestService.listChecklistItems("alice", "101")).thenReturn(checklist("101"));
        when(fixture.bitrix24RestService.listChecklistItems("alice", "102")).thenReturn(checklist("102"));
        when(fixture.iikoDirectoryService.disableCorporateDiscount("alice", "+79992222222"))
            .thenReturn(new IikoDirectoryService.MutationResult(true, "done"));

        Map<String, Object> result = fixture.service.run(false, "alice", List.of("102"));

        verify(fixture.iikoDirectoryService, never()).disableCorporateDiscount("alice", "+79991111111");
        verify(fixture.bitrix24RestService, never()).completeChecklistItem("alice", "101", "501-101");
        verify(fixture.iikoDirectoryService).disableCorporateDiscount("alice", "+79992222222");
        verify(fixture.bitrix24RestService).completeChecklistItem("alice", "102", "501-102");
        assertThat(items(result)).anySatisfy(item -> {
            assertThat(item).containsEntry("task_id", "101").containsEntry("status", "skipped");
            assertThat(item.get("message").toString()).contains("снята оператором");
        });
        assertThat(items(result)).anySatisfy(item -> assertThat(item).containsEntry("task_id", "102").containsEntry("status", "success"));
    }

    @Test
    void ignoredPhoneNeverCallsIikoOrBitrixChecklistAndIsReturnedInRunHistory() {
        Fixture fixture = fixture(List.of("+79993334455"));
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L))
            .thenReturn(List.of(task("105", "Контакт: 8 (999) 333-44-55")));
        when(fixture.bitrix24RestService.listChecklistItems("alice", "105")).thenReturn(checklist("105"));

        Map<String, Object> result = fixture.service.run(false, "alice", List.of("105"));

        verify(fixture.iikoDirectoryService, never()).disableCorporateDiscount(any(), any());
        verify(fixture.bitrix24RestService, never()).completeChecklistItem(any(), any(), any());
        assertThat(run(result).get("summary").toString()).contains("ignored=1");
        assertThat(items(result)).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("status", "ignored").containsEntry("phone", "+79993334455");
            assertThat(item.get("message").toString()).contains("ignore-list");
        });
    }

    @Test
    void ambiguousFallbackPhonesBecomeErrorInsteadOfChoosingRandomNumber() {
        Fixture fixture = fixture(List.of());
        when(fixture.bitrix24RestService.listTasksForGroup("alice", 77L)).thenReturn(List.of(task(
            "106",
            "Контакты для сверки: 8 999 111 11 11 и 8 999 222 22 22"
        )));
        when(fixture.bitrix24RestService.listChecklistItems("alice", "106")).thenReturn(checklist("106"));

        Map<String, Object> preview = fixture.service.previewSelection("alice");

        assertThat(items(preview)).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("status", "error");
            assertThat(item.get("message").toString()).contains("несколько разных телефонных номеров");
        });
    }

    private Fixture fixture(List<String> ignoredPhones) {
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
            ignoredPhones,
            true
        ));
        when(automationRunRepository.save(any(AutomationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(automationRunItemRepository.save(any(AutomationRunItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeDiscountAutomationService service = new EmployeeDiscountAutomationService(
            settingsService,
            credentialService,
            bitrix24RestService,
            iikoDirectoryService,
            automationRunRepository,
            automationRunItemRepository
        );
        return new Fixture(service, bitrix24RestService, iikoDirectoryService);
    }

    private Map<String, Object> task(String id, String description) {
        return Map.of(
            "id", id,
            "title", "Увольнение сотрудника",
            "description", description,
            "status", "2",
            "closed_date", ""
        );
    }

    private List<Map<String, Object>> checklist(String taskId) {
        return List.of(Map.of(
            "id", "501-" + taskId,
            "title", "отключение корпоративной скидки",
            "is_complete", false
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("items");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> run(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("run");
    }

    private record Fixture(EmployeeDiscountAutomationService service,
                           Bitrix24RestService bitrix24RestService,
                           IikoDirectoryService iikoDirectoryService) {
    }
}