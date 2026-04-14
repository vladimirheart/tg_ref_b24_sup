package com.example.panel.model.clients;

public record ClientBlacklistHistoryEntry(
        String action,
        String actionLabel,
        String at,
        String actor,
        String reason
) {
}
