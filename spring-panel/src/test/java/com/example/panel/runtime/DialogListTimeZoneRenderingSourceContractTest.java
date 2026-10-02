package com.example.panel.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DialogListTimeZoneRenderingSourceContractTest {

    @Test
    void dialogListCreatedColumnUsesRawCreatedAtAndSharedUiTimezoneForInitialAndDynamicRows() throws IOException {
        String dialogs = read("src/main/resources/static/js/dialogs.js");
        String template = read("src/main/resources/templates/dialogs/index.html");

        assertThat(dialogs)
            .contains("function formatDialogListCreatedAt(value")
            .contains("const parsed = parseUtcDateValue(value);")
            .contains("const uiTime = window.iguanaUiTime;")
            .contains("date: uiTime.formatDate(parsed, { fallback: fallbackDate })")
            .contains("time: uiTime.formatTime(parsed, { fallback: fallbackTime, includeSeconds: true })")
            .contains("function applyDialogListCreatedTimeZone(root = table)")
            .contains("row.dataset.createdAt || ''")
            .contains("const created = formatDialogListCreatedAt(")
            .contains("item?.createdAt || ''")
            .contains("data-dialog-created-date")
            .contains("data-dialog-created-time")
            .contains("applyDialogListCreatedTimeZone();")
            .doesNotContain("const createdDate = item?.createdDateSafe")
            .doesNotContain("const createdTime = item?.createdTimeSafe");

        assertThat(template)
            .contains("th:data-created-at=\"${dialog.createdAt()}\"")
            .contains("data-dialog-created-date th:text=\"${dialog.createdDateSafe()}\"")
            .contains("data-dialog-created-time th:text=\"${dialog.createdTimeSafe()}\"")
            .contains("dialogsAssetVersion='20261002-01-278-timezone-r46'");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}
