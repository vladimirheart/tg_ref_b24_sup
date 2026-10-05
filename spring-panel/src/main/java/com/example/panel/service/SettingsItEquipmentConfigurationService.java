package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SettingsItEquipmentConfigurationService {

    private static final Set<String> VALUE_TYPES = Set.of("text", "integer", "decimal", "boolean", "enum");
    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("[a-z][a-z0-9_.-]{0,99}");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SettingsItEquipmentConfigurationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> listAttributes(String equipmentType) {
        String type = trimToNull(equipmentType);
        String sql = "SELECT id, equipment_type, attribute_key, label, value_type, section_name, unit, help_text, " +
                "required, options::text AS options_json, sort_order, active, created_at, updated_at " +
                "FROM it_equipment_attribute_definitions";
        List<Map<String, Object>> items;
        if (type == null) {
            items = jdbcTemplate.query(sql + " ORDER BY equipment_type, sort_order, id", (rs, rowNum) -> attributeRow(rs));
        } else {
            items = jdbcTemplate.query(sql + " WHERE equipment_type = ? ORDER BY sort_order, id", (rs, rowNum) -> attributeRow(rs), type);
        }
        return Map.of("success", true, "items", items);
    }

    @Transactional
    public Map<String, Object> createAttribute(Map<String, Object> payload) {
        try {
            AttributeInput input = attributeInput(payload);
            Long id = jdbcTemplate.queryForObject(
                    "INSERT INTO it_equipment_attribute_definitions(" +
                            "equipment_type, attribute_key, label, value_type, section_name, unit, help_text, required, options, sort_order, active, created_at, updated_at" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                    Long.class,
                    input.equipmentType(), input.attributeKey(), input.label(), input.valueType(), input.sectionName(),
                    input.unit(), input.helpText(), input.required(), toJson(input.options()), input.sortOrder(), input.active());
            return successItem(loadAttribute(id));
        } catch (RuntimeException ex) {
            return failure(readableMessage(ex));
        }
    }

    @Transactional
    public Map<String, Object> updateAttribute(long attributeId, Map<String, Object> payload) {
        try {
            Map<String, Object> existing = loadAttribute(attributeId);
            if (existing == null) {
                return failure("Характеристика не найдена");
            }
            AttributeInput input = attributeInput(payload);
            String oldType = stringValue(existing.get("equipment_type"));
            String oldKey = stringValue(existing.get("attribute_key"));
            if (!oldType.equals(input.equipmentType()) || !oldKey.equals(input.attributeKey())) {
                int references = profileReferenceCount(oldType, oldKey);
                if (references > 0) {
                    return failure("Нельзя изменить тип оборудования или код характеристики: характеристика уже используется профилями");
                }
            }
            int updated = jdbcTemplate.update(
                    "UPDATE it_equipment_attribute_definitions SET equipment_type = ?, attribute_key = ?, label = ?, value_type = ?, " +
                            "section_name = ?, unit = ?, help_text = ?, required = ?, options = CAST(? AS jsonb), sort_order = ?, active = ?, " +
                            "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    input.equipmentType(), input.attributeKey(), input.label(), input.valueType(), input.sectionName(), input.unit(),
                    input.helpText(), input.required(), toJson(input.options()), input.sortOrder(), input.active(), attributeId);
            if (updated != 1) {
                return failure("Характеристика не найдена");
            }
            validateExistingProfileValues(input.equipmentType());
            return successItem(loadAttribute(attributeId));
        } catch (RuntimeException ex) {
            throwIfValidationAfterWrite(ex);
            return failure(readableMessage(ex));
        }
    }

    @Transactional
    public Map<String, Object> deleteAttribute(long attributeId) {
        try {
            Map<String, Object> existing = loadAttribute(attributeId);
            if (existing == null) {
                return failure("Характеристика не найдена");
            }
            String equipmentType = stringValue(existing.get("equipment_type"));
            String attributeKey = stringValue(existing.get("attribute_key"));
            jdbcTemplate.update(
                    "UPDATE it_equipment_configuration_profiles SET values = values - ?, updated_at = CURRENT_TIMESTAMP " +
                            "WHERE equipment_type = ? AND jsonb_exists(values, ?)",
                    attributeKey, equipmentType, attributeKey);
            int removed = jdbcTemplate.update("DELETE FROM it_equipment_attribute_definitions WHERE id = ?", attributeId);
            return Map.of("success", removed == 1);
        } catch (RuntimeException ex) {
            return failure(readableMessage(ex));
        }
    }

    public Map<String, Object> listProfiles(String equipmentType, Long catalogId) {
        String type = trimToNull(equipmentType);
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.equipment_type, p.catalog_id, p.profile_name, p.description, p.values::text AS values_json, " +
                        "p.active, p.created_at, p.updated_at, c.equipment_vendor, c.equipment_model " +
                        "FROM it_equipment_configuration_profiles p LEFT JOIN it_equipment_catalog c ON c.id = p.catalog_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (type != null) {
            sql.append(" AND p.equipment_type = ?");
            args.add(type);
        }
        if (catalogId != null && catalogId > 0) {
            sql.append(" AND p.catalog_id = ?");
            args.add(catalogId);
        }
        sql.append(" ORDER BY p.equipment_type, p.profile_name, p.id");
        List<Map<String, Object>> items = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> profileRow(rs), args.toArray());
        return Map.of("success", true, "items", items);
    }

    @Transactional
    public Map<String, Object> createProfile(Map<String, Object> payload) {
        try {
            ProfileInput input = profileInput(payload);
            validateProfileValues(input.equipmentType(), input.values());
            validateCatalogLink(input.equipmentType(), input.catalogId());
            Long id = jdbcTemplate.queryForObject(
                    "INSERT INTO it_equipment_configuration_profiles(" +
                            "equipment_type, catalog_id, profile_name, description, values, active, created_at, updated_at" +
                            ") VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                    Long.class,
                    input.equipmentType(), input.catalogId(), input.profileName(), input.description(), toJson(input.values()), input.active());
            return successItem(loadProfile(id));
        } catch (RuntimeException ex) {
            return failure(readableMessage(ex));
        }
    }

    @Transactional
    public Map<String, Object> updateProfile(long profileId, Map<String, Object> payload) {
        try {
            if (loadProfile(profileId) == null) {
                return failure("Профиль комплектации не найден");
            }
            ProfileInput input = profileInput(payload);
            validateProfileValues(input.equipmentType(), input.values());
            validateCatalogLink(input.equipmentType(), input.catalogId());
            int updated = jdbcTemplate.update(
                    "UPDATE it_equipment_configuration_profiles SET equipment_type = ?, catalog_id = ?, profile_name = ?, description = ?, " +
                            "values = CAST(? AS jsonb), active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    input.equipmentType(), input.catalogId(), input.profileName(), input.description(), toJson(input.values()), input.active(), profileId);
            if (updated != 1) {
                return failure("Профиль комплектации не найден");
            }
            return successItem(loadProfile(profileId));
        } catch (RuntimeException ex) {
            return failure(readableMessage(ex));
        }
    }

    @Transactional
    public Map<String, Object> deleteProfile(long profileId) {
        try {
            int removed = jdbcTemplate.update("DELETE FROM it_equipment_configuration_profiles WHERE id = ?", profileId);
            if (removed != 1) {
                return failure("Профиль комплектации не найден");
            }
            return Map.of("success", true);
        } catch (RuntimeException ex) {
            return failure(readableMessage(ex));
        }
    }

    private Map<String, Object> loadAttribute(Long id) {
        if (id == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, equipment_type, attribute_key, label, value_type, section_name, unit, help_text, required, " +
                        "options::text AS options_json, sort_order, active, created_at, updated_at " +
                        "FROM it_equipment_attribute_definitions WHERE id = ?",
                (rs, rowNum) -> attributeRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> loadProfile(Long id) {
        if (id == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT p.id, p.equipment_type, p.catalog_id, p.profile_name, p.description, p.values::text AS values_json, " +
                        "p.active, p.created_at, p.updated_at, c.equipment_vendor, c.equipment_model " +
                        "FROM it_equipment_configuration_profiles p LEFT JOIN it_equipment_catalog c ON c.id = p.catalog_id WHERE p.id = ?",
                (rs, rowNum) -> profileRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> attributeRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("equipment_type", rs.getString("equipment_type"));
        row.put("attribute_key", rs.getString("attribute_key"));
        row.put("label", rs.getString("label"));
        row.put("value_type", rs.getString("value_type"));
        row.put("section_name", rs.getString("section_name"));
        row.put("unit", rs.getString("unit"));
        row.put("help_text", rs.getString("help_text"));
        row.put("required", rs.getBoolean("required"));
        row.put("options", parseStringList(rs.getString("options_json")));
        row.put("sort_order", rs.getInt("sort_order"));
        row.put("active", rs.getBoolean("active"));
        row.put("created_at", rs.getObject("created_at"));
        row.put("updated_at", rs.getObject("updated_at"));
        return row;
    }

    private Map<String, Object> profileRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("equipment_type", rs.getString("equipment_type"));
        long rawCatalogId = rs.getLong("catalog_id");
        row.put("catalog_id", rs.wasNull() ? null : rawCatalogId);
        row.put("profile_name", rs.getString("profile_name"));
        row.put("description", rs.getString("description"));
        row.put("values", parseObjectMap(rs.getString("values_json")));
        row.put("active", rs.getBoolean("active"));
        row.put("equipment_vendor", rs.getString("equipment_vendor"));
        row.put("equipment_model", rs.getString("equipment_model"));
        row.put("created_at", rs.getObject("created_at"));
        row.put("updated_at", rs.getObject("updated_at"));
        return row;
    }

    private AttributeInput attributeInput(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        String equipmentType = requiredText(safe.get("equipment_type"), "Тип оборудования обязателен");
        String attributeKey = requiredText(safe.get("attribute_key"), "Код характеристики обязателен").toLowerCase(Locale.ROOT);
        if (!ATTRIBUTE_KEY.matcher(attributeKey).matches()) {
            throw new IllegalArgumentException("Код характеристики должен начинаться с латинской буквы и содержать только a-z, 0-9, _, . или -");
        }
        String label = requiredText(safe.get("label"), "Название характеристики обязательно");
        String valueType = requiredText(safe.get("value_type"), "Тип значения обязателен").toLowerCase(Locale.ROOT);
        if (!VALUE_TYPES.contains(valueType)) {
            throw new IllegalArgumentException("Неподдерживаемый тип значения: " + valueType);
        }
        List<String> options = sanitizeOptions(safe.get("options"));
        if (!"enum".equals(valueType) && !options.isEmpty()) {
            throw new IllegalArgumentException("Варианты значений допустимы только для enum");
        }
        if ("enum".equals(valueType) && options.isEmpty()) {
            throw new IllegalArgumentException("Для enum нужен хотя бы один вариант значения");
        }
        return new AttributeInput(
                equipmentType, attributeKey, label, valueType,
                trimToNull(safe.get("section_name")), trimToNull(safe.get("unit")), trimToNull(safe.get("help_text")),
                booleanValue(safe.get("required"), false), options, intValue(safe.get("sort_order"), 0), booleanValue(safe.get("active"), true));
    }

    private ProfileInput profileInput(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        String equipmentType = requiredText(safe.get("equipment_type"), "Тип оборудования обязателен");
        String profileName = requiredText(safe.get("profile_name"), "Название профиля обязательно");
        Long catalogId = positiveLong(safe.get("catalog_id"));
        Map<String, Object> values = sanitizeValues(safe.get("values"));
        return new ProfileInput(
                equipmentType, catalogId, profileName, trimToNull(safe.get("description")), values,
                booleanValue(safe.get("active"), true));
    }

    private void validateCatalogLink(String equipmentType, Long catalogId) {
        if (catalogId == null) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM it_equipment_catalog WHERE id = ? AND equipment_type = ?",
                Integer.class, catalogId, equipmentType);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Выбранная модель не принадлежит указанному типу оборудования");
        }
    }

    private void validateProfileValues(String equipmentType, Map<String, Object> values) {
        Map<String, DefinitionMeta> definitions = loadDefinitionMeta(equipmentType);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            DefinitionMeta definition = definitions.get(entry.getKey());
            if (definition == null || !definition.active()) {
                throw new IllegalArgumentException("Неизвестная или отключенная характеристика: " + entry.getKey());
            }
            validateTypedValue(definition, entry.getValue());
        }
    }

    private void validateExistingProfileValues(String equipmentType) {
        List<Map<String, Object>> profiles = jdbcTemplate.query(
                "SELECT values::text AS values_json FROM it_equipment_configuration_profiles WHERE equipment_type = ?",
                (rs, rowNum) -> parseObjectMap(rs.getString("values_json")), equipmentType);
        for (Map<String, Object> profileValues : profiles) {
            validateProfileValues(equipmentType, profileValues);
        }
    }

    private Map<String, DefinitionMeta> loadDefinitionMeta(String equipmentType) {
        Map<String, DefinitionMeta> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT attribute_key, value_type, options::text AS options_json, active FROM it_equipment_attribute_definitions WHERE equipment_type = ?",
                rs -> {
                    result.put(rs.getString("attribute_key"), new DefinitionMeta(
                            rs.getString("value_type"), parseStringList(rs.getString("options_json")), rs.getBoolean("active")));
                }, equipmentType);
        return result;
    }

    private void validateTypedValue(DefinitionMeta definition, Object value) {
        if (value == null) {
            return;
        }
        switch (definition.valueType()) {
            case "text" -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("Текстовая характеристика должна содержать строку");
                }
            }
            case "integer" -> {
                if (!isInteger(value)) {
                    throw new IllegalArgumentException("Целочисленная характеристика должна содержать целое число");
                }
            }
            case "decimal" -> {
                if (!isDecimal(value)) {
                    throw new IllegalArgumentException("Числовая характеристика должна содержать число");
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException("Логическая характеристика должна содержать true/false");
                }
            }
            case "enum" -> {
                if (!(value instanceof String text) || !definition.options().contains(text)) {
                    throw new IllegalArgumentException("Значение enum отсутствует среди разрешенных вариантов");
                }
            }
            default -> throw new IllegalArgumentException("Неподдерживаемый тип характеристики: " + definition.valueType());
        }
    }

    private int profileReferenceCount(String equipmentType, String attributeKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM it_equipment_configuration_profiles WHERE equipment_type = ? AND jsonb_exists(values, ?)",
                Integer.class, equipmentType, attributeKey);
        return count == null ? 0 : count;
    }

    private boolean isInteger(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return true;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().scale() <= 0;
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            return Double.isFinite(raw) && Math.rint(raw) == raw;
        }
        return false;
    }

    private boolean isDecimal(Object value) {
        if (value instanceof Number number) {
            return Double.isFinite(number.doubleValue());
        }
        return false;
    }

    private Map<String, Object> sanitizeValues(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("values должен быть JSON-объектом");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = trimToNull(entry.getKey());
            if (key == null) {
                continue;
            }
            if (!ATTRIBUTE_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Некорректный код характеристики в values: " + key);
            }
            if (entry.getValue() != null) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private List<String> sanitizeOptions(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("options должен быть JSON-массивом");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            String value = trimToNull(item);
            if (value != null) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private List<String> parseStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, STRING_LIST);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось разобрать options JSON", ex);
        }
    }

    private Map<String, Object> parseObjectMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, OBJECT_MAP);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось разобрать values JSON", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Некорректные JSON-данные", ex);
        }
    }

    private Map<String, Object> successItem(Map<String, Object> item) {
        return Map.of("success", true, "item", item == null ? Map.of() : item);
    }

    private Map<String, Object> failure(String error) {
        return Map.of("success", false, "error", error);
    }

    private String readableMessage(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof IllegalArgumentException && StringUtils.hasText(current.getMessage())) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        String message = ex.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("unique")) {
            return "Запись с такой идентичностью уже существует";
        }
        return "Не удалось сохранить конфигурацию оборудования";
    }

    private void throwIfValidationAfterWrite(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException) {
            throw ex;
        }
    }

    private String requiredText(Object value, String error) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(error);
        }
        return text;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("sort_order должен быть целым числом");
        }
    }

    private Long positiveLong(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        long parsed;
        try {
            parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("catalog_id должен быть положительным числом");
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException("catalog_id должен быть положительным числом");
        }
        return parsed;
    }

    private record AttributeInput(
            String equipmentType,
            String attributeKey,
            String label,
            String valueType,
            String sectionName,
            String unit,
            String helpText,
            boolean required,
            List<String> options,
            int sortOrder,
            boolean active) {
    }

    private record ProfileInput(
            String equipmentType,
            Long catalogId,
            String profileName,
            String description,
            Map<String, Object> values,
            boolean active) {
    }

    private record DefinitionMeta(String valueType, List<String> options, boolean active) {
    }
}
