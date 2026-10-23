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
public class IncidentMonitoringPolicyController {
    private final IncidentMonitoringPolicyService policy;
    public IncidentMonitoringPolicyController(IncidentMonitoringPolicyService policy) { this.policy = policy; }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PAGE_DIALOGS','PAGE_TASKS','PAGE_OBJECT_PASSPORTS','PAGE_SETTINGS')")
    public Map<String, Object> get(Authentication auth) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(policy.overview());
        var authorities = auth.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
        result.put("can_edit", authorities.contains("ROLE_ADMIN") && authorities.contains("PAGE_SETTINGS"));
        return result;
    }

    @PatchMapping("/{direction}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> update(@PathVariable String direction, @RequestBody Map<String, Object> payload, Authentication auth) {
        return policy.update(direction, payload, auth.getName());
    }
}
