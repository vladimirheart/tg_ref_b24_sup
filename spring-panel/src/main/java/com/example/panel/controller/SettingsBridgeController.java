package com.example.panel.controller;

import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
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
            || payload.containsKey("dialog_time_metrics")) {
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
            if (payload.containsKey("dialog_time_metrics")) {
                dialogConfig.put("time_metrics", payload.get("dialog_time_metrics"));
            }
            settings.put("dialog_config", dialogConfig);
            modified = true;
        }

        if (payload.containsKey("reporting_config")) {
            settings.put("reporting_config", payload.get("reporting_config"));
            modified = true;
        }

        if (modified) {
            sharedConfigService.saveSettings(settings);
        }

        if (payload.containsKey("locations")) {
            sharedConfigService.saveLocations(payload.get("locations"));
        }

        return Map.of("success", true);
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
        String extraJson = buildExtraJson(payload, Set.of("param_type", "value", "state"));
        jdbcTemplate.update(
            "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, ?, 0, ?)",
            paramType, value, state, extraJson
        );
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
            "SELECT extra_json FROM settings_parameters WHERE id = ?",
            paramId
        );
        if (existingRows.isEmpty()) {
            return Map.of("success", false, "error", "Параметр не найден");
        }
        Map<String, Object> existing = existingRows.get(0);

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
}
