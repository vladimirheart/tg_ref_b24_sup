package com.example.panel.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardOlapBuilderUiSourceContractTest {

    @Test
    void olapBuilderUsesCompactThreeStageContractWithoutChangingApi() throws IOException {
        String html = read("src/main/resources/templates/dashboard/index.html");
        String scss = read("src/main/resources/scss/app/dashboard/_subworkspaces.scss");

        assertThat(html)
                .contains("reports-olap-workspace")
                .contains("reports-olap-step__index\" aria-hidden=\"true\">1</span>")
                .contains("reports-olap-step__index\" aria-hidden=\"true\">2</span>")
                .contains("reports-olap-step__index\" aria-hidden=\"true\">3</span>")
                .contains("Группировка")
                .contains("Показатели")
                .contains("Построение")
                .contains("id=\"olapContextPeriod\"")
                .contains("id=\"olapContextRestaurants\"")
                .contains("id=\"olapSelectionSummary\"")
                .contains("id=\"olapResultMeta\"")
                .contains("data-olap-spinner")
                .contains("data-olap-column=\"appeals\"")
                .contains("data-olap-column=\"tasks\"")
                .contains("data-olap-column=\"system_changes\"")
                .contains("const OLAP_METRICS = [")
                .contains("function updateOlapContext()")
                .contains("function updateOlapBuilderSummary()")
                .contains("function setOlapBuildLoading(loading)")
                .contains("Выберите хотя бы один показатель.")
                .contains("escapeOlapHtml(row.dimension)")
                .contains("fetch('/api/dashboard/olap-preview'")
                .contains("includeAppeals:")
                .contains("includeTasks:")
                .contains("includeSystemChanges:");

        assertThat(scss)
                .contains("01-272 S8: compact OLAP builder UX")
                .contains(".reports-olap-builder")
                .contains(".reports-olap-metric-option:has(input:checked)")
                .contains(".reports-olap-result__meta[data-tone='danger']")
                .contains(".reports-olap-dimension-cell");
    }

    @Test
    void reportsUseCalmSharedVisualGrammar() throws IOException {
        String html = read("src/main/resources/templates/dashboard/index.html");
        String scss = read("src/main/resources/scss/app/dashboard/_subworkspaces.scss");

        assertThat(html)
                .contains("dashboard-overview-grid reports-overview-unified")
                .contains("reports-subpanel reports-manager-workspace ops-panel")
                .doesNotContain("<span class=\"section-chip\">Live query</span>")
                .doesNotContain("metrics-headline__chip")
                .doesNotContain("metric-card metric-card--note");

        assertThat(scss)
                .contains("01-272 S8 R2: calm reports visual system")
                .contains(".reports-overview-unified")
                .contains("grid-template-columns: repeat(3, minmax(0, 1fr));")
                .contains(".reports-subpanel.reports-manager-workspace")
                .contains(".reports-olap-table thead th:not(:first-child)")
                .contains("font-variant-numeric: tabular-nums;");
    }

    @Test
    void reportsUseSharedCompactIconControls() throws IOException {
        String html = read("src/main/resources/templates/dashboard/index.html");
        String scss = read("src/main/resources/scss/app/dashboard/_subworkspaces.scss");
        String uiKit = read("src/main/resources/scss/app/_ui-kit.scss");

        assertThat(html)
                .contains("decorateReportsCompactControls()")
                .contains("ui-shortcut-control")
                .contains("ui-icon-control")
                .contains("bi-check2")
                .contains("bi-arrow-counterclockwise")
                .contains("aria-label=\"Описание отчёта по управляющим\"")
                .contains("aria-label=\"Описание конструктора отчётов\"");

        assertThat(scss)
                .contains("01-272 S8 R4/R7: compact controls consume shared UI-kit rules")
                .contains("font-weight: 520;")
                .doesNotContain(".reports-shortcut-control")
                .doesNotContain(".reports-icon-control");

        assertThat(uiKit)
                .contains("01-272 shared compact icon controls")
                .contains(".ui-icon-control")
                .contains(".ui-shortcut-control");
    }
    @Test
    void reportsUseSharedInlineStatesAndRefinedDensity() throws IOException {
        String html = read("src/main/resources/templates/dashboard/index.html");
        String scss = read("src/main/resources/scss/app/dashboard/_subworkspaces.scss");
        String uiKit = read("src/main/resources/scss/app/_ui-kit.scss");

        assertThat(html)
                .contains("ui-inline-state")
                .contains("bi-inbox")
                .contains("bi-exclamation-circle")
                .contains("Нет данных по управляющим за выбранный период.")
                .contains("Не удалось загрузить отчёт.");

        assertThat(scss)
                .contains("01-272 S8 R8: reports refinement and shared states")
                .contains(".activity-heatmap-cell--level-0")
                .contains("grid-template-columns: minmax(220px, 360px) 2.45rem 2.45rem;")
                .contains(".reports-olap-step--build .reports-olap-build");

        assertThat(uiKit)
                .contains("01-272 shared inline states")
                .contains(".ui-inline-state")
                .contains("width: 2.45rem !important;")
                .contains("max-width: 2.45rem;");
    }

    @Test
    void managerUsesSingleEmptyStateSurface() throws IOException {
        String html = read("src/main/resources/templates/dashboard/index.html");

        assertThat(html)
                .contains("id=\"managerSupervisorSummaryCard\" hidden")
                .contains("supervisorCard.hidden = false")
                .contains("supervisorCard.hidden = true")
                .contains("supervisor.textContent = ''");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
