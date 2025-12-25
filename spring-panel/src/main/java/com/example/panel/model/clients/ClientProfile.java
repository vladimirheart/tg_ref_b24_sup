package com.example.panel.model.clients;

import java.util.List;

public record ClientProfile(
        ClientProfileHeader header,
        ClientProfileStats stats,
        List<ClientProfileTicket> tickets,
        ClientBlacklistInfo blacklist,
        Double averageRating,
        String clientStatus,
        List<ClientUsernameEntry> usernameHistory,
        List<ClientAnalyticsItem> categoryStats,
        List<ClientAnalyticsItem> locationStats,
        List<ClientPhoneEntry> phonesTelegram,
        List<ClientPhoneEntry> phonesManual
) {
}
