package com.example.panel.controller;

import com.example.panel.service.BackupManualOperationService;
import com.example.panel.service.BackupSettingsService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/backup")
public class BackupSettingsController {

    private final BackupSettingsService backupSettingsService;
    private final BackupManualOperationService backupManualOperationService;

    public BackupSettingsController(BackupSettingsService backupSettingsService,
                                    BackupManualOperationService backupManualOperationService) {
        this.backupSettingsService = backupSettingsService;
        this.backupManualOperationService = backupManualOperationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> getSettings() {
        return Map.of("success", true, "settings", backupSettingsService.load());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> payload) {
        try {
            return Map.of("success", true, "settings", backupSettingsService.save(payload));
        } catch (IllegalArgumentException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }
    }

    @GetMapping("/manual")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> getManualStatus() {
        return Map.of("success", true, "manual", backupManualOperationService.status());
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> queueManualBackup(@RequestBody Map<String, Object> payload,
                                                 Authentication authentication) {
        try {
            String requestedBy = authentication != null ? authentication.getName() : "unknown";
            return Map.of(
                    "success", true,
                    "manual", backupManualOperationService.enqueue(payload, requestedBy)
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "manual", backupManualOperationService.status()
            );
        }
    }
}
