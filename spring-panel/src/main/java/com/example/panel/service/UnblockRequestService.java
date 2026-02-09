package com.example.panel.service;

import com.example.panel.model.clients.UnblockRequestItem;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UnblockRequestService {

    private final JdbcTemplate botJdbcTemplate;

    public UnblockRequestService(@Qualifier("botJdbcTemplate") JdbcTemplate botJdbcTemplate) {
        this.botJdbcTemplate = botJdbcTemplate;
    }

    public long countPendingRequests() {
        Long count = botJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM client_unblock_requests WHERE status = 'pending'",
                Long.class
        );
        return count != null ? count : 0L;
    }

    public List<UnblockRequestItem> loadRequests(String status) {
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toLowerCase() : null;
        String sql = """
            SELECT
                r.id,
                r.user_id,
                r.reason,
                r.status,
                r.created_at,
                r.decided_at,
                r.decided_by,
                r.decision_comment,
                c.channel_name
            FROM client_unblock_requests r
            LEFT JOIN channels c ON c.id = r.channel_id
            %s
            ORDER BY r.created_at DESC
            """.formatted(StringUtils.hasText(normalizedStatus) ? "WHERE r.status = ?" : "");

        Object[] args = StringUtils.hasText(normalizedStatus) ? new Object[]{normalizedStatus} : new Object[]{};
        return botJdbcTemplate.query(
                sql,
                args,
                (rs, rowNum) -> new UnblockRequestItem(
                        rs.getLong("id"),
                        rs.getString("user_id"),
                        rs.getString("channel_name"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("created_at"),
                        rs.getString("decided_at"),
                        rs.getString("decided_by"),
                        rs.getString("decision_comment")
                )
        ).stream().filter(Objects::nonNull).toList();
    }
}
