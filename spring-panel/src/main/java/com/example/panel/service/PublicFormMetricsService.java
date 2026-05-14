package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PublicFormMetricsService {
    private static final int DEFAULT_ALERT_MIN_VIEWS = 20;
    private static final double DEFAULT_ALERT_ERROR_RATE = 0.35d;
    private static final double DEFAULT_ALERT_CAPTCHA_FAILURE_RATE = 0.20d;
    private static final double DEFAULT_ALERT_RATE_LIMIT_REJECTION_RATE = 0.20d;
    private static final double DEFAULT_ALERT_SESSION_LOOKUP_MISS_RATE = 0.30d;

    private final PublicFormRuntimeConfigService runtimeConfigService;
    private final Map<Long, PublicFormMetricsAccumulator> metricsByChannel = new ConcurrentHashMap<>();

    public PublicFormMetricsService(PublicFormRuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    public void recordConfigView(Long channelId) {
        if (!runtimeConfigService.isMetricsEnabled() || channelId == null) {
            return;
        }
        metrics(channelId).views.incrementAndGet();
        metrics(channelId).touch();
    }

    public void recordSubmitSuccess(Long channelId) {
        if (!runtimeConfigService.isMetricsEnabled() || channelId == null) {
            return;
        }
        metrics(channelId).submits.incrementAndGet();
        metrics(channelId).touch();
    }

    public void recordSubmitError(Long channelId, String reason) {
        if (!runtimeConfigService.isMetricsEnabled() || channelId == null) {
            return;
        }
        PublicFormMetricsAccumulator accumulator = metrics(channelId);
        accumulator.submitErrors.incrementAndGet();
        String normalizedReason = reason == null ? "unknown" : reason.trim().toLowerCase(Locale.ROOT);
        if (normalizedReason.contains("captcha")) {
            accumulator.captchaFailures.incrementAndGet();
        }
        if (normalizedReason.contains("слишком много запросов")
                || normalizedReason.contains("too many requests")
                || normalizedReason.contains("rate limit")) {
            accumulator.rateLimitRejections.incrementAndGet();
        }
        accumulator.touch();
    }

    public void recordSessionLookup(Long channelId, boolean found) {
        if (!runtimeConfigService.isMetricsEnabled() || channelId == null) {
            return;
        }
        PublicFormMetricsAccumulator accumulator = metrics(channelId);
        accumulator.sessionLookups.incrementAndGet();
        if (!found) {
            accumulator.sessionLookupMisses.incrementAndGet();
        }
        accumulator.touch();
    }

    public Map<String, Object> loadMetricsSnapshot(Long channelId) {
        boolean alertsEnabled = runtimeConfigService.readDialogConfigBoolean("public_form_alerts_enabled", true);
        int minViews = runtimeConfigService.readDialogConfigInt("public_form_alert_min_views", DEFAULT_ALERT_MIN_VIEWS, 1, 10_000);
        double errorRateThreshold = runtimeConfigService.readDialogConfigDouble("public_form_alert_error_rate_threshold", DEFAULT_ALERT_ERROR_RATE, 0.01d, 1d);
        double captchaFailureRateThreshold = runtimeConfigService.readDialogConfigDouble("public_form_alert_captcha_failure_rate_threshold", DEFAULT_ALERT_CAPTCHA_FAILURE_RATE, 0.01d, 1d);
        double rateLimitRejectionRateThreshold = runtimeConfigService.readDialogConfigDouble("public_form_alert_rate_limit_rejection_rate_threshold", DEFAULT_ALERT_RATE_LIMIT_REJECTION_RATE, 0.01d, 1d);
        double sessionLookupMissRateThreshold = runtimeConfigService.readDialogConfigDouble("public_form_alert_session_lookup_miss_rate_threshold", DEFAULT_ALERT_SESSION_LOOKUP_MISS_RATE, 0.01d, 1d);

        List<Map<String, Object>> channels = metricsByChannel.entrySet().stream()
                .filter(entry -> channelId == null || channelId.equals(entry.getKey()))
                .sorted(Comparator.comparingLong(Map.Entry::getKey))
                .map(entry -> buildChannelMetricsRow(entry.getKey(), entry.getValue(), alertsEnabled, minViews,
                        errorRateThreshold, captchaFailureRateThreshold,
                        rateLimitRejectionRateThreshold, sessionLookupMissRateThreshold))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", runtimeConfigService.isMetricsEnabled());
        payload.put("alertsEnabled", alertsEnabled);
        payload.put("alertThresholds", Map.of(
                "minViews", minViews,
                "errorRate", errorRateThreshold,
                "captchaFailureRate", captchaFailureRateThreshold,
                "rateLimitRejectionRate", rateLimitRejectionRateThreshold,
                "sessionLookupMissRate", sessionLookupMissRateThreshold));
        payload.put("channelsWithAlerts", channels.stream()
                .filter(channel -> channel.get("alerts") instanceof List<?> alertList && !alertList.isEmpty())
                .count());
        payload.put("channels", channels);
        return payload;
    }

    private Map<String, Object> buildChannelMetricsRow(Long channelId,
                                                       PublicFormMetricsAccumulator metric,
                                                       boolean alertsEnabled,
                                                       int minViews,
                                                       double errorRateThreshold,
                                                       double captchaFailureRateThreshold,
                                                       double rateLimitRejectionRateThreshold,
                                                       double sessionLookupMissRateThreshold) {
        long views = metric.views.get();
        long submits = metric.submits.get();
        long submitErrors = metric.submitErrors.get();
        long captchaFailures = metric.captchaFailures.get();
        long rateLimitRejections = metric.rateLimitRejections.get();
        long sessionLookups = metric.sessionLookups.get();
        long sessionLookupMisses = metric.sessionLookupMisses.get();
        long submitAttempts = submits + submitErrors;
        double submitErrorRateByAttempts = submitAttempts > 0 ? (double) submitErrors / submitAttempts : 0.0d;
        double captchaFailureRateByAttempts = submitAttempts > 0 ? (double) captchaFailures / submitAttempts : 0.0d;
        double rateLimitRejectionRateByAttempts = submitAttempts > 0 ? (double) rateLimitRejections / submitAttempts : 0.0d;
        double sessionLookupMissRate = sessionLookups > 0 ? (double) sessionLookupMisses / sessionLookups : 0.0d;
        List<String> alerts = buildMetricAlerts(alertsEnabled, views, minViews, submitErrorRateByAttempts,
                errorRateThreshold, captchaFailureRateByAttempts, captchaFailureRateThreshold,
                rateLimitRejectionRateByAttempts, rateLimitRejectionRateThreshold,
                sessionLookupMissRate, sessionLookupMissRateThreshold);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("channelId", channelId);
        row.put("views", views);
        row.put("submits", submits);
        row.put("submitErrors", submitErrors);
        row.put("captchaFailures", captchaFailures);
        row.put("rateLimitRejections", rateLimitRejections);
        row.put("sessionLookups", sessionLookups);
        row.put("sessionLookupMisses", sessionLookupMisses);
        row.put("sessionLookupMissRate", sessionLookupMissRate);
        row.put("conversion", views > 0 ? (double) submits / views : 0.0d);
        row.put("errorRate", submits > 0 ? (double) submitErrors / submits : 0.0d);
        row.put("submitErrorRateByAttempts", submitErrorRateByAttempts);
        row.put("captchaFailureRateByAttempts", captchaFailureRateByAttempts);
        row.put("rateLimitRejectionRateByAttempts", rateLimitRejectionRateByAttempts);
        row.put("alerts", alerts);
        row.put("updatedAt", OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(metric.lastUpdatedAtMs.get()), ZoneOffset.UTC));
        return row;
    }

    private List<String> buildMetricAlerts(boolean alertsEnabled,
                                           long views,
                                           int minViews,
                                           double submitErrorRate,
                                           double submitErrorThreshold,
                                           double captchaFailureRate,
                                           double captchaFailureThreshold,
                                           double rateLimitRejectionRate,
                                           double rateLimitRejectionThreshold,
                                           double sessionLookupMissRate,
                                           double sessionLookupMissRateThreshold) {
        if (!alertsEnabled || views < minViews) {
            return List.of();
        }
        List<String> alerts = new java.util.ArrayList<>();
        if (submitErrorRate >= submitErrorThreshold) {
            alerts.add("high_submit_error_rate");
        }
        if (captchaFailureRate >= captchaFailureThreshold) {
            alerts.add("high_captcha_failure_rate");
        }
        if (rateLimitRejectionRate >= rateLimitRejectionThreshold) {
            alerts.add("high_rate_limit_rejection_rate");
        }
        if (sessionLookupMissRate >= sessionLookupMissRateThreshold) {
            alerts.add("high_session_lookup_miss_rate");
        }
        return alerts;
    }

    private PublicFormMetricsAccumulator metrics(Long channelId) {
        return metricsByChannel.computeIfAbsent(channelId, key -> new PublicFormMetricsAccumulator());
    }

    private static final class PublicFormMetricsAccumulator {
        private final AtomicLong views = new AtomicLong(0);
        private final AtomicLong submits = new AtomicLong(0);
        private final AtomicLong submitErrors = new AtomicLong(0);
        private final AtomicLong captchaFailures = new AtomicLong(0);
        private final AtomicLong rateLimitRejections = new AtomicLong(0);
        private final AtomicLong sessionLookups = new AtomicLong(0);
        private final AtomicLong sessionLookupMisses = new AtomicLong(0);
        private final AtomicLong lastUpdatedAtMs = new AtomicLong(System.currentTimeMillis());

        private void touch() {
            lastUpdatedAtMs.set(System.currentTimeMillis());
        }
    }
}
