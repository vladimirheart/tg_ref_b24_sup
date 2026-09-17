package com.example.panel.passports;

import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteConnectionConfigSupport;
import com.example.panel.storage.ObjectPassportPhotoStorageService;
import com.example.panel.storage.ObjectPassportPhotoStorageService.StoredPhoto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ObjectPassportService {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportCreateCommand createCommand;
    private final ObjectPassportUpdateCommand updateCommand;
    private final ObjectPassportReplaceAllCommand replaceAllCommand;
    private final ObjectPassportAppealQuery appealQuery;
    private final ObjectPassportCasesQuery casesQuery;
    private final ObjectPassportDetailsQuery detailsQuery;
    private final ObjectPassportListQuery listQuery;
    private final ObjectPassportNetBoxQuery netBoxQuery;
    private final ObjectPassportEquipmentCatalogQuery equipmentCatalogQuery;
    private final ObjectPassportPhotoStorageService photoStorageService;
    private final ObjectPassportPhotoModel photoModel;
    private final ObjectPassportPhotoQuery photoQuery;
    private final ObjectPassportPayloadModel payloadModel;
    private final ObjectPassportManualOverrideModel manualOverrideModel;
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
        this.photoStorageService = photoStorageService;
        this.photoModel = new ObjectPassportPhotoModel(photoStorageService::buildPhotoUrl);
        this.payloadModel = new ObjectPassportPayloadModel(photoModel);
        this.manualOverrideModel = new ObjectPassportManualOverrideModel();
        this.persistence = new ObjectPassportPersistence(objectMapper, payloadModel);
        this.createCommand = new ObjectPassportCreateCommand(persistence, payloadModel);
        this.updateCommand = new ObjectPassportUpdateCommand(persistence, payloadModel, manualOverrideModel);
        this.replaceAllCommand = new ObjectPassportReplaceAllCommand(persistence, payloadModel, photoModel);
        this.detailsQuery = new ObjectPassportDetailsQuery(persistence, payloadModel);
        this.casesQuery = new ObjectPassportCasesQuery(persistence, payloadModel, appealQuery);
        this.equipmentCatalogQuery = new ObjectPassportEquipmentCatalogQuery(persistence);
        this.listQuery = new ObjectPassportListQuery(persistence, payloadModel, photoModel, appealQuery);
        this.photoQuery = new ObjectPassportPhotoQuery(persistence, photoModel);
        this.netBoxQuery = new ObjectPassportNetBoxQuery(persistence, payloadModel);
    }

    public Map<String, Object> createPassport(Map<String, Object> payload) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> created = createCommand.create(connection, payload);
                connection.commit();
                return created;
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
                Map<String, Object> updated = updateCommand.update(
                        connection, passportId, payload, trackManualOverrides);
                connection.commit();
                return updated;
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
            return detailsQuery.load(connection, passportId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить паспорт объекта", ex);
        }
    }

    public Map<String, Object> findPassportByNetBoxSiteId(Object siteId) {
        String normalizedSiteId = netBoxQuery.normalizeSiteId(siteId);
        if (!StringUtils.hasText(normalizedSiteId)) {
            return null;
        }
        try (Connection connection = openConnection()) {
            return netBoxQuery.findBySiteId(connection, normalizedSiteId);
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
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                Set<String> storedPhotosToDelete = replaceAllCommand.replace(connection, payloads);
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
            return equipmentCatalogQuery.listCandidates(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось собрать модели оборудования из паспортов", ex);
        }
    }

    public Map<String, Object> getEmptyCasesPayload(long passportId) {
        try (Connection connection = openConnection()) {
            return casesQuery.load(connection, passportId);
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
                ObjectPassportPersistence.StoredPassportRecord existing = photoQuery.findStoredPassportByPhotoId(connection, photoId);
                Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), Map.of(), existing.passportId());
                List<Map<String, Object>> photos = photoModel.updatePhoto(
                        normalized.get("photos"), photoId, payload);
                normalized.put("photos", photos);
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
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                ObjectPassportPersistence.StoredPassportRecord existing = photoQuery.findStoredPassportByPhotoId(connection, photoId);
                Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), Map.of(), existing.passportId());
                ObjectPassportPhotoModel.PhotoDeleteResult deletion = photoModel.deletePhoto(
                        normalized.get("photos"), photoId);
                normalized.put("photos", deletion.photos());
                persistence.updatePassportRow(connection, existing.passportId(), existing.objectId(), normalized);
                connection.commit();
                photoStorageService.deleteQuietly(deletion.storedName());
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

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

}
