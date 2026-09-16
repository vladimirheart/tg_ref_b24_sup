package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

final class ObjectPassportDetailsQuery {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;

    ObjectPassportDetailsQuery(ObjectPassportPersistence persistence,
                               ObjectPassportPayloadModel payloadModel) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
    }

    Map<String, Object> load(Connection connection, long passportId) throws SQLException {
        ObjectPassportPersistence.StoredPassportRecord existing =
                persistence.loadStoredPassport(connection, passportId);
        return Map.of(
                "success", true,
                "passport", payloadModel.normalizePayload(Map.of(), existing.payload(), passportId));
    }
}
