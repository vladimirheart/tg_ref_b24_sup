package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public SettingsItEquipmentService(JdbcTemplate jdbcTemplate,
                                      NotificationRoutingService notificationRoutingService,
                                      ObjectPassportService objectPassportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationRoutingService = notificationRoutingService;
        this.objectPassportService = objectPassportService;
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
        String photoUrl = stringValue(payload.getOrDefault("photo_url", payload.get("photo")));
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
        updates.append("updated_at = CURRENT_TIMESTAMP");
        params.add(itemId);
        jdbcTemplate.update("UPDATE it_equipment_catalog SET " + updates + " WHERE id = ?", params.toArray());
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
        int removed = jdbcTemplate.update("DELETE FROM it_equipment_catalog WHERE id = ?", itemId);
        if (removed == 0) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
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

    private List<Map<String, Object>> loadItemsWithDiscovered() {
        List<Map<String, Object>> persisted = loadItems();
        List<Map<String, Object>> merged = new ArrayList<>();
        Set<String> persistedKeys = new LinkedHashSet<>();
        for (Map<String, Object> row : persisted) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>(row);
            item.put("discovered", false);
            item.put("source", "catalog");
            merged.add(item);
            persistedKeys.add(catalogKey(row.get("equipment_type"), row.get("equipment_vendor"), row.get("equipment_model")));
        }
        for (Map<String, Object> candidate : objectPassportService.listEquipmentCatalogCandidates()) {
            String key = catalogKey(candidate.get("equipment_type"), candidate.get("equipment_vendor"), candidate.get("equipment_model"));
            if (!StringUtils.hasText(key) || persistedKeys.contains(key)) {
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

    private List<Map<String, Object>> loadItems() {
        return jdbcTemplate.queryForList(
                "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                        "FROM it_equipment_catalog ORDER BY id DESC"
        );
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
