package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

final class ObjectPassportUpdateCommand {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;
    private final ObjectPassportManualOverrideModel manualOverrideModel;

    ObjectPassportUpdateCommand(ObjectPassportPersistence persistence,
                                ObjectPassportPayloadModel payloadModel,
                                ObjectPassportManualOverrideModel manualOverrideModel) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
        this.manualOverrideModel = manualOverrideModel;
    }

    Map<String, Object> update(Connection connection,
                               long passportId,
                               Map<String, Object> payload,
                               boolean trackManualOverrides) throws SQLException {
        ObjectPassportPersistence.StoredPassportRecord existing =
                persistence.loadStoredPassport(connection, passportId);
        Map<String, Object> incoming = trackManualOverrides
                ? manualOverrideModel.markManualOverrides(existing.payload(), payload)
                : (payload == null ? Map.of() : payload);
        Map<String, Object> normalized =
                payloadModel.normalizePayload(existing.payload(), incoming, passportId);
        payloadModel.validatePayload(normalized);
        long objectId = existing.objectId();
        if (!persistence.updateObject(connection, objectId, normalized)) {
            objectId = persistence.insertObject(connection, normalized);
        }
        persistence.updatePassportRow(connection, passportId, objectId, normalized);
        return Map.of(
                "success", true,
                "id", passportId,
                "passport", payloadModel.normalizePayload(Map.of(), normalized, passportId));
    }
}
