package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SettingsLocationsUpdateService {

    private final SharedConfigService sharedConfigService;
    private final SettingsParameterService settingsParameterService;

    public SettingsLocationsUpdateService(SharedConfigService sharedConfigService,
                                          SettingsParameterService settingsParameterService) {
        this.sharedConfigService = sharedConfigService;
        this.settingsParameterService = settingsParameterService;
    }

    public boolean applyLocationsUpdate(Map<String, Object> payload) {
        if (!payload.containsKey("locations")) {
            return false;
        }
        Object locationsPayload = payload.get("locations");
        sharedConfigService.saveLocations(locationsPayload);
        settingsParameterService.syncParametersFromLocationsPayload(locationsPayload);
        return true;
    }
}
