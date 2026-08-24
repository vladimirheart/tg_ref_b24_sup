package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmployeeDiscountAutomationSettingsServiceTest {

    @Test
    void explicitEmptyTitleMarkersClearPreviouslySavedFilter() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        Map<String, Object> current = Map.of(
            EmployeeDiscountAutomationSettingsService.SETTINGS_KEY,
            Map.of(
                "bitrix_group_id", 77,
                "task_title_markers", List.of("old marker"),
                "checklist_labels", List.of("отключение корпоративной скидки"),
                "phone_regex", "phone-regex",
                "dry_run_by_default", true
            )
        );
        when(sharedConfigService.loadSettings()).thenReturn(current);
        EmployeeDiscountAutomationSettingsService service =
            new EmployeeDiscountAutomationSettingsService(sharedConfigService);

        EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings saved =
            service.save(Map.of("task_title_markers", List.of()));

        assertThat(saved.taskTitleMarkers()).isEmpty();
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(sharedConfigService).saveSettings(captor.capture());
        assertThat(captor.getValue().toString()).contains("task_title_markers=[]");
    }
@Test
    void ignorePhoneListIsNormalizedDeduplicatedAndCanBeCleared() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        Map<String, Object> current = Map.of(
            EmployeeDiscountAutomationSettingsService.SETTINGS_KEY,
            Map.of(
                "bitrix_group_id", 77,
                "ignored_phone_numbers", List.of("+79990000000"),
                "dry_run_by_default", true
            )
        );
        when(sharedConfigService.loadSettings()).thenReturn(current);
        EmployeeDiscountAutomationSettingsService service =
            new EmployeeDiscountAutomationSettingsService(sharedConfigService);

        EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings saved =
            service.save(Map.of("ignored_phone_numbers", List.of(
                "89999999998",
                "8 (999) 999-99-98",
                "+7 999 111 22 33"
            )));

        assertThat(saved.ignoredPhoneNumbers()).containsExactly("+79999999998", "+79991112233");

        EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings cleared =
            service.save(Map.of("ignored_phone_numbers", List.of()));
        assertThat(cleared.ignoredPhoneNumbers()).isEmpty();
    }
}