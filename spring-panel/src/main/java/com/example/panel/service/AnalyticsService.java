package com.example.panel.service;

import com.example.panel.model.AnalyticsClientSummary;
import com.example.panel.model.AnalyticsTicketSummary;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable("analytics")
    public List<AnalyticsTicketSummary> loadTicketSummary() {
        return jdbcTemplate.query(
                "SELECT business, city, status, COUNT(*) AS total FROM tickets GROUP BY business, city, status",
                (rs, rowNum) -> new AnalyticsTicketSummary(
                        rs.getString("business"),
                        rs.getString("city"),
                        rs.getString("status"),
                        rs.getLong("total")
                )
        );
    }

    @Cacheable("analytics")
    public List<AnalyticsClientSummary> loadClientSummary() {
        return jdbcTemplate.query(
                "SELECT username, MAX(last_contact) AS last_contact, SUM(tickets) AS total_tickets FROM client_stats GROUP BY username",
                (rs, rowNum) -> new AnalyticsClientSummary(
                        rs.getString("username"),
                        rs.getObject("last_contact", OffsetDateTime.class),
                        rs.getLong("total_tickets")
                )
        );
    }
}
