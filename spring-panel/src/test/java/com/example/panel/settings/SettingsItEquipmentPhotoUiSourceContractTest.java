package com.example.panel.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettingsItEquipmentPhotoUiSourceContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void equipmentPhotosUseDedicatedTabCompactThumbnailsAndManagedModals() throws Exception {
        String template = read("src/main/resources/templates/settings/index.html");
        String runtime = read("src/main/resources/static/js/settings-it-equipment-runtime.js");
        String scss = read("src/main/resources/scss/settings/_foundation.scss");

        assertThat(template)
                .contains("data-it-equipment-photo-tab")
                .contains("data-it-equipment-photo-count")
                .contains("id=\"itEquipmentPhotoViewerModal\"")
                .contains("id=\"itEquipmentPhotoEditModal\"")
                .contains("id=\"itEquipmentPhotoConfirmModal\"")
                .contains("settings.css?v=20260908-01-259-r4-5")
                .contains("settings-it-equipment-runtime.js?v=20260908-01-259-r4-5");

        assertThat(runtime)
                .contains("data-it-equipment-photo-preview")
                .contains("data-it-equipment-photo-edit")
                .contains("showPhotoConfirm")
                .contains("confirmTitleReplacement")
                .contains("replace_title")
                .contains("async function saveEquipmentPhotoEdit()")
                .contains("it-equipment-photo-thumb__comment")
                .doesNotContain("target=\"_blank\" rel=\"noopener\" aria-label=\"Открыть фото\"")
                .doesNotContain("confirmAction('Удалить это фото оборудования?')");

        assertThat(scss)
                .contains("/* Equipment photo workspace — 01-259 r4.5 */")
                .contains("#itEquipmentAddModal .it-equipment-photo-thumb__preview")
                .contains("width: 25px;")
                .contains("height: 25px;")
                .contains("#itEquipmentPhotoViewerModal .it-equipment-photo-viewer-frame")
                .doesNotContain("#itEquipmentSection .it-equipment-photo-thumb {");
    }
}
