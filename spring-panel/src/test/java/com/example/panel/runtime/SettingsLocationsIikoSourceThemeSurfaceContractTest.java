package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsLocationsIikoSourceThemeSurfaceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void disclosureCardsUseSemanticThemeClassesInsteadOfBootstrapBodySurfaces() throws IOException {
        String runtime = read("spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js");

        assertThat(runtime)
            .contains("locations-iiko-source-card")
            .contains("locations-iiko-source-card__header")
            .contains("locations-iiko-source-card__toggle")
            .contains("locations-iiko-source-card__meta")
            .contains("locations-iiko-source-card__actions")
            .contains("locations-iiko-source-card__body")
            .contains("locations-iiko-source-state--enabled")
            .contains("locations-iiko-source-state--disabled")
            .contains("locations-iiko-source-state--warning")
            .contains("locations-iiko-sources-empty")
            .doesNotContain("card-header bg-body")
            .doesNotContain("text-body text-start")
            .doesNotContain("text-bg-success-subtle")
            .doesNotContain("text-bg-secondary-subtle")
            .doesNotContain("text-bg-warning-subtle");
    }

    @Test
    void calmScssOwnsIikoDisclosurePresentationWithIguanaTokens() throws IOException {
        String calm = read("spring-panel/src/main/resources/scss/settings/_calm.scss");

        assertThat(calm)
            .contains("#locationsModal .locations-iiko-source-card")
            .contains("#locationsModal .locations-iiko-source-card__header")
            .contains("#locationsModal .locations-iiko-source-card__toggle")
            .contains("#locationsModal .locations-iiko-source-state--enabled")
            .contains("#locationsModal .locations-iiko-source-state--warning")
            .contains("background: var(--surface-raised);")
            .contains("background: var(--surface-interactive);")
            .contains("background: var(--surface-selected);")
            .contains("color: var(--color-text);")
            .contains("color: var(--color-text-muted);")
            .contains("background: var(--state-success-bg);")
            .contains("background: var(--state-warning-bg);");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
