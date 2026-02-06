package com.example.panel.model.dialog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.util.StringUtils;

public record DialogListItem(String ticketId,
                             Long userId,
                             String username,
                             String clientName,
                             String business,
                             Long channelId,
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
                             String clientStatus,
                             String lastMessageSender,
                             String lastMessageTimestamp,
                             Integer unreadCount,
                             String categories) {

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

    @JsonProperty("statusLabel")
    public String statusLabel() {
        if (isClosed()) {
            return isAutoClosed() ? "Закрыт автоматически" : "Закрыт";
        }
        if (!StringUtils.hasText(responsible) && !hasOperatorReply()) {
            return "новый";
        }
        if (!hasOperatorReply()) {
            return "ожидает ответа оператора";
        }
        return "ожидает ответа клиента";
    }

    @JsonProperty("statusKey")
    public String statusKey() {
        if (isClosed()) {
            return isAutoClosed() ? "auto_closed" : "closed";
        }
        if (!StringUtils.hasText(responsible) && !hasOperatorReply()) {
            return "new";
        }
        if (!hasOperatorReply()) {
            return "waiting_operator";
        }
        return "waiting_client";
    }

    @JsonProperty("statusClass")
    public String statusClass() {
        return switch (statusKey()) {
            case "auto_closed" -> " bg-secondary-subtle text-secondary";
            case "closed" -> " bg-success-subtle text-success";
            case "waiting_operator" -> " bg-warning-subtle text-warning";
            case "waiting_client" -> " bg-info-subtle text-info";
            case "new" -> " bg-primary-subtle text-primary";
            default -> " bg-secondary-subtle text-secondary";
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
            return responsible;
        }
        if (resolvedBy != null && !resolvedBy.isBlank()) {
            return resolvedBy;
        }
        return null;
    }

    public String categoriesSafe() {
        if (categories == null || categories.isBlank()) {
            return "—";
        }
        return categories;
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

    @JsonProperty("unreadCount")
    public Integer unreadCount() {
        return unreadCount != null ? unreadCount : 0;
    }

    private boolean isClosed() {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase();
        return "resolved".equals(normalized) || "closed".equals(normalized);
    }

    private boolean isAutoClosed() {
        if (!isClosed() || !StringUtils.hasText(resolvedBy)) {
            return false;
        }
        String normalized = resolvedBy.trim().toLowerCase();
        return normalized.contains("auto") || normalized.contains("авто");
    }

    private boolean hasOperatorReply() {
        if (!StringUtils.hasText(lastMessageSender)) {
            return false;
        }
        String normalized = lastMessageSender.trim().toLowerCase();
        return normalized.contains("support")
                || normalized.contains("operator")
                || normalized.contains("admin")
                || normalized.contains("system");
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
