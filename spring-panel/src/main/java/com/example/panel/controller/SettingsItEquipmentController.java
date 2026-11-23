package com.example.panel.controller;

import com.example.panel.service.SettingsItEquipmentService;
import com.example.panel.service.SettingsItEquipmentHierarchyService;
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
    private final SettingsItEquipmentHierarchyService hierarchyService;
    private final ObjectMapper objectMapper;

    public SettingsItEquipmentController(SettingsItEquipmentService settingsItEquipmentService,
                                         SettingsItEquipmentHierarchyService hierarchyService,
                                         ObjectMapper objectMapper) {
        this.settingsItEquipmentService = settingsItEquipmentService;
        this.hierarchyService = hierarchyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/settings/it-equipment")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listItEquipment() {
        return settingsItEquipmentService.listItEquipment();
    }

    @GetMapping("/api/settings/it-equipment/hierarchy")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listHierarchy() {
        return hierarchyService.listHierarchy();
    }

    @PostMapping("/api/settings/it-equipment/hierarchy/branches")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> createHierarchyBranch(@RequestBody Map<String, Object> payload) {
        return hierarchyService.createBranch(payload);
    }

    @RequestMapping(value = "/api/settings/it-equipment/hierarchy/branches/{branchId}", method = RequestMethod.PATCH)
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> renameHierarchyBranch(@PathVariable long branchId, @RequestBody Map<String, Object> payload) {
        return hierarchyService.renameBranch(branchId, payload);
    }

    @DeleteMapping("/api/settings/it-equipment/hierarchy/branches/{branchId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deleteHierarchyBranch(@PathVariable long branchId) {
        return hierarchyService.deleteBranch(branchId);
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
