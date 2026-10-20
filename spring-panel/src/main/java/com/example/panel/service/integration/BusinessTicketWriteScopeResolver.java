package com.example.panel.service.integration;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Verifies an explicit owner against a single-business channel; never infers one. */
@Service
public class BusinessTicketWriteScopeResolver {

    private final JdbcTemplate jdbc;

    public BusinessTicketWriteScopeResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long requireSingleBusinessBinding(Long channelId, Long businessId) {
        if (channelId == null || channelId <= 0 || businessId == null || businessId <= 0) {
            throw new IllegalStateException("Ticket creation requires an explicit business ID and channel");
        }
        List<Binding> bindings = jdbc.query("""
                SELECT binding.business_id, binding.resolution_mode, business.status
                  FROM channels channel
                  JOIN business_channel_bindings binding ON binding.channel_id = channel.id
                  JOIN businesses business ON business.id = binding.business_id
                 WHERE channel.id = ? AND channel.is_active = TRUE AND binding.is_active = TRUE
                """, (rs, row) -> new Binding(rs.getLong("business_id"),
                rs.getString("resolution_mode"), rs.getString("status")), channelId);
        if (bindings.size() != 1 || bindings.get(0).businessId() != businessId
                || !"SINGLE_BUSINESS".equals(bindings.get(0).resolutionMode())
                || !"active".equals(bindings.get(0).businessStatus())) {
            throw new IllegalStateException("Ticket creation business binding is not uniquely verified");
        }
        return businessId;
    }

    /** An explicit-selection channel must never create a legacy/unresolved root. */
    public boolean requiresExplicitSelection(long channelId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM business_channel_bindings
                WHERE channel_id=? AND is_active=TRUE AND resolution_mode='EXPLICIT_SELECTION')
            """, Boolean.class, channelId));
    }

    private record Binding(long businessId, String resolutionMode, String businessStatus) {
    }
}
