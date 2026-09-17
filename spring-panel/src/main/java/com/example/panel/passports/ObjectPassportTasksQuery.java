package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

final class ObjectPassportTasksQuery {

    private final ObjectPassportPersistence persistence;

    ObjectPassportTasksQuery(ObjectPassportPersistence persistence) {
        this.persistence = persistence;
    }

    Map<String, Object> load(Connection connection, long passportId) throws SQLException {
        persistence.loadStoredPassport(connection, passportId);
        return Map.of(
                "success", true,
                "items", List.of(),
                "total_minutes", 0,
                "total_display", "0 мин");
    }
}
