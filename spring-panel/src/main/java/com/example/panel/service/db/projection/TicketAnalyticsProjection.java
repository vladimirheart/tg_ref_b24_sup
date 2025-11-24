package com.example.panel.service.db.projection;

public record TicketAnalyticsProjection(
        String business,
        String city,
        String status,
        Long totalTickets
) {
}