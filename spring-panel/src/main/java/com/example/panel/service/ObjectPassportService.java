package com.example.panel.service;

import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectPassportService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectsSqliteDataSourceProperties objectsProperties;
    private final ObjectMapper objectMapper;

    public ObjectPassportService(ObjectsSqliteDataSourceProperties objectsProperties,
                                 ObjectMapper objectMapper) {
        this.objectsProperties = objectsProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createPassport(Map<String, Object> payload) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> normalized = normalizePayload(Map.of(), payload, null);
                validatePayload(normalized);
                long objectId = insertObject(connection, normalized);
                long passportId = insertPassport(connection, objectId, normalized);
                connection.commit();
                return Map.of(
                        "success", true,
                        "id", passportId,
                        "passport", normalizePayload(Map.of(), normalized, passportId));
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить паспорт объекта", ex);
        }
    }

    public Map<String, Object> updatePassport(long passportId, Map<String, Object> payload) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                StoredPassportRecord existing = loadStoredPassport(connection, passportId);
                Map<String, Object> normalized = normalizePayload(existing.payload(), payload, passportId);
                validatePayload(normalized);
                long objectId = existing.objectId();
                if (!updateObject(connection, objectId, normalized)) {
                    objectId = insertObject(connection, normalized);
                }
                updatePassportRow(connection, passportId, objectId, normalized);
                connection.commit();
                return Map.of(
                        "success", true,
                        "id", passportId,
                        "passport", normalizePayload(Map.of(), normalized, passportId));
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось обновить паспорт объекта", ex);
        }
    }

    public Map<String, Object> getPassport(long passportId) {
        try (Connection connection = openConnection()) {
            StoredPassportRecord existing = loadStoredPassport(connection, passportId);
            return Map.of(
                    "success", true,
                    "passport", normalizePayload(Map.of(), existing.payload(), passportId));
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить паспорт объекта", ex);
        }
    }

    public Map<String, Object> getEmptyCasesPayload(long passportId) {
        ensurePassportExists(passportId);
        return Map.of(
                "success", true,
                "items", List.of(),
                "total_minutes", 0,
                "total_display", "0 мин");
    }

    public Map<String, Object> getEmptyTasksPayload(long passportId) {
        ensurePassportExists(passportId);
        return Map.of(
                "success", true,
                "items", List.of(),
                "total_minutes", 0,
                "total_display", "0 мин");
    }

    private void ensurePassportExists(long passportId) {
        try (Connection connection = openConnection()) {
            loadStoredPassport(connection, passportId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить паспорт объекта", ex);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(objectsProperties.buildJdbcUrl());
    }

    private void validatePayload(Map<String, Object> payload) {
        if (!StringUtils.hasText(stringValue(payload.get("department")))) {
            throw new IllegalArgumentException("Поле «Департамент» обязательно для заполнения.");
        }
    }

    private long insertObject(Connection connection, Map<String, Object> payload) throws SQLException {
        String sql = "INSERT INTO objects(name, address, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, buildObjectName(payload));
            statement.setString(2, stringValue(payload.get("location_address")));
            statement.setString(3, nowText());
            statement.executeUpdate();
            return fetchLastInsertRowId(connection);
        }
        throw new SQLException("Не удалось создать объект для паспорта");
    }

    private boolean updateObject(Connection connection, long objectId, Map<String, Object> payload) throws SQLException {
        String sql = "UPDATE objects SET name = ?, address = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, buildObjectName(payload));
            statement.setString(2, stringValue(payload.get("location_address")));
            statement.setLong(3, objectId);
            return statement.executeUpdate() > 0;
        }
    }

    private long insertPassport(Connection connection, long objectId, Map<String, Object> payload) throws SQLException {
        String sql = "INSERT INTO object_passports(object_id, passport_number, details, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, objectId);
            statement.setString(2, buildPassportNumber(payload));
            statement.setString(3, writeJson(normalizePayload(Map.of(), payload, null)));
            statement.setString(4, nowText());
            statement.executeUpdate();
            return fetchLastInsertRowId(connection);
        }
        throw new SQLException("Не удалось создать запись паспорта");
    }

    private long fetchLastInsertRowId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT last_insert_rowid()");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("РќРµ СѓРґР°Р»РѕСЃСЊ РїРѕР»СѓС‡РёС‚СЊ id РІСЃС‚Р°РІР»РµРЅРЅРѕР№ Р·Р°РїРёСЃРё");
    }

    private void updatePassportRow(Connection connection,
                                   long passportId,
                                   long objectId,
                                   Map<String, Object> payload) throws SQLException {
        String sql = "UPDATE object_passports SET object_id = ?, passport_number = ?, details = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, objectId);
            statement.setString(2, buildPassportNumber(payload));
            statement.setString(3, writeJson(normalizePayload(Map.of(), payload, passportId)));
            statement.setLong(4, passportId);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Паспорт объекта не найден");
            }
        }
    }

    private StoredPassportRecord loadStoredPassport(Connection connection, long passportId) throws SQLException {
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
                Map<String, Object> payload = readJson(rs.getString("details"));
                if (!StringUtils.hasText(stringValue(payload.get("department")))) {
                    payload.put("department", rs.getString("passport_number"));
                }
                if (!StringUtils.hasText(stringValue(payload.get("location_address")))) {
                    payload.put("location_address", rs.getString("object_address"));
                }
                return new StoredPassportRecord(
                        rs.getLong("id"),
                        rs.getLong("object_id"),
                        payload);
            }
        }
    }

    private Map<String, Object> normalizePayload(Map<String, Object> existing,
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
        ensureList(normalized, "photos");
        ensureList(normalized, "network_files");
        ensureList(normalized, "equipment");
        ensureList(normalized, "status_history");
        return normalized;
    }

    private void ensureList(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof List<?>)) {
            payload.put(key, List.of());
        }
    }

    private Map<String, Object> readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать паспорт объекта", ex);
        }
    }

    private String buildObjectName(Map<String, Object> payload) {
        String department = stringValue(payload.get("department"));
        String city = stringValue(payload.get("city"));
        String business = stringValue(payload.get("business"));
        if (StringUtils.hasText(department) && StringUtils.hasText(city)) {
            return city + " — " + department;
        }
        if (StringUtils.hasText(department)) {
            return department;
        }
        if (StringUtils.hasText(business) && StringUtils.hasText(city)) {
            return business + " — " + city;
        }
        return "Паспорт объекта";
    }

    private String buildPassportNumber(Map<String, Object> payload) {
        String department = stringValue(payload.get("department"));
        if (StringUtils.hasText(department)) {
            return department;
        }
        return buildObjectName(payload);
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String nowText() {
        return OffsetDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    private record StoredPassportRecord(long passportId, long objectId, Map<String, Object> payload) {
    }
}
