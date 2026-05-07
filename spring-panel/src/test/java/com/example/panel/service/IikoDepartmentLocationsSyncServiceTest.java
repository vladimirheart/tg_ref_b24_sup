package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IikoDepartmentLocationsSyncServiceTest {

    @Test
    void syncNowPersistsLiveCatalogIntoSharedLocations() {
        IikoDepartmentLocationCatalogService locationCatalogService = mock(IikoDepartmentLocationCatalogService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        SettingsParameterService settingsParameterService = mock(SettingsParameterService.class);
        LocationsIikoSyncSettingsService syncSettingsService = new LocationsIikoSyncSettingsService();
        IikoDepartmentLocationsSyncService service = new IikoDepartmentLocationsSyncService(
                locationCatalogService,
                sharedConfigService,
                settingsParameterService,
                syncSettingsService
        );

        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot snapshot =
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                        Map.of(),
                        "iiko_api",
                        false,
                        List.of()
                );
        Map<String, Object> currentPayload = Map.of(
                "tree", Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Москва", List.of("Тверская")))),
                "statuses", Map.of("open", "Открыта")
        );
        Map<String, Object> effectivePayload = Map.of(
                "tree", Map.of("БлинБери", Map.of(
                        "Корпоративная сеть", Map.of(
                                "Москва", List.of("Тверская"),
                                "Смоленск", List.of("Ленина 1")
                        ))),
                "statuses", Map.of(
                        "open", "Открыта",
                        "location::БлинБери::Корпоративная сеть::Москва::Тверская", "Закрыт",
                        "location::БлинБери::Корпоративная сеть::Смоленск::Ленина 1", "Активен"
                ),
                "city_meta", Map.of("БлинБери::Корпоративная сеть::Смоленск", Map.of("country", "Россия", "partner_type", "Корпоративная сеть")),
                "location_meta", Map.of("БлинБери::Корпоративная сеть::Смоленск::Ленина 1", Map.of("country", "Россия", "partner_type", "Корпоративная сеть"))
        );

        when(locationCatalogService.loadCatalog(true)).thenReturn(snapshot);
        when(locationCatalogService.buildEffectiveLocationsPayload(null)).thenReturn(currentPayload);
        when(locationCatalogService.buildEffectiveLocationsPayload(snapshot)).thenReturn(effectivePayload);

        IikoDepartmentLocationsSyncService.SyncStatusSnapshot result = service.syncNow("manual", true);

        verify(sharedConfigService).saveLocations(effectivePayload);
        verify(settingsParameterService).syncParametersFromLocationsPayload(effectivePayload);
        assertThat(result.state()).isEqualTo("success");
        assertThat(result.changed()).isTrue();
        assertThat(result.result()).isNotNull();
        assertThat(result.result().totalLocations()).isEqualTo(2);
        assertThat(result.result().activeLocations()).isEqualTo(1);
        assertThat(result.result().closedLocations()).isEqualTo(1);
        assertThat(result.result().addedLocations()).isEqualTo(1);
        assertThat(result.result().closedBySync()).isEqualTo(1);
        assertThat(result.result().reopenedLocations()).isEqualTo(0);
        assertThat(result.result().addedExamples()).anyMatch(item -> item.contains("Ленина 1"));
        assertThat(result.result().closedExamples()).anyMatch(item -> item.contains("Тверская"));
    }

    @Test
    void syncNowSkipsWriteWhenCatalogFallsBackToSharedConfig() {
        IikoDepartmentLocationCatalogService locationCatalogService = mock(IikoDepartmentLocationCatalogService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        SettingsParameterService settingsParameterService = mock(SettingsParameterService.class);
        LocationsIikoSyncSettingsService syncSettingsService = new LocationsIikoSyncSettingsService();
        IikoDepartmentLocationsSyncService service = new IikoDepartmentLocationsSyncService(
                locationCatalogService,
                sharedConfigService,
                settingsParameterService,
                syncSettingsService
        );

        when(locationCatalogService.loadCatalog(true)).thenReturn(
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of(),
                        Map.of(),
                        "shared_config",
                        true,
                        List.of("fallback")
                )
        );

        IikoDepartmentLocationsSyncService.SyncStatusSnapshot result = service.syncNow("manual", true);

        verify(sharedConfigService, never()).saveLocations(org.mockito.ArgumentMatchers.any());
        verify(settingsParameterService, never()).syncParametersFromLocationsPayload(org.mockito.ArgumentMatchers.any());
        assertThat(result.state()).isEqualTo("skipped");
    }
}
