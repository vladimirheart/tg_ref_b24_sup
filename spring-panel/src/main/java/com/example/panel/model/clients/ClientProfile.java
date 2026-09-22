package com.example.panel.model.clients;

import java.util.List;

public record ClientProfile(
        ClientProfileHeader header,
        ClientProfileStats stats,
        List<ClientProfileTicket> tickets,
        ClientBlacklistInfo blacklist,
        Double averageRating,
        List<ClientAnalyticsItem> ratingStats,
        long ratingTotal,
        String clientStatus,
        List<ClientUsernameEntry> usernameHistory,
        List<ClientBlacklistHistoryEntry> blacklistHistory,
        List<ClientAnalyticsItem> categoryStats,
        List<ClientAnalyticsItem> locationStats,
        List<ClientPeriodComparison> periodComparisons,
        List<ClientPhoneEntry> phonesTelegram,
        List<ClientPhoneEntry> phonesManual
) {
    public record ClientPeriodComparison(
            String key,
            String label,
            String description,
            long currentCount,
            long previousCount,
            String deltaLabel,
            String deltaTone,
            String currentRange,
            String previousRange,
            List<ClientTypeComparison> types
    ) {
    }

    public record ClientTypeComparison(
            String label,
            long currentCount,
            long previousCount
    ) {
    }
}
