package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
        Map<String, IikoProfile> profiles = parseProfiles(payload.get("iiko_profiles"));
        if (profiles.isEmpty()) {
            IikoProfile legacyProfile = parseLegacyIiko(payload.get("iiko"));
            if (StringUtils.hasText(legacyProfile.baseUrl())) {
                profiles.put(normalizeProfileKey(legacyProfile.baseUrl()), legacyProfile);
            }
        }
        String activeProfileUrl = normalizeProfileKey(text(
            payload.get("active_iiko_profile_url"),
            payload.get("activeIikoProfileUrl")
        ));
        if (!StringUtils.hasText(activeProfileUrl) && !profiles.isEmpty()) {
            activeProfileUrl = profiles.keySet().iterator().next();
        }
        if (StringUtils.hasText(activeProfileUrl) && !profiles.containsKey(activeProfileUrl)) {
            activeProfileUrl = !profiles.isEmpty() ? profiles.keySet().iterator().next() : "";
        }
        return new EmployeeDiscountAutomationCredentials(
            parseBitrix(payload.get("bitrix24")),
            activeProfileUrl,
            profiles
        );
    }

    public IikoProfile loadActiveIikoProfile(String username) {
        return loadForUser(username).activeIikoProfile();
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

        Bitrix24Credentials bitrix = source.containsKey("bitrix24")
            ? mergeBitrix(current.bitrix24(), source.get("bitrix24"))
            : current.bitrix24();

        LinkedHashMap<String, IikoProfile> profiles = new LinkedHashMap<>(current.iikoProfiles());
        String activeProfileUrl = normalizeProfileKey(text(
            source.get("select_profile_url"),
            source.get("active_iiko_profile_url"),
            current.activeIikoProfileUrl()
        ));

        if (source.containsKey("iiko_profile")) {
            Map<String, Object> profileNode = asMap(source.get("iiko_profile"));
            String requestedBaseUrl = text(profileNode.get("base_url"), profileNode.get("baseUrl"));
            String profileKey = normalizeProfileKey(requestedBaseUrl);
            if (!StringUtils.hasText(profileKey) && StringUtils.hasText(activeProfileUrl)) {
                profileKey = activeProfileUrl;
            }
            if (!StringUtils.hasText(profileKey)) {
                throw new IllegalArgumentException("Для профиля iiko обязательно укажите URL.");
            }
            IikoProfile merged = mergeProfile(profiles.get(profileKey), profileNode, profileKey);
            profiles.put(profileKey, merged);
            activeProfileUrl = profileKey;
        }

        String removeProfileUrl = normalizeProfileKey(text(source.get("remove_profile_url")));
        if (StringUtils.hasText(removeProfileUrl)) {
            profiles.remove(removeProfileUrl);
            if (removeProfileUrl.equals(activeProfileUrl)) {
                activeProfileUrl = profiles.isEmpty() ? "" : profiles.keySet().iterator().next();
            }
        }

        if (!StringUtils.hasText(activeProfileUrl) && !profiles.isEmpty()) {
            activeProfileUrl = profiles.keySet().iterator().next();
        }

        EmployeeDiscountAutomationCredentials updated = new EmployeeDiscountAutomationCredentials(
            bitrix,
            activeProfileUrl,
            profiles
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

    private IikoProfile parseLegacyIiko(Object raw) {
        Map<String, Object> node = asMap(raw);
        return new IikoProfile(
            normalizeProfileKey(text(node.get("base_url"), node.get("baseUrl"))),
            text(node.get("api_login"), node.get("apiLogin"), node.get("login")),
            text(node.get("api_secret"), node.get("apiSecret"), node.get("password")),
            text(node.get("organization_id"), node.get("organizationId")),
            normalizeStringList(node.get("selected_discount_category_ids")),
            normalizeStringList(node.get("selected_wallet_ids"))
        );
    }

    private Map<String, IikoProfile> parseProfiles(Object raw) {
        Map<String, Object> node = asMap(raw);
        LinkedHashMap<String, IikoProfile> profiles = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            Map<String, Object> profileNode = asMap(entry.getValue());
            String baseUrl = normalizeProfileKey(text(
                profileNode.get("base_url"),
                profileNode.get("baseUrl"),
                entry.getKey()
            ));
            if (!StringUtils.hasText(baseUrl)) {
                continue;
            }
            profiles.put(baseUrl, new IikoProfile(
                baseUrl,
                text(profileNode.get("api_login"), profileNode.get("apiLogin"), profileNode.get("login")),
                text(profileNode.get("api_secret"), profileNode.get("apiSecret"), profileNode.get("password")),
                text(profileNode.get("organization_id"), profileNode.get("organizationId")),
                normalizeStringList(profileNode.get("selected_discount_category_ids")),
                normalizeStringList(profileNode.get("selected_wallet_ids"))
            ));
        }
        return profiles;
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

    private IikoProfile mergeProfile(IikoProfile current, Map<String, Object> node, String profileKey) {
        IikoProfile fallback = current != null ? current : IikoProfile.empty(profileKey);
        return new IikoProfile(
            normalizeProfileKey(resolvePlain(node, fallback.baseUrl(), "base_url", "baseUrl")),
            resolvePlain(node, fallback.apiLogin(), "api_login", "apiLogin", "login"),
            resolveSecret(node, fallback.apiSecret(), "api_secret", "apiSecret", "password"),
            resolvePlain(node, fallback.organizationId(), "organization_id", "organizationId"),
            normalizeStringListOrDefault(node.get("selected_discount_category_ids"), fallback.selectedDiscountCategoryIds()),
            normalizeStringListOrDefault(node.get("selected_wallet_ids"), fallback.selectedWalletIds())
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

    private List<String> normalizeStringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String value = text(item);
                if (StringUtils.hasText(value) && !values.contains(value)) {
                    values.add(value);
                }
            }
            return values;
        }
        if (raw instanceof String text) {
            for (String chunk : text.split("[\\r\\n,;]+")) {
                if (StringUtils.hasText(chunk)) {
                    String value = chunk.trim();
                    if (!values.contains(value)) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    private List<String> normalizeStringListOrDefault(Object raw, List<String> fallback) {
        if (raw == null) {
            return fallback;
        }
        List<String> values = normalizeStringList(raw);
        return values.isEmpty() ? fallback : values;
    }

    private String normalizeProfileKey(String rawUrl) {
        String value = text(rawUrl);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
                                                        String activeIikoProfileUrl,
                                                        Map<String, IikoProfile> iikoProfiles) {

        public static EmployeeDiscountAutomationCredentials empty() {
            return new EmployeeDiscountAutomationCredentials(Bitrix24Credentials.empty(), "", Map.of());
        }

        public IikoProfile activeIikoProfile() {
            if (!StringUtils.hasText(activeIikoProfileUrl)) {
                return iikoProfiles.values().stream().findFirst().orElse(null);
            }
            IikoProfile profile = iikoProfiles.get(activeIikoProfileUrl);
            return profile != null ? profile : iikoProfiles.values().stream().findFirst().orElse(null);
        }

        public Map<String, Object> toStorageMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bitrix24", bitrix24.toStorageMap());
            payload.put("active_iiko_profile_url", activeIikoProfileUrl);
            Map<String, Object> profiles = new LinkedHashMap<>();
            for (Map.Entry<String, IikoProfile> entry : iikoProfiles.entrySet()) {
                profiles.put(entry.getKey(), entry.getValue().toStorageMap());
            }
            payload.put("iiko_profiles", profiles);
            return payload;
        }

        public Map<String, Object> toClientMap(String username) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scope", username != null ? username.trim() : "");
            payload.put("bitrix24", bitrix24.toClientMap());
            payload.put("active_iiko_profile_url", activeIikoProfileUrl);
            payload.put("iiko_profiles", iikoProfiles.values().stream().map(profile -> profile.toClientMap(activeIikoProfileUrl)).toList());
            payload.put("iiko", activeIikoProfile() != null ? activeIikoProfile().toClientMap(activeIikoProfileUrl) : Map.of());
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

    public record IikoProfile(String baseUrl,
                              String apiLogin,
                              String apiSecret,
                              String organizationId,
                              List<String> selectedDiscountCategoryIds,
                              List<String> selectedWalletIds) {

        public static IikoProfile empty(String baseUrl) {
            return new IikoProfile(baseUrl, "", "", "", List.of(), List.of());
        }

        public Map<String, Object> toStorageMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("base_url", baseUrl);
            payload.put("api_login", apiLogin);
            payload.put("api_secret", apiSecret);
            payload.put("organization_id", organizationId);
            payload.put("selected_discount_category_ids", selectedDiscountCategoryIds);
            payload.put("selected_wallet_ids", selectedWalletIds);
            return payload;
        }

        public Map<String, Object> toClientMap(String activeProfileUrl) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("profile_key", baseUrl);
            payload.put("base_url", baseUrl);
            payload.put("api_login", apiLogin);
            payload.put("api_secret_saved", StringUtils.hasText(apiSecret));
            payload.put("organization_id", organizationId);
            payload.put("selected_discount_category_ids", selectedDiscountCategoryIds);
            payload.put("selected_wallet_ids", selectedWalletIds);
            payload.put("active", StringUtils.hasText(activeProfileUrl) && activeProfileUrl.equals(baseUrl));
            return payload;
        }
    }
}
