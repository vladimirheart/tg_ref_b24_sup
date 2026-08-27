package com.example.panel.model.observability;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record AlertmanagerWebhookPayload(
    String version,
    String groupKey,
    Integer truncatedAlerts,
    String status,
    String receiver,
    Map<String, String> groupLabels,
    Map<String, String> routeLabels,
    Map<String, String> commonLabels,
    Map<String, String> commonAnnotations,
    String externalURL,
    @JsonProperty("notification_reason") String notificationReason,
    List<Alert> alerts
) {

    public List<Alert> safeAlerts() {
        return alerts == null ? List.of() : alerts;
    }

    public Map<String, String> safeCommonLabels() {
        return commonLabels == null ? Map.of() : commonLabels;
    }

    public Map<String, String> safeCommonAnnotations() {
        return commonAnnotations == null ? Map.of() : commonAnnotations;
    }

    public record Alert(
        String status,
        Map<String, String> labels,
        Map<String, String> annotations,
        String startsAt,
        String endsAt,
        String generatorURL,
        String fingerprint
    ) {
        public Map<String, String> safeLabels() {
            return labels == null ? Map.of() : labels;
        }

        public Map<String, String> safeAnnotations() {
            return annotations == null ? Map.of() : annotations;
        }
    }
}
