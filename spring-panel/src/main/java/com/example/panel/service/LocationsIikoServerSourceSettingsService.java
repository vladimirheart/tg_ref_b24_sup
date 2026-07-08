package com.example.panel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocationsIikoServerSourceSettingsService {

    public static final String SETTINGS_KEY = "locations_iiko_server_sources";
    private static final String SHA1_PATTERN = "(?i)^[0-9a-f]{40}$";

    public List<LocationIikoServerSource> loadForRuntime(Map<String, Object> settings) {
        return parseSources(settings != null ? settings.get(SETTINGS_KEY) : null, true, Map.of());
    }

    public List<Map<String, Object>> loadForClient(Map<String, Object> settings) {
        return loadForRuntime(settings).stream()
                .map(LocationIikoServerSource::toClientMap)
                .toList();
    }

    public boolean applyPayload(Map<String, Object> payload, Map<String, Object> settings) {
        if (payload == null || settings == null || !payload.containsKey(SETTINGS_KEY)) {
            return false;
        }
        List<LocationIikoServerSource> current = loadForRuntime(settings);
        Map<String, String> currentSecretsById = new LinkedHashMap<>();
        for (LocationIikoServerSource source : current) {
            if (StringUtils.hasText(source.id())) {
                currentSecretsById.put(source.id(), source.apiSecret());
            }
        }
        List<LocationIikoServerSource> updated = parseSources(payload.get(SETTINGS_KEY), false, currentSecretsById);
        settings.put(SETTINGS_KEY, updated.stream().map(LocationIikoServerSource::toStorageMap).toList());
        return true;
    }

    private List<LocationIikoServerSource> parseSources(Object raw,
                                                        boolean allowRawSecrets,
                                                        Map<String, String> currentSecretsById) {
        List<LocationIikoServerSource> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String id = text(map.get("id"));
            if (!StringUtils.hasText(id)) {
                id = UUID.randomUUID().toString();
            }
            String name = text(map.get("name"));
            String baseUrl = normalizeUrl(text(map.get("base_url"), map.get("baseUrl")));
            String apiLogin = text(map.get("api_login"), map.get("apiLogin"));
            boolean enabled = parseBoolean(map.get("enabled"), true);
            boolean hasNewSecretValue = hasNonBlankSecretValue(map);
            String apiSecret = allowRawSecrets
                    ? text(map.get("api_secret"), map.get("apiSecret"))
                    : resolveSecret(map, currentSecretsById.getOrDefault(id, ""));
            if (!allowRawSecrets && hasNewSecretValue) {
                apiSecret = normalizeSha1Secret(apiSecret, StringUtils.hasText(name) ? name : baseUrl);
            }
            if (!StringUtils.hasText(name) && StringUtils.hasText(baseUrl)) {
                name = baseUrl;
            }
            if (!StringUtils.hasText(name)
                    && !StringUtils.hasText(baseUrl)
                    && !StringUtils.hasText(apiLogin)
                    && !StringUtils.hasText(apiSecret)) {
                continue;
            }
            result.add(new LocationIikoServerSource(id, name, baseUrl, apiLogin, apiSecret, enabled));
        }
        return result;
    }

    private String resolveSecret(Map<?, ?> map, String fallback) {
        boolean secretTouched = map.containsKey("api_secret") || map.containsKey("apiSecret");
        if (!secretTouched) {
            return fallback;
        }
        String value = text(map.get("api_secret"), map.get("apiSecret"));
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean hasNonBlankSecretValue(Map<?, ?> map) {
        if (map == null) {
            return false;
        }
        return StringUtils.hasText(text(map.get("api_secret"), map.get("apiSecret")));
    }

    private String normalizeSha1Secret(String rawValue, String sourceName) {
        String value = text(rawValue);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches(SHA1_PATTERN)) {
            String suffix = StringUtils.hasText(sourceName) ? " для источника «" + sourceName + "»" : "";
            throw new IllegalArgumentException(
                    "SHA-1 пароль" + suffix + " должен содержать ровно 40 символов hex (0-9, a-f)"
            );
        }
        return normalized;
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String value = text(raw);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return !"false".equalsIgnoreCase(value) && !"0".equals(value) && !"off".equalsIgnoreCase(value);
    }

    private String normalizeUrl(String raw) {
        String value = text(raw);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        while (normalized.toLowerCase(Locale.ROOT).endsWith("/resto")) {
            normalized = normalized.substring(0, normalized.length() - "/resto".length());
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
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

    public record LocationIikoServerSource(String id,
                                           String name,
                                           String baseUrl,
                                           String apiLogin,
                                           String apiSecret,
                                           boolean enabled) {

        public Map<String, Object> toStorageMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("name", name);
            payload.put("base_url", baseUrl);
            payload.put("api_login", apiLogin);
            payload.put("api_secret", apiSecret);
            payload.put("enabled", enabled);
            return payload;
        }

        public Map<String, Object> toClientMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("name", name);
            payload.put("base_url", baseUrl);
            payload.put("api_login", apiLogin);
            payload.put("api_secret", "");
            payload.put("api_secret_saved", StringUtils.hasText(apiSecret));
            payload.put("enabled", enabled);
            return payload;
        }
    }
}
