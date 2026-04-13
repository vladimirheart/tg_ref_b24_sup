package com.example.panel.controller;

import com.example.panel.entity.SslCertificateMonitor;
import com.example.panel.service.SslCertificateMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring/certificates")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class SslCertificateMonitoringApiController {

    private final SslCertificateMonitoringService monitoringService;

    public SslCertificateMonitoringApiController(SslCertificateMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/sites")
    public Map<String, Object> listSites() {
        List<Map<String, Object>> items = monitoringService.findAll()
            .stream()
            .map(this::toDto)
            .toList();
        return Map.of(
            "success", true,
            "items", items
        );
    }

    @PostMapping("/sites")
    public ResponseEntity<Map<String, Object>> createSite(@RequestBody(required = false) SitePayload payload) {
        try {
            SitePayload source = payload != null ? payload : new SitePayload(null, null, true);
            SslCertificateMonitor created = monitoringService.createSite(source.siteName(), source.endpointUrl(), source.enabled());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "item", toDto(created)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PatchMapping("/sites/{siteId}")
    public ResponseEntity<Map<String, Object>> updateSite(@PathVariable long siteId,
                                                          @RequestBody(required = false) SitePayload payload) {
        try {
            SitePayload source = payload != null ? payload : new SitePayload(null, null, true);
            SslCertificateMonitor updated = monitoringService.updateSite(siteId, source.siteName(), source.endpointUrl(), source.enabled());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "item", toDto(updated)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @DeleteMapping("/sites/{siteId}")
    public ResponseEntity<Map<String, Object>> deleteSite(@PathVariable long siteId) {
        try {
            monitoringService.deleteSite(siteId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/sites/{siteId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshSite(@PathVariable long siteId) {
        try {
            SslCertificateMonitor item = monitoringService.refreshById(siteId, false);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "item", toDto(item)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        SslCertificateMonitoringService.RefreshSummary summary = monitoringService.refreshAll(false);
        List<Map<String, Object>> items = monitoringService.findAll()
            .stream()
            .map(this::toDto)
            .toList();
        return Map.of(
            "success", true,
            "summary", Map.of(
                "total", summary.total(),
                "checked", summary.checked(),
                "notified", summary.notified()
            ),
            "items", items
        );
    }

    private Map<String, Object> toDto(SslCertificateMonitor item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("site_name", item.getSiteName());
        dto.put("endpoint_url", item.getEndpointUrl());
        dto.put("host", item.getHost());
        dto.put("port", item.getPort());
        dto.put("enabled", item.getEnabled());
        dto.put("monitor_status", item.getMonitorStatus());
        dto.put("status_level", monitoringService.resolveSeverity(item));
        dto.put("availability", monitoringService.resolveAvailability(item));
        dto.put("error_message", item.getErrorMessage());
        dto.put("days_left", item.getDaysLeft());
        dto.put("expires_at", item.getExpiresAt());
        dto.put("last_checked_at", item.getLastCheckedAt());
        dto.put("last_notified_at", item.getLastNotifiedAt());
        dto.put("created_at", item.getCreatedAt());
        dto.put("updated_at", item.getUpdatedAt());
        return dto;
    }

    private record SitePayload(String siteName, String endpointUrl, Boolean enabled) {
    }
}
