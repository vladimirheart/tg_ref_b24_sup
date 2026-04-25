package com.example.panel.controller;

import com.example.panel.entity.IikoApiMonitor;
import com.example.panel.service.IikoApiMonitoringService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitoring/iiko")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class IikoApiMonitoringApiController {

    private final IikoApiMonitoringService monitoringService;

    public IikoApiMonitoringApiController(IikoApiMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/monitors")
    public Map<String, Object> listMonitors() {
        List<IikoApiMonitoringService.RequestTypeOption> requestTypes = monitoringService.requestTypeCatalog();
        Map<String, String> requestTypeLabels = requestTypes.stream().collect(Collectors.toMap(
            IikoApiMonitoringService.RequestTypeOption::code,
            IikoApiMonitoringService.RequestTypeOption::label,
            (left, right) -> left,
            LinkedHashMap::new
        ));
        List<Map<String, Object>> items = monitoringService.findAll().stream()
            .map(item -> toDto(item, requestTypeLabels))
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("items", items);
        payload.put("request_types", requestTypes);
        payload.put("refresh_state", toRefreshState(monitoringService.currentRefreshState()));
        return payload;
    }

    @PostMapping("/monitors")
    public ResponseEntity<Map<String, Object>> createMonitor(@RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : emptyPayload();
            IikoApiMonitor created = monitoringService.createMonitor(toDraft(source));
            return ResponseEntity.ok(successItemResponse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @PatchMapping("/monitors/{monitorId}")
    public ResponseEntity<Map<String, Object>> updateMonitor(@PathVariable long monitorId,
                                                             @RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : emptyPayload();
            IikoApiMonitor updated = monitoringService.updateMonitor(monitorId, toDraft(source));
            return ResponseEntity.ok(successItemResponse(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @DeleteMapping("/monitors/{monitorId}")
    public ResponseEntity<Map<String, Object>> deleteMonitor(@PathVariable long monitorId) {
        try {
            monitoringService.deleteMonitor(monitorId);
            return ResponseEntity.ok(successOnly());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        IikoApiMonitoringService.RefreshRequestResult result = monitoringService.requestRefresh();
        return refreshResponse(result);
    }

    @PostMapping("/monitors/{monitorId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshMonitor(@PathVariable long monitorId) {
        try {
            IikoApiMonitoringService.RefreshRequestResult result = monitoringService.requestRefreshForMonitor(monitorId);
            return ResponseEntity.ok(refreshResponse(result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @PostMapping("/bulk/enabled")
    public Map<String, Object> bulkToggle(@RequestBody(required = false) EnabledPayload payload) {
        boolean enabled = payload == null || payload.enabled() == null || payload.enabled();
        monitoringService.setEnabledForAll(enabled);
        Map<String, Object> response = successOnly();
        response.put("enabled", enabled);
        return response;
    }

    @GetMapping("/monitors/{monitorId}/response")
    public ResponseEntity<Map<String, Object>> loadLastResponse(@PathVariable long monitorId) {
        try {
            IikoApiMonitoringService.LastResponseView view = monitoringService.loadLastResponse(monitorId);
            Map<String, Object> payload = successOnly();
            payload.put("id", view.id());
            payload.put("monitor_name", view.monitorName());
            payload.put("request_type", view.requestType());
            payload.put("request_type_label", view.requestTypeLabel());
            payload.put("last_checked_at", view.lastCheckedAt());
            payload.put("last_http_status", view.lastHttpStatus());
            payload.put("last_duration_ms", view.lastDurationMs());
            payload.put("last_error_message", view.lastErrorMessage());
            payload.put("summary", view.summary());
            payload.put("response_excerpt", view.responseExcerpt());
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    private IikoApiMonitoringService.MonitorDraft toDraft(MonitorPayload payload) {
        IikoApiMonitoringService.MonitorConfig config = new IikoApiMonitoringService.MonitorConfig(
            sanitizeList(payload.organizationIds()),
            normalize(payload.organizationId()),
            sanitizeList(payload.terminalGroupIds()),
            normalize(payload.externalMenuId()),
            normalize(payload.priceCategoryId()),
            payload.menuVersion(),
            normalize(payload.language()),
            payload.startRevision(),
            payload.returnAdditionalInfo(),
            payload.includeDisabled(),
            sanitizeList(payload.returnExternalData()),
            payload.returnSize()
        );
        return new IikoApiMonitoringService.MonitorDraft(
            normalize(payload.monitorName()),
            normalize(payload.baseUrl()),
            normalize(payload.apiLogin()),
            normalize(payload.requestType()),
            config,
            payload.enabled()
        );
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(this::normalize)
            .filter(item -> item != null && !item.isBlank())
            .toList();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> successItemResponse(IikoApiMonitor item) {
        Map<String, String> requestTypeLabels = monitoringService.requestTypeCatalog().stream().collect(Collectors.toMap(
            IikoApiMonitoringService.RequestTypeOption::code,
            IikoApiMonitoringService.RequestTypeOption::label,
            (left, right) -> left,
            LinkedHashMap::new
        ));
        Map<String, Object> payload = successOnly();
        payload.put("item", toDto(item, requestTypeLabels));
        return payload;
    }

    private Map<String, Object> refreshResponse(IikoApiMonitoringService.RefreshRequestResult result) {
        Map<String, Object> payload = successOnly();
        payload.put("state", result.state());
        payload.put("refresh_state", toRefreshState(result.refreshState()));
        return payload;
    }

    private Map<String, Object> toDto(IikoApiMonitor item, Map<String, String> requestTypeLabels) {
        IikoApiMonitoringService.MonitorConfig config = monitoringService.readConfig(item);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("monitor_name", item.getMonitorName());
        dto.put("base_url", item.getBaseUrl());
        dto.put("api_login", item.getApiLogin());
        dto.put("request_type", item.getRequestType());
        dto.put("request_type_label", requestTypeLabels.getOrDefault(item.getRequestType(), item.getRequestType()));
        dto.put("organization_ids", config.organizationIds());
        dto.put("organization_id", config.organizationId());
        dto.put("terminal_group_ids", config.terminalGroupIds());
        dto.put("external_menu_id", config.externalMenuId());
        dto.put("price_category_id", config.priceCategoryId());
        dto.put("menu_version", config.menuVersion());
        dto.put("language", config.language());
        dto.put("start_revision", config.startRevision());
        dto.put("return_additional_info", config.returnAdditionalInfo());
        dto.put("include_disabled", config.includeDisabled());
        dto.put("return_external_data", config.returnExternalData());
        dto.put("return_size", config.returnSize());
        dto.put("enabled", item.getEnabled());
        dto.put("last_status", item.getLastStatus());
        dto.put("last_status_level", monitoringService.resolveStatus(item));
        dto.put("last_http_status", item.getLastHttpStatus());
        dto.put("last_error_message", item.getLastErrorMessage());
        dto.put("last_duration_ms", item.getLastDurationMs());
        dto.put("last_checked_at", item.getLastCheckedAt());
        dto.put("last_token_checked_at", item.getLastTokenCheckedAt());
        dto.put("last_response_summary", monitoringService.readResponseSummary(item));
        dto.put("has_response_excerpt", item.getLastResponseExcerpt() != null && !item.getLastResponseExcerpt().isBlank());
        dto.put("consecutive_failures", item.getConsecutiveFailures());
        dto.put("created_at", item.getCreatedAt());
        dto.put("updated_at", item.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toRefreshState(IikoApiMonitoringService.RefreshState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("running", state.running());
        payload.put("queued", state.queued());
        payload.put("last_requested_at", state.lastRequestedAt());
        payload.put("last_completed_at", state.lastCompletedAt());
        return payload;
    }

    private Map<String, Object> successOnly() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        return payload;
    }

    private Map<String, Object> errorResponse(String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", error);
        return payload;
    }

    private MonitorPayload emptyPayload() {
        return new MonitorPayload(
            null, null, null, null,
            List.of(), null, List.of(),
            null, null, null, null, null,
            null, null, List.of(), null, null
        );
    }

    private record MonitorPayload(String monitorName,
                                  String baseUrl,
                                  String apiLogin,
                                  String requestType,
                                  List<String> organizationIds,
                                  String organizationId,
                                  List<String> terminalGroupIds,
                                  String externalMenuId,
                                  String priceCategoryId,
                                  Integer menuVersion,
                                  String language,
                                  Long startRevision,
                                  Boolean returnAdditionalInfo,
                                  Boolean includeDisabled,
                                  List<String> returnExternalData,
                                  Boolean returnSize,
                                  Boolean enabled) {
    }

    private record EnabledPayload(Boolean enabled) {
    }
}
