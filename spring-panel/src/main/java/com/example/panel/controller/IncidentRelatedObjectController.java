package com.example.panel.controller;

import com.example.panel.service.IncidentRelatedObjectService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@PreAuthorize("hasAnyAuthority('PAGE_DIALOGS','PAGE_TASKS','PAGE_OBJECT_PASSPORTS')")
public class IncidentRelatedObjectController {
    private final IncidentRelatedObjectService service;
    public IncidentRelatedObjectController(IncidentRelatedObjectService service) { this.service = service; }

    @GetMapping("/related-object-options")
    public Map<String, Object> capabilities() { return service.capabilities(); }

    @GetMapping("/related-objects")
    public Map<String, Object> search(@RequestParam String type,
                                      @RequestParam(required = false) String query,
                                      @RequestParam(required = false) Integer limit) {
        return service.search(type, query, limit);
    }

    @PostMapping("/external-objects")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAnyAuthority('PAGE_DIALOGS','PAGE_TASKS','PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> create(@RequestBody Map<String, Object> payload) { return service.createExternal(payload); }
}
