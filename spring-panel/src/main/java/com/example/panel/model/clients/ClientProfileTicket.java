package com.example.panel.model.clients;

public record ClientProfileTicket(
        String ticketId,
        String business,
        String city,
        String locationType,
        String locationName,
        String problem,
        String createdAt,
        String status,
        String resolvedAt,
        String category,
        String clientStatus,
        String channelName
) {
}
