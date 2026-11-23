package com.example.panel.model.clients;

public record ClientProfileTicket(
        String ticketId,
        String requestNumber,
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
        String channelName,
        Long channelId,
        String dialogUrl
) {
    public ClientProfileTicket(String ticketId, String requestNumber, String business, String city,
                               String locationType, String locationName, String problem, String createdAt,
                               String status, String resolvedAt, String category, String clientStatus,
                               String channelName, Long channelId) {
        this(ticketId, requestNumber, business, city, locationType, locationName, problem, createdAt,
            status, resolvedAt, category, clientStatus, channelName, channelId, null);
    }
}
