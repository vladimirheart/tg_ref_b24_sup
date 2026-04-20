package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DialogWorkspaceService {

    private final DialogService dialogService;
    private final SharedConfigService sharedConfigService;
    private final DialogAuthorizationService dialogAuthorizationService;
    private final SlaEscalationWebhookNotifier slaEscalationWebhookNotifier;
    private final DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService;
    private final DialogWorkspaceParityService dialogWorkspaceParityService;
    private final DialogWorkspaceNavigationService dialogWorkspaceNavigationService;
    private final DialogWorkspaceRolloutService dialogWorkspaceRolloutService;
    private final DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService;
    private final DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService;
    private final DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService;
    private final DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
    private static final int DEFAULT_SLA_WARNING_MINUTES = 4 * 60;
    private static final int DEFAULT_WORKSPACE_LIMIT = 50;
    private static final int MAX_WORKSPACE_LIMIT = 200;
    private static final Set<String> WORKSPACE_INCLUDE_ALLOWED = Set.of("messages", "context", "sla", "permissions");
    private static final Map<String, String> WORKSPACE_TELEMETRY_EVENT_GROUPS = Map.ofEntries(
            Map.entry("workspace_open_ms", "performance"),
            Map.entry("workspace_render_error", "stability"),
            Map.entry("workspace_fallback_to_legacy", "stability"),
            Map.entry("workspace_guardrail_breach", "stability"),
            Map.entry("workspace_abandon", "engagement"),
            Map.entry("workspace_experiment_exposure", "experiment"),
            Map.entry("workspace_draft_saved", "workspace"),
            Map.entry("workspace_draft_restored", "workspace"),
            Map.entry("workspace_reply_target_selected", "workspace"),
            Map.entry("workspace_reply_target_cleared", "workspace"),
            Map.entry("workspace_media_sent", "workspace"),
            Map.entry("workspace_context_profile_gap", "workspace"),
            Map.entry("workspace_context_source_gap", "workspace"),
            Map.entry("workspace_context_attribute_policy_gap", "workspace"),
            Map.entry("workspace_context_block_gap", "workspace"),
            Map.entry("workspace_context_contract_gap", "workspace"),
            Map.entry("workspace_context_sources_expanded", "workspace"),
            Map.entry("workspace_context_attribute_policy_expanded", "workspace"),
            Map.entry("workspace_context_extra_attributes_expanded", "workspace"),
            Map.entry("workspace_sla_policy_gap", "workspace"),
            Map.entry("workspace_parity_gap", "workspace"),
            Map.entry("workspace_inline_navigation", "workspace"),
            Map.entry("workspace_open_legacy_manual", "workspace"),
            Map.entry("workspace_open_legacy_blocked", "workspace"),
            Map.entry("workspace_rollout_packet_viewed", "experiment"),
            Map.entry("kpi_frt_recorded", "kpi"),
            Map.entry("kpi_ttr_recorded", "kpi"),
            Map.entry("kpi_sla_breach_recorded", "kpi"),
            Map.entry("kpi_dialogs_per_shift_recorded", "kpi"),
            Map.entry("kpi_csat_recorded", "kpi"),
            Map.entry("macro_preview", "macro"),
            Map.entry("macro_apply", "macro"),
            Map.entry("triage_view_switch", "triage"),
            Map.entry("triage_preferences_saved", "triage"),
            Map.entry("triage_quick_assign", "triage"),
            Map.entry("triage_quick_snooze", "triage"),
            Map.entry("triage_quick_close", "triage"),
            Map.entry("triage_bulk_action", "triage")
    );
    public DialogWorkspaceService(DialogService dialogService,
                                  SharedConfigService sharedConfigService,
                                  DialogAuthorizationService dialogAuthorizationService,
                                  SlaEscalationWebhookNotifier slaEscalationWebhookNotifier,
                                  DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService,
                                  DialogWorkspaceParityService dialogWorkspaceParityService,
                                  DialogWorkspaceNavigationService dialogWorkspaceNavigationService,
                                  DialogWorkspaceRolloutService dialogWorkspaceRolloutService,
                                  DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService,
                                  DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService,
                                  DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService,
                                  DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService) {
        this.dialogService = dialogService;
        this.sharedConfigService = sharedConfigService;
        this.dialogAuthorizationService = dialogAuthorizationService;
        this.slaEscalationWebhookNotifier = slaEscalationWebhookNotifier;
        this.dialogWorkspaceExternalProfileService = dialogWorkspaceExternalProfileService;
        this.dialogWorkspaceParityService = dialogWorkspaceParityService;
        this.dialogWorkspaceNavigationService = dialogWorkspaceNavigationService;
        this.dialogWorkspaceRolloutService = dialogWorkspaceRolloutService;
        this.dialogWorkspaceClientProfileService = dialogWorkspaceClientProfileService;
        this.dialogWorkspaceContextBlockService = dialogWorkspaceContextBlockService;
        this.dialogWorkspaceClientPayloadService = dialogWorkspaceClientPayloadService;
        this.dialogWorkspaceContextSourceService = dialogWorkspaceContextSourceService;
    }

    private Map<String, Object> resolveWorkspaceExternalProfileEnrichment(Map<String, Object> settings,
                                                                           DialogListItem summary,
                                                                           String ticketId,
                                                                           Map<String, Object> profileEnrichment) {
        Object rawDialogConfig = settings != null ? settings.get("dialog_config") : null;
        if (!(rawDialogConfig instanceof Map<?, ?> dialogConfig) || summary == null) {
            return Map.of();
        }
        String externalUrlTemplate = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_url")));
        if (!StringUtils.hasText(externalUrlTemplate)) {
            return Map.of();
        }
        Map<String, String> placeholders = dialogWorkspaceClientPayloadService.buildExternalLinkPlaceholders(summary, ticketId, profileEnrichment);
        String resolvedUrl = externalUrlTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        resolvedUrl = trimToNull(resolvedUrl);
        if (!StringUtils.hasText(resolvedUrl)) {
            return Map.of();
        }
        return dialogWorkspaceExternalProfileService.resolveProfile(dialogConfig, resolvedUrl);
    }

    private String humanizeMacroVariableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return "Переменная";
        }
        return Arrays.stream(key.trim().toLowerCase().split("_"))
                .filter(StringUtils::hasText)
                .map(token -> Character.toUpperCase(token.charAt(0)) + token.substring(1))
                .collect(Collectors.joining(" "));
    }

    public ResponseEntity<?> workspace(String ticketId,
                                       Long channelId,
                                       String include,
                                       Integer limit,
                                       String cursor,
                                       Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.markDialogAsRead(ticketId, operator);
        Optional<DialogDetails> details = dialogService.loadDialogDetails(ticketId, channelId, operator);
        if (details.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }

        DialogDetails dialogDetails = details.get();
        DialogListItem summary = dialogDetails.summary();
        Set<String> includeSections = resolveWorkspaceInclude(include);
        int resolvedLimit = resolveWorkspaceLimit(limit);
        int resolvedCursor = resolveWorkspaceCursor(cursor);
        List<ChatMessageDto> history = dialogService.loadHistory(ticketId, channelId);

        int safeCursor = Math.min(Math.max(resolvedCursor, 0), history.size());
        int endExclusive = Math.min(safeCursor + resolvedLimit, history.size());
        List<ChatMessageDto> pagedHistory = history.subList(safeCursor, endExclusive);
        boolean hasMore = endExclusive < history.size();
        Integer nextCursor = hasMore ? endExclusive : null;

        int slaTargetMinutes = resolveDialogConfigMinutes("sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int slaWarningMinutes = Math.min(
                resolveDialogConfigMinutes("sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES),
                slaTargetMinutes
        );
        int slaCriticalMinutes = Math.min(resolveDialogConfigMinutes("sla_critical_minutes", 30), slaTargetMinutes);
        String slaState = resolveSlaState(summary.createdAt(), slaTargetMinutes, slaWarningMinutes, summary.statusKey());
        Long slaMinutesLeft = resolveSlaMinutesLeft(summary.createdAt(), slaTargetMinutes, summary.statusKey(), System.currentTimeMillis());
        Map<String, Object> settings = sharedConfigService.loadSettings();
        int workspaceHistoryLimit = resolveDialogConfigRangeMinutes(settings, "workspace_context_history_limit", 5, 1, 20);
        int workspaceRelatedEventsLimit = resolveDialogConfigRangeMinutes(settings, "workspace_context_related_events_limit", 5, 1, 20);
        List<Map<String, Object>> clientHistory = dialogService.loadClientDialogHistory(summary.userId(), ticketId, workspaceHistoryLimit);
        List<Map<String, Object>> relatedEvents = dialogService.loadRelatedEvents(ticketId, workspaceRelatedEventsLimit);
        Map<String, Object> profileEnrichment = dialogService.loadClientProfileEnrichment(summary.userId());
        Map<String, Object> externalProfileEnrichment = resolveWorkspaceExternalProfileEnrichment(settings, summary, ticketId, profileEnrichment);
        if (!externalProfileEnrichment.isEmpty()) {
            Map<String, Object> mergedEnrichment = new LinkedHashMap<>(profileEnrichment);
            externalProfileEnrichment.forEach(mergedEnrichment::putIfAbsent);
            profileEnrichment = mergedEnrichment;
        }
        Set<String> hiddenProfileAttributes = dialogWorkspaceClientPayloadService.resolveHiddenClientAttributes(settings);
        Map<String, Object> filteredProfileEnrichment = dialogWorkspaceClientPayloadService.filterProfileEnrichment(profileEnrichment, hiddenProfileAttributes);
        Map<String, Object> workspaceClient = new LinkedHashMap<>();
        workspaceClient.put("id", summary.userId());
        workspaceClient.put("name", summary.displayClientName());
        workspaceClient.put("language", "ru");
        workspaceClient.put("username", summary.username());
        workspaceClient.put("status", summary.clientStatusLabel());
        workspaceClient.put("channel", summary.channelLabel());
        workspaceClient.put("business", summary.businessLabel());
        workspaceClient.put("location", summary.location());
        workspaceClient.put("responsible", summary.responsible());
        workspaceClient.put("unread_count", summary.unreadCount());
        workspaceClient.put("rating", summary.rating());
        workspaceClient.put("last_message_at", summary.lastMessageTimestamp());
        workspaceClient.put("segments", dialogWorkspaceClientProfileService.buildClientSegments(summary, filteredProfileEnrichment, settings));
        Map<String, Object> externalLinks = dialogWorkspaceClientPayloadService.resolveExternalProfileLinks(settings, summary, ticketId, filteredProfileEnrichment);
        if (!externalLinks.isEmpty()) {
            workspaceClient.put("external_links", externalLinks);
        }
        Map<String, String> attributeLabels = dialogWorkspaceClientPayloadService.resolveClientAttributeLabels(settings);
        List<String> attributeOrder = dialogWorkspaceClientPayloadService.resolveClientAttributeOrder(settings);
        if (!attributeLabels.isEmpty()) {
            workspaceClient.put("attribute_labels", attributeLabels);
        }
        if (!attributeOrder.isEmpty()) {
            workspaceClient.put("attribute_order", attributeOrder);
        }
        if (!filteredProfileEnrichment.isEmpty()) {
            workspaceClient.putAll(filteredProfileEnrichment);
        }
        Map<String, String> profileMatchIncomingValues = new LinkedHashMap<>();
        putProfileMatchField(profileMatchIncomingValues, "business", summary.businessLabel());
        putProfileMatchField(profileMatchIncomingValues, "location", summary.location());
        putProfileMatchField(profileMatchIncomingValues, "city", workspaceClient.get("city"));
        putProfileMatchField(profileMatchIncomingValues, "country", workspaceClient.get("country"));
        Map<String, Object> profileMatchCandidates = dialogService.loadDialogProfileMatchCandidates(profileMatchIncomingValues, 5);
        if (!profileMatchCandidates.isEmpty()) {
            workspaceClient.put("profile_match_candidates", profileMatchCandidates);
        }
        Map<String, Object> profileHealth = dialogWorkspaceClientProfileService.buildProfileHealth(settings, workspaceClient, attributeLabels);
        if (!profileHealth.isEmpty()) {
            workspaceClient.put("profile_health", profileHealth);
        }
        List<Map<String, Object>> contextSources = dialogWorkspaceContextSourceService.buildContextSources(
                settings,
                workspaceClient,
                filteredProfileEnrichment,
                externalProfileEnrichment,
                externalLinks
        );
        if (!contextSources.isEmpty()) {
            workspaceClient.put("context_sources", contextSources);
        }
        List<Map<String, Object>> attributePolicies = dialogWorkspaceContextSourceService.buildContextAttributePolicies(
                workspaceClient,
                profileHealth,
                contextSources
        );
        if (!attributePolicies.isEmpty()) {
            workspaceClient.put("attribute_policies", attributePolicies);
        }
        List<Map<String, Object>> contextBlocks = dialogWorkspaceContextBlockService.buildContextBlocks(
                settings,
                profileHealth,
                contextSources,
                clientHistory,
                relatedEvents,
                slaState,
                externalLinks
        );
        Map<String, Object> contextBlocksHealth = dialogWorkspaceContextBlockService.buildBlocksHealth(contextBlocks);
        Map<String, Object> contextContract = buildWorkspaceContextContract(
                settings,
                summary,
                workspaceClient,
                contextSources,
                contextBlocks
        );
        if (!contextContract.isEmpty()) {
            workspaceClient.put("context_contract", contextContract);
        }

        Map<String, Object> workspaceRollout = dialogWorkspaceRolloutService.resolveRolloutMeta(settings);
        Map<String, Object> workspaceNavigation = dialogWorkspaceNavigationService.buildNavigationMeta(settings, operator, ticketId);
        Map<String, Object> workspaceSlaPolicyRaw = slaEscalationWebhookNotifier.buildRoutingPolicySnapshot(summary, settings);
        Map<String, Object> workspaceSlaPolicy = workspaceSlaPolicyRaw != null ? workspaceSlaPolicyRaw : Map.of();
        Map<String, Object> workspacePermissions = includeSections.contains("permissions")
                ? dialogAuthorizationService.resolveWorkspacePermissions(authentication)
                : Map.of(
                "can_reply", false,
                "can_assign", false,
                "can_close", false,
                "can_snooze", false,
                "can_bulk", false,
                "unavailable", true
        );
        Map<String, Object> workspaceComposer = dialogWorkspaceParityService.buildComposerMeta(summary, history, workspacePermissions);
        Map<String, Object> workspaceParity = dialogWorkspaceParityService.buildParityMeta(
                includeSections,
                workspaceClient,
                clientHistory,
                relatedEvents,
                profileHealth,
                contextBlocksHealth,
                workspacePermissions,
                workspaceComposer,
                slaState,
                summary,
                workspaceRollout
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contract_version", "workspace.v1");
        payload.put("conversation", summary);
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
                "deadline_at", computeDeadlineAt(summary.createdAt(), slaTargetMinutes),
                "state", slaState,
                "minutes_left", slaMinutesLeft,
                "escalation_required", slaMinutesLeft != null && slaMinutesLeft <= slaCriticalMinutes,
                "policy", workspaceSlaPolicy
        )
                : mapWithNullableValues(
                "target_minutes", slaTargetMinutes,
                "warning_minutes", slaWarningMinutes,
                "critical_minutes", slaCriticalMinutes,
                "deadline_at", computeDeadlineAt(summary.createdAt(), slaTargetMinutes),
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
        return ResponseEntity.ok(payload);
    }

    private void putProfileMatchField(Map<String, String> target, String key, Object value) {
        if (target == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        String normalized = String.valueOf(value).trim();
        if (!StringUtils.hasText(normalized) || "—".equals(normalized) || "-".equals(normalized)) {
            return;
        }
        target.put(key, normalized);
    }

    private Map<String, Object> mapWithNullableValues(Object... keyValues) {
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

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = value != null ? String.valueOf(value).trim().toLowerCase() : "";
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return switch (normalized) {
            case "1", "true", "yes", "on", "ok", "ready" -> true;
            default -> false;
        };
    }

    private List<String> safeStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(item -> trimToNull(String.valueOf(item)))
                .filter(StringUtils::hasText)
                .toList();
    }
    private Map<String, List<String>> safeStringListMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = trimToNull(String.valueOf(entry.getKey()));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<String> items = safeStringList(entry.getValue());
            if (!items.isEmpty()) {
                result.put(key.toLowerCase(Locale.ROOT), items);
            }
        }
        return result;
    }

    private boolean resolveBooleanDialogConfig(Map<?, ?> dialogConfig, String key, boolean fallbackValue) {
        if (dialogConfig == null || key == null) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            return fallbackValue;
        }
        return switch (normalized) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> fallbackValue;
        };
    }

    private int resolveIntegerDialogConfig(Map<?, ?> dialogConfig,
                                           String key,
                                           int fallbackValue,
                                           int minValue,
                                           int maxValue) {
        if (dialogConfig == null || key == null) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                return fallbackValue;
            }
        }
        if (parsed < minValue || parsed > maxValue) {
            return fallbackValue;
        }
        return parsed;
    }

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fallback to legacy datetime-local without explicit offset
        }
        try {
            return LocalDateTime.parse(rawValue).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalizeUtcTimestamp(Object rawValue) {
        String normalized = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        OffsetDateTime parsed = parseUtcTimestamp(normalized);
        return parsed != null ? parsed.toString() : null;
    }

    private int resolveDialogConfigRangeMinutes(Map<String, Object> settings,
                                                String key,
                                                int fallbackValue,
                                                int min,
                                                int max) {
        if (settings == null || settings.isEmpty()) {
            return fallbackValue;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < min || parsed > max) {
                return fallbackValue;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return fallbackValue;
        }
    }

    private Set<String> resolveWorkspaceInclude(String include) {
        if (include == null || include.isBlank()) {
            return WORKSPACE_INCLUDE_ALLOWED;
        }
        Set<String> result = new HashSet<>();
        Arrays.stream(include.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(WORKSPACE_INCLUDE_ALLOWED::contains)
                .forEach(result::add);
        return result.isEmpty() ? WORKSPACE_INCLUDE_ALLOWED : Collections.unmodifiableSet(result);
    }

    private int resolveWorkspaceLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_WORKSPACE_LIMIT;
        }
        return Math.min(limit, MAX_WORKSPACE_LIMIT);
    }

    private int resolveWorkspaceCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String stringifyMacroVariableValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence sequence) {
            String text = sequence.toString().trim();
            return StringUtils.hasText(text) ? text : null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::stringifyMacroVariableValue)
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(null);
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> {
                        String mapKey = entry.getKey() != null ? String.valueOf(entry.getKey()).trim() : "";
                        String mapValue = stringifyMacroVariableValue(entry.getValue());
                        if (!StringUtils.hasText(mapKey) || !StringUtils.hasText(mapValue)) {
                            return null;
                        }
                        return mapKey + ": " + mapValue;
                    })
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse(null);
        }
        String fallback = String.valueOf(value).trim();
        return StringUtils.hasText(fallback) ? fallback : null;
    }


    private Map<String, Object> buildP1OperationalControl(Map<String, Object> payload) {
        Map<String, Object> totals = payload.get("totals") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> previousTotals = payload.get("previous_totals") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> rolloutPacket = payload.get("rollout_packet") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> legacyInventory = rolloutPacket.get("legacy_only_inventory") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> contextContract = rolloutPacket.get("context_contract") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();

        long currentContextRate = asLong(totals.get("context_secondary_details_open_rate_pct"));
        long previousContextRate = asLong(previousTotals.get("context_secondary_details_open_rate_pct"));
        long currentExtraRate = asLong(totals.get("context_extra_attributes_open_rate_pct"));
        long previousExtraRate = asLong(previousTotals.get("context_extra_attributes_open_rate_pct"));
        long contextDeltaPct = currentContextRate - previousContextRate;
        long extraDeltaPct = currentExtraRate - previousExtraRate;

        String legacyStatus = Boolean.TRUE.equals(legacyInventory.get("review_queue_management_review_required"))
                ? "management_review"
                : Boolean.TRUE.equals(legacyInventory.get("review_queue_followup_required"))
                ? "followup"
                : "controlled";
        boolean contextManagementReviewRequired = Boolean.TRUE.equals(contextContract.get("secondary_noise_management_review_required"))
                || Boolean.TRUE.equals(totals.get("context_secondary_details_management_review_required"));
        boolean contextFollowupRequired = Boolean.TRUE.equals(contextContract.get("secondary_noise_followup_required"))
                || Boolean.TRUE.equals(totals.get("context_secondary_details_followup_required"));
        String contextStatus = contextManagementReviewRequired
                ? "management_review"
                : contextFollowupRequired
                ? "followup"
                : "controlled";
        String status = ("management_review".equals(legacyStatus) || "management_review".equals(contextStatus))
                ? "management_review"
                : ("followup".equals(legacyStatus) || "followup".equals(contextStatus))
                ? "followup"
                : "controlled";
        String nextActionSummary = firstNonBlank(
                String.valueOf(legacyInventory.getOrDefault("review_queue_next_action_summary", "")),
                String.valueOf(contextContract.getOrDefault("secondary_noise_compaction_summary", "")),
                "P1 operational control не требует дополнительного follow-up.");
        Map<String, Object> control = new LinkedHashMap<>();
        control.put("status", status);
        control.put("summary", status.equals("controlled")
                ? "P1 operational control удерживается: legacy queue и context noise под наблюдением."
                : "P1 operational control требует follow-up для legacy queue или context noise.");
        control.put("legacy_status", legacyStatus);
        control.put("legacy_summary", firstNonBlank(
                String.valueOf(legacyInventory.getOrDefault("review_queue_management_review_summary", "")),
                String.valueOf(legacyInventory.getOrDefault("review_queue_summary", ""))));
        control.put("legacy_next_action_summary", String.valueOf(legacyInventory.getOrDefault("review_queue_next_action_summary", "")));
        control.put("legacy_management_review_count", asLong(legacyInventory.get("review_queue_escalated_count")));
        control.put("legacy_consolidation_count", asLong(legacyInventory.get("review_queue_consolidation_count")));
        control.put("context_status", contextStatus);
        control.put("context_summary", firstNonBlank(
                String.valueOf(contextContract.getOrDefault("secondary_noise_compaction_summary", "")),
                String.valueOf(totals.getOrDefault("context_secondary_details_compaction_summary", "")),
                String.valueOf(contextContract.getOrDefault("secondary_noise_summary", "")),
                String.valueOf(totals.getOrDefault("context_secondary_details_summary", ""))));
        control.put("context_noise_trend_status", contextDeltaPct >= 10L ? "rising" : contextDeltaPct <= -10L ? "improving" : "stable");
        control.put("context_noise_trend_delta_pct", contextDeltaPct);
        control.put("context_extra_attributes_delta_pct", extraDeltaPct);
        control.put("context_extra_attributes_compaction_candidate",
                Boolean.TRUE.equals(contextContract.get("extra_attributes_compaction_candidate"))
                        || Boolean.TRUE.equals(totals.get("context_extra_attributes_compaction_candidate")));
        control.put("next_action_summary", nextActionSummary);
        control.put("management_review_required", "management_review".equals(status));
        return control;
    }

    private Map<String, Object> buildSlaReviewPathControl(Map<String, Object> payload,
                                                          Map<String, Object> slaPolicyAudit) {
        Map<String, Object> totals = payload.get("totals") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> safeSlaAudit = slaPolicyAudit != null ? slaPolicyAudit : Map.of();
        boolean cheapPathConfirmed = Boolean.TRUE.equals(safeSlaAudit.get("cheap_review_path_confirmed"));
        boolean minimumPathReady = Boolean.TRUE.equals(safeSlaAudit.get("minimum_required_review_path_ready"));
        boolean churnFollowupRequired = Boolean.TRUE.equals(totals.get("workspace_sla_policy_churn_followup_required"))
                || Boolean.TRUE.equals(safeSlaAudit.get("weekly_review_followup_required"));
        boolean hasSlaSignals = !safeSlaAudit.isEmpty()
                || totals.containsKey("workspace_sla_policy_churn_followup_required")
                || totals.containsKey("workspace_sla_policy_churn_level");
        String leadTimeStatus = String.valueOf(safeSlaAudit.getOrDefault("decision_lead_time_status", "unknown"));
        String status = !hasSlaSignals
                ? "controlled"
                : cheapPathConfirmed
                ? "controlled"
                : minimumPathReady && !churnFollowupRequired
                ? "monitor"
                : churnFollowupRequired
                ? "followup"
                : "attention";
        String nextActionSummary = cheapPathConfirmed
                ? "Удерживайте только minimum required SLA review path и не возвращайте advisory checkpoints в типовые policy changes."
                : !hasSlaSignals
                ? "Дополнительный SLA follow-up не требуется."
                : Boolean.TRUE.equals(safeSlaAudit.get("advisory_path_reduction_candidate"))
                ? "Сократите advisory checkpoints до minimum required path и перепроверьте decision cadence."
                : minimumPathReady
                ? "Проверьте decision cadence: minimum required path уже готов, но lead time или churn ещё шумят."
                : "Закройте minimum required SLA review path перед следующими policy changes.";

        Map<String, Object> control = new LinkedHashMap<>();
        control.put("status", status);
        control.put("summary", !hasSlaSignals
                ? "SLA review path не требует отдельного follow-up."
                : cheapPathConfirmed
                ? "Минимальный дешёвый SLA review path зафиксирован и удерживается под операционным контролем."
                : "SLA review path требует follow-up, чтобы остаться дешёвым и обязательным.");
        control.put("minimum_required_review_path_ready", minimumPathReady);
        control.put("minimum_required_review_path_summary", String.valueOf(safeSlaAudit.getOrDefault("minimum_required_review_path_summary", "")));
        control.put("cheap_review_path_confirmed", cheapPathConfirmed);
        control.put("decision_lead_time_status", leadTimeStatus);
        control.put("decision_lead_time_summary", String.valueOf(safeSlaAudit.getOrDefault("decision_lead_time_summary", "")));
        control.put("policy_churn_level", String.valueOf(totals.getOrDefault(
                "workspace_sla_policy_churn_level",
                safeSlaAudit.getOrDefault("policy_churn_risk_level", "controlled"))));
        control.put("next_action_summary", nextActionSummary);
        control.put("management_review_required", "followup".equals(status) && "high".equals(String.valueOf(
                safeSlaAudit.getOrDefault("policy_churn_risk_level", totals.getOrDefault("workspace_sla_policy_churn_level", "")))));
        return control;
    }

    private Map<String, Object> buildP2GovernanceControl(Map<String, Object> payload,
                                                         Map<String, Object> slaPolicyAudit,
                                                         Map<String, Object> macroGovernanceAudit) {
        Map<String, Object> totals = payload.get("totals") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> previousTotals = payload.get("previous_totals") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> safeSlaAudit = slaPolicyAudit != null ? slaPolicyAudit : Map.of();
        Map<String, Object> safeMacroAudit = macroGovernanceAudit != null ? macroGovernanceAudit : Map.of();
        boolean hasSlaAuditSignals = !safeSlaAudit.isEmpty();
        boolean hasMacroAuditSignals = !safeMacroAudit.isEmpty();

        long currentSlaChurnPct = asLong(totals.get("workspace_sla_policy_churn_ratio_pct"));
        long previousSlaChurnPct = asLong(previousTotals.get("workspace_sla_policy_churn_ratio_pct"));
        long slaChurnDeltaPct = currentSlaChurnPct - previousSlaChurnPct;
        String slaTrendStatus = slaChurnDeltaPct >= 25L
                ? "rising"
                : slaChurnDeltaPct <= -25L ? "improving" : "stable";
        long slaClosureRatePct = hasSlaAuditSignals ? asLong(safeSlaAudit.get("required_checkpoint_closure_rate_pct")) : 100L;
        long slaFreshnessRatePct = hasSlaAuditSignals ? asLong(safeSlaAudit.get("freshness_closure_rate_pct")) : 100L;
        String slaClosureStatus = slaClosureRatePct >= 100L ? "controlled" : "followup";
        String slaFreshnessStatus = slaFreshnessRatePct >= 100L ? "controlled" : "followup";

        boolean slaManagementReviewRequired = "high".equals(String.valueOf(safeSlaAudit.getOrDefault("cheap_path_drift_risk_level", "")))
                || "high".equals(String.valueOf(totals.getOrDefault("workspace_sla_policy_churn_level", "")));
        boolean slaFollowupRequired = Boolean.TRUE.equals(safeSlaAudit.get("weekly_review_followup_required"))
                || Boolean.TRUE.equals(totals.get("workspace_sla_policy_churn_followup_required"));
        String slaStatus = slaManagementReviewRequired
                ? "management_review"
                : slaFollowupRequired
                ? "followup"
                : "controlled";

        boolean macroManagementReviewRequired = !Boolean.TRUE.equals(safeMacroAudit.get("minimum_required_path_controlled"))
                && Boolean.TRUE.equals(safeMacroAudit.get("weekly_review_followup_required"));
        boolean macroFollowupRequired = Boolean.TRUE.equals(safeMacroAudit.get("advisory_followup_required"))
                || Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate"))
                || Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"));
        String macroStatus = macroManagementReviewRequired
                ? "management_review"
                : macroFollowupRequired
                ? "followup"
                : "controlled";
        long macroClosureRatePct = hasMacroAuditSignals ? asLong(safeMacroAudit.get("required_checkpoint_closure_rate_pct")) : 100L;
        long macroFreshnessRatePct = hasMacroAuditSignals ? asLong(safeMacroAudit.get("freshness_closure_rate_pct")) : 100L;
        String macroClosureStatus = macroClosureRatePct >= 100L ? "controlled" : "followup";
        String macroFreshnessStatus = macroFreshnessRatePct >= 100L ? "controlled" : "followup";
        boolean macroActionableSignalDominant = asLong(safeMacroAudit.get("actionable_advisory_share_pct"))
                >= asLong(safeMacroAudit.get("low_signal_advisory_share_pct"))
                && !Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"));
        String governanceClosureHealth = ("followup".equals(slaClosureStatus) || "followup".equals(slaFreshnessStatus)
                || "followup".equals(macroClosureStatus) || "followup".equals(macroFreshnessStatus))
                ? "followup"
                : "controlled";
        String macroNoiseHealth = Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"))
                ? "followup"
                : macroActionableSignalDominant ? "controlled" : "monitor";

        String status = ("management_review".equals(slaStatus) || "management_review".equals(macroStatus))
                ? "management_review"
                : ("followup".equals(slaStatus) || "followup".equals(macroStatus))
                ? "followup"
                : "controlled";

        String nextActionSummary = firstNonBlank(
                Boolean.TRUE.equals(safeSlaAudit.get("advisory_path_reduction_candidate"))
                        ? "Сократите SLA advisory checkpoints для типовых policy changes и удерживайте cheap path."
                        : "",
                Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"))
                        ? "Оставьте low-signal macro red-list аналитическим и не превращайте его в ручной backlog."
                        : "",
                String.valueOf(safeSlaAudit.getOrDefault("weekly_review_summary", "")),
                String.valueOf(safeMacroAudit.getOrDefault("weekly_review_summary", "")),
                "P2 governance control не требует дополнительного follow-up.");

        Map<String, Object> control = new LinkedHashMap<>();
        control.put("status", status);
        control.put("summary", "controlled".equals(status)
                ? "P2 governance control удерживается: SLA churn и macro noise остаются в рабочем диапазоне."
                : "P2 governance control требует follow-up для SLA churn-control или macro noise.");
        control.put("sla_status", slaStatus);
        control.put("sla_summary", firstNonBlank(
                String.valueOf(safeSlaAudit.getOrDefault("minimum_required_review_path_summary", "")),
                String.valueOf(safeSlaAudit.getOrDefault("weekly_review_summary", ""))));
        control.put("sla_churn_trend_status", slaTrendStatus);
        control.put("sla_churn_delta_pct", slaChurnDeltaPct);
        control.put("sla_cheap_path_drift_risk_level", String.valueOf(safeSlaAudit.getOrDefault("cheap_path_drift_risk_level", "controlled")));
        control.put("sla_typical_policy_change_ready", Boolean.TRUE.equals(safeSlaAudit.get("typical_policy_change_ready")));
        control.put("sla_closure_rate_pct", slaClosureRatePct);
        control.put("sla_closure_status", slaClosureStatus);
        control.put("sla_freshness_rate_pct", slaFreshnessRatePct);
        control.put("sla_freshness_status", slaFreshnessStatus);
        control.put("macro_status", macroStatus);
        control.put("macro_summary", firstNonBlank(
                String.valueOf(safeMacroAudit.getOrDefault("low_signal_backlog_summary", "")),
                String.valueOf(safeMacroAudit.getOrDefault("weekly_review_summary", ""))));
        control.put("macro_low_signal_backlog_dominant", Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant")));
        control.put("macro_actionable_advisory_share_pct", asLong(safeMacroAudit.get("actionable_advisory_share_pct")));
        control.put("macro_low_signal_advisory_share_pct", asLong(safeMacroAudit.get("low_signal_advisory_share_pct")));
        control.put("macro_actionable_signal_dominant", macroActionableSignalDominant);
        control.put("macro_closure_rate_pct", macroClosureRatePct);
        control.put("macro_closure_status", macroClosureStatus);
        control.put("macro_freshness_rate_pct", macroFreshnessRatePct);
        control.put("macro_freshness_status", macroFreshnessStatus);
        control.put("governance_closure_health", governanceClosureHealth);
        control.put("macro_noise_health", macroNoiseHealth);
        control.put("next_action_summary", nextActionSummary);
        control.put("management_review_required", "management_review".equals(status));
        return control;
    }

    private Map<String, Object> buildWorkspaceWeeklyReviewFocus(Map<String, Object> payload,
                                                                Map<String, Object> slaPolicyAudit,
                                                                Map<String, Object> macroGovernanceAudit) {
        Map<String, Object> totals = payload.get("totals") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> previousTotals = payload.get("previous_totals") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> rolloutPacket = payload.get("rollout_packet") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> legacyInventory = rolloutPacket.get("legacy_only_inventory") instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> safeSlaAudit = slaPolicyAudit != null ? slaPolicyAudit : Map.of();
        Map<String, Object> safeMacroAudit = macroGovernanceAudit != null ? macroGovernanceAudit : Map.of();
        long previousContextSecondaryRatePct = asLong(previousTotals.get("context_secondary_details_open_rate_pct"));
        long currentContextSecondaryRatePct = asLong(totals.get("context_secondary_details_open_rate_pct"));
        long contextSecondaryDeltaPct = currentContextSecondaryRatePct - previousContextSecondaryRatePct;
        long previousContextExtraRatePct = asLong(previousTotals.get("context_extra_attributes_open_rate_pct"));
        long currentContextExtraRatePct = asLong(totals.get("context_extra_attributes_open_rate_pct"));
        long contextExtraDeltaPct = currentContextExtraRatePct - previousContextExtraRatePct;
        String contextTrendStatus = contextSecondaryDeltaPct >= 10L
                ? "rising"
                : contextSecondaryDeltaPct <= -10L ? "improving" : "stable";
        boolean contextExtraAttributesCompactionCandidate = Boolean.TRUE.equals(totals.get("context_extra_attributes_compaction_candidate"));
        boolean legacyManagementReviewRequired = Boolean.TRUE.equals(legacyInventory.get("review_queue_escalation_required"))
                || asLong(legacyInventory.get("review_queue_repeat_cycles")) >= 3L
                || asLong(legacyInventory.get("review_queue_oldest_overdue_days")) >= 7L;
        boolean contextManagementReviewRequired = Boolean.TRUE.equals(totals.get("context_secondary_details_management_review_required"))
                || ("heavy".equals(String.valueOf(totals.getOrDefault("context_secondary_details_usage_level", "")))
                && previousContextSecondaryRatePct >= 25L);
        boolean slaManagementReviewRequired = "high".equals(String.valueOf(safeSlaAudit.getOrDefault("policy_churn_risk_level", "")))
                || "high".equals(String.valueOf(totals.getOrDefault("workspace_sla_policy_churn_level", "")));
        boolean macroManagementReviewRequired = Boolean.TRUE.equals(safeMacroAudit.get("weekly_review_followup_required"))
                && !Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate"));

        List<Map<String, Object>> sections = new ArrayList<>();
        if (Boolean.TRUE.equals(legacyInventory.get("review_queue_followup_required"))
                || Boolean.TRUE.equals(legacyInventory.get("repeat_review_required"))) {
            sections.add(Map.of(
                    "key", "legacy",
                    "label", "Legacy closure loop",
                    "priority", "high",
                    "summary", String.valueOf(legacyInventory.getOrDefault("review_queue_summary", "")),
                    "management_review_required", legacyManagementReviewRequired,
                    "action_item", firstNonBlank(
                            String.valueOf(legacyInventory.getOrDefault("review_queue_next_action_summary", "")),
                            firstListItem(legacyInventory.get("action_items"),
                            "Закройте weekly closure-loop для legacy review-queue.")
                    )
            ));
        }
        if (Boolean.TRUE.equals(totals.get("context_secondary_details_followup_required"))) {
            sections.add(Map.of(
                    "key", "context",
                    "label", "Context noise",
                    "priority", "medium",
                    "summary", firstNonBlank(
                            String.valueOf(totals.getOrDefault("context_secondary_details_compaction_summary", "")),
                            String.valueOf(totals.getOrDefault("context_extra_attributes_summary", "")),
                            String.valueOf(totals.getOrDefault("context_secondary_details_summary", ""))),
                    "trend_status", contextTrendStatus,
                    "trend_delta_pct", contextSecondaryDeltaPct,
                    "extra_attributes_delta_pct", contextExtraDeltaPct,
                    "management_review_required", contextManagementReviewRequired,
                    "action_item", contextExtraAttributesCompactionCandidate
                            ? "Ужмите extra attributes: оставьте в runtime sidebar только действительно используемые поля."
                            : "Проверьте, почему secondary context раскрывается слишком часто, и ужмите noisy blocks."
            ));
        }
        if (Boolean.TRUE.equals(totals.get("workspace_sla_policy_churn_followup_required"))
                || Boolean.TRUE.equals(safeSlaAudit.get("weekly_review_followup_required"))) {
            sections.add(Map.of(
                    "key", "sla",
                    "label", "SLA governance",
                    "priority", "high",
                    "summary", firstNonBlank(
                            String.valueOf(totals.getOrDefault("workspace_sla_policy_churn_summary", "")),
                            String.valueOf(safeSlaAudit.getOrDefault("weekly_review_summary", ""))),
                    "management_review_required", slaManagementReviewRequired,
                    "action_item", Boolean.TRUE.equals(safeSlaAudit.get("advisory_path_reduction_candidate"))
                            ? "Сократите advisory checkpoints для типовых SLA policy changes."
                            : "Закройте обязательный SLA review path и stabilise decision cadence."
            ));
        }
        if (Boolean.TRUE.equals(safeMacroAudit.get("weekly_review_followup_required"))
                || Boolean.TRUE.equals(safeMacroAudit.get("advisory_followup_required"))) {
            sections.add(Map.of(
                    "key", "macro",
                    "label", "Macro governance",
                    "priority", Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate")) ? "medium" : "high",
                    "summary", String.valueOf(safeMacroAudit.getOrDefault("weekly_review_summary", "")),
                    "management_review_required", macroManagementReviewRequired,
                    "action_item", Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate"))
                            ? "Оставьте low-signal red-list аналитическими и сократите ручной backlog."
                            : "Закройте обязательные macro checkpoints и только потом revisited advisory cleanup."
            ));
        }

        List<Map<String, Object>> sortedSections = sections.stream()
                .sorted((left, right) -> Integer.compare(
                        reviewFocusPriorityWeight(String.valueOf(left.get("priority"))),
                        reviewFocusPriorityWeight(String.valueOf(right.get("priority")))))
                .map(item -> {
                    Map<String, Object> enriched = new LinkedHashMap<>(item);
                    int priorityWeight = reviewFocusPriorityWeight(String.valueOf(item.get("priority")));
                    boolean managementReviewRequired = Boolean.TRUE.equals(item.get("management_review_required"));
                    enriched.put("priority_weight", priorityWeight);
                    enriched.put("followup_required", true);
                    enriched.put("management_review_required", managementReviewRequired);
                    enriched.put("section_status", managementReviewRequired
                            ? "management_review"
                            : priorityWeight == 0 ? "blocking" : "followup");
                    return enriched;
                })
                .toList();
        List<String> topActions = sortedSections.stream()
                .map(item -> trimToNull(String.valueOf(item.get("action_item"))))
                .filter(StringUtils::hasText)
                .limit(4)
                .toList();
        long blockingCount = sortedSections.stream()
                .filter(item -> "high".equals(String.valueOf(item.get("priority"))))
                .count();
        long managementReviewSectionCount = sortedSections.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("management_review_required")))
                .count();
        long focusScore = sortedSections.stream()
                .mapToLong(item -> switch (String.valueOf(item.get("priority"))) {
                    case "high" -> 3L;
                    case "medium" -> 2L;
                    default -> 1L;
                })
                .sum();
        String status = sortedSections.isEmpty()
                ? "ok"
                : blockingCount > 0 ? "hold" : "attention";
        String summary = sortedSections.isEmpty()
                ? "Weekly review focus не требует дополнительных follow-up."
                : "Weekly review focus: %d секции(й), high=%d.".formatted(sortedSections.size(), blockingCount);
        String topPriorityKey = sortedSections.isEmpty()
                ? ""
                : String.valueOf(sortedSections.get(0).getOrDefault("key", ""));
        String topPriorityLabel = sortedSections.isEmpty()
                ? ""
                : String.valueOf(sortedSections.get(0).getOrDefault("label", ""));
        String nextActionSummary = topActions.isEmpty()
                ? "Дополнительный follow-up не требуется."
                : topActions.get(0);
        boolean requiresManagementReview = blockingCount > 1
                || (blockingCount > 0 && sortedSections.size() > 2)
                || managementReviewSectionCount > 0;
        String focusHealth = sortedSections.isEmpty()
                ? "stable"
                : requiresManagementReview ? "management_review" : "followup";
        String priorityMixSummary = sortedSections.isEmpty()
                ? "Weekly focus пуст: follow-up не требуется."
                : "high=%d, follow-up=%d, management-review=%d."
                .formatted(blockingCount, sortedSections.size(), managementReviewSectionCount);

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("status", status);
        focus.put("summary", summary);
        focus.put("section_count", sortedSections.size());
        focus.put("followup_section_count", sortedSections.size());
        focus.put("blocking_count", blockingCount);
        focus.put("management_review_section_count", managementReviewSectionCount);
        focus.put("focus_score", focusScore);
        focus.put("focus_health", focusHealth);
        focus.put("top_priority_key", topPriorityKey);
        focus.put("top_priority_label", topPriorityLabel);
        focus.put("priority_mix_summary", priorityMixSummary);
        focus.put("next_action_summary", nextActionSummary);
        focus.put("requires_management_review", requiresManagementReview);
        focus.put("sections", sortedSections);
        focus.put("top_actions", topActions);
        return focus;
    }

    private int reviewFocusPriorityWeight(String value) {
        return switch (trimToNull(value) == null ? "" : trimToNull(value).toLowerCase(Locale.ROOT)) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private String firstListItem(Object value, String fallback) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String candidate = trimToNull(String.valueOf(item));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String candidate = trimToNull(value);
            if (candidate != null) {
                return candidate;
            }
        }
        return "";
    }

    private String buildAiMonitoringEventsCsv(List<Map<String, Object>> items) {
        StringBuilder out = new StringBuilder();
        out.append("id,ticket_id,event_type,actor,decision_type,decision_reason,source,score,detail,created_at\n");
        for (Map<String, Object> row : items) {
            out.append(csvCell(row.get("id"))).append(',')
                    .append(csvCell(row.get("ticket_id"))).append(',')
                    .append(csvCell(row.get("event_type"))).append(',')
                    .append(csvCell(row.get("actor"))).append(',')
                    .append(csvCell(row.get("decision_type"))).append(',')
                    .append(csvCell(row.get("decision_reason"))).append(',')
                    .append(csvCell(row.get("source"))).append(',')
                    .append(csvCell(row.get("score"))).append(',')
                    .append(csvCell(row.get("detail"))).append(',')
                    .append(csvCell(row.get("created_at"))).append('\n');
        }
        return out.toString();
    }

    private String csvCell(Object value) {
        String text = value != null ? String.valueOf(value) : "";
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private int resolveDialogConfigMinutes(String key, int fallbackValue) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallbackValue;
        } catch (NumberFormatException ex) {
            return fallbackValue;
        }
    }

    private boolean resolveDialogConfigBoolean(String key, boolean fallbackValue) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallbackValue;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeMacroVariableKey(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw == null) {
            return false;
        }
        String normalized = String.valueOf(raw).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Map<String, Object> buildWorkspaceContextContract(Map<String, Object> settings,
                                                              DialogListItem summary,
                                                              Map<String, Object> workspaceClient,
                                                              List<Map<String, Object>> contextSources,
                                                              List<Map<String, Object>> contextBlocks) {
        Map<String, Object> dialogConfig = extractDialogConfig(settings);
        if (dialogConfig.isEmpty()) {
            return Map.of("enabled", false, "required", false, "ready", true);
        }
        boolean required = toBoolean(dialogConfig.get("workspace_rollout_context_contract_required"));
        List<String> scenarios = safeStringList(dialogConfig.get("workspace_rollout_context_contract_scenarios"));
        List<String> mandatoryFields = safeStringList(dialogConfig.get("workspace_rollout_context_contract_mandatory_fields"));
        Map<String, List<String>> mandatoryFieldsByScenario = safeStringListMap(
                dialogConfig.get("workspace_rollout_context_contract_mandatory_fields_by_scenario"));
        List<String> sourceOfTruth = safeStringList(dialogConfig.get("workspace_rollout_context_contract_source_of_truth"));
        Map<String, List<String>> sourceOfTruthByScenario = safeStringListMap(
                dialogConfig.get("workspace_rollout_context_contract_source_of_truth_by_scenario"));
        List<String> priorityBlocks = safeStringList(dialogConfig.get("workspace_rollout_context_contract_priority_blocks"));
        Map<String, List<String>> priorityBlocksByScenario = safeStringListMap(
                dialogConfig.get("workspace_rollout_context_contract_priority_blocks_by_scenario"));
        Map<String, Map<String, String>> playbooks = resolveContextContractPlaybooks(dialogConfig);
        boolean enabled = required
                || !scenarios.isEmpty()
                || !mandatoryFields.isEmpty()
                || !mandatoryFieldsByScenario.isEmpty()
                || !sourceOfTruth.isEmpty()
                || !sourceOfTruthByScenario.isEmpty()
                || !priorityBlocks.isEmpty()
                || !priorityBlocksByScenario.isEmpty()
                || !playbooks.isEmpty();
        if (!enabled) {
            return Map.of("enabled", false, "required", false, "ready", true);
        }

        List<Map<String, Object>> safeSources = contextSources == null ? List.of() : contextSources;
        List<Map<String, Object>> safeBlocks = contextBlocks == null ? List.of() : contextBlocks;
        List<String> activeScenarios = resolveActiveScenarios(summary, scenarios);
        List<String> effectiveMandatoryFields = computeEffectiveMandatoryFields(
                mandatoryFields,
                mandatoryFieldsByScenario,
                activeScenarios);
        List<String> effectiveSourceOfTruth = computeEffectiveScenarioList(
                sourceOfTruth,
                sourceOfTruthByScenario,
                activeScenarios);
        List<String> effectivePriorityBlocks = computeEffectiveScenarioList(
                priorityBlocks,
                priorityBlocksByScenario,
                activeScenarios);
        List<String> missingMandatoryFields = effectiveMandatoryFields.stream()
                .map(this::normalizeMacroVariableKey)
                .filter(StringUtils::hasText)
                .filter(field -> !dialogWorkspaceClientProfileService.hasProfileValue(workspaceClient.get(field)))
                .distinct()
                .toList();

        List<String> sourceViolations = effectiveSourceOfTruth.stream()
                .map(rule -> resolveContextSourceViolation(rule, safeSources))
                .filter(StringUtils::hasText)
                .toList();

        List<String> missingPriorityBlocks = effectivePriorityBlocks.stream()
                .map(this::normalizeMacroVariableKey)
                .filter(StringUtils::hasText)
                .filter(requiredBlock -> safeBlocks.stream().noneMatch(block ->
                        requiredBlock.equals(normalizeMacroVariableKey(String.valueOf(block.get("key"))))
                                && toBoolean(block.get("ready"))))
                .toList();

        List<String> violations = Stream.of(
                        missingMandatoryFields.stream().map(field -> "mandatory_field:" + field),
                        sourceViolations.stream().map(reason -> "source_of_truth:" + reason),
                        missingPriorityBlocks.stream().map(block -> "priority_block:" + block))
                .flatMap(stream -> stream)
                .distinct()
                .toList();
        List<Map<String, Object>> violationDetails = buildWorkspaceContextContractViolationDetails(
                workspaceClient,
                safeSources,
                safeBlocks,
                missingMandatoryFields,
                sourceViolations,
                missingPriorityBlocks,
                playbooks);
        List<Map<String, Object>> primaryViolationDetails = violationDetails.stream()
                .limit(2)
                .toList();
        int deferredViolationCount = Math.max(0, violationDetails.size() - primaryViolationDetails.size());
        List<String> definitionGaps = Stream.of(
                        scenarios.isEmpty() ? "scenarios" : null,
                        (mandatoryFields.isEmpty() && mandatoryFieldsByScenario.isEmpty()) ? "mandatory_fields" : null,
                        (sourceOfTruth.isEmpty() && sourceOfTruthByScenario.isEmpty()) ? "source_of_truth" : null,
                        (priorityBlocks.isEmpty() && priorityBlocksByScenario.isEmpty()) ? "priority_blocks" : null)
                .filter(StringUtils::hasText)
                .toList();
        List<String> operatorFocusBlocks = Stream.concat(priorityBlocks.stream(), priorityBlocksByScenario.values().stream().flatMap(List::stream))
                .map(this::normalizeMacroVariableKey)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
        List<String> actionItems = new ArrayList<>();
        if (!definitionGaps.isEmpty()) {
            actionItems.add("Дополните contract definitions: " + String.join(", ", definitionGaps) + ".");
        }
        if (!missingMandatoryFields.isEmpty()) {
            actionItems.add("Сначала дозаполните поля: " + String.join(", ", missingMandatoryFields.stream().limit(3).toList()) + ".");
        } else if (!sourceViolations.isEmpty()) {
            actionItems.add("Проверьте источники данных: " + String.join(", ", sourceViolations.stream().limit(2).toList()) + ".");
        } else if (!missingPriorityBlocks.isEmpty()) {
            actionItems.add("Верните в workspace блоки: " + String.join(", ", missingPriorityBlocks.stream().limit(3).toList()) + ".");
        }
        String operatorSummary = violations.isEmpty()
                ? "Minimum profile соблюдён."
                : !missingMandatoryFields.isEmpty()
                ? "Сначала заполните обязательные поля клиента."
                : !sourceViolations.isEmpty()
                ? "Проверьте source-of-truth и freshness для customer context."
                : !missingPriorityBlocks.isEmpty()
                ? "Верните обязательные context-блоки в основной workspace."
                : !definitionGaps.isEmpty()
                ? "Contract definitions требуют cleanup."
                : "Context contract требует action-oriented follow-up.";
        String nextStepSummary = actionItems.isEmpty() ? "" : actionItems.get(0);
        boolean definitionReady = !scenarios.isEmpty()
                 && (!mandatoryFields.isEmpty() || !mandatoryFieldsByScenario.isEmpty())
                 && (!sourceOfTruth.isEmpty() || !sourceOfTruthByScenario.isEmpty())
                 && (!priorityBlocks.isEmpty() || !priorityBlocksByScenario.isEmpty());
        boolean ready = violations.isEmpty() && definitionReady;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", true);
        payload.put("required", required);
        payload.put("ready", ready);
        payload.put("definition_ready", definitionReady);
        payload.put("scenarios", scenarios);
        payload.put("mandatory_fields", mandatoryFields);
        payload.put("mandatory_fields_by_scenario", mandatoryFieldsByScenario);
        payload.put("active_scenarios", activeScenarios);
        payload.put("effective_mandatory_fields", effectiveMandatoryFields);
        payload.put("source_of_truth", sourceOfTruth);
        payload.put("source_of_truth_by_scenario", sourceOfTruthByScenario);
        payload.put("effective_source_of_truth", effectiveSourceOfTruth);
        payload.put("priority_blocks", priorityBlocks);
        payload.put("priority_blocks_by_scenario", priorityBlocksByScenario);
        payload.put("effective_priority_blocks", effectivePriorityBlocks);
        payload.put("missing_mandatory_fields", missingMandatoryFields);
        payload.put("source_of_truth_violations", sourceViolations);
        payload.put("missing_priority_blocks", missingPriorityBlocks);
        payload.put("violations", violations);
        payload.put("violation_details", violationDetails);
        payload.put("primary_violation_details", primaryViolationDetails);
        payload.put("deferred_violation_count", deferredViolationCount);
        payload.put("playbooks", playbooks);
        payload.put("definition_gaps", definitionGaps);
        payload.put("operator_focus_blocks", operatorFocusBlocks);
        payload.put("progressive_disclosure_ready", !operatorFocusBlocks.isEmpty());
        payload.put("operator_summary", operatorSummary);
        payload.put("next_step_summary", nextStepSummary);
        payload.put("action_items", actionItems);
        payload.put("checked_at_utc", Instant.now().toString());
        return payload;
    }

    private List<String> resolveActiveScenarios(DialogListItem summary, List<String> configuredScenarios) {
        if (configuredScenarios.isEmpty()) {
            return List.of();
        }
        String category = summary.categoriesSafe();
        List<String> active = new ArrayList<>();
        for (String scenario : configuredScenarios) {
            if (StringUtils.hasText(category) && category.toLowerCase(Locale.ROOT).contains(scenario.toLowerCase(Locale.ROOT))) {
                active.add(scenario);
            }
        }
        return active.isEmpty() && !configuredScenarios.isEmpty() ? List.of(configuredScenarios.get(0)) : active;
    }

    private List<String> computeEffectiveMandatoryFields(List<String> baseline,
                                                         Map<String, List<String>> byScenario,
                                                         List<String> activeScenarios) {
        return computeEffectiveScenarioList(baseline, byScenario, activeScenarios);
    }

    private List<String> computeEffectiveScenarioList(List<String> baseline,
                                                      Map<String, List<String>> byScenario,
                                                      List<String> activeScenarios) {
        Set<String> effective = new LinkedHashSet<>(baseline);
        for (String scenario : activeScenarios) {
            List<String> scenarioFields = byScenario.get(scenario.toLowerCase(Locale.ROOT));
            if (scenarioFields != null) {
                effective.addAll(scenarioFields);
            }
        }
        return new ArrayList<>(effective);
    }
    private String resolveContextSourceViolation(String sourceRule, List<Map<String, Object>> contextSources) {
        String normalizedRule = trimToNull(sourceRule);
        if (normalizedRule == null) {
            return null;
        }
        String[] parts = normalizedRule.split(":", 2);
        String field = normalizeMacroVariableKey(parts[0]);
        String source = parts.length > 1 ? normalizeMacroVariableKey(parts[1]) : null;
        if (!StringUtils.hasText(field) || !StringUtils.hasText(source)) {
            return normalizedRule + ":invalid_rule";
        }
        Map<String, Object> matchedSource = contextSources.stream()
                .filter(item -> source.equals(normalizeMacroVariableKey(String.valueOf(item.get("key")))))
                .findFirst()
                .orElse(null);
        if (matchedSource == null) {
            return field + ":" + source + ":source_missing";
        }
        if (!safeStringList(matchedSource.get("matched_attributes")).contains(field)) {
            return field + ":" + source + ":field_not_matched";
        }
        if (!toBoolean(matchedSource.get("ready"))) {
            String status = normalizeMacroVariableKey(String.valueOf(matchedSource.get("status")));
            return field + ":" + source + ":" + (StringUtils.hasText(status) ? status : "not_ready");
        }
        return null;
    }

    private Map<String, Map<String, String>> resolveContextContractPlaybooks(Map<String, Object> dialogConfig) {
        Object raw = dialogConfig.get("workspace_rollout_context_contract_playbooks");
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalizeMacroVariableKey(String.valueOf(entry.getKey()));
            if (!StringUtils.hasText(key) || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            Object labelRaw = item.get("label");
            Object urlRaw = item.get("url");
            Object summaryRaw = item.get("summary");
            String label = trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
            String url = trimToNull(urlRaw == null ? null : String.valueOf(urlRaw));
            String summary = trimToNull(summaryRaw == null ? null : String.valueOf(summaryRaw));
            if (!StringUtils.hasText(url) || (!url.startsWith("https://") && !url.startsWith("http://"))) {
                continue;
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("label", label != null ? label : "Playbook");
            payload.put("url", url);
            payload.put("summary", summary != null ? summary : "");
            result.put(key, payload);
        }
        return result;
    }

    private List<Map<String, Object>> buildWorkspaceContextContractViolationDetails(Map<String, Object> workspaceClient,
                                                                                    List<Map<String, Object>> contextSources,
                                                                                    List<Map<String, Object>> contextBlocks,
                                                                                    List<String> missingMandatoryFields,
                                                                                    List<String> sourceViolations,
                                                                                    List<String> missingPriorityBlocks,
                                                                                    Map<String, Map<String, String>> playbooks) {
        List<Map<String, Object>> result = new ArrayList<>();
        missingMandatoryFields.forEach(field -> result.add(buildMandatoryFieldViolationDetail(field, workspaceClient, playbooks)));
        sourceViolations.forEach(violation -> result.add(buildSourceOfTruthViolationDetail(violation, workspaceClient, contextSources, playbooks)));
        missingPriorityBlocks.forEach(block -> result.add(buildPriorityBlockViolationDetail(block, contextBlocks, playbooks)));
        return result;
    }

    private Map<String, Object> buildMandatoryFieldViolationDetail(String field,
                                                                   Map<String, Object> workspaceClient,
                                                                   Map<String, Map<String, String>> playbooks) {
        String normalizedField = normalizeMacroVariableKey(field);
        String label = resolveWorkspaceContextAttributeLabel(workspaceClient, normalizedField);
        String code = "mandatory_field:" + normalizedField;
        return buildContextViolationDetail(
                code,
                "mandatory_field",
                normalizedField,
                "high",
                "Поле \"" + label + "\" не заполнено",
                "Заполните обязательное поле \"" + label + "\" в карточке клиента.",
                "Свяжитесь с клиентом или проверьте CRM, затем сохраните поле \"" + label + "\".",
                "Missing mandatory field: " + label,
                resolveContextContractPlaybook(playbooks, code, "mandatory_field:" + normalizedField, "mandatory_field"));
    }

    private Map<String, Object> buildSourceOfTruthViolationDetail(String rawViolation,
                                                                  Map<String, Object> workspaceClient,
                                                                  List<Map<String, Object>> contextSources,
                                                                  Map<String, Map<String, String>> playbooks) {
        String normalized = trimToNull(rawViolation);
        String[] parts = normalized == null ? new String[0] : normalized.split(":");
        String field = parts.length > 0 ? normalizeMacroVariableKey(parts[0]) : "";
        String source = parts.length > 1 ? normalizeMacroVariableKey(parts[1]) : "";
        String status = parts.length > 2 ? normalizeMacroVariableKey(parts[2]) : "not_ready";
        String fieldLabel = resolveWorkspaceContextAttributeLabel(workspaceClient, field);
        String sourceLabel = resolveWorkspaceContextSourceLabel(contextSources, source);
        String operatorMessage;
        String shortLabel;
        String nextStep;
        if ("source_missing".equals(status)) {
            shortLabel = "Нет источника \"" + sourceLabel + "\"";
            operatorMessage = "Подключите источник \"" + sourceLabel + "\" для поля \"" + fieldLabel + "\".";
            nextStep = "Проверьте интеграцию или включите источник \"" + sourceLabel + "\" для этого клиента.";
        } else if ("field_not_matched".equals(status)) {
            shortLabel = "Поле \"" + fieldLabel + "\" не приходит из \"" + sourceLabel + "\"";
            operatorMessage = "Проверьте, что источник \"" + sourceLabel + "\" действительно отдаёт поле \"" + fieldLabel + "\".";
            nextStep = "Сверьте маппинг поля \"" + fieldLabel + "\" и обновите источник \"" + sourceLabel + "\".";
        } else if ("stale".equals(status)) {
            shortLabel = "Данные \"" + fieldLabel + "\" устарели";
            operatorMessage = "Обновите данные \"" + fieldLabel + "\" из источника \"" + sourceLabel + "\".";
            nextStep = "Запустите обновление из \"" + sourceLabel + "\" или подтвердите актуальность данных вручную.";
        } else if ("invalid_utc".equals(status)) {
            shortLabel = "Невалидный UTC timestamp для \"" + sourceLabel + "\"";
            operatorMessage = "Исправьте UTC timestamp источника \"" + sourceLabel + "\" для поля \"" + fieldLabel + "\".";
            nextStep = "Исправьте timestamp источника \"" + sourceLabel + "\" в ISO-8601 UTC и повторите синхронизацию.";
        } else {
            shortLabel = "Проблема с источником \"" + sourceLabel + "\"";
            operatorMessage = "Проверьте источник \"" + sourceLabel + "\" для поля \"" + fieldLabel + "\".";
            nextStep = "Проверьте доступность источника \"" + sourceLabel + "\" и корректность данных для поля \"" + fieldLabel + "\".";
        }
        String code = "source_of_truth:" + normalized;
        return buildContextViolationDetail(
                code,
                "source_of_truth",
                field,
                "invalid_utc".equals(status) || "source_missing".equals(status) ? "high" : "medium",
                shortLabel,
                operatorMessage,
                nextStep,
                "Source-of-truth gap: " + fieldLabel + " via " + sourceLabel + " (" + status + ")",
                resolveContextContractPlaybook(playbooks, code, "source_of_truth:" + field + ":" + source, "source_of_truth"));
    }

    private Map<String, Object> buildPriorityBlockViolationDetail(String block,
                                                                  List<Map<String, Object>> contextBlocks,
                                                                  Map<String, Map<String, String>> playbooks) {
        String normalizedBlock = normalizeMacroVariableKey(block);
        String label = resolveWorkspaceContextBlockLabel(contextBlocks, normalizedBlock);
        String code = "priority_block:" + normalizedBlock;
        return buildContextViolationDetail(
                code,
                "priority_block",
                normalizedBlock,
                "medium",
                "Нет блока \"" + label + "\"",
                "Верните в рабочий контур блок \"" + label + "\" или снимите его из обязательных для этого сценария.",
                "Верните блок \"" + label + "\" в workspace либо обновите contract для текущего сценария.",
                "Priority block missing: " + label,
                resolveContextContractPlaybook(playbooks, code, "priority_block:" + normalizedBlock, "priority_block"));
    }

    private Map<String, Object> buildContextViolationDetail(String code,
                                                            String type,
                                                            String key,
                                                            String severity,
                                                            String shortLabel,
                                                            String operatorMessage,
                                                            String nextStep,
                                                            String analyticsMessage,
                                                            Map<String, String> playbook) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("type", type);
        payload.put("key", key);
        payload.put("severity", severity);
        payload.put("severity_rank", switch (severity) {
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        });
        payload.put("short_label", shortLabel);
        payload.put("operator_message", operatorMessage);
        payload.put("next_step", nextStep);
        payload.put("action_label", playbook != null && !playbook.isEmpty()
                ? "Открыть playbook"
                : switch (type) {
                    case "mandatory_field" -> "Заполнить поле";
                    case "source_of_truth" -> "Проверить источник";
                    case "priority_block" -> "Вернуть блок";
                    default -> "Исправить";
                });
        payload.put("analytics_message", analyticsMessage);
        if (playbook != null && !playbook.isEmpty()) {
            payload.put("playbook", playbook);
        }
        return payload;
    }

    private Map<String, String> resolveContextContractPlaybook(Map<String, Map<String, String>> playbooks,
                                                               String exactCode,
                                                               String scopedCode,
                                                               String type) {
        if (playbooks.isEmpty()) {
            return Map.of();
        }
        for (String key : List.of(
                normalizeMacroVariableKey(exactCode),
                normalizeMacroVariableKey(scopedCode),
                normalizeMacroVariableKey(type))) {
            if (StringUtils.hasText(key) && playbooks.containsKey(key)) {
                return playbooks.get(key);
            }
        }
        return Map.of();
    }

    private String resolveWorkspaceContextAttributeLabel(Map<String, Object> workspaceClient, String key) {
        if (workspaceClient.get("attribute_labels") instanceof Map<?, ?> labels) {
            for (Map.Entry<?, ?> entry : labels.entrySet()) {
                String normalizedKey = normalizeMacroVariableKey(String.valueOf(entry.getKey()));
                if (key.equals(normalizedKey)) {
                    Object labelRaw = entry.getValue();
                    String label = trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
                    if (StringUtils.hasText(label)) {
                        return label;
                    }
                }
            }
        }
        return humanizeMacroVariableLabel(key);
    }

    private String resolveWorkspaceContextSourceLabel(List<Map<String, Object>> contextSources, String sourceKey) {
        return contextSources.stream()
                .filter(item -> sourceKey.equals(normalizeMacroVariableKey(String.valueOf(item.get("key")))))
                .map(item -> {
                    Object labelRaw = item.get("label");
                    return trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
                })
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(humanizeMacroVariableLabel(sourceKey));
    }

    private String resolveWorkspaceContextBlockLabel(List<Map<String, Object>> contextBlocks, String blockKey) {
        return contextBlocks.stream()
                .filter(item -> blockKey.equals(normalizeMacroVariableKey(String.valueOf(item.get("key")))))
                .map(item -> {
                    Object labelRaw = item.get("label");
                    return trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
                })
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(humanizeMacroVariableLabel(blockKey));
    }

    private Map<String, Object> extractDialogConfig(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        dialogConfig.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    private boolean hasWorkspaceSourceCoverage(String sourceKey,
                                               Map<String, Object> workspaceClient,
                                               Map<String, Object> localProfileEnrichment,
                                               Map<String, Object> externalProfileEnrichment,
                                               Map<String, Object> externalLinks) {
        if ("local".equals(sourceKey)) {
            return true;
        }
        if (externalLinks != null && externalLinks.containsKey(sourceKey)) {
            return true;
        }
        return !resolveWorkspaceContextSourceMatchedAttributes(sourceKey, workspaceClient, localProfileEnrichment, externalProfileEnrichment).isEmpty();
    }

    private List<String> resolveWorkspaceContextSourceMatchedAttributes(String sourceKey,
                                                                       Map<String, Object> workspaceClient,
                                                                       Map<String, Object> localProfileEnrichment,
                                                                       Map<String, Object> externalProfileEnrichment) {
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        if ("local".equals(sourceKey)) {
            if (workspaceClient != null) {
                List.of("name", "status", "last_message_at", "responsible").forEach(field -> {
                    if (dialogWorkspaceClientProfileService.hasProfileValue(workspaceClient.get(field))) {
                        matches.add(field);
                    }
                });
            }
            if (localProfileEnrichment != null) {
                localProfileEnrichment.keySet().forEach(key -> {
                    String normalized = normalizeMacroVariableKey(key);
                    if (StringUtils.hasText(normalized)) {
                        matches.add(normalized);
                    }
                });
            }
            return new ArrayList<>(matches);
        }

        String prefix = sourceKey + "_";
        appendMatchingWorkspaceSourceKeys(matches, workspaceClient, prefix);
        appendMatchingWorkspaceSourceKeys(matches, localProfileEnrichment, prefix);
        appendMatchingWorkspaceSourceKeys(matches, externalProfileEnrichment, prefix);
        return new ArrayList<>(matches);
    }

    private void appendMatchingWorkspaceSourceKeys(Set<String> matches,
                                                   Map<String, Object> values,
                                                   String prefix) {
        if (values == null || values.isEmpty()) {
            return;
        }
        values.forEach((rawKey, value) -> {
            String normalized = normalizeMacroVariableKey(String.valueOf(rawKey));
            if (StringUtils.hasText(normalized) && normalized.startsWith(prefix) && dialogWorkspaceClientProfileService.hasProfileValue(value)) {
                matches.add(normalized);
            }
        });
    }

    private String resolveSlaState(String createdAt, int targetMinutes, int warningMinutes, String statusKey) {
        if ("closed".equals(normalizeSlaLifecycleState(statusKey))) {
            return "closed";
        }
        Long createdAtMs = parseTimestampToMillis(createdAt);
        if (createdAtMs == null) {
            return "normal";
        }
        long deadlineMs = createdAtMs + targetMinutes * 60_000L;
        long warningMs = deadlineMs - warningMinutes * 60_000L;
        long nowMs = System.currentTimeMillis();
        if (nowMs >= deadlineMs) {
            return "breached";
        }
        if (nowMs >= warningMs) {
            return "at_risk";
        }
        return "normal";
    }

    private Long resolveSlaMinutesLeft(String createdAt, int targetMinutes, String statusKey, long nowMs) {
        if (!"open".equals(normalizeSlaLifecycleState(statusKey))) {
            return null;
        }
        Long createdAtMs = parseTimestampToMillis(createdAt);
        if (createdAtMs == null) {
            return null;
        }
        long deadlineMs = createdAtMs + targetMinutes * 60_000L;
        return Math.round((deadlineMs - nowMs) / 60_000d);
    }

    private String normalizeSlaLifecycleState(String statusKey) {
        String normalized = statusKey != null ? statusKey.trim().toLowerCase() : "";
        if ("closed".equals(normalized) || "auto_closed".equals(normalized)) {
            return "closed";
        }
        return "open";
    }

    private String computeDeadlineAt(String createdAt, int targetMinutes) {
        Long createdAtMs = parseTimestampToMillis(createdAt);
        if (createdAtMs == null) {
            return null;
        }
        return Instant.ofEpochMilli(createdAtMs + targetMinutes * 60_000L).toString();
    }

    private Long parseTimestampToMillis(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim();
        if (value.matches("\\d{10,13}")) {
            try {
                long epoch = Long.parseLong(value);
                return value.length() == 10 ? epoch * 1000 : epoch;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ex) {
            // try the same timestamp as explicit UTC when timezone is omitted
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            // continue
        }
        try {
            String normalized = value.contains(" ") ? value.replace(" ", "T") : value;
            return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

}
