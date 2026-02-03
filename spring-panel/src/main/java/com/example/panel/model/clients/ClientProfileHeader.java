package com.example.panel.model.clients;

public record ClientProfileHeader(
        Long userId,
        String username,
        String clientName,
        Long channelId,
        String channelName,
        String platform
) {
}
