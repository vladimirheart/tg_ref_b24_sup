package com.example.panel.service;

import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IikoDepartmentLocationsSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IikoDepartmentLocationsSyncScheduler.class);

    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final SharedConfigService sharedConfigService;
    private final SettingsParameterService settingsParameterService;

    public IikoDepartmentLocationsSyncScheduler(IikoDepartmentLocationCatalogService locationCatalogService,
                                                SharedConfigService sharedConfigService,
                                                SettingsParameterService settingsParameterService) {
        this.locationCatalogService = locationCatalogService;
        this.sharedConfigService = sharedConfigService;
        this.settingsParameterService = settingsParameterService;
    }

    @Scheduled(
        initialDelayString = "${panel.iiko-departments-sync.initial-delay-ms:10000}",
        fixedDelayString = "${panel.iiko-departments-sync.interval-ms:300000}"
    )
    public void refreshSharedLocationsSnapshot() {
        try {
            syncNow();
        } catch (Exception ex) {
            log.warn("iiko departments sync scheduler failed", ex);
        }
    }

    void syncNow() {
        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot snapshot = locationCatalogService.loadCatalog();
        if (snapshot == null || !"iiko_api".equals(snapshot.source()) || snapshot.tree().isEmpty()) {
            return;
        }

        Map<String, Object> existingPayload = locationCatalogService.buildEffectiveLocationsPayload(null);
        Map<String, Object> effectivePayload = locationCatalogService.buildEffectiveLocationsPayload(snapshot);
        if (Objects.equals(existingPayload, effectivePayload)) {
            return;
        }

        sharedConfigService.saveLocations(effectivePayload);
        settingsParameterService.syncParametersFromLocationsPayload(effectivePayload);
        log.info("Updated shared locations snapshot from iiko: businesses={}, warnings={}",
                effectivePayload.get("tree") instanceof Map<?, ?> tree ? tree.size() : 0,
                snapshot.warnings().size());
    }
}
