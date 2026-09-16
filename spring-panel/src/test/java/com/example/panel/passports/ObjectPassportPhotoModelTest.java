package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    @Test
    void updatesAndDeletesPhotosWithExistingMutationSemantics() {
        ObjectPassportPhotoModel model = new ObjectPassportPhotoModel(
                storedName -> "/api/object_passports/photos/" + storedName);
        List<Map<String, Object>> photos = List.of(
                Map.of(
                        "id", "photo-1",
                        "category", "title",
                        "caption", "Первое",
                        "stored_name", "first.jpg"),
                Map.of(
                        "id", "photo-2",
                        "category", "archive",
                        "caption", "Второе",
                        "stored_name", "second.jpg"));

        List<Map<String, Object>> updated = model.updatePhoto(
                photos,
                "  photo-2  ",
                Map.of("caption", "  Новый вид  ", "category", "title"));

        assertThat(updated).hasSize(2);
        assertThat(updated.get(0)).containsEntry("category", "archive");
        assertThat(updated.get(1))
                .containsEntry("category", "title")
                .containsEntry("caption", "Новый вид");

        ObjectPassportPhotoModel.PhotoDeleteResult deletion = model.deletePhoto(updated, "photo-2");
        assertThat(deletion.storedName()).isEqualTo("second.jpg");
        assertThat(deletion.photos()).hasSize(1);
        assertThat(deletion.photos().get(0))
                .containsEntry("id", "photo-1")
                .containsEntry("category", "archive");

        assertPhotoNotFound(() -> model.updatePhoto(photos, "missing", Map.of("caption", "x")));
        assertPhotoNotFound(() -> model.deletePhoto(photos, "missing"));
    }

    private void assertPhotoNotFound(Runnable mutation) {
        assertThatThrownBy(mutation::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

}
