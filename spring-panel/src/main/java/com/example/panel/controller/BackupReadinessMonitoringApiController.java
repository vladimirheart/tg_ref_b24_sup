package com.example.panel.controller;

import com.example.panel.entity.BackupReadinessMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.service.BackupReadinessMonitoringService;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring/backups")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class BackupReadinessMonitoringApiController {

    private final BackupReadinessMonitoringService monitoringService;

    public BackupReadinessMonitoringApiController(BackupReadinessMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/monitors")
    public Map<String, Object> listMonitors() {
        List<BackupReadinessMonitor> monitors = monitoringService.findAll();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("items", monitors.stream().map(this::toDto).toList());
        payload.put("availability_overview", toAvailabilityOverview(monitoringService.buildAvailabilityOverview(monitors)));
        return payload;
    }

    @PostMapping("/monitors")
    public ResponseEntity<Map<String, Object>> createMonitor(@RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : new MonitorPayload(null, null, null, null, null, null);
            BackupReadinessMonitor item = monitoringService.createMonitor(toDraft(source));
            return ResponseEntity.ok(successItem(item));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @PatchMapping("/monitors/{monitorId}")
    public ResponseEntity<Map<String, Object>> updateMonitor(@PathVariable long monitorId,
                                                             @RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : new MonitorPayload(null, null, null, null, null, null);
            BackupReadinessMonitor item = monitoringService.updateMonitor(monitorId, toDraft(source));
            return ResponseEntity.ok(successItem(item));
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
        BackupReadinessMonitoringService.RefreshSummary summary = monitoringService.refreshAll();
        Map<String, Object> payload = successOnly();
        payload.put("summary", Map.of(
            "total", summary.total(),
            "checked", summary.checked()
        ));
        return payload;
    }

    @PostMapping("/monitors/{monitorId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshMonitor(@PathVariable long monitorId) {
        try {
            BackupReadinessMonitor item = monitoringService.refreshById(monitorId);
            return ResponseEntity.ok(successItem(item));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @PostMapping("/monitors/{monitorId}/restore-evidence")
    public ResponseEntity<Map<String, Object>> confirmRestoreEvidence(@PathVariable long monitorId,
                                                                      @RequestBody(required = false) RestoreEvidencePayload payload) {
        try {
            RestoreEvidencePayload source = payload != null ? payload : new RestoreEvidencePayload(null, null);
            BackupReadinessMonitor item = monitoringService.confirmRestoreEvidence(
                monitorId,
                new BackupReadinessMonitoringService.RestoreEvidenceDraft(parseVerifiedAt(source.verifiedAt()), source.note())
            );
            return ResponseEntity.ok(successItem(item));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @GetMapping("/monitors/{monitorId}/history")
    public ResponseEntity<Map<String, Object>> loadHistory(@PathVariable long monitorId) {
        try {
            List<Map<String, Object>> timeline = monitoringService.loadHistory(monitorId, 20).stream()
                .map(this::toHistoryDto)
                .toList();
            Map<String, Object> payload = successOnly();
            payload.put("items", timeline);
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    private BackupReadinessMonitoringService.MonitorDraft toDraft(MonitorPayload payload) {
        return new BackupReadinessMonitoringService.MonitorDraft(
            payload.monitorName(),
            payload.backupKind(),
            payload.pathPattern(),
            payload.enabled(),
            payload.freshnessThresholdHours(),
            payload.restoreThresholdDays()
        );
    }

    private OffsetDateTime parseVerifiedAt(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("verified_at должен быть в ISO-8601 формате");
        }
    }

    private Map<String, Object> successOnly() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        return payload;
    }

    private Map<String, Object> successItem(BackupReadinessMonitor item) {
        Map<String, Object> payload = successOnly();
        payload.put("item", toDto(item));
        return payload;
    }

    private Map<String, Object> errorResponse(String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", error);
        return payload;
    }

    private Map<String, Object> toDto(BackupReadinessMonitor item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("monitor_name", item.getMonitorName());
        dto.put("backup_kind", item.getBackupKind());
        dto.put("path_pattern", item.getPathPattern());
        dto.put("enabled", item.getEnabled());
        dto.put("freshness_threshold_hours", item.getFreshnessThresholdHours());
        dto.put("restore_threshold_days", item.getRestoreThresholdDays());
        dto.put("last_status", item.getLastStatus());
        dto.put("status_level", monitoringService.resolveSeverity(item));
        dto.put("availability", monitoringService.resolveAvailability(item));
        dto.put("last_summary", item.getLastSummary());
        dto.put("last_error_message", item.getLastErrorMessage());
        dto.put("last_backup_at", item.getLastBackupAt());
        dto.put("last_backup_size_bytes", item.getLastBackupSizeBytes());
        dto.put("last_backup_path", item.getLastBackupPath());
        dto.put("last_restore_verified_at", item.getLastRestoreVerifiedAt());
        dto.put("last_restore_note", item.getLastRestoreNote());
        dto.put("last_checked_at", item.getLastCheckedAt());
        dto.put("created_at", item.getCreatedAt());
        dto.put("updated_at", item.getUpdatedAt());
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

    private Map<String, Object> toAvailabilityOverview(BackupReadinessMonitoringService.AvailabilityOverview overview) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", overview.total());
        payload.put("up", overview.up());
        payload.put("down", overview.down());
        payload.put("unknown", overview.unknown());
        payload.put("disabled", overview.disabled());
        payload.put("availability_percent", overview.availabilityPercent());
        return payload;
    }

    private record MonitorPayload(String monitorName,
                                  String backupKind,
                                  String pathPattern,
                                  Boolean enabled,
                                  Integer freshnessThresholdHours,
                                  Integer restoreThresholdDays) {
    }

    private record RestoreEvidencePayload(String verifiedAt, String note) {
    }
}
