package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DialogWorkspacePayloadAssemblerService {

    public Map<String, Object> buildWorkspacePayload(Set<String> includeSections,
                                                     int resolvedLimit,
                                                     int safeCursor,
                                                     List<ChatMessageDto> pagedHistory,
                                                     Integer nextCursor,
                                                     boolean hasMore,
                                                     Map<String, Object> workspaceClient,
                                                     List<Map<String, Object>> clientHistory,
                                                     Map<String, Object> profileMatchCandidates,
                                                     List<Map<String, Object>> relatedEvents,
                                                     Map<String, Object> profileHealth,
                                                     List<Map<String, Object>> contextSources,
                                                     List<Map<String, Object>> attributePolicies,
                                                     List<Map<String, Object>> contextBlocks,
                                                     Map<String, Object> contextBlocksHealth,
                                                     Map<String, Object> contextContract,
                                                     Map<String, Object> workspacePermissions,
                                                     Map<String, Object> workspaceComposer,
                                                     int slaTargetMinutes,
                                                     int slaWarningMinutes,
                                                     int slaCriticalMinutes,
                                                     String slaDeadlineAt,
                                                     String slaState,
                                                     Long slaMinutesLeft,
                                                     Map<String, Object> workspaceSlaPolicy,
                                                     Map<String, Object> workspaceRollout,
                                                     Map<String, Object> workspaceNavigation,
                                                     Map<String, Object> workspaceParity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contract_version", "workspace.v1");
        payload.put("messages", includeSections.contains("messages")
                ? mapWithNullableValues(
                "items", pagedHistory,
                "next_cursor", nextCursor,
                "has_more", hasMore,
                "limit", resolvedLimit,
                "cursor", safeCursor
        )
                : mapWithNullableValues(
                "items", List.of(),
                "next_cursor", null,
                "has_more", false,
                "unavailable", true
        ));
        payload.put("context", includeSections.contains("context")
                ? Map.of(
                "client", workspaceClient,
                "history", clientHistory,
                "profile_match_candidates", profileMatchCandidates,
                "related_events", relatedEvents,
                "profile_health", profileHealth,
                "context_sources", contextSources,
                "attribute_policies", attributePolicies,
                "blocks", contextBlocks,
                "blocks_health", contextBlocksHealth,
                "contract", contextContract
        )
                : Map.of(
                "client", Map.of(),
                "history", List.of(),
                "related_events", List.of(),
                "context_sources", List.of(),
                "attribute_policies", List.of(),
                "blocks", List.of(),
                "contract", Map.of("enabled", false),
                "unavailable", true
        ));
        payload.put("permissions", workspacePermissions);
        payload.put("composer", workspaceComposer);
        payload.put("sla", includeSections.contains("sla")
                ? mapWithNullableValues(
                "target_minutes", slaTargetMinutes,
                "warning_minutes", slaWarningMinutes,
                "critical_minutes", slaCriticalMinutes,
                "deadline_at", slaDeadlineAt,
                "state", slaState,
                "minutes_left", slaMinutesLeft,
                "escalation_required", slaMinutesLeft != null && slaMinutesLeft <= slaCriticalMinutes,
                "policy", workspaceSlaPolicy
        )
                : mapWithNullableValues(
                "target_minutes", slaTargetMinutes,
                "warning_minutes", slaWarningMinutes,
                "critical_minutes", slaCriticalMinutes,
                "deadline_at", slaDeadlineAt,
                "state", "unknown",
                "minutes_left", null,
                "escalation_required", false,
                "policy", workspaceSlaPolicy,
                "unavailable", true
        ));
        payload.put("meta", mapWithNullableValues(
                "include", includeSections,
                "limit", resolvedLimit,
                "cursor", safeCursor,
                "rollout", workspaceRollout,
                "navigation", workspaceNavigation,
                "parity", workspaceParity
        ));
        payload.put("success", true);
        return payload;
    }

    public Map<String, Object> mapWithNullableValues(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("mapWithNullableValues expects even number of arguments");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }
}
