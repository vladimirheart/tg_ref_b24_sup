package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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

    record DeleteResult(Map<String, Object> response, String storedName) {
    }
}
