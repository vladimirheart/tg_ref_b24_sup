package com.example.panel.controller;

import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
public class SettingsBridgeController {

    private static final Logger log = LoggerFactory.getLogger(SettingsBridgeController.class);

    private final JdbcTemplate jdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final ObjectMapper objectMapper;

    public SettingsBridgeController(JdbcTemplate jdbcTemplate,
                                    SharedConfigService sharedConfigService,
                                    SettingsCatalogService settingsCatalogService,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = {"/settings", "/settings/"}, method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
            boolean modified = false;

            if (payload.containsKey("auto_close_hours")) {
                Object raw = payload.get("auto_close_hours");
                settings.put("auto_close_hours", raw);
                modified = true;
            }

            if (payload.containsKey("auto_close_config")) {
                settings.put("auto_close_config", payload.get("auto_close_config"));
                modified = true;
            }

            if (payload.containsKey("categories")) {
                Object raw = payload.get("categories");
                List<String> categories = new ArrayList<>();
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        String value = item != null ? item.toString().trim() : "";
                        if (!value.isEmpty()) {
                            categories.add(value);
                        }
                    }
                }
                settings.put("categories", categories);
                modified = true;
            }

            if (payload.containsKey("client_statuses")) {
                List<String> statuses = new ArrayList<>();
                Object raw = payload.get("client_statuses");
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        String value = item != null ? item.toString().trim() : "";
                        if (!value.isEmpty() && !statuses.contains(value)) {
                            statuses.add(value);
                        }
                    }
                }
                settings.put("client_statuses", statuses);
                modified = true;
            }

            if (payload.containsKey("client_status_colors")) {
                Map<String, String> colors = new LinkedHashMap<>();
                Object raw = payload.get("client_status_colors");
                if (raw instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> {
                        String name = key != null ? key.toString().trim() : "";
                        String color = value != null ? value.toString().trim() : "";
                        if (StringUtils.hasText(name) && StringUtils.hasText(color)) {
                            colors.put(name, color);
                        }
                    });
                }
                settings.put("client_status_colors", colors);
                modified = true;
            }

            if (payload.containsKey("business_cell_styles")) {
                settings.put("business_cell_styles", payload.get("business_cell_styles"));
                modified = true;
            }

            if (payload.containsKey("network_profiles")) {
                settings.put("network_profiles", payload.get("network_profiles"));
                modified = true;
            }

            if (payload.containsKey("bot_settings")) {
                settings.put("bot_settings", payload.get("bot_settings"));
                modified = true;
            }

            if (payload.containsKey("dialog_category_templates")
                || payload.containsKey("dialog_question_templates")
                || payload.containsKey("dialog_completion_templates")
                || payload.containsKey("dialog_macro_templates")
                || payload.containsKey("dialog_time_metrics")
                || payload.containsKey("dialog_summary_badges")
                || payload.containsKey("dialog_sla_target_minutes")
                || payload.containsKey("dialog_sla_warning_minutes")) {
                Map<String, Object> dialogConfig = new LinkedHashMap<>();
                Object existing = settings.get("dialog_config");
                if (existing instanceof Map<?, ?> existingMap) {
                    existingMap.forEach((key, value) -> dialogConfig.put(String.valueOf(key), value));
                }
                if (payload.containsKey("dialog_category_templates")) {
                    dialogConfig.put("category_templates", payload.get("dialog_category_templates"));
                }
                if (payload.containsKey("dialog_question_templates")) {
                    dialogConfig.put("question_templates", payload.get("dialog_question_templates"));
                }
                if (payload.containsKey("dialog_completion_templates")) {
                    dialogConfig.put("completion_templates", payload.get("dialog_completion_templates"));
                }
                if (payload.containsKey("dialog_macro_templates")) {
                    dialogConfig.put("macro_templates", payload.get("dialog_macro_templates"));
                }
                if (payload.containsKey("dialog_time_metrics")) {
                    dialogConfig.put("time_metrics", payload.get("dialog_time_metrics"));
                }
                if (payload.containsKey("dialog_sla_target_minutes")) {
                    dialogConfig.put("sla_target_minutes", payload.get("dialog_sla_target_minutes"));
                }
                if (payload.containsKey("dialog_sla_warning_minutes")) {
                    dialogConfig.put("sla_warning_minutes", payload.get("dialog_sla_warning_minutes"));
                }
                if (payload.containsKey("dialog_summary_badges")) {
                    Map<String, Object> summaryBadges = new LinkedHashMap<>();
                    Object existingBadges = dialogConfig.get("summary_badges");
                    if (existingBadges instanceof Map<?, ?> badgesMap) {
                        badgesMap.forEach((key, value) -> summaryBadges.put(String.valueOf(key), value));
                    }
                    Object rawBadges = payload.get("dialog_summary_badges");
                    if (rawBadges instanceof Map<?, ?> incomingMap) {
                        incomingMap.forEach((key, value) -> {
                            if (key != null) {
                                summaryBadges.put(String.valueOf(key), value);
                            }
                        });
                    }
                    dialogConfig.put("summary_badges", summaryBadges);
                }
                settings.put("dialog_config", dialogConfig);
                modified = true;
            }

            if (payload.containsKey("reporting_config")) {
                settings.put("reporting_config", payload.get("reporting_config"));
                modified = true;
            }

            if (payload.containsKey("manager_location_bindings")) {
                settings.put("manager_location_bindings", payload.get("manager_location_bindings"));
                modified = true;
            }

            if (modified) {
                sharedConfigService.saveSettings(settings);
            }

            if (payload.containsKey("locations")) {
                Object locationsPayload = payload.get("locations");
                sharedConfigService.saveLocations(locationsPayload);
                syncParametersFromLocations(locationsPayload);
            }

            return Map.of("success", true);
        } catch (Exception ex) {
            log.error("Failed to update settings payload", ex);
            String message = ex.getMessage();
            if (!StringUtils.hasText(message)) {
                message = "Не удалось сохранить настройки. Проверьте журнал приложения.";
            }
            return Map.of("success", false, "error", message);
        }
    }

    @GetMapping("/api/settings/parameters")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listParameters() {
        return fetchParametersGrouped(true);
    }

    @PostMapping({"/api/settings/parameters", "/api/settings/parameters/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> createParameter(HttpServletRequest request) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
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
        Map<String, Object> data = fetchParametersGrouped(true);
        return Map.of("success", true, "data", data);
    }

    @RequestMapping(
        value = {"/api/settings/parameters/{paramId}", "/api/settings/parameters/{paramId}/"},
        method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH}
    )
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateParameter(@PathVariable long paramId,
                                               @RequestBody Map<String, Object> payload) {
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
        Map<String, Object> data = fetchParametersGrouped(true);
        return Map.of("success", true, "data", data);
    }

    @DeleteMapping("/api/settings/parameters/{paramId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deleteParameter(@PathVariable long paramId) {
        int updated = jdbcTemplate.update(
            "UPDATE settings_parameters SET is_deleted = 1, deleted_at = datetime('now') WHERE id = ?",
            paramId
        );
        if (updated == 0) {
            return Map.of("success", false, "error", "Параметр не найден");
        }
        syncLocationsFromParameters();
        Map<String, Object> data = fetchParametersGrouped(true);
        return Map.of("success", true, "data", data);
    }

    @GetMapping("/api/settings/it-equipment")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listItEquipment() {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    @PostMapping({"/api/settings/it-equipment", "/api/settings/it-equipment/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> createItEquipment(HttpServletRequest request) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        String type = stringValue(payload.get("equipment_type"));
        String vendor = stringValue(payload.get("equipment_vendor"));
        String model = stringValue(payload.get("equipment_model"));
        if (!StringUtils.hasText(type)) {
            return Map.of("success", false, "error", "Поле «Тип оборудования» обязательно");
        }
        if (!StringUtils.hasText(vendor)) {
            return Map.of("success", false, "error", "Поле «Производитель оборудования» обязательно");
        }
        if (!StringUtils.hasText(model)) {
            return Map.of("success", false, "error", "Поле «Модель оборудования» обязательно");
        }
        String photoUrl = stringValue(payload.getOrDefault("photo_url", payload.get("photo")));
        String serialNumber = stringValue(payload.get("serial_number"));
        String accessories = stringValue(payload.getOrDefault("accessories", payload.get("additional_equipment")));

        jdbcTemplate.update(
            "INSERT INTO it_equipment_catalog(equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))",
            type, vendor, model, photoUrl, serialNumber, accessories
        );
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    @RequestMapping(value = "/api/settings/it-equipment/{itemId}",
        method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateItEquipment(@PathVariable long itemId,
                                                 @RequestBody Map<String, Object> payload) {
        StringBuilder updates = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (payload.containsKey("equipment_type")) {
            String value = stringValue(payload.get("equipment_type"));
            if (!StringUtils.hasText(value)) {
                return Map.of("success", false, "error", "Поле «Тип оборудования» обязательно");
            }
            updates.append("equipment_type = ?,");
            params.add(value);
        }
        if (payload.containsKey("equipment_vendor")) {
            String value = stringValue(payload.get("equipment_vendor"));
            if (!StringUtils.hasText(value)) {
                return Map.of("success", false, "error", "Поле «Производитель оборудования» обязательно");
            }
            updates.append("equipment_vendor = ?,");
            params.add(value);
        }
        if (payload.containsKey("equipment_model")) {
            String value = stringValue(payload.get("equipment_model"));
            if (!StringUtils.hasText(value)) {
                return Map.of("success", false, "error", "Поле «Модель оборудования» обязательно");
            }
            updates.append("equipment_model = ?,");
            params.add(value);
        }
        if (payload.containsKey("photo_url") || payload.containsKey("photo")) {
            updates.append("photo_url = ?,");
            params.add(stringValue(payload.getOrDefault("photo_url", payload.get("photo"))));
        }
        if (payload.containsKey("serial_number")) {
            updates.append("serial_number = ?,");
            params.add(stringValue(payload.get("serial_number")));
        }
        if (payload.containsKey("accessories") || payload.containsKey("additional_equipment")) {
            updates.append("accessories = ?,");
            params.add(stringValue(payload.getOrDefault("accessories", payload.get("additional_equipment"))));
        }

        if (updates.length() == 0) {
            return Map.of("success", false, "error", "Нет данных для обновления");
        }
        updates.append("updated_at = datetime('now')");
        params.add(itemId);
        jdbcTemplate.update("UPDATE it_equipment_catalog SET " + updates + " WHERE id = ?", params.toArray());

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    @DeleteMapping("/api/settings/it-equipment/{itemId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deleteItEquipment(@PathVariable long itemId) {
        int removed = jdbcTemplate.update("DELETE FROM it_equipment_catalog WHERE id = ?", itemId);
        if (removed == 0) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    private Map<String, Object> fetchParametersGrouped(boolean includeDeleted) {
        Map<String, Object> grouped = new LinkedHashMap<>();
        Map<String, String> types = settingsCatalogService.getParameterTypes();
        types.keySet().forEach(key -> grouped.put(key, new ArrayList<>()));

        String sql = "SELECT id, param_type, value, state, is_deleted, deleted_at, extra_json " +
            "FROM settings_parameters";
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

    private String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
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

    private void validateParameterUniqueness(String paramType,
                                             String value,
                                             Map<String, Object> payload,
                                             Long excludeId) {
        if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
            return;
        }
        List<String> dependencyKeys = settingsCatalogService.getParameterDependencies().getOrDefault(paramType, List.of());
        Map<String, String> incomingDependencies = extractDependencies(paramType, payload, dependencyKeys);

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
                paramType,
                parseExtraJson(candidate.get("extra_json")),
                dependencyKeys
            );
            if (dependencyKeys.isEmpty() || existingDependencies.equals(incomingDependencies)) {
                throw new IllegalArgumentException("Такая запись уже существует");
            }
        }
    }

    private Map<String, String> extractDependencies(String paramType,
                                                    Map<String, Object> source,
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
            Map<String, String> existingDeps = extractDependencies(paramType, parseExtraJson(row.get("extra_json")), dependencyKeys);
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
                paramType,
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
                locations.sort(String::compareToIgnoreCase);
            }
            cities.put(city, locations);
            upsertLocationMeta(cityMeta, String.join("::", business, partnerType, city), country, partnerType);
            upsertLocationMeta(locationMeta, String.join("::", business, partnerType, city, value), country, partnerType);
        }

        payload.put("tree", tree);
        payload.put("city_meta", cityMeta);
        payload.put("location_meta", locationMeta);
        sharedConfigService.saveLocations(payload);
    }

    private void upsertLocationMeta(Map<String, Object> metaMap, String key, String country, String partnerType) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        Map<String, String> value = metaMap.get(key) instanceof Map<?, ?> map
            ? new LinkedHashMap<>((Map<String, String>) map)
            : new LinkedHashMap<>();
        if (StringUtils.hasText(country)) {
            value.put("country", country);
        }
        if (StringUtils.hasText(partnerType)) {
            value.put("partner_type", partnerType);
        }
        metaMap.put(key, value);
    }
}
