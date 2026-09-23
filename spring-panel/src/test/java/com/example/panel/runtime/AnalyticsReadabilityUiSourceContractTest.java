package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsReadabilityUiSourceContractTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/analytics/index.html");
    private static final Path SCSS = Path.of("src/main/resources/scss/app/analytics/_workspace.scss");
    private static final Path TELEMETRY_SCSS = Path.of("src/main/resources/scss/app/analytics/_telemetry.scss");

    @Test
    void analyticsOverviewUsesCompactNavigationAndSharedInfoControls() throws IOException {
        String html = read(TEMPLATE);

        assertThat(html)
            .contains("analytics-route-strip")
            .contains("analytics-jump-nav")
            .contains("#analyticsBotRuntimeCard")
            .contains("#analyticsIntegrationTransportCard")
            .contains("#workspaceTelemetryCard")
            .contains("Runtime ботов")
            .contains("Транспорт интеграций")
            .contains("Контроль rollout workspace")
            .contains("data-content-disclosure-help")
            .contains("analytics-export-action")
            .contains("ui-icon-control")
            .contains("bi bi-download")
            .contains("bi bi-arrow-clockwise");
    }

    @Test
    void operationalRecoveryActionsRemainVisibleAndAddressable() throws IOException {
        String html = read(TEMPLATE);

        assertThat(html)
            .contains("analyticsIntegrationTransportReplayFailed")
            .contains("Replay failed inbound")
            .contains("analyticsIntegrationTransportRequeueFailed")
            .contains("Requeue failed publish")
            .contains("workspaceTelemetryDays")
            .contains("workspaceTelemetryScope");
    }

    @Test
    void analyticsStylesUseCalmerMetricAndTableDensity() throws IOException {
        String scss = read(SCSS);
        String telemetryScss = read(TELEMETRY_SCSS);

        assertThat(scss)
            .contains("01-272 S9 analytics readability")
            .contains(".analytics-route-strip")
            .contains(".analytics-jump-nav")
            .contains(".analytics-section-title-row")
            .contains(".analytics-table-shell .table > :not(caption) > * > *")
            .contains("max-height: 380px")
            .contains("font-weight: 650");
        assertThat(telemetryScss)
            .contains(".analytics-export-form")
            .contains("width: auto")
            .doesNotContain(".analytics-page-actions form .btn");
    }

    @Test
    void analyticsR2UsesProgressiveDisclosureForDeepOperationalContent() throws IOException {
        String html = read(TEMPLATE);
        String scss = read(SCSS);

        assertThat(html)
            .contains("analytics-transport-diagnostics")
            .contains("Диагностика и recovery")
            .contains("analytics-primary-metrics")
            .contains("analytics-metrics-details")
            .contains("analytics-secondary-metrics")
            .contains("19 показателей")
            .contains("analytics-review-disclosure")
            .contains("analytics-governance-edit")
            .contains("workspaceTelemetryReviewConfirm")
            .contains("workspaceTelemetryLegacyUsageDecision")
            .contains("workspaceTelemetrySlaPolicySaveReview")
            .contains("workspaceTelemetryMacroGovernanceSaveReview")
            .contains("workspaceTelemetryMacroExternalCatalogSavePolicy")
            .contains("workspaceTelemetryMacroDeprecationSavePolicy");
        assertThat(scss)
            .contains("01-272 S9 R2 progressive disclosure")
            .contains(".analytics-progressive-disclosure")
            .contains(".analytics-review-disclosure")
            .contains(".analytics-governance-edit");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}
