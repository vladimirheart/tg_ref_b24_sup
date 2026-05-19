package com.example.panel.controller;

import com.example.panel.service.NotificationRoutingService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/notification-routing")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class NotificationRoutingSettingsController {

    private final NotificationRoutingService notificationRoutingService;

    public NotificationRoutingSettingsController(NotificationRoutingService notificationRoutingService) {
        this.notificationRoutingService = notificationRoutingService;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        return Map.of(
                "success", true,
                NotificationRoutingService.SETTINGS_KEY, notificationRoutingService.loadSettingsPayload()
        );
    }

    @PostMapping
    public Map<String, Object> saveSettings(@RequestBody(required = false) Map<String, Object> payload) {
        return Map.of(
                "success", true,
                NotificationRoutingService.SETTINGS_KEY, notificationRoutingService.saveSettingsPayload(payload)
        );
    }
}
