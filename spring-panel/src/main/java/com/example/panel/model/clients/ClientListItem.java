package com.example.panel.model.clients;

public record ClientListItem(
        Long userId,
        String username,
        String clientName,
        String channelName,
        long ticketCount,
        int totalMinutes,
        String formattedTime,
        String firstContact,
        String lastContact,
        String clientStatus,
        String panelId,
        boolean blacklisted,
        boolean unblockRequested
) {
}
