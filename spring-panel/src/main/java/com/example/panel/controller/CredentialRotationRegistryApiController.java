package com.example.panel.controller;

import com.example.panel.entity.CredentialRotationRegistryEntry;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.service.CredentialRotationRegistryService;
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

@RestController
@RequestMapping("/api/monitoring/credential-rotation")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class CredentialRotationRegistryApiController {

    private final CredentialRotationRegistryService registryService;

    public CredentialRotationRegistryApiController(CredentialRotationRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping("/entries")
    public Map<String, Object> listEntries() {
        CredentialRotationRegistryService.RegistrySnapshot snapshot = registryService.buildSnapshot();
        Map<String, Object> payload = successOnly();
        payload.put("generated_at", snapshot.generatedAt());
        payload.put("overview", toOverview(snapshot.overview()));
        payload.put("items", snapshot.items().stream().map(this::toDto).toList());
        return payload;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        CredentialRotationRegistryService.RegistrySnapshot snapshot = registryService.refreshAll();
        Map<String, Object> payload = successOnly();
        payload.put("generated_at", snapshot.generatedAt());
        payload.put("overview", toOverview(snapshot.overview()));
        payload.put("items", snapshot.items().stream().map(this::toDto).toList());
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
            response.put("item", toDto(item));
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

    private Map<String, Object> toDto(CredentialRotationRegistryEntry item) {
        Map<String, Object> dto = new LinkedHashMap<>();
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

    private record UpdatePayload(String ownerName,
                                 String note,
                                 String expiresAt,
                                 String rotatedAt,
                                 Integer rotationIntervalDays) {
    }
}
