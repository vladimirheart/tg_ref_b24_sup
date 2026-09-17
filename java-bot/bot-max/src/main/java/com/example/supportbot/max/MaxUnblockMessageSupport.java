package com.example.supportbot.max;

import com.example.supportbot.entity.ClientUnblockRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

final class MaxUnblockMessageSupport {

    private MaxUnblockMessageSupport() {
    }

    static String buildOperatorRequestMessage(ClientUnblockRequest request) {
        if (request == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Новый запрос на разблокировку\n");
        if (request.getId() != null) {
            builder.append("Заявка: #").append(request.getId()).append("\n");
        }
        builder.append("Клиент: ").append(request.getUserId()).append("\n");
        if (request.getReason() != null && !request.getReason().isBlank()) {
            builder.append("Причина: ").append(request.getReason()).append("\n");
        }
        if (request.getCreatedAt() != null) {
            builder.append("Создан: ").append(formatTimestamp(request.getCreatedAt())).append("\n");
        }
        builder.append("Статус: ").append(request.getStatus());
        return builder.toString();
    }

    static String buildClientResponse(ClientUnblockRequest request, boolean created, Duration retryAfter) {
        String requestId = request != null && request.getId() != null
                ? "#" + request.getId()
                : null;
        if (created) {
            return requestId == null
                    ? "Запрос на разблокировку отправлен оператору."
                    : "Запрос на разблокировку отправлен оператору. Номер заявки: " + requestId + ".";
        }
        if (retryAfter != null && !retryAfter.isZero() && !retryAfter.isNegative()) {
            String retryText = formatRetryAfter(retryAfter);
            if (requestId != null) {
                return "Запрос уже зарегистрирован под номером " + requestId
                        + ". Повторно можно отправить через " + retryText + ".";
            }
            return "Запрос уже зарегистрирован. Повторно можно отправить через " + retryText + ".";
        }
        return requestId == null
                ? "Запрос уже на рассмотрении."
                : "Запрос уже на рассмотрении. Номер заявки: " + requestId + ".";
    }

    private static String formatRetryAfter(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            return "несколько минут";
        }
        long seconds = retryAfter.getSeconds();
        if (seconds < 60) {
            return "менее минуты";
        }
        long minutes = (seconds + 59) / 60;
        if (minutes <= 1) {
            return "менее минуты";
        }
        return minutes + " мин.";
    }

    private static String formatTimestamp(OffsetDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }
}
