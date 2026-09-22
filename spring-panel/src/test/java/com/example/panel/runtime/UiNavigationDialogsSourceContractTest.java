package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UiNavigationDialogsSourceContractTest {
  private String read(String path) throws IOException { return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n"); }

  @Test void sidebarDropsReorderAndUsesUserCardForActions() throws IOException {
    String js = read("src/main/resources/static/js/sidebar.js");
    String html = read("src/main/resources/templates/fragments/navbar.html");
    assertThat(js).doesNotContain("sidebarNavOrder").doesNotContain("editSidebarOrderBtn").doesNotContain("applyDraggableState");
    assertThat(html).contains("data-sidebar-action-trigger").contains("editSelf").doesNotContain("sidebar-action-menu-orb").doesNotContain("editSidebarOrderBtn");
  }

  @Test void dialogsKeepListOnlyPreferenceAndInitializeWithoutDeadCompactToggle() throws IOException {
    String js = read("src/main/resources/static/js/dialogs.js");
    String shell = read("src/main/resources/static/js/dialogs-shell-runtime.js");
    String html = read("src/main/resources/templates/dialogs/index.html");
    assertThat(js)
        .doesNotContain("exitListOnlyMode")
        .doesNotContain("STORAGE_COMPACT_MODE")
        .doesNotContain("dialogCompactToggle")
        .contains("item.key !== 'select'")
        .contains("DIALOG_COLUMNS_SCHEMA_VERSION")
        .contains("const defaultColumnOrder = [")
        .contains("'actions',")
        .contains("sanitizeDialogProblemLabel")
        .contains("Уточнение после ответов на вопрос")
        .contains("dialog-open-icon");
    assertThat(shell)
        .contains("nextState.select = false;")
        .contains("nextOrder = ['actions', ...nextOrder.filter((key) => key !== 'actions')];");
    assertThat(html)
        .doesNotContain("id=\"dialogCompactToggle\"")
        .contains("data-column-key=\"select\"")
        .contains("dialog-open-icon")
        .contains("page-header-title-row");
    assertThat(html.indexOf("Действия</th>")).isLessThan(html.indexOf("№ обращения</th>"));
  }

  @Test void dialogsSlaUsesResolvedAtAndRendersStatusOverTiming() throws IOException {
    String js = read("src/main/resources/static/js/dialogs.js");
    String html = read("src/main/resources/templates/dialogs/index.html");
    String sla = read("src/main/resources/static/js/dialogs-sla-runtime.js");
    String scss = read("src/main/resources/scss/app/dialogs/_list-layout.scss");
    assertThat(js).contains("data-resolved-at=\"${escapeHtml(item?.resolvedAt || '')}\"");
    assertThat(html).contains("th:data-resolved-at=\"${dialog.resolvedAt()}\"");
    assertThat(sla)
        .contains("row.dataset.resolvedAt")
        .contains("timingLabel")
        .contains("dialog-sla-status")
        .contains("dialog-sla-timing")
        .contains("Просрочен на");
    assertThat(scss)
        .contains("/* 01-272 dialogs corrective S2 */")
        .contains("background: transparent !important;")
        .contains(".dialog-sla-status")
        .contains(".dialog-sla-timing");
  }

  @Test void dialogsDensityKeepsActionsLabelForColumnPickerButHidesTableHeader() throws IOException {
    String js = read("src/main/resources/static/js/dialogs.js");
    String html = read("src/main/resources/templates/dialogs/index.html");
    String scss = read("src/main/resources/scss/app/dialogs/_template-layout.scss");
    assertThat(js).contains("label: (cell.textContent || '').trim()");
    assertThat(html)
        .contains("class=\"dialog-actions-head\"><span class=\"visually-hidden\">Действия</span>")
        .doesNotContain("<th data-column-key=\"actions\">Действия</th>");
    assertThat(scss)
        .contains("/* 01-272 dialogs density S3 */")
        .contains("[data-column-key='client'] { width: 8.5rem; }")
        .contains("[data-column-key='status'] { width: 7rem; }")
        .contains("[data-column-key='business'] { width: 6.25rem; }")
        .contains("[data-column-key='actions'] { width: 2.4rem; }")
        .contains("padding-top: 0.32rem !important;")
        .contains("height: 1.45rem;");
  }

  @Test void selfProfileDeepLinkUsesExistingAuthorizedEditor() throws IOException {
    String auth = read("src/main/resources/static/js/auth-management.js");
    assertThat(auth).contains("openRequestedSelfEditor").contains("params.get('editSelf')").contains("this.openUserModal(userId)");
  }
}
