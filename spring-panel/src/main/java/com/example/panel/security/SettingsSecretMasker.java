package com.example.panel.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;

/** Read-only settings responses expose saved-secret indicators without credentials. */
public final class SettingsSecretMasker {
    private SettingsSecretMasker() {}

    public static Object forBlock(Object value, String block, Authentication actor) {
        return PanelAccessPolicy.canEdit(actor, block) ? value : redact(value);
    }

    public static Map<String, Object> settings(Map<String, Object> values, Authentication actor) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String block = PanelAccessPolicy.payloadBlock(key);
            result.put(key, block == null ? redact(value) : forBlock(value, block, actor));
        });
        return result;
    }

    public static Map<String, Object> section(Map<String, Object> values, Authentication actor) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String block = switch (key) {
                case "botSettings" -> "bot_templates";
                case "integrationNetwork", "integrationNetworkProfiles", "networkProfiles" -> "network_profiles";
                case "iikoServerSources", "iikoSyncSettings", "tree" -> "locations";
                case "netBoxSyncSettings", "netBoxSyncStatus" -> "netbox";
                case "reportingConfig" -> "reporting";
                case "managerLocationBindings" -> "manager_bindings";
                default -> null;
            };
            result.put(key, block == null ? redact(value) : forBlock(value, block, actor));
        });
        return result;
    }

    public static Object redact(Object raw) {
        if (raw instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((name, value) -> {
                String key = String.valueOf(name);
                String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                boolean secret = normalized.endsWith("token") || normalized.endsWith("password")
                        || normalized.endsWith("secret") || normalized.endsWith("apikey")
                        || normalized.equals("authorization") || normalized.equals("apilogin");
                if (secret && value instanceof String text && !normalized.startsWith("masked")) {
                    result.put(key, "");
                    result.putIfAbsent(key + "_saved", !text.isEmpty());
                } else result.put(key, redact(value));
            });
            return result;
        }
        if (raw instanceof List<?> source) return source.stream().map(SettingsSecretMasker::redact).toList();
        return raw;
    }
}
