package com.example.panel.controller;

import com.example.panel.service.BackupSettingsService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/backup")
public class BackupSettingsController {

    private final BackupSettingsService backupSettingsService;

    public BackupSettingsController(BackupSettingsService backupSettingsService) {
        this.backupSettingsService = backupSettingsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> getSettings() {
        return Map.of(
                "success", true,
                "settings", backupSettingsService.load()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> payload) {
        try {
            return Map.of(
                    "success", true,
                    "settings", backupSettingsService.save(payload)
            );
        } catch (IllegalArgumentException ex) {
            return Map.of(
                    "success", false,
                    "error", ex.getMessage()
            );
        }
    }
}
