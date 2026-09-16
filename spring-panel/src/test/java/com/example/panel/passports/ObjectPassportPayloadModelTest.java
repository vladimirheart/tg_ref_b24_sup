package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectPassportPayloadModelTest {

    private ObjectPassportPayloadModel model() {
        ObjectPassportPhotoModel photoModel = new ObjectPassportPhotoModel(
                storedName -> "/api/object_passports/photos/" + storedName);
        return new ObjectPassportPayloadModel(photoModel);
    }

    @Test
    void normalizesMergedPayloadListsIdAndPhotos() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("business", "БлинБери");
        existing.put("schedule", "legacy");
        existing.put("photos", List.of(Map.of(
                "id", "photo-1",
                "category", "title",
                "stored_name", "front.jpg")));
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("department", "Ленина 1");
        incoming.put("city", "Смоленск");
        incoming.put("tasks", "legacy");

        Map<String, Object> normalized = model().normalizePayload(existing, incoming, 42L);

        assertThat(normalized)
                .containsEntry("id", 42L)
                .containsEntry("is_new", false)
                .containsEntry("business", "БлинБери")
                .containsEntry("department", "Ленина 1")
                .containsEntry("city", "Смоленск");
        for (String key : List.of("schedule", "cases", "tasks", "network_files", "equipment", "status_history")) {
            assertThat(normalized.get(key)).isEqualTo(List.of());
        }
        List<?> photos = (List<?>) normalized.get("photos");
        assertThat(photos).hasSize(1);
        Map<?, ?> photo = (Map<?, ?>) photos.get(0);
        assertThat(photo.get("category")).isEqualTo("title");
        assertThat(photo.get("stored_name")).isEqualTo("front.jpg");
        assertThat(photo.get("url")).isEqualTo("/api/object_passports/photos/front.jpg");
    }

    @Test
    void validatesDepartmentAndPreservesNameAndDeletedStatusRules() {
        ObjectPassportPayloadModel model = model();
        assertThatThrownBy(() -> model.validatePayload(Map.of("department", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Поле «Департамент» обязательно для заполнения.");

        Map<String, Object> full = Map.of(
                "department", "Ленина 1",
                "city", "Смоленск",
                "business", "БлинБери");
        assertThat(model.buildObjectName(full)).isEqualTo("Смоленск - Ленина 1");
        assertThat(model.buildPassportNumber(full)).isEqualTo("Ленина 1");
        assertThat(model.buildObjectName(Map.of("business", "БлинБери", "city", "Смоленск")))
                .isEqualTo("БлинБери - Смоленск");
        assertThat(model.buildObjectName(Map.of())).isEqualTo("Паспорт объекта");
        assertThat(model.isDeletedStatus("Удалён")).isTrue();
        assertThat(model.isDeletedStatus("DELETED")).isTrue();
        assertThat(model.isDeletedStatus("Active")).isFalse();
    }
}
