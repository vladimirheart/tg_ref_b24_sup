package com.example.panel.service;

import com.example.panel.service.EmployeeDiscountAutomationCredentialService.IikoProfile;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IikoDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(IikoDirectoryService.class);

    private final EmployeeDiscountAutomationCredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IikoDirectoryService(EmployeeDiscountAutomationCredentialService credentialService,
                                ObjectMapper objectMapper) {
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public List<Map<String, Object>> loadOrganizations(String username) {
        IikoProfile profile = requireActiveProfile(username);
        String accessToken = requestAccessToken(profile);
        JsonNode root = executeGet(buildApiUrl(profile.baseUrl(), "/api/0/organization/list", Map.of(
            "access_token", accessToken
        )));
        List<Map<String, Object>> items = new ArrayList<>();
        JsonNode result = unwrapResult(root);
        if (result.isArray()) {
            for (JsonNode item : result) {
                String id = firstNonBlank(text(item, "organizationId"), text(item, "id"), text(item, "ID"));
                String name = firstNonBlank(text(item, "name"), text(item, "NAME"), text(item, "title"));
                if (StringUtils.hasText(id)) {
                    items.add(Map.of(
                        "id", id,
                        "name", StringUtils.hasText(name) ? name : id
                    ));
                }
            }
        }
        return items;
    }

    public List<Map<String, Object>> loadCategories(String username) {
        IikoProfile profile = requireProfileWithOrganization(username);
        String accessToken = requestAccessToken(profile);
        JsonNode root = executeGet(buildApiUrl(profile.baseUrl(),
            "/api/0/organization/" + encodePath(profile.organizationId()) + "/guest_categories",
            Map.of("access_token", accessToken)));
        List<Map<String, Object>> items = new ArrayList<>();
        JsonNode result = unwrapResult(root);
        if (result.isArray()) {
            for (JsonNode item : result) {
                String id = text(item, "id", "ID");
                String name = firstNonBlank(text(item, "name"), text(item, "NAME"), id);
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                items.add(Map.of(
                    "id", id,
                    "name", name,
                    "active", parseBoolean(text(item, "isActive", "active"))
                ));
            }
        }
        return items;
    }

    public List<Map<String, Object>> loadWallets(String username) {
        IikoProfile profile = requireProfileWithOrganization(username);
        String accessToken = requestAccessToken(profile);
        JsonNode root = executeGet(buildApiUrl(profile.baseUrl(),
            "/api/0/organization/" + encodePath(profile.organizationId()) + "/corporate_nutritions",
            Map.of("access_token", accessToken)));
        List<Map<String, Object>> items = new ArrayList<>();
        JsonNode result = unwrapResult(root);
        if (result.isArray()) {
            for (JsonNode item : result) {
                String programId = firstNonBlank(text(item, "id"), text(item, "ID"), text(item, "corporateNutritionId"));
                String programName = firstNonBlank(text(item, "name"), text(item, "NAME"), programId);
                if (!StringUtils.hasText(programId)) {
                    continue;
                }
                List<String> walletNames = collectWalletNames(item);
                String displayName = walletNames.isEmpty()
                    ? programName
                    : programName + " / " + String.join(", ", walletNames);
                items.add(Map.of(
                    "id", programId,
                    "name", displayName,
                    "program_name", programName,
                    "wallet_names", walletNames
                ));
            }
        }
        return items;
    }

    public MutationResult disableCorporateDiscount(String username, String phone) {
        IikoProfile profile = requireProfileWithOrganization(username);
        if (profile.selectedDiscountCategoryIds().isEmpty() && profile.selectedWalletIds().isEmpty()) {
            return new MutationResult(false, "Для активного URL-профиля не выбраны категории и кошельки для удаления.");
        }

        String accessToken = requestAccessToken(profile);
        JsonNode customer = getCustomerByPhone(profile, accessToken, phone);
        String customerId = firstNonBlank(
            text(customer, "id"),
            text(customer, "ID"),
            text(customer, "customerId"),
            text(customer, "guestId"),
            text(customer, "userId")
        );
        if (!StringUtils.hasText(customerId)) {
            return new MutationResult(false, "Гость по телефону " + phone + " не найден в iiko.");
        }

        Set<String> currentCategoryIds = collectCategoryIds(customer);
        int removedCategories = 0;
        int removedWalletPrograms = 0;
        List<String> actions = new ArrayList<>();

        for (String categoryId : profile.selectedDiscountCategoryIds()) {
            if (!StringUtils.hasText(categoryId)) {
                continue;
            }
            if (!currentCategoryIds.isEmpty() && !currentCategoryIds.contains(categoryId)) {
                actions.add("категория " + categoryId + " уже отсутствует");
                continue;
            }
            callPost(buildApiUrl(profile.baseUrl(),
                "/api/0/customers/" + encodePath(customerId) + "/remove_category",
                Map.of(
                    "access_token", accessToken,
                    "organization", profile.organizationId(),
                    "categoryId", categoryId
                )));
            removedCategories++;
        }

        for (String walletProgramId : profile.selectedWalletIds()) {
            if (!StringUtils.hasText(walletProgramId)) {
                continue;
            }
            try {
                callPost(buildApiUrl(profile.baseUrl(),
                    "/api/0/customers/" + encodePath(customerId) + "/remove_from_nutrition_organization",
                    Map.of(
                        "access_token", accessToken,
                        "organization", profile.organizationId(),
                        "corporate_nutrition_id", walletProgramId
                    )));
                removedWalletPrograms++;
            } catch (ResponseStatusException ex) {
                if (isIgnorableRemovalError(ex.getReason())) {
                    actions.add("кошелек/программа " + walletProgramId + " уже отключены");
                    continue;
                }
                throw ex;
            }
        }

        if (removedCategories == 0 && removedWalletPrograms == 0 && actions.isEmpty()) {
            return new MutationResult(false, "Не удалось применить изменения для гостя " + phone + ".");
        }

        StringBuilder message = new StringBuilder();
        message.append("iiko: категорий снято=").append(removedCategories)
            .append(", кошельков/программ отключено=").append(removedWalletPrograms);
        if (!actions.isEmpty()) {
            message.append(". ").append(String.join("; ", actions));
        }
        return new MutationResult(true, message.toString());
    }

    public Map<String, Object> loadStatus(String username) {
        IikoProfile profile = credentialService.loadActiveIikoProfile(username);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", profile != null && StringUtils.hasText(profile.baseUrl()));
        status.put("active_profile_url", profile != null ? profile.baseUrl() : "");
        status.put("organization_id", profile != null ? profile.organizationId() : "");
        status.put("api_login", profile != null ? profile.apiLogin() : "");
        status.put("api_secret_saved", profile != null && StringUtils.hasText(profile.apiSecret()));
        status.put("selected_categories_count", profile != null ? profile.selectedDiscountCategoryIds().size() : 0);
        status.put("selected_wallets_count", profile != null ? profile.selectedWalletIds().size() : 0);
        status.put("mutation_ready",
            profile != null
                && StringUtils.hasText(profile.baseUrl())
                && StringUtils.hasText(profile.apiLogin())
                && StringUtils.hasText(profile.apiSecret())
                && StringUtils.hasText(profile.organizationId())
                && (!profile.selectedDiscountCategoryIds().isEmpty() || !profile.selectedWalletIds().isEmpty()));
        return status;
    }

    private JsonNode getCustomerByPhone(IikoProfile profile, String accessToken, String phone) {
        JsonNode root = executeGet(buildApiUrl(profile.baseUrl(), "/api/0/customers/get_customer_by_phone", Map.of(
            "access_token", accessToken,
            "organization", profile.organizationId(),
            "phone", phone
        )));
        return unwrapResult(root);
    }

    private IikoProfile requireActiveProfile(String username) {
        IikoProfile profile = credentialService.loadActiveIikoProfile(username);
        if (profile == null || !StringUtils.hasText(profile.baseUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала сохраните активный iiko URL-профиль.");
        }
        return profile;
    }

    private IikoProfile requireProfileWithOrganization(String username) {
        IikoProfile profile = requireActiveProfile(username);
        if (!StringUtils.hasText(profile.organizationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для активного iiko URL-профиля сначала выберите organization id.");
        }
        if (!StringUtils.hasText(profile.apiLogin()) || !StringUtils.hasText(profile.apiSecret())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для активного iiko URL-профиля задайте api login и api secret.");
        }
        return profile;
    }

    private String requestAccessToken(IikoProfile profile) {
        String url = buildApiUrl(profile.baseUrl(), "/api/0/auth/access_token", Map.of(
            "user_id", profile.apiLogin(),
            "user_secret", profile.apiSecret()
        ));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось получить access_token iiko: HTTP " + response.statusCode());
            }
            String body = response.body() != null ? response.body().trim() : "";
            if (!StringUtils.hasText(body)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "iiko вернул пустой access_token.");
            }
            try {
                JsonNode root = objectMapper.readTree(body);
                String token = firstNonBlank(
                    text(root, "access_token"),
                    text(root, "accessToken"),
                    root.isTextual() ? root.asText("") : ""
                );
                if (StringUtils.hasText(token)) {
                    return token;
                }
            } catch (Exception ignored) {
            }
            return body.replace("\"", "").trim();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось прочитать access_token iiko: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Запрос access_token в iiko был прерван.", ex);
        }
    }

    private JsonNode executeGet(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = readJson(response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "iiko returned HTTP " + response.statusCode() + " for " + maskUrl(url));
            }
            if (hasApiError(root)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, extractError(root));
            }
            return root;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось прочитать ответ iiko: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Запрос в iiko был прерван.", ex);
        }
    }

    private void callPost(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = readJson(response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "iiko returned HTTP " + response.statusCode() + " for " + maskUrl(url));
            }
            if (hasApiError(root)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, extractError(root));
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось прочитать ответ iiko: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Запрос в iiko был прерван.", ex);
        }
    }

    private JsonNode readJson(String body) throws IOException {
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private boolean hasApiError(JsonNode root) {
        return root != null && (root.has("error") || root.has("errorDescription") || root.has("error_description"));
    }

    private String extractError(JsonNode root) {
        return firstNonBlank(
            text(root, "errorDescription"),
            text(root, "error_description"),
            text(root, "error"),
            "Неизвестная ошибка iiko"
        );
    }

    private JsonNode unwrapResult(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (root.has("result")) {
            return root.path("result");
        }
        return root;
    }

    private String buildApiUrl(String baseUrl, String path, Map<String, String> query) {
        String base = baseUrl != null ? baseUrl.trim() : "";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        StringBuilder result = new StringBuilder(base).append(path);
        if (query != null && !query.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, String> entry : query.entrySet()) {
                parts.add(encode(entry.getKey()) + "=" + encode(entry.getValue() != null ? entry.getValue() : ""));
            }
            result.append("?").append(String.join("&", parts));
        }
        return result.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private List<String> collectWalletNames(JsonNode programNode) {
        LinkedHashSet<String> walletNames = new LinkedHashSet<>();
        walkWallets(programNode, walletNames);
        return new ArrayList<>(walletNames);
    }

    private void walkWallets(JsonNode node, Set<String> walletNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                walkWallets(item, walletNames);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        boolean looksLikeWallet = StringUtils.hasText(firstNonBlank(text(node, "walletId"), text(node, "id")))
            && StringUtils.hasText(firstNonBlank(text(node, "walletName"), text(node, "name")));
        if (looksLikeWallet) {
            walletNames.add(firstNonBlank(text(node, "walletName"), text(node, "name")));
        }
        node.fields().forEachRemaining(entry -> walkWallets(entry.getValue(), walletNames));
    }

    private Set<String> collectCategoryIds(JsonNode root) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        walkCategories(root, ids);
        return ids;
    }

    private void walkCategories(JsonNode node, Set<String> ids) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                walkCategories(item, ids);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        boolean looksLikeCategory = StringUtils.hasText(text(node, "id"))
            && (node.has("isActive") || node.has("isDefaultForNewGuests"));
        if (looksLikeCategory) {
            ids.add(text(node, "id"));
        }
        node.fields().forEachRemaining(entry -> walkCategories(entry.getValue(), ids));
    }

    private boolean isIgnorableRemovalError(String message) {
        String normalized = message != null ? message.trim().toLowerCase(Locale.ROOT) : "";
        return normalized.contains("не найден")
            || normalized.contains("not found")
            || normalized.contains("already")
            || normalized.contains("отсутств")
            || normalized.contains("не состоит")
            || normalized.contains("does not")
            || normalized.contains("не подключ");
    }

    private boolean parseBoolean(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "y".equals(normalized);
    }

    private String text(JsonNode node, String... fields) {
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
