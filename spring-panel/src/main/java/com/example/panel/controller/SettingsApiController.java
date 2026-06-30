package com.example.panel.controller;

import com.example.panel.service.SettingsClientStatusService;
import com.example.panel.service.SettingsIntegrationNetworkProbeService;
import com.example.panel.service.SettingsItConnectionCategoryService;
import com.example.panel.service.SettingsPageDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class SettingsApiController {

    private static final Logger log = LoggerFactory.getLogger(SettingsApiController.class);

    private final SettingsClientStatusService settingsClientStatusService;
    private final SettingsItConnectionCategoryService settingsItConnectionCategoryService;
    private final SettingsIntegrationNetworkProbeService settingsIntegrationNetworkProbeService;
    private final SettingsPageDataService settingsPageDataService;
    private final ObjectMapper objectMapper;

    public SettingsApiController(SettingsClientStatusService settingsClientStatusService,
                                 SettingsItConnectionCategoryService settingsItConnectionCategoryService,
                                 SettingsIntegrationNetworkProbeService settingsIntegrationNetworkProbeService,
                                 SettingsPageDataService settingsPageDataService,
                                 ObjectMapper objectMapper) {
        this.settingsClientStatusService = settingsClientStatusService;
        this.settingsItConnectionCategoryService = settingsItConnectionCategoryService;
        this.settingsIntegrationNetworkProbeService = settingsIntegrationNetworkProbeService;
        this.settingsPageDataService = settingsPageDataService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/page-data/{section}")
    public Map<String, Object> getPageDataSection(@PathVariable String section) {
        try {
            return Map.of(
                    "success", true,
                    "section", section,
                    "data", settingsPageDataService.loadSection(section)
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping({"/client-statuses", "/client-statuses/"})
    public Map<String, Object> updateClientStatuses(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = settingsClientStatusService.updateClientStatuses(payload);
        log.info("Updated client statuses payload via delegated service");
        return response;
    }

    @PostMapping({"/it-connection-categories", "/it-connection-categories/"})
    public Map<String, Object> createItConnectionCategory(HttpServletRequest request) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        return settingsItConnectionCategoryService.createCategory(payload);
    }

    @PostMapping({"/integration-network/profiles/probe", "/integration-network/profiles/probe/"})
    public Map<String, Object> probeIntegrationNetworkProfiles(@RequestBody(required = false) Map<String, Object> payload) {
        return settingsIntegrationNetworkProbeService.probeProfiles(payload);
    }
}
