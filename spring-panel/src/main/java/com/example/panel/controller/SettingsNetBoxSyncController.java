package com.example.panel.controller;

import com.example.panel.service.NetBoxObjectPassportSyncService;
import com.example.panel.service.SettingsTopLevelUpdateService;
import com.example.panel.service.SharedConfigService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/netbox-sync")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class SettingsNetBoxSyncController {

    private final NetBoxObjectPassportSyncService syncService;
    private final SharedConfigService sharedConfigService;
    private final SettingsTopLevelUpdateService settingsTopLevelUpdateService;

    public SettingsNetBoxSyncController(NetBoxObjectPassportSyncService syncService,
                                        SharedConfigService sharedConfigService,
                                        SettingsTopLevelUpdateService settingsTopLevelUpdateService) {
        this.syncService = syncService;
        this.sharedConfigService = sharedConfigService;
        this.settingsTopLevelUpdateService = settingsTopLevelUpdateService;
    }

    @GetMapping("/status")
    public NetBoxObjectPassportSyncService.SyncStatusSnapshot status() {
        return syncService.getStatus();
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody(required = false) Map<String, Object> payload) {
        persistNetBoxSyncSettings(payload);
        return Map.of("success", true);
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody(required = false) Map<String, Object> payload) {
        persistNetBoxSyncSettings(payload);
        NetBoxObjectPassportSyncService.SyncTriggerResponse result = syncService.triggerManualSync();
        return Map.of(
                "success", true,
                "started", result.started(),
                "status", result.status()
        );
    }

    private void persistNetBoxSyncSettings(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("netbox_sync")) {
            return;
        }
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        boolean modified = settingsTopLevelUpdateService.applyTopLevelUpdates(payload, settings);
        if (modified) {
            sharedConfigService.saveSettings(settings);
        }
    }
}
