package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.PublicFormService;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    private final PublicFormService publicFormService;
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
            Map.entry("kpi_frt_recorded", "kpi"),
            Map.entry("kpi_ttr_recorded", "kpi"),
            Map.entry("kpi_sla_breach_recorded", "kpi"),
            Map.entry("kpi_dialogs_per_shift_recorded", "kpi"),
            Map.entry("kpi_csat_recorded", "kpi"),
            Map.entry("macro_preview", "macro"),
            Map.entry("macro_apply", "macro"),
            Map.entry("triage_view_switch", "triage"),
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
                               PublicFormService publicFormService) {
        this.dialogService = dialogService;
        this.dialogReplyService = dialogReplyService;
        this.dialogNotificationService = dialogNotificationService;
        this.attachmentService = attachmentService;
        this.sharedConfigService = sharedConfigService;
        this.permissionService = permissionService;
        this.publicFormService = publicFormService;
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
            }
        });
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(externalUrl))
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contract_version", "workspace.v1");
        payload.put("conversation", summary);
        payload.put("messages", includeSections.contains("messages")
                ? Map.of(
                "items", pagedHistory,
                "next_cursor", nextCursor,
                "has_more", hasMore,
                "limit", resolvedLimit,
                "cursor", safeCursor
        )
                : Map.of(
                "items", List.of(),
                "next_cursor", null,
                "has_more", false,
                "unavailable", true
        ));
        payload.put("context", includeSections.contains("context")
                ? Map.of(
                "client", workspaceClient,
                "history", clientHistory,
                "related_events", relatedEvents
        )
                : Map.of(
                "client", Map.of(),
                "history", List.of(),
                "related_events", List.of(),
                "unavailable", true
        ));
        payload.put("permissions", includeSections.contains("permissions")
                ? resolveWorkspacePermissions(authentication)
                : Map.of(
                "can_reply", false,
                "can_assign", false,
                "can_close", false,
                "can_snooze", false,
                "can_bulk", false,
                "unavailable", true
        ));
        payload.put("sla", includeSections.contains("sla")
                ? Map.of(
                "target_minutes", slaTargetMinutes,
                "warning_minutes", slaWarningMinutes,
                "critical_minutes", slaCriticalMinutes,
                "deadline_at", computeDeadlineAt(summary.createdAt(), slaTargetMinutes),
                "state", slaState,
                "minutes_left", slaMinutesLeft,
                "escalation_required", slaMinutesLeft != null && slaMinutesLeft <= slaCriticalMinutes
        )
                : Map.of(
                "target_minutes", slaTargetMinutes,
                "warning_minutes", slaWarningMinutes,
                "critical_minutes", slaCriticalMinutes,
                "deadline_at", computeDeadlineAt(summary.createdAt(), slaTargetMinutes),
                "state", "unknown",
                "minutes_left", null,
                "escalation_required", false,
                "unavailable", true
        ));
        payload.put("meta", Map.of(
                "include", includeSections,
                "limit", resolvedLimit,
                "cursor", safeCursor
        ));
        payload.put("success", true);
        return ResponseEntity.ok(payload);
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

        if (totalDialogs >= highLifetimeDialogsThreshold) {
            segments.add("high_lifetime_volume");
        }
        if (openDialogs >= multiOpenDialogsThreshold) {
            segments.add("multi_open_dialogs");
        }
        if (totalDialogs >= reactivationDialogsThreshold && resolved30d <= reactivationResolvedThreshold) {
            segments.add("reactivation_risk");
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
                                                       @RequestParam(name = "experiment_name", required = false) String experimentName) {
        int safeDays = days != null ? days : 7;
        Map<String, Object> payload = new LinkedHashMap<>(dialogService.loadWorkspaceTelemetrySummary(safeDays, experimentName));
        payload.put("success", true);
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
        var metadata = attachmentService.storeTicketAttachment(authentication, ticketId, file);
        var result = dialogReplyService.sendMediaReply(ticketId, file, message, operator, metadata.storedName(), metadata.originalName());
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", result.error()));
        }
        String attachmentUrl = "/api/attachments/tickets/" + ticketId + "/" + result.storedName();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "timestamp", result.timestamp(),
                "telegramMessageId", result.telegramMessageId(),
                "responsible", operator,
                "attachment", attachmentUrl,
                "messageType", result.messageType(),
                "message", result.message()
        ));
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
                dialogNotificationService.notifyResolved(ticketId);
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
            dialogNotificationService.notifyReopened(ticketId);
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

            Optional<DialogListItem> updated = dialogService.findDialog(ticketId, operator);
            String responsible = updated.map(DialogListItem::responsible).orElse(dialog.get().responsible());
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
}
