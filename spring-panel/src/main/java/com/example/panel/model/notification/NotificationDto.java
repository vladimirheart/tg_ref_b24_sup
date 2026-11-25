package com.example.panel.model.notification;

import java.time.OffsetDateTime;

public record NotificationDto(Long id,
                              String text,
                              String url,
                              boolean read,
                              OffsetDateTime createdAt,
                              String source,
                              String sourceLabel) {
    public NotificationDto(Long id, String text, String url, boolean read, OffsetDateTime createdAt) {
        this(id, text, url, read, createdAt, NotificationSource.fromUrl(url).key(), NotificationSource.fromUrl(url).label());
    }
}
