package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogDetailsCompactHeaderComposerSlaUiSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void compactDialogHeaderComposerAndSlaPulseLiveInSourceFiles() throws IOException {
        String template = read("spring-panel/src/main/resources/templates/dialogs/index.html");
        String dialogsJs = read("spring-panel/src/main/resources/static/js/dialogs.js");
        String dialogsScss = read("spring-panel/src/main/resources/scss/app/_dialogs.scss");
        String dialogsActionsRuntime = read("spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js");
        String dialogsDetailsRuntime = read("spring-panel/src/main/resources/static/js/dialogs-details-runtime.js");
        String taskList = read("ai-context/tasks/task-list.md");

        assertThat(template)
            .contains("/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css")
            .contains("id=\"dialogDetailsParticipantsBtn\"")
            .contains("bi bi-people")
            .contains("id=\"dialogDetailsReassignBtn\"")
            .contains("id=\"dialogDetailsCreateTask\"")
            .contains("id=\"dialogFontSizeToggle\"")
            .contains("id=\"dialogFontSizePanel\"")
            .contains("id=\"dialogFontSizeValue\"")
            .contains("id=\"dialogDetailsMetricsSection\"")
            .contains("id=\"dialogReplyText\" rows=\"2\" placeholder=\"Введите ответ...\" aria-label=\"Ответ клиенту\"")
            .contains("id=\"dialogReplySend\" aria-label=\"Отправить\"")
            .contains("bi bi-send-fill")
            .contains("class=\"modal fade dialog-media-preview-modal\" id=\"dialogMediaPreviewModal\"")
            .contains("class=\"btn-close dialog-media-preview-close\"")
            .contains("class=\"dialog-media-preview-toolbar\"")
            .contains("id=\"dialogMediaZoomOut\"")
            .contains("bi bi-zoom-out")
            .contains("id=\"dialogMediaZoomIn\"")
            .contains("bi bi-zoom-in")
            .contains("id=\"dialogMediaDownloadLink\"")
            .contains("bi bi-download")
            .contains("@{/css/app.css(v='20260904-2')}")
            .contains("dialogsAssetVersion='20260904-3'")
            .doesNotContain(">Участники</button>")
            .doesNotContain(">Передать</button>")
            .doesNotContain(">Отправить</button>")
            .doesNotContain("for=\"dialogReplyText\">Ответ клиенту</label>")
            .doesNotContain("<h5 class=\"modal-title\">Просмотр медиа</h5>")
            .doesNotContain("<div class=\"modal-footer\">\n                <div class=\"btn-group d-none\" id=\"dialogMediaImageControls\">");

        assertThat(dialogsJs)
            .contains("const detailsMetricsSection = document.getElementById('dialogDetailsMetricsSection');")
            .contains("const dialogFontSizeToggle = document.getElementById('dialogFontSizeToggle');")
            .contains("function setDialogFontControlOpen(open)")
            .contains("function syncDetailsMetricsSlaState()")
            .contains("dialogsSlaRuntime?.computeSlaState(activeDialogRow)")
            .contains("syncDetailsMetricsSlaState();")
            .contains("dialogFontSizePanel.hidden = !shouldOpen")
            .contains("dialogFontSizeValue.textContent = size + ' px'");

        assertThat(dialogsScss)
            .contains("/* 01-254: compact dialog header, composer controls and SLA pulse */")
            .contains("#dialogDetailsModal .dialog-details-action-icon")
            .contains("#dialogDetailsModal .dialog-font-control-panel")
            .contains("#dialogDetailsModal .dialog-composer-input > .dialog-reply-send-icon")
            .contains("#dialogDetailsMetricsSection.dialog-sla-safe")
            .contains("#dialogDetailsMetricsSection.dialog-sla-risk")
            .contains("#dialogDetailsMetricsSection.dialog-sla-overdue")
            .contains("#dialogMediaPreviewModal .dialog-media-preview-shell")
            .contains("#dialogMediaPreviewModal .dialog-media-preview-stage")
            .contains("#dialogMediaPreviewModal .dialog-media-preview-toolbar")
            .contains("@keyframes dialog-details-sla-pulse")
            .contains("@media (prefers-reduced-motion: reduce)");

        assertThat(dialogsActionsRuntime)
            .contains("function setDetailsIconButtonVisual(button, iconClass, label, visualOptions = {})")
            .contains("resolved ? 'bi-check-circle-fill' : 'bi-check-circle'")
            .contains("'bi-send-fill'")
            .contains("'Отправка...'")
            .doesNotContain("elements.detailsResolve.textContent = resolved ? 'Обращение закрыто' : 'Закрыть обращение';")
            .doesNotContain("elements.detailsReplySend.textContent = pendingCount > 0");

        assertThat(dialogsDetailsRuntime)
            .contains("const PROBLEM_FOLLOW_UP_PREFIX = 'Уточнение после ответов на вопросы:';")
            .contains("function formatDetailsProblemLabel(raw)")
            .contains("const problemLabel = formatDetailsProblemLabel(");

        assertThat(taskList)
            .contains("🟢 [01-253] Сделать reply-target пульсацию заметной и вынести меню сообщения за bubble")
            .contains("🟣 [01-254] Уплотнить шапку диалога, composer и добавить SLA-пульсацию метрик");

        assertThat(Files.exists(REPO_ROOT.resolve("apply-dialog-reply-pulse-menu-ui-v1.js"))).isFalse();
        assertThat(Files.exists(REPO_ROOT.resolve("apply-dialog-reply-pulse-menu-ui-v2.js"))).isFalse();
        assertThat(Files.exists(REPO_ROOT.resolve("apply-dialog-reply-pulse-menu-ui-v3.js"))).isFalse();
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(
            REPO_ROOT.resolve(relativePath),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }
}
