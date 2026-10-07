package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsLocationsTreeHierarchySourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void locationTreeOpensOneLevelAtATimeAndUsesCompactReadableHierarchy() throws IOException {
        String treeRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js");
        String locationsTemplate = read("spring-panel/src/main/resources/templates/settings/fragments/locations.html");
        String settingsTemplate = read("spring-panel/src/main/resources/templates/settings/index.html");
        String calm = read("spring-panel/src/main/resources/scss/settings/calm/_infrastructure.scss");
        String compiled = read("spring-panel/src/main/resources/static/css/settings.css");

        assertThat(treeRuntime)
            .contains("collapsedLocationNodes.add(makeCollapseKey('business', business));")
            .contains("collapsedLocationNodes.add(makeCollapseKey('type', business, type));")
            .contains("collapsedLocationNodes.add(makeCollapseKey('city', business, type, city));")
            .contains("const CLOSED_LOCATION_STATUS = 'Закрыт';")
            .contains("let showClosedLocations = false;")
            .contains("function isLocationClosed(businessName, typeName, cityName, locationName)")
            .contains("closedLocationCount")
            .contains("const visibleLocationNames = showClosedLocations")
            .contains("document.getElementById('locationsShowClosed')")
            .contains("row.classList.toggle('is-closed', statusSelect.value === CLOSED_LOCATION_STATUS);");

        assertThat(locationsTemplate)
            .contains("id=\"locationsShowClosed\"")
            .contains("Показывать закрытые")
            .contains("location-closed-filter");

        assertThat(settingsTemplate)
            .contains("/css/settings.css?v=20261007-locations-tree-r32")
            .contains("/js/settings-locations-tree-runtime.js?v=20261007-closed-filter-r32");

        assertThat(calm)
            .contains("Location hierarchy polish - 2026-10-07 R26")
            .contains("#locationsModal .location-tree-row")
            .contains("display: flex;")
            .contains("#locationsModal .location-tree-bubble::before")
            .contains("content: \"Бизнес\";")
            .contains("#locationsModal .location-tree__children > .location-tree__item::before")
            .contains("#locationsModal .location-tree__children.is-collapsed")
            .contains("#locationsModal .location-name-input:disabled")
            .contains("-webkit-text-fill-color: var(--color-text);")
            .contains("#locationsModal .location-actions .btn-outline-danger:disabled")
            .contains("Location closed visibility - 2026-10-07 R32")
            .contains("#locationsModal .location-tree-card.is-closed")
            .contains("#locationsModal .location-status-badge.status-closed")
            .contains("#locationsModal .location-node-meta__badge.is-closed-count")
            .contains("#locationsModal .location-closed-filter")
            .contains("padding-left: 0;");

        assertThat(compiled)
            .contains("Location hierarchy polish - 2026-10-07 R26")
            .contains("Location closed visibility - 2026-10-07 R32")
            .contains("#locationsModal .location-tree-card")
            .contains("#locationsModal .location-tree-card.is-closed")
            .contains("#locationsModal .location-status-badge.status-closed")
            .contains("#locationsModal .location-node-meta__badge.is-closed-count")
            .contains("#locationsModal .location-tree__children.is-collapsed");

        assertThat(
            compiled.contains("#locationsModal .location-tree-bubble[data-location-level=\"location\"]::before")
                || compiled.contains("#locationsModal .location-tree-bubble[data-location-level=location]::before")
                || compiled.contains("#locationsModal .location-tree-bubble[data-location-level='location']::before")
        ).isTrue();
    }

    @Test
    void locationMetadataAndIikoSavedSecretUseThemeTokens() throws IOException {
        String treeRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js");
        String iikoRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js");
        String calm = read("spring-panel/src/main/resources/scss/settings/calm/_infrastructure.scss");

        assertThat(treeRuntime)
            .contains("badge rounded-pill location-node-meta__badge")
            .doesNotContain("badge rounded-pill text-bg-light");
        assertThat(iikoRuntime)
            .contains("locations-iiko-source-secret-badge")
            .doesNotContain("text-bg-light border text-body-secondary");
        assertThat(calm)
            .contains("#locationsModal .location-node-meta__badge")
            .contains("#locationsModal .locations-iiko-source-secret-badge")
            .contains("background: var(--surface-selected);")
            .contains("color: var(--color-text-muted);");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
