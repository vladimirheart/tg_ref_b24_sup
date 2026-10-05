package com.example.panel.controller;

import com.example.panel.service.SettingsItEquipmentConfigurationService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/it-equipment")
public class SettingsItEquipmentConfigurationController {

    private final SettingsItEquipmentConfigurationService configurationService;

    public SettingsItEquipmentConfigurationController(SettingsItEquipmentConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/attributes")
    public Map<String, Object> listAttributes(
            @RequestParam(name = "equipmentType", required = false) String equipmentType) {
        return configurationService.listAttributes(equipmentType);
    }

    @PostMapping("/attributes")
    public Map<String, Object> createAttribute(@RequestBody(required = false) Map<String, Object> payload) {
        return configurationService.createAttribute(payload);
    }

    @PutMapping("/attributes/{attributeId}")
    public Map<String, Object> updateAttribute(
            @PathVariable long attributeId,
            @RequestBody(required = false) Map<String, Object> payload) {
        return configurationService.updateAttribute(attributeId, payload);
    }

    @DeleteMapping("/attributes/{attributeId}")
    public Map<String, Object> deleteAttribute(@PathVariable long attributeId) {
        return configurationService.deleteAttribute(attributeId);
    }

    @GetMapping("/profiles")
    public Map<String, Object> listProfiles(
            @RequestParam(name = "equipmentType", required = false) String equipmentType,
            @RequestParam(name = "catalogId", required = false) Long catalogId) {
        return configurationService.listProfiles(equipmentType, catalogId);
    }

    @PostMapping("/profiles")
    public Map<String, Object> createProfile(@RequestBody(required = false) Map<String, Object> payload) {
        return configurationService.createProfile(payload);
    }

    @PutMapping("/profiles/{profileId}")
    public Map<String, Object> updateProfile(
            @PathVariable long profileId,
            @RequestBody(required = false) Map<String, Object> payload) {
        return configurationService.updateProfile(profileId, payload);
    }

    @DeleteMapping("/profiles/{profileId}")
    public Map<String, Object> deleteProfile(@PathVariable long profileId) {
        return configurationService.deleteProfile(profileId);
    }
}
