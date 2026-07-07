package com.example.panel.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class LocationsSharedConfigRepairService implements ApplicationRunner {

    private final SettingsParameterService settingsParameterService;

    public LocationsSharedConfigRepairService(SettingsParameterService settingsParameterService) {
        this.settingsParameterService = settingsParameterService;
    }

    @Override
    public void run(ApplicationArguments args) {
        settingsParameterService.normalizeLocationBusinessAliasesIfNeeded();
        settingsParameterService.repairLocationsSharedConfigIfNeeded();
    }
}
