package com.example.panel.controller;

import com.example.panel.service.AnalyticsBotRuntimeMonitoringService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/bot-runtime")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class AnalyticsBotRuntimeMonitoringController {

    private final AnalyticsBotRuntimeMonitoringService analyticsBotRuntimeMonitoringService;

    public AnalyticsBotRuntimeMonitoringController(AnalyticsBotRuntimeMonitoringService analyticsBotRuntimeMonitoringService) {
        this.analyticsBotRuntimeMonitoringService = analyticsBotRuntimeMonitoringService;
    }

    @GetMapping
    public Map<String, Object> overview() {
        return analyticsBotRuntimeMonitoringService.buildOverview();
    }
}
