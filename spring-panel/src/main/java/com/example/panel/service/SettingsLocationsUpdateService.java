package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettingsLocationsUpdateService {

    private final SharedConfigService sharedConfigService;
    private final SettingsParameterService settingsParameterService;

    public SettingsLocationsUpdateService(SharedConfigService sharedConfigService,
                                          SettingsParameterService settingsParameterService) {
        this.sharedConfigService = sharedConfigService;
        this.settingsParameterService = settingsParameterService;
    }

    public boolean applyLocationsUpdate(Map<String, Object> payload) {
        if (!payload.containsKey("locations")) {
            return false;
        }
        Map<String, Object> locationsPayload = normalizeLocationsPayload(payload.get("locations"));
        if (locationsPayload == null) {
            return false;
        }
        sharedConfigService.saveLocations(locationsPayload);
        settingsParameterService.syncParametersFromLocationsPayload(locationsPayload);
        return true;
    }

    private Map<String, Object> normalizeLocationsPayload(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        boolean hasCanonicalKey = map.containsKey("tree")
                || map.containsKey("statuses")
                || map.containsKey("city_meta")
                || map.containsKey("location_meta");
        if (!hasCanonicalKey) {
            return null;
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("tree", normalizeTree(map.get("tree")));
        normalized.put("statuses", normalizeStringObjectMap(map.get("statuses")));
        normalized.put("city_meta", normalizeMetaMap(map.get("city_meta")));
        normalized.put("location_meta", normalizeMetaMap(map.get("location_meta")));
        return normalized;
    }

    private Map<String, Object> normalizeTree(Object raw) {
        if (!(raw instanceof Map<?, ?> businesses)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        businesses.forEach((businessKey, typesRaw) -> {
            String business = text(businessKey);
            if (!StringUtils.hasText(business) || !(typesRaw instanceof Map<?, ?> types)) {
                return;
            }
            Map<String, Object> normalizedTypes = new LinkedHashMap<>();
            types.forEach((typeKey, citiesRaw) -> {
                String type = text(typeKey);
                if (!StringUtils.hasText(type) || !(citiesRaw instanceof Map<?, ?> cities)) {
                    return;
                }
                Map<String, Object> normalizedCities = new LinkedHashMap<>();
                cities.forEach((cityKey, locationsRaw) -> {
                    String city = text(cityKey);
                    if (!StringUtils.hasText(city)) {
                        return;
                    }
                    normalizedCities.put(city, normalizeStringList(locationsRaw));
                });
                normalizedTypes.put(type, normalizedCities);
            });
            normalized.put(business, normalizedTypes);
        });
        return normalized;
    }

    private Map<String, Object> normalizeMetaMap(Object raw) {
        if (!(raw instanceof Map<?, ?> metaMap)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        metaMap.forEach((pathKey, attrsRaw) -> {
            String path = text(pathKey);
            if (!StringUtils.hasText(path) || !(attrsRaw instanceof Map<?, ?> attrs)) {
                return;
            }
            Map<String, Object> normalizedAttrs = new LinkedHashMap<>();
            String country = text(attrs.get("country"));
            String partnerType = text(attrs.get("partner_type"), attrs.get("partnerType"));
            if (StringUtils.hasText(country)) {
                normalizedAttrs.put("country", country);
            }
            if (StringUtils.hasText(partnerType)) {
                normalizedAttrs.put("partner_type", partnerType);
            }
            if (!normalizedAttrs.isEmpty()) {
                normalized.put(path, normalizedAttrs);
            }
        });
        return normalized;
    }

    private Map<String, Object> normalizeStringObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String normalizedKey = text(key);
            String normalizedValue = text(value);
            if (StringUtils.hasText(normalizedKey) && StringUtils.hasText(normalizedValue)) {
                normalized.put(normalizedKey, normalizedValue);
            }
        });
        return normalized;
    }

    private List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof Iterable<?> values)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (Object value : values) {
            String item = text(value);
            if (StringUtils.hasText(item) && !normalized.contains(item)) {
                normalized.add(item);
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
            String normalized = String.valueOf(value).trim();
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "";
    }
}
