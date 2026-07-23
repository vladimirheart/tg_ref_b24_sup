package com.example.panel.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsPageDataService {

    private final SharedConfigService sharedConfigService;
    private final BotSettingsPayloadNormalizer botSettingsPayloadNormalizer;
    private final LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;
    private final LocationsIikoSyncSettingsService locationsIikoSyncSettingsService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final SettingsCatalogService settingsCatalogService;

    public SettingsPageDataService(SharedConfigService sharedConfigService,
                                   BotSettingsPayloadNormalizer botSettingsPayloadNormalizer,
                                   LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                   LocationsIikoSyncSettingsService locationsIikoSyncSettingsService,
                                   IikoDepartmentLocationCatalogService locationCatalogService,
                                   SettingsCatalogService settingsCatalogService) {
        this.sharedConfigService = sharedConfigService;
        this.botSettingsPayloadNormalizer = botSettingsPayloadNormalizer;
        this.locationsIikoServerSourceSettingsService = locationsIikoServerSourceSettingsService;
        this.locationsIikoSyncSettingsService = locationsIikoSyncSettingsService;
        this.locationCatalogService = locationCatalogService;
        this.settingsCatalogService = settingsCatalogService;
    }

    public Map<String, Object> loadSection(String sectionName) {
        String normalizedSection = normalizeSectionName(sectionName);
        Map<String, Object> settings = sharedConfigService.loadSettings();
        return switch (normalizedSection) {
            case "admin" -> Map.of(
                    "reportingConfig", settings.getOrDefault("reporting_config", Map.of()),
                    "managerLocationBindings", settings.getOrDefault("manager_location_bindings", List.of())
            );
            case "channels" -> {
                IikoDepartmentLocationCatalogService.LocationCatalogSnapshot catalog = locationCatalogService.loadCatalog();
                Map<String, Object> effectiveLocationsPayload = locationCatalogService.buildEffectiveLocationsPayload(catalog);
                Map<String, Object> effectiveLocationTree = toObjectMap(effectiveLocationsPayload.get("tree"));
                Map<String, Object> effectiveLocationStatuses = toObjectMap(effectiveLocationsPayload.get("statuses"));
                yield Map.of(
                        "botSettings", botSettingsPayloadNormalizer.normalize(settings.get("bot_settings")),
                        "integrationNetwork", settings.getOrDefault("integration_network", Map.of()),
                        "integrationNetworkProfiles", settings.getOrDefault("integration_network_profiles", List.of()),
                        "botPresetDefinitions",
                        settingsCatalogService.buildLocationPresets(effectiveLocationTree, effectiveLocationStatuses)
                );
            }
            case "locations" -> Map.of(
                    "tree", locationCatalogService.buildEffectiveLocationsPayload(locationCatalogService.loadCatalog()),
                    "iikoServerSources", locationsIikoServerSourceSettingsService.loadForClient(settings),
                    "iikoSyncSettings", locationsIikoSyncSettingsService.loadForClient(settings)
            );
            case "parameters" -> Map.of(
                    "networkProfiles", settings.getOrDefault("network_profiles", List.of())
            );
            default -> throw new IllegalArgumentException("Unsupported settings page data section: " + sectionName);
        };
    }

    private String normalizeSectionName(String sectionName) {
        return sectionName == null ? "" : sectionName.trim().toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
