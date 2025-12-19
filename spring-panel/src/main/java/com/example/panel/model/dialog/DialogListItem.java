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

    public String avatarInitial() {
        String source = displayClientName();
        if (source != null && !source.isBlank()) {
            return source.substring(0, 1).toUpperCase();
        }
        return "—";
    }

    public String displayClientName() {
        if (clientName != null && !clientName.isBlank()) {
            return clientName;
        }
        if (username != null && !username.isBlank()) {
            return username;
        }
        return "Неизвестный клиент";
    }

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
        if (business != null && !business.isBlank()) {
            return business;
        }
        return "Без канала";
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

    public String createdDateSafe() {
        if (createdDate != null && !createdDate.isBlank()) {
            return createdDate;
        }
        if (createdAt != null && !createdAt.isBlank() && createdAt.length() >= 10) {
            return createdAt.substring(0, 10);
        }
        return "Дата не указана";
    }

    public String createdTimeSafe() {
        if (createdTime != null && !createdTime.isBlank()) {
            return createdTime;
        }
        if (createdAt != null && !createdAt.isBlank()) {
            int timeStart = createdAt.indexOf(' ');
            if (timeStart > 0 && timeStart + 1 < createdAt.length()) {
                return createdAt.substring(timeStart + 1);
            }
        }
        return "—";
    }

    public String problemSafe() {
        if (problem != null && !problem.isBlank()) {
            return problem;
        }
        return "Проблема не указана";
    }
}
