package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogsColumnWidthsUiSourceContractTest {

    @Test
    void dialogsColumnsResizeIndependentlyAndUsePerUserPreferences() throws IOException {
        String shell = read("src/main/resources/static/js/dialogs-shell-runtime.js");
        String dialogs = read("src/main/resources/static/js/dialogs.js");
        String prefs = read("src/main/resources/static/js/ui-preferences.js");
        String service = read("src/main/java/com/example/panel/service/UiPreferenceService.java");
        String scss = read("src/main/resources/scss/app/dialogs/_template-layout.scss");
        String settings = read("src/main/resources/templates/settings/index.html");
        String template = read("src/main/resources/templates/dialogs/index.html");

        assertThat(shell)
            .contains("DIALOG_COLUMN_WIDTHS_PREFERENCE")
            .contains("freezeVisibleColumnWidths")
            .contains("pointerdown")
            .contains("aria-valuemin")
            .contains("api.set(DIALOG_COLUMN_WIDTHS_PREFERENCE")
            .contains("api.remove(DIALOG_COLUMN_WIDTHS_PREFERENCE, 'server')")
            .doesNotContain("dataset?.resizable !== 'true'");
        assertThat(dialogs)
            .doesNotContain("STORAGE_WIDTHS")
            .doesNotContain("widths: STORAGE_WIDTHS");
        assertThat(prefs)
            .contains("dialogsColumnWidths")
            .contains("iguana:dialogs:column-widths-v2")
            .contains("flush: flushRemoteSyncNow")
            .contains("keepalive: true");
        assertThat(service)
            .contains("dialogsColumnWidths")
            .contains("normalizeDialogsColumnWidths");
        assertThat(scss)
            .contains("01-273 R8: dialogs per-user independent column resizing")
            .contains(".dialog-table th[data-column-key] > .resize-handle")
            .contains("cursor: col-resize");
        assertThat(template)
            .contains("app.css(v='20260924-01-273-r8')")
            .contains("dialogsAssetVersion='20260924-01-273-r8'");
        assertThat(settings)
            .contains("row row-cols-1 row-cols-md-2 row-cols-xl-3 g-3 settings-tiles")
            .doesNotContain("settings-overview-column-widths.js")
            .doesNotContain("settings-overview-resizable-grid");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}
