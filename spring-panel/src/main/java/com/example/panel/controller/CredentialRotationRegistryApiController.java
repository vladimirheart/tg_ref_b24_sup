package com.example.panel.controller;

import com.example.panel.entity.CredentialRotationRegistryEntry;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.service.CredentialRotationRegistryService;
import com.example.panel.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.Locale;

@RestController
@RequestMapping("/api/monitoring/credential-rotation")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class CredentialRotationRegistryApiController {

    private final CredentialRotationRegistryService registryService;
    private final IncidentService incidentService;

    public CredentialRotationRegistryApiController(CredentialRotationRegistryService registryService,
                                                   IncidentService incidentService) {
        this.registryService = registryService;
        this.incidentService = incidentService;
    }

    @GetMapping("/entries")
    public Map<String, Object> listEntries() {
        CredentialRotationRegistryService.RegistrySnapshot snapshot = registryService.buildSnapshot();
        Map<String, List<Map<String, Object>>> relatedIncidents = loadRelatedIncidents();
        Map<String, Object> payload = successOnly();
        payload.put("generated_at", snapshot.generatedAt());
        payload.put("overview", toOverview(snapshot.overview()));
        payload.put("incident_alerting", toIncidentAlertingOverview(relatedIncidents));
        payload.put("items", snapshot.items().stream().map(item -> toDto(item, relatedIncidents)).toList());
        return payload;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        CredentialRotationRegistryService.RegistrySnapshot snapshot = registryService.refreshAll();
        Map<String, List<Map<String, Object>>> relatedIncidents = loadRelatedIncidents();
        Map<String, Object> payload = successOnly();
        payload.put("generated_at", snapshot.generatedAt());
        payload.put("overview", toOverview(snapshot.overview()));
        payload.put("incident_alerting", toIncidentAlertingOverview(relatedIncidents));
        payload.put("items", snapshot.items().stream().map(item -> toDto(item, relatedIncidents)).toList());
        return payload;
    }

    @PatchMapping("/entries/{entryId}")
    public ResponseEntity<Map<String, Object>> updateMetadata(@PathVariable long entryId,
                                                              @RequestBody(required = false) UpdatePayload payload) {
        try {
            UpdatePayload source = payload != null ? payload : new UpdatePayload(null, null, null, null, null);
            CredentialRotationRegistryEntry item = registryService.updateMetadata(
                entryId,
                new CredentialRotationRegistryService.MetadataPatch(
                    source.ownerName(),
                    source.note(),
                    source.expiresAt(),
                    source.rotatedAt(),
                    source.rotationIntervalDays()
                )
            );
            Map<String, Object> response = successOnly();
            Map<String, List<Map<String, Object>>> relatedIncidents = loadRelatedIncidents();
            response.put("item", toDto(item, relatedIncidents));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @GetMapping("/entries/{entryId}/history")
    public ResponseEntity<Map<String, Object>> loadHistory(@PathVariable long entryId) {
        try {
            List<Map<String, Object>> items = registryService.loadHistory(entryId, 20).stream()
                .map(this::toHistoryDto)
                .toList();
            Map<String, Object> response = successOnly();
            response.put("items", items);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
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

    private Map<String, Object> toDto(CredentialRotationRegistryEntry item,
                                      Map<String, List<Map<String, Object>>> incidentsByEntryKey) {
        Map<String, Object> dto = new LinkedHashMap<>();
        List<Map<String, Object>> relatedIncidents = incidentsByEntryKey.getOrDefault(normalizeKey(item.getEntryKey()), List.of());
        dto.put("id", item.getId());
        dto.put("entry_key", item.getEntryKey());
        dto.put("integration_kind", item.getIntegrationKind());
        dto.put("credential_kind", item.getCredentialKind());
        dto.put("display_name", item.getDisplayName());
        dto.put("source_type", item.getSourceType());
        dto.put("source_ref", item.getSourceRef());
        dto.put("owner_name", item.getOwnerName());
        dto.put("note", item.getNote());
        dto.put("source_present", item.getSourcePresent());
        dto.put("secret_present", item.getSecretPresent());
        dto.put("last_status", item.getLastStatus());
        dto.put("status_level", item.getStatusLevel());
        dto.put("status_reason", item.getStatusReason());
        dto.put("expires_at", item.getExpiresAt());
        dto.put("rotated_at", item.getRotatedAt());
        dto.put("rotation_interval_days", item.getRotationIntervalDays());
        dto.put("next_rotation_due_at", item.getNextRotationDueAt());
        dto.put("last_seen_at", item.getLastSeenAt());
        dto.put("last_checked_at", item.getLastCheckedAt());
        dto.put("created_at", item.getCreatedAt());
        dto.put("updated_at", item.getUpdatedAt());
        dto.put("has_active_incident", relatedIncidents.stream().anyMatch(this::isActiveIncident));
        dto.put("active_incident_count", relatedIncidents.stream().filter(this::isActiveIncident).count());
        dto.put("related_incidents", relatedIncidents);
        return dto;
    }

    private Map<String, Object> toOverview(CredentialRotationRegistryService.RegistryOverview overview) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("total", overview.total());
        dto.put("ok", overview.ok());
        dto.put("warning", overview.warning());
        dto.put("critical", overview.critical());
        dto.put("tracking_missing", overview.trackingMissing());
        dto.put("missing_secret", overview.missingSecret());
        dto.put("source_removed", overview.sourceRemoved());
        return dto;
    }

    private Map<String, Object> toHistoryDto(MonitoringCheckHistoryRepository.HistoryEntry entry) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entry.id());
        dto.put("check_kind", entry.checkKind());
        dto.put("status", entry.status());
        dto.put("summary", entry.summary());
        dto.put("details_excerpt", entry.detailsExcerpt());
        dto.put("duration_ms", entry.durationMs());
        dto.put("created_at", entry.createdAt());
        return dto;
    }

    private Map<String, List<Map<String, Object>>> loadRelatedIncidents() {
        Map<String, List<Map<String, Object>>> incidentsByEntryKey = new LinkedHashMap<>();
        for (Map<String, Object> incident : incidentService.listIncidentSummariesForSignalType(
            CredentialRotationRegistryService.INCIDENT_SIGNAL_TYPE
        )) {
            String signalKey = normalizeKey(incident.get("signal_key"));
            if (signalKey.isEmpty()) {
                continue;
            }
            incidentsByEntryKey.computeIfAbsent(signalKey, ignored -> new java.util.ArrayList<>())
                .add(toIncidentSummaryDto(incident));
        }
        return incidentsByEntryKey;
    }

    private Map<String, Object> toIncidentAlertingOverview(Map<String, List<Map<String, Object>>> incidentsByEntryKey) {
        long totalIncidentCount = 0L;
        long activeIncidentCount = 0L;
        long activeEntryCount = 0L;
        long criticalOpenCount = 0L;
        for (List<Map<String, Object>> incidents : incidentsByEntryKey.values()) {
            totalIncidentCount += incidents.size();
            boolean hasActive = false;
            for (Map<String, Object> incident : incidents) {
                if (isActiveIncident(incident)) {
                    activeIncidentCount++;
                    hasActive = true;
                    if ("critical".equals(normalizeKey(incident.get("severity")))) {
                        criticalOpenCount++;
                    }
                }
            }
            if (hasActive) {
                activeEntryCount++;
            }
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("signal_type", CredentialRotationRegistryService.INCIDENT_SIGNAL_TYPE);
        dto.put("escalation_policy", "critical");
        dto.put("total_incident_count", totalIncidentCount);
        dto.put("active_incident_count", activeIncidentCount);
        dto.put("active_entry_count", activeEntryCount);
        dto.put("critical_open_count", criticalOpenCount);
        return dto;
    }

    private Map<String, Object> toIncidentSummaryDto(Map<String, Object> incident) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", incident.get("id"));
        dto.put("incident_key", incident.get("incident_key"));
        dto.put("title", incident.get("title"));
        dto.put("summary", incident.get("summary"));
        dto.put("status", incident.get("status"));
        dto.put("severity", incident.get("severity"));
        dto.put("source", incident.get("source"));
        dto.put("updated_at", incident.get("updated_at"));
        dto.put("created_at", incident.get("created_at"));
        dto.put("resolved_at", incident.get("resolved_at"));
        dto.put("route_count", incident.get("route_count"));
        dto.put("failed_route_count", incident.get("failed_route_count"));
        return dto;
    }

    private boolean isActiveIncident(Map<String, Object> incident) {
        String status = normalizeKey(incident.get("status"));
        return !"resolved".equals(status) && !"closed".equals(status);
    }

    private String normalizeKey(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private record UpdatePayload(String ownerName,
                                 String note,
                                 String expiresAt,
                                 String rotatedAt,
                                 Integer rotationIntervalDays) {
    }
}
