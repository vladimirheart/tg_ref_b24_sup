package com.example.panel.passports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.util.StringUtils;

final class ObjectPassportPhotoModel {

    private final Function<String, String> photoUrlBuilder;

    ObjectPassportPhotoModel(Function<String, String> photoUrlBuilder) {
        this.photoUrlBuilder = photoUrlBuilder;
    }

    List<Map<String, Object>> normalizePhotos(Object value) {
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
            String source = stringValue(rawMap.get("source"));
            if (StringUtils.hasText(source)) {
                photo.put("source", source);
            }
            String externalId = firstNonBlank(rawMap.get("external_id"), rawMap.get("externalId"));
            if (StringUtils.hasText(externalId)) {
                photo.put("external_id", externalId);
            }
            String sourceUrl = firstNonBlank(rawMap.get("source_url"), rawMap.get("sourceUrl"));
            if (StringUtils.hasText(sourceUrl)) {
                photo.put("source_url", sourceUrl);
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
                url = photoUrlBuilder.apply(storedName);
            }
            if (StringUtils.hasText(url)) {
                photo.put("url", url);
            }
            photos.add(photo);
        }
        return enforceSingleTitlePhoto(photos, null);
    }

    List<Map<String, Object>> mutablePhotoList(Object value) {
        List<Map<String, Object>> normalized = normalizePhotos(value);
        List<Map<String, Object>> mutable = new ArrayList<>();
        for (Map<String, Object> photo : normalized) {
            mutable.add(new LinkedHashMap<>(photo));
        }
        return mutable;
    }

    List<Map<String, Object>> enforceSingleTitlePhoto(List<Map<String, Object>> photos, String preferredTitlePhotoId) {
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

    String normalizePhotoCategory(Object raw) {
        String value = stringValue(raw).toLowerCase();
        return "title".equals(value) ? "title" : "archive";
    }

    String findTitlePhotoUrl(List<Map<String, Object>> photos) {
        if (photos == null) {
            return "";
        }
        for (Map<String, Object> photo : photos) {
            if (!"title".equals(normalizePhotoCategory(photo.get("category")))) {
                continue;
            }
            String url = stringValue(photo.get("url"));
            if (StringUtils.hasText(url)) {
                return url;
            }
        }
        return "";
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

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

}
