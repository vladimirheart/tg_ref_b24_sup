package com.example.panel.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IikoDepartmentLocationsSyncSchedulerTest {

    @Test
    void syncNowPersistsLiveCatalogIntoSharedLocations() {
        IikoDepartmentLocationCatalogService locationCatalogService = mock(IikoDepartmentLocationCatalogService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        SettingsParameterService settingsParameterService = mock(SettingsParameterService.class);
        IikoDepartmentLocationsSyncScheduler scheduler = new IikoDepartmentLocationsSyncScheduler(
                locationCatalogService,
                sharedConfigService,
                settingsParameterService
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
                "tree", Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                "statuses", Map.of("open", "Открыта"),
                "city_meta", Map.of("БлинБери::Корпоративная сеть::Смоленск", Map.of("country", "Россия", "partner_type", "Корпоративная сеть")),
                "location_meta", Map.of("БлинБери::Корпоративная сеть::Смоленск::Ленина 1", Map.of("country", "Россия", "partner_type", "Корпоративная сеть"))
        );

        when(locationCatalogService.loadCatalog()).thenReturn(snapshot);
        when(locationCatalogService.buildEffectiveLocationsPayload(null)).thenReturn(currentPayload);
        when(locationCatalogService.buildEffectiveLocationsPayload(snapshot)).thenReturn(effectivePayload);

        scheduler.syncNow();

        verify(sharedConfigService).saveLocations(effectivePayload);
        verify(settingsParameterService).syncParametersFromLocationsPayload(effectivePayload);
    }

    @Test
    void syncNowSkipsWriteWhenCatalogFallsBackToSharedConfig() {
        IikoDepartmentLocationCatalogService locationCatalogService = mock(IikoDepartmentLocationCatalogService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        SettingsParameterService settingsParameterService = mock(SettingsParameterService.class);
        IikoDepartmentLocationsSyncScheduler scheduler = new IikoDepartmentLocationsSyncScheduler(
                locationCatalogService,
                sharedConfigService,
                settingsParameterService
        );

        when(locationCatalogService.loadCatalog()).thenReturn(
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of(),
                        Map.of(),
                        "shared_config",
                        true,
                        List.of("fallback")
                )
        );

        scheduler.syncNow();

        verify(sharedConfigService, never()).saveLocations(org.mockito.ArgumentMatchers.any());
        verify(settingsParameterService, never()).syncParametersFromLocationsPayload(org.mockito.ArgumentMatchers.any());
    }
}
