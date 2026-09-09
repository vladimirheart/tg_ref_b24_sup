package com.example.panel.service;

import com.example.panel.storage.ObjectPassportPhotoStorageService;
import com.example.panel.storage.ObjectPassportPhotoStorageService.StoredPhoto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SettingsItEquipmentPhotoService {

    private static final int MEDIA_VERSION = 2;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectPassportPhotoStorageService photoStorageService;

    public SettingsItEquipmentPhotoService(JdbcTemplate jdbcTemplate,
                                           ObjectMapper objectMapper,
                                           ObjectPassportPhotoStorageService photoStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.photoStorageService = photoStorageService;
    }

    @Transactional
    public Map<String, Object> uploadPhoto(long itemId,
                                           MultipartFile file,
                                           String category,
                                           String comment) throws IOException {
        return uploadPhoto(itemId, file, category, comment, false);
    }

    @Transactional
    public Map<String, Object> uploadPhoto(long itemId,
                                           MultipartFile file,
                                           String category,
                                           String comment,
                                           boolean replaceTitle) throws IOException {
        String normalizedCategory = normalizeCategory(category);
        String normalizedComment = stringValue(comment);
        Map<String, Object> validation = validateMetadata(normalizedCategory, normalizedComment);
        if (validation != null) {
            return validation;
        }

        Map<String, Object> row = loadRowForUpdate(itemId);
        if (row == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }

        EquipmentMedia media = parseMedia(stringValue(row.get("photo_url")));
        List<Map<String, Object>> photos = mutablePhotos(media.photos());
        Map<String, Object> existingTitle = findTitlePhoto(photos, null);
        if ("title".equals(normalizedCategory) && existingTitle != null && !replaceTitle) {
            return titleReplacementRequired(existingTitle);
        }

        StoredPhoto stored = photoStorageService.store(file);
        deleteStoredOnRollback(stored.storedName());
        try {
            if ("title".equals(normalizedCategory)) {
                demoteOtherTitles(photos, null);
            }

            LinkedHashMap<String, Object> photo = new LinkedHashMap<>();
            photo.put("id", UUID.randomUUID().toString());
            photo.put("category", normalizedCategory);
            photo.put("comment", normalizedComment);
            photo.put("url", buildPhotoUrl(stored.storedName()));
            photo.put("stored_name", stored.storedName());
            photo.put("original_name", stored.originalName());
            photo.put("mime_type", stored.mimeType());
            photo.put("size", stored.size());
            photo.put("created_at", stored.uploadedAt());
            photos.add(photo);

            String encoded = serializeMedia(media.links(), photos);
            if (updateMedia(itemId, encoded) != 1) {
                photoStorageService.deleteQuietly(stored.storedName());
                return Map.of("success", false, "error", "Оборудование не найдено");
            }
            return successPayload(encoded, parseMedia(encoded).photos(), null);
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                photoStorageService.deleteQuietly(stored.storedName());
            }
            throw ex;
        }
    }

    @Transactional
    public Map<String, Object> updatePhoto(long itemId,
                                           String photoId,
                                           String category,
                                           String comment,
                                           boolean replaceTitle) {
        String normalizedId = stringValue(photoId);
        String normalizedCategory = normalizeCategory(category);
        String normalizedComment = stringValue(comment);
        if (!StringUtils.hasText(normalizedId)) {
            return Map.of("success", false, "error", "Фото не найдено");
        }
        Map<String, Object> validation = validateMetadata(normalizedCategory, normalizedComment);
        if (validation != null) {
            return validation;
        }

        Map<String, Object> row = loadRowForUpdate(itemId);
        if (row == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }

        EquipmentMedia media = parseMedia(stringValue(row.get("photo_url")));
        List<Map<String, Object>> photos = mutablePhotos(media.photos());
        int targetIndex = findPhotoIndex(photos, normalizedId);
        if (targetIndex < 0) {
            return Map.of("success", false, "error", "Фото не найдено");
        }

        Map<String, Object> target = photos.get(targetIndex);
        boolean targetWasTitle = "title".equals(normalizeCategory(target.get("category")));
        Map<String, Object> anotherTitle = findTitlePhoto(photos, normalizedId);
        if ("title".equals(normalizedCategory) && anotherTitle != null && !replaceTitle) {
            return titleReplacementRequired(anotherTitle);
        }

        if ("title".equals(normalizedCategory)) {
            demoteOtherTitles(photos, normalizedId);
        }

        LinkedHashMap<String, Object> updatedPhoto = new LinkedHashMap<>(photos.get(targetIndex));
        updatedPhoto.put("category", normalizedCategory);
        updatedPhoto.put("comment", normalizedComment);
        photos.set(targetIndex, updatedPhoto);

        String promotedId = null;
        if (targetWasTitle && "general".equals(normalizedCategory)) {
            promotedId = promoteFallbackTitle(photos, normalizedId);
        }

        String encoded = serializeMedia(media.links(), photos);
        if (updateMedia(itemId, encoded) != 1) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        return successPayload(encoded, parseMedia(encoded).photos(), promotedId);
    }

    @Transactional
    public Map<String, Object> replacePhoto(long itemId,
                                            String photoId,
                                            MultipartFile file,
                                            String category,
                                            String comment,
                                            boolean replaceTitle) throws IOException {
        String normalizedId = stringValue(photoId);
        String normalizedCategory = normalizeCategory(category);
        String normalizedComment = stringValue(comment);
        if (!StringUtils.hasText(normalizedId)) {
            return Map.of("success", false, "error", "Фото не найдено");
        }
        Map<String, Object> validation = validateMetadata(normalizedCategory, normalizedComment);
        if (validation != null) {
            return validation;
        }

        Map<String, Object> row = loadRowForUpdate(itemId);
        if (row == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }

        EquipmentMedia media = parseMedia(stringValue(row.get("photo_url")));
        List<Map<String, Object>> photos = mutablePhotos(media.photos());
        int targetIndex = findPhotoIndex(photos, normalizedId);
        if (targetIndex < 0) {
            return Map.of("success", false, "error", "Фото не найдено");
        }

        Map<String, Object> target = photos.get(targetIndex);
        boolean targetWasTitle = "title".equals(normalizeCategory(target.get("category")));
        Map<String, Object> anotherTitle = findTitlePhoto(photos, normalizedId);
        if ("title".equals(normalizedCategory) && anotherTitle != null && !replaceTitle) {
            return titleReplacementRequired(anotherTitle);
        }

        StoredPhoto stored = photoStorageService.store(file);
        deleteStoredOnRollback(stored.storedName());
        try {
            if ("title".equals(normalizedCategory)) {
                demoteOtherTitles(photos, normalizedId);
            }

            LinkedHashMap<String, Object> updatedPhoto = new LinkedHashMap<>(target);
            updatedPhoto.put("category", normalizedCategory);
            updatedPhoto.put("comment", normalizedComment);
            updatedPhoto.put("url", buildPhotoUrl(stored.storedName()));
            updatedPhoto.put("stored_name", stored.storedName());
            updatedPhoto.put("original_name", stored.originalName());
            updatedPhoto.put("mime_type", stored.mimeType());
            updatedPhoto.put("size", stored.size());
            updatedPhoto.put("updated_at", stored.uploadedAt());
            photos.set(targetIndex, updatedPhoto);

            String promotedId = null;
            if (targetWasTitle && "general".equals(normalizedCategory)) {
                promotedId = promoteFallbackTitle(photos, normalizedId);
            }

            String encoded = serializeMedia(media.links(), photos);
            if (updateMedia(itemId, encoded) != 1) {
                photoStorageService.deleteQuietly(stored.storedName());
                return Map.of("success", false, "error", "Оборудование не найдено");
            }

            deleteStoredAfterCommit(stringValue(target.get("stored_name")));
            return successPayload(encoded, parseMedia(encoded).photos(), promotedId);
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                photoStorageService.deleteQuietly(stored.storedName());
            }
            throw ex;
        }
    }

    @Transactional
    public Map<String, Object> deletePhoto(long itemId, String photoId) {
        String normalizedId = stringValue(photoId);
        if (!StringUtils.hasText(normalizedId)) {
            return Map.of("success", false, "error", "Фото не найдено");
        }
        Map<String, Object> row = loadRowForUpdate(itemId);
        if (row == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }

        EquipmentMedia media = parseMedia(stringValue(row.get("photo_url")));
        List<Map<String, Object>> photos = mutablePhotos(media.photos());
        int targetIndex = findPhotoIndex(photos, normalizedId);
        if (targetIndex < 0) {
            return Map.of("success", false, "error", "Фото не найдено");
        }

        Map<String, Object> removed = photos.remove(targetIndex);
        boolean removedWasTitle = "title".equals(normalizeCategory(removed.get("category")));
        String promotedId = removedWasTitle ? promoteFallbackTitle(photos, null) : null;

        String encoded = serializeMedia(media.links(), photos);
        if (updateMedia(itemId, encoded) != 1) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        deleteStoredAfterCommit(stringValue(removed.get("stored_name")));
        return successPayload(encoded, parseMedia(encoded).photos(), promotedId);
    }

    @Transactional
    public Map<String, Object> updateLinksPreservingPhotos(long itemId, String incomingRaw) {
        Map<String, Object> row = loadRowForUpdate(itemId);
        if (row == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        EquipmentMedia existing = parseMedia(stringValue(row.get("photo_url")));
        EquipmentMedia incoming = parseMedia(incomingRaw);
        String encoded = serializeMedia(incoming.links(), existing.photos());
        if (updateMedia(itemId, encoded) != 1) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        return successPayload(encoded, parseMedia(encoded).photos(), null);
    }

    public ResponseEntity<Resource> downloadPhoto(String storedName) throws IOException {
        return photoStorageService.download(storedName);
    }

    public String mergeLinksPreservingPhotos(String existingRaw, String incomingRaw) {
        EquipmentMedia existing = parseMedia(existingRaw);
        EquipmentMedia incoming = parseMedia(incomingRaw);
        return serializeMedia(incoming.links(), existing.photos());
    }

    public void deleteStoredPhotos(String raw) {
        for (Map<String, Object> photo : parseMedia(raw).photos()) {
            photoStorageService.deleteQuietly(stringValue(photo.get("stored_name")));
        }
    }

    private Map<String, Object> validateMetadata(String category, String comment) {
        if (!StringUtils.hasText(category)) {
            return Map.of("success", false, "error", "Укажите тип фото: титульное или общее");
        }
        if (!StringUtils.hasText(comment)) {
            return Map.of("success", false, "error", "Комментарий к фото обязателен");
        }
        return null;
    }

    private Map<String, Object> titleReplacementRequired(Map<String, Object> existingTitle) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("requires_confirmation", true);
        response.put("error_code", "title_photo_exists");
        response.put("error", "У модели уже есть титульное фото. Подтвердите его замену.");
        String id = stringValue(existingTitle == null ? null : existingTitle.get("id"));
        String comment = stringValue(existingTitle == null ? null : existingTitle.get("comment"));
        if (StringUtils.hasText(id)) {
            response.put("existing_title_id", id);
        }
        if (StringUtils.hasText(comment)) {
            response.put("existing_title_comment", comment);
        }
        return response;
    }

    private Map<String, Object> successPayload(String encoded,
                                               List<Map<String, Object>> photos,
                                               String promotedPhotoId) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("photo_url", encoded);
        response.put("photos", List.copyOf(photos));
        if (StringUtils.hasText(promotedPhotoId)) {
            response.put("promoted_photo_id", promotedPhotoId);
        }
        return response;
    }

    private void demoteOtherTitles(List<Map<String, Object>> photos, String keepPhotoId) {
        for (int index = 0; index < photos.size(); index++) {
            Map<String, Object> current = photos.get(index);
            if (StringUtils.hasText(keepPhotoId) && keepPhotoId.equals(stringValue(current.get("id")))) {
                continue;
            }
            if (!"title".equals(normalizeCategory(current.get("category")))) {
                continue;
            }
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>(current);
            copy.put("category", "general");
            photos.set(index, copy);
        }
    }

    private Map<String, Object> findTitlePhoto(List<Map<String, Object>> photos, String excludedPhotoId) {
        for (Map<String, Object> photo : photos) {
            if (StringUtils.hasText(excludedPhotoId)
                    && excludedPhotoId.equals(stringValue(photo.get("id")))) {
                continue;
            }
            if ("title".equals(normalizeCategory(photo.get("category")))) {
                return photo;
            }
        }
        return null;
    }

    private int findPhotoIndex(List<Map<String, Object>> photos, String photoId) {
        for (int index = 0; index < photos.size(); index++) {
            if (photoId.equals(stringValue(photos.get(index).get("id")))) {
                return index;
            }
        }
        return -1;
    }

    private String promoteFallbackTitle(List<Map<String, Object>> photos, String excludedPhotoId) {
        if (photos.isEmpty() || findTitlePhoto(photos, null) != null) {
            return null;
        }
        for (int index = 0; index < photos.size(); index++) {
            Map<String, Object> current = photos.get(index);
            if (StringUtils.hasText(excludedPhotoId)
                    && excludedPhotoId.equals(stringValue(current.get("id")))) {
                continue;
            }
            LinkedHashMap<String, Object> promoted = new LinkedHashMap<>(current);
            promoted.put("category", "title");
            photos.set(index, promoted);
            return stringValue(promoted.get("id"));
        }
        return null;
    }

    private int updateMedia(long itemId, String encoded) {
        return jdbcTemplate.update(
                "UPDATE it_equipment_catalog SET photo_url = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                encoded,
                itemId
        );
    }

    private Map<String, Object> loadRowForUpdate(long itemId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, photo_url FROM it_equipment_catalog WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("photo_url", rs.getString("photo_url"));
                    return row;
                },
                itemId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void deleteStoredAfterCommit(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    photoStorageService.deleteQuietly(storedName);
                }
            });
            return;
        }
        photoStorageService.deleteQuietly(storedName);
    }

    private void deleteStoredOnRollback(String storedName) {
        if (!StringUtils.hasText(storedName) || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    photoStorageService.deleteQuietly(storedName);
                }
            }
        });
    }

    private EquipmentMedia parseMedia(String raw) {
        String value = stringValue(raw);
        if (!StringUtils.hasText(value)) {
            return new EquipmentMedia(List.of(), List.of());
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node != null && node.isArray()) {
                List<String> links = new ArrayList<>();
                node.forEach(entry -> {
                    if (entry.isTextual() && StringUtils.hasText(entry.asText())) {
                        links.add(entry.asText().trim());
                    }
                });
                return new EquipmentMedia(List.copyOf(links), List.of());
            }
            if (node != null && node.isObject()) {
                List<String> links = new ArrayList<>();
                JsonNode linksNode = node.get("links");
                if (linksNode != null && linksNode.isArray()) {
                    linksNode.forEach(entry -> {
                        if (entry.isTextual() && StringUtils.hasText(entry.asText())) {
                            links.add(entry.asText().trim());
                        }
                    });
                }
                List<Map<String, Object>> photos = new ArrayList<>();
                JsonNode photosNode = node.get("photos");
                if (photosNode != null && photosNode.isArray()) {
                    photosNode.forEach(entry -> {
                        if (!entry.isObject()) {
                            return;
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> converted = objectMapper.convertValue(entry, LinkedHashMap.class);
                        photos.add(normalizePhoto(converted));
                    });
                }
                return new EquipmentMedia(List.copyOf(links), List.copyOf(photos));
            }
        } catch (Exception ignored) {
            // Legacy newline-separated links remain supported below.
        }
        List<String> links = value.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return new EquipmentMedia(links, List.of());
    }

    private Map<String, Object> normalizePhoto(Map<String, Object> raw) {
        LinkedHashMap<String, Object> photo = new LinkedHashMap<>();
        if (raw != null) {
            photo.putAll(raw);
        }
        String category = normalizeCategory(photo.get("category"));
        photo.put("category", StringUtils.hasText(category) ? category : "general");
        String comment = stringValue(photo.getOrDefault("comment", photo.get("caption")));
        photo.put("comment", comment);
        photo.remove("caption");
        return photo;
    }

    private List<Map<String, Object>> mutablePhotos(List<Map<String, Object>> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (source != null) {
            source.forEach(photo -> result.add(new LinkedHashMap<>(photo)));
        }
        return result;
    }

    private List<Map<String, Object>> normalizeSingleTitle(List<Map<String, Object>> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        boolean titleSeen = false;
        if (source != null) {
            for (Map<String, Object> raw : source) {
                LinkedHashMap<String, Object> photo = new LinkedHashMap<>(normalizePhoto(raw));
                if ("title".equals(normalizeCategory(photo.get("category")))) {
                    if (titleSeen) {
                        photo.put("category", "general");
                    } else {
                        titleSeen = true;
                    }
                }
                result.add(photo);
            }
        }
        return result;
    }

    private String serializeMedia(List<String> links, List<Map<String, Object>> photos) {
        List<String> safeLinks = links == null ? List.of() : links.stream()
                .map(this::stringValue)
                .filter(StringUtils::hasText)
                .toList();
        List<Map<String, Object>> safePhotos = normalizeSingleTitle(photos);
        if (safeLinks.isEmpty() && safePhotos.isEmpty()) {
            return "";
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", MEDIA_VERSION);
        payload.put("links", safeLinks);
        payload.put("photos", safePhotos);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось сериализовать фото оборудования", ex);
        }
    }

    private String normalizeCategory(Object raw) {
        String value = stringValue(raw).toLowerCase(Locale.ROOT);
        if ("title".equals(value) || "титульное".equals(value)) {
            return "title";
        }
        if ("general".equals(value) || "общее".equals(value)) {
            return "general";
        }
        return "";
    }

    private String buildPhotoUrl(String storedName) {
        return "/api/settings/it-equipment/photos/file/" + stringValue(storedName);
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private record EquipmentMedia(List<String> links, List<Map<String, Object>> photos) {
    }
}
