package com.example.panel.controller;

import com.example.panel.service.IncidentMonitoringPolicyService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/monitoring")
@PreAuthorize(com.example.panel.service.SharedSystemAccessService.REQUIRE)
public class IncidentMonitoringPolicyController {
    private final IncidentMonitoringPolicyService policy;
    public IncidentMonitoringPolicyController(IncidentMonitoringPolicyService policy) { this.policy = policy; }

    @GetMapping
    public Map<String, Object> get(Authentication auth) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(policy.overview());
        result.put("can_edit", true);
        return result;
    }

    @PatchMapping("/{direction}")
    public Map<String, Object> update(@PathVariable String direction, @RequestBody Map<String, Object> payload, Authentication auth) {
        return policy.update(direction, payload, auth.getName());
    }
}
