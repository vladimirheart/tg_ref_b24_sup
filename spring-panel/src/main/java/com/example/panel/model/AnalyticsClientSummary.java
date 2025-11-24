package com.example.panel.model;

import java.time.OffsetDateTime;

public record AnalyticsClientSummary(String username, OffsetDateTime lastContact, long totalTickets) {
}