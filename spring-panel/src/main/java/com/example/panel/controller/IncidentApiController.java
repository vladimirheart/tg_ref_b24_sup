package com.example.panel.controller;

import com.example.panel.service.IncidentOpsMetricsService;
import com.example.panel.service.IncidentService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@PreAuthorize("hasAnyAuthority('PAGE_DIALOGS','PAGE_TASKS','PAGE_OBJECT_PASSPORTS')")
public class IncidentApiController {

    private final IncidentService incidentService;
    private final IncidentOpsMetricsService incidentOpsMetricsService;

    public IncidentApiController(IncidentService incidentService,
                                 IncidentOpsMetricsService incidentOpsMetricsService) {
        this.incidentService = incidentService;
        this.incidentOpsMetricsService = incidentOpsMetricsService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(name = "status", required = false) String status,
                                    @RequestParam(name = "severity", required = false) String severity,
                                    @RequestParam(name = "relation_type", required = false) String relationType,
                                    @RequestParam(name = "relation_key", required = false) String relationKey,
                                    @RequestParam(name = "query", required = false) String query,
                                    @RequestParam(name = "signal_type", required = false) String signalType,
                                    @RequestParam(name = "limit", required = false) Integer limit) {
        return incidentService.listIncidents(status, severity, relationType, relationKey, query, signalType, limit);
    }

    @GetMapping("/ops-summary")
    public Map<String, Object> opsSummary() {
        return incidentOpsMetricsService.buildSummary();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable("id") Long id) {
        return incidentService.getIncident(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> payload,
                                      Authentication authentication) {
        return incidentService.createIncident(payload, authentication != null ? authentication.getName() : null);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable("id") Long id,
                                      @RequestBody Map<String, Object> payload,
                                      Authentication authentication) {
        return incidentService.updateIncident(id, payload, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/{id}/events")
    public Map<String, Object> addEvent(@PathVariable("id") Long id,
                                        @RequestBody Map<String, Object> payload,
                                        Authentication authentication) {
        return incidentService.addIncidentEvent(id, payload, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/{id}/watchers")
    public Map<String, Object> addWatcher(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> payload,
                                          Authentication authentication) {
        return incidentService.addWatcher(id, payload.get("watcher_identity"), authentication != null ? authentication.getName() : null);
    }

    @DeleteMapping("/{id}/watchers/{watcherIdentity}")
    public Map<String, Object> removeWatcher(@PathVariable("id") Long id,
                                             @PathVariable("watcherIdentity") String watcherIdentity,
                                             Authentication authentication) {
        return incidentService.removeWatcher(id, watcherIdentity, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/{id}/routes")
    public Map<String, Object> addRoute(@PathVariable("id") Long id,
                                        @RequestBody Map<String, Object> payload,
                                        Authentication authentication) {
        return incidentService.addRoute(id, payload, authentication != null ? authentication.getName() : null);
    }

    @PatchMapping("/{id}/routes/{routeId}")
    public Map<String, Object> updateRoute(@PathVariable("id") Long id,
                                           @PathVariable("routeId") Long routeId,
                                           @RequestBody Map<String, Object> payload,
                                           Authentication authentication) {
        return incidentService.updateRoute(id, routeId, payload, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/{id}/routes/{routeId}/redeliver")
    public Map<String, Object> redeliverRoute(@PathVariable("id") Long id,
                                              @PathVariable("routeId") Long routeId,
                                              Authentication authentication) {
        return incidentService.redeliverRoute(id, routeId, authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/{id}/routes/redeliver-failed")
    public Map<String, Object> redeliverFailedRoutes(@PathVariable("id") Long id,
                                                     @RequestParam(name = "limit", required = false, defaultValue = "25") Integer limit,
                                                     Authentication authentication) {
        return incidentService.redeliverFailedRoutes(id, limit, authentication != null ? authentication.getName() : null);
    }
}
