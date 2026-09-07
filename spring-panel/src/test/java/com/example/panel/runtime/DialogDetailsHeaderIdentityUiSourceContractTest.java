package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogDetailsHeaderIdentityUiSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void headerShowsOnlyDialogNumberInlineLocationAndCenteredBusiness() throws IOException {
        String template = read("spring-panel/src/main/resources/templates/dialogs/index.html");
        String dialogsJs = read("spring-panel/src/main/resources/static/js/dialogs.js");
        String presentationRuntime = read("spring-panel/src/main/resources/static/js/dialogs-presentation-runtime.js");
        String detailsRuntime = read("spring-panel/src/main/resources/static/js/dialogs-details-runtime.js");
        String dialogsScss = read("spring-panel/src/main/resources/scss/app/_dialogs.scss");
        String taskList = read("ai-context/tasks/task-list.md");

        assertThat(template)
            .contains("class=\"dialog-details-meta-pair\"")
            .contains("<strong class=\"dialog-details-meta-label\">Диалог</strong>")
            .contains("<strong class=\"dialog-details-meta-label\">Локация</strong>")
            .contains("id=\"dialogDetailsLocation\">—</span>")
            .contains("id=\"dialogDetailsBusiness\" aria-label=\"Бизнес\">—</div>")
            .contains("@{/css/app.css(v='20260907-1')}")
            .contains("dialogsAssetVersion='20260907-1'")
            .doesNotContain("id=\"dialogDetailsLocation\">Локация: —</div>");

        assertThat(dialogsJs)
            .contains("const detailsBusiness = document.getElementById('dialogDetailsBusiness');")
            .contains("detailsLocation.textContent = safeValue;")
            .contains("detailsBusiness,");

        assertThat(presentationRuntime)
            .contains("function formatDialogMeta(_ticketId, requestNumber)")
            .contains("return normalizedRequest ? `№ ${normalizedRequest}` : '—';")
            .doesNotContain("№ обращения:")
            .doesNotContain("ID диалога:")
            .doesNotContain(" · ID: ");

        assertThat(detailsRuntime)
            .contains("function updateDetailsBusinessLabel(raw)")
            .contains("updateDetailsBusinessLabel(fallbackRow?.dataset?.business || '—');")
            .contains("updateDetailsBusinessLabel(businessLabel);")
            .contains("businessStyle.background")
            .contains("businessStyle.text");

        assertThat(dialogsScss)
            .contains("/* 01-255: dialog header identity density and centered business */")
            .contains("grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);")
            .contains("padding-bottom: 0.5rem;")
            .contains("#dialogDetailsModal .dialog-details-meta-pair")
            .contains("#dialogDetailsModal .dialog-details-meta-label")
            .contains("#dialogDetailsModal .dialog-details-header-business");

        assertThat(taskList)
            .contains("🟢 [01-254] Уплотнить шапку диалога, composer и добавить SLA-пульсацию метрик")
            .contains("🟣 [01-255] Уплотнить идентификационную шапку диалога и вынести бизнес в центр");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(
            REPO_ROOT.resolve(relativePath),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }
}
