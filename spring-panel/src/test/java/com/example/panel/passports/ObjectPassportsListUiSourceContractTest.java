package com.example.panel.passports;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectPassportsListUiSourceContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void listTemplateKeepsCompactActionsPhotoPreviewAndSoftDeleteVisibilityContract() throws IOException {
        String html = read("src/main/resources/templates/passports/list.html");
        assertTrue(html.contains("passport-icon-action"));
        assertTrue(html.contains("title=\"Фильтры\""));
        assertTrue(html.contains("aria-label=\"Создать паспорт\""));
        assertTrue(html.contains("data-passport-title-photo"));
        assertTrue(html.contains("passportPhotoPreview"));
        assertTrue(html.contains("}, 500);"));
        assertTrue(html.contains("thumb.addEventListener('mouseleave', schedulePhotoPreviewHide)"));
        assertTrue(html.contains("photoPreview.addEventListener('mouseenter', cancelPhotoPreviewHide)"));
        assertTrue(html.contains("photoPreview.addEventListener('mouseleave', hidePhotoPreview)"));
        assertTrue(html.contains("}, 300);"));
        assertTrue(html.contains("showDeletedFilter"));
        assertTrue(html.contains("data-deleted"));
        assertTrue(html.contains("visibilityRows.length"));
    }

    @Test
    void scssKeepsReadableAppealBadgeThumbnailPreviewAndDeletedRowContract() throws IOException {
        String scss = read("src/main/resources/scss/app/_passports.scss");
        assertTrue(scss.contains("passport-title-thumb"));
        assertTrue(scss.contains("passport-photo-preview"));
        assertTrue(scss.contains("pointer-events: auto"));
        assertTrue(scss.contains("font-size: 0.8125rem"));
        assertTrue(scss.contains("font-weight: 800"));
        assertTrue(scss.contains("font-variant-numeric: tabular-nums"));
        assertTrue(scss.contains("passport-row--deleted"));
    }

    @Test
    void backendExposesDeletedAndTitlePhotoFieldsAndDeletedStatusOption() throws IOException {
        String service = read("src/main/java/com/example/panel/service/ObjectPassportService.java");
        String controller = read("src/main/java/com/example/panel/controller/ManagementController.java");
        assertTrue(service.contains("item.put(\"title_photo_url\""));
        assertTrue(service.contains("item.put(\"deleted\""));
        assertTrue(service.contains("\"удален\".equals(status) || \"deleted\".equals(status)"));
        assertTrue(controller.contains("\"Удалён\""));
    }
}
