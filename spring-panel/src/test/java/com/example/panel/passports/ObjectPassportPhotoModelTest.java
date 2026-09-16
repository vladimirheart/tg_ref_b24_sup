package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectPassportPhotoModelTest {

    @Test
    void normalizesLegacyFieldsAndKeepsExactlyOneTitlePhoto() {
        ObjectPassportPhotoModel model = new ObjectPassportPhotoModel(storedName -> "/api/object_passports/photos/" + storedName);

        List<Map<String, Object>> normalized = model.normalizePhotos(List.of(
                Map.of(
                        "id", "photo-1",
                        "category", "title",
                        "caption", "Вход",
                        "storedName", "front.jpg"
                ),
                Map.of(
                        "id", "photo-2",
                        "category", "title",
                        "url", "/photos/second.jpg"
                )
        ));

        assertThat(normalized).hasSize(2);
        assertThat(normalized.get(0))
                .containsEntry("category", "title")
                .containsEntry("stored_name", "front.jpg")
                .containsEntry("url", "/api/object_passports/photos/front.jpg");
        assertThat(normalized.get(1)).containsEntry("category", "archive");
        assertThat(model.findTitlePhotoUrl(normalized)).isEqualTo("/api/object_passports/photos/front.jpg");

        List<Map<String, Object>> preferred = model.enforceSingleTitlePhoto(List.of(
                Map.<String, Object>of("id", "photo-1", "category", "title", "url", "/photos/first.jpg"),
                Map.<String, Object>of("id", "photo-2", "category", "title", "url", "/photos/second.jpg")
        ), "photo-2");
        assertThat(preferred.get(0)).containsEntry("category", "archive");
        assertThat(preferred.get(1)).containsEntry("category", "title");
        assertThat(model.findTitlePhotoUrl(preferred)).isEqualTo("/photos/second.jpg");
    }

    @Test
    void mutableProjectionDoesNotMutateNormalizedInputMaps() {
        ObjectPassportPhotoModel model = new ObjectPassportPhotoModel(storedName -> storedName);
        List<Map<String, Object>> normalized = model.normalizePhotos(List.of(
                Map.of("id", "photo-1", "category", "archive", "caption", "До")
        ));

        List<Map<String, Object>> mutable = model.mutablePhotoList(normalized);
        mutable.get(0).put("caption", "После");

        assertThat(normalized.get(0)).containsEntry("caption", "До");
        assertThat(mutable.get(0)).containsEntry("caption", "После");
    }
}
