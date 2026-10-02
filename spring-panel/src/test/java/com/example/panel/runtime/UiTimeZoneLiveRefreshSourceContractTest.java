package com.example.panel.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTimeZoneLiveRefreshSourceContractTest {

    @Test
    void timezoneControlPersistsPreferenceBeforeRefreshingCurrentPage() throws Exception {
        String source = Files.readString(Path.of("src/main/resources/static/js/ui-time.js"));

        int handler = source.indexOf("select.addEventListener('change', async () => {");
        int awaitedSet = source.indexOf("await prefApi.set('displayTimeZone', next, 'time-zone-control');", handler);
        int marker = source.indexOf("01-278 R32: refresh the current page after the display timezone is persisted", handler);
        int reload = source.indexOf("window.location.reload();", handler);

        assertTrue(handler >= 0, "timezone select change handler must exist");
        assertTrue(awaitedSet > handler, "displayTimeZone persistence must be awaited inside the handler");
        assertTrue(marker > awaitedSet, "R32 refresh marker must follow preference persistence");
        assertTrue(reload > marker, "current page must refresh after displayTimeZone changes");
    }
}
