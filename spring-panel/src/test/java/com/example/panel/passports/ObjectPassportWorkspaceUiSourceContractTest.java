package com.example.panel.passports;

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
        assertTrue(html.contains("passport-workspace-tabs"));
        assertTrue(html.contains("passport-kpi-strip"));
        assertTrue(html.contains("passport-equipment-grid"));
        assertTrue(html.contains("passport-asset-card"));
        assertTrue(html.contains("passport-edit-layer"));
        assertTrue(html.contains("initialEditMode"));
        assertTrue(html.contains("passport-photo-viewer"));
        assertTrue(html.contains("data-passport-tab-target=\"photos\""));
        assertTrue(html.contains("/api/object_passports/${passportId}/cases"));
        assertTrue(html.contains("/api/object_passports/${passportId}/tasks"));
        assertTrue(html.contains("/api/object_passports/${passportId}/incidents"));
        assertTrue(html.contains("findCatalogItem"));
        assertTrue(html.contains("item && item.catalog_id"));
        assertTrue(html.contains("catalogKey("));
        assertTrue(html.contains("data-equipment-restore"));
        assertTrue(html.contains("archived_at"));
        assertTrue(html.contains("перестанет блокировать удаление модели из каталога"));
    }

    @Test
    void controllerSeparatesReadOnlyDetailFromExistingEditorAndExposesCatalogIds() throws IOException {
        String controller = read("src/main/java/com/example/panel/controller/ManagementController.java");
        assertTrue(controller.contains("return \"passports/detail\";"));
        assertTrue(controller.contains("@GetMapping(\"/object-passports/{id}/edit\")"));
        assertTrue(controller.contains("@GetMapping(\"/object-passports/{id}/legacy-edit\")"));
        assertTrue(controller.contains("model.addAttribute(\"passportEditMode\", true)"));
        assertTrue(controller.contains("catalogItem.put(\"id\", item.getId())"));
        assertTrue(controller.contains("catalogItem.put(\"equipment_model\", item.getEquipmentModel())"));
    }

    @Test
    void settingsEquipmentCatalogUsesAssetCardsInsteadOfInlineEditorRows() throws IOException {
        String settings = read("src/main/resources/templates/settings/index.html");
        String runtime = read("src/main/resources/static/js/settings-it-equipment-runtime.js");
        String settingsScss = read("src/main/resources/scss/settings/_foundation.scss");
        assertTrue(settings.contains("itEquipmentSearchInput"));
        assertTrue(settings.contains("itEquipmentTypeFilter"));
        assertTrue(settings.contains("itEquipmentVendorFilter"));
        assertTrue(settings.contains("itEquipmentCountBadge"));
        assertTrue(settings.contains("it-equipment-catalog-grid"));
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
        assertTrue(settings.contains("data-it-equipment-photo-list"));
        assertTrue(settings.contains("data-it-equipment-photo-add-comment"));
        assertTrue(runtime.contains("/api/settings/it-equipment/${id}/photos"));
        assertTrue(runtime.contains("data-it-equipment-photo-delete"));
        assertTrue(runtime.contains("firstPhoto(item)"));
        assertTrue(settings.contains("/css/settings.css?v=20260909-01-259-r4-7"));
        assertTrue(settings.contains("/js/settings-it-equipment-runtime.js?v=20260909-01-259-r4-7"));
        assertTrue(settingsScss.contains("Equipment catalogue media and lifecycle — 01-259 r4.4"));
    }

    @Test
    void passportScssKeepsCompactDenseWorkspaceContract() throws IOException {
        String scss = read("src/main/resources/scss/app/_passports.scss");
        assertTrue(scss.contains("Passport workspace — 01-259"));
        assertTrue(scss.contains("passport-overview-grid"));
        assertTrue(scss.contains("passport-property-grid"));
        assertTrue(scss.contains("passport-asset-card"));
        assertTrue(scss.contains("grid-template-columns: repeat(auto-fill, minmax(320px, 1fr))"));
        assertTrue(scss.contains("Catalogue polish — 01-258"));
        assertTrue(scss.contains("Workspace refinements — 01-259 r4"));
        assertTrue(scss.contains("passport-photo-viewer"));
        assertTrue(scss.contains("passport-edit-drawer"));
        assertTrue(scss.contains("Equipment archive history — 01-259 r4.4"));
    }

    @Test
    void uiPreferencesExposeServerBackedPerPageFontScale() throws IOException {
        String runtime = read("src/main/resources/static/js/ui-preferences.js");
        String service = read("src/main/java/com/example/panel/service/UiPreferenceService.java");
        String sidebarScss = read("src/main/resources/scss/sidebar/_sections.scss");
        assertTrue(runtime.contains("pageFontScales"));
        assertTrue(runtime.contains("currentPageFontKey"));
        assertTrue(runtime.contains("data-page-font-scale-control"));
        assertTrue(service.contains("pageFontScales"));
        assertTrue(sidebarScss.contains("sidebar-font-scale-control"));
    }
}
