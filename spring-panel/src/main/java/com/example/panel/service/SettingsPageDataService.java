package com.example.panel.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsPageDataService {

    private final SharedConfigService sharedConfigService;
    private final LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;
    private final LocationsIikoSyncSettingsService locationsIikoSyncSettingsService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;

    public SettingsPageDataService(SharedConfigService sharedConfigService,
                                   LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                   LocationsIikoSyncSettingsService locationsIikoSyncSettingsService,
                                   IikoDepartmentLocationCatalogService locationCatalogService) {
        this.sharedConfigService = sharedConfigService;
        this.locationsIikoServerSourceSettingsService = locationsIikoServerSourceSettingsService;
        this.locationsIikoSyncSettingsService = locationsIikoSyncSettingsService;
        this.locationCatalogService = locationCatalogService;
    }

    public Map<String, Object> loadSection(String sectionName) {
        String normalizedSection = normalizeSectionName(sectionName);
        Map<String, Object> settings = sharedConfigService.loadSettings();
        return switch (normalizedSection) {
            case "admin" -> Map.of(
                    "reportingConfig", settings.getOrDefault("reporting_config", Map.of()),
                    "managerLocationBindings", settings.getOrDefault("manager_location_bindings", List.of())
            );
            case "channels" -> Map.of(
                    "integrationNetwork", settings.getOrDefault("integration_network", Map.of()),
                    "integrationNetworkProfiles", settings.getOrDefault("integration_network_profiles", List.of())
            );
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
}
