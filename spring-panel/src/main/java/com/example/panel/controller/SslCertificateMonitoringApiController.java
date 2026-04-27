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

import java.net.IDN;
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
        List<SslCertificateMonitor> monitors = monitoringService.findAll();
        List<Map<String, Object>> items = monitors
            .stream()
            .map(this::toDto)
            .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("items", items);
        payload.put("availability_overview", toAvailabilityOverview(monitoringService.buildAvailabilityOverview(monitors)));
        return payload;
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
        String displayHost = toUnicodeHost(item.getHost());
        String displaySiteName = resolveDisplaySiteName(item.getSiteName(), item.getHost(), displayHost);
        String displayEndpoint = buildDisplayEndpoint(displayHost, item.getPort());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("site_name", item.getSiteName());
        dto.put("site_display_name", displaySiteName);
        dto.put("endpoint_url", item.getEndpointUrl());
        dto.put("endpoint_display", displayEndpoint);
        dto.put("host", item.getHost());
        dto.put("host_display", displayHost);
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

    private String toUnicodeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String trimmed = host.trim();
        try {
            String unicode = IDN.toUnicode(trimmed, IDN.ALLOW_UNASSIGNED);
            return unicode != null && !unicode.isBlank() ? unicode : trimmed;
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private String resolveDisplaySiteName(String siteName, String host, String displayHost) {
        if (siteName == null || siteName.isBlank()) {
            return displayHost;
        }
        String trimmed = siteName.trim();
        if (host != null && !host.isBlank() && trimmed.equalsIgnoreCase(host.trim())) {
            return displayHost;
        }
        if (trimmed.startsWith("xn--") || trimmed.contains(".xn--")) {
            try {
                String unicode = IDN.toUnicode(trimmed, IDN.ALLOW_UNASSIGNED);
                if (unicode != null && !unicode.isBlank()) {
                    return unicode;
                }
            } catch (Exception ignored) {
                // fallback to original value
            }
        }
        return trimmed;
    }

    private String buildDisplayEndpoint(String displayHost, Integer port) {
        if (displayHost == null || displayHost.isBlank()) {
            return "";
        }
        int safePort = port != null && port > 0 ? port : 443;
        return safePort == 443 ? "https://" + displayHost : "https://" + displayHost + ":" + safePort;
    }

    private record SitePayload(String siteName, String endpointUrl, Boolean enabled) {
    }

    private Map<String, Object> toAvailabilityOverview(SslCertificateMonitoringService.AvailabilityOverview overview) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", overview.total());
        payload.put("up", overview.up());
        payload.put("down", overview.down());
        payload.put("unknown", overview.unknown());
        payload.put("disabled", overview.disabled());
        payload.put("availability_percent", overview.availabilityPercent());
        return payload;
    }
}
