package com.example.panel.controller;

import com.example.panel.service.SettingsParameterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@RestController
public class SettingsParametersController {

    private final SettingsParameterService settingsParameterService;
    private final ObjectMapper objectMapper;

    public SettingsParametersController(SettingsParameterService settingsParameterService,
                                        ObjectMapper objectMapper) {
        this.settingsParameterService = settingsParameterService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/settings/parameters")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listParameters() {
        return settingsParameterService.listParameters(true);
    }

    @PostMapping({"/api/settings/parameters", "/api/settings/parameters/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> createParameter(HttpServletRequest request) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        return settingsParameterService.createParameter(payload);
    }

    @RequestMapping(
            value = {"/api/settings/parameters/{paramId}", "/api/settings/parameters/{paramId}/"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH}
    )
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateParameter(@PathVariable long paramId,
                                               @RequestBody Map<String, Object> payload) {
        return settingsParameterService.updateParameter(paramId, payload);
    }

    @DeleteMapping("/api/settings/parameters/{paramId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deleteParameter(@PathVariable long paramId) {
        return settingsParameterService.deleteParameter(paramId);
    }
}
