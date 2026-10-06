package com.example.panel.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UiTimeZonePreferenceSourceContractTest {

    @Test
    void userDisplayTimezoneDefaultsToUtcAndDrivesSharedUiTimestampFormatting() throws IOException {
        String service = read("src/main/java/com/example/panel/service/UiPreferenceService.java");
        String preferences = read("src/main/resources/static/js/ui-preferences.js");
        String head = read("src/main/resources/templates/fragments/ui-head.html");
        String timeRuntime = read("src/main/resources/static/js/ui-time.js");
        String dialogsPresentation = read("src/main/resources/static/js/dialogs-presentation-runtime.js");
        String sidebar = read("src/main/resources/static/js/sidebar.js");
        String tasks = read("src/main/resources/static/js/tasks.js");

        assertThat(service)
            .contains("mergeField(target, normalized, requestPayload, \"displayTimeZone\")")
            .contains("normalizeTimeZone(payload.get(\"displayTimeZone\"))")
            .contains("ZoneId.of(raw).getId()");

        assertThat(preferences)
            .contains("displayTimeZone: Object.freeze({")
            .contains("storageKey: 'iguana:display-time-zone'")
            .contains("fallback: 'UTC'");

        assertThat(head)
            .contains("ui-preferences.js(v='20261006-legacy-editor-removal-r1')")
            .contains("ui-time.js(v='20261002-01-278-timezone-r10')");

        assertThat(timeRuntime)
            .contains("const DEFAULT_TIME_ZONE = 'UTC';")
            .contains("patchDateLocaleMethod('toLocaleString'")
            .contains("patchDateLocaleMethod('toLocaleDateString'")
            .contains("patchDateLocaleMethod('toLocaleTimeString'")
            .contains("prefApi.set('displayTimeZone', next, 'time-zone-control')")
            .contains("UTC (по умолчанию)")
            .contains("Intl.supportedValuesOf('timeZone')");

        assertThat(dialogsPresentation)
            .contains("const uiTime = window.iguanaUiTime;")
            .contains("uiTime.formatDateTime(date")
            .contains("uiTime.formatDate(date");

        assertThat(sidebar).contains("return date.toLocaleString('ru-RU');");
        assertThat(tasks).contains("date.toLocaleString()");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), UTF_8);
    }
}
