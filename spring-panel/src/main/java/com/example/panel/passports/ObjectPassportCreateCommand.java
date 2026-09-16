package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

final class ObjectPassportCreateCommand {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;

    ObjectPassportCreateCommand(ObjectPassportPersistence persistence,
                                ObjectPassportPayloadModel payloadModel) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
    }

    Map<String, Object> create(Connection connection, Map<String, Object> payload) throws SQLException {
        Map<String, Object> normalized = payloadModel.normalizePayload(Map.of(), payload, null);
        payloadModel.validatePayload(normalized);
        long objectId = persistence.insertObject(connection, normalized);
        long passportId = persistence.insertPassport(connection, objectId, normalized);
        return Map.of(
                "success", true,
                "id", passportId,
                "passport", payloadModel.normalizePayload(Map.of(), normalized, passportId));
    }
}
