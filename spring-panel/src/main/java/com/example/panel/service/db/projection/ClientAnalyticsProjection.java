package com.example.panel.service.db.projection;

import java.time.OffsetDateTime;

public record ClientAnalyticsProjection(
        String username,
        OffsetDateTime lastContact,
        Long totalTickets
) {
}
