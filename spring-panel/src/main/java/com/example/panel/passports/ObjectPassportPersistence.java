package com.example.panel.passports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

final class ObjectPassportPersistence {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectMapper objectMapper;
    private final ObjectPassportPayloadModel payloadModel;

    ObjectPassportPersistence(ObjectMapper objectMapper, ObjectPassportPayloadModel payloadModel) {
        this.objectMapper = objectMapper;
        this.payloadModel = payloadModel;
    }

    long insertObject(Connection connection, Map<String, Object> payload) throws SQLException {
        String sql = "INSERT INTO objects(name, address, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, payloadModel.buildObjectName(payload));
            statement.setString(2, stringValue(payload.get("location_address")));
            statement.setString(3, nowText());
            statement.executeUpdate();
            return readGeneratedKey(statement, "objects");
        }
    }

    boolean updateObject(Connection connection, long objectId, Map<String, Object> payload) throws SQLException {
        String sql = "UPDATE objects SET name = ?, address = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, payloadModel.buildObjectName(payload));
            statement.setString(2, stringValue(payload.get("location_address")));
            statement.setLong(3, objectId);
            return statement.executeUpdate() > 0;
        }
    }

    long insertPassport(Connection connection, long objectId, Map<String, Object> payload) throws SQLException {
        String sql = "INSERT INTO object_passports(object_id, passport_number, details, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, objectId);
            statement.setString(2, payloadModel.buildPassportNumber(payload));
            statement.setString(3, writeJson(payloadModel.normalizePayload(Map.of(), payload, null)));
            statement.setString(4, nowText());
            statement.executeUpdate();
            return readGeneratedKey(statement, "object_passports");
        }
    }

    void updatePassportRow(Connection connection,
                           long passportId,
                           long objectId,
                           Map<String, Object> payload) throws SQLException {
        String sql = "UPDATE object_passports SET object_id = ?, passport_number = ?, details = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, objectId);
            statement.setString(2, payloadModel.buildPassportNumber(payload));
            statement.setString(3, writeJson(payloadModel.normalizePayload(Map.of(), payload, passportId)));
            statement.setLong(4, passportId);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Паспорт объекта не найден");
            }
        }
    }

    StoredPassportRecord loadStoredPassport(Connection connection, long passportId) throws SQLException {
        String sql = """
                SELECT p.id, p.object_id, p.passport_number, p.details, o.name AS object_name, o.address AS object_address
                FROM object_passports p
                LEFT JOIN objects o ON o.id = p.object_id
                WHERE p.id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, passportId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Паспорт объекта не найден");
                }
                Map<String, Object> payload = readPayload(rs.getString("details"));
                if (!StringUtils.hasText(stringValue(payload.get("department")))) {
                    payload.put("department", rs.getString("passport_number"));
                }
                if (!StringUtils.hasText(stringValue(payload.get("location_address")))) {
                    payload.put("location_address", rs.getString("object_address"));
                }
                return new StoredPassportRecord(rs.getLong("id"), rs.getLong("object_id"), payload);
            }
        }
    }

    List<StoredPassportRecord> loadAllStoredPassports(Connection connection) throws SQLException {
        String sql = """
                SELECT p.id, p.object_id, p.passport_number, p.details, o.name AS object_name, o.address AS object_address
                FROM object_passports p
                LEFT JOIN objects o ON o.id = p.object_id
                ORDER BY p.id ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<StoredPassportRecord> items = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> payload = readPayload(rs.getString("details"));
                if (!StringUtils.hasText(stringValue(payload.get("department")))) {
                    payload.put("department", rs.getString("passport_number"));
                }
                if (!StringUtils.hasText(stringValue(payload.get("location_address")))) {
                    payload.put("location_address", rs.getString("object_address"));
                }
                items.add(new StoredPassportRecord(rs.getLong("id"), rs.getLong("object_id"), payload));
            }
            return items;
        }
    }

    void deleteAllPassportData(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM object_passports")) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM objects")) {
            statement.executeUpdate();
        }
    }

    Map<String, Object> readPayload(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private long readGeneratedKey(PreparedStatement statement, String tableName) throws SQLException {
        try (ResultSet rs = statement.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("Failed to read generated key for " + tableName);
    }

    private long fetchLastInsertRowId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 0");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("Не удалось получить id вставленной записи");
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать паспорт объекта", ex);
        }
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String nowText() {
        return OffsetDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    record StoredPassportRecord(long passportId, long objectId, Map<String, Object> payload) {
    }
}
