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
    void equipmentPhotoWorkspaceSupportsLargerPreviewsMiniAddFullEditAndStackSafeEscape() throws Exception {
        String template = read("src/main/resources/templates/settings/index.html");
        String runtime = read("src/main/resources/static/js/settings-it-equipment-runtime.js");
        String shell = read("src/main/resources/static/js/settings-page-shell.js");
        String scss = read("src/main/resources/scss/settings/_foundation.scss");

        assertThat(template)
                .contains("data-it-equipment-photo-tab")
                .contains("data-it-equipment-photo-count")
                .contains("data-it-equipment-photo-add-open")
                .contains("id=\"itEquipmentPhotoAddModal\"")
                .contains("id=\"itEquipmentPhotoViewerModal\"")
                .contains("id=\"itEquipmentPhotoEditModal\"")
                .contains("data-it-equipment-photo-edit-file")
                .contains("id=\"itEquipmentPhotoConfirmModal\"")
                .contains("data-settings-suspend-parent=\"itEquipmentAddModal\"")
                .contains("settings.css?v=20260909-01-259-r4-6")
                .contains("settings-it-equipment-runtime.js?v=20260909-01-259-r4-6")
                .contains("settings-page-shell.js?v=20260909-01-259-r4-6");

        assertThat(runtime)
                .contains("function openPhotoAddModal()")
                .contains("data-it-equipment-photo-preview")
                .contains("data-it-equipment-photo-edit")
                .contains("showPhotoConfirm")
                .contains("confirmTitleReplacement")
                .contains("replace_title")
                .contains("async function replaceEquipmentPhoto(")
                .contains("/replace")
                .contains("it-equipment-photo-thumb__comment")
                .doesNotContain("target=\"_blank\" rel=\"noopener\" aria-label=\"Открыть фото\"")
                .doesNotContain("confirmAction('Удалить это фото оборудования?')");

        assertThat(shell)
                .contains("function getSettingsTopVisibleModal()")
                .contains("function initSettingsTopModalEscapeGuard()")
                .contains("event.stopImmediatePropagation()")
                .contains("hideSettingsModal(topModal)")
                .contains("}, true);");

        assertThat(scss)
                .contains("/* Equipment photo workspace — 01-259 r4.6 */")
                .contains("grid-template-columns: 120px minmax(0, 1fr) auto;")
                .contains("width: 120px;")
                .contains("height: 90px;")
                .contains("#itEquipmentPhotoEditModal .it-equipment-photo-edit-preview")
                .contains("#itEquipmentPhotoViewerModal .it-equipment-photo-viewer-frame");
    }
}
