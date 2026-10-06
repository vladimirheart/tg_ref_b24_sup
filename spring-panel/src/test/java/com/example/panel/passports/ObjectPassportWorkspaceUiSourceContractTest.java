package com.example.panel.passports;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectPassportWorkspaceUiSourceContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void detailUsesViewFirstWorkspaceAndDedicatedEditRoute() throws IOException {
        String html = read("src/main/resources/templates/passports/detail.html");
        String listHtml = read("src/main/resources/templates/passports/list.html");
        String edit = read("src/main/resources/templates/passports/fragments/detail-edit.html");
        String media = read("src/main/resources/templates/passports/fragments/detail-media.html");
        String equipmentMediaRuntime = read("src/main/resources/static/js/equipment-media-runtime.js");
        String coreRuntime = read("src/main/resources/static/js/passport-detail-core-runtime.js");
        String equipmentRuntime = read("src/main/resources/static/js/passport-detail-equipment-runtime.js");
        String editorRuntime = read("src/main/resources/static/js/passport-detail-editor-runtime.js");
        String pageRuntime = read("src/main/resources/static/js/passport-detail-page-runtime.js");
        String equipment = read("src/main/resources/templates/passports/fragments/detail-equipment.html");
        assertTrue(html.contains("passport-workspace-tabs"));
        assertTrue(html.contains("passport-kpi-strip"));
        assertTrue(html.contains("passports/fragments/detail-equipment :: equipmentPanel"));
        assertTrue(equipment.contains("passport-equipment-grid"));
        assertTrue(equipmentRuntime.contains("passport-asset-card"));
        assertTrue(html.contains("passports/fragments/detail-edit :: passportEditLayer"));
        assertTrue(edit.contains("passport-edit-layer"));
        assertTrue(html.contains("initialEditMode"));
        assertTrue(html.contains("passports/fragments/detail-media :: photosPanel"));
        assertTrue(html.contains("passports/fragments/detail-media :: photoViewer"));
        assertTrue(media.contains("data-passport-panel=\"photos\""));
        assertTrue(media.contains("passport-photo-viewer"));
        assertTrue(html.contains("data-passport-tab-target=\"photos\""));
        assertTrue(pageRuntime.contains("/api/object_passports/${passportId}/cases"));
        assertTrue(pageRuntime.contains("/api/object_passports/${passportId}/tasks"));
        assertTrue(pageRuntime.contains("/api/object_passports/${passportId}/incidents"));
        assertTrue(html.contains("/js/equipment-media-runtime.js?v=20260922-01-272-s7"));
        assertTrue(html.contains("/js/passport-detail-core-runtime.js?v=20260922-01-272-s7"));
        assertTrue(equipmentMediaRuntime.contains("window.EquipmentMediaRuntime"));
        assertTrue(equipmentMediaRuntime.contains("function coverUrl(raw)"));
        assertTrue(pageRuntime.contains("PassportDetailCoreRuntime"));
        assertTrue(html.contains("/js/passport-detail-equipment-runtime.js?v=20261005-01-280-r43"));
        assertTrue(html.contains("passport-ui-polish.css(v='20261005-01-280-r43')"));
        assertTrue(listHtml.contains("passport-ui-polish.css(v='20261005-01-280-r43')"));
        assertTrue(pageRuntime.contains("PassportDetailEquipmentRuntime"));
        assertTrue(coreRuntime.contains("equipmentMediaRuntime.coverUrl"));
        assertTrue(equipmentRuntime.contains("passport-asset-card__visual-placeholder"));
        assertTrue(equipmentRuntime.contains("passport-asset-card__type-center"));
        assertTrue(equipmentRuntime.contains("data-equipment-details-toggle"));
        assertTrue(equipmentRuntime.contains("data-equipment-profile-name"));
        assertTrue(equipmentRuntime.contains("Профиль комплектации"));
        assertTrue(equipmentRuntime.contains("data-equipment-group-toggle"));
        assertTrue(equipmentRuntime.contains("expandedEquipmentTypeKeys"));
        assertTrue(equipmentRuntime.contains("const expandForSearch = Boolean(query)"));
        assertTrue(!equipmentRuntime.contains("media.photos.find"));
        assertTrue(html.contains("/js/passport-detail-editor-runtime.js?v=20261006-modern-create-r12"));
        assertTrue(html.contains("/js/passport-detail-header-runtime.js?v=20261006-legacy-editor-removal-r1"));
        assertTrue(html.contains("/js/passport-detail-page-runtime.js?v=20261006-modern-create-r12"));
        assertTrue(html.contains("PassportDetailPageRuntime"));
        assertTrue(pageRuntime.contains("PassportDetailEditorRuntime"));
        assertTrue(coreRuntime.contains("findCatalogItem"));
        assertTrue(coreRuntime.contains("item && item.catalog_id"));
        assertTrue(coreRuntime.contains("catalogKey("));
        assertTrue(editorRuntime.contains("data-equipment-restore"));
        assertTrue(editorRuntime.contains("archived_at"));
        assertTrue(editorRuntime.contains("перестанет блокировать удаление модели из каталога"));
    }

    @Test
    void creationUsesModernWorkspaceAndLegacyCreationArtifactsAreGone() throws IOException {
        String html = read("src/main/resources/templates/passports/detail.html");
        String pageRuntime = read("src/main/resources/static/js/passport-detail-page-runtime.js");
        String editorRuntime = read("src/main/resources/static/js/passport-detail-editor-runtime.js");
        String controller = read("src/main/java/com/example/panel/passports/api/ObjectPassportPageController.java");
        assertTrue(html.contains("initialCreateMode"));
        assertTrue(pageRuntime.contains("const initialCreateMode = options.initialCreateMode === true"));
        assertTrue(pageRuntime.contains("createMode: initialCreateMode"));
        assertTrue(pageRuntime.contains("function initializeCreateMode()"));
        assertTrue(editorRuntime.contains("const createMode = options.createMode === true"));
        assertTrue(editorRuntime.contains("const saveMethod = createMode ? 'POST' : 'PUT'"));
        assertTrue(editorRuntime.contains("window.location.assign"));
        assertTrue(editorRuntime.contains("/object-passports/${createdId}/edit"));
        assertTrue(controller.contains("model.addAttribute(\"passportCreateMode\", true)"));
        assertFalse(controller.contains("return \"passports/new\";"));
        assertFalse(Files.exists(Path.of("src/main/resources/templates/passports/new.html")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/passport-editor-page-runtime.js")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/passport-editor-equipment-runtime.js")));
        assertFalse(Files.exists(Path.of("src/main/resources/templates/passports/fragments/editor-basic-info.html")));
        assertFalse(Files.exists(Path.of("src/main/resources/templates/passports/fragments/editor-network.html")));
        assertFalse(Files.exists(Path.of("src/main/resources/templates/passports/fragments/editor-activity.html")));
        assertFalse(Files.exists(Path.of("src/main/resources/templates/passports/fragments/editor-media.html")));
        assertFalse(Files.exists(Path.of("src/main/resources/templates/passports/fragments/editor-equipment.html")));
    }

    @Test
    void controllerSeparatesReadOnlyDetailFromExistingEditorAndExposesCatalogIds() throws IOException {
        String controller = read("src/main/java/com/example/panel/passports/api/ObjectPassportPageController.java");
        String edit = read("src/main/resources/templates/passports/fragments/detail-edit.html");
        String headerRuntime = read("src/main/resources/static/js/passport-detail-header-runtime.js");
        String uiPreferences = read("src/main/resources/static/js/ui-preferences.js");
        String pageModel = read("src/main/java/com/example/panel/passports/api/ObjectPassportPageModelAssembler.java");
        String managementController = read("src/main/java/com/example/panel/controller/ManagementController.java");
        assertTrue(controller.contains("return \"passports/detail\";"));
        assertTrue(controller.contains("@GetMapping(\"/object-passports/{id}/edit\")"));
        assertTrue(controller.contains("@GetMapping(\"/object-passports/new\")"));
        assertFalse(controller.contains("@GetMapping(\"/object-passports/{id}/legacy-edit\")"));
        assertFalse(controller.contains("passportLegacyEdit("));
        assertFalse(edit.contains("passportLegacyEditLink"));
        assertFalse(edit.contains("Legacy-редактор"));
        assertFalse(headerRuntime.contains("passportLegacyEditLink"));
        assertFalse(headerRuntime.contains("/legacy-edit"));
        assertFalse(uiPreferences.contains("legacy-edit"));
        assertTrue(controller.contains("model.addAttribute(\"passportEditMode\", true)"));
        assertTrue(pageModel.contains("catalogItem.put(\"id\", item.getId())"));
        assertTrue(pageModel.contains("catalogItem.put(\"equipment_model\", item.getEquipmentModel())"));
        assertTrue(!managementController.contains("@GetMapping(\"/object-passports"));
    }

    @Test
    void settingsEquipmentCatalogUsesAssetCardsInsteadOfInlineEditorRows() throws IOException {
        String settings = read("src/main/resources/templates/settings/index.html");
        String equipment = read("src/main/resources/templates/settings/fragments/it-equipment.html");
        String runtime = read("src/main/resources/static/js/settings-it-equipment-runtime.js");
        String settingsCatalogueScss = read("src/main/resources/scss/settings/foundation/_catalogue.scss");
        assertTrue(equipment.contains("itEquipmentSearchInput"));
        assertTrue(equipment.contains("itEquipmentTypeFilter"));
        assertTrue(equipment.contains("itEquipmentVendorFilter"));
        assertTrue(equipment.contains("itEquipmentCountBadge"));
        assertTrue(equipment.contains("it-equipment-catalog-grid"));
        assertTrue(runtime.contains("it-equipment-catalog-card"));
        assertTrue(runtime.contains("it-equipment-catalog-card__type-center"));
        assertTrue(runtime.contains("Используется у объектов"));
        assertTrue(runtime.contains("item.object_count ?? item.usage_count"));
        assertTrue(runtime.contains("bi bi-pencil"));
        assertTrue(runtime.contains("bi bi-trash"));
        assertTrue(runtime.contains("data-it-equipment-action=\"edit\""));
        assertTrue(runtime.contains("data-it-equipment-action=\"promote\""));
        assertTrue(runtime.contains("Из паспортов"));
        assertTrue(runtime.contains("/api/settings/it-equipment/${editId}"));
        assertTrue(runtime.contains("if (state.editingId !== null)"));
        assertTrue(equipment.contains("data-it-equipment-photo-list"));
        assertTrue(equipment.contains("data-it-equipment-photo-add-comment"));
        assertTrue(runtime.contains("/api/settings/it-equipment/${id}/photos"));
        assertTrue(runtime.contains("data-it-equipment-photo-delete"));
        assertTrue(runtime.contains("firstPhoto(item)"));
        assertTrue(settings.contains("/css/settings.css?v=20260909-01-259-r4-8"));
        assertTrue(settings.contains("/js/equipment-media-runtime.js?v=20260922-01-272-s7"));
        assertTrue(settings.contains("/js/settings-it-equipment-runtime.js?v=20260922-01-272-s7"));
        assertTrue(settingsCatalogueScss.contains("Equipment catalogue media and lifecycle — 01-259 r4.4"));
    }

    @Test
    void passportScssKeepsCompactDenseWorkspaceContract() throws IOException {
        String workspaceScss = read("src/main/resources/scss/app/passports/_workspace.scss");
        String workspaceInteractionsScss = read("src/main/resources/scss/app/passports/_workspace-interactions.scss");
        String listPolishScss = read("src/main/resources/scss/app/passports/_list-polish.scss");
        String equipmentHistoryScss = read("src/main/resources/scss/app/passports/_equipment-history.scss");
        String passportPolishCss = read("src/main/resources/static/css/passport-ui-polish.css");
        assertTrue(workspaceScss.contains("Passport workspace — 01-259"));
        assertTrue(workspaceScss.contains("passport-overview-grid"));
        assertTrue(workspaceScss.contains("passport-property-grid"));
        assertTrue(workspaceScss.contains("passport-asset-card"));
        assertTrue(workspaceScss.contains("grid-template-columns: repeat(auto-fill, minmax(320px, 1fr))"));
        assertTrue(listPolishScss.contains("Catalogue polish — 01-258"));
        assertTrue(workspaceInteractionsScss.contains("Workspace refinements — 01-259 r4"));
        assertTrue(workspaceInteractionsScss.contains("passport-photo-viewer"));
        assertTrue(workspaceInteractionsScss.contains("passport-edit-drawer"));
        assertTrue(equipmentHistoryScss.contains("Equipment archive history — 01-259 r4.4"));
        assertTrue(passportPolishCss.contains("grid-template-columns: 118px minmax(0, 1fr)"));
        assertTrue(passportPolishCss.contains("passport-asset-card__quick-line"));
        assertTrue(passportPolishCss.contains("passport-equipment-group__toggle"));
        assertTrue(passportPolishCss.contains("passport-equipment-group__body[hidden]"));
    }

    @Test
    void uiPreferencesExposeServerBackedPerPageFontScale() throws IOException {
        String runtime = read("src/main/resources/static/js/ui-preferences.js");
        String service = read("src/main/java/com/example/panel/service/UiPreferenceService.java");
        String sidebarScss = read("src/main/resources/scss/sidebar/_sections.scss");
        assertTrue(runtime.contains("pageFontScales"));
        assertTrue(runtime.contains("currentPageFontKey"));
        assertTrue(runtime.contains("defaultPageFontScale"));
        assertTrue(runtime.contains("key.startsWith('/object-passports/')"));
        assertTrue(runtime.contains("return 130"));
        assertTrue(runtime.contains("140, 150"));
        assertTrue(runtime.contains("source === 'page-font-scale'"));
        assertTrue(runtime.contains("PENDING_PAGE_FONT_SYNC_STORAGE_KEY"));
        assertTrue(runtime.contains("page-font-scale-recovery"));
        assertTrue(runtime.contains("syncInFlight"));
        assertTrue(runtime.contains("response.ok"));
        assertTrue(runtime.contains("meta[name=\"_csrf_header\"]"));
        assertTrue(service.contains("130, 140, 150"));
        assertTrue(runtime.contains("data-page-font-scale-control"));
        assertTrue(service.contains("pageFontScales"));
        assertTrue(sidebarScss.contains("sidebar-font-scale-control"));
    }
}
