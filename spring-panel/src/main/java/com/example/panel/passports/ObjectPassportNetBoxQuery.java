package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ObjectPassportNetBoxQuery {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;

    ObjectPassportNetBoxQuery(ObjectPassportPersistence persistence,
                              ObjectPassportPayloadModel payloadModel) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
    }

    String normalizeSiteId(Object siteId) {
        return stringValue(siteId);
    }

    Map<String, Object> findBySiteId(Connection connection,
                                     String normalizedSiteId) throws SQLException {
        if (!StringUtils.hasText(normalizedSiteId)) {
            return null;
        }
        for (ObjectPassportPersistence.StoredPassportRecord record : persistence.loadAllStoredPassports(connection)) {
            if (normalizedSiteId.equals(stringValue(record.payload().get("netbox_site_id")))) {
                return payloadModel.normalizePayload(Map.of(), record.payload(), record.passportId());
            }
        }
        return null;
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
