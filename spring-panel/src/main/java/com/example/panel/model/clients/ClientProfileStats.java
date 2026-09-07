package com.example.panel.model.clients;

public record ClientProfileStats(
        int total,
        int resolved,
        int pending,
        int totalMinutes,
        String formattedTime
) {
}
