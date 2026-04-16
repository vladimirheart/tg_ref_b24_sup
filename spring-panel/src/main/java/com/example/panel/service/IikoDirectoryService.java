package com.example.panel.service;

import com.example.panel.service.LocalMachineIntegrationsConfigService.IikoLocalConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IikoDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(IikoDirectoryService.class);

    private final LocalMachineIntegrationsConfigService localMachineIntegrationsConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IikoDirectoryService(LocalMachineIntegrationsConfigService localMachineIntegrationsConfigService,
                                ObjectMapper objectMapper) {
        this.localMachineIntegrationsConfigService = localMachineIntegrationsConfigService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public List<Map<String, Object>> loadCategories() {
        return loadNamedDirectory("categories", localMachineIntegrationsConfigService.loadConfig().iiko().categoriesUrl());
    }

    public List<Map<String, Object>> loadWallets() {
        return loadNamedDirectory("wallets", localMachineIntegrationsConfigService.loadConfig().iiko().walletsUrl());
    }

    public MutationResult disableCorporateDiscount(String phone,
                                                   EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings settings) {
        IikoLocalConfig config = localMachineIntegrationsConfigService.loadConfig().iiko();
        if (!StringUtils.hasText(config.customerLookupUrl()) || !StringUtils.hasText(config.customerUpdateUrl())) {
            return new MutationResult(false,
                "Боевой iiko-мутатор пока не сконфигурирован: задайте customer_lookup_url и customer_update_url в local-machine конфиге.");
        }
        return new MutationResult(false,
            "Боевой iiko-мутатор ожидает точный payload из iikocard-документации. Пока доступны discovery справочников и dry-run.");
    }

    public Map<String, Object> loadStatus() {
        IikoLocalConfig config = localMachineIntegrationsConfigService.loadConfig().iiko();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", StringUtils.hasText(config.baseUrl()) || StringUtils.hasText(config.categoriesUrl()) || StringUtils.hasText(config.walletsUrl()));
        status.put("group_name", config.groupName());
        status.put("organization_id", config.organizationId());
        status.put("categories_url", maskUrl(config.categoriesUrl()));
        status.put("wallets_url", maskUrl(config.walletsUrl()));
        status.put("mutation_ready",
            StringUtils.hasText(config.customerLookupUrl()) && StringUtils.hasText(config.customerUpdateUrl()));
        return status;
    }

    private List<Map<String, Object>> loadNamedDirectory(String label, String url) {
        if (!StringUtils.hasText(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Не задан " + label + "_url в local-machine конфиге iiko.");
        }
        JsonNode root = executeGet(resolveUrl(localMachineIntegrationsConfigService.loadConfig().iiko(), url));
        List<Map<String, Object>> entries = collectNamedObjects(root);
        if (entries.isEmpty()) {
            log.warn("Iiko {} discovery returned no recognizable id/name pairs", label);
        }
        return entries;
    }

    private JsonNode executeGet(String url) {
        IikoLocalConfig config = localMachineIntegrationsConfigService.loadConfig().iiko();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .GET();
        if (StringUtils.hasText(config.token())) {
            builder.header("Authorization", "Bearer " + config.token().trim());
        }
        if (StringUtils.hasText(config.login()) && StringUtils.hasText(config.password())) {
            String basic = Base64.getEncoder()
                .encodeToString((config.login().trim() + ":" + config.password().trim()).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "iiko returned HTTP " + response.statusCode() + " for " + maskUrl(url));
            }
            if (!StringUtils.hasText(response.body())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось прочитать ответ iiko: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Запрос в iiko был прерван.", ex);
        }
    }

    private String resolveUrl(IikoLocalConfig config, String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "";
        }
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl.trim();
        }
        if (!StringUtils.hasText(config.baseUrl())) {
            return rawUrl.trim();
        }
        String base = config.baseUrl().trim();
        if (base.endsWith("/") && rawUrl.startsWith("/")) {
            return base.substring(0, base.length() - 1) + rawUrl.trim();
        }
        if (!base.endsWith("/") && !rawUrl.startsWith("/")) {
            return base + "/" + rawUrl.trim();
        }
        return base + rawUrl.trim();
    }

    private List<Map<String, Object>> collectNamedObjects(JsonNode root) {
        List<Map<String, Object>> entries = new ArrayList<>();
        walk(root, entries);
        return entries;
    }

    private void walk(JsonNode node, List<Map<String, Object>> entries) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                walk(item, entries);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String id = firstNonBlank(text(node, "id"), text(node, "ID"), text(node, "walletId"), text(node, "categoryId"));
        String name = firstNonBlank(text(node, "name"), text(node, "NAME"), text(node, "title"), text(node, "label"), text(node, "caption"));
        if (StringUtils.hasText(id) && StringUtils.hasText(name) && entries.stream().noneMatch(item -> id.equals(String.valueOf(item.get("id"))))) {
            entries.add(Map.of("id", id, "name", name));
        }
        node.fields().forEachRemaining(entry -> walk(entry.getValue(), entries));
    }

    private String text(JsonNode node, String field) {
        if (node != null && node.has(field) && !node.path(field).isNull()) {
            String value = node.path(field).asText("");
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
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

    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String normalized = url.trim();
        int queryIndex = normalized.indexOf('?');
        return queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
    }

    public record MutationResult(boolean success, String message) {
    }
}
