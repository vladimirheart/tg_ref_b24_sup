package com.example.panel.controller;

import com.example.panel.service.SettingsUpdateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SettingsBridgeController {

    private final SettingsUpdateService settingsUpdateService;

    public SettingsBridgeController(SettingsUpdateService settingsUpdateService) {
        this.settingsUpdateService = settingsUpdateService;
    }

    @RequestMapping(value = {"/settings", "/settings/"}, method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> payload,
                                              Authentication authentication) {
        return settingsUpdateService.updateSettings(payload, authentication);
    }
}
