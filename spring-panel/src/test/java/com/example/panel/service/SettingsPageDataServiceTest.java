package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsPageDataServiceTest {

    @Mock
    private SharedConfigService sharedConfigService;

    @Mock
    private LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;

    @Mock
    private LocationsIikoSyncSettingsService locationsIikoSyncSettingsService;

    @Mock
    private IikoDepartmentLocationCatalogService locationCatalogService;

    @Mock
    private SettingsCatalogService settingsCatalogService;

    @InjectMocks
    private SettingsPageDataService settingsPageDataService;

    @Test
    void loadChannelsSectionIncludesDynamicBotPresetDefinitions() {
        Map<String, Object> settings = Map.of(
                "integration_network", Map.of("enabled", true),
                "integration_network_profiles", List.of(Map.of("id", "corp"))
        );
        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot catalog =
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                        Map.of("location::БлинБери::Корпоративная сеть::Смоленск::Ленина 1", "Активен"),
                        "iiko_api",
                        false,
                        List.of()
                );
        Map<String, Object> effectiveLocationsPayload = Map.of(
                "tree", catalog.tree(),
                "statuses", catalog.statuses(),
                "city_meta", Map.of(),
                "location_meta", Map.of()
        );
        Map<String, Object> presetDefinitions = Map.of(
                "locations", Map.of(
                        "label", "Структура локаций",
                        "fields", Map.of("business", Map.of("label", "Бизнес", "options", List.of("БлинБери")))
                )
        );

        when(sharedConfigService.loadSettings()).thenReturn(settings);
        when(locationCatalogService.loadCatalog()).thenReturn(catalog);
        when(locationCatalogService.buildEffectiveLocationsPayload(catalog)).thenReturn(effectiveLocationsPayload);
        when(settingsCatalogService.buildLocationPresets(catalog.tree(), catalog.statuses())).thenReturn(presetDefinitions);

        Map<String, Object> section = settingsPageDataService.loadSection("channels");

        assertThat(section)
                .containsEntry("integrationNetwork", settings.get("integration_network"))
                .containsEntry("integrationNetworkProfiles", settings.get("integration_network_profiles"))
                .containsEntry("botPresetDefinitions", presetDefinitions);
        verify(settingsCatalogService).buildLocationPresets(catalog.tree(), catalog.statuses());
    }
}
