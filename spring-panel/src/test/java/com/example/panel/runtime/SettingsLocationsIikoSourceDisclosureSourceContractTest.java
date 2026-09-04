package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsLocationsIikoSourceDisclosureSourceContractTest {

    private static final Path RUNTIME = Path.of(
        "src/main/resources/static/js/settings-locations-iiko-runtime.js"
    );

    @Test
    void iikoServerSourcesUseCompactDisclosureCards() throws IOException {
        String runtime = Files.readString(RUNTIME, StandardCharsets.UTF_8)
            .replace("\r\n", "\n");

        assertThat(runtime)
            .contains("const expandedLocationsIikoServerSourceIds = new Set();")
            .contains("data-bs-toggle=\"collapse\"")
            .contains("data-locations-source-collapse")
            .contains("bindLocationsIikoServerSourceCollapseState(container);")
            .contains("expandedLocationsIikoServerSourceIds.add(sourceId);")
            .contains("expandedLocationsIikoServerSourceIds.clear();")
            .contains("Секрет сохранён")
            .contains("Нужен секрет");
    }
}
