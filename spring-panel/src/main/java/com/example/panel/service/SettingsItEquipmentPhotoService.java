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

    public Map<String, Object> uploadPhoto(long itemId,
                                           MultipartFile file,
                                           String category,
                                           String comment) throws IOException {
        String normalizedCategory = normalizeCategory(category);
        String normalizedComment = stringValue(comment);
        if (!StringUtils.hasText(normalizedCategory)) {
            return Map.of("success", false, "error", "Укажите тип фото: титульное или общее");
        }
        if (!StringUtils.hasText(normalizedComment)) {
            return Map.of("success", false, "error", "Комментарий к фото обязателен");
        }

        Map<String, Object> row = loadRow(itemId);
        if (row == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }

        StoredPhoto stored = photoStorageService.store(file);
        try {
            EquipmentMedia media = parseMedia(stringValue(row.get("photo_url")));
            List<Map<String, Object>> photos = mutablePhotos(media.photos());
            if ("title".equals(normalizedCategory)) {
                for (int index = 0; index < photos.size(); index++) {
                    LinkedHashMap<String, Object> copy = new LinkedHashMap<>(photos.get(index));
                    if ("title".equals(normalizeCategory(copy.get("category")))) {
                        copy.put("category", "general");
                    }
                    photos.set(index, copy);
                }
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
            int updated = jdbcTemplate.update(
                    "UPDATE it_equipment_catalog SET photo_url = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    encoded,
                    itemId
            );
            if (updated != 1) {
                photoStorageService.deleteQuietly(stored.storedName());
                return Map.of("success", false, "error", "Оборудование не найдено");
            }
            return Map.of(
                    "success", true,
                    "photo_url", encoded,
                    "photos", List.copyOf(photos)
            );
        } catch (RuntimeException ex) {
            photoStorageService.deleteQuietly(stored.storedName());
            throw ex;
        }
    }

    public Map<String, Object> deletePhoto(long itemId, String photoId) {
        String normalizedId = stringValue(photoId);
        if (!StringUtils.hasText(normalizedId)) {
            return Map.of("success", false, "error", "Фото не найдено");
        }
        Map<String, Object> row = loadRow(itemId);
        if (row == null) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }

        EquipmentMedia media = parseMedia(stringValue(row.get("photo_url")));
        List<Map<String, Object>> photos = mutablePhotos(media.photos());
        Map<String, Object> removed = null;
        for (int index = 0; index < photos.size(); index++) {
            if (normalizedId.equals(stringValue(photos.get(index).get("id")))) {
                removed = photos.remove(index);
                break;
            }
        }
        if (removed == null) {
            return Map.of("success", false, "error", "Фото не найдено");
        }

        String encoded = serializeMedia(media.links(), photos);
        int updated = jdbcTemplate.update(
                "UPDATE it_equipment_catalog SET photo_url = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                encoded,
                itemId
        );
        if (updated != 1) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        photoStorageService.deleteQuietly(stringValue(removed.get("stored_name")));
        return Map.of(
                "success", true,
                "photo_url", encoded,
                "photos", List.copyOf(photos)
        );
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

    private Map<String, Object> loadRow(long itemId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, photo_url FROM it_equipment_catalog WHERE id = ?",
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

    private String serializeMedia(List<String> links, List<Map<String, Object>> photos) {
        List<String> safeLinks = links == null ? List.of() : links.stream()
                .map(this::stringValue)
                .filter(StringUtils::hasText)
                .toList();
        List<Map<String, Object>> safePhotos = photos == null ? List.of() : photos.stream()
                .map(this::normalizePhoto)
                .toList();
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
