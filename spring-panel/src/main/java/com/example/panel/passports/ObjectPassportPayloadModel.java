package com.example.panel.passports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ObjectPassportPayloadModel {

    private final ObjectPassportPhotoModel photoModel;

    ObjectPassportPayloadModel(ObjectPassportPhotoModel photoModel) {
        this.photoModel = photoModel;
    }

    Map<String, Object> normalizePayload(Map<String, Object> existing,
                                         Map<String, Object> incoming,
                                         Long passportId) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (existing != null) {
            normalized.putAll(existing);
        }
        if (incoming != null) {
            normalized.putAll(incoming);
        }
        if (passportId != null) {
            normalized.put("id", passportId);
        }
        normalized.put("is_new", false);
        ensureList(normalized, "schedule");
        ensureList(normalized, "cases");
        ensureList(normalized, "tasks");
        normalized.put("photos", photoModel.normalizePhotos(normalized.get("photos")));
        ensureList(normalized, "network_files");
        ensureList(normalized, "equipment");
        ensureList(normalized, "status_history");
        return normalized;
    }

    void validatePayload(Map<String, Object> payload) {
        if (!StringUtils.hasText(stringValue(payload.get("department")))) {
            throw new IllegalArgumentException("Поле «Департамент» обязательно для заполнения.");
        }
    }

    String buildObjectName(Map<String, Object> payload) {
        String department = stringValue(payload.get("department"));
        String city = stringValue(payload.get("city"));
        String business = stringValue(payload.get("business"));
        if (StringUtils.hasText(department) && StringUtils.hasText(city)) {
            return city + " - " + department;
        }
        if (StringUtils.hasText(department)) {
            return department;
        }
        if (StringUtils.hasText(business) && StringUtils.hasText(city)) {
            return business + " - " + city;
        }
        return "Паспорт объекта";
    }

    String buildPassportNumber(Map<String, Object> payload) {
        String department = stringValue(payload.get("department"));
        if (StringUtils.hasText(department)) {
            return department;
        }
        return buildObjectName(payload);
    }

    boolean isDeletedStatus(Object raw) {
        String status = normalizeLookupValue(raw);
        return "удален".equals(status) || "deleted".equals(status);
    }

    private void ensureList(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof List<?>)) {
            payload.put(key, List.of());
        }
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
