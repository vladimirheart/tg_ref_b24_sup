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
    void locationTreeOpensOneLevelAtATimeAndKeepsLeafIndentation() throws IOException {
        String treeRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js");
        String calm = read("spring-panel/src/main/resources/scss/settings/_calm.scss");

        assertThat(treeRuntime)
            .contains("collapsedLocationNodes.add(makeCollapseKey('business', business));")
            .contains("collapsedLocationNodes.add(makeCollapseKey('type', business, type));")
            .contains("collapsedLocationNodes.add(makeCollapseKey('city', business, type, city));");

        assertThat(calm)
            .contains("#locationsModal .location-level-location")
            .contains("padding-left: 3rem;")
            .contains("padding-left: 0;");
    }

    @Test
    void locationMetadataAndIikoSavedSecretUseThemeTokens() throws IOException {
        String treeRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js");
        String iikoRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js");
        String calm = read("spring-panel/src/main/resources/scss/settings/_calm.scss");

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
