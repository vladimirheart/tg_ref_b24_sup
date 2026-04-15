package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SettingsItEquipmentService {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

    public SettingsItEquipmentService(JdbcTemplate jdbcTemplate,
                                      NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    public Map<String, Object> listItEquipment() {
        return Map.of("success", true, "items", loadItems());
    }

    public Map<String, Object> createItEquipment(Map<String, Object> payload, String actor) {
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
        notificationService.notifyAllOperators(
                "Создан паспорт оборудования: " + vendor + " " + model,
                "/object-passports",
                actor
        );
        return Map.of("success", true, "items", loadItems());
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
        notificationService.notifyAllOperators(
                "Обновлен паспорт оборудования #" + itemId,
                "/object-passports/" + itemId,
                actor
        );
        return Map.of("success", true, "items", loadItems());
    }

    public Map<String, Object> deleteItEquipment(long itemId, String actor) {
        int removed = jdbcTemplate.update("DELETE FROM it_equipment_catalog WHERE id = ?", itemId);
        if (removed == 0) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        notificationService.notifyAllOperators(
                "Удален паспорт оборудования #" + itemId,
                "/object-passports",
                actor
        );
        return Map.of("success", true, "items", loadItems());
    }

    private List<Map<String, Object>> loadItems() {
        return jdbcTemplate.queryForList(
                "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                        "FROM it_equipment_catalog ORDER BY id DESC"
        );
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }
}
