package com.example.panel.passports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ObjectPassportListQuery {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;
    private final ObjectPassportPhotoModel photoModel;
    private final ObjectPassportAppealQuery appealQuery;

    ObjectPassportListQuery(ObjectPassportPersistence persistence,
                            ObjectPassportPayloadModel payloadModel,
                            ObjectPassportPhotoModel photoModel,
                            ObjectPassportAppealQuery appealQuery) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
        this.photoModel = photoModel;
        this.appealQuery = appealQuery;
    }

    List<Map<String, Object>> list(Connection connection,
                                   Map<String, Long> appealsCountByLocation) throws SQLException {
        String sql = """
                SELECT p.id, p.object_id, p.passport_number, p.details, o.name AS object_name, o.address AS object_address
                FROM object_passports p
                LEFT JOIN objects o ON o.id = p.object_id
                ORDER BY p.id DESC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Map<String, Object>> items = new ArrayList<>();
            while (rs.next()) {
                long passportId = rs.getLong("id");
                Map<String, Object> payload = persistence.readPayload(rs.getString("details"));
                Map<String, Object> normalized = payloadModel.normalizePayload(Map.of(), payload, passportId);
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("id", passportId);
                item.put("department", stringValue(normalized.get("department")));
                item.put("city", stringValue(normalized.get("city")));
                item.put("business", stringValue(normalized.get("business")));
                String status = stringValue(normalized.get("status"));
                List<Map<String, Object>> photos = photoModel.normalizePhotos(normalized.get("photos"));
                item.put("status", status);
                item.put("deleted", payloadModel.isDeletedStatus(status));
                item.put("title_photo_url", photoModel.findTitlePhotoUrl(photos));
                item.put("location_address", firstNonBlank(normalized.get("location_address"), rs.getString("object_address")));
                item.put("passport_number", firstNonBlank(normalized.get("department"), rs.getString("passport_number")));
                item.put("object_name", firstNonBlank(rs.getString("object_name"), normalized.get("department")));
                item.put("appeals_count", appealQuery.resolveAppealCount(appealsCountByLocation, normalized));
                item.put("photos", photos);
                items.add(item);
            }
            return items;
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

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
