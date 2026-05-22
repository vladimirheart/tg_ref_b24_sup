package com.example.panel.service;

import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.example.panel.storage.ObjectPassportPhotoStorageService;
import com.example.panel.storage.ObjectPassportPhotoStorageService.StoredPhoto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectPassportService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectsSqliteDataSourceProperties objectsProperties;
    private final ObjectMapper objectMapper;
    private final ObjectPassportPhotoStorageService photoStorageService;

    public ObjectPassportService(ObjectsSqliteDataSourceProperties objectsProperties,
                                 ObjectMapper objectMapper,
                                 ObjectPassportPhotoStorageService photoStorageService) {
        this.objectsProperties = objectsProperties;
        this.objectMapper = objectMapper;
        this.photoStorageService = photoStorageService;
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

    public Map<String, Object> uploadPhoto(long passportId,
                                           MultipartFile file,
                                           String category,
                                           String caption) throws IOException {
        StoredPhoto storedPhoto = photoStorageService.store(file);
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                StoredPassportRecord existing = loadStoredPassport(connection, passportId);
                Map<String, Object> normalized = normalizePayload(existing.payload(), Map.of(), passportId);
                List<Map<String, Object>> photos = mutablePhotoList(normalized.get("photos"));
                LinkedHashMap<String, Object> photo = new LinkedHashMap<>();
                photo.put("id", UUID.randomUUID().toString());
                photo.put("category", normalizePhotoCategory(category));
                photo.put("caption", stringValue(caption));
                photo.put("url", storedPhoto.url());
                photo.put("stored_name", storedPhoto.storedName());
                photo.put("original_name", storedPhoto.originalName());
                photo.put("mime_type", storedPhoto.mimeType());
                photo.put("size", storedPhoto.size());
                photo.put("created_at", storedPhoto.uploadedAt());
                photos.add(photo);
                normalized.put("photos", enforceSingleTitlePhoto(photos, stringValue(photo.get("id"))));
                updatePassportRow(connection, passportId, existing.objectId(), normalized);
                connection.commit();
                return Map.of("success", true, "photos", normalized.get("photos"));
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                photoStorageService.deleteQuietly(storedPhoto.storedName());
                throw ex;
            }
        } catch (SQLException ex) {
            photoStorageService.deleteQuietly(storedPhoto.storedName());
            throw new IllegalStateException("Не удалось сохранить фото паспорта объекта", ex);
        }
    }

    public Map<String, Object> updatePhoto(String photoId, Map<String, Object> payload) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                StoredPassportRecord existing = findStoredPassportByPhotoId(connection, photoId);
                Map<String, Object> normalized = normalizePayload(existing.payload(), Map.of(), existing.passportId());
                List<Map<String, Object>> photos = mutablePhotoList(normalized.get("photos"));
                boolean updated = false;
                for (Map<String, Object> photo : photos) {
                    if (!stringValue(photo.get("id")).equals(stringValue(photoId))) {
                        continue;
                    }
                    if (payload.containsKey("caption")) {
                        photo.put("caption", stringValue(payload.get("caption")));
                    }
                    if (payload.containsKey("category")) {
                        photo.put("category", normalizePhotoCategory(payload.get("category")));
                    }
                    updated = true;
                    break;
                }
                if (!updated) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
                }
                normalized.put("photos", enforceSingleTitlePhoto(photos, stringValue(photoId)));
                updatePassportRow(connection, existing.passportId(), existing.objectId(), normalized);
                connection.commit();
                return Map.of("success", true, "photos", normalized.get("photos"));
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось обновить фото паспорта объекта", ex);
        }
    }

    public Map<String, Object> deletePhoto(String photoId) {
        String storedNameToDelete = null;
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                StoredPassportRecord existing = findStoredPassportByPhotoId(connection, photoId);
                Map<String, Object> normalized = normalizePayload(existing.payload(), Map.of(), existing.passportId());
                List<Map<String, Object>> photos = mutablePhotoList(normalized.get("photos"));
                List<Map<String, Object>> remaining = new ArrayList<>();
                boolean deleted = false;
                for (Map<String, Object> photo : photos) {
                    if (stringValue(photo.get("id")).equals(stringValue(photoId))) {
                        storedNameToDelete = stringValue(photo.get("stored_name"));
                        deleted = true;
                        continue;
                    }
                    remaining.add(photo);
                }
                if (!deleted) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
                }
                normalized.put("photos", enforceSingleTitlePhoto(remaining, null));
                updatePassportRow(connection, existing.passportId(), existing.objectId(), normalized);
                connection.commit();
                photoStorageService.deleteQuietly(storedNameToDelete);
                return Map.of("success", true, "photos", normalized.get("photos"));
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось удалить фото паспорта объекта", ex);
        }
    }

    public ResponseEntity<Resource> downloadPhoto(String storedName) throws IOException {
        return photoStorageService.download(storedName);
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
    }

    private long fetchLastInsertRowId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT last_insert_rowid()");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("Не удалось получить id вставленной записи");
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
        normalized.put("photos", normalizePhotos(normalized.get("photos")));
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

    private List<Map<String, Object>> normalizePhotos(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> photos = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            LinkedHashMap<String, Object> photo = new LinkedHashMap<>();
            String id = stringValue(rawMap.get("id"));
            if (StringUtils.hasText(id)) {
                photo.put("id", id);
            }
            photo.put("category", normalizePhotoCategory(rawMap.get("category")));
            photo.put("caption", stringValue(rawMap.get("caption")));
            String storedName = firstNonBlank(rawMap.get("stored_name"), rawMap.get("storedName"), rawMap.get("filename"));
            if (StringUtils.hasText(storedName)) {
                photo.put("stored_name", storedName);
            }
            String originalName = firstNonBlank(rawMap.get("original_name"), rawMap.get("originalName"));
            if (StringUtils.hasText(originalName)) {
                photo.put("original_name", originalName);
            }
            String mimeType = stringValue(rawMap.get("mime_type"));
            if (StringUtils.hasText(mimeType)) {
                photo.put("mime_type", mimeType);
            }
            Object size = rawMap.get("size");
            if (size instanceof Number number) {
                photo.put("size", number.longValue());
            }
            String createdAt = firstNonBlank(rawMap.get("created_at"), rawMap.get("createdAt"));
            if (StringUtils.hasText(createdAt)) {
                photo.put("created_at", createdAt);
            }
            String url = firstNonBlank(rawMap.get("url"), rawMap.get("download_url"));
            if (!StringUtils.hasText(url) && StringUtils.hasText(storedName)) {
                url = photoStorageService.buildPhotoUrl(storedName);
            }
            if (StringUtils.hasText(url)) {
                photo.put("url", url);
            }
            photos.add(photo);
        }
        return enforceSingleTitlePhoto(photos, null);
    }

    private List<Map<String, Object>> mutablePhotoList(Object value) {
        List<Map<String, Object>> normalized = normalizePhotos(value);
        List<Map<String, Object>> mutable = new ArrayList<>();
        for (Map<String, Object> photo : normalized) {
            mutable.add(new LinkedHashMap<>(photo));
        }
        return mutable;
    }

    private List<Map<String, Object>> enforceSingleTitlePhoto(List<Map<String, Object>> photos, String preferredTitlePhotoId) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        String titleHolderId = null;
        if (StringUtils.hasText(preferredTitlePhotoId)) {
            for (Map<String, Object> photo : photos) {
                if (preferredTitlePhotoId.equals(stringValue(photo.get("id")))
                        && "title".equals(normalizePhotoCategory(photo.get("category")))) {
                    titleHolderId = preferredTitlePhotoId;
                    break;
                }
            }
        }
        if (!StringUtils.hasText(titleHolderId)) {
            for (Map<String, Object> photo : photos) {
                if ("title".equals(normalizePhotoCategory(photo.get("category")))) {
                    titleHolderId = stringValue(photo.get("id"));
                    break;
                }
            }
        }
        for (Map<String, Object> photo : photos) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>(photo);
            String photoId = stringValue(copy.get("id"));
            if (StringUtils.hasText(titleHolderId) && titleHolderId.equals(photoId)) {
                copy.put("category", "title");
            } else if ("title".equals(normalizePhotoCategory(copy.get("category")))) {
                copy.put("category", "archive");
            } else {
                copy.put("category", normalizePhotoCategory(copy.get("category")));
            }
            normalized.add(copy);
        }
        return normalized;
    }

    private String normalizePhotoCategory(Object raw) {
        String value = stringValue(raw).toLowerCase();
        return "title".equals(value) ? "title" : "archive";
    }

    private StoredPassportRecord findStoredPassportByPhotoId(Connection connection, String photoId) throws SQLException {
        String normalizedPhotoId = stringValue(photoId);
        if (!StringUtils.hasText(normalizedPhotoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
        }
        String sql = "SELECT id FROM object_passports";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                long passportId = rs.getLong("id");
                StoredPassportRecord record = loadStoredPassport(connection, passportId);
                List<Map<String, Object>> photos = normalizePhotos(record.payload().get("photos"));
                boolean found = photos.stream()
                        .anyMatch(photo -> normalizedPhotoId.equals(stringValue(photo.get("id"))));
                if (found) {
                    Map<String, Object> payload = new LinkedHashMap<>(record.payload());
                    payload.put("photos", photos);
                    return new StoredPassportRecord(record.passportId(), record.objectId(), payload);
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
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
