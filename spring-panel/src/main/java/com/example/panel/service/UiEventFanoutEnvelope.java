package com.example.panel.service;

import java.util.Map;

public record UiEventFanoutEnvelope(
    String eventId,
    String originInstanceId,
    String targetUser,
    String eventName,
    Map<String, Object> payload,
    UiEventTicketScope ticketScope
) {
    /** Compatibility constructor for system events; ticket events without scope fail closed at delivery. */
    public UiEventFanoutEnvelope(String eventId, String originInstanceId, String targetUser,
                                 String eventName, Map<String, Object> payload) {
        this(eventId, originInstanceId, targetUser, eventName, payload, null);
    }

    public UiEventFanoutEnvelope {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
