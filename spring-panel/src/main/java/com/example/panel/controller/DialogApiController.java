package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogService;
import com.example.panel.service.DialogAiAssistantService;
import com.example.panel.service.NotificationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.PublicFormService;
import com.example.panel.service.SlaEscalationWebhookNotifier;
import com.example.panel.service.SharedConfigService;
import com.example.panel.storage.AttachmentService;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/dialogs")
@Validated
public class DialogApiController {

    private static final Logger log = LoggerFactory.getLogger(DialogApiController.class);

    private final DialogService dialogService;
    private final DialogReplyService dialogReplyService;
    private final DialogNotificationService dialogNotificationService;
    private final AttachmentService attachmentService;
    private final SharedConfigService sharedConfigService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final PublicFormService publicFormService;
    private final SlaEscalationWebhookNotifier slaEscalationWebhookNotifier;
    private final DialogAiAssistantService dialogAiAssistantService;
    private static final long QUICK_ACTION_TARGET_MS = 1500;
    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
    private static final int DEFAULT_SLA_WARNING_MINUTES = 4 * 60;
    private static final int DEFAULT_WORKSPACE_LIMIT = 50;
    private static final int MAX_WORKSPACE_LIMIT = 200;
    private static final Pattern MACRO_VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-z0-9_]+)(?:\\s*\\|\\s*([^}]+))?\\s*}}", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter MACRO_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Moscow"));
    private static final DateTimeFormatter MACRO_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final int DEFAULT_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS = 2000;
    private static final int MIN_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS = 200;
    private static final int MAX_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS = 10000;
    private static final int DEFAULT_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS = 120;
    private static final int MIN_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS = 0;
    private static final int MAX_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS = 3600;
    private static final int DEFAULT_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS = 2500;
    private static final int MIN_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS = 300;
    private static final int MAX_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS = 10000;
    private static final int DEFAULT_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS = 120;
    private static final int MIN_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS = 0;
    private static final int MAX_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS = 3600;
    private static final Pattern SAFE_HTTP_HEADER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final Object MACRO_CATALOG_EXTERNAL_CACHE_LOCK = new Object();
    private static final Object WORKSPACE_EXTERNAL_PROFILE_CACHE_LOCK = new Object();
    private static volatile String macroCatalogExternalCacheUrl = null;
    private static volatile List<Map<String, String>> macroCatalogExternalCacheVariables = List.of();
    private static volatile Instant macroCatalogExternalCacheExpiresAt = Instant.EPOCH;
    private static volatile String workspaceExternalProfileCacheUrl = null;
    private static volatile Map<String, Object> workspaceExternalProfileCache = Map.of();
    private static volatile Instant workspaceExternalProfileCacheExpiresAt = Instant.EPOCH;
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
    private static final List<Map<String, String>> BUILTIN_MACRO_VARIABLES = List.of(
            macroVariable("client_name", "Имя клиента"),
            macroVariable("ticket_id", "ID обращения"),
            macroVariable("operator_name", "Имя оператора"),
            macroVariable("channel_name", "Канал обращения"),
            macroVariable("business", "Бизнес-направление"),
            macroVariable("location", "Локация клиента"),
            macroVariable("dialog_status", "Текущий статус диалога"),
            macroVariable("created_at", "Дата создания обращения"),
            macroVariable("client_total_dialogs", "Всего обращений клиента"),
            macroVariable("client_open_dialogs", "Открытые обращения клиента"),
            macroVariable("client_resolved_30d", "Решено за 30 дней"),
            macroVariable("client_avg_rating", "Средний рейтинг клиента"),
            macroVariable("client_segment_list", "Сегменты клиента (через запятую)"),
            macroVariable("current_date", "Текущая дата"),
            macroVariable("current_time", "Текущее время")
    );

    public DialogApiController(DialogService dialogService,
                               DialogReplyService dialogReplyService,
                               DialogNotificationService dialogNotificationService,
                               AttachmentService attachmentService,
                               SharedConfigService sharedConfigService,
                               PermissionService permissionService,
                               NotificationService notificationService,
                               PublicFormService publicFormService,
                               SlaEscalationWebhookNotifier slaEscalationWebhookNotifier,
                               DialogAiAssistantService dialogAiAssistantService) {
        this.dialogService = dialogService;
        this.dialogReplyService = dialogReplyService;
        this.dialogNotificationService = dialogNotificationService;
        this.attachmentService = attachmentService;
        this.sharedConfigService = sharedConfigService;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
        this.publicFormService = publicFormService;
        this.slaEscalationWebhookNotifier = slaEscalationWebhookNotifier;
        this.dialogAiAssistantService = dialogAiAssistantService;
    }

    @GetMapping
    public Map<String, Object> list(Authentication authentication) {
        DialogSummary summary = dialogService.loadSummary();
        List<DialogListItem> dialogs = dialogService.loadDialogs(authentication != null ? authentication.getName() : null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", summary);
        payload.put("dialogs", dialogs);
        payload.put("sla_orchestration", buildSlaOrchestration(dialogs));
        payload.put("success", true);
        log.info("Loaded dialogs API payload: {} dialogs, summary stats loaded", dialogs.size());
        return payload;
    }

    @GetMapping("/public-form-metrics")
    public Map<String, Object> publicFormMetrics(@RequestParam(value = "channelId", required = false) Long channelId) {
        return Map.of(
                "success", true,
                "metrics", publicFormService.loadMetricsSnapshot(channelId)
        );
    }

    private Map<String, Object> buildSlaOrchestration(List<DialogListItem> dialogs) {
        int targetMinutes = resolveDialogConfigMinutes("sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int warningMinutes = Math.min(resolveDialogConfigMinutes("sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES), targetMinutes);
        int criticalMinutes = resolveDialogConfigMinutes("sla_critical_minutes", 30);
        boolean escalationEnabled = resolveDialogConfigBoolean("sla_critical_escalation_enabled", true);

        Map<String, Object> ticketSignals = new LinkedHashMap<>();
        long nowMs = System.currentTimeMillis();
        for (DialogListItem dialog : dialogs) {
            String ticketId = dialog.ticketId();
            if (ticketId == null || ticketId.isBlank()) {
                continue;
            }
            String statusKey = dialog.statusKey();
            String state = resolveSlaState(dialog.createdAt(), targetMinutes, warningMinutes, statusKey);
            Long minutesLeft = resolveSlaMinutesLeft(dialog.createdAt(), targetMinutes, statusKey, nowMs);
            boolean critical = escalationEnabled && "open".equals(normalizeSlaLifecycleState(statusKey))
                    && minutesLeft != null && minutesLeft <= criticalMinutes;
            boolean assigned = dialog.responsible() != null && !dialog.responsible().isBlank();

            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("state", state);
            signal.put("minutes_left", minutesLeft);
            signal.put("is_critical", critical);
            signal.put("auto_pin", critical);
            signal.put("escalation_required", critical && !assigned);
            signal.put("escalation_reason", critical && !assigned ? "critical_sla_unassigned" : null);
            ticketSignals.put(ticketId, signal);
        }

        return Map.of(
                "enabled", escalationEnabled,
                "target_minutes", targetMinutes,
                "warning_minutes", warningMinutes,
                "critical_minutes", criticalMinutes,
                "generated_at", Instant.ofEpochMilli(nowMs).toString(),
                "tickets", ticketSignals
        );
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> details(@PathVariable String ticketId,
                                     @RequestParam(value = "channelId", required = false) Long channelId,
                                     Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.markDialogAsRead(ticketId, operator);
        Optional<DialogDetails> details = dialogService.loadDialogDetails(ticketId, channelId, operator);
        log.info("Dialog details requested for ticket {} (channelId={}): {}", ticketId, channelId,
                details.map(d -> "found").orElse("not found"));
        return details.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден")));
    }

    @GetMapping("/{ticketId}/history")
    public Map<String, Object> history(@PathVariable String ticketId,
                                        @RequestParam(value = "channelId", required = false) Long channelId,
                                        Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.markDialogAsRead(ticketId, operator);
        List<ChatMessageDto> history = dialogService.loadHistory(ticketId, channelId);
        log.info("History requested for ticket {} (channelId={}): {} messages", ticketId, channelId, history.size());
        return Map.of(
                "success", true,
                "messages", history
        );
    }

    @PostMapping("/macro/dry-run")
    public ResponseEntity<?> dryRunMacro(@RequestBody(required = false) MacroDryRunRequest request,
                                         Authentication authentication) {
        if (request == null || !StringUtils.hasText(request.templateText())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "template_text is required"
            ));
        }
        String operator = authentication != null ? authentication.getName() : null;
        Map<String, String> variables = buildMacroVariables(request.ticketId(), operator, request.variables());
        MacroDryRunResult result = renderMacroTemplate(request.templateText(), variables);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "rendered_text", result.renderedText(),
                "used_variables", result.usedVariables(),
                "missing_variables", result.missingVariables()
        ));
    }

    @GetMapping("/macro/variables")
    public Map<String, Object> macroVariables(@RequestParam(value = "ticketId", required = false) String ticketId,
                                              Authentication authentication) {
        List<Map<String, String>> variables = BUILTIN_MACRO_VARIABLES.stream()
                .map(item -> macroVariable(item.get("key"), item.get("label"), null, "builtin"))
                .collect(Collectors.toCollection(ArrayList::new));
        Map<String, Object> settings = sharedConfigService.loadSettings();
        appendMacroCatalogVariables(variables, resolveMacroVariableCatalog(settings), "settings_catalog");
        appendMacroCatalogVariables(variables, resolveExternalMacroVariableCatalog(settings), "external_catalog");
        resolveDialogConfigMacroVariableDefaults(settings).forEach((key, defaultValue) -> {
            if (variables.stream().noneMatch(item -> key.equals(item.get("key")))) {
                variables.add(macroVariable(key, "Значение из dialog_config", defaultValue, "dialog_config_default"));
            }
        });
        if (StringUtils.hasText(ticketId)) {
            String operator = authentication != null ? authentication.getName() : null;
            Map<String, String> contextVariables = buildMacroVariables(ticketId.trim(), operator, null);
            contextVariables.forEach((key, value) -> {
                if (variables.stream().noneMatch(item -> key.equals(item.get("key")))) {
                    variables.add(macroVariable(key, humanizeMacroVariableLabel(key), value, "ticket_context"));
                }
            });
        }
        return Map.of("success", true, "variables", variables);
    }

    private void appendMacroCatalogVariables(List<Map<String, String>> variables, Object source, String sourceName) {
        if (!(source instanceof List<?> entries)) {
            return;
        }
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String key = map.get("key") != null ? String.valueOf(map.get("key")).trim().toLowerCase() : "";
            String label = map.get("label") != null ? String.valueOf(map.get("label")).trim() : "";
            String defaultValue = map.get("default_value") != null ? String.valueOf(map.get("default_value")).trim() : "";
            if (!StringUtils.hasText(key) || !StringUtils.hasText(label)) {
                continue;
            }
            if (variables.stream().noneMatch(item -> key.equals(item.get("key")))) {
                variables.add(macroVariable(key, label, defaultValue, sourceName));
            }
        }
    }

    private Object resolveExternalMacroVariableCatalog(Map<String, Object> settings) {
        Object rawDialogConfig = settings != null ? settings.get("dialog_config") : null;
        if (!(rawDialogConfig instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        String externalUrl = dialogConfig.get("macro_variable_catalog_external_url") != null
                ? String.valueOf(dialogConfig.get("macro_variable_catalog_external_url")).trim()
                : "";
        if (!StringUtils.hasText(externalUrl)) {
            return List.of();
        }
        int cacheTtlSeconds = clampMacroCatalogCacheTtlSeconds(dialogConfig.get("macro_variable_catalog_external_cache_ttl_seconds"));
        List<Map<String, String>> cached = resolveCachedExternalMacroCatalog(externalUrl, cacheTtlSeconds);
        if (!cached.isEmpty()) {
            return cached;
        }
        int timeoutMs = clampMacroCatalogTimeout(dialogConfig.get("macro_variable_catalog_external_timeout_ms"));
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(externalUrl))
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs));
            applyMacroCatalogExternalAuthHeader(requestBuilder, dialogConfig);
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("macro catalog external fetch failed: status={} url={}", response.statusCode(), externalUrl);
                return resolveCachedExternalMacroCatalogOnFailure(externalUrl);
            }
            Object parsed = OBJECT_MAPPER.readValue(response.body(), new TypeReference<Object>() {});
            List<Map<String, String>> normalized = normalizeMacroCatalogVariables(parsed);
            if (!normalized.isEmpty()) {
                storeExternalMacroCatalogCache(externalUrl, normalized, cacheTtlSeconds);
                return normalized;
            }
            if (parsed instanceof Map<?, ?> parsedMap && parsedMap.get("variables") instanceof List<?>) {
                return resolveCachedExternalMacroCatalogOnFailure(externalUrl);
            }
            if (parsed instanceof List<?>) {
                return resolveCachedExternalMacroCatalogOnFailure(externalUrl);
            }
            log.warn("macro catalog external fetch returned unexpected payload type: url={} type={}",
                    externalUrl,
                    parsed != null ? parsed.getClass().getSimpleName() : "null");
        } catch (Exception exception) {
            log.warn("macro catalog external fetch failed: url={} detail={}", externalUrl, exception.toString());
            return resolveCachedExternalMacroCatalogOnFailure(externalUrl);
        }
        return List.of();
    }

    private void applyMacroCatalogExternalAuthHeader(HttpRequest.Builder requestBuilder,
                                                    Map<?, ?> dialogConfig) {
        if (requestBuilder == null || dialogConfig == null) {
            return;
        }
        String token = trimToNull(String.valueOf(dialogConfig.get("macro_variable_catalog_external_auth_token")));
        if (!StringUtils.hasText(token)) {
            return;
        }
        String configuredHeader = trimToNull(String.valueOf(dialogConfig.get("macro_variable_catalog_external_auth_header")));
        String headerName = StringUtils.hasText(configuredHeader) ? configuredHeader : "Authorization";
        if (!SAFE_HTTP_HEADER_NAME_PATTERN.matcher(headerName).matches()) {
            log.warn("macro catalog external auth header ignored due to unsafe header name: {}", headerName);
            return;
        }
        requestBuilder.header(headerName, token);
    }

    private List<Map<String, String>> normalizeMacroCatalogVariables(Object rawPayload) {
        Object payload = rawPayload;
        if (payload instanceof Map<?, ?> map && map.get("variables") instanceof List<?>) {
            payload = map.get("variables");
        }
        if (!(payload instanceof List<?> entries)) {
            return List.of();
        }
        List<Map<String, String>> normalized = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String key = trimToNull(String.valueOf(map.get("key")));
            String label = trimToNull(String.valueOf(map.get("label")));
            if (!StringUtils.hasText(key) || !StringUtils.hasText(label)) {
                continue;
            }
            String normalizedKey = key.trim().toLowerCase();
            if (keys.contains(normalizedKey)) {
                continue;
            }
            keys.add(normalizedKey);
            String defaultValue = trimToNull(String.valueOf(map.get("default_value")));
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", normalizedKey);
            item.put("label", label);
            if (StringUtils.hasText(defaultValue)) {
                item.put("default_value", defaultValue);
            }
            normalized.add(item);
        }
        return normalized;
    }

    private List<Map<String, String>> resolveCachedExternalMacroCatalog(String externalUrl, int cacheTtlSeconds) {
        if (cacheTtlSeconds <= 0) {
            return List.of();
        }
        if (!externalUrl.equals(macroCatalogExternalCacheUrl)) {
            return List.of();
        }
        if (macroCatalogExternalCacheExpiresAt != null && Instant.now().isBefore(macroCatalogExternalCacheExpiresAt)) {
            return macroCatalogExternalCacheVariables;
        }
        return List.of();
    }

    private List<Map<String, String>> resolveCachedExternalMacroCatalogOnFailure(String externalUrl) {
        if (externalUrl.equals(macroCatalogExternalCacheUrl) && !macroCatalogExternalCacheVariables.isEmpty()) {
            log.info("macro catalog external fetch fallback to stale cache: url={} entries={}",
                    externalUrl,
                    macroCatalogExternalCacheVariables.size());
            return macroCatalogExternalCacheVariables;
        }
        return List.of();
    }

    private void storeExternalMacroCatalogCache(String externalUrl, List<Map<String, String>> variables, int cacheTtlSeconds) {
        if (!StringUtils.hasText(externalUrl) || variables == null || variables.isEmpty() || cacheTtlSeconds <= 0) {
            return;
        }
        synchronized (MACRO_CATALOG_EXTERNAL_CACHE_LOCK) {
            macroCatalogExternalCacheUrl = externalUrl;
            macroCatalogExternalCacheVariables = List.copyOf(variables);
            macroCatalogExternalCacheExpiresAt = Instant.now().plusSeconds(cacheTtlSeconds);
        }
    }

    private int clampMacroCatalogCacheTtlSeconds(Object value) {
        if (value == null) {
            return DEFAULT_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < MIN_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS) {
                return MIN_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS;
            }
            return Math.min(parsed, MAX_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS);
        } catch (NumberFormatException ignored) {
            return DEFAULT_MACRO_CATALOG_EXTERNAL_CACHE_TTL_SECONDS;
        }
    }

    private int clampMacroCatalogTimeout(Object value) {
        if (value == null) {
            return DEFAULT_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS;
        }
        try {
            int timeout = Integer.parseInt(String.valueOf(value).trim());
            if (timeout < MIN_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS) {
                return MIN_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS;
            }
            return Math.min(timeout, MAX_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS);
        } catch (NumberFormatException ignored) {
            return DEFAULT_MACRO_CATALOG_EXTERNAL_TIMEOUT_MS;
        }
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
        Map<String, String> placeholders = buildWorkspaceExternalLinkPlaceholders(summary, ticketId, profileEnrichment);
        String resolvedUrl = externalUrlTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        resolvedUrl = trimToNull(resolvedUrl);
        if (!StringUtils.hasText(resolvedUrl)) {
            return Map.of();
        }
        int cacheTtlSeconds = clampWorkspaceExternalProfileCacheTtl(dialogConfig.get("workspace_client_external_profile_cache_ttl_seconds"));
        Map<String, Object> cached = resolveCachedWorkspaceExternalProfile(resolvedUrl, cacheTtlSeconds);
        if (!cached.isEmpty()) {
            return cached;
        }
        int timeoutMs = clampWorkspaceExternalProfileTimeout(dialogConfig.get("workspace_client_external_profile_timeout_ms"));
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(resolvedUrl))
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs));
            applyWorkspaceExternalProfileAuthHeader(requestBuilder, dialogConfig);
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("workspace external profile fetch failed: status={} url={}", response.statusCode(), resolvedUrl);
                return resolveCachedWorkspaceExternalProfileOnFailure(resolvedUrl);
            }
            Object parsed = OBJECT_MAPPER.readValue(response.body(), new TypeReference<Object>() {});
            Map<String, Object> normalized = normalizeWorkspaceExternalProfilePayload(parsed);
            if (!normalized.isEmpty()) {
                storeWorkspaceExternalProfileCache(resolvedUrl, normalized, cacheTtlSeconds);
                return normalized;
            }
            if (parsed instanceof Map<?, ?> || parsed instanceof List<?>) {
                return resolveCachedWorkspaceExternalProfileOnFailure(resolvedUrl);
            }
            log.warn("workspace external profile fetch returned unsupported payload type: url={} type={}",
                    resolvedUrl,
                    parsed != null ? parsed.getClass().getSimpleName() : "null");
        } catch (Exception exception) {
            log.warn("workspace external profile fetch failed: url={} detail={}", resolvedUrl, exception.toString());
            return resolveCachedWorkspaceExternalProfileOnFailure(resolvedUrl);
        }
        return Map.of();
    }

    private void applyWorkspaceExternalProfileAuthHeader(HttpRequest.Builder requestBuilder,
                                                         Map<?, ?> dialogConfig) {
        if (requestBuilder == null || dialogConfig == null) {
            return;
        }
        String token = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_auth_token")));
        if (!StringUtils.hasText(token)) {
            return;
        }
        String configuredHeader = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_auth_header")));
        String headerName = StringUtils.hasText(configuredHeader) ? configuredHeader : "Authorization";
        if (!SAFE_HTTP_HEADER_NAME_PATTERN.matcher(headerName).matches()) {
            log.warn("workspace external profile auth header ignored due to unsafe header name: {}", headerName);
            return;
        }
        requestBuilder.header(headerName, token);
    }

    private Map<String, Object> normalizeWorkspaceExternalProfilePayload(Object payload) {
        Object candidate = payload;
        if (candidate instanceof Map<?, ?> map && map.get("profile") != null) {
            candidate = map.get("profile");
        }
        if (!(candidate instanceof Map<?, ?> profileMap)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        profileMap.forEach((keyRaw, valueRaw) -> {
            String key = normalizeMacroVariableKey(String.valueOf(keyRaw));
            if (!StringUtils.hasText(key) || valueRaw == null) {
                return;
            }
            normalized.put(key, valueRaw);
        });
        return normalized;
    }

    private Map<String, Object> resolveCachedWorkspaceExternalProfile(String resolvedUrl, int cacheTtlSeconds) {
        if (cacheTtlSeconds <= 0 || !resolvedUrl.equals(workspaceExternalProfileCacheUrl)) {
            return Map.of();
        }
        if (workspaceExternalProfileCacheExpiresAt != null && Instant.now().isBefore(workspaceExternalProfileCacheExpiresAt)) {
            return workspaceExternalProfileCache;
        }
        return Map.of();
    }

    private Map<String, Object> resolveCachedWorkspaceExternalProfileOnFailure(String resolvedUrl) {
        if (resolvedUrl.equals(workspaceExternalProfileCacheUrl) && !workspaceExternalProfileCache.isEmpty()) {
            log.info("workspace external profile fetch fallback to stale cache: url={} keys={}",
                    resolvedUrl,
                    workspaceExternalProfileCache.size());
            return workspaceExternalProfileCache;
        }
        return Map.of();
    }

    private void storeWorkspaceExternalProfileCache(String resolvedUrl, Map<String, Object> profile, int cacheTtlSeconds) {
        if (!StringUtils.hasText(resolvedUrl) || profile == null || profile.isEmpty() || cacheTtlSeconds <= 0) {
            return;
        }
        synchronized (WORKSPACE_EXTERNAL_PROFILE_CACHE_LOCK) {
            workspaceExternalProfileCacheUrl = resolvedUrl;
            workspaceExternalProfileCache = Map.copyOf(profile);
            workspaceExternalProfileCacheExpiresAt = Instant.now().plusSeconds(cacheTtlSeconds);
        }
    }

    private int clampWorkspaceExternalProfileTimeout(Object value) {
        if (value == null) {
            return DEFAULT_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < MIN_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS) {
                return MIN_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS;
            }
            return Math.min(parsed, MAX_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS);
        } catch (NumberFormatException ignored) {
            return DEFAULT_WORKSPACE_EXTERNAL_PROFILE_TIMEOUT_MS;
        }
    }

    private int clampWorkspaceExternalProfileCacheTtl(Object value) {
        if (value == null) {
            return DEFAULT_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < MIN_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS) {
                return MIN_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS;
            }
            return Math.min(parsed, MAX_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS);
        } catch (NumberFormatException ignored) {
            return DEFAULT_WORKSPACE_EXTERNAL_PROFILE_CACHE_TTL_SECONDS;
        }
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

    private static Map<String, String> macroVariable(String key, String label) {
        return macroVariable(key, label, null, "builtin");
    }

    private static Map<String, String> macroVariable(String key, String label, String defaultValue) {
        return macroVariable(key, label, defaultValue, "custom");
    }

    private static Map<String, String> macroVariable(String key, String label, String defaultValue, String source) {
        Map<String, String> variable = new LinkedHashMap<>();
        variable.put("key", key);
        variable.put("label", label);
        variable.put("source", StringUtils.hasText(source) ? source : "custom");
        if (StringUtils.hasText(defaultValue)) {
            variable.put("default_value", defaultValue);
        }
        return variable;
    }

    @GetMapping("/{ticketId}/workspace")
    public ResponseEntity<?> workspace(@PathVariable String ticketId,
                                       @RequestParam(value = "channelId", required = false) Long channelId,
                                       @RequestParam(value = "include", required = false) String include,
                                       @RequestParam(value = "limit", required = false) Integer limit,
                                       @RequestParam(value = "cursor", required = false) String cursor,
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
        Set<String> hiddenProfileAttributes = resolveWorkspaceHiddenClientAttributes(settings);
        Map<String, Object> filteredProfileEnrichment = filterWorkspaceProfileEnrichment(profileEnrichment, hiddenProfileAttributes);
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
        workspaceClient.put("segments", buildWorkspaceClientSegments(summary, filteredProfileEnrichment, settings));
        Map<String, Object> externalLinks = resolveWorkspaceExternalProfileLinks(settings, summary, ticketId, filteredProfileEnrichment);
        if (!externalLinks.isEmpty()) {
            workspaceClient.put("external_links", externalLinks);
        }
        Map<String, String> attributeLabels = resolveWorkspaceClientAttributeLabels(settings);
        List<String> attributeOrder = resolveWorkspaceClientAttributeOrder(settings);
        if (!attributeLabels.isEmpty()) {
            workspaceClient.put("attribute_labels", attributeLabels);
        }
        if (!attributeOrder.isEmpty()) {
            workspaceClient.put("attribute_order", attributeOrder);
        }
        if (!filteredProfileEnrichment.isEmpty()) {
            workspaceClient.putAll(filteredProfileEnrichment);
        }
        Map<String, Object> profileHealth = buildWorkspaceProfileHealth(settings, workspaceClient, attributeLabels);
        if (!profileHealth.isEmpty()) {
            workspaceClient.put("profile_health", profileHealth);
        }
        List<Map<String, Object>> contextSources = buildWorkspaceContextSources(
                settings,
                workspaceClient,
                filteredProfileEnrichment,
                externalProfileEnrichment,
                externalLinks
        );
        if (!contextSources.isEmpty()) {
            workspaceClient.put("context_sources", contextSources);
        }
        List<Map<String, Object>> attributePolicies = buildWorkspaceContextAttributePolicies(
                workspaceClient,
                profileHealth,
                contextSources
        );
        if (!attributePolicies.isEmpty()) {
            workspaceClient.put("attribute_policies", attributePolicies);
        }
        List<Map<String, Object>> contextBlocks = buildWorkspaceContextBlocks(
                settings,
                profileHealth,
                contextSources,
                clientHistory,
                relatedEvents,
                slaState,
                externalLinks
        );
        Map<String, Object> contextBlocksHealth = buildWorkspaceContextBlocksHealth(contextBlocks);
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

        Map<String, Object> workspaceRollout = resolveWorkspaceRolloutMeta(settings);
        Map<String, Object> workspaceNavigation = buildWorkspaceNavigationMeta(settings, operator, ticketId);
        Map<String, Object> workspaceSlaPolicyRaw = slaEscalationWebhookNotifier.buildRoutingPolicySnapshot(summary, settings);
        Map<String, Object> workspaceSlaPolicy = workspaceSlaPolicyRaw != null ? workspaceSlaPolicyRaw : Map.of();
        Map<String, Object> workspacePermissions = includeSections.contains("permissions")
                ? resolveWorkspacePermissions(authentication)
                : Map.of(
                "can_reply", false,
                "can_assign", false,
                "can_close", false,
                "can_snooze", false,
                "can_bulk", false,
                "unavailable", true
        );
        Map<String, Object> workspaceComposer = buildWorkspaceComposerMeta(summary, history, workspacePermissions);
        Map<String, Object> workspaceParity = buildWorkspaceParityMeta(
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

    private Map<String, Object> buildWorkspaceNavigationMeta(Map<String, Object> settings,
                                                             String operator,
                                                             String currentTicketId) {
        boolean enabled = true;
        if (settings != null && settings.get("dialog_config") instanceof Map<?, ?> dialogConfig) {
            enabled = resolveBooleanDialogConfig(dialogConfig, "workspace_inline_navigation", true);
        }
        List<DialogListItem> dialogs = dialogService.loadDialogs(operator);
        List<DialogListItem> navigationItems = dialogs == null
                ? List.of()
                : dialogs.stream()
                .filter(item -> item != null && StringUtils.hasText(item.ticketId()))
                .toList();
        int currentIndex = -1;
        for (int i = 0; i < navigationItems.size(); i++) {
            if (String.valueOf(navigationItems.get(i).ticketId()).equals(currentTicketId)) {
                currentIndex = i;
                break;
            }
        }
        DialogListItem previous = currentIndex > 0 ? navigationItems.get(currentIndex - 1) : null;
        DialogListItem next = currentIndex >= 0 && currentIndex + 1 < navigationItems.size()
                ? navigationItems.get(currentIndex + 1)
                : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("current_ticket_id", currentTicketId);
        payload.put("found_in_queue", currentIndex >= 0);
        payload.put("position", currentIndex >= 0 ? currentIndex + 1 : null);
        payload.put("total", navigationItems.size());
        payload.put("has_previous", previous != null);
        payload.put("has_next", next != null);
        payload.put("previous", buildWorkspaceNavigationItem(previous));
        payload.put("next", buildWorkspaceNavigationItem(next));
        payload.put("queue_generated_at_utc", Instant.now().toString());
        payload.put("summary", buildWorkspaceNavigationSummary(enabled, currentIndex, navigationItems.size(), previous, next));
        return payload;
    }

    private Map<String, Object> buildWorkspaceNavigationItem(DialogListItem item) {
        if (item == null || !StringUtils.hasText(item.ticketId())) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", item.ticketId());
        payload.put("channel_id", item.channelId());
        payload.put("client_name", item.displayClientName());
        payload.put("status", item.statusLabel());
        payload.put("last_message_at_utc", normalizeUtcTimestamp(item.lastMessageTimestamp()));
        return payload;
    }

    private String buildWorkspaceNavigationSummary(boolean enabled,
                                                   int currentIndex,
                                                   int total,
                                                   DialogListItem previous,
                                                   DialogListItem next) {
        if (!enabled) {
            return "Inline navigation отключена текущей настройкой rollout.";
        }
        if (currentIndex < 0 || total <= 0) {
            return "Текущий диалог открыт вне активной очереди — inline navigation недоступна.";
        }
        if (previous == null && next == null) {
            return "В очереди только текущий диалог.";
        }
        return "Позиция %d из %d. %s%s".formatted(
                currentIndex + 1,
                total,
                previous != null ? "Есть предыдущий диалог. " : "",
                next != null ? "Есть следующий диалог." : ""
        ).trim();
    }

    private Map<String, Object> buildWorkspaceParityMeta(Set<String> includeSections,
                                                         Map<String, Object> workspaceClient,
                                                         List<Map<String, Object>> clientHistory,
                                                         List<Map<String, Object>> relatedEvents,
                                                         Map<String, Object> profileHealth,
                                                         Map<String, Object> contextBlocksHealth,
                                                         Map<String, Object> permissions,
                                                         Map<String, Object> composer,
                                                         String slaState,
                                                         DialogListItem summary,
                                                         Map<String, Object> workspaceRollout) {
        List<Map<String, Object>> checks = new ArrayList<>();
        Instant checkedAt = Instant.now();

        boolean messagesReady = includeSections.contains("messages");
        checks.add(buildWorkspaceParityCheck(
                "messages_timeline",
                "Лента сообщений загружена в workspace",
                messagesReady ? "ok" : "attention",
                messagesReady ? "Основная лента доступна без перехода в legacy modal." : "Контракт workspace запрошен без секции messages.",
                checkedAt
        ));

        boolean customerContextReady = workspaceClient != null && !workspaceClient.isEmpty();
        checks.add(buildWorkspaceParityCheck(
                "customer_context",
                "Контекст клиента доступен в workspace",
                customerContextReady ? "ok" : "attention",
                customerContextReady ? "Карточка клиента доступна в основном workspace-потоке." : "Контекст клиента не загрузился.",
                checkedAt
        ));

        boolean profileReady = !toBoolean(profileHealth != null ? profileHealth.get("enabled") : null)
                || toBoolean(profileHealth.get("ready"));
        checks.add(buildWorkspaceParityCheck(
                "customer_profile_minimum",
                "Минимальный customer profile готов",
                profileReady ? "ok" : "attention",
                profileReady ? "Контекст достаточен для решения без переключения в сторонние экраны." : "Есть обязательные profile gaps — нужен дозаполняющий контекст.",
                checkedAt
        ));

        boolean contextBlocksReady = !toBoolean(contextBlocksHealth != null ? contextBlocksHealth.get("enabled") : null)
                || toBoolean(contextBlocksHealth.get("ready"));
        checks.add(buildWorkspaceParityCheck(
                "customer_context_blocks",
                "Контекстные блоки стандартизированы по приоритету",
                contextBlocksReady ? "ok" : "attention",
                contextBlocksReady
                        ? "Приоритетные блоки customer context готовы и не требуют переключения между экранами."
                        : "Есть обязательные context-block gaps — приоритетные блоки ещё не готовы.",
                checkedAt
        ));

        boolean historyReady = includeSections.contains("context") && clientHistory != null;
        checks.add(buildWorkspaceParityCheck(
                "history_context",
                "История клиента доступна",
                historyReady ? "ok" : "attention",
                historyReady ? "Оператор видит историю клиента в workspace." : "Не удалось загрузить клиентскую историю.",
                checkedAt
        ));

        boolean relatedEventsReady = includeSections.contains("context") && relatedEvents != null;
        checks.add(buildWorkspaceParityCheck(
                "related_events",
                "Связанные события доступны",
                relatedEventsReady ? "ok" : "attention",
                relatedEventsReady ? "Связанные события доступны в правой колонке workspace." : "Связанные события недоступны.",
                checkedAt
        ));

        boolean slaReady = includeSections.contains("sla") && StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState);
        checks.add(buildWorkspaceParityCheck(
                "sla_visibility",
                "SLA-контекст доступен",
                slaReady ? "ok" : "attention",
                slaReady ? "SLA состояние и дедлайн доступны в workspace." : "SLA-контекст неполный или недоступен.",
                checkedAt
        ));

        boolean actionControlsReady = permissions != null
                && permissions.get("can_reply") instanceof Boolean
                && permissions.get("can_assign") instanceof Boolean
                && permissions.get("can_close") instanceof Boolean
                && permissions.get("can_snooze") instanceof Boolean;
        checks.add(buildWorkspaceParityCheck(
                "operator_actions",
                "Операторские действия доступны по контракту",
                actionControlsReady ? "ok" : "blocked",
                actionControlsReady ? "Workspace знает, какие действия можно выполнять." : "Права оператора невалидны — parity с legacy неполный.",
                checkedAt
        ));

        boolean replyThreadingReady = composer != null
                && Boolean.TRUE.equals(composer.get("reply_target_supported"))
                && Boolean.TRUE.equals(composer.get("reply_supported"));
        checks.add(buildWorkspaceParityCheck(
                "reply_threading",
                "Ответ на конкретное сообщение доступен в workspace",
                replyThreadingReady ? "ok" : "attention",
                replyThreadingReady ? "Оператор может отвечать на конкретное сообщение без перехода в legacy modal." : "Reply-threading в workspace недоступен или контракт композера неполный.",
                checkedAt
        ));

        boolean mediaReplyReady = composer != null
                && Boolean.TRUE.equals(composer.get("media_supported"))
                && Boolean.TRUE.equals(composer.get("reply_supported"));
        checks.add(buildWorkspaceParityCheck(
                "media_reply",
                "Отправка медиа доступна в workspace",
                mediaReplyReady ? "ok" : "attention",
                mediaReplyReady ? "Медиа-ответы доступны напрямую из workspace composer." : "Медиа-ответы в workspace недоступны по текущему контракту.",
                checkedAt
        ));

        String rolloutMode = workspaceRollout != null ? String.valueOf(workspaceRollout.getOrDefault("mode", "")) : "";
        boolean workspacePrimary = StringUtils.hasText(rolloutMode) && !"legacy_primary".equalsIgnoreCase(rolloutMode);
        checks.add(buildWorkspaceParityCheck(
                "workspace_primary_flow",
                "Workspace остаётся основным рабочим потоком",
                workspacePrimary ? "ok" : "attention",
                workspacePrimary ? "Legacy modal рассматривается как rollback-механизм." : "Legacy modal всё ещё основной режим.",
                checkedAt
        ));

        long okChecks = checks.stream().filter(item -> "ok".equals(item.get("status"))).count();
        long blockedChecks = checks.stream().filter(item -> "blocked".equals(item.get("status"))).count();
        int scorePct = checks.isEmpty() ? 100 : (int) Math.round((okChecks * 100d) / checks.size());
        List<String> missingKeys = checks.stream()
                .filter(item -> !"ok".equals(item.get("status")))
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        List<String> missingLabels = checks.stream()
                .filter(item -> !"ok".equals(item.get("status")))
                .map(item -> String.valueOf(item.get("label")))
                .toList();

        String status;
        if (blockedChecks > 0 || scorePct < 60) {
            status = "blocked";
        } else if (!missingKeys.isEmpty()) {
            status = "attention";
        } else {
            status = "ok";
        }

        String summaryText;
        if ("ok".equals(status)) {
            summaryText = "Workspace покрывает ключевые ежедневные операторские сценарии без видимого parity-gap.";
        } else if ("blocked".equals(status)) {
            summaryText = "Есть критичный parity-gap: часть operator-flow ещё не может считаться production-grade без legacy fallback.";
        } else {
            summaryText = "Есть неполный parity с legacy: workspace покрывает основной поток, но требует дозакрытия нескольких сценариев.";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("score_pct", Math.max(0, Math.min(100, scorePct)));
        payload.put("checked_at", checkedAt.toString());
        payload.put("summary", summaryText);
        payload.put("ticket_id", summary != null ? summary.ticketId() : null);
        payload.put("missing_capabilities", missingKeys);
        payload.put("missing_labels", missingLabels);
        payload.put("checks", checks);
        return payload;
    }

    private Map<String, Object> buildWorkspaceComposerMeta(DialogListItem summary,
                                                           List<ChatMessageDto> history,
                                                           Map<String, Object> permissions) {
        boolean canReply = permissions != null && Boolean.TRUE.equals(permissions.get("can_reply"));
        boolean hasReplyTargets = history != null && history.stream().anyMatch(message -> message != null && message.telegramMessageId() != null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reply_supported", canReply);
        payload.put("media_supported", canReply);
        payload.put("reply_target_supported", canReply && hasReplyTargets);
        payload.put("draft_supported", true);
        payload.put("channel_id", summary != null ? summary.channelId() : null);
        payload.put("channel_label", summary != null ? summary.channelLabel() : null);
        payload.put("timezone", "UTC");
        return payload;
    }

    private Map<String, Object> buildWorkspaceParityCheck(String key,
                                                          String label,
                                                          String status,
                                                          String detail,
                                                          Instant checkedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("status", normalizeWorkspaceParityStatus(status));
        item.put("detail", StringUtils.hasText(detail) ? detail.trim() : null);
        item.put("checked_at", checkedAt != null ? checkedAt.toString() : Instant.now().toString());
        return item;
    }

    private String normalizeWorkspaceParityStatus(String status) {
        String normalized = status != null ? status.trim().toLowerCase() : "";
        return switch (normalized) {
            case "ok", "attention", "blocked" -> normalized;
            default -> "attention";
        };
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

    private Map<String, Object> resolveWorkspaceRolloutMeta(Map<String, Object> settings) {
        Object dialogConfigRaw = settings != null ? settings.get("dialog_config") : null;
        Map<?, ?> dialogConfig = dialogConfigRaw instanceof Map<?, ?> map ? map : Map.of();

        boolean workspaceEnabled = resolveBooleanDialogConfig(dialogConfig, "workspace_v1", true);
        boolean workspaceSingleMode = resolveBooleanDialogConfig(dialogConfig, "workspace_single_mode", false);
        boolean forceWorkspace = resolveBooleanDialogConfig(dialogConfig, "workspace_force_workspace", false) || workspaceSingleMode;
        boolean decommissionLegacyModal = resolveBooleanDialogConfig(dialogConfig, "workspace_decommission_legacy_modal", false) || workspaceSingleMode;
        boolean disableLegacyFallback = resolveBooleanDialogConfig(dialogConfig, "workspace_disable_legacy_fallback", false)
                || forceWorkspace
                || decommissionLegacyModal;
        boolean abEnabled = resolveBooleanDialogConfig(dialogConfig, "workspace_ab_enabled", false) && !workspaceSingleMode;
        int rolloutPercent = resolveIntegerDialogConfig(dialogConfig, "workspace_ab_rollout_percent", 0, 0, 100);
        String experimentName = trimToNull(String.valueOf(dialogConfig.get("workspace_ab_experiment_name")));
        String operatorSegment = trimToNull(String.valueOf(dialogConfig.get("workspace_ab_operator_segment")));
        OffsetDateTime reviewedAtUtc = parseUtcTimestamp(trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_external_kpi_reviewed_at"))));
        OffsetDateTime dataUpdatedAtUtc = parseUtcTimestamp(trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_external_kpi_data_updated_at"))));
        boolean legacyManualOpenPolicyEnabled = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_policy_enabled", false);
        boolean legacyManualOpenReasonRequired = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_reason_required", true);
        boolean legacyManualOpenBlockOnHold = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_block_on_hold", false);
        boolean legacyManualOpenBlockOnStaleReview = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_block_on_stale_review", false);
        int legacyManualOpenReviewTtlHours = resolveIntegerDialogConfig(dialogConfig,
                "workspace_rollout_legacy_manual_open_review_ttl_hours", 168, 1, 24 * 60);
        List<String> legacyManualAllowedReasons = safeStringList(dialogConfig.get("workspace_rollout_legacy_manual_open_allowed_reasons"));
        boolean legacyManualReasonCatalogRequired = resolveBooleanDialogConfig(dialogConfig, "workspace_rollout_legacy_manual_open_reason_catalog_required", false);
        String legacyUsageDecision = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_decision")));
        if (legacyUsageDecision != null) {
            legacyUsageDecision = legacyUsageDecision.toLowerCase(Locale.ROOT);
        }
        String legacyUsageReviewedBy = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_reviewed_by")));
        String legacyUsageReviewNote = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_review_note")));
        String legacyUsageReviewedAtRaw = trimToNull(String.valueOf(dialogConfig.get("workspace_rollout_governance_legacy_usage_reviewed_at")));
        OffsetDateTime legacyUsageReviewedAtUtc = parseUtcTimestamp(legacyUsageReviewedAtRaw);
        boolean legacyUsageReviewInvalidUtc = StringUtils.hasText(legacyUsageReviewedAtRaw) && legacyUsageReviewedAtUtc == null;
        Long legacyUsageReviewAgeHours = null;
        if (legacyUsageReviewedAtUtc != null) {
            long hours = Duration.between(legacyUsageReviewedAtUtc.toInstant(), Instant.now()).toHours();
            legacyUsageReviewAgeHours = Math.max(0L, hours);
        }
        boolean legacyUsageReviewStale = legacyManualOpenPolicyEnabled
                && legacyManualOpenBlockOnStaleReview
                && (legacyUsageReviewedAtUtc == null || legacyUsageReviewAgeHours == null || legacyUsageReviewAgeHours > legacyManualOpenReviewTtlHours);
        boolean legacyUsageDecisionHold = legacyManualOpenPolicyEnabled
                && legacyManualOpenBlockOnHold
                && "hold".equalsIgnoreCase(legacyUsageDecision);
        boolean legacyManualOpenBlocked = legacyUsageDecisionHold || legacyUsageReviewStale || legacyUsageReviewInvalidUtc;
        String legacyManualOpenBlockReason = null;
        if (legacyManualOpenBlocked) {
            if (legacyUsageReviewInvalidUtc) {
                legacyManualOpenBlockReason = "invalid_review_timestamp";
            } else if (legacyUsageDecisionHold) {
                legacyManualOpenBlockReason = "review_decision_hold";
            } else if (legacyUsageReviewStale) {
                legacyManualOpenBlockReason = "stale_review";
            }
        }
        String mode;
        String bannerTone;
        if (!workspaceEnabled) {
            mode = "legacy_primary";
            bannerTone = "warning";
        } else if (workspaceSingleMode) {
            mode = "workspace_single_mode";
            bannerTone = "success";
        } else if (forceWorkspace || decommissionLegacyModal || !abEnabled) {
            mode = "workspace_primary";
            bannerTone = disableLegacyFallback ? "success" : "info";
        } else {
            mode = "cohort_rollout";
            bannerTone = disableLegacyFallback ? "warning" : "info";
        }

        String summary;
        if (!workspaceEnabled) {
            summary = "Workspace выключен: используется legacy modal.";
        } else if (workspaceSingleMode) {
            summary = "Workspace-only режим включён: legacy modal отключён, fallback недоступен, A/B rollout выключен.";
        } else if (disableLegacyFallback) {
            summary = "Workspace — основной режим. Auto-fallback в legacy отключён текущим rollout-режимом.";
        } else if (abEnabled) {
            summary = "Workspace включён в cohort-rollout; legacy modal остаётся fallback-механизмом.";
        } else {
            summary = "Workspace — основной режим. Legacy modal оставлен как rollback-механизм.";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspace_enabled", workspaceEnabled);
        payload.put("workspace_single_mode", workspaceSingleMode);
        payload.put("mode", mode);
        payload.put("banner_tone", bannerTone);
        payload.put("summary", summary);
        payload.put("force_workspace", forceWorkspace);
        payload.put("decommission_legacy_modal", decommissionLegacyModal);
        payload.put("legacy_fallback_available", workspaceEnabled && !disableLegacyFallback);
        payload.put("disable_legacy_fallback", disableLegacyFallback);
        payload.put("ab_enabled", abEnabled);
        payload.put("rollout_percent", rolloutPercent);
        payload.put("experiment_name", experimentName != null ? experimentName : "");
        payload.put("operator_segment", operatorSegment != null ? operatorSegment : "");
        Map<String, Object> legacyManualOpenPolicy = new LinkedHashMap<>();
        legacyManualOpenPolicy.put("enabled", legacyManualOpenPolicyEnabled);
        legacyManualOpenPolicy.put("reason_required", legacyManualOpenReasonRequired);
        legacyManualOpenPolicy.put("block_on_hold", legacyManualOpenBlockOnHold);
        legacyManualOpenPolicy.put("block_on_stale_review", legacyManualOpenBlockOnStaleReview);
        legacyManualOpenPolicy.put("review_ttl_hours", legacyManualOpenReviewTtlHours);
        legacyManualOpenPolicy.put("reviewed_by", legacyUsageReviewedBy != null ? legacyUsageReviewedBy : "");
        legacyManualOpenPolicy.put("review_note", legacyUsageReviewNote != null ? legacyUsageReviewNote : "");
        legacyManualOpenPolicy.put("review_timestamp_invalid", legacyUsageReviewInvalidUtc);
                legacyManualOpenPolicy.put("review_age_hours", legacyUsageReviewAgeHours == null ? "" : legacyUsageReviewAgeHours);
        legacyManualOpenPolicy.put("decision", legacyUsageDecision != null ? legacyUsageDecision : "");
        legacyManualOpenPolicy.put("allowed_reasons", legacyManualAllowedReasons);
        legacyManualOpenPolicy.put("reason_catalog_required", legacyManualReasonCatalogRequired);
        legacyManualOpenPolicy.put("blocked", legacyManualOpenBlocked);
        legacyManualOpenPolicy.put("block_reason", legacyManualOpenBlockReason != null ? legacyManualOpenBlockReason : "");
        if (legacyUsageReviewedAtUtc != null) {
            legacyManualOpenPolicy.put("reviewed_at_utc", legacyUsageReviewedAtUtc.toString());
        }
        payload.put("legacy_manual_open_policy", legacyManualOpenPolicy);
        if (reviewedAtUtc != null) {
            payload.put("reviewed_at_utc", reviewedAtUtc.toString());
        }
        if (dataUpdatedAtUtc != null) {
            payload.put("data_updated_at_utc", dataUpdatedAtUtc.toString());
        }
        return payload;
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

    private List<String> buildWorkspaceClientSegments(DialogListItem summary,
                                                      Map<String, Object> profileEnrichment,
                                                      Map<String, Object> settings) {
        if (summary == null) {
            return List.of();
        }
        List<String> segments = new java.util.ArrayList<>();
        if (summary.unreadCount() != null && summary.unreadCount() > 0) {
            segments.add("needs_reply");
        }
        if (summary.responsible() == null || summary.responsible().isBlank()) {
            segments.add("unassigned");
        }
        if (summary.rating() != null && summary.rating() > 0 && summary.rating() <= 2) {
            segments.add("low_csat_risk");
        }
        if ("new".equals(summary.statusKey())) {
            segments.add("new_dialog");
        }

        int totalDialogs = parseInteger(profileEnrichment != null ? profileEnrichment.get("total_dialogs") : null);
        int openDialogs = parseInteger(profileEnrichment != null ? profileEnrichment.get("open_dialogs") : null);
        int resolved30d = parseInteger(profileEnrichment != null ? profileEnrichment.get("resolved_30d") : null);

        int highLifetimeDialogsThreshold = resolveDialogConfigRangeMinutes(
                settings,
                "workspace_segment_high_lifetime_volume_min_dialogs",
                5,
                1,
                500
        );
        int multiOpenDialogsThreshold = resolveDialogConfigRangeMinutes(
                settings,
                "workspace_segment_multi_open_dialogs_min_open",
                2,
                1,
                50
        );
        int reactivationDialogsThreshold = resolveDialogConfigRangeMinutes(
                settings,
                "workspace_segment_reactivation_risk_min_dialogs",
                3,
                1,
                500
        );
        int reactivationResolvedThreshold = resolveDialogConfigRangeMinutes(
                settings,
                "workspace_segment_reactivation_risk_max_resolved_30d",
                0,
                0,
                100
        );
        int openBacklogMinOpenThreshold = resolveDialogConfigRangeMinutes(
                settings,
                "workspace_segment_open_backlog_min_open",
                3,
                1,
                100
        );
        int openBacklogMinSharePercent = resolveDialogConfigRangeMinutes(
                settings,
                "workspace_segment_open_backlog_min_share_percent",
                50,
                1,
                100
        );

        if (totalDialogs >= highLifetimeDialogsThreshold) {
            segments.add("high_lifetime_volume");
        }
        if (openDialogs >= multiOpenDialogsThreshold) {
            segments.add("multi_open_dialogs");
        }
        if (totalDialogs >= reactivationDialogsThreshold && resolved30d <= reactivationResolvedThreshold) {
            segments.add("reactivation_risk");
        }
        if (openDialogs >= openBacklogMinOpenThreshold && totalDialogs > 0) {
            int openSharePercent = Math.round((openDialogs * 100f) / totalDialogs);
            if (openSharePercent >= openBacklogMinSharePercent) {
                segments.add("open_backlog_pressure");
            }
        }
        return segments;
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

    private int parseInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
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

    private Map<String, String> buildMacroVariables(String ticketId,
                                                    String operator,
                                                    Map<String, String> requestVariables) {
        Map<String, String> variables = new LinkedHashMap<>();
        if (requestVariables != null) {
            requestVariables.forEach((key, value) -> {
                if (!StringUtils.hasText(key) || value == null) {
                    return;
                }
                variables.put(key.trim().toLowerCase(), value);
            });
        }

        String safeTicketId = StringUtils.hasText(ticketId) ? ticketId.trim() : null;
        if (safeTicketId != null) {
            dialogService.loadDialogDetails(safeTicketId, null, operator).ifPresent(details -> {
                DialogListItem summary = details.summary();
                putMacroVariableIfAbsent(variables, "client_name", summary.displayClientName());
                putMacroVariableIfAbsent(variables, "ticket_id", summary.ticketId());
                putMacroVariableIfAbsent(variables, "operator_name", operator);
                putMacroVariableIfAbsent(variables, "channel_name", summary.channelLabel());
                putMacroVariableIfAbsent(variables, "business", summary.businessLabel());
                putMacroVariableIfAbsent(variables, "location", summary.location());
                putMacroVariableIfAbsent(variables, "dialog_status", summary.statusLabel());
                putMacroVariableIfAbsent(variables, "created_at", summary.createdAt());
                Map<String, Object> profileEnrichment = dialogService.loadClientProfileEnrichment(summary.userId());
                putMacroVariablesFromClientProfile(variables, profileEnrichment);
            });
        }
        putMacroVariableIfAbsent(variables, "ticket_id", safeTicketId != null ? safeTicketId : "—");
        putMacroVariableIfAbsent(variables, "operator_name", StringUtils.hasText(operator) ? operator.trim() : "оператор");
        Map<String, String> configuredDefaults = resolveConfiguredMacroVariableDefaults();
        configuredDefaults.forEach((key, value) -> putMacroVariableIfAbsent(variables, key, value));
        putMacroVariableIfAbsent(variables, "current_date", MACRO_DATE_FORMATTER.format(Instant.now()));
        putMacroVariableIfAbsent(variables, "current_time", MACRO_TIME_FORMATTER.format(Instant.now()));
        return variables;
    }

    private void putMacroVariablesFromClientProfile(Map<String, String> variables,
                                                    Map<String, Object> profileEnrichment) {
        if (profileEnrichment == null || profileEnrichment.isEmpty()) {
            return;
        }
        putMacroVariableIfAbsent(variables, "client_total_dialogs", stringifyMacroVariableValue(profileEnrichment.get("total_dialogs")));
        putMacroVariableIfAbsent(variables, "client_open_dialogs", stringifyMacroVariableValue(profileEnrichment.get("open_dialogs")));
        putMacroVariableIfAbsent(variables, "client_resolved_30d", stringifyMacroVariableValue(profileEnrichment.get("resolved_30d")));
        putMacroVariableIfAbsent(variables, "client_avg_rating", stringifyMacroVariableValue(profileEnrichment.get("avg_rating")));
        putMacroVariableIfAbsent(variables, "client_segment_list", stringifyMacroVariableValue(profileEnrichment.get("segments")));

        profileEnrichment.forEach((key, value) -> {
            String normalizedKey = normalizeClientProfileMacroKey(key);
            if (!StringUtils.hasText(normalizedKey)) {
                return;
            }
            putMacroVariableIfAbsent(variables, "client_" + normalizedKey, stringifyMacroVariableValue(value));
        });
    }

    private String normalizeClientProfileMacroKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String normalized = key.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
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

    private Map<String, String> resolveConfiguredMacroVariableDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object configured = resolveMacroVariableCatalog(settings);
        if (!(configured instanceof List<?> entries)) {
            defaults.putAll(resolveDialogConfigMacroVariableDefaults(settings));
            return defaults;
        }
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            String key = map.get("key") != null ? String.valueOf(map.get("key")).trim().toLowerCase() : "";
            String defaultValue = map.get("default_value") != null ? String.valueOf(map.get("default_value")).trim() : "";
            if (StringUtils.hasText(key) && StringUtils.hasText(defaultValue)) {
                defaults.putIfAbsent(key, defaultValue);
            }
        }
        resolveDialogConfigMacroVariableDefaults(settings)
                .forEach(defaults::putIfAbsent);
        return defaults;
    }

    private Object resolveMacroVariableCatalog(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        Object configured = settings.get("macro_variable_catalog");
        Object dialogConfigRaw = settings.get("dialog_config");
        if (dialogConfigRaw instanceof Map<?, ?> dialogConfig && dialogConfig.get("macro_variable_catalog") instanceof List<?>) {
            configured = dialogConfig.get("macro_variable_catalog");
        }
        return configured;
    }

    private Map<String, String> resolveDialogConfigMacroVariableDefaults(Map<String, Object> settings) {
        Map<String, String> defaults = new LinkedHashMap<>();
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return defaults;
        }
        Object defaultsRaw = dialogConfig.get("macro_variable_defaults");
        if (!(defaultsRaw instanceof Map<?, ?> configuredDefaults)) {
            return defaults;
        }
        configuredDefaults.forEach((keyRaw, valueRaw) -> {
            String key = keyRaw != null ? String.valueOf(keyRaw).trim().toLowerCase() : "";
            String value = valueRaw != null ? String.valueOf(valueRaw).trim() : "";
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                defaults.putIfAbsent(key, value);
            }
        });
        return defaults;
    }

    private void putMacroVariableIfAbsent(Map<String, String> variables, String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value) || variables.containsKey(key)) {
            return;
        }
        variables.put(key, value);
    }

    private MacroDryRunResult renderMacroTemplate(String templateText, Map<String, String> variables) {
        Matcher matcher = MACRO_VARIABLE_PATTERN.matcher(templateText);
        StringBuilder rendered = new StringBuilder();
        List<String> usedVariables = new ArrayList<>();
        List<String> missingVariables = new ArrayList<>();
        int previousEnd = 0;
        while (matcher.find()) {
            rendered.append(templateText, previousEnd, matcher.start());
            String key = String.valueOf(matcher.group(1)).trim().toLowerCase();
            String fallback = matcher.group(2);
            String value = variables.get(key);
            if (value != null) {
                rendered.append(value);
                if (!usedVariables.contains(key)) {
                    usedVariables.add(key);
                }
            } else if (StringUtils.hasText(fallback)) {
                rendered.append(fallback.trim());
                if (!missingVariables.contains(key)) {
                    missingVariables.add(key);
                }
            } else {
                rendered.append(matcher.group());
                if (!missingVariables.contains(key)) {
                    missingVariables.add(key);
                }
            }
            previousEnd = matcher.end();
        }
        rendered.append(templateText.substring(previousEnd));
        return new MacroDryRunResult(rendered.toString().trim(), usedVariables, missingVariables);
    }

    private record MacroDryRunResult(String renderedText, List<String> usedVariables, List<String> missingVariables) {
    }


    @PostMapping("/workspace-telemetry")
    public ResponseEntity<?> workspaceTelemetry(@RequestBody(required = false) WorkspaceTelemetryRequest request,
                                                Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "anonymous";
        if (request == null || request.eventType() == null || request.eventType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "event_type is required"));
        }
        String normalizedEventType = request.eventType().trim().toLowerCase();
        String eventGroup = WORKSPACE_TELEMETRY_EVENT_GROUPS.get(normalizedEventType);
        if (eventGroup == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "unsupported event_type",
                    "allowed_event_types", WORKSPACE_TELEMETRY_EVENT_GROUPS.keySet()));
        }
        log.info("Workspace telemetry: actor='{}', event='{}', group='{}', ticket='{}', reason='{}', error='{}', contract='{}', durationMs={}, experiment='{}', cohort='{}', segment='{}', primaryKpis='{}', secondaryKpis='{}', templateId='{}', templateName='{}'",
                operator,
                normalizedEventType,
                eventGroup,
                request.ticketId(),
                request.reason(),
                request.errorCode(),
                request.contractVersion(),
                request.durationMs(),
                request.experimentName(),
                request.experimentCohort(),
                request.operatorSegment(),
                request.primaryKpis(),
                request.secondaryKpis(),
                request.templateId(),
                request.templateName());
        dialogService.logWorkspaceTelemetry(
                operator,
                normalizedEventType,
                eventGroup,
                request.ticketId(),
                request.reason(),
                request.errorCode(),
                request.contractVersion(),
                request.durationMs(),
                request.experimentName(),
                request.experimentCohort(),
                request.operatorSegment(),
                request.primaryKpis(),
                request.secondaryKpis(),
                request.templateId(),
                request.templateName());
        maybeAuditMacroUsage(operator, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void maybeAuditMacroUsage(String operator, WorkspaceTelemetryRequest request) {
        if (request == null || !"macro_apply".equalsIgnoreCase(String.valueOf(request.eventType()))) {
            return;
        }
        if (!StringUtils.hasText(request.ticketId())) {
            return;
        }
        StringBuilder detail = new StringBuilder("Macro applied from workspace telemetry");
        if (StringUtils.hasText(request.templateName())) {
            detail.append(": ").append(request.templateName().trim());
        }
        if (StringUtils.hasText(request.templateId())) {
            detail.append(" [").append(request.templateId().trim()).append("]");
        }
        dialogService.logDialogActionAudit(
                request.ticketId(),
                operator,
                "macro_apply",
                "success",
                detail.toString());
    }

    @GetMapping("/workspace-telemetry/summary")
    public ResponseEntity<?> workspaceTelemetrySummary(@RequestParam(name = "days", defaultValue = "7") Integer days,
                                                       @RequestParam(name = "experiment_name", required = false) String experimentName,
                                                       @RequestParam(name = "from_utc", required = false) String fromUtcRaw,
                                                       @RequestParam(name = "to_utc", required = false) String toUtcRaw) {
        int safeDays = days != null ? days : 7;
        String fromUtcValue = trimToNull(fromUtcRaw);
        String toUtcValue = trimToNull(toUtcRaw);
        boolean explicitWindowRequested = fromUtcValue != null || toUtcValue != null;
        OffsetDateTime fromUtc = null;
        OffsetDateTime toUtc = null;
        if (fromUtcValue != null) {
            fromUtc = parseUtcTimestamp(fromUtcValue);
            if (fromUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "from_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }
        if (toUtcValue != null) {
            toUtc = parseUtcTimestamp(toUtcValue);
            if (toUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "to_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }
        if (fromUtc != null && toUtc != null && !fromUtc.isBefore(toUtc)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "from_utc must be earlier than to_utc"));
        }

        Map<String, Object> payload = explicitWindowRequested
                ? new LinkedHashMap<>(dialogService.loadWorkspaceTelemetrySummary(
                safeDays,
                experimentName,
                fromUtc != null ? fromUtc.toInstant() : null,
                toUtc != null ? toUtc.toInstant() : null))
                : new LinkedHashMap<>(dialogService.loadWorkspaceTelemetrySummary(safeDays, experimentName));
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> slaPolicyAudit = slaEscalationWebhookNotifier.buildRoutingGovernanceAudit(
                dialogService.loadDialogs(null),
                settings);
        Map<String, Object> macroGovernanceAudit = dialogService.buildMacroGovernanceAudit(settings);
        payload.put("sla_policy_audit", slaPolicyAudit != null ? slaPolicyAudit : Map.of());
        payload.put("macro_governance_audit", macroGovernanceAudit);
        payload.put("p1_operational_control", buildP1OperationalControl(payload));
        payload.put("sla_review_path_control", buildSlaReviewPathControl(payload, slaPolicyAudit));
        payload.put("p2_governance_control", buildP2GovernanceControl(payload, slaPolicyAudit, macroGovernanceAudit));
        payload.put("weekly_review_focus", buildWorkspaceWeeklyReviewFocus(payload, slaPolicyAudit, macroGovernanceAudit));
        payload.put("success", true);
        return ResponseEntity.ok(payload);
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

    @GetMapping("/triage-preferences")
    public ResponseEntity<?> triagePreferences(Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        if (!StringUtils.hasText(operator)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Требуется авторизация"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("preferences", loadOperatorTriagePreferences(operator));
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/triage-preferences")
    public ResponseEntity<?> updateTriagePreferences(@RequestBody(required = false) TriagePreferencesRequest request,
                                                     Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        if (!StringUtils.hasText(operator)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Требуется авторизация"));
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "request body is required"));
        }
        String view = normalizeDialogView(request.view());
        String sortMode = normalizeSortMode(request.sortMode());
        Integer slaWindowMinutes = normalizeSlaWindowMinutes(request.slaWindowMinutes());
        String pageSize = normalizePageSizePreference(request.pageSize());

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>(OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {}))
                : new LinkedHashMap<>();
        Map<String, Object> byOperator = dialogConfig.get("workspace_triage_preferences_by_operator") instanceof Map<?, ?> map
                ? new LinkedHashMap<>(OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {}))
                : new LinkedHashMap<>();
        Map<String, Object> operatorPreferences = byOperator.get(operator) instanceof Map<?, ?> map
                ? new LinkedHashMap<>(OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {}))
                : new LinkedHashMap<>();
        operatorPreferences.put("view", view);
        operatorPreferences.put("sort_mode", sortMode);
        if (slaWindowMinutes != null) {
            operatorPreferences.put("sla_window_minutes", slaWindowMinutes);
        } else {
            operatorPreferences.remove("sla_window_minutes");
        }
        operatorPreferences.put("page_size", pageSize);
        String updatedAtUtc = Instant.now().toString();
        operatorPreferences.put("updated_at_utc", updatedAtUtc);
        byOperator.put(operator, operatorPreferences);
        dialogConfig.put("workspace_triage_preferences_by_operator", byOperator);
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogService.logWorkspaceTelemetry(
                operator,
                "triage_preferences_saved",
                "triage",
                null,
                "view=%s;sort=%s;sla=%s;page=%s".formatted(
                        view,
                        sortMode,
                        slaWindowMinutes != null ? slaWindowMinutes : "all",
                        pageSize),
                null,
                "triage_preferences.v1",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("preferences", loadOperatorTriagePreferences(operator));
        payload.put("updated_at_utc", updatedAtUtc);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<?> reply(@PathVariable String ticketId,
                                   @RequestBody DialogReplyRequest request,
                                   Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "reply", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        dialogAiAssistantService.clearProcessing(ticketId, "operator_reply", null);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(
                ticketId,
                request.message(),
                request.replyToTelegramId(),
                operator
        );
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        notificationService.notifyDialogParticipants(
                ticketId,
                "Новое сообщение в обращении " + ticketId,
                "/dialogs?ticketId=" + ticketId,
                operator
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "timestamp", result.timestamp(),
                "telegramMessageId", result.telegramMessageId(),
                "responsible", operator
        ));
    }


    @PostMapping("/{ticketId}/edit")
    public ResponseEntity<?> editMessage(@PathVariable String ticketId,
                                         @RequestBody DialogEditRequest request,
                                         Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "edit", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogReplyService.editOperatorMessage(
                ticketId,
                request.telegramMessageId(),
                request.message(),
                operator
        );
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        notificationService.notifyDialogParticipants(
                ticketId,
                "Сообщение в обращении " + ticketId + " было отредактировано",
                "/dialogs?ticketId=" + ticketId,
                operator
        );
        return ResponseEntity.ok(Map.of("success", true, "timestamp", result.timestamp()));
    }

    @PostMapping("/{ticketId}/delete")
    public ResponseEntity<?> deleteMessage(@PathVariable String ticketId,
                                           @RequestBody DialogDeleteRequest request,
                                           Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "delete", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogReplyService.DialogReplyResult result = dialogReplyService.deleteOperatorMessage(
                ticketId,
                request.telegramMessageId(),
                operator
        );
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        notificationService.notifyDialogParticipants(
                ticketId,
                "Сообщение в обращении " + ticketId + " было удалено",
                "/dialogs?ticketId=" + ticketId,
                operator
        );
        return ResponseEntity.ok(Map.of("success", true, "timestamp", result.timestamp()));
    }

    @PostMapping(value = "/{ticketId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replyWithMedia(@PathVariable String ticketId,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "message", required = false) String message,
                                            Authentication authentication) throws IOException {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "reply_media", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        dialogAiAssistantService.clearProcessing(ticketId, "operator_reply_media", null);
        var metadata = attachmentService.storeTicketAttachment(authentication, ticketId, file);
        var result = dialogReplyService.sendMediaReply(ticketId, file, message, operator, metadata.storedName(), metadata.originalName());
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        String attachmentUrl = "/api/attachments/tickets/" + ticketId + "/" + result.storedName();
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", true);
        response.put("timestamp", result.timestamp());
        response.put("telegramMessageId", result.telegramMessageId());
        response.put("responsible", operator);
        response.put("attachment", attachmentUrl);
        response.put("messageType", result.messageType());
        response.put("message", result.message());
        notificationService.notifyDialogParticipants(
                ticketId,
                "Новое медиа-сообщение в обращении " + ticketId,
                "/dialogs?ticketId=" + ticketId,
                operator
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{ticketId}/resolve")
    public ResponseEntity<?> resolve(@PathVariable String ticketId,
                                     @RequestBody(required = false) DialogResolveRequest request,
                                     Authentication authentication) {
        return withQuickActionTiming("quick_close", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_close", "quick_close", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            List<String> categories = request != null ? request.categories() : List.of();
            DialogService.ResolveResult result = dialogService.resolveTicket(ticketId, operator, categories);
            if (!result.exists()) {
                logQuickAction(operator, ticketId, "quick_close", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }
            if (result.error() != null) {
                logQuickAction(operator, ticketId, "quick_close", "error", result.error());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", result.error()));
            }
            if (result.updated()) {
                dialogAiAssistantService.clearProcessing(ticketId, "resolved", null);
                dialogNotificationService.notifyResolved(ticketId);
                notificationService.notifyDialogParticipants(
                        ticketId,
                        "Обращение " + ticketId + " закрыто",
                        "/dialogs?ticketId=" + ticketId,
                        operator
                );
            }
            logQuickAction(operator, ticketId, "quick_close", "success", result.updated() ? "updated" : "noop");
            return ResponseEntity.ok(Map.of("success", true, "updated", result.updated()));
        });
    }

    @PostMapping("/{ticketId}/reopen")
    public ResponseEntity<?> reopen(@PathVariable String ticketId,
                                    Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_close", "reopen", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogService.ResolveResult result = dialogService.reopenTicket(ticketId, operator);
        if (!result.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }
        if (result.error() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        if (result.updated()) {
            dialogAiAssistantService.clearProcessing(ticketId, "reopened", null);
            dialogNotificationService.notifyReopened(ticketId);
            notificationService.notifyDialogParticipants(
                    ticketId,
                    "Обращение " + ticketId + " снова открыто",
                    "/dialogs?ticketId=" + ticketId,
                    operator
            );
        }
        return ResponseEntity.ok(Map.of("success", true, "updated", result.updated()));
    }

    @PostMapping("/{ticketId}/categories")
    public ResponseEntity<?> updateCategories(@PathVariable String ticketId,
                                              @RequestBody(required = false) DialogCategoriesRequest request,
                                              Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_close", "categories", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        String operator = authentication != null ? authentication.getName() : null;
        dialogService.assignResponsibleIfMissing(ticketId, operator);
        dialogService.setTicketCategories(ticketId, request != null ? request.categories() : List.of());
        notificationService.notifyDialogParticipants(
                ticketId,
                "В обращении " + ticketId + " обновлены категории",
                "/dialogs?ticketId=" + ticketId,
                operator
        );
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{ticketId}/take")
    public ResponseEntity<?> take(@PathVariable String ticketId,
                                  Authentication authentication) {
        return withQuickActionTiming("take", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_assign", "take", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : null;
            if (operator == null || operator.isBlank()) {
                logQuickAction(null, ticketId, "take", "unauthorized", "Требуется авторизация");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Требуется авторизация"));
            }
            Optional<DialogListItem> dialog = dialogService.findDialog(ticketId, operator);
            if (dialog.isEmpty()) {
                logQuickAction(operator, ticketId, "take", "not_found", "Диалог не найден");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден"));
            }

            dialogService.assignResponsibleIfMissing(ticketId, operator);
            dialogAiAssistantService.clearProcessing(ticketId, "operator_take", null);

            Optional<DialogListItem> updated = dialogService.findDialog(ticketId, operator);
            String responsible = updated.map(DialogListItem::responsible).orElse(dialog.get().responsible());
            notificationService.notifyDialogParticipants(
                    ticketId,
                    "Обращение " + ticketId + " взято в работу оператором " + operator,
                    "/dialogs?ticketId=" + ticketId,
                    operator
            );
            logQuickAction(operator, ticketId, "take", "success", "responsible_assigned");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "responsible", responsible != null && !responsible.isBlank() ? responsible : operator
            ));
        });
    }

    @PostMapping("/{ticketId}/snooze")
    public ResponseEntity<?> snooze(@PathVariable String ticketId,
                                    @RequestBody(required = false) DialogSnoozeRequest request,
                                    Authentication authentication) {
        return withQuickActionTiming("snooze", ticketId, () -> {
            ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_snooze", "snooze", ticketId);
            if (permissionDenied != null) {
                return permissionDenied;
            }
            String operator = authentication != null ? authentication.getName() : "anonymous";
            Integer minutes = request != null ? request.minutes() : null;
            if (minutes == null || minutes <= 0) {
                logQuickAction(operator, ticketId, "snooze", "error", "Некорректная длительность snooze");
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Некорректная длительность snooze"));
            }
            logQuickAction(operator, ticketId, "snooze", "success", "minutes=" + minutes);
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    @GetMapping("/{ticketId}/ai-suggestions")
    public ResponseEntity<?> aiSuggestions(@PathVariable String ticketId,
                                           @RequestParam(value = "limit", required = false) Integer limit,
                                           Authentication authentication) {
        ResponseEntity<Map<String, Object>> permissionDenied = requireDialogPermission(authentication, "can_reply", "ai_suggestions", ticketId);
        if (permissionDenied != null) {
            return permissionDenied;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("ticket_id", ticketId);
        payload.put("processing", dialogAiAssistantService.isProcessing(ticketId));
        payload.put("items", dialogAiAssistantService.loadOperatorSuggestions(ticketId, limit));
        return ResponseEntity.ok(payload);
    }

    private <T> T withQuickActionTiming(String action, String ticketId, Supplier<T> supplier) {
        long startedAtMs = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            if (elapsedMs > QUICK_ACTION_TARGET_MS) {
                log.warn("Quick action '{}' for ticket '{}' exceeded target: {}ms > {}ms", action, ticketId, elapsedMs, QUICK_ACTION_TARGET_MS);
            } else {
                log.debug("Quick action '{}' for ticket '{}' completed in {}ms", action, ticketId, elapsedMs);
            }
        }
    }

    private void logQuickAction(String actor, String ticketId, String action, String result, String detail) {
        String safeActor = actor != null ? actor : "anonymous";
        String safeDetail = detail != null ? detail : "";
        log.info("Dialog quick action: actor='{}', ticket='{}', action='{}', result='{}', detail='{}'",
                safeActor,
                ticketId,
                action,
                result,
                safeDetail);
        dialogService.logDialogActionAudit(ticketId, safeActor, action, result, safeDetail);
    }

    private Map<String, Object> resolveWorkspacePermissions(Authentication authentication) {
        boolean canDialog = permissionService.hasAuthority(authentication, "PAGE_DIALOGS");
        boolean canBulk = canDialog && (permissionService.hasAuthority(authentication, "DIALOG_BULK_ACTIONS")
                || permissionService.hasAuthority(authentication, "ROLE_ADMIN"));
        return Map.of(
                "can_reply", canDialog,
                "can_assign", canDialog,
                "can_close", canDialog,
                "can_snooze", canDialog,
                "can_bulk", canBulk
        );
    }

    private ResponseEntity<Map<String, Object>> requireDialogPermission(Authentication authentication,
                                                                         String permission,
                                                                         String action,
                                                                         String ticketId) {
        Map<String, Object> permissions = resolveWorkspacePermissions(authentication);
        boolean allowed = Boolean.TRUE.equals(permissions.get(permission));
        if (allowed) {
            return null;
        }
        String operator = authentication != null ? authentication.getName() : null;
        logQuickAction(operator, ticketId, action, "forbidden", "Недостаточно прав: " + permission);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "error", "Недостаточно прав для выполнения действия"));
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

    private Map<String, Object> loadOperatorTriagePreferences(String operator) {
        if (!StringUtils.hasText(operator)) {
            return Map.of();
        }
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object rawDialogConfig = settings.get("dialog_config");
        if (!(rawDialogConfig instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Object rawByOperator = dialogConfig.get("workspace_triage_preferences_by_operator");
        if (!(rawByOperator instanceof Map<?, ?> byOperator)) {
            return Map.of();
        }
        Object rawPreferences = byOperator.get(operator);
        if (!(rawPreferences instanceof Map<?, ?> preferences)) {
            return Map.of();
        }
        String view = normalizeDialogView(preferences.get("view"));
        String sortMode = normalizeSortMode(preferences.get("sort_mode"));
        Integer slaWindowMinutes = normalizeSlaWindowMinutes(preferences.get("sla_window_minutes"));
        String pageSize = normalizePageSizePreference(preferences.get("page_size"));
        String updatedAtUtc = normalizeUtcTimestamp(preferences.get("updated_at_utc"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("view", view);
        payload.put("sort_mode", sortMode);
        payload.put("page_size", pageSize);
        if (slaWindowMinutes != null) {
            payload.put("sla_window_minutes", slaWindowMinutes);
        }
        if (updatedAtUtc != null) {
            payload.put("updated_at_utc", updatedAtUtc);
        }
        return payload;
    }

    private String normalizeDialogView(Object rawValue) {
        String value = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(value)) {
            return "all";
        }
        return switch (value.toLowerCase()) {
            case "active", "new", "unassigned", "overdue", "sla_critical", "escalation_required" -> value.toLowerCase();
            default -> "all";
        };
    }

    private String normalizeSortMode(Object rawValue) {
        String value = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        return "sla_priority".equalsIgnoreCase(value) ? "sla_priority" : "default";
    }

    private Integer normalizeSlaWindowMinutes(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        int parsed;
        if (rawValue instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return switch (parsed) {
            case 15, 30, 60, 120, 240 -> parsed;
            default -> null;
        };
    }

    private String normalizePageSizePreference(Object rawValue) {
        String value = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(value)) {
            return "20";
        }
        if ("all".equalsIgnoreCase(value)) {
            return "all";
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? String.valueOf(parsed) : "20";
        } catch (NumberFormatException ex) {
            return "20";
        }
    }

    private int resolveIntegerDialogConfigValue(Object rawValue, int fallbackValue, int min, int max) {
        if (rawValue == null) {
            return fallbackValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallbackValue;
        }
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

    private Map<String, Object> resolveWorkspaceExternalProfileLinks(Map<String, Object> settings,
                                                                     DialogListItem summary,
                                                                     String ticketId,
                                                                     Map<String, Object> profileEnrichment) {
        if (settings == null || summary == null) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Map<String, Object> links = new LinkedHashMap<>();
        Map<String, String> placeholders = buildWorkspaceExternalLinkPlaceholders(summary, ticketId, profileEnrichment);
        appendWorkspaceExternalProfileLink(links, "crm", dialogConfig, "workspace_client_crm_profile_url_template",
                "workspace_client_crm_profile_label", "CRM профиль", placeholders);
        appendWorkspaceExternalProfileLink(links, "contract", dialogConfig, "workspace_client_contract_profile_url_template",
                "workspace_client_contract_profile_label", "Договор/контракт", placeholders);
        appendConfiguredWorkspaceExternalProfileLinks(links, dialogConfig, placeholders);
        return links;
    }

    private void appendConfiguredWorkspaceExternalProfileLinks(Map<String, Object> links,
                                                               Map<?, ?> dialogConfig,
                                                               Map<String, String> placeholders) {
        Object configuredRaw = dialogConfig.get("workspace_client_external_links");
        if (!(configuredRaw instanceof List<?> configuredLinks)) {
            return;
        }
        for (Object candidate : configuredLinks) {
            if (!(candidate instanceof Map<?, ?> linkConfig)) {
                continue;
            }
            boolean enabled = !linkConfig.containsKey("enabled") || asBoolean(linkConfig.get("enabled"));
            if (!enabled) {
                continue;
            }
            String key = normalizeMacroVariableKey(String.valueOf(linkConfig.get("key")));
            if (!StringUtils.hasText(key) || links.containsKey(key)) {
                continue;
            }
            String template = trimToNull(String.valueOf(linkConfig.get("url_template")));
            if (!StringUtils.hasText(template)) {
                continue;
            }
            String fallbackLabel = StringUtils.hasText(trimToNull(String.valueOf(linkConfig.get("label"))))
                ? trimToNull(String.valueOf(linkConfig.get("label")))
                : "Внешний профиль";
            appendWorkspaceExternalProfileLink(links, key, linkConfig, "url_template", "label", fallbackLabel, placeholders);
        }
    }

    private void appendWorkspaceExternalProfileLink(Map<String, Object> links,
                                                    String key,
                                                    Map<?, ?> dialogConfig,
                                                    String templateKey,
                                                    String labelKey,
                                                    String fallbackLabel,
                                                    Map<String, String> placeholders) {
        String template = trimToNull(String.valueOf(dialogConfig.get(templateKey)));
        if (!StringUtils.hasText(template)) {
            return;
        }
        String resolved = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        resolved = trimToNull(resolved);
        if (!StringUtils.hasText(resolved) || !(resolved.startsWith("https://") || resolved.startsWith("http://"))) {
            return;
        }
        String label = trimToNull(String.valueOf(dialogConfig.get(labelKey)));
        links.put(key, Map.of(
                "label", StringUtils.hasText(label) ? label : fallbackLabel,
                "url", resolved
        ));
    }

    private Map<String, String> buildWorkspaceExternalLinkPlaceholders(DialogListItem summary,
                                                                        String ticketId,
                                                                        Map<String, Object> profileEnrichment) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("ticket_id", StringUtils.hasText(ticketId) ? ticketId : "");
        placeholders.put("user_id", summary.userId() != null ? String.valueOf(summary.userId()) : "");
        placeholders.put("username", StringUtils.hasText(summary.username()) ? summary.username() : "");
        placeholders.put("channel", StringUtils.hasText(summary.channelLabel()) ? summary.channelLabel() : "");
        placeholders.put("business", StringUtils.hasText(summary.businessLabel()) ? summary.businessLabel() : "");
        placeholders.put("location", StringUtils.hasText(summary.location()) ? summary.location() : "");
        placeholders.put("responsible", StringUtils.hasText(summary.responsible()) ? summary.responsible() : "");
        if (profileEnrichment != null && !profileEnrichment.isEmpty()) {
            profileEnrichment.forEach((keyRaw, valueRaw) -> {
                String key = normalizeMacroVariableKey(String.valueOf(keyRaw));
                String value = stringifyMacroVariableValue(valueRaw);
                if (StringUtils.hasText(key) && value != null && !placeholders.containsKey(key)) {
                    placeholders.put(key, value);
                }
            });
        }
        return placeholders;
    }

    private Set<String> resolveWorkspaceHiddenClientAttributes(Map<String, Object> settings) {
        Set<String> hidden = new HashSet<>();
        if (settings == null || settings.isEmpty()) {
            return hidden;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return hidden;
        }
        Object hiddenRaw = dialogConfig.get("workspace_client_hidden_attributes");
        if (!(hiddenRaw instanceof List<?> hiddenList)) {
            return hidden;
        }
        hiddenList.forEach(item -> {
            String normalized = normalizeMacroVariableKey(String.valueOf(item));
            if (StringUtils.hasText(normalized)) {
                hidden.add(normalized);
            }
        });
        return hidden;
    }

    private Map<String, Object> filterWorkspaceProfileEnrichment(Map<String, Object> profileEnrichment,
                                                                  Set<String> hiddenAttributes) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (profileEnrichment == null || profileEnrichment.isEmpty()) {
            return filtered;
        }
        profileEnrichment.forEach((keyRaw, valueRaw) -> {
            String normalizedKey = normalizeMacroVariableKey(String.valueOf(keyRaw));
            if (StringUtils.hasText(normalizedKey) && hiddenAttributes.contains(normalizedKey)) {
                return;
            }
            filtered.put(String.valueOf(keyRaw), valueRaw);
        });
        return filtered;
    }

    private Map<String, String> resolveWorkspaceClientAttributeLabels(Map<String, Object> settings) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (settings == null || settings.isEmpty()) {
            return labels;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return labels;
        }
        Object labelsRaw = dialogConfig.get("workspace_client_attribute_labels");
        if (!(labelsRaw instanceof Map<?, ?> labelMap)) {
            return labels;
        }
        labelMap.forEach((keyRaw, valueRaw) -> {
            String key = normalizeMacroVariableKey(String.valueOf(keyRaw));
            String value = trimToNull(String.valueOf(valueRaw));
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                labels.put(key, value);
            }
        });
        return labels;
    }

    private List<String> resolveWorkspaceClientAttributeOrder(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object orderRaw = dialogConfig.get("workspace_client_attribute_order");
        if (!(orderRaw instanceof List<?> orderList)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        orderList.forEach(item -> {
            String key = normalizeMacroVariableKey(String.valueOf(item));
            if (StringUtils.hasText(key) && !normalized.contains(key)) {
                normalized.add(key);
            }
        });
        return normalized;
    }

    private Map<String, Object> buildWorkspaceProfileHealth(Map<String, Object> settings,
                                                            Map<String, Object> workspaceClient,
                                                            Map<String, String> configuredLabels) {
        List<String> globalRequiredFields = resolveWorkspaceRequiredClientAttributes(settings);
        Map<String, List<String>> segmentRequiredFields = resolveWorkspaceRequiredClientAttributesBySegment(settings);
        List<String> activeSegments = resolveWorkspaceClientSegments(workspaceClient);
        List<String> requiredFields = mergeWorkspaceRequiredClientAttributes(globalRequiredFields, segmentRequiredFields, activeSegments);
        Instant checkedAt = Instant.now();
        if (requiredFields.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enabled", false);
            payload.put("ready", true);
            payload.put("coverage_pct", 100);
            payload.put("checked_at", checkedAt.toString());
            payload.put("checked_at_utc", checkedAt.toString());
            payload.put("required_fields", List.of());
            payload.put("global_required_fields", List.of());
            payload.put("segment_required_fields", Map.of());
            payload.put("active_segments", activeSegments);
            payload.put("missing_fields", List.of());
            payload.put("missing_field_labels", List.of());
            return payload;
        }
        Map<String, String> labelMap = new LinkedHashMap<>();
        if (configuredLabels != null) {
            configuredLabels.forEach((key, value) -> {
                String normalizedKey = normalizeMacroVariableKey(key);
                if (StringUtils.hasText(normalizedKey) && StringUtils.hasText(value)) {
                    labelMap.put(normalizedKey, value);
                }
            });
        }
        labelMap.putIfAbsent("id", "ID");
        labelMap.putIfAbsent("name", "Имя");
        labelMap.putIfAbsent("username", "Username");
        labelMap.putIfAbsent("status", "Статус");
        labelMap.putIfAbsent("channel", "Канал");
        labelMap.putIfAbsent("business", "Бизнес");
        labelMap.putIfAbsent("location", "Локация");
        labelMap.putIfAbsent("responsible", "Ответственный");
        labelMap.putIfAbsent("last_message_at", "Последнее сообщение");
        labelMap.putIfAbsent("first_seen_at", "Первое обращение");
        labelMap.putIfAbsent("last_ticket_activity_at", "Последняя активность тикета");

        List<String> missingFields = new ArrayList<>();
        List<String> missingFieldLabels = new ArrayList<>();
        for (String field : requiredFields) {
            Object value = workspaceClient != null ? workspaceClient.get(field) : null;
            if (hasWorkspaceProfileValue(value)) {
                continue;
            }
            missingFields.add(field);
            missingFieldLabels.add(labelMap.getOrDefault(field, humanizeMacroVariableLabel(field)));
        }

        int totalRequired = requiredFields.size();
        int readyCount = totalRequired - missingFields.size();
        int coveragePct = totalRequired > 0
                ? (int) Math.round((readyCount * 100d) / totalRequired)
                : 100;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", true);
        payload.put("ready", missingFields.isEmpty());
        payload.put("coverage_pct", Math.max(0, Math.min(100, coveragePct)));
        payload.put("checked_at", checkedAt.toString());
        payload.put("checked_at_utc", checkedAt.toString());
        payload.put("required_fields", requiredFields);
        payload.put("global_required_fields", globalRequiredFields);
        payload.put("segment_required_fields", segmentRequiredFields);
        payload.put("active_segments", activeSegments);
        payload.put("missing_fields", missingFields);
        payload.put("missing_field_labels", missingFieldLabels);
        return payload;
    }

    private List<String> resolveWorkspaceClientSegments(Map<String, Object> workspaceClient) {
        if (workspaceClient == null || workspaceClient.isEmpty()) {
            return List.of();
        }
        Object segmentsRaw = workspaceClient.get("segments");
        if (!(segmentsRaw instanceof List<?> segments)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        segments.forEach(item -> appendNormalizedContextSource(normalized, item));
        return normalized;
    }

    private Map<String, List<String>> resolveWorkspaceRequiredClientAttributesBySegment(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Object rawValue = dialogConfig.get("workspace_required_client_attributes_by_segment");
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        rawMap.forEach((segmentRaw, attributesRaw) -> {
            String segmentKey = normalizeMacroVariableKey(String.valueOf(segmentRaw));
            if (!StringUtils.hasText(segmentKey)) {
                return;
            }
            List<String> attributes = normalizeWorkspaceClientAttributeList(attributesRaw);
            if (!attributes.isEmpty()) {
                normalized.put(segmentKey, attributes);
            }
        });
        return normalized;
    }

    private List<String> mergeWorkspaceRequiredClientAttributes(List<String> globalRequiredFields,
                                                                Map<String, List<String>> segmentRequiredFields,
                                                                List<String> activeSegments) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (globalRequiredFields != null) {
            merged.addAll(globalRequiredFields);
        }
        if (segmentRequiredFields != null && !segmentRequiredFields.isEmpty() && activeSegments != null) {
            activeSegments.forEach(segment -> merged.addAll(segmentRequiredFields.getOrDefault(segment, List.of())));
        }
        return new ArrayList<>(merged);
    }

    private List<String> normalizeWorkspaceClientAttributeList(Object rawValue) {
        List<String> normalized = new ArrayList<>();
        if (rawValue instanceof List<?> values) {
            values.forEach(item -> appendNormalizedContextSource(normalized, item));
            return normalized;
        }
        if (rawValue != null) {
            for (String part : String.valueOf(rawValue).split(",")) {
                appendNormalizedContextSource(normalized, part);
            }
        }
        return normalized;
    }

    private boolean hasWorkspaceProfileValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return StringUtils.hasText(String.valueOf(value));
    }

    private List<String> resolveWorkspaceRequiredClientAttributes(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object requiredRaw = dialogConfig.get("workspace_required_client_attributes");
        List<String> normalized = new ArrayList<>();
        if (requiredRaw instanceof List<?> values) {
            values.forEach(item -> {
                String key = normalizeMacroVariableKey(String.valueOf(item));
                if (StringUtils.hasText(key) && !normalized.contains(key)) {
                    normalized.add(key);
                }
            });
            return normalized;
        }
        if (requiredRaw != null) {
            for (String part : String.valueOf(requiredRaw).split(",")) {
                String key = normalizeMacroVariableKey(part);
                if (StringUtils.hasText(key) && !normalized.contains(key)) {
                    normalized.add(key);
                }
            }
        }
        return normalized;
    }

    private List<Map<String, Object>> buildWorkspaceContextSources(Map<String, Object> settings,
                                                                   Map<String, Object> workspaceClient,
                                                                   Map<String, Object> localProfileEnrichment,
                                                                   Map<String, Object> externalProfileEnrichment,
                                                                   Map<String, Object> externalLinks) {
        if (!isWorkspaceContextSourcesEnabled(settings)) {
            return List.of();
        }
        List<String> requiredSources = resolveWorkspaceRequiredContextSources(settings);
        List<String> configuredOrder = resolveWorkspaceContextSourcePriority(settings);
        Map<String, String> configuredLabels = resolveWorkspaceContextSourceLabels(settings);
        Map<String, List<String>> configuredUpdatedAtAttributes = resolveWorkspaceContextSourceUpdatedAtAttributes(settings);
        int defaultStaleAfterHours = resolveWorkspaceContextSourceStaleAfterHours(settings);
        Map<String, Integer> sourceSpecificStaleAfterHours = resolveWorkspaceContextSourceStaleAfterHoursBySource(settings, defaultStaleAfterHours);

        LinkedHashSet<String> sourceKeys = new LinkedHashSet<>();
        sourceKeys.add("local");
        configuredOrder.forEach(sourceKeys::add);
        requiredSources.forEach(sourceKeys::add);
        if (hasWorkspaceSourceCoverage("crm", workspaceClient, localProfileEnrichment, externalProfileEnrichment, externalLinks)) {
            sourceKeys.add("crm");
        }
        if (hasWorkspaceSourceCoverage("contract", workspaceClient, localProfileEnrichment, externalProfileEnrichment, externalLinks)) {
            sourceKeys.add("contract");
        }
        if (hasWorkspaceSourceCoverage("external", workspaceClient, localProfileEnrichment, externalProfileEnrichment, externalLinks)) {
            sourceKeys.add("external");
        }

        List<Map<String, Object>> sources = new ArrayList<>();
        Instant now = Instant.now();
        for (String sourceKey : sourceKeys) {
            String normalizedKey = normalizeMacroVariableKey(sourceKey);
            if (!StringUtils.hasText(normalizedKey)) {
                continue;
            }
            boolean required = requiredSources.contains(normalizedKey);
            List<String> matchedAttributes = resolveWorkspaceContextSourceMatchedAttributes(
                    normalizedKey,
                    workspaceClient,
                    localProfileEnrichment,
                    externalProfileEnrichment
            );
            boolean linked = externalLinks != null && externalLinks.containsKey(normalizedKey);
            boolean available = "local".equals(normalizedKey)
                    || !matchedAttributes.isEmpty()
                    || linked;
            int staleAfterHours = sourceSpecificStaleAfterHours.getOrDefault(normalizedKey, defaultStaleAfterHours);

            TimestampResolution timestampResolution = resolveWorkspaceContextSourceTimestamp(
                    normalizedKey,
                    workspaceClient,
                    configuredUpdatedAtAttributes
            );
            boolean stale = available
                    && timestampResolution.updatedAtUtc() != null
                    && staleAfterHours > 0
                    && timestampResolution.updatedAtUtc().toInstant().isBefore(now.minusSeconds(staleAfterHours * 3600L));

            List<String> issues = new ArrayList<>();
            String status;
            if (!available) {
                status = required ? "missing" : "unavailable";
                if (required) {
                    issues.add("missing");
                }
            } else if (timestampResolution.invalidUtc()) {
                status = "invalid_utc";
                issues.add("invalid_utc");
            } else if (stale) {
                status = "stale";
                issues.add("stale");
            } else {
                status = "ready";
            }

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("key", normalizedKey);
            source.put("label", configuredLabels.getOrDefault(normalizedKey, humanizeMacroVariableLabel(normalizedKey)));
            source.put("required", required);
            source.put("status", status);
            source.put("ready", "ready".equals(status));
            source.put("available", available);
            source.put("linked", linked);
            source.put("matched_attributes", matchedAttributes);
            source.put("matched_attribute_count", matchedAttributes.size());
            source.put("freshness_ttl_hours", staleAfterHours > 0 ? staleAfterHours : null);
            source.put("freshness_policy_scope", sourceSpecificStaleAfterHours.containsKey(normalizedKey) ? "source" : "global");
            source.put("issues", issues);
            if (timestampResolution.attributeKey() != null) {
                source.put("updated_at_attribute", timestampResolution.attributeKey());
            }
            if (timestampResolution.updatedAtRaw() != null) {
                source.put("updated_at_raw", timestampResolution.updatedAtRaw());
            }
            if (timestampResolution.updatedAtUtc() != null) {
                source.put("updated_at_utc", timestampResolution.updatedAtUtc().toString());
            }
            source.put("summary", buildWorkspaceContextSourceSummary(
                    status,
                    required,
                    linked,
                    matchedAttributes.size(),
                    timestampResolution,
                    staleAfterHours
            ));
            sources.add(source);
        }
        return sources;
    }

    private boolean isWorkspaceContextSourcesEnabled(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return false;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return false;
        }
        return dialogConfig.containsKey("workspace_client_context_required_sources")
                || dialogConfig.containsKey("workspace_client_context_source_labels")
                || dialogConfig.containsKey("workspace_client_context_source_priority")
                || dialogConfig.containsKey("workspace_client_context_source_updated_at_attributes")
                || dialogConfig.containsKey("workspace_client_context_source_stale_after_hours")
                || dialogConfig.containsKey("workspace_client_context_source_stale_after_hours_by_source");
    }

    private List<Map<String, Object>> buildWorkspaceContextAttributePolicies(Map<String, Object> workspaceClient,
                                                                             Map<String, Object> profileHealth,
                                                                             List<Map<String, Object>> contextSources) {
        if (workspaceClient == null || workspaceClient.isEmpty()
                || profileHealth == null || profileHealth.isEmpty()
                || !toBoolean(profileHealth.get("enabled"))) {
            return List.of();
        }
        List<String> requiredFields = safeStringList(profileHealth.get("required_fields"));
        if (requiredFields.isEmpty()) {
            return List.of();
        }
        Map<String, String> attributeLabels = workspaceClient.get("attribute_labels") instanceof Map<?, ?> labels
                ? labels.entrySet().stream()
                .filter(entry -> StringUtils.hasText(normalizeMacroVariableKey(String.valueOf(entry.getKey()))))
                .collect(Collectors.toMap(
                        entry -> normalizeMacroVariableKey(String.valueOf(entry.getKey())),
                        entry -> String.valueOf(entry.getValue()),
                        (first, second) -> first,
                        LinkedHashMap::new))
                : Map.of();
        List<Map<String, Object>> safeSources = contextSources == null ? List.of() : contextSources;

        List<Map<String, Object>> policies = new ArrayList<>();
        for (String field : requiredFields) {
            String normalizedField = normalizeMacroVariableKey(field);
            if (!StringUtils.hasText(normalizedField)) {
                continue;
            }
            Object value = workspaceClient.get(normalizedField);
            boolean valueReady = hasWorkspaceProfileValue(value);
            Map<String, Object> preferredSource = safeSources.stream()
                    .filter(source -> safeStringList(source.get("matched_attributes")).contains(normalizedField))
                    .findFirst()
                    .orElse(null);
            String status;
            List<String> issues = new ArrayList<>();
            if (!valueReady) {
                status = "missing";
                issues.add("missing");
            } else if (preferredSource == null) {
                status = "untracked";
                issues.add("untracked");
            } else {
                status = normalizeWorkspaceAttributePolicyStatus(String.valueOf(preferredSource.get("status")));
                if (!"ready".equals(status)) {
                    issues.add(status);
                }
            }

            Map<String, Object> policy = new LinkedHashMap<>();
            policy.put("key", normalizedField);
            policy.put("label", attributeLabels.getOrDefault(normalizedField, humanizeMacroVariableLabel(normalizedField)));
            policy.put("required", true);
            policy.put("ready", "ready".equals(status));
            policy.put("status", status);
            policy.put("issues", issues);
            if (preferredSource != null) {
                policy.put("source_key", preferredSource.get("key"));
                policy.put("source_label", preferredSource.get("label"));
                policy.put("source_ready", toBoolean(preferredSource.get("ready")));
                policy.put("freshness_required", preferredSource.get("freshness_ttl_hours") instanceof Number);
                if (preferredSource.get("freshness_ttl_hours") instanceof Number ttl) {
                    policy.put("freshness_ttl_hours", ttl.intValue());
                }
                if (preferredSource.get("updated_at_utc") != null) {
                    policy.put("updated_at_utc", preferredSource.get("updated_at_utc"));
                }
                if (preferredSource.get("updated_at_raw") != null) {
                    policy.put("updated_at_raw", preferredSource.get("updated_at_raw"));
                }
            } else {
                policy.put("freshness_required", false);
            }
            policy.put("summary", buildWorkspaceContextAttributePolicySummary(
                    normalizedField,
                    status,
                    valueReady,
                    preferredSource
            ));
            policies.add(policy);
        }
        return policies;
    }

    private String normalizeWorkspaceAttributePolicyStatus(String status) {
        String normalized = trimToNull(status);
        if (!StringUtils.hasText(normalized)) {
            return "untracked";
        }
        return switch (normalized.toLowerCase()) {
            case "ready", "stale", "invalid_utc", "missing", "untracked", "unavailable" -> normalized.toLowerCase();
            default -> "untracked";
        };
    }

    private String buildWorkspaceContextAttributePolicySummary(String field,
                                                               String status,
                                                               boolean valueReady,
                                                               Map<String, Object> preferredSource) {
        if (!valueReady) {
            return "Поле %s не заполнено в mandatory profile.".formatted(field);
        }
        if (preferredSource == null) {
            return "Для поля %s не определён source/freshness policy.".formatted(field);
        }
        String sourceLabel = String.valueOf(preferredSource.getOrDefault("label", preferredSource.getOrDefault("key", "source")));
        return switch (status) {
            case "ready" -> "Поле %s подтверждено источником %s.".formatted(field, sourceLabel);
            case "stale" -> "Поле %s опирается на stale-источник %s.".formatted(field, sourceLabel);
            case "invalid_utc" -> "Для поля %s источник %s вернул невалидный UTC timestamp.".formatted(field, sourceLabel);
            case "missing" -> "Для поля %s обязательный источник %s недоступен.".formatted(field, sourceLabel);
            case "unavailable" -> "Для поля %s источник %s пока недоступен.".formatted(field, sourceLabel);
            default -> "Для поля %s policy по источнику %s ещё не формализована.".formatted(field, sourceLabel);
        };
    }

    private List<Map<String, Object>> buildWorkspaceContextBlocks(Map<String, Object> settings,
                                                                  Map<String, Object> profileHealth,
                                                                  List<Map<String, Object>> contextSources,
                                                                  List<Map<String, Object>> clientHistory,
                                                                  List<Map<String, Object>> relatedEvents,
                                                                  String slaState,
                                                                  Map<String, Object> externalLinks) {
        if (!isWorkspaceContextBlocksEnabled(settings)) {
            return List.of();
        }
        List<String> priority = resolveWorkspaceContextBlockPriority(settings);
        List<String> requiredBlocks = resolveWorkspaceRequiredContextBlocks(settings);
        Instant checkedAt = Instant.now();

        Map<String, Map<String, Object>> blockIndex = new LinkedHashMap<>();
        blockIndex.put("customer_profile", buildWorkspaceContextBlock(
                "customer_profile",
                "Минимальный профиль клиента",
                requiredBlocks.contains("customer_profile"),
                !toBoolean(profileHealth != null ? profileHealth.get("enabled") : null) || toBoolean(profileHealth.get("ready")),
                resolveWorkspaceContextProfileBlockStatus(profileHealth),
                resolveWorkspaceContextProfileBlockSummary(profileHealth),
                normalizeUtcTimestamp(profileHealth != null ? profileHealth.get("checked_at") : null),
                checkedAt
        ));
        blockIndex.put("context_sources", buildWorkspaceContextBlock(
                "context_sources",
                "Источники customer context",
                requiredBlocks.contains("context_sources"),
                resolveWorkspaceContextSourcesReady(contextSources),
                resolveWorkspaceContextSourcesBlockStatus(contextSources),
                resolveWorkspaceContextSourcesBlockSummary(contextSources),
                resolveWorkspaceContextSourcesUpdatedAtUtc(contextSources),
                checkedAt
        ));
        blockIndex.put("history", buildWorkspaceContextBlock(
                "history",
                "История обращений клиента",
                requiredBlocks.contains("history"),
                clientHistory != null,
                clientHistory != null ? "ready" : "missing",
                clientHistory == null
                        ? "История обращений недоступна."
                        : "Записей: %d.".formatted(clientHistory.size()),
                null,
                checkedAt
        ));
        blockIndex.put("related_events", buildWorkspaceContextBlock(
                "related_events",
                "Связанные события",
                requiredBlocks.contains("related_events"),
                relatedEvents != null,
                relatedEvents != null ? "ready" : "missing",
                relatedEvents == null
                        ? "Связанные события недоступны."
                        : "Событий: %d.".formatted(relatedEvents.size()),
                null,
                checkedAt
        ));
        blockIndex.put("sla", buildWorkspaceContextBlock(
                "sla",
                "SLA-контекст",
                requiredBlocks.contains("sla"),
                StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState),
                StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState) ? "ready" : "missing",
                StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState)
                        ? "SLA state: %s.".formatted(slaState)
                        : "SLA-контекст недоступен.",
                null,
                checkedAt
        ));
        blockIndex.put("external_links", buildWorkspaceContextBlock(
                "external_links",
                "Внешние customer links",
                requiredBlocks.contains("external_links"),
                externalLinks != null && !externalLinks.isEmpty(),
                externalLinks != null && !externalLinks.isEmpty() ? "ready" : "unavailable",
                externalLinks != null && !externalLinks.isEmpty()
                        ? "Ссылок: %d.".formatted(externalLinks.size())
                        : "Внешние ссылки не настроены.",
                null,
                checkedAt
        ));

        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String blockKey : priority) {
            Map<String, Object> block = blockIndex.remove(blockKey);
            if (block != null) {
                ordered.add(block);
            }
        }
        ordered.addAll(blockIndex.values());
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).put("priority", i + 1);
        }
        return ordered;
    }

    private Map<String, Object> buildWorkspaceContextBlock(String key,
                                                           String label,
                                                           boolean required,
                                                           boolean ready,
                                                           String status,
                                                           String summary,
                                                           String updatedAtUtc,
                                                           Instant checkedAt) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("key", key);
        block.put("label", label);
        block.put("required", required);
        block.put("ready", ready);
        block.put("status", normalizeWorkspaceContextBlockStatus(status));
        block.put("summary", trimToNull(summary));
        block.put("checked_at_utc", checkedAt != null ? checkedAt.toString() : Instant.now().toString());
        if (StringUtils.hasText(updatedAtUtc)) {
            block.put("updated_at_utc", updatedAtUtc);
        }
        return block;
    }

    private Map<String, Object> buildWorkspaceContextBlocksHealth(List<Map<String, Object>> contextBlocks) {
        if (contextBlocks == null || contextBlocks.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> requiredBlocks = contextBlocks.stream()
                .filter(item -> toBoolean(item.get("required")))
                .toList();
        List<String> missingKeys = requiredBlocks.stream()
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        List<String> missingLabels = requiredBlocks.stream()
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> String.valueOf(item.get("label")))
                .toList();
        int coveragePct = requiredBlocks.isEmpty()
                ? 100
                : (int) Math.round(((requiredBlocks.size() - missingKeys.size()) * 100d) / requiredBlocks.size());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", true);
        payload.put("ready", missingKeys.isEmpty());
        payload.put("coverage_pct", Math.max(0, Math.min(100, coveragePct)));
        payload.put("required_count", requiredBlocks.size());
        payload.put("missing_required_keys", missingKeys);
        payload.put("missing_required_labels", missingLabels);
        payload.put("checked_at_utc", Instant.now().toString());
        return payload;
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
                .filter(field -> !hasWorkspaceProfileValue(workspaceClient.get(field)))
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

    private boolean isWorkspaceContextBlocksEnabled(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return false;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return false;
        }
        return dialogConfig.containsKey("workspace_context_block_priority")
                || dialogConfig.containsKey("workspace_context_block_required");
    }

    private List<String> resolveWorkspaceContextBlockPriority(Map<String, Object> settings) {
        List<String> defaults = new ArrayList<>(List.of(
                "customer_profile",
                "context_sources",
                "history",
                "related_events",
                "sla",
                "external_links"
        ));
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return defaults;
        }
        Object priorityRaw = dialogConfig.get("workspace_context_block_priority");
        if (!(priorityRaw instanceof List<?> priorityList)) {
            return defaults;
        }
        List<String> ordered = new ArrayList<>();
        priorityList.forEach(item -> appendNormalizedContextSource(ordered, item));
        defaults.forEach(item -> {
            if (!ordered.contains(item)) {
                ordered.add(item);
            }
        });
        return ordered;
    }

    private List<String> resolveWorkspaceRequiredContextBlocks(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object requiredRaw = dialogConfig.get("workspace_context_block_required");
        List<String> required = new ArrayList<>();
        if (requiredRaw instanceof List<?> requiredList) {
            requiredList.forEach(item -> appendNormalizedContextSource(required, item));
            return required;
        }
        if (requiredRaw != null) {
            for (String part : String.valueOf(requiredRaw).split(",")) {
                appendNormalizedContextSource(required, part);
            }
        }
        return required;
    }

    private String normalizeWorkspaceContextBlockStatus(String status) {
        String normalized = trimToNull(status);
        if (!StringUtils.hasText(normalized)) {
            return "unavailable";
        }
        return switch (normalized.toLowerCase()) {
            case "ready", "missing", "attention", "invalid_utc", "stale", "unavailable" -> normalized.toLowerCase();
            default -> "attention";
        };
    }

    private String resolveWorkspaceContextProfileBlockStatus(Map<String, Object> profileHealth) {
        if (profileHealth == null || profileHealth.isEmpty() || !toBoolean(profileHealth.get("enabled"))) {
            return "ready";
        }
        return toBoolean(profileHealth.get("ready")) ? "ready" : "attention";
    }

    private String resolveWorkspaceContextProfileBlockSummary(Map<String, Object> profileHealth) {
        if (profileHealth == null || profileHealth.isEmpty() || !toBoolean(profileHealth.get("enabled"))) {
            return "Минимальный customer profile не требует дополнительных полей.";
        }
        List<String> missingLabels = safeStringList(profileHealth.get("missing_field_labels"));
        if (toBoolean(profileHealth.get("ready"))) {
            return "Обязательные поля customer profile заполнены.";
        }
        return "Не хватает полей: %s.".formatted(String.join(", ", missingLabels.isEmpty() ? List.of("нет данных") : missingLabels));
    }

    private boolean resolveWorkspaceContextSourcesReady(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return true;
        }
        return contextSources.stream()
                .filter(item -> toBoolean(item.get("required")))
                .allMatch(item -> toBoolean(item.get("ready")));
    }

    private String resolveWorkspaceContextSourcesBlockStatus(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return "unavailable";
        }
        return contextSources.stream()
                .filter(item -> toBoolean(item.get("required")))
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> normalizeWorkspaceContextBlockStatus(String.valueOf(item.get("status"))))
                .findFirst()
                .orElse("ready");
    }

    private String resolveWorkspaceContextSourcesBlockSummary(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return "Источники customer context не настроены.";
        }
        List<String> blocking = contextSources.stream()
                .filter(item -> toBoolean(item.get("required")))
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> "%s (%s)".formatted(item.get("label"), item.get("status")))
                .toList();
        if (blocking.isEmpty()) {
            return "Все обязательные источники customer context готовы.";
        }
        return "Проблемные источники: %s.".formatted(String.join(", ", blocking));
    }

    private String resolveWorkspaceContextSourcesUpdatedAtUtc(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return null;
        }
        return contextSources.stream()
                .map(item -> normalizeUtcTimestamp(item.get("updated_at_utc")))
                .filter(StringUtils::hasText)
                .max(String::compareTo)
                .orElse(null);
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
                    if (hasWorkspaceProfileValue(workspaceClient.get(field))) {
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
            if (StringUtils.hasText(normalized) && normalized.startsWith(prefix) && hasWorkspaceProfileValue(value)) {
                matches.add(normalized);
            }
        });
    }

    private Map<String, String> resolveWorkspaceContextSourceLabels(Map<String, Object> settings) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("local", "Локальный профиль");
        labels.put("crm", "CRM");
        labels.put("contract", "Контракт");
        labels.put("external", "Внешний источник");
        if (settings == null || settings.isEmpty()) {
            return labels;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return labels;
        }
        Object labelsRaw = dialogConfig.get("workspace_client_context_source_labels");
        if (!(labelsRaw instanceof Map<?, ?> labelMap)) {
            return labels;
        }
        labelMap.forEach((keyRaw, valueRaw) -> {
            String key = normalizeMacroVariableKey(String.valueOf(keyRaw));
            String value = trimToNull(String.valueOf(valueRaw));
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                labels.put(key, value);
            }
        });
        return labels;
    }

    private List<String> resolveWorkspaceRequiredContextSources(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object requiredRaw = dialogConfig.get("workspace_client_context_required_sources");
        List<String> required = new ArrayList<>();
        if (requiredRaw instanceof List<?> items) {
            items.forEach(item -> appendNormalizedContextSource(required, item));
            return required;
        }
        if (requiredRaw != null) {
            for (String part : String.valueOf(requiredRaw).split(",")) {
                appendNormalizedContextSource(required, part);
            }
        }
        return required;
    }

    private void appendNormalizedContextSource(List<String> target, Object rawValue) {
        String normalized = normalizeMacroVariableKey(String.valueOf(rawValue));
        if (StringUtils.hasText(normalized) && !target.contains(normalized)) {
            target.add(normalized);
        }
    }

    private List<String> resolveWorkspaceContextSourcePriority(Map<String, Object> settings) {
        List<String> defaults = new ArrayList<>(List.of("local", "crm", "contract", "external"));
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return defaults;
        }
        Object priorityRaw = dialogConfig.get("workspace_client_context_source_priority");
        if (!(priorityRaw instanceof List<?> priorityList)) {
            return defaults;
        }
        List<String> ordered = new ArrayList<>();
        priorityList.forEach(item -> appendNormalizedContextSource(ordered, item));
        defaults.forEach(defaultItem -> {
            if (!ordered.contains(defaultItem)) {
                ordered.add(defaultItem);
            }
        });
        return ordered;
    }

    private Map<String, List<String>> resolveWorkspaceContextSourceUpdatedAtAttributes(Map<String, Object> settings) {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("local", List.of("local_updated_at", "last_ticket_activity_at", "last_message_at"));
        defaults.put("crm", List.of("crm_updated_at", "crm_profile_updated_at"));
        defaults.put("contract", List.of("contract_updated_at", "contract_profile_updated_at"));
        defaults.put("external", List.of("external_updated_at", "profile_updated_at", "source_updated_at"));
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return defaults;
        }
        Object updatedAtRaw = dialogConfig.get("workspace_client_context_source_updated_at_attributes");
        if (!(updatedAtRaw instanceof Map<?, ?> updatedAtMap)) {
            return defaults;
        }
        updatedAtMap.forEach((keyRaw, valueRaw) -> {
            String sourceKey = normalizeMacroVariableKey(String.valueOf(keyRaw));
            if (!StringUtils.hasText(sourceKey)) {
                return;
            }
            List<String> attributes = new ArrayList<>();
            if (valueRaw instanceof List<?> items) {
                items.forEach(item -> appendNormalizedContextSource(attributes, item));
            } else if (valueRaw != null) {
                for (String part : String.valueOf(valueRaw).split(",")) {
                    appendNormalizedContextSource(attributes, part);
                }
            }
            if (!attributes.isEmpty()) {
                defaults.put(sourceKey, attributes);
            }
        });
        return defaults;
    }

    private int resolveWorkspaceContextSourceStaleAfterHours(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return 0;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return 0;
        }
        return resolveIntegerDialogConfig(dialogConfig, "workspace_client_context_source_stale_after_hours", 0, 0, 24 * 365);
    }

    private Map<String, Integer> resolveWorkspaceContextSourceStaleAfterHoursBySource(Map<String, Object> settings,
                                                                                       int fallbackValue) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Object rawValue = dialogConfig.get("workspace_client_context_source_stale_after_hours_by_source");
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        rawMap.forEach((sourceRaw, ttlRaw) -> {
            String sourceKey = normalizeMacroVariableKey(String.valueOf(sourceRaw));
            if (!StringUtils.hasText(sourceKey)) {
                return;
            }
            normalized.put(sourceKey, resolveIntegerDialogConfigValue(ttlRaw, fallbackValue, 0, 24 * 365));
        });
        return normalized;
    }

    private TimestampResolution resolveWorkspaceContextSourceTimestamp(String sourceKey,
                                                                      Map<String, Object> workspaceClient,
                                                                      Map<String, List<String>> configuredUpdatedAtAttributes) {
        List<String> candidateAttributes = configuredUpdatedAtAttributes.getOrDefault(sourceKey, List.of());
        for (String attributeKey : candidateAttributes) {
            Object rawValue = workspaceClient != null ? workspaceClient.get(attributeKey) : null;
            String normalizedRawValue = trimToNull(String.valueOf(rawValue));
            if (!StringUtils.hasText(normalizedRawValue)) {
                continue;
            }
            OffsetDateTime parsed = parseUtcTimestamp(normalizedRawValue);
            return new TimestampResolution(
                    attributeKey,
                    normalizedRawValue,
                    parsed,
                    parsed == null
            );
        }
        return new TimestampResolution(null, null, null, false);
    }

    private String buildWorkspaceContextSourceSummary(String status,
                                                      boolean required,
                                                      boolean linked,
                                                      int matchedAttributeCount,
                                                      TimestampResolution timestampResolution,
                                                      int staleAfterHours) {
        return switch (StringUtils.hasText(status) ? status : "unavailable") {
            case "ready" -> "Источник готов: %d атрибутов%s%s.".formatted(
                    matchedAttributeCount,
                    linked ? ", есть внешняя ссылка" : "",
                    timestampResolution.updatedAtUtc() != null ? ", UTC " + timestampResolution.updatedAtUtc() : ""
            );
            case "stale" -> "Источник доступен, но устарел по TTL %dч.".formatted(Math.max(0, staleAfterHours));
            case "invalid_utc" -> "Дата источника не распознана как UTC: %s.".formatted(
                    timestampResolution.updatedAtRaw() != null ? timestampResolution.updatedAtRaw() : "пусто"
            );
            case "missing" -> required
                    ? "Обязательный источник ещё не подключён или не вернул атрибуты."
                    : "Источник пока недоступен.";
            default -> linked
                    ? "Источник доступен только как внешняя ссылка."
                    : "Источник не предоставил данных.";
        };
    }

    private record TimestampResolution(String attributeKey,
                                       String updatedAtRaw,
                                       OffsetDateTime updatedAtUtc,
                                       boolean invalidUtc) {
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

    public record WorkspaceTelemetryRequest(@JsonAlias("event_type") String eventType,
                                          String timestamp,
                                          @JsonAlias("event_group") String eventGroup,
                                          @JsonAlias("ticket_id") String ticketId,
                                          String reason,
                                          @JsonAlias("error_code") String errorCode,
                                          @JsonAlias("contract_version") String contractVersion,
                                          @JsonAlias("duration_ms") Long durationMs,
                                          @JsonAlias("experiment_name") String experimentName,
                                          @JsonAlias("experiment_cohort") String experimentCohort,
                                          @JsonAlias("operator_segment") String operatorSegment,
                                          @JsonAlias("primary_kpis") List<String> primaryKpis,
                                          @JsonAlias("secondary_kpis") List<String> secondaryKpis,
                                          @JsonAlias("template_id") String templateId,
                                          @JsonAlias("template_name") String templateName) {}

    public record MacroDryRunRequest(@JsonAlias({"ticket_id", "ticketId"}) String ticketId,
                                     @JsonAlias({"template_text", "templateText", "text"}) String templateText,
                                     Map<String, String> variables) {}

    public record DialogReplyRequest(String message, Long replyToTelegramId) {}

    public record DialogResolveRequest(List<String> categories) {}

    public record DialogEditRequest(Long telegramMessageId, String message) {}

    public record DialogDeleteRequest(Long telegramMessageId) {}

    public record DialogCategoriesRequest(List<String> categories) {}

    public record DialogSnoozeRequest(Integer minutes) {}

    public record TriagePreferencesRequest(@JsonAlias("view") String view,
                                           @JsonAlias("sort_mode") String sortMode,
                                           @JsonAlias("sla_window_minutes") Integer slaWindowMinutes,
                                           @JsonAlias("page_size") String pageSize) {}
}
