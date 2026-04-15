package com.example.panel.service;

import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DialogMacroService {

    private static final Logger log = LoggerFactory.getLogger(DialogMacroService.class);
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
    private static final Pattern SAFE_HTTP_HEADER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final Object MACRO_CATALOG_EXTERNAL_CACHE_LOCK = new Object();
    private static volatile String macroCatalogExternalCacheUrl = null;
    private static volatile List<Map<String, String>> macroCatalogExternalCacheVariables = List.of();
    private static volatile Instant macroCatalogExternalCacheExpiresAt = Instant.EPOCH;
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

    private final DialogService dialogService;
    private final SharedConfigService sharedConfigService;

    public DialogMacroService(DialogService dialogService,
                              SharedConfigService sharedConfigService) {
        this.dialogService = dialogService;
        this.sharedConfigService = sharedConfigService;
    }

    public MacroDryRunResponse dryRun(String ticketId, String templateText, String operator, Map<String, String> requestVariables) {
        Map<String, String> variables = buildMacroVariables(ticketId, operator, requestVariables);
        MacroDryRunResult result = renderMacroTemplate(templateText, variables);
        return new MacroDryRunResponse(result.renderedText(), result.usedVariables(), result.missingVariables());
    }

    public List<Map<String, String>> loadVariables(String ticketId, String operator) {
        List<Map<String, String>> variables = BUILTIN_MACRO_VARIABLES.stream()
                .map(item -> macroVariable(item.get("key"), item.get("label"), null, "builtin"))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Map<String, Object> settings = sharedConfigService.loadSettings();
        appendMacroCatalogVariables(variables, resolveMacroVariableCatalog(settings), "settings_catalog");
        appendMacroCatalogVariables(variables, resolveExternalMacroVariableCatalog(settings), "external_catalog");
        resolveDialogConfigMacroVariableDefaults(settings).forEach((key, defaultValue) -> {
            if (variables.stream().noneMatch(item -> key.equals(item.get("key")))) {
                variables.add(macroVariable(key, "Значение из dialog_config", defaultValue, "dialog_config_default"));
            }
        });
        if (StringUtils.hasText(ticketId)) {
            Map<String, String> contextVariables = buildMacroVariables(ticketId.trim(), operator, null);
            contextVariables.forEach((key, value) -> {
                if (variables.stream().noneMatch(item -> key.equals(item.get("key")))) {
                    variables.add(macroVariable(key, humanizeMacroVariableLabel(key), value, "ticket_context"));
                }
            });
        }
        return variables;
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
            Optional<DialogDetails> detailsOptional = dialogService.loadDialogDetails(safeTicketId, null, operator);
            detailsOptional.ifPresent(details -> {
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
        resolveConfiguredMacroVariableDefaults().forEach((key, value) -> putMacroVariableIfAbsent(variables, key, value));
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
        resolveDialogConfigMacroVariableDefaults(settings).forEach(defaults::putIfAbsent);
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
            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
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

    private void applyMacroCatalogExternalAuthHeader(HttpRequest.Builder requestBuilder, Map<?, ?> dialogConfig) {
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
        if (cacheTtlSeconds <= 0 || !externalUrl.equals(macroCatalogExternalCacheUrl)) {
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

    private String humanizeMacroVariableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return "Переменная";
        }
        String[] tokens = key.trim().toLowerCase().split("_");
        StringBuilder label = new StringBuilder();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return label.isEmpty() ? "Переменная" : label.toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Map<String, String> macroVariable(String key, String label) {
        return macroVariable(key, label, null, "builtin");
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

    private record MacroDryRunResult(String renderedText, List<String> usedVariables, List<String> missingVariables) {
    }

    public record MacroDryRunResponse(String renderedText, List<String> usedVariables, List<String> missingVariables) {
    }
}
