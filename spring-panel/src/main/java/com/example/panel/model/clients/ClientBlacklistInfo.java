package com.example.panel.model.clients;

public record ClientBlacklistInfo(
        boolean blacklisted,
        boolean unblockRequested,
        String addedAt,
        String addedBy,
        String reason
) {
}
