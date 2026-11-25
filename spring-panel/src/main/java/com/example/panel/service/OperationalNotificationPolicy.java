package com.example.panel.service;

import com.example.panel.entity.Incident;
import java.util.Locale;

/**
 * Load-shedding policy for persisted operator notifications.
 *
 * <p>Routine dialog events remain available in the dialog UI and realtime stream,
 * but do not create one bell notification per operator. Persisted notifications are
 * reserved for SLA/AI escalations and critical operational incidents.</p>
 */
final class OperationalNotificationPolicy {

    private OperationalNotificationPolicy() {
    }

    static boolean allowsRoutineDialogAlert() {
        return false;
    }

    static boolean allowsIncidentNotification(Incident incident, String eventType) {
        if (isEscalation(eventType)) {
            return true;
        }
        if (incident == null || incident.getSeverity() == null) {
            return false;
        }
        return switch (incident.getSeverity().trim().toLowerCase(Locale.ROOT)) {
            case "critical", "emergency", "fatal", "blocker" -> true;
            default -> false;
        };
    }

    static boolean isEscalation(String eventType) {
        return eventType != null && eventType.toLowerCase(Locale.ROOT).contains("escalat");
    }
}
