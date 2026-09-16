package com.example.panel.passports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ObjectPassportEquipmentCatalogQuery {

    List<Map<String, Object>> listCandidates(List<Map<String, Object>> passportPayloads) {
        List<Map<String, Object>> safePayloads = passportPayloads == null ? List.of() : passportPayloads;
        LinkedHashMap<String, Map<String, Object>> candidates = new LinkedHashMap<>();
        for (Map<String, Object> passportPayload : safePayloads) {
            Set<String> objectKeys = new LinkedHashSet<>();
            Object rawEquipment = passportPayload.get("equipment");
            if (!(rawEquipment instanceof List<?> equipmentItems)) {
                continue;
            }
            for (Object rawItem : equipmentItems) {
                if (!(rawItem instanceof Map<?, ?> item)) {
                    continue;
                }
                if (isArchivedEquipment(item)) {
                    continue;
                }
                String type = firstNonBlank(item.get("equipment_type"), item.get("type"));
                String vendor = firstNonBlank(item.get("vendor"), item.get("equipment_vendor"));
                String model = firstNonBlank(item.get("model"), item.get("equipment_model"));
                if (!StringUtils.hasText(type) || !StringUtils.hasText(model)) {
                    continue;
                }
                String key = normalizeLookupValue(type) + "::" + normalizeLookupValue(vendor) + "::" + normalizeLookupValue(model);
                Map<String, Object> candidate = candidates.computeIfAbsent(key, ignored -> {
                    LinkedHashMap<String, Object> value = new LinkedHashMap<>();
                    value.put("equipment_type", type);
                    value.put("equipment_vendor", vendor);
                    value.put("equipment_model", model);
                    value.put("photo_url", "");
                    value.put("serial_number", "");
                    value.put("accessories", firstNonBlank(item.get("accessories"), item.get("additional_equipment")));
                    value.put("usage_count", 0);
                    value.put("object_count", 0);
                    value.put("catalog_ids", new ArrayList<Long>());
                    value.put("discovered", true);
                    value.put("source", "passports");
                    return value;
                });
                Long catalogId = positiveLongValue(item.get("catalog_id"));
                if (catalogId != null) {
                    @SuppressWarnings("unchecked")
                    List<Long> catalogIds = (List<Long>) candidate.get("catalog_ids");
                    if (!catalogIds.contains(catalogId)) {
                        catalogIds.add(catalogId);
                    }
                }
                int usage = candidate.get("usage_count") instanceof Number number ? number.intValue() : 0;
                candidate.put("usage_count", usage + 1);
                if (objectKeys.add(key)) {
                    int objectCount = candidate.get("object_count") instanceof Number number ? number.intValue() : 0;
                    candidate.put("object_count", objectCount + 1);
                }
            }
        }
        return new ArrayList<>(candidates.values());
    }

    private boolean isArchivedEquipment(Map<?, ?> item) {
        if (item == null) {
            return false;
        }
        Object raw = item.get("archived");
        return Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(stringValue(raw));
    }

    private Long positiveLongValue(Object raw) {
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 0 ? value : null;
        }
        try {
            long value = Long.parseLong(stringValue(raw));
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            String normalized = stringValue(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private String normalizeLookupValue(Object raw) {
        String value = stringValue(raw);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace('Ё', 'Е')
                .replace('ё', 'е')
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

}
