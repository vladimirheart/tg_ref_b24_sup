package com.example.panel.model.dialog;

public record DialogListItem(String ticketId,
                             Long userId,
                             String username,
                             String clientName,
                             String business,
                             String city,
                             String locationName,
                             String problem,
                             String createdAt,
                             String status,
                             String resolvedBy,
                             String resolvedAt,
                             String createdDate,
                             String createdTime,
                             String clientStatus) {
}