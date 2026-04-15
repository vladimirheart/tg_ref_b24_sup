package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SettingsParameterService {

    private final JdbcTemplate jdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final ObjectMapper objectMapper;

    public SettingsParameterService(JdbcTemplate jdbcTemplate,
                                    SharedConfigService sharedConfigService,
                                    SettingsCatalogService settingsCatalogService,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> listParameters(boolean includeDeleted) {
        return fetchParametersGrouped(includeDeleted);
    }

    public Map<String, Object> createParameter(Map<String, Object> payload) {
        String paramType = stringValue(payload.get("param_type"));
        String value = stringValue(payload.get("value"));
        if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
            return Map.of("success", false, "error", "Тип и значение параметра обязательны");
        }
        String state = stringValue(payload.get("state"));
        if (!StringUtils.hasText(state)) {
            state = "Активен";
        }
        try {
            validateParameterUniqueness(paramType, value, payload, null);
        } catch (IllegalArgumentException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }
        String extraJson = buildExtraJson(payload, Set.of("param_type", "value", "state"));
        jdbcTemplate.update(
                "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, ?, 0, ?)",
                paramType, value, state, extraJson
        );
        syncLocationsFromParameters();
        return Map.of("success", true, "data", fetchParametersGrouped(true));
    }

    public Map<String, Object> updateParameter(long paramId, Map<String, Object> payload) {
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
                "SELECT param_type, value, extra_json FROM settings_parameters WHERE id = ?",
                paramId
        );
        if (existingRows.isEmpty()) {
            return Map.of("success", false, "error", "Параметр не найден");
        }
        Map<String, Object> existing = existingRows.get(0);

        String paramType = stringValue(existing.get("param_type"));
        String finalValue = payload.containsKey("value")
                ? stringValue(payload.get("value"))
                : stringValue(existing.get("value"));
        try {
            validateParameterUniqueness(paramType, finalValue, payload, paramId);
        } catch (IllegalArgumentException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }

        StringBuilder updates = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (payload.containsKey("value")) {
            updates.append("value = ?,");
            params.add(stringValue(payload.get("value")));
        }
        if (payload.containsKey("state")) {
            updates.append("state = ?,");
            params.add(stringValue(payload.get("state")));
        }
        if (payload.containsKey("is_deleted")) {
            updates.append("is_deleted = ?,");
            params.add(Boolean.TRUE.equals(payload.get("is_deleted")) ? 1 : 0);
            if (Boolean.TRUE.equals(payload.get("is_deleted"))) {
                updates.append("deleted_at = datetime('now'),");
            } else {
                updates.append("deleted_at = NULL,");
            }
        }

        String extraJson = mergeExtraJson(existing.get("extra_json"), payload, Set.of("value", "state", "is_deleted"));
        updates.append("extra_json = ?");
        params.add(extraJson);

        if (updates.length() == 0) {
            return Map.of("success", false, "error", "Нет данных для обновления");
        }

        params.add(paramId);
        jdbcTemplate.update("UPDATE settings_parameters SET " + updates + " WHERE id = ?", params.toArray());
        syncLocationsFromParameters();
        return Map.of("success", true, "data", fetchParametersGrouped(true));
    }

    public Map<String, Object> deleteParameter(long paramId) {
        int updated = jdbcTemplate.update(
                "UPDATE settings_parameters SET is_deleted = 1, deleted_at = datetime('now') WHERE id = ?",
                paramId
        );
        if (updated == 0) {
            return Map.of("success", false, "error", "Параметр не найден");
        }
        syncLocationsFromParameters();
        return Map.of("success", true, "data", fetchParametersGrouped(true));
    }

    public void syncParametersFromLocationsPayload(Object locationsPayload) {
        syncParametersFromLocations(locationsPayload);
    }

    private Map<String, Object> fetchParametersGrouped(boolean includeDeleted) {
        Map<String, Object> grouped = new LinkedHashMap<>();
        Map<String, String> types = settingsCatalogService.getParameterTypes();
        types.keySet().forEach(key -> grouped.put(key, new ArrayList<>()));

        String sql = "SELECT id, param_type, value, state, is_deleted, deleted_at, extra_json FROM settings_parameters";
        if (!includeDeleted) {
            sql += " WHERE is_deleted = 0";
        }
        sql += " ORDER BY param_type, value";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        Map<String, List<String>> dependenciesMap = settingsCatalogService.getParameterDependencies();
        for (Map<String, Object> row : rows) {
            String type = stringValue(row.get("param_type"));
            if (!grouped.containsKey(type)) {
                continue;
            }
            Map<String, Object> extra = parseExtraJson(row.get("extra_json"));
            Map<String, String> dependencies = new LinkedHashMap<>();
            List<String> depKeys = dependenciesMap.get(type);
            if (depKeys != null) {
                Object rawDeps = extra.get("dependencies");
                Map<?, ?> depsMap = rawDeps instanceof Map<?, ?> map ? map : Map.of();
                for (String key : depKeys) {
                    Object value = depsMap.containsKey(key) ? depsMap.get(key) : extra.get(key);
                    dependencies.put(key, value != null ? value.toString().trim() : "");
                }
                extra.put("dependencies", dependencies);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.get("id"));
            entry.put("value", row.get("value"));
            entry.put("state", row.get("state") != null ? row.get("state") : "Активен");
            entry.put("is_deleted", asBoolean(row.get("is_deleted")));
            entry.put("deleted_at", row.get("deleted_at"));
            entry.put("usage_count", 0);
            entry.put("extra", extra);
            entry.put("dependencies", dependencies);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) grouped.get(type);
            list.add(entry);
        }
        return grouped;
    }

    private Map<String, Object> parseExtraJson(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private String buildExtraJson(Map<String, Object> payload, Set<String> skipKeys) {
        Map<String, Object> extra = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (!skipKeys.contains(key)) {
                extra.put(key, value);
            }
        });
        return writeJson(extra);
    }

    private String mergeExtraJson(Object existingRaw, Map<String, Object> payload, Set<String> skipKeys) {
        Map<String, Object> extra = parseExtraJson(existingRaw);
        payload.forEach((key, value) -> {
            if (!skipKeys.contains(key)) {
                extra.put(key, value);
            }
        });
        return writeJson(extra);
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw instanceof String s) {
            return s.equalsIgnoreCase("true") || s.equals("1");
        }
        return false;
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private void validateParameterUniqueness(String paramType,
                                             String value,
                                             Map<String, Object> payload,
                                             Long excludeId) {
        if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
            return;
        }
        List<String> dependencyKeys = settingsCatalogService.getParameterDependencies().getOrDefault(paramType, List.of());
        Map<String, String> incomingDependencies = extractDependencies(payload, dependencyKeys);

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                "SELECT id, extra_json FROM settings_parameters WHERE param_type = ? AND value = ? AND is_deleted = 0",
                paramType,
                value
        );
        for (Map<String, Object> candidate : candidates) {
            Long candidateId = candidate.get("id") instanceof Number n ? n.longValue() : null;
            if (excludeId != null && candidateId != null && excludeId.equals(candidateId)) {
                continue;
            }
            Map<String, String> existingDependencies = extractDependencies(
                    parseExtraJson(candidate.get("extra_json")),
                    dependencyKeys
            );
            if (dependencyKeys.isEmpty() || existingDependencies.equals(incomingDependencies)) {
                throw new IllegalArgumentException("Такая запись уже существует");
            }
        }
    }

    private Map<String, String> extractDependencies(Map<String, Object> source,
                                                    List<String> dependencyKeys) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null || dependencyKeys == null || dependencyKeys.isEmpty()) {
            return result;
        }
        Object rawDependencies = source.get("dependencies");
        Map<?, ?> dependenciesMap = rawDependencies instanceof Map<?, ?> map ? map : Map.of();
        for (String key : dependencyKeys) {
            Object raw = dependenciesMap.containsKey(key) ? dependenciesMap.get(key) : source.get(key);
            result.put(key, stringValue(raw));
        }
        return result;
    }

    private void syncLocationsFromParameters() {
        JsonNode existingLocations = sharedConfigService.loadLocations();
        Map<String, Object> payload = existingLocations != null && existingLocations.isObject()
                ? objectMapper.convertValue(existingLocations, Map.class)
                : new LinkedHashMap<>();

        Map<String, Object> tree = payload.get("tree") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        Map<String, Object> cityMeta = payload.get("city_meta") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        Map<String, Object> locationMeta = payload.get("location_meta") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT param_type, value, extra_json FROM settings_parameters "
                        + "WHERE is_deleted = 0 AND param_type IN ('business', 'city', 'department')"
        );

        for (Map<String, Object> row : rows) {
            String paramType = stringValue(row.get("param_type"));
            String value = stringValue(row.get("value"));
            if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
                continue;
            }

            Map<String, Object> extra = parseExtraJson(row.get("extra_json"));
            Map<String, String> dependencies = extractDependencies(
                    extra,
                    settingsCatalogService.getParameterDependencies().getOrDefault(paramType, List.of())
            );

            if ("business".equals(paramType)) {
                tree.computeIfAbsent(value, k -> new LinkedHashMap<>());
                continue;
            }

            String business = stringValue(dependencies.get("business"));
            String partnerType = stringValue(dependencies.get("partner_type"));
            String country = stringValue(dependencies.get("country"));
            if (!StringUtils.hasText(business) || !StringUtils.hasText(partnerType)) {
                continue;
            }

            Map<String, Object> types = tree.get(business) instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map)
                    : new LinkedHashMap<>();
            tree.put(business, types);

            Map<String, Object> cities = types.get(partnerType) instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map)
                    : new LinkedHashMap<>();
            types.put(partnerType, cities);

            if ("city".equals(paramType)) {
                if (!cities.containsKey(value) || !(cities.get(value) instanceof List<?>)) {
                    cities.put(value, new ArrayList<>());
                }
                upsertLocationMeta(cityMeta, String.join("::", business, partnerType, value), country, partnerType);
                continue;
            }

            String city = stringValue(dependencies.get("city"));
            if (!StringUtils.hasText(city)) {
                continue;
            }
            List<String> locations = cities.get(city) instanceof List<?> list
                    ? new ArrayList<>(list.stream().map(this::stringValue).filter(StringUtils::hasText).toList())
                    : new ArrayList<>();
            if (!locations.contains(value)) {
                locations.add(value);
            }
            cities.put(city, locations);
            upsertLocationMeta(locationMeta, String.join("::", business, partnerType, city, value), country, partnerType);
            if (!cityMeta.containsKey(String.join("::", business, partnerType, city))) {
                upsertLocationMeta(cityMeta, String.join("::", business, partnerType, city), country, partnerType);
            }
        }

        payload.put("tree", tree);
        payload.put("city_meta", cityMeta);
        payload.put("location_meta", locationMeta);
        sharedConfigService.saveLocations(payload);
        syncParametersFromLocations(payload);
    }

    private void syncParametersFromLocations(Object locationsPayload) {
        if (!(locationsPayload instanceof Map<?, ?> map)) {
            return;
        }
        Object treeRaw = map.get("tree");
        if (!(treeRaw instanceof Map<?, ?> tree)) {
            return;
        }
        Map<String, Map<String, String>> cityMeta = readMetaMap(map.get("city_meta"));
        Map<String, Map<String, String>> locationMeta = readMetaMap(map.get("location_meta"));

        Set<String> businesses = new LinkedHashSet<>();
        Set<String> partnerTypes = new LinkedHashSet<>();
        Set<String> countries = new LinkedHashSet<>();

        for (Map.Entry<?, ?> businessEntry : tree.entrySet()) {
            String business = stringValue(businessEntry.getKey());
            if (!StringUtils.hasText(business)) {
                continue;
            }
            businesses.add(business);
            if (!(businessEntry.getValue() instanceof Map<?, ?> types)) {
                continue;
            }
            for (Map.Entry<?, ?> typeEntry : types.entrySet()) {
                String type = stringValue(typeEntry.getKey());
                if (StringUtils.hasText(type)) {
                    partnerTypes.add(type);
                }
                if (!(typeEntry.getValue() instanceof Map<?, ?> cities)) {
                    continue;
                }
                for (Map.Entry<?, ?> cityEntry : cities.entrySet()) {
                    String city = stringValue(cityEntry.getKey());
                    if (!StringUtils.hasText(city)) {
                        continue;
                    }
                    String cityPath = String.join("::", business, type, city);
                    Map<String, String> cityAttrs = cityMeta.getOrDefault(cityPath, Map.of());
                    String country = stringValue(cityAttrs.get("country"));
                    String partnerType = stringValue(cityAttrs.get("partner_type"));
                    if (!StringUtils.hasText(partnerType)) {
                        partnerType = type;
                    }
                    if (StringUtils.hasText(partnerType)) {
                        partnerTypes.add(partnerType);
                    }
                    if (StringUtils.hasText(country)) {
                        countries.add(country);
                        upsertParameterIfMissing("city", city, Map.of(
                                "country", country,
                                "partner_type", partnerType,
                                "business", business
                        ));
                    }
                    Object locationsRaw = cityEntry.getValue();
                    if (!(locationsRaw instanceof Iterable<?> locations)) {
                        continue;
                    }
                    for (Object locationRaw : locations) {
                        String location = stringValue(locationRaw);
                        if (!StringUtils.hasText(location)) {
                            continue;
                        }
                        String locationPath = String.join("::", business, type, city, location);
                        Map<String, String> locationAttrs = locationMeta.getOrDefault(locationPath, Map.of());
                        String locationCountry = stringValue(locationAttrs.get("country"));
                        String locationPartnerType = stringValue(locationAttrs.get("partner_type"));
                        if (!StringUtils.hasText(locationCountry)) {
                            locationCountry = country;
                        }
                        if (!StringUtils.hasText(locationPartnerType)) {
                            locationPartnerType = partnerType;
                        }
                        if (StringUtils.hasText(locationCountry)) {
                            countries.add(locationCountry);
                        }
                        if (StringUtils.hasText(locationPartnerType)) {
                            partnerTypes.add(locationPartnerType);
                        }
                        if (StringUtils.hasText(locationCountry) && StringUtils.hasText(locationPartnerType)) {
                            upsertParameterIfMissing("department", location, Map.of(
                                    "country", locationCountry,
                                    "partner_type", locationPartnerType,
                                    "business", business,
                                    "city", city
                            ));
                        }
                    }
                }
            }
        }

        businesses.forEach(b -> upsertParameterIfMissing("business", b, Map.of()));
        partnerTypes.forEach(t -> upsertParameterIfMissing("partner_type", t, Map.of("country", "Россия")));
        countries.forEach(c -> upsertParameterIfMissing("country", c, Map.of()));
    }

    private Map<String, Map<String, String>> readMetaMap(Object raw) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = stringValue(entry.getKey());
            if (!StringUtils.hasText(key) || !(entry.getValue() instanceof Map<?, ?> values)) {
                continue;
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("country", stringValue(values.get("country")));
            attrs.put("partner_type", stringValue(values.get("partner_type")));
            result.put(key, attrs);
        }
        return result;
    }

    private void upsertParameterIfMissing(String paramType, String value, Map<String, String> dependencies) {
        if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
            return;
        }
        List<String> dependencyKeys = settingsCatalogService.getParameterDependencies().getOrDefault(paramType, List.of());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, extra_json FROM settings_parameters WHERE param_type = ? AND value = ? AND is_deleted = 0",
                paramType,
                value
        );
        for (Map<String, Object> row : rows) {
            Map<String, String> existingDeps = extractDependencies(parseExtraJson(row.get("extra_json")), dependencyKeys);
            Map<String, String> targetDeps = new LinkedHashMap<>();
            for (String key : dependencyKeys) {
                targetDeps.put(key, stringValue(dependencies.get(key)));
            }
            if (dependencyKeys.isEmpty() || existingDeps.equals(targetDeps)) {
                return;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dependencies", dependencies);
        dependencies.forEach(payload::put);
        String extraJson = writeJson(payload);
        jdbcTemplate.update(
                "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, 'Активен', 0, ?)",
                paramType,
                value,
                extraJson
        );
    }

    private void upsertLocationMeta(Map<String, Object> target, String path, String country, String partnerType) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        Map<String, Object> attrs = target.get(path) instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        attrs.put("country", StringUtils.hasText(country) ? country : "");
        attrs.put("partner_type", StringUtils.hasText(partnerType) ? partnerType : "");
        target.put(path, attrs);
    }
}
