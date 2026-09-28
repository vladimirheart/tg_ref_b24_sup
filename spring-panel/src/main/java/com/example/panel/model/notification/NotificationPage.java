package com.example.panel.model.notification;

import java.util.List;

public record NotificationPage(
        List<NotificationDto> items,
        String nextCursor,
        boolean hasMore
) {
    public NotificationPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
