package com.example.panel.service;

import java.util.Map;

public record UiEventFanoutEnvelope(
    String eventId,
    String originInstanceId,
    String targetUser,
    String eventName,
    Map<String, Object> payload
) {
    public UiEventFanoutEnvelope {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
