package com.example.panel.passports;

import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteConnectionConfigSupport;
import com.example.panel.storage.ObjectPassportPhotoStorageService;
import com.example.panel.storage.ObjectPassportPhotoStorageService.StoredPhoto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectPassportService {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportAppealQuery appealQuery;
    private final ObjectPassportListQuery listQuery;
    private final ObjectPassportEquipmentCatalogQuery equipmentCatalogQuery;
    private final ObjectPassportPhotoStorageService photoStorageService;
    private final ObjectPassportPhotoModel photoModel;
    private final ObjectPassportPayloadModel payloadModel;
    private final DataSource primaryDataSource;
    private final ObjectsSqliteDataSourceProperties objectsSqliteProperties;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;
    private volatile DataSource objectsCompatibilityDataSource;

    public ObjectPassportService(DataSource primaryDataSource,
                                 ObjectsSqliteDataSourceProperties objectsSqliteProperties,
                                 PanelDatabaseRuntimeMode databaseRuntimeMode,
                                 JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 ObjectPassportPhotoStorageService photoStorageService) {
        this.primaryDataSource = primaryDataSource;
        this.objectsSqliteProperties = objectsSqliteProperties;
        this.databaseRuntimeMode = databaseRuntimeMode;
        this.appealQuery = new ObjectPassportAppealQuery(jdbcTemplate);
        this.equipmentCatalogQuery = new ObjectPassportEquipmentCatalogQuery();
        this.photoStorageService = photoStorageService;
        this.photoModel = new ObjectPassportPhotoModel(photoStorageService::buildPhotoUrl);
        this.payloadModel = new ObjectPassportPayloadModel(photoModel);
        this.persistence = new ObjectPassportPersistence(objectMapper, payloadModel);
        this.listQuery = new ObjectPassportListQuery(persistence, payloadModel, photoModel, appealQuery);
    }

    public Map<String, Object> createPassport(Map<String, Object> payload) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> normalized = payloadModel.normalizePayload(Map.of(), payload, null);
                payloadModel.validatePayload(normalized);
                long objectId = persistence.insertObject(connection, normalized);
                long passportId = persistence.insertPassport(connection, objectId, normalized);
                connection.commit();
                return Map.of(
                        "success", true,
                        "id", passportId,
                        "passport", payloadModel.normalizePayload(Map.of(), normalized, passportId));
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить паспорт объекта", ex);
        }
    }

    public Map<String, Object> updatePassport(long passportId, Map<String, Object> payload) {
        return updatePassportInternal(passportId, payload, true);
    }

    private Map<String, Object> updatePassportFromExternalSource(long passportId,
                                                                 Map<String, Object> payload) {
        return updatePassportInternal(passportId, payload, false);
    }

    private Map<String, Object> updatePassportInternal(long passportId,
                                                       Map<String, Object> payload,
                                                       boolean trackManualOverrides) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                ObjectPassportPersistence.StoredPassportRecord existing = persistence.loadStoredPassport(connection, passportId);
                Map<String, Object> incoming = trackManualOverrides
                        ? markManualOverrides(existing.payload(), payload)
                        : (payload == null ? Map.of() : payload);
                Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), incoming, passportId);
                payloadModel.validatePayload(normalized);
                long objectId = existing.objectId();
                if (!persistence.updateObject(connection, objectId, normalized)) {
                    objectId = persistence.insertObject(connection, normalized);
                }
                persistence.updatePassportRow(connection, passportId, objectId, normalized);
                connection.commit();
                return Map.of(
                        "success", true,
                        "id", passportId,
                        "passport", payloadModel.normalizePayload(Map.of(), normalized, passportId));
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось обновить паспорт объекта", ex);
        }
    }

    static Map<String, Object> markManualOverrides(Map<String, Object> existing,
                                                   Map<String, Object> incoming) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (incoming != null) {
            result.putAll(incoming);
        }
        java.util.LinkedHashSet<String> overrides = new java.util.LinkedHashSet<>();
        Object storedOverrides = existing == null ? null : existing.get("_manual_overrides");
        if (storedOverrides instanceof List<?> list) {
            for (Object item : list) {
                String key = item == null ? "" : String.valueOf(item).trim();
                if (StringUtils.hasText(key)) {
                    overrides.add(key);
                }
            }
        }
        if (incoming != null) {
            for (Map.Entry<String, Object> entry : incoming.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
                if (!StringUtils.hasText(key) || key.startsWith("_") || "id".equals(key) || "is_new".equals(key)) {
                    continue;
                }
                Object previous = existing == null ? null : existing.get(key);
                if (!java.util.Objects.deepEquals(previous, entry.getValue())) {
                    overrides.add(key);
                }
            }
        }
        result.put("_manual_overrides", List.copyOf(overrides));
        return result;
    }

    public Map<String, Object> getPassport(long passportId) {
        try (Connection connection = openConnection()) {
            ObjectPassportPersistence.StoredPassportRecord existing = persistence.loadStoredPassport(connection, passportId);
            return Map.of(
                    "success", true,
                    "passport", payloadModel.normalizePayload(Map.of(), existing.payload(), passportId));
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить паспорт объекта", ex);
        }
    }

    public Map<String, Object> findPassportByNetBoxSiteId(Object siteId) {
        String normalizedSiteId = stringValue(siteId);
        if (!StringUtils.hasText(normalizedSiteId)) {
            return null;
        }
        try (Connection connection = openConnection()) {
            for (ObjectPassportPersistence.StoredPassportRecord record : persistence.loadAllStoredPassports(connection)) {
                if (normalizedSiteId.equals(stringValue(record.payload().get("netbox_site_id")))) {
                    return payloadModel.normalizePayload(Map.of(), record.payload(), record.passportId());
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new IllegalStateException("РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р№С‚Рё РїР°СЃРїРѕСЂС‚ NetBox-РѕР±СЉРµРєС‚Р°", ex);
        }
    }

    public Map<String, Object> upsertPassportByNetBoxSiteId(Object siteId,
                                                            Map<String, Object> payload) {
        Map<String, Object> existing = findPassportByNetBoxSiteId(siteId);
        if (existing != null && existing.get("id") instanceof Number id) {
            return updatePassportFromExternalSource(id.longValue(), payload);
        }
        return createPassport(payload);
    }

    public void replaceAllPassports(List<Map<String, Object>> payloads) {
        List<Map<String, Object>> safePayloads = payloads == null ? List.of() : payloads;
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                List<ObjectPassportPersistence.StoredPassportRecord> existing = persistence.loadAllStoredPassports(connection);
                Set<String> storedPhotosToDelete = collectStoredPhotos(existing);

                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM object_passports")) {
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM objects")) {
                    statement.executeUpdate();
                }

                for (Map<String, Object> payload : safePayloads) {
                    Map<String, Object> normalized = payloadModel.normalizePayload(Map.of(), payload, null);
                    payloadModel.validatePayload(normalized);
                    long objectId = persistence.insertObject(connection, normalized);
                    persistence.insertPassport(connection, objectId, normalized);
                }

                connection.commit();
                storedPhotosToDelete.forEach(photoStorageService::deleteQuietly);
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("РќРµ СѓРґР°Р»РѕСЃСЊ РїРѕР»РЅРѕСЃС‚СЊСЋ РѕР±РЅРѕРІРёС‚СЊ РїР°СЃРїРѕСЂС‚С‹ РѕР±СЉРµРєС‚РѕРІ", ex);
        }
    }

    public List<Map<String, Object>> listPassports() {
        Map<String, Long> appealsCountByLocation = appealQuery.loadAppealCountsByLocation();
        try (Connection connection = openConnection()) {
            return listQuery.list(connection, appealsCountByLocation);
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить список паспортов объектов", ex);
        }
    }

    public List<Map<String, Object>> listEquipmentCatalogCandidates() {
        try (Connection connection = openConnection()) {
            List<Map<String, Object>> passportPayloads = new ArrayList<>();
            for (ObjectPassportPersistence.StoredPassportRecord record : persistence.loadAllStoredPassports(connection)) {
                passportPayloads.add(record.payload());
            }
            return equipmentCatalogQuery.listCandidates(passportPayloads);
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось собрать модели оборудования из паспортов", ex);
        }
    }

    public Map<String, Object> getEmptyCasesPayload(long passportId) {
        try (Connection connection = openConnection()) {
            ObjectPassportPersistence.StoredPassportRecord existing = persistence.loadStoredPassport(connection, passportId);
            Map<String, Object> normalized = payloadModel.normalizePayload(Map.of(), existing.payload(), passportId);
            List<Map<String, Object>> items = appealQuery.loadCases(normalized);
            return Map.of(
                    "success", true,
                    "items", items,
                    "total_count", items.size(),
                    "total_minutes", 0,
                    "total_display", "0 мин");
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить обращения паспорта объекта", ex);
        }
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
                ObjectPassportPersistence.StoredPassportRecord existing = persistence.loadStoredPassport(connection, passportId);
                Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), Map.of(), passportId);
                List<Map<String, Object>> photos = photoModel.mutablePhotoList(normalized.get("photos"));
                LinkedHashMap<String, Object> photo = new LinkedHashMap<>();
                photo.put("id", UUID.randomUUID().toString());
                photo.put("category", photoModel.normalizePhotoCategory(category));
                photo.put("caption", stringValue(caption));
                photo.put("url", storedPhoto.url());
                photo.put("stored_name", storedPhoto.storedName());
                photo.put("original_name", storedPhoto.originalName());
                photo.put("mime_type", storedPhoto.mimeType());
                photo.put("size", storedPhoto.size());
                photo.put("created_at", storedPhoto.uploadedAt());
                photos.add(photo);
                normalized.put("photos", photoModel.enforceSingleTitlePhoto(photos, stringValue(photo.get("id"))));
                persistence.updatePassportRow(connection, passportId, existing.objectId(), normalized);
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
                ObjectPassportPersistence.StoredPassportRecord existing = findStoredPassportByPhotoId(connection, photoId);
                Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), Map.of(), existing.passportId());
                List<Map<String, Object>> photos = photoModel.mutablePhotoList(normalized.get("photos"));
                boolean updated = false;
                for (Map<String, Object> photo : photos) {
                    if (!stringValue(photo.get("id")).equals(stringValue(photoId))) {
                        continue;
                    }
                    if (payload.containsKey("caption")) {
                        photo.put("caption", stringValue(payload.get("caption")));
                    }
                    if (payload.containsKey("category")) {
                        photo.put("category", photoModel.normalizePhotoCategory(payload.get("category")));
                    }
                    updated = true;
                    break;
                }
                if (!updated) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
                }
                normalized.put("photos", photoModel.enforceSingleTitlePhoto(photos, stringValue(photoId)));
                persistence.updatePassportRow(connection, existing.passportId(), existing.objectId(), normalized);
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
                ObjectPassportPersistence.StoredPassportRecord existing = findStoredPassportByPhotoId(connection, photoId);
                Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), Map.of(), existing.passportId());
                List<Map<String, Object>> photos = photoModel.mutablePhotoList(normalized.get("photos"));
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
                normalized.put("photos", photoModel.enforceSingleTitlePhoto(remaining, null));
                persistence.updatePassportRow(connection, existing.passportId(), existing.objectId(), normalized);
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
            persistence.loadStoredPassport(connection, passportId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить паспорт объекта", ex);
        }
    }

    private Connection openConnection() throws SQLException {
        return runtimeObjectsDataSource().getConnection();
    }

    private DataSource runtimeObjectsDataSource() {
        if (databaseRuntimeMode.isExternalDatabaseEnabled()) {
            return primaryDataSource;
        }
        DataSource cached = objectsCompatibilityDataSource;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (objectsCompatibilityDataSource == null) {
                objectsCompatibilityDataSource = SqliteConnectionConfigSupport.createDataSource(objectsSqliteProperties);
            }
            return objectsCompatibilityDataSource;
        }
    }

    private Set<String> collectStoredPhotos(List<ObjectPassportPersistence.StoredPassportRecord> records) {
        Set<String> storedNames = new HashSet<>();
        if (records == null) {
            return storedNames;
        }
        for (ObjectPassportPersistence.StoredPassportRecord record : records) {
            List<Map<String, Object>> photos = photoModel.normalizePhotos(record.payload().get("photos"));
            for (Map<String, Object> photo : photos) {
                String storedName = stringValue(photo.get("stored_name"));
                if (StringUtils.hasText(storedName)) {
                    storedNames.add(storedName);
                }
            }
        }
        return storedNames;
    }

    private ObjectPassportPersistence.StoredPassportRecord findStoredPassportByPhotoId(Connection connection, String photoId) throws SQLException {
        String normalizedPhotoId = stringValue(photoId);
        if (!StringUtils.hasText(normalizedPhotoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
        }
        String sql = "SELECT id FROM object_passports";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                long passportId = rs.getLong("id");
                ObjectPassportPersistence.StoredPassportRecord record = persistence.loadStoredPassport(connection, passportId);
                List<Map<String, Object>> photos = photoModel.normalizePhotos(record.payload().get("photos"));
                boolean found = photos.stream()
                        .anyMatch(photo -> normalizedPhotoId.equals(stringValue(photo.get("id"))));
                if (found) {
                    Map<String, Object> payload = new LinkedHashMap<>(record.payload());
                    payload.put("photos", photos);
                    return new ObjectPassportPersistence.StoredPassportRecord(record.passportId(), record.objectId(), payload);
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

}
