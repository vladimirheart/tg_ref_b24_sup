package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
@RuntimeWorkload(
    id = "locations-shared-config-repair",
    roles = {RuntimeRole.MIGRATOR},
    replicaPolicy = RuntimeReplicaPolicy.SINGLETON
)public class LocationsSharedConfigRepairService implements ApplicationRunner {

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
