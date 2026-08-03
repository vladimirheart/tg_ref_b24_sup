package com.example.panel.controller;

import com.example.panel.service.NetBoxObjectPassportSyncService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/netbox-sync")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class SettingsNetBoxSyncController {

    private final NetBoxObjectPassportSyncService syncService;

    public SettingsNetBoxSyncController(NetBoxObjectPassportSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/status")
    public NetBoxObjectPassportSyncService.SyncStatusSnapshot status() {
        return syncService.getStatus();
    }

    @PostMapping("/run")
    public Map<String, Object> run() {
        NetBoxObjectPassportSyncService.SyncTriggerResponse result = syncService.triggerManualSync();
        return Map.of(
                "success", true,
                "started", result.started(),
                "status", result.status()
        );
    }
}
