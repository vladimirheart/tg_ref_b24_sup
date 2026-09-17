package com.example.panel.passports;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class ObjectPassportReplaceAllCommand {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPayloadModel payloadModel;
    private final ObjectPassportPhotoModel photoModel;

    ObjectPassportReplaceAllCommand(ObjectPassportPersistence persistence,
                                    ObjectPassportPayloadModel payloadModel,
                                    ObjectPassportPhotoModel photoModel) {
        this.persistence = persistence;
        this.payloadModel = payloadModel;
        this.photoModel = photoModel;
    }

    Set<String> replace(Connection connection, List<Map<String, Object>> payloads) throws SQLException {
        List<Map<String, Object>> safePayloads = payloads == null ? List.of() : payloads;
        List<ObjectPassportPersistence.StoredPassportRecord> existing =
                persistence.loadAllStoredPassports(connection);
        Set<String> storedPhotosToDelete = collectStoredPhotos(existing);

        persistence.deleteAllPassportData(connection);

        for (Map<String, Object> payload : safePayloads) {
            Map<String, Object> normalized = payloadModel.normalizePayload(Map.of(), payload, null);
            payloadModel.validatePayload(normalized);
            long objectId = persistence.insertObject(connection, normalized);
            persistence.insertPassport(connection, objectId, normalized);
        }
        return storedPhotosToDelete;
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

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
