package com.example.panel.model.notification;

import java.time.OffsetDateTime;

public record NotificationDto(Long id,
                              String text,
                              String url,
                              boolean read,
                              OffsetDateTime createdAt) {
}