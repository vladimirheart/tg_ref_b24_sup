package com.example.panel.passports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

final class ObjectPassportPhotoQuery {

    private final ObjectPassportPersistence persistence;
    private final ObjectPassportPhotoModel photoModel;

    ObjectPassportPhotoQuery(ObjectPassportPersistence persistence,
                             ObjectPassportPhotoModel photoModel) {
        this.persistence = persistence;
        this.photoModel = photoModel;
    }

    ObjectPassportPersistence.StoredPassportRecord findStoredPassportByPhotoId(Connection connection,
                                                                                String photoId) throws SQLException {
        String normalizedPhotoId = stringValue(photoId);
        if (!StringUtils.hasText(normalizedPhotoId)) {
            throw photoNotFound();
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
                    return new ObjectPassportPersistence.StoredPassportRecord(
                            record.passportId(), record.objectId(), payload);
                }
            }
        }
        throw photoNotFound();
    }

    private ResponseStatusException photoNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Фото паспорта не найдено");
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
