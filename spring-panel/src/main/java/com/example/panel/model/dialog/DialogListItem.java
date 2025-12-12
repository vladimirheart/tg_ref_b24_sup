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

    public String statusLabel() {
        if (status == null || status.isBlank()) {
            return "Неизвестно";
        }
        return switch (status.toLowerCase()) {
            case "resolved" -> "Закрыт";
            case "pending" -> "В ожидании";
            default -> "Открыт";
        };
    }

    public String statusClass() {
        if (status == null || status.isBlank()) {
            return " bg-secondary-subtle text-secondary";
        }
        return switch (status.toLowerCase()) {
            case "resolved" -> " bg-success-subtle text-success";
            case "pending" -> " bg-warning-subtle text-warning";
            default -> " bg-primary-subtle text-primary";
        };
    }

    public String channelName() {
        return business;
    }

    public String location() {
        if (city == null || city.isBlank()) {
            return locationName;
        }
        if (locationName == null || locationName.isBlank()) {
            return city;
        }
        return city + ", " + locationName;
    }

    public String responsible() {
        if (resolvedBy != null && !resolvedBy.isBlank()) {
            return resolvedBy;
        }
        if (username != null && !username.isBlank()) {
            return username;
        }
        return null;
    }
}
