package com.example.panel.service;

import com.example.panel.service.LocalMachineIntegrationsConfigService.Bitrix24Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class Bitrix24RestService {

    private final LocalMachineIntegrationsConfigService localMachineIntegrationsConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public Bitrix24RestService(LocalMachineIntegrationsConfigService localMachineIntegrationsConfigService,
                               ObjectMapper objectMapper) {
        this.localMachineIntegrationsConfigService = localMachineIntegrationsConfigService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public List<Map<String, Object>> listWorkgroups(String query, int limit) {
        JsonNode root = call("sonet_group.get", query == null || query.isBlank()
            ? Map.of()
            : Map.of("FILTER[NAME]", query.trim()));
        List<JsonNode> groups = collectRows(root);
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode group : groups) {
            String id = pickText(group, "ID", "id");
            String name = pickText(group, "NAME", "name", "TITLE", "title");
            if (!StringUtils.hasText(id) || !StringUtils.hasText(name)) {
                continue;
            }
            String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
            if (StringUtils.hasText(normalizedQuery) && !name.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                continue;
            }
            items.add(Map.of("id", id, "name", name));
            if (items.size() >= Math.max(limit, 1)) {
                break;
            }
        }
        return items;
    }

    public List<Map<String, Object>> listTasksForGroup(Long groupId) {
        if (groupId == null || groupId <= 0) {
            return List.of();
        }
        JsonNode root = call("tasks.task.list", Map.of("filter[GROUP_ID]", String.valueOf(groupId)));
        List<JsonNode> tasks = collectRows(root);
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode task : tasks) {
            String id = pickText(task, "id", "ID");
            if (!StringUtils.hasText(id)) {
                continue;
            }
            Map<String, Object> details = getTaskDetails(id);
            if (!details.isEmpty()) {
                items.add(details);
            }
        }
        return items;
    }

    public Map<String, Object> getTaskDetails(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Map.of();
        }
        JsonNode root = call("tasks.task.get", Map.of("taskId", taskId));
        JsonNode result = root.path("result");
        JsonNode task = result.has("task") ? result.path("task") : result;
        if (task == null || task.isMissingNode() || task.isNull()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", pickText(task, "id", "ID"));
        payload.put("title", pickText(task, "title", "TITLE"));
        payload.put("description", firstNonBlank(
            pickText(task, "description", "DESCRIPTION"),
            pickText(task, "descriptionInBbcode", "DESCRIPTION_IN_BBCODE")
        ));
        payload.put("status", firstNonBlank(
            pickText(task, "realStatus", "REAL_STATUS"),
            pickText(task, "status", "STATUS")
        ));
        payload.put("closed_date", firstNonBlank(
            pickText(task, "closedDate", "CLOSED_DATE"),
            pickText(task, "closed_date")
        ));
        return payload;
    }

    public List<Map<String, Object>> listChecklistItems(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return List.of();
        }
        JsonNode root = call("task.checklistitem.getlist", Map.of("TASKID", taskId));
        List<JsonNode> rows = collectRows(root);
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode row : rows) {
            String id = pickText(row, "ID", "id");
            String title = pickText(row, "TITLE", "title", "TEXT", "text");
            boolean complete = parseBoolean(pickText(row, "IS_COMPLETE", "isComplete", "COMPLETE"));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            items.add(Map.of(
                "id", id,
                "title", title,
                "is_complete", complete
            ));
        }
        return items;
    }

    public void completeChecklistItem(String taskId, String itemId) {
        if (!StringUtils.hasText(taskId) || !StringUtils.hasText(itemId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId and itemId are required");
        }
        call("task.checklistitem.complete", Map.of(
            "TASKID", taskId,
            "ITEMID", itemId
        ));
    }

    public Map<String, Object> loadConnectionStatus() {
        Bitrix24Config config = localMachineIntegrationsConfigService.loadConfig().bitrix24();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", StringUtils.hasText(config.webhookUrl()));
        status.put("portal_url", config.portalUrl());
        if (!StringUtils.hasText(config.webhookUrl())) {
            status.put("reachable", false);
            status.put("message", "Webhook URL не задан в local-machine конфиге.");
            return status;
        }
        try {
            call("server.time", Map.of());
            status.put("reachable", true);
            status.put("message", "Bitrix24 webhook отвечает.");
        } catch (Exception ex) {
            status.put("reachable", false);
            status.put("message", ex.getMessage());
        }
        return status;
    }

    private JsonNode call(String method, Map<String, String> params) {
        Bitrix24Config config = localMachineIntegrationsConfigService.loadConfig().bitrix24();
        if (!StringUtils.hasText(config.webhookUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не задан Bitrix24 webhook URL в local-machine конфиге.");
        }
        String url = normalizeWebhookUrl(config.webhookUrl(), method);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodeForm(params)))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = readJson(response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, extractBitrixError(root, "Bitrix24 returned HTTP " + response.statusCode()));
            }
            if (root.has("error_description") || root.has("error")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, extractBitrixError(root, "Bitrix24 API error"));
            }
            return root;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось прочитать ответ Bitrix24: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Запрос в Bitrix24 был прерван.", ex);
        }
    }

    private JsonNode readJson(String body) throws IOException {
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String normalizeWebhookUrl(String rawUrl, String method) {
        String base = rawUrl.trim();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + method + ".json";
    }

    private String encodeForm(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            parts.add(encode(entry.getKey()) + "=" + encode(value));
        }
        return String.join("&", parts);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<JsonNode> collectRows(JsonNode root) {
        List<JsonNode> rows = new ArrayList<>();
        JsonNode result = root.path("result");
        if (result.isArray()) {
            result.forEach(rows::add);
            return rows;
        }
        if (result.has("tasks") && result.path("tasks").isArray()) {
            result.path("tasks").forEach(rows::add);
            return rows;
        }
        if (result.has("items") && result.path("items").isArray()) {
            result.path("items").forEach(rows::add);
            return rows;
        }
        if (result.has("groups") && result.path("groups").isArray()) {
            result.path("groups").forEach(rows::add);
            return rows;
        }
        if (result.isObject()) {
            result.elements().forEachRemaining(node -> {
                if (node.isArray()) {
                    node.forEach(rows::add);
                }
            });
        }
        return rows;
    }

    private String extractBitrixError(JsonNode root, String fallback) {
        String description = pickText(root, "error_description", "errorDescription");
        if (StringUtils.hasText(description)) {
            return description;
        }
        String error = pickText(root, "error");
        if (StringUtils.hasText(error)) {
            return error;
        }
        return fallback;
    }

    private boolean parseBoolean(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "y".equals(normalized) || "yes".equals(normalized);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String pickText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            if (node.has(field) && !node.path(field).isNull()) {
                String value = node.path(field).asText("");
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }
}
