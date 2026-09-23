package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectPassportEquipmentEditorUiSourceContractTest {

    private String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void detailEditorUsesCookieCsrfHeaderAndCompactCatalogAwareEquipmentCards() throws Exception {
        String editor = read("src/main/resources/static/js/passport-detail-editor-runtime.js");
        String html = read("src/main/resources/templates/passports/detail.html");
        String scss = read("src/main/resources/scss/app/passports/_workspace-interactions.scss");
        String security = read("src/main/java/com/example/panel/security/SecurityConfig.java");

        assertThat(security).contains("CookieCsrfTokenRepository.withHttpOnlyFalse()");
        assertThat(editor)
                .contains("headers['X-XSRF-TOKEN'] = csrfToken")
                .doesNotContain("headers['X-CSRF-TOKEN'] = csrfToken")
                .contains("function catalogMatch(item)")
                .contains("function resolvedEquipmentDraft()")
                .contains("passport-edit-equipment-card__summary")
                .contains("passport-edit-equipment-card__link-state")
                .contains("passport-edit-equipment-card__details")
                .contains("catalog_id: match.id")
                .contains("equipmentCover")
                .contains("passport-edit-equipment-card__visual")
                .contains("passport-edit-equipment-card__visual-placeholder");
        assertThat(html)
                .contains("app.css(v='20260923-01-272-s7-r8')")
                .contains("/js/passport-detail-editor-runtime.js?v=20260923-01-272-s7-r8");
        assertThat(scss)
                .contains("01-272 S7 corrective: compact equipment editor")
                .contains("passport-edit-equipment-card__summary")
                .contains("passport-edit-equipment-card__catalog-hint")
                .contains("passport-edit-equipment-card__details")
                .contains("01-272 S7 R8: catalogue thumbnails in equipment editor")
                .contains("passport-edit-equipment-card__visual");
    }
}
