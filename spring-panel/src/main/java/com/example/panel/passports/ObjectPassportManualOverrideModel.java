package com.example.panel.passports;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

final class ObjectPassportManualOverrideModel {

    Map<String, Object> markManualOverrides(Map<String, Object> existing,
                                            Map<String, Object> incoming) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (incoming != null) {
            result.putAll(incoming);
        }
        LinkedHashSet<String> overrides = new LinkedHashSet<>();
        Object storedOverrides = existing == null ? null : existing.get("_manual_overrides");
        if (storedOverrides instanceof List<?> list) {
            for (Object item : list) {
                String key = item == null ? "" : String.valueOf(item).trim();
                if (StringUtils.hasText(key)) {
                    overrides.add(key);
                }
            }
        }
        if (incoming != null) {
            for (Map.Entry<String, Object> entry : incoming.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
                if (!StringUtils.hasText(key) || key.startsWith("_") || "id".equals(key) || "is_new".equals(key)) {
                    continue;
                }
                Object previous = existing == null ? null : existing.get(key);
                if (!Objects.deepEquals(previous, entry.getValue())) {
                    overrides.add(key);
                }
            }
        }
        result.put("_manual_overrides", List.copyOf(overrides));
        return result;
    }
}
