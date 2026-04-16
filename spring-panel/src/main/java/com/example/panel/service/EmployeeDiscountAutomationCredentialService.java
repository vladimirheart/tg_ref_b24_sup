package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmployeeDiscountAutomationCredentialService {

    private static final String PARAM_TYPE = "employee_discount_automation_credentials.v1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EmployeeDiscountAutomationCredentialService(JdbcTemplate jdbcTemplate,
                                                       ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public EmployeeDiscountAutomationCredentials loadForUser(String username) {
        Map<String, Object> payload = loadStoredPayload(username);
        return new EmployeeDiscountAutomationCredentials(
            parseBitrix(payload.get("bitrix24")),
            parseIiko(payload.get("iiko"))
        );
    }

    public Map<String, Object> loadClientView(String username) {
        return loadForUser(username).toClientMap(username);
    }

    public EmployeeDiscountAutomationCredentials saveForUser(String username, Map<String, Object> payload) {
        if (!StringUtils.hasText(username)) {
            return EmployeeDiscountAutomationCredentials.empty();
        }
        EmployeeDiscountAutomationCredentials current = loadForUser(username);
        Map<String, Object> source = payload != null ? payload : Map.of();
        EmployeeDiscountAutomationCredentials updated = new EmployeeDiscountAutomationCredentials(
            mergeBitrix(current.bitrix24(), source.get("bitrix24")),
            mergeIiko(current.iiko(), source.get("iiko"))
        );
        persistForUser(username, updated);
        return updated;
    }

    private Bitrix24Credentials parseBitrix(Object raw) {
        Map<String, Object> node = asMap(raw);
        return new Bitrix24Credentials(
            text(node.get("portal_url"), node.get("portalUrl")),
            text(node.get("webhook_url"), node.get("webhookUrl"))
        );
    }

    private IikoCredentials parseIiko(Object raw) {
        Map<String, Object> node = asMap(raw);
        return new IikoCredentials(
            text(node.get("group_name"), node.get("groupName")),
            text(node.get("base_url"), node.get("baseUrl")),
            text(node.get("login")),
            text(node.get("password")),
            text(node.get("organization_id"), node.get("organizationId")),
            text(node.get("token")),
            text(node.get("categories_url"), node.get("categoriesUrl")),
            text(node.get("wallets_url"), node.get("walletsUrl")),
            text(node.get("customer_lookup_url"), node.get("customerLookupUrl")),
            text(node.get("customer_update_url"), node.get("customerUpdateUrl"))
        );
    }

    private Bitrix24Credentials mergeBitrix(Bitrix24Credentials current, Object raw) {
        Map<String, Object> node = asMap(raw);
        if (node.isEmpty()) {
            return current;
        }
        return new Bitrix24Credentials(
            resolvePlain(node, current.portalUrl(), "portal_url", "portalUrl"),
            resolveSecret(node, current.webhookUrl(), "webhook_url", "webhookUrl")
        );
    }

    private IikoCredentials mergeIiko(IikoCredentials current, Object raw) {
        Map<String, Object> node = asMap(raw);
        if (node.isEmpty()) {
            return current;
        }
        return new IikoCredentials(
            resolvePlain(node, current.groupName(), "group_name", "groupName"),
            resolvePlain(node, current.baseUrl(), "base_url", "baseUrl"),
            resolvePlain(node, current.login(), "login"),
            resolveSecret(node, current.password(), "password"),
            resolvePlain(node, current.organizationId(), "organization_id", "organizationId"),
            resolveSecret(node, current.token(), "token"),
            resolvePlain(node, current.categoriesUrl(), "categories_url", "categoriesUrl"),
            resolvePlain(node, current.walletsUrl(), "wallets_url", "walletsUrl"),
            resolvePlain(node, current.customerLookupUrl(), "customer_lookup_url", "customerLookupUrl"),
            resolvePlain(node, current.customerUpdateUrl(), "customer_update_url", "customerUpdateUrl")
        );
    }

    private String resolvePlain(Map<String, Object> node, String fallback, String... keys) {
        for (String key : keys) {
            if (node.containsKey(key)) {
                return text(node.get(key));
            }
        }
        return fallback;
    }

    private String resolveSecret(Map<String, Object> node, String fallback, String... keys) {
        for (String key : keys) {
            if (node.containsKey(key)) {
                String value = text(node.get(key));
                return StringUtils.hasText(value) ? value : fallback;
            }
        }
        return fallback;
    }

    private Map<String, Object> loadStoredPayload(String username) {
        if (!StringUtils.hasText(username)) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT extra_json FROM settings_parameters WHERE param_type = ? AND lower(value) = lower(?) AND is_deleted = 0 LIMIT 1",
                PARAM_TYPE,
                username.trim()
            );
            if (rows.isEmpty()) {
                return Map.of();
            }
            Object rawJson = rows.get(0).get("extra_json");
            if (!(rawJson instanceof String json) || !StringUtils.hasText(json)) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void persistForUser(String username, EmployeeDiscountAutomationCredentials credentials) {
        Map<String, Object> payload = credentials.toStorageMap();
        payload.put("updated_at_utc", OffsetDateTime.now(ZoneOffset.UTC).toString());
        String json = writeJson(payload);
        if (json == null) {
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM settings_parameters WHERE param_type = ? AND lower(value) = lower(?) AND is_deleted = 0 LIMIT 1",
                PARAM_TYPE,
                username.trim()
            );
            if (rows.isEmpty()) {
                jdbcTemplate.update(
                    "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, 'Активен', 0, ?)",
                    PARAM_TYPE,
                    username.trim(),
                    json
                );
            } else {
                Number id = (Number) rows.get(0).get("id");
                jdbcTemplate.update(
                    "UPDATE settings_parameters SET state = 'Активен', is_deleted = 0, deleted_at = NULL, extra_json = ? WHERE id = ?",
                    json,
                    id != null ? id.longValue() : null
                );
            }
        } catch (DataAccessException ignored) {
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> asMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return objectMapper.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private String text(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    public record EmployeeDiscountAutomationCredentials(Bitrix24Credentials bitrix24,
                                                        IikoCredentials iiko) {

        public static EmployeeDiscountAutomationCredentials empty() {
            return new EmployeeDiscountAutomationCredentials(Bitrix24Credentials.empty(), IikoCredentials.empty());
        }

        public Map<String, Object> toStorageMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bitrix24", bitrix24.toStorageMap());
            payload.put("iiko", iiko.toStorageMap());
            return payload;
        }

        public Map<String, Object> toClientMap(String username) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scope", username != null ? username.trim() : "");
            payload.put("bitrix24", bitrix24.toClientMap());
            payload.put("iiko", iiko.toClientMap());
            return payload;
        }
    }

    public record Bitrix24Credentials(String portalUrl,
                                      String webhookUrl) {

        public static Bitrix24Credentials empty() {
            return new Bitrix24Credentials("", "");
        }

        public Map<String, Object> toStorageMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("portal_url", portalUrl);
            payload.put("webhook_url", webhookUrl);
            return payload;
        }

        public Map<String, Object> toClientMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("portal_url", portalUrl);
            payload.put("webhook_saved", StringUtils.hasText(webhookUrl));
            return payload;
        }
    }

    public record IikoCredentials(String groupName,
                                  String baseUrl,
                                  String login,
                                  String password,
                                  String organizationId,
                                  String token,
                                  String categoriesUrl,
                                  String walletsUrl,
                                  String customerLookupUrl,
                                  String customerUpdateUrl) {

        public static IikoCredentials empty() {
            return new IikoCredentials("", "", "", "", "", "", "", "", "", "");
        }

        public Map<String, Object> toStorageMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("group_name", groupName);
            payload.put("base_url", baseUrl);
            payload.put("login", login);
            payload.put("password", password);
            payload.put("organization_id", organizationId);
            payload.put("token", token);
            payload.put("categories_url", categoriesUrl);
            payload.put("wallets_url", walletsUrl);
            payload.put("customer_lookup_url", customerLookupUrl);
            payload.put("customer_update_url", customerUpdateUrl);
            return payload;
        }

        public Map<String, Object> toClientMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("group_name", groupName);
            payload.put("base_url", baseUrl);
            payload.put("login", login);
            payload.put("password_saved", StringUtils.hasText(password));
            payload.put("organization_id", organizationId);
            payload.put("token_saved", StringUtils.hasText(token));
            payload.put("categories_url", categoriesUrl);
            payload.put("wallets_url", walletsUrl);
            payload.put("customer_lookup_url", customerLookupUrl);
            payload.put("customer_update_url", customerUpdateUrl);
            payload.put("auth_mode", resolveAuthMode());
            return payload;
        }

        public String resolveAuthMode() {
            if (StringUtils.hasText(token)) {
                return "bearer";
            }
            if (StringUtils.hasText(login) && StringUtils.hasText(password)) {
                return "basic";
            }
            return "none";
        }
    }
}
