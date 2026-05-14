package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicFormMetricsServiceTest {

    @Test
    void loadMetricsSnapshotBuildsAlertsFromRecordedFailures() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "public_form_metrics_enabled", true,
                        "public_form_alerts_enabled", true,
                        "public_form_alert_min_views", 1,
                        "public_form_alert_error_rate_threshold", 0.20d,
                        "public_form_alert_captcha_failure_rate_threshold", 0.10d,
                        "public_form_alert_rate_limit_rejection_rate_threshold", 0.10d,
                        "public_form_alert_session_lookup_miss_rate_threshold", 0.10d
                )
        ));
        PublicFormRuntimeConfigService runtimeConfigService = new PublicFormRuntimeConfigService(sharedConfigService);
        PublicFormMetricsService service = new PublicFormMetricsService(runtimeConfigService);

        service.recordConfigView(15L);
        service.recordSubmitSuccess(15L);
        service.recordSubmitError(15L, "captcha failed");
        service.recordSubmitError(15L, "too many requests");
        service.recordSessionLookup(15L, false);

        Map<String, Object> snapshot = service.loadMetricsSnapshot(15L);

        assertThat(snapshot.get("enabled")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> channels = (List<Map<String, Object>>) snapshot.get("channels");
        assertThat(channels).hasSize(1);
        assertThat(channels.get(0).get("channelId")).isEqualTo(15L);
        assertThat(channels.get(0).get("alerts")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .contains("high_submit_error_rate", "high_captcha_failure_rate", "high_rate_limit_rejection_rate", "high_session_lookup_miss_rate");
    }
}
