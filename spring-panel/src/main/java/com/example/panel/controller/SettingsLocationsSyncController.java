package com.example.panel.controller;

import com.example.panel.service.IikoDepartmentLocationsSyncService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/locations-sync")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class SettingsLocationsSyncController {

    private final IikoDepartmentLocationsSyncService syncService;

    public SettingsLocationsSyncController(IikoDepartmentLocationsSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/status")
    public IikoDepartmentLocationsSyncService.SyncStatusSnapshot status() {
        return syncService.getStatus();
    }

    @PostMapping("/run")
    public Map<String, Object> run() {
        IikoDepartmentLocationsSyncService.SyncTriggerResponse result = syncService.triggerManualSync();
        return Map.of(
                "success", true,
                "started", result.started(),
                "status", result.status()
        );
    }
}
