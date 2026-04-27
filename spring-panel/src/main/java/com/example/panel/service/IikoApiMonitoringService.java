package com.example.panel.service;

import com.example.panel.entity.IikoApiMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.IikoApiMonitorRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IikoApiMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(IikoApiMonitoringService.class);

    public static final String STATUS_OK = "ok";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_DISABLED = "disabled";

    private static final long QUEUE_GAP_MS = 20_000L;
    private static final long HTTP_TIMEOUT_MS = 30_000L;
    private static final int EXCERPT_LIMIT = 4_000;
    private static final String DEFAULT_BASE_URL = "https://api-ru.iiko.services";

    private final IikoApiMonitorRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService refreshExecutor;
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);
    private final AtomicReference<Long> pendingMonitorId = new AtomicReference<>();
    private final AtomicReference<OffsetDateTime> lastRefreshRequestedAt = new AtomicReference<>();
    private final AtomicReference<OffsetDateTime> lastRefreshCompletedAt = new AtomicReference<>();

    public IikoApiMonitoringService(IikoApiMonitorRepository repository,
                                    MonitoringCheckHistoryRepository historyRepository,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.refreshExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("iiko-api-refresh"));
    }

    @PreDestroy
    void shutdownExecutor() {
        refreshExecutor.shutdownNow();
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public List<IikoApiMonitor> findAll() {
        return repository.findAllByOrderByMonitorNameAscIdAsc();
    }

    @Transactional(transactionManager = "monitoringTransactionManager")
    public IikoApiMonitor createMonitor(MonitorDraft draft) {
        MonitorDraft normalizedDraft = normalizeDraft(draft);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        IikoApiMonitor monitor = new IikoApiMonitor();
        monitor.setMonitorName(normalizedDraft.monitorName());
        monitor.setBaseUrl(normalizedDraft.baseUrl());
        monitor.setApiLogin(normalizedDraft.apiLogin());
        monitor.setRequestType(normalizedDraft.requestType());
        monitor.setRequestConfigJson(writeConfig(normalizedDraft.config()));
        monitor.setEnabled(normalizedDraft.enabled() == null || normalizedDraft.enabled());
        monitor.setLastStatus(Boolean.TRUE.equals(monitor.getEnabled()) ? STATUS_ERROR : STATUS_DISABLED);
        monitor.setLastErrorMessage(Boolean.TRUE.equals(monitor.getEnabled()) ? "Ожидает первой проверки" : "Мониторинг отключён");
        monitor.setConsecutiveFailures(0);
        monitor.setCreatedAt(now);
        monitor.setUpdatedAt(now);
        monitor = repository.save(monitor);

        synchronizeDisabledState(monitor);
        return repository.findById(monitor.getId()).orElse(monitor);
    }

    @Transactional(transactionManager = "monitoringTransactionManager")
    public IikoApiMonitor updateMonitor(long id, MonitorDraft draft) {
        IikoApiMonitor monitor = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Запись iiko API не найдена"));
        MonitorDraft normalizedDraft = normalizeDraft(draft);

        monitor.setMonitorName(normalizedDraft.monitorName());
        monitor.setBaseUrl(normalizedDraft.baseUrl());
        monitor.setApiLogin(normalizedDraft.apiLogin());
        monitor.setRequestType(normalizedDraft.requestType());
        monitor.setRequestConfigJson(writeConfig(normalizedDraft.config()));
        monitor.setEnabled(normalizedDraft.enabled() == null || normalizedDraft.enabled());
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(monitor);

        synchronizeDisabledState(monitor);
        return repository.findById(monitor.getId()).orElse(monitor);
    }

    @Transactional(transactionManager = "monitoringTransactionManager")
    public void deleteMonitor(long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Запись iiko API не найдена");
        }
        repository.deleteById(id);
    }

    public RefreshRequestResult requestRefresh() {
        return queueRefresh(null);
    }

    public RefreshRequestResult requestRefreshForMonitor(long id) {
        requireMonitor(id);
        return queueRefresh(id);
    }

    @Transactional(transactionManager = "monitoringTransactionManager")
    public void setEnabledForAll(boolean enabled) {
        List<IikoApiMonitor> monitors = repository.findAllByOrderByMonitorNameAscIdAsc();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (IikoApiMonitor monitor : monitors) {
            monitor.setEnabled(enabled);
            monitor.setUpdatedAt(now);
            if (!enabled) {
                applyDisabledState(monitor);
            } else if (STATUS_DISABLED.equals(resolveStatus(monitor))) {
                monitor.setLastStatus(STATUS_ERROR);
                monitor.setLastErrorMessage("Ожидает обновления");
                repository.save(monitor);
            } else {
                repository.save(monitor);
            }
        }
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public LastResponseView loadLastResponse(long id) {
        IikoApiMonitor monitor = requireMonitor(id);
        return new LastResponseView(
            monitor.getId(),
            monitor.getMonitorName(),
            monitor.getRequestType(),
            requestTypeFromStoredValue(monitor.getRequestType()).label(),
            monitor.getLastCheckedAt(),
            monitor.getLastHttpStatus(),
            monitor.getLastDurationMs(),
            monitor.getLastErrorMessage(),
            readResponseSummary(monitor),
            StringUtils.hasText(monitor.getLastResponseExcerpt()) ? monitor.getLastResponseExcerpt() : "",
            loadTimelineForMonitor(monitor.getId())
        );
    }

    public RefreshState currentRefreshState() {
        return new RefreshState(
            refreshRunning.get(),
            refreshPending.get(),
            lastRefreshRequestedAt.get(),
            lastRefreshCompletedAt.get()
        );
    }

    public String resolveStatus(IikoApiMonitor monitor) {
        if (monitor == null || !Boolean.TRUE.equals(monitor.getEnabled())) {
            return STATUS_DISABLED;
        }
        String value = normalizeText(monitor.getLastStatus(), 40);
        return value == null ? STATUS_ERROR : value.toLowerCase(Locale.ROOT);
    }

    public MonitorConfig readConfig(IikoApiMonitor monitor) {
        if (monitor == null || !StringUtils.hasText(monitor.getRequestConfigJson())) {
            return new MonitorConfig(List.of(), null, List.of(), null, null, null, null, null, null, null, List.of(), null);
        }
        try {
            MonitorConfig raw = objectMapper.readValue(monitor.getRequestConfigJson(), MonitorConfig.class);
            return sanitizeConfig(raw);
        } catch (Exception ex) {
            return new MonitorConfig(List.of(), null, List.of(), null, null, null, null, null, null, null, List.of(), null);
        }
    }

    public Map<String, Object> readResponseSummary(IikoApiMonitor monitor) {
        if (monitor == null || !StringUtils.hasText(monitor.getLastResponseSummaryJson())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                monitor.getLastResponseSummaryJson(),
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
            );
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public List<RequestTypeOption> requestTypeCatalog() {
        List<RequestTypeOption> items = new ArrayList<>();
        for (RequestType type : RequestType.values()) {
            items.add(new RequestTypeOption(type.code(), type.label(), type.endpointPath(), type.description()));
        }
        return items;
    }

    private List<TimelineEntryView> loadTimelineForMonitor(Long monitorId) {
        if (monitorId == null) {
            return List.of();
        }
        return historyRepository.findRecent("iiko", monitorId, 20).stream()
            .map(entry -> new TimelineEntryView(
                entry.checkKind(),
                entry.status(),
                entry.summary(),
                entry.detailsExcerpt(),
                entry.httpStatus(),
                entry.durationMs(),
                entry.createdAt()
            ))
            .toList();
    }

    private void recordTimelineEntry(IikoApiMonitor monitor,
                                     String checkKind,
                                     String status,
                                     String summary,
                                     String detailsExcerpt,
                                     Integer httpStatus,
                                     Long durationMs,
                                     OffsetDateTime createdAt) {
        if (monitor == null || monitor.getId() == null) {
            return;
        }
        historyRepository.record(
            "iiko",
            monitor.getId(),
            checkKind,
            trimText(status, 40, ""),
            trimText(summary, 600, ""),
            trimText(detailsExcerpt, EXCERPT_LIMIT, ""),
            httpStatus,
            durationMs,
            createdAt != null ? createdAt : OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private RefreshRequestResult queueRefresh(Long monitorId) {
        lastRefreshRequestedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        if (!refreshPending.compareAndSet(false, true)) {
            return new RefreshRequestResult("already_queued", currentRefreshState());
        }
        pendingMonitorId.set(monitorId);
        refreshExecutor.submit(this::runQueuedRefresh);
        return new RefreshRequestResult("queued", currentRefreshState());
    }

    private void runQueuedRefresh() {
        Long monitorId = pendingMonitorId.getAndSet(null);
        refreshPending.set(false);
        refreshRunning.set(true);
        try {
            if (monitorId == null) {
                refreshAllInternal();
            } else {
                refreshMonitor(requireMonitor(monitorId));
            }
            lastRefreshCompletedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (Exception ex) {
            log.warn("iiko API refresh queue failed", ex);
        } finally {
            refreshRunning.set(false);
        }
    }

    private void refreshAllInternal() {
        List<IikoApiMonitor> monitors = repository.findAllByOrderByMonitorNameAscIdAsc();
        for (int index = 0; index < monitors.size(); index++) {
            refreshMonitor(monitors.get(index));
            sleepBetweenQueueItems(index, monitors.size());
        }
    }

    private void refreshMonitor(IikoApiMonitor monitor) {
        if (monitor == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        monitor.setLastCheckedAt(now);
        monitor.setUpdatedAt(now);

        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            applyDisabledState(monitor);
            return;
        }

        RequestType requestType;
        MonitorConfig config;
        try {
            requestType = requestTypeFromStoredValue(monitor.getRequestType());
            config = readConfig(monitor);
            validateConfig(requestType, config);
        } catch (Exception ex) {
            monitor.setLastStatus(STATUS_ERROR);
            monitor.setLastHttpStatus(null);
            monitor.setLastDurationMs(null);
            monitor.setLastErrorMessage(trimText(ex.getMessage(), 500, "Ошибка конфигурации"));
            monitor.setLastResponseSummaryJson(null);
            monitor.setLastResponseExcerpt(null);
            monitor.setConsecutiveFailures(incrementFailures(monitor));
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "request",
                monitor.getLastStatus(),
                monitor.getLastErrorMessage(),
                null,
                null,
                null,
                now
            );
            return;
        }

        long startedAt = System.nanoTime();
        try {
            AuthResult authResult = requestAccessToken(monitor.getBaseUrl(), monitor.getApiLogin());
            monitor.setLastTokenCheckedAt(now);

            Map<String, Object> responseSummary;
            Integer httpStatus;
            String responseExcerpt;
            if (requestType == RequestType.ACCESS_TOKEN) {
                responseSummary = new LinkedHashMap<>();
                responseSummary.put("request_type", requestType.code());
                responseSummary.put("auth", "ok");
                responseSummary.put("correlationId", authResult.correlationId());
                httpStatus = authResult.httpStatus();
                responseExcerpt = redactAccessTokenExcerpt(authResult.correlationId());
            } else {
                ObjectNode requestBody = buildRequestBody(requestType, config);
                MonitorHttpResponse response = postJson(
                    monitor.getBaseUrl(),
                    requestType.endpointPath(),
                    requestBody,
                    authResult.token()
                );
                responseSummary = buildResponseSummary(requestType, response.json());
                httpStatus = response.statusCode();
                responseExcerpt = trimText(response.body(), EXCERPT_LIMIT, "");
            }

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            monitor.setLastStatus(STATUS_OK);
            monitor.setLastHttpStatus(httpStatus);
            monitor.setLastDurationMs(durationMs);
            monitor.setLastErrorMessage(null);
            monitor.setLastResponseSummaryJson(writeSummary(responseSummary));
            monitor.setLastResponseExcerpt(responseExcerpt);
            monitor.setConsecutiveFailures(0);
            monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "request",
                monitor.getLastStatus(),
                buildTimelineSummary(requestType, httpStatus, responseSummary),
                responseExcerpt,
                httpStatus,
                durationMs,
                now
            );
        } catch (MonitorRequestException ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            monitor.setLastStatus(STATUS_ERROR);
            monitor.setLastHttpStatus(ex.httpStatus());
            monitor.setLastDurationMs(durationMs);
            monitor.setLastErrorMessage(trimText(ex.getMessage(), 500, "Ошибка вызова iiko API"));
            monitor.setLastResponseExcerpt(trimText(ex.responseBody(), EXCERPT_LIMIT, ""));
            monitor.setLastResponseSummaryJson(null);
            monitor.setConsecutiveFailures(incrementFailures(monitor));
            monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "request",
                monitor.getLastStatus(),
                monitor.getLastErrorMessage(),
                monitor.getLastResponseExcerpt(),
                monitor.getLastHttpStatus(),
                durationMs,
                now
            );
        } catch (Exception ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            monitor.setLastStatus(STATUS_ERROR);
            monitor.setLastHttpStatus(null);
            monitor.setLastDurationMs(durationMs);
            monitor.setLastErrorMessage(trimText(ex.getMessage(), 500, "Ошибка вызова iiko API"));
            monitor.setLastResponseExcerpt(null);
            monitor.setLastResponseSummaryJson(null);
            monitor.setConsecutiveFailures(incrementFailures(monitor));
            monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            repository.save(monitor);
            recordTimelineEntry(
                monitor,
                "request",
                monitor.getLastStatus(),
                monitor.getLastErrorMessage(),
                null,
                null,
                durationMs,
                now
            );
        }
    }

    private AuthResult requestAccessToken(String baseUrl, String apiLogin) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("apiLogin", apiLogin);
        MonitorHttpResponse response = postJson(baseUrl, RequestType.ACCESS_TOKEN.endpointPath(), payload, null);
        String token = firstNonBlank(
            response.json().path("token").asText(""),
            response.json().path("accessToken").asText(""),
            response.json().path("access_token").asText("")
        );
        if (!StringUtils.hasText(token)) {
            throw new MonitorRequestException("iiko не вернул token", response.statusCode(), redactAccessTokenExcerpt(
                response.json().path("correlationId").asText("")
            ));
        }
        return new AuthResult(
            token.trim(),
            normalizeText(response.json().path("correlationId").asText(""), 120),
            response.statusCode()
        );
    }

    private MonitorHttpResponse postJson(String baseUrl,
                                         String endpointPath,
                                         JsonNode body,
                                         String bearerToken) throws Exception {
        String url = buildApiUrl(baseUrl, endpointPath);
        String requestBody = objectMapper.writeValueAsString(body == null ? objectMapper.createObjectNode() : body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken.trim());
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json = parseJson(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MonitorRequestException(
                extractErrorMessage(json, "HTTP " + response.statusCode()),
                response.statusCode(),
                trimText(response.body(), EXCERPT_LIMIT, "")
            );
        }
        String inlineError = extractInlineError(json);
        if (inlineError != null) {
            throw new MonitorRequestException(inlineError, response.statusCode(), trimText(response.body(), EXCERPT_LIMIT, ""));
        }
        return new MonitorHttpResponse(response.statusCode(), response.body(), json);
    }

    private ObjectNode buildRequestBody(RequestType requestType, MonitorConfig config) {
        ObjectNode body = objectMapper.createObjectNode();
        switch (requestType) {
            case ACCESS_TOKEN -> {
                return body;
            }
            case ORGANIZATIONS -> {
                appendStringArray(body, "organizationIds", config.organizationIds());
                appendBoolean(body, "returnAdditionalInfo", config.returnAdditionalInfo());
                appendBoolean(body, "includeDisabled", config.includeDisabled());
                appendStringArray(body, "returnExternalData", config.returnExternalData());
            }
            case TERMINAL_GROUPS -> {
                appendStringArray(body, "organizationIds", config.organizationIds());
                appendBoolean(body, "includeDisabled", config.includeDisabled());
                appendStringArray(body, "returnExternalData", config.returnExternalData());
            }
            case NOMENCLATURE -> {
                body.put("organizationId", config.organizationId());
                if (config.startRevision() != null) {
                    body.put("startRevision", config.startRevision());
                }
            }
            case STOP_LISTS -> {
                appendStringArray(body, "organizationIds", config.organizationIds());
                appendBoolean(body, "returnSize", config.returnSize());
                appendStringArray(body, "terminalGroupsIds", config.terminalGroupIds());
            }
            case MENU_BY_ID -> {
                body.put("externalMenuId", config.externalMenuId());
                appendStringArray(body, "organizationIds", config.organizationIds());
                if (StringUtils.hasText(config.priceCategoryId())) {
                    body.put("priceCategoryId", config.priceCategoryId());
                }
                if (config.menuVersion() != null) {
                    body.put("version", config.menuVersion());
                }
                if (StringUtils.hasText(config.language())) {
                    body.put("language", config.language());
                }
                if (config.startRevision() != null) {
                    body.put("startRevision", config.startRevision());
                }
            }
        }
        return body;
    }

    private Map<String, Object> buildResponseSummary(RequestType requestType, JsonNode root) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("request_type", requestType.code());
        if (root == null || root.isMissingNode() || root.isNull()) {
            summary.put("state", "empty");
            return summary;
        }
        putIfText(summary, "correlationId", root.path("correlationId").asText(""));
        putIfNumber(summary, "revision", root.path("revision"));
        putIfNumber(summary, "version", root.path("version"));

        if (requestType == RequestType.ORGANIZATIONS) {
            putArrayCount(summary, "organizations", root, "organizations");
        } else if (requestType == RequestType.TERMINAL_GROUPS) {
            putArrayCount(summary, "terminalGroups", root, "terminalGroups");
        } else if (requestType == RequestType.NOMENCLATURE) {
            putArrayCount(summary, "groups", root, "groups");
            putArrayCount(summary, "productCategories", root, "productCategories");
            putArrayCount(summary, "products", root, "products");
            putArrayCount(summary, "sizes", root, "sizes");
        } else if (requestType == RequestType.STOP_LISTS) {
            putArrayCount(summary, "terminalGroupStopLists", root, "terminalGroupStopLists");
        } else if (requestType == RequestType.MENU_BY_ID) {
            putArrayCount(summary, "groups", root, "groups");
            putArrayCount(summary, "items", root, "items");
            putArrayCount(summary, "products", root, "products");
            putArrayCount(summary, "combos", root, "combos");
            putIfText(summary, "name", root.path("name").asText(""));
        }

        if (summary.size() <= 1 && root.isObject()) {
            root.fieldNames().forEachRemaining(field -> {
                JsonNode child = root.path(field);
                if (child.isArray() && summary.size() < 8) {
                    summary.put(field + "_count", child.size());
                } else if (child.isValueNode() && summary.size() < 8) {
                    String text = normalizeText(child.asText(""), 160);
                    if (text != null) {
                        summary.put(field, text);
                    }
                }
            });
        }
        if (summary.size() == 1 && root.isArray()) {
            summary.put("items", root.size());
        }
        return summary;
    }

    private void appendStringArray(ObjectNode body, String fieldName, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        ArrayNode arrayNode = body.putArray(fieldName);
        values.forEach(arrayNode::add);
    }

    private void appendBoolean(ObjectNode body, String fieldName, Boolean value) {
        if (value != null) {
            body.put(fieldName, value);
        }
    }

    private void putArrayCount(Map<String, Object> summary, String key, JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (node.isArray()) {
            summary.put(key, node.size());
        }
    }

    private void putIfText(Map<String, Object> summary, String key, String value) {
        String text = normalizeText(value, 160);
        if (text != null) {
            summary.put(key, text);
        }
    }

    private void putIfNumber(Map<String, Object> summary, String key, JsonNode value) {
        if (value != null && value.isNumber()) {
            summary.put(key, value.numberValue());
        }
    }

    private String buildTimelineSummary(RequestType requestType, Integer httpStatus, Map<String, Object> responseSummary) {
        StringBuilder summary = new StringBuilder();
        if (requestType != null) {
            summary.append(requestType.label());
        }
        if (httpStatus != null) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append("HTTP ").append(httpStatus);
        }
        if (responseSummary != null && !responseSummary.isEmpty()) {
            String compact = responseSummary.entrySet().stream()
                .limit(3)
                .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
            if (StringUtils.hasText(compact)) {
                if (summary.length() > 0) {
                    summary.append(", ");
                }
                summary.append(compact);
            }
        }
        return trimText(summary.toString(), 600, "");
    }

    private MonitorDraft normalizeDraft(MonitorDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Тело запроса не передано");
        }
        String monitorName = normalizeRequired(draft.monitorName(), 160, "Укажите название монитора");
        String baseUrl = normalizeBaseUrl(draft.baseUrl());
        String apiLogin = normalizeRequired(draft.apiLogin(), 500, "Укажите apikey iiko");
        RequestType requestType = requestTypeFromInput(draft.requestType());
        MonitorConfig config = sanitizeConfig(draft.config());
        validateConfig(requestType, config);
        return new MonitorDraft(monitorName, baseUrl, apiLogin, requestType.code(), config, draft.enabled());
    }

    private void validateConfig(RequestType requestType, MonitorConfig config) {
        if (requestType == RequestType.TERMINAL_GROUPS && config.organizationIds().isEmpty()) {
            throw new IllegalArgumentException("Для terminal_groups нужен хотя бы один organization id");
        }
        if (requestType == RequestType.NOMENCLATURE && !StringUtils.hasText(config.organizationId())) {
            throw new IllegalArgumentException("Для nomenclature нужен organization id");
        }
        if (requestType == RequestType.STOP_LISTS && config.organizationIds().isEmpty()) {
            throw new IllegalArgumentException("Для stop_lists нужен хотя бы один organization id");
        }
        if (requestType == RequestType.MENU_BY_ID) {
            if (config.organizationIds().isEmpty()) {
                throw new IllegalArgumentException("Для menu/by_id нужен хотя бы один organization id");
            }
            if (!StringUtils.hasText(config.externalMenuId())) {
                throw new IllegalArgumentException("Для menu/by_id нужен external menu id");
            }
        }
    }

    private MonitorConfig sanitizeConfig(MonitorConfig input) {
        MonitorConfig safe = input != null
            ? input
            : new MonitorConfig(List.of(), null, List.of(), null, null, null, null, null, null, null, List.of(), null);
        return new MonitorConfig(
            sanitizeList(safe.organizationIds(), 64, 120),
            normalizeText(safe.organizationId(), 120),
            sanitizeList(safe.terminalGroupIds(), 64, 120),
            normalizeText(safe.externalMenuId(), 120),
            normalizeText(safe.priceCategoryId(), 120),
            sanitizeMenuVersion(safe.menuVersion()),
            normalizeText(safe.language(), 32),
            sanitizeRevision(safe.startRevision()),
            safe.returnAdditionalInfo(),
            safe.includeDisabled(),
            sanitizeList(safe.returnExternalData(), 32, 120),
            safe.returnSize()
        );
    }

    private List<String> sanitizeList(List<String> raw, int maxItems, int maxLength) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String item : raw) {
            String normalized = normalizeText(item, maxLength);
            if (normalized != null) {
                values.add(normalized);
            }
            if (values.size() >= maxItems) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private Integer sanitizeMenuVersion(Integer value) {
        if (value == null || value < 1 || value > 20) {
            return null;
        }
        return value;
    }

    private Long sanitizeRevision(Long value) {
        if (value == null || value < 0L) {
            return null;
        }
        return value;
    }

    private String writeConfig(MonitorConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось сохранить конфигурацию запроса iiko");
        }
    }

    private String writeSummary(Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary == null ? Map.of() : summary);
        } catch (Exception ex) {
            return null;
        }
    }

    private JsonNode parseJson(String body) {
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("raw", trimText(body, EXCERPT_LIMIT, ""));
            return fallback;
        }
    }

    private String extractInlineError(JsonNode json) {
        return firstNonBlank(
            normalizeText(text(json, "errorDescription"), 400),
            normalizeText(text(json, "error_description"), 400),
            normalizeText(text(json, "error"), 400),
            normalizeText(text(json, "message"), 400),
            normalizeText(text(json, "description"), 400)
        );
    }

    private String extractErrorMessage(JsonNode json, String fallback) {
        String inline = extractInlineError(json);
        return inline != null ? inline : fallback;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.path(fieldName).isNull()) {
            return "";
        }
        return node.path(fieldName).asText("");
    }

    private void synchronizeDisabledState(IikoApiMonitor monitor) {
        if (monitor == null) {
            return;
        }
        if (!Boolean.TRUE.equals(monitor.getEnabled())) {
            applyDisabledState(monitor);
        }
    }

    private void applyDisabledState(IikoApiMonitor monitor) {
        monitor.setLastStatus(STATUS_DISABLED);
        monitor.setLastHttpStatus(null);
        monitor.setLastDurationMs(null);
        monitor.setLastErrorMessage("Мониторинг отключён");
        monitor.setLastResponseSummaryJson(null);
        monitor.setLastResponseExcerpt(null);
        monitor.setConsecutiveFailures(0);
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(monitor);
    }

    private int incrementFailures(IikoApiMonitor monitor) {
        Integer current = monitor.getConsecutiveFailures();
        return current == null ? 1 : Math.max(1, current + 1);
    }

    private String normalizeBaseUrl(String rawValue) {
        String candidate = normalizeText(rawValue, 300);
        if (candidate == null) {
            return DEFAULT_BASE_URL;
        }
        if (!candidate.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            candidate = "https://" + candidate;
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный base URL iiko");
        }
        String scheme = Optional.ofNullable(uri.getScheme()).orElse("https").trim().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Поддерживаются только http/https URL iiko");
        }
        String host = Optional.ofNullable(uri.getHost()).orElse("").trim();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("Не удалось определить host iiko API");
        }
        try {
            host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный host iiko API");
        }
        int port = uri.getPort();
        StringBuilder result = new StringBuilder(scheme).append("://").append(host);
        if (port > 0 && !(port == 443 && "https".equals(scheme)) && !(port == 80 && "http".equals(scheme))) {
            result.append(":").append(port);
        }
        return result.toString();
    }

    private String buildApiUrl(String baseUrl, String endpointPath) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        return normalizedBase + endpointPath;
    }

    private RequestType requestTypeFromInput(String rawValue) {
        String normalized = normalizeRequired(rawValue, 80, "Укажите тип запроса iiko");
        return RequestType.fromCode(normalized)
            .orElseThrow(() -> new IllegalArgumentException("Неподдерживаемый тип запроса iiko"));
    }

    private RequestType requestTypeFromStoredValue(String rawValue) {
        return RequestType.fromCode(rawValue)
            .orElseThrow(() -> new IllegalArgumentException("Неподдерживаемый тип запроса iiko: " + rawValue));
    }

    private String normalizeRequired(String value, int limit, String errorMessage) {
        String normalized = normalizeText(value, limit);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String normalizeText(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, limit);
    }

    private String trimText(String value, int limit, String fallback) {
        String normalized = normalizeText(value, limit);
        return normalized != null ? normalized : fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String redactAccessTokenExcerpt(String correlationId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("auth", "ok");
        if (StringUtils.hasText(correlationId)) {
            node.put("correlationId", correlationId.trim());
        }
        node.put("token", "***");
        return node.toPrettyString();
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(prefix + "-" + UUID.randomUUID());
            return thread;
        };
    }

    private void sleepBetweenQueueItems(int index, int total) {
        if (index >= total - 1) {
            return;
        }
        try {
            Thread.sleep(QUEUE_GAP_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private IikoApiMonitor requireMonitor(long id) {
        return repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Запись iiko API не найдена"));
    }

    public record MonitorDraft(String monitorName,
                               String baseUrl,
                               String apiLogin,
                               String requestType,
                               MonitorConfig config,
                               Boolean enabled) {
    }

    public record MonitorConfig(List<String> organizationIds,
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
                                Boolean returnSize) {
    }

    public record RequestTypeOption(String code,
                                    String label,
                                    String endpointPath,
                                    String description) {
    }

    public record RefreshState(boolean running,
                               boolean queued,
                               OffsetDateTime lastRequestedAt,
                               OffsetDateTime lastCompletedAt) {
    }

    public record RefreshRequestResult(String state, RefreshState refreshState) {
    }

    public record LastResponseView(Long id,
                                   String monitorName,
                                   String requestType,
                                   String requestTypeLabel,
                                   OffsetDateTime lastCheckedAt,
                                   Integer lastHttpStatus,
                                   Long lastDurationMs,
                                   String lastErrorMessage,
                                   Map<String, Object> summary,
                                   String responseExcerpt,
                                   List<TimelineEntryView> timeline) {
    }

    public record TimelineEntryView(String checkKind,
                                    String status,
                                    String summary,
                                    String detailsExcerpt,
                                    Integer httpStatus,
                                    Long durationMs,
                                    OffsetDateTime createdAt) {
    }

    private record MonitorHttpResponse(int statusCode, String body, JsonNode json) {
    }

    private record AuthResult(String token, String correlationId, int httpStatus) {
    }

    private static final class MonitorRequestException extends Exception {
        private final Integer httpStatus;
        private final String responseBody;

        private MonitorRequestException(String message, Integer httpStatus, String responseBody) {
            super(message);
            this.httpStatus = httpStatus;
            this.responseBody = responseBody;
        }

        private Integer httpStatus() {
            return httpStatus;
        }

        private String responseBody() {
            return responseBody;
        }
    }

    public enum RequestType {
        ACCESS_TOKEN("access_token", "Проверка авторизации", "/api/1/access_token",
            "Проверяет только получение bearer token по apiLogin."),
        ORGANIZATIONS("organizations", "Организации", "/api/1/organizations",
            "Read-only запрос списка организаций iiko."),
        TERMINAL_GROUPS("terminal_groups", "Терминальные группы", "/api/1/terminal_groups",
            "Read-only запрос терминальных групп по organizationIds."),
        NOMENCLATURE("nomenclature", "Номенклатура", "/api/1/nomenclature",
            "Read-only запрос номенклатуры по organizationId."),
        STOP_LISTS("stop_lists", "Стоп-листы", "/api/1/stop_lists",
            "Read-only запрос стоп-листов по organizationIds."),
        MENU_BY_ID("menu_by_id", "Меню по ID", "/api/2/menu/by_id",
            "Read-only запрос внешнего меню по externalMenuId.");

        private final String code;
        private final String label;
        private final String endpointPath;
        private final String description;

        RequestType(String code, String label, String endpointPath, String description) {
            this.code = code;
            this.label = label;
            this.endpointPath = endpointPath;
            this.description = description;
        }

        public String code() {
            return code;
        }

        public String label() {
            return label;
        }

        public String endpointPath() {
            return endpointPath;
        }

        public String description() {
            return description;
        }

        public static Optional<RequestType> fromCode(String rawValue) {
            if (!StringUtils.hasText(rawValue)) {
                return Optional.empty();
            }
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            for (RequestType type : values()) {
                if (type.code.equals(normalized)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }
}
