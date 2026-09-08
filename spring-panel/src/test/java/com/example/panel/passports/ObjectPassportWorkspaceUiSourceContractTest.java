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
        assertTrue(html.contains("/object-passports/${passportId}/edit"));
        assertTrue(html.contains("/api/object_passports/${passportId}/cases"));
        assertTrue(html.contains("/api/object_passports/${passportId}/tasks"));
        assertTrue(html.contains("/api/object_passports/${passportId}/incidents"));
        assertTrue(html.contains("findCatalogItem"));
        assertTrue(html.contains("item && item.catalog_id"));
        assertTrue(html.contains("catalogKey("));
    }

    @Test
    void controllerSeparatesReadOnlyDetailFromExistingEditorAndExposesCatalogIds() throws IOException {
        String controller = read("src/main/java/com/example/panel/controller/ManagementController.java");
        assertTrue(controller.contains("return \"passports/detail\";"));
        assertTrue(controller.contains("@GetMapping(\"/object-passports/{id}/edit\")"));
        assertTrue(controller.contains("catalogItem.put(\"id\", item.getId())"));
        assertTrue(controller.contains("catalogItem.put(\"equipment_model\", item.getEquipmentModel())"));
    }

    @Test
    void settingsEquipmentCatalogUsesAssetCardsInsteadOfInlineEditorRows() throws IOException {
        String settings = read("src/main/resources/templates/settings/index.html");
        String runtime = read("src/main/resources/static/js/settings-it-equipment-runtime.js");
        String settingsScss = read("src/main/resources/scss/settings/_foundation.scss");
        assertTrue(settings.contains("itEquipmentSearchInput"));
        assertTrue(settings.contains("itEquipmentCountBadge"));
        assertTrue(settings.contains("it-equipment-catalog-grid"));
        assertTrue(runtime.contains("it-equipment-catalog-card"));
        assertTrue(runtime.contains("data-it-equipment-action=\"edit\""));
        assertTrue(runtime.contains("/api/settings/it-equipment/${editId}"));
        assertTrue(settingsScss.contains("it-equipment-catalog-card"));
    }

    @Test
    void passportScssKeepsCompactDenseWorkspaceContract() throws IOException {
        String scss = read("src/main/resources/scss/app/_passports.scss");
        assertTrue(scss.contains("Passport workspace — 01-259"));
        assertTrue(scss.contains("passport-overview-grid"));
        assertTrue(scss.contains("passport-property-grid"));
        assertTrue(scss.contains("passport-asset-card"));
        assertTrue(scss.contains("grid-template-columns: repeat(auto-fill, minmax(320px, 1fr))"));
    }
}
