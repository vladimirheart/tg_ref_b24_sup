package com.example.panel.controller;

import com.example.panel.entity.RmsLicenseMonitor;
import com.example.panel.service.RmsLicenseMonitoringService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring/rms")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class RmsLicenseMonitoringApiController {

    private final RmsLicenseMonitoringService monitoringService;

    public RmsLicenseMonitoringApiController(RmsLicenseMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/sites")
    public Map<String, Object> listSites() {
        List<RmsLicenseMonitor> monitors = monitoringService.findAll();
        List<Map<String, Object>> items = monitors.stream().map(this::toDto).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("items", items);
        payload.put("refresh_state", toRefreshState(monitoringService.currentRefreshState()));
        payload.put("availability_overview", toAvailabilityOverview(monitoringService.buildAvailabilityOverview(monitors)));
        return payload;
    }

    @PostMapping("/sites")
    public ResponseEntity<Map<String, Object>> createSite(@RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : new MonitorPayload(null, null, null, true, true, true);
            RmsLicenseMonitor created = monitoringService.createMonitor(
                source.rmsAddress(),
                source.authLogin(),
                source.authPassword(),
                source.enabled(),
                source.licenseMonitoringEnabled(),
                source.networkMonitoringEnabled()
            );
            return ResponseEntity.ok(Map.of("success", true, "item", toDto(created)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PatchMapping("/sites/{siteId}")
    public ResponseEntity<Map<String, Object>> updateSite(@PathVariable long siteId,
                                                          @RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : new MonitorPayload(null, null, null, true, true, true);
            RmsLicenseMonitor updated = monitoringService.updateMonitor(
                siteId,
                source.rmsAddress(),
                source.authLogin(),
                source.authPassword(),
                source.enabled(),
                source.licenseMonitoringEnabled(),
                source.networkMonitoringEnabled()
            );
            return ResponseEntity.ok(Map.of("success", true, "item", toDto(updated)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @DeleteMapping("/sites/{siteId}")
    public ResponseEntity<Map<String, Object>> deleteSite(@PathVariable long siteId) {
        try {
            monitoringService.deleteMonitor(siteId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/refresh/licenses")
    public Map<String, Object> refreshLicenses() {
        RmsLicenseMonitoringService.RefreshRequestResult result = monitoringService.requestLicenseRefresh(true);
        return Map.of(
            "success", true,
            "state", result.state(),
            "refresh_state", toRefreshState(result.refreshState())
        );
    }

    @PostMapping("/refresh/network")
    public Map<String, Object> refreshNetwork() {
        RmsLicenseMonitoringService.RefreshRequestResult result = monitoringService.requestNetworkRefresh();
        return Map.of(
            "success", true,
            "state", result.state(),
            "refresh_state", toRefreshState(result.refreshState())
        );
    }

    @PostMapping("/sites/{siteId}/refresh/licenses")
    public ResponseEntity<Map<String, Object>> refreshSiteLicense(@PathVariable long siteId) {
        try {
            RmsLicenseMonitoringService.RefreshRequestResult result =
                monitoringService.requestLicenseRefreshForMonitor(siteId, true);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "state", result.state(),
                "refresh_state", toRefreshState(result.refreshState())
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/sites/{siteId}/refresh/network")
    public ResponseEntity<Map<String, Object>> refreshSiteNetwork(@PathVariable long siteId) {
        try {
            RmsLicenseMonitoringService.RefreshRequestResult result =
                monitoringService.requestNetworkRefreshForMonitor(siteId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "state", result.state(),
                "refresh_state", toRefreshState(result.refreshState())
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/bulk/license-monitoring")
    public Map<String, Object> bulkToggleLicenseMonitoring(@RequestBody(required = false) FeatureTogglePayload payload) {
        boolean enabled = payload == null || payload.enabled() == null || payload.enabled();
        monitoringService.setLicenseMonitoringEnabledForAll(enabled);
        return Map.of("success", true, "enabled", enabled);
    }

    @PostMapping("/bulk/network-monitoring")
    public Map<String, Object> bulkToggleNetworkMonitoring(@RequestBody(required = false) FeatureTogglePayload payload) {
        boolean enabled = payload == null || payload.enabled() == null || payload.enabled();
        monitoringService.setNetworkMonitoringEnabledForAll(enabled);
        return Map.of("success", true, "enabled", enabled);
    }

    @GetMapping(value = "/sites/{siteId}/traceroute", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<byte[]> downloadTraceroute(@PathVariable long siteId) {
        try {
            String report = monitoringService.loadTracerouteReport(siteId);
            byte[] body = report.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rms-traceroute-" + siteId + ".txt\"")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(ex.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/sites/{siteId}/licenses")
    public ResponseEntity<Map<String, Object>> loadSiteLicenses(@PathVariable long siteId) {
        try {
            RmsLicenseMonitoringService.LicenseDetailsView view = monitoringService.loadLicenseDetails(siteId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", view.id(),
                "rms_address", view.rmsAddress(),
                "server_name", view.serverName(),
                "server_name_display", view.serverName(),
                "last_checked_at", view.lastCheckedAt(),
                "items", view.items()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping("/sites/{siteId}/diagnostics")
    public ResponseEntity<Map<String, Object>> loadSiteDiagnostics(@PathVariable long siteId) {
        try {
            RmsLicenseMonitoringService.DiagnosticsView view = monitoringService.loadDiagnostics(siteId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("id", view.id());
            payload.put("rms_address", view.rmsAddress());
            payload.put("server_name", view.serverName());
            payload.put("server_name_display", view.serverName());
            payload.put("license_last_checked_at", view.licenseLastCheckedAt());
            payload.put("rms_last_checked_at", view.rmsLastCheckedAt());
            payload.put("license_status", view.licenseStatus());
            payload.put("license_error_message", view.licenseErrorMessage());
            payload.put("license_debug_excerpt", view.licenseDebugExcerpt());
            payload.put("rms_status", view.rmsStatus());
            payload.put("rms_status_message", view.rmsStatusMessage());
            payload.put("ping_output", view.pingOutput());
            payload.put("traceroute_summary", view.tracerouteSummary());
            payload.put("traceroute_report", view.tracerouteReport());
            payload.put("timeline", view.timeline());
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    private Map<String, Object> toDto(RmsLicenseMonitor item) {
        String displayHost = toUnicodeHost(item.getHost());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("rms_address", item.getRmsAddress());
        dto.put("rms_address_display", buildDisplayAddress(item.getScheme(), displayHost, item.getPort()));
        dto.put("host", item.getHost());
        dto.put("host_display", displayHost);
        dto.put("port", item.getPort());
        dto.put("scheme", item.getScheme());
        dto.put("auth_login", item.getAuthLogin());
        dto.put("password_saved", item.getAuthPassword() != null && !item.getAuthPassword().isBlank());
        dto.put("enabled", item.getEnabled());
        dto.put("license_monitoring_enabled", item.getLicenseMonitoringEnabled());
        dto.put("network_monitoring_enabled", item.getNetworkMonitoringEnabled());
        dto.put("license_refresh_state", monitoringService.resolveLicenseRefreshState(item));
        dto.put("network_refresh_state", monitoringService.resolveNetworkRefreshState(item));
        dto.put("server_name", item.getServerName());
        dto.put("server_name_display", monitoringService.resolveDisplayServerNameForView(item));
        dto.put("server_type", item.getServerType());
        dto.put("server_version", item.getServerVersion());
        dto.put("license_status", item.getLicenseStatus());
        dto.put("license_status_level", monitoringService.resolveLicenseSeverity(item));
        dto.put("license_error_message", item.getLicenseErrorMessage());
        dto.put("license_expires_at", item.getLicenseExpiresAt());
        dto.put("license_days_left", item.getLicenseDaysLeft());
        dto.put("target_license_id", monitoringService.resolveTargetLicenseId(item));
        dto.put("target_license_label", monitoringService.resolveTargetLicenseLabel(item));
        dto.put("target_license_count", monitoringService.resolveTargetLicenseQuantity(item));
        dto.put("kiosk_connector_license_count", monitoringService.resolveKioskConnectorLicenseQuantity(item));
        dto.put("license_last_checked_at", item.getLicenseLastCheckedAt());
        dto.put("has_license_details", item.getLicenseDetailsJson() != null && !item.getLicenseDetailsJson().isBlank());
        dto.put("has_diagnostics",
            (item.getPingOutput() != null && !item.getPingOutput().isBlank())
                || (item.getTracerouteReport() != null && !item.getTracerouteReport().isBlank())
                || (item.getLicenseDebugExcerpt() != null && !item.getLicenseDebugExcerpt().isBlank())
                || (item.getLicenseErrorMessage() != null && !item.getLicenseErrorMessage().isBlank()));
        dto.put("rms_status", item.getRmsStatus());
        dto.put("rms_status_level", monitoringService.resolveRmsAvailability(item));
        dto.put("rms_status_message", item.getRmsStatusMessage());
        dto.put("rms_last_checked_at", item.getRmsLastCheckedAt());
        dto.put("traceroute_summary", item.getTracerouteSummary());
        dto.put("traceroute_checked_at", item.getTracerouteCheckedAt());
        dto.put("has_traceroute_report", item.getTracerouteReport() != null && !item.getTracerouteReport().isBlank());
        dto.put("created_at", item.getCreatedAt());
        dto.put("updated_at", item.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toRefreshState(RmsLicenseMonitoringService.RefreshState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("licenses", toQueueState(state.licenses()));
        payload.put("network", toQueueState(state.network()));
        return payload;
    }

    private Map<String, Object> toQueueState(RmsLicenseMonitoringService.QueueState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("running", state.running());
        payload.put("queued", state.queued());
        payload.put("last_requested_at", state.lastRequestedAt());
        payload.put("last_completed_at", state.lastCompletedAt());
        payload.put("current_monitor_id", state.currentMonitorId());
        payload.put("total_count", state.totalCount());
        payload.put("completed_count", state.completedCount());
        return payload;
    }

    private Map<String, Object> toAvailabilityOverview(RmsLicenseMonitoringService.AvailabilityOverview overview) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", overview.total());
        payload.put("up", overview.up());
        payload.put("down", overview.down());
        payload.put("unknown", overview.unknown());
        payload.put("disabled", overview.disabled());
        payload.put("availability_percent", overview.availabilityPercent());
        return payload;
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

    private String buildDisplayAddress(String scheme, String displayHost, Integer port) {
        if (displayHost == null || displayHost.isBlank()) {
            return "";
        }
        String safeScheme = scheme != null && !scheme.isBlank() ? scheme.trim() : "https";
        int safePort = port != null && port > 0 ? port : ("http".equalsIgnoreCase(safeScheme) ? 80 : 443);
        boolean defaultPort = ("https".equalsIgnoreCase(safeScheme) && safePort == 443)
            || ("http".equalsIgnoreCase(safeScheme) && safePort == 80);
        return safeScheme + "://" + displayHost + (defaultPort ? "" : ":" + safePort);
    }

    private record MonitorPayload(String rmsAddress,
                                  String authLogin,
                                  String authPassword,
                                  Boolean enabled,
                                  Boolean licenseMonitoringEnabled,
                                  Boolean networkMonitoringEnabled) {
    }

    private record FeatureTogglePayload(Boolean enabled) {
    }
}
