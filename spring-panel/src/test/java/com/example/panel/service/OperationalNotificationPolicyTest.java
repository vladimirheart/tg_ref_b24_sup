package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.entity.Incident;
import org.junit.jupiter.api.Test;

class OperationalNotificationPolicyTest {

    @Test
    void retainsOnlyCriticalIncidentsAndEscalations() {
        Incident high = incident("high");
        Incident critical = incident("critical");

        assertThat(OperationalNotificationPolicy.allowsRoutineDialogAlert()).isFalse();
        assertThat(OperationalNotificationPolicy.allowsIncidentNotification(high, "incident_created")).isFalse();
        assertThat(OperationalNotificationPolicy.allowsIncidentNotification(critical, "incident_created")).isTrue();
        assertThat(OperationalNotificationPolicy.allowsIncidentNotification(high, "incident_escalation")).isTrue();
    }

    private Incident incident(String severity) {
        Incident incident = new Incident();
        incident.setSeverity(severity);
        return incident;
    }
}
