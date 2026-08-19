package com.example.panel.controller;

import com.example.panel.service.integration.IntegrationTransportOpsService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/integration-transport")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class AnalyticsIntegrationTransportController {

    private final IntegrationTransportOpsService integrationTransportOpsService;

    public AnalyticsIntegrationTransportController(IntegrationTransportOpsService integrationTransportOpsService) {
        this.integrationTransportOpsService = integrationTransportOpsService;
    }

    @GetMapping
    public Map<String, Object> overview() {
        return integrationTransportOpsService.buildOverview();
    }

    @PostMapping("/inbound-events/{eventId}/replay")
    public Map<String, Object> replayInboundEvent(@PathVariable("eventId") String eventId,
                                                  Authentication authentication) {
        return integrationTransportOpsService.replayInboundEvent(eventId, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/inbound-events/replay-failed")
    public Map<String, Object> replayFailedInboundEvents(@RequestParam(name = "limit", required = false, defaultValue = "25") Integer limit,
                                                         Authentication authentication) {
        return integrationTransportOpsService.replayFailedInboundEvents(limit != null ? limit : 25, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/outbox-events/{eventId}/requeue")
    public Map<String, Object> requeueOutboundEvent(@PathVariable("eventId") String eventId,
                                                    Authentication authentication) {
        return integrationTransportOpsService.requeueOutboundEvent(eventId, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/outbox-events/requeue-failed")
    public Map<String, Object> requeueFailedOutboundEvents(@RequestParam(name = "limit", required = false, defaultValue = "25") Integer limit,
                                                           Authentication authentication) {
        return integrationTransportOpsService.requeueFailedOutboundEvents(limit != null ? limit : 25, authentication != null ? authentication.getName() : null);
    }
}
