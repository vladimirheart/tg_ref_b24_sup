package com.example.panel.controller;

import com.example.panel.service.SettingsItEquipmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
public class SettingsItEquipmentController {

    private final SettingsItEquipmentService settingsItEquipmentService;
    private final ObjectMapper objectMapper;

    public SettingsItEquipmentController(SettingsItEquipmentService settingsItEquipmentService,
                                         ObjectMapper objectMapper) {
        this.settingsItEquipmentService = settingsItEquipmentService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/settings/it-equipment")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listItEquipment() {
        return settingsItEquipmentService.listItEquipment();
    }

    @PostMapping({"/api/settings/it-equipment", "/api/settings/it-equipment/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> createItEquipment(HttpServletRequest request,
                                                 Authentication authentication) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        String actor = authentication != null ? authentication.getName() : null;
        return settingsItEquipmentService.createItEquipment(payload, actor);
    }

    @RequestMapping(value = "/api/settings/it-equipment/{itemId}",
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateItEquipment(@PathVariable long itemId,
                                                 @RequestBody Map<String, Object> payload,
                                                 Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        return settingsItEquipmentService.updateItEquipment(itemId, payload, actor);
    }

    @DeleteMapping("/api/settings/it-equipment/{itemId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deleteItEquipment(@PathVariable long itemId,
                                                 Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        return settingsItEquipmentService.deleteItEquipment(itemId, actor);
    }
}
