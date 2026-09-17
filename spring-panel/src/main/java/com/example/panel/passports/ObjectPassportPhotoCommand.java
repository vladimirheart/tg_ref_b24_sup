package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ObjectPassportPhotoCommand {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;
    private final ObjectPassportPhotoModel photoModel;
    private final ObjectPassportPhotoQuery photoQuery;

    ObjectPassportPhotoCommand(ObjectPassportPersistence persistence,
                               ObjectPassportPayloadModel payloadModel,
                               ObjectPassportPhotoModel photoModel,
                               ObjectPassportPhotoQuery photoQuery) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
        this.photoModel = photoModel;
        this.photoQuery = photoQuery;
    }

    Map<String, Object> upload(Connection connection,
                               long passportId,
                               UploadMetadata metadata,
                               String category,
                               String caption) throws SQLException {
        ObjectPassportPersistence.StoredPassportRecord existing =
                persistence.loadStoredPassport(connection, passportId);
        Map<String, Object> normalized =
                payloadModel.normalizePayload(existing.payload(), Map.of(), passportId);
        List<Map<String, Object>> photos = photoModel.mutablePhotoList(normalized.get("photos"));
        LinkedHashMap<String, Object> photo = new LinkedHashMap<>();
        String photoId = UUID.randomUUID().toString();
        photo.put("id", photoId);
        photo.put("category", photoModel.normalizePhotoCategory(category));
        photo.put("caption", caption == null ? "" : caption.trim());
        photo.put("url", metadata.url());
        photo.put("stored_name", metadata.storedName());
        photo.put("original_name", metadata.originalName());
        photo.put("mime_type", metadata.mimeType());
        photo.put("size", metadata.size());
        photo.put("created_at", metadata.uploadedAt());
        photos.add(photo);
        normalized.put("photos", photoModel.enforceSingleTitlePhoto(photos, photoId));
        persistence.updatePassportRow(connection, passportId, existing.objectId(), normalized);
        return Map.of("success", true, "photos", normalized.get("photos"));
    }

    Map<String, Object> update(Connection connection,
                               String photoId,
                               Map<String, Object> payload) throws SQLException {
        ObjectPassportPersistence.StoredPassportRecord existing =
                photoQuery.findStoredPassportByPhotoId(connection, photoId);
        Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), Map.of(), existing.passportId());
        List<Map<String, Object>> photos = photoModel.updatePhoto(
                normalized.get("photos"), photoId, payload);
        normalized.put("photos", photos);
        persistence.updatePassportRow(connection, existing.passportId(), existing.objectId(), normalized);
        return Map.of("success", true, "photos", normalized.get("photos"));
    }

    DeleteResult delete(Connection connection, String photoId) throws SQLException {
        ObjectPassportPersistence.StoredPassportRecord existing =
                photoQuery.findStoredPassportByPhotoId(connection, photoId);
        Map<String, Object> normalized = payloadModel.normalizePayload(existing.payload(), Map.of(), existing.passportId());
        ObjectPassportPhotoModel.PhotoDeleteResult deletion = photoModel.deletePhoto(
                normalized.get("photos"), photoId);
        normalized.put("photos", deletion.photos());
        persistence.updatePassportRow(connection, existing.passportId(), existing.objectId(), normalized);
        return new DeleteResult(
                Map.of("success", true, "photos", normalized.get("photos")),
                deletion.storedName());
    }

    record UploadMetadata(String originalName,
                          String storedName,
                          String url,
                          String mimeType,
                          long size,
                          String uploadedAt) {
    }

    record DeleteResult(Map<String, Object> response, String storedName) {
    }
}
