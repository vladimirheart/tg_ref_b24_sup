package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

final class ObjectPassportCasesQuery {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;
    private final ObjectPassportAppealQuery appealQuery;

    ObjectPassportCasesQuery(ObjectPassportPersistence persistence,
                             ObjectPassportPayloadModel payloadModel,
                             ObjectPassportAppealQuery appealQuery) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
        this.appealQuery = appealQuery;
    }

    Map<String, Object> load(Connection connection, long passportId) throws SQLException {
        ObjectPassportPersistence.StoredPassportRecord existing =
                persistence.loadStoredPassport(connection, passportId);
        Map<String, Object> normalized =
                payloadModel.normalizePayload(Map.of(), existing.payload(), passportId);
        List<Map<String, Object>> items = appealQuery.loadCases(normalized);
        return Map.of(
                "success", true,
                "items", items,
                "total_count", items.size(),
                "total_minutes", 0,
                "total_display", "0 мин");
    }
}
