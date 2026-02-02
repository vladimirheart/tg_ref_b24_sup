package com.example.panel.model.dialog;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record DialogListItem(String ticketId,
                             Long userId,
                             String username,
                             String clientName,
                             String business,
                             String channelName,
                             String city,
                             String locationName,
                             String problem,
                             String createdAt,
                             String status,
                             String resolvedBy,
                             String resolvedAt,
                             String responsible,
                             String createdDate,
                             String createdTime,
                             String clientStatus) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

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

    public String channelLabel() {
        if (channelName != null && !channelName.isBlank()) {
            return channelName;
        }
        return "Без канала";
    }

    public String businessLabel() {
        if (business != null && !business.isBlank()) {
            return business;
        }
        return "Без бизнеса";
    }

    public String clientStatusLabel() {
        if (clientStatus != null && !clientStatus.isBlank()) {
            return clientStatus;
        }
        return "статус не указан";
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
        if (responsible != null && !responsible.isBlank()) {
            return resolvedBy;
        }
        if (resolvedBy != null && !resolvedBy.isBlank()) {
            return username;
        }
        return null;
    }

    public String createdDateSafe() {
        if (createdDate != null && !createdDate.isBlank()) {
            String formatted = formatEpoch(createdDate, DATE_FORMAT);
            return formatted != null ? formatted : createdDate;
        }
        if (createdAt != null && !createdAt.isBlank() && createdAt.length() >= 10) {
            String formatted = formatEpoch(createdAt, DATE_FORMAT);
            if (formatted != null) {
                return formatted;
            }
            return createdAt.substring(0, 10);
        }
        return "Дата не указана";
    }

    public String createdTimeSafe() {
        if (createdTime != null && !createdTime.isBlank()) {
            String formatted = formatEpoch(createdTime, TIME_FORMAT);
            return formatted != null ? formatted : createdTime;
        }
        if (createdAt != null && !createdAt.isBlank()) {
            String formatted = formatEpoch(createdAt, TIME_FORMAT);
            if (formatted != null) {
                return formatted;
            }
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

    private static String formatEpoch(String raw, DateTimeFormatter formatter) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d{10,13}")) {
            return null;
        }
        try {
            long epoch = Long.parseLong(trimmed);
            if (trimmed.length() == 10) {
                epoch *= 1000;
            }
            return Instant.ofEpochMilli(epoch)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
