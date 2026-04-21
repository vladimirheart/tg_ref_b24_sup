package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
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
    private final DialogWorkspaceContextContractService dialogWorkspaceContextContractService;
    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
    private static final int DEFAULT_SLA_WARNING_MINUTES = 4 * 60;
    private static final int DEFAULT_WORKSPACE_LIMIT = 50;
    private static final int MAX_WORKSPACE_LIMIT = 200;
    private static final Set<String> WORKSPACE_INCLUDE_ALLOWED = Set.of("messages", "context", "sla", "permissions");
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
                                  DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService,
                                  DialogWorkspaceContextContractService dialogWorkspaceContextContractService) {
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
        this.dialogWorkspaceContextContractService = dialogWorkspaceContextContractService;
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
        Map<String, Object> contextContract = dialogWorkspaceContextContractService.buildContextContract(
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
