package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SettingsItEquipmentService {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationRoutingService notificationRoutingService;
    private final ObjectPassportService objectPassportService;
    private final SettingsItEquipmentPhotoService photoService;

    public SettingsItEquipmentService(JdbcTemplate jdbcTemplate,
                                      NotificationRoutingService notificationRoutingService,
                                      ObjectPassportService objectPassportService,
                                      SettingsItEquipmentPhotoService photoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationRoutingService = notificationRoutingService;
        this.objectPassportService = objectPassportService;
        this.photoService = photoService;
    }

    public Map<String, Object> listItEquipment() {
        return Map.of("success", true, "items", loadItemsWithDiscovered());
    }

    public Map<String, Object> createItEquipment(Map<String, Object> payload, String actor) {
        String type = stringValue(payload.get("equipment_type"));
        String vendor = stringValue(payload.get("equipment_vendor"));
        String model = stringValue(payload.get("equipment_model"));
        if (!StringUtils.hasText(type)) {
            return Map.of("success", false, "error", "Поле «Тип оборудования» обязательно");
        }
        if (!StringUtils.hasText(model)) {
            return Map.of("success", false, "error", "Поле «Модель оборудования» обязательно");
        }
        String photoUrl = photoService.mergeLinksPreservingPhotos(
                "",
                stringValue(payload.getOrDefault("photo_url", payload.get("photo")))
        );
        String serialNumber = stringValue(payload.get("serial_number"));
        String accessories = stringValue(payload.getOrDefault("accessories", payload.get("additional_equipment")));

        String key = catalogKey(type, vendor, model);
        for (Map<String, Object> existing : loadItems()) {
            if (key.equals(catalogKey(existing.get("equipment_type"), existing.get("equipment_vendor"), existing.get("equipment_model")))) {
                return Map.of("success", false, "error", "Такая модель уже есть в каталоге");
            }
        }

        jdbcTemplate.update(
                "INSERT INTO it_equipment_catalog(equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                type, vendor, model, photoUrl, serialNumber, accessories
        );
        notificationRoutingService.notify(
                "passports",
                "equipment_catalog_changed",
                java.util.Set.of(),
                "Создан паспорт оборудования: " + (StringUtils.hasText(vendor) ? vendor + " " + model : model),
                "/object-passports",
                actor
        );
        return Map.of("success", true, "items", loadItemsWithDiscovered());
    }

    @Transactional
    public Map<String, Object> updateItEquipment(long itemId, Map<String, Object> payload, String actor) {
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
            updates.append("equipment_vendor = ?,");
            params.add(stringValue(payload.get("equipment_vendor")));
        }
        if (payload.containsKey("equipment_model")) {
            String value = stringValue(payload.get("equipment_model"));
            if (!StringUtils.hasText(value)) {
                return Map.of("success", false, "error", "Поле «Модель оборудования» обязательно");
            }
            updates.append("equipment_model = ?,");
            params.add(value);
        }
        boolean updateLinks = payload.containsKey("photo_url") || payload.containsKey("photo");
        String pendingLinks = updateLinks
                ? stringValue(payload.getOrDefault("photo_url", payload.get("photo")))
                : "";
        if (payload.containsKey("serial_number")) {
            updates.append("serial_number = ?,");
            params.add(stringValue(payload.get("serial_number")));
        }
        if (payload.containsKey("accessories") || payload.containsKey("additional_equipment")) {
            updates.append("accessories = ?,");
            params.add(stringValue(payload.getOrDefault("accessories", payload.get("additional_equipment"))));
        }

        if (updates.length() == 0 && !updateLinks) {
            return Map.of("success", false, "error", "Нет данных для обновления");
        }
        if (updates.length() > 0) {
            updates.append("updated_at = CURRENT_TIMESTAMP");
            params.add(itemId);
            int updated = jdbcTemplate.update("UPDATE it_equipment_catalog SET " + updates + " WHERE id = ?", params.toArray());
            if (updated == 0) {
                return Map.of("success", false, "error", "Оборудование не найдено");
            }
        }
        if (updateLinks) {
            Map<String, Object> mediaResult = photoService.updateLinksPreservingPhotos(itemId, pendingLinks);
            if (Boolean.FALSE.equals(mediaResult.get("success"))) {
                return mediaResult;
            }
        }
        notificationRoutingService.notify(
                "passports",
                "equipment_catalog_changed",
                java.util.Set.of(),
                "Обновлен паспорт оборудования #" + itemId,
                "/object-passports/" + itemId,
                actor
        );
        return Map.of("success", true, "items", loadItemsWithDiscovered());
    }

    public Map<String, Object> deleteItEquipment(long itemId, String actor) {
        Map<String, Object> existing = loadItem(itemId);
        if (existing == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        int objectCount = activeObjectCount(existing);
        if (objectCount > 0) {
            return Map.of(
                    "success", false,
                    "error", "Нельзя удалить модель: используется у объектов — " + objectCount,
                    "object_count", objectCount
            );
        }

        int removed = jdbcTemplate.update("DELETE FROM it_equipment_catalog WHERE id = ?", itemId);
        if (removed == 0) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        photoService.deleteStoredPhotos(stringValue(existing.get("photo_url")));
        notificationRoutingService.notify(
                "passports",
                "equipment_catalog_changed",
                java.util.Set.of(),
                "Удален паспорт оборудования #" + itemId,
                "/object-passports",
                actor
        );
        return Map.of("success", true, "items", loadItemsWithDiscovered());
    }

    private int activeObjectCount(Map<String, Object> row) {
        Long persistedId = positiveLongValue(row.get("id"));
        String key = catalogKey(row.get("equipment_type"), row.get("equipment_vendor"), row.get("equipment_model"));
        for (Map<String, Object> candidate : objectPassportService.listEquipmentCatalogCandidates()) {
            if (persistedId != null && catalogIds(candidate.get("catalog_ids")).contains(persistedId)) {
                return intValue(candidate.get("object_count"));
            }
            if (StringUtils.hasText(key)
                    && key.equals(catalogKey(candidate.get("equipment_type"), candidate.get("equipment_vendor"), candidate.get("equipment_model")))) {
                return intValue(candidate.get("object_count"));
            }
        }
        return 0;
    }

    private List<Map<String, Object>> loadItemsWithDiscovered() {
        List<Map<String, Object>> persisted = loadItems();
        List<Map<String, Object>> discovered = objectPassportService.listEquipmentCatalogCandidates();
        Map<String, Map<String, Object>> discoveredByKey = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> discoveredByCatalogId = new LinkedHashMap<>();
        for (Map<String, Object> candidate : discovered) {
            String key = catalogKey(candidate.get("equipment_type"), candidate.get("equipment_vendor"), candidate.get("equipment_model"));
            if (StringUtils.hasText(key)) {
                discoveredByKey.putIfAbsent(key, candidate);
            }
            for (Long catalogId : catalogIds(candidate.get("catalog_ids"))) {
                discoveredByCatalogId.putIfAbsent(catalogId, candidate);
            }
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        Set<String> persistedKeys = new LinkedHashSet<>();
        Set<Long> persistedIds = new LinkedHashSet<>();
        for (Map<String, Object> row : persisted) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>(row);
            Long persistedId = positiveLongValue(row.get("id"));
            String key = catalogKey(row.get("equipment_type"), row.get("equipment_vendor"), row.get("equipment_model"));
            Map<String, Object> usage = persistedId == null ? null : discoveredByCatalogId.get(persistedId);
            if (usage == null) {
                usage = discoveredByKey.get(key);
            }
            item.put("usage_count", usage == null ? 0 : intValue(usage.get("usage_count")));
            item.put("object_count", usage == null ? 0 : intValue(usage.get("object_count")));
            item.put("discovered", false);
            item.put("source", "catalog");
            merged.add(item);
            persistedKeys.add(key);
            if (persistedId != null) {
                persistedIds.add(persistedId);
            }
        }
        for (Map<String, Object> candidate : discovered) {
            String key = catalogKey(candidate.get("equipment_type"), candidate.get("equipment_vendor"), candidate.get("equipment_model"));
            boolean linkedToPersisted = catalogIds(candidate.get("catalog_ids")).stream().anyMatch(persistedIds::contains);
            if (linkedToPersisted || !StringUtils.hasText(key) || persistedKeys.contains(key)) {
                continue;
            }
            LinkedHashMap<String, Object> virtual = new LinkedHashMap<>(candidate);
            virtual.put("id", null);
            virtual.put("discovered", true);
            virtual.put("source", "passports");
            merged.add(virtual);
        }
        return merged;
    }

    private Set<Long> catalogIds(Object raw) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> values) {
            for (Object value : values) {
                Long parsed = positiveLongValue(value);
                if (parsed != null) {
                    ids.add(parsed);
                }
            }
        } else {
            Long parsed = positiveLongValue(raw);
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        return ids;
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

    private int intValue(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(raw));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Map<String, Object> loadItem(long itemId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                        "FROM it_equipment_catalog WHERE id = ?",
                this::mapEquipmentRow,
                itemId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> loadItems() {
        return jdbcTemplate.query(
                "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                        "FROM it_equipment_catalog ORDER BY id DESC",
                this::mapEquipmentRow
        );
    }

    private Map<String, Object> mapEquipmentRow(ResultSet rs, int rowNum) throws SQLException {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("equipment_type", rs.getString("equipment_type"));
        row.put("equipment_vendor", rs.getString("equipment_vendor"));
        row.put("equipment_model", rs.getString("equipment_model"));
        row.put("photo_url", rs.getString("photo_url"));
        row.put("serial_number", rs.getString("serial_number"));
        row.put("accessories", rs.getString("accessories"));
        return row;
    }

    private String catalogKey(Object rawType, Object rawVendor, Object rawModel) {
        String type = stringValue(rawType).toLowerCase(Locale.ROOT);
        String vendor = stringValue(rawVendor).toLowerCase(Locale.ROOT);
        String model = stringValue(rawModel).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(type) || !StringUtils.hasText(model)) {
            return "";
        }
        return type + "|" + vendor + "|" + model;
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }
}
