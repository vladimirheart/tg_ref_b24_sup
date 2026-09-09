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
    void equipmentPhotoWorkspaceSupportsAcceptanceFixesAndRetainsR46Integrity() throws Exception {
        String template = read("src/main/resources/templates/settings/index.html");
        String runtime = read("src/main/resources/static/js/settings-it-equipment-runtime.js");
        String shell = read("src/main/resources/static/js/settings-page-shell.js");
        String scss = read("src/main/resources/scss/settings/_foundation.scss");
        String equipmentService = read("src/main/java/com/example/panel/service/SettingsItEquipmentService.java");
        String photoService = read("src/main/java/com/example/panel/service/SettingsItEquipmentPhotoService.java");

        assertThat(template)
                .contains("data-it-equipment-photo-tab")
                .contains("data-it-equipment-photo-count")
                .contains("data-it-equipment-photo-add-open")
                .contains("id=\"itEquipmentPhotoAddModal\"")
                .contains("id=\"itEquipmentPhotoViewerModal\"")
                .contains("id=\"itEquipmentPhotoEditModal\"")
                .contains("id=\"itEquipmentPhotoConfirmModal\"")
                .contains("data-settings-suspend-parent=\"itEquipmentAddModal\"")
                .contains("data-it-equipment-photo-info")
                .contains("data-it-equipment-photo-info-popover")
                .contains("aria-controls=\"itEquipmentPhotoInfoPopover\"")
                .contains("data-it-equipment-photo-add-file-open")
                .contains("data-it-equipment-photo-add-paste")
                .contains("data-it-equipment-photo-edit-file-open")
                .contains("data-it-equipment-photo-edit-paste")
                .contains("class=\"visually-hidden\" id=\"itEquipmentPhotoAddFile\"")
                .contains("class=\"visually-hidden\" id=\"itEquipmentPhotoEditFile\"")
                .contains("settings.css?v=20260909-01-259-r4-8")
                .contains("settings-it-equipment-runtime.js?v=20260909-01-259-r4-8")
                .contains("settings-page-shell.js?v=20260909-01-259-r4-6")
                .doesNotContain("<div class=\"small text-muted\">Фото сохраняются сразу. Нажмите на превью для просмотра.</div>");

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
                .contains("function handlePhotoPaste(")
                .contains("navigator.clipboard.read")
                .contains("function setPhotoInfoVisible(")
                .contains("window.URL.createObjectURL")
                .contains("window.URL.revokeObjectURL")
                .contains("previewPhotoEditFile(file)")
                .contains("selectedPhotoFile('add')")
                .contains("selectedPhotoFile('edit')")
                .contains("payload.photo_url = formatEquipmentLinksPayload(links)")
                .contains("JSON.stringify(links) !== JSON.stringify(existingLinks)")
                .doesNotContain("target=\"_blank\" rel=\"noopener\" aria-label=\"Открыть фото\"")
                .doesNotContain("confirmAction('Удалить это фото оборудования?')")
                .doesNotContain("? 'Фото сохраняются сразу. Для каждого фото обязательны тип и описание.'")
                .doesNotContain("window.bootstrap.Popover.getOrCreateInstance");

        assertThat(equipmentService)
                .contains("photoService.updateLinksPreservingPhotos(itemId, pendingLinks)")
                .doesNotContain("@Transactional\n    public Map<String, Object> updateItEquipment");

        assertThat(photoService)
                .contains("@Transactional\n    public Map<String, Object> updateLinksPreservingPhotos")
                .contains("FOR UPDATE");

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
                .contains("#itEquipmentPhotoViewerModal .it-equipment-photo-viewer-frame")
                .contains("/* Equipment photo UX polish — 01-259 r4.7 */")
                .contains("/* Equipment photo acceptance polish — 01-259 r4.8 */")
                .contains("it-equipment-photo-file-picker")
                .contains("it-equipment-photo-file-action")
                .contains("var(--surface-raised)")
                .contains("var(--color-text)")
                .contains("it-equipment-photo-info-popover");
    }
}
