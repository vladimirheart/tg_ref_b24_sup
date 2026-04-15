package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogPublicFormConfigService {

    public void applySettings(Map<String, Object> payload,
                              Map<String, Object> dialogConfig) {
        if (payload.containsKey("dialog_public_form_message_max_length")) {
            dialogConfig.put("public_form_message_max_length",
                    payload.get("dialog_public_form_message_max_length"));
        }
        if (payload.containsKey("dialog_public_form_answers_total_max_length")) {
            dialogConfig.put("public_form_answers_total_max_length",
                    payload.get("dialog_public_form_answers_total_max_length"));
        }
        if (payload.containsKey("dialog_public_form_session_ttl_hours")) {
            dialogConfig.put("public_form_session_ttl_hours",
                    payload.get("dialog_public_form_session_ttl_hours"));
        }
        if (payload.containsKey("dialog_public_form_idempotency_ttl_seconds")) {
            dialogConfig.put("public_form_idempotency_ttl_seconds",
                    payload.get("dialog_public_form_idempotency_ttl_seconds"));
        }
        if (payload.containsKey("dialog_public_form_rate_limit_enabled")) {
            dialogConfig.put("public_form_rate_limit_enabled",
                    payload.get("dialog_public_form_rate_limit_enabled"));
        }
        if (payload.containsKey("dialog_public_form_rate_limit_window_seconds")) {
            dialogConfig.put("public_form_rate_limit_window_seconds",
                    payload.get("dialog_public_form_rate_limit_window_seconds"));
        }
        if (payload.containsKey("dialog_public_form_rate_limit_max_requests")) {
            dialogConfig.put("public_form_rate_limit_max_requests",
                    payload.get("dialog_public_form_rate_limit_max_requests"));
        }
        if (payload.containsKey("dialog_public_form_rate_limit_use_fingerprint")) {
            dialogConfig.put("public_form_rate_limit_use_fingerprint",
                    payload.get("dialog_public_form_rate_limit_use_fingerprint"));
        }
        if (payload.containsKey("dialog_public_form_metrics_enabled")) {
            dialogConfig.put("public_form_metrics_enabled",
                    payload.get("dialog_public_form_metrics_enabled"));
        }
        if (payload.containsKey("dialog_public_form_alerts_enabled")) {
            dialogConfig.put("public_form_alerts_enabled",
                    payload.get("dialog_public_form_alerts_enabled"));
        }
        if (payload.containsKey("dialog_public_form_alert_min_views")) {
            dialogConfig.put("public_form_alert_min_views",
                    payload.get("dialog_public_form_alert_min_views"));
        }
        if (payload.containsKey("dialog_public_form_alert_error_rate_threshold")) {
            dialogConfig.put("public_form_alert_error_rate_threshold",
                    payload.get("dialog_public_form_alert_error_rate_threshold"));
        }
        if (payload.containsKey("dialog_public_form_alert_captcha_failure_rate_threshold")) {
            dialogConfig.put("public_form_alert_captcha_failure_rate_threshold",
                    payload.get("dialog_public_form_alert_captcha_failure_rate_threshold"));
        }
        if (payload.containsKey("dialog_public_form_alert_rate_limit_rejection_rate_threshold")) {
            dialogConfig.put("public_form_alert_rate_limit_rejection_rate_threshold",
                    payload.get("dialog_public_form_alert_rate_limit_rejection_rate_threshold"));
        }
        if (payload.containsKey("dialog_public_form_alert_session_lookup_miss_rate_threshold")) {
            dialogConfig.put("public_form_alert_session_lookup_miss_rate_threshold",
                    payload.get("dialog_public_form_alert_session_lookup_miss_rate_threshold"));
        }
        if (payload.containsKey("dialog_public_form_strip_html_tags")) {
            dialogConfig.put("public_form_strip_html_tags",
                    payload.get("dialog_public_form_strip_html_tags"));
        }
        if (payload.containsKey("dialog_public_form_captcha_shared_secret")) {
            dialogConfig.put("public_form_captcha_shared_secret",
                    payload.get("dialog_public_form_captcha_shared_secret"));
        }
        if (payload.containsKey("dialog_public_form_captcha_mode")) {
            dialogConfig.put("public_form_captcha_mode",
                    payload.get("dialog_public_form_captcha_mode"));
        }
        if (payload.containsKey("dialog_public_form_turnstile_secret_key")) {
            dialogConfig.put("public_form_turnstile_secret_key",
                    payload.get("dialog_public_form_turnstile_secret_key"));
        }
        if (payload.containsKey("dialog_public_form_turnstile_verify_url")) {
            dialogConfig.put("public_form_turnstile_verify_url",
                    payload.get("dialog_public_form_turnstile_verify_url"));
        }
        if (payload.containsKey("dialog_public_form_turnstile_timeout_ms")) {
            dialogConfig.put("public_form_turnstile_timeout_ms",
                    payload.get("dialog_public_form_turnstile_timeout_ms"));
        }
        if (payload.containsKey("dialog_public_form_session_polling_enabled")) {
            dialogConfig.put("public_form_session_polling_enabled",
                    payload.get("dialog_public_form_session_polling_enabled"));
        }
        if (payload.containsKey("dialog_public_form_session_polling_interval_seconds")) {
            dialogConfig.put("public_form_session_polling_interval_seconds",
                    payload.get("dialog_public_form_session_polling_interval_seconds"));
        }
        if (payload.containsKey("dialog_public_form_session_token_rotate_on_read")) {
            dialogConfig.put("public_form_session_token_rotate_on_read",
                    payload.get("dialog_public_form_session_token_rotate_on_read"));
        }
        if (payload.containsKey("dialog_public_form_default_locale")) {
            dialogConfig.put("public_form_default_locale",
                    payload.get("dialog_public_form_default_locale"));
        }
        if (payload.containsKey("dialog_summary_badges")) {
            Map<String, Object> summaryBadges = new LinkedHashMap<>();
            Object existingBadges = dialogConfig.get("summary_badges");
            if (existingBadges instanceof Map<?, ?> badgesMap) {
                badgesMap.forEach((key, value) -> summaryBadges.put(String.valueOf(key), value));
            }
            Object rawBadges = payload.get("dialog_summary_badges");
            if (rawBadges instanceof Map<?, ?> incomingMap) {
                incomingMap.forEach((key, value) -> {
                    if (key != null) {
                        summaryBadges.put(String.valueOf(key), value);
                    }
                });
            }
            dialogConfig.put("summary_badges", summaryBadges);
        }
    }
}
