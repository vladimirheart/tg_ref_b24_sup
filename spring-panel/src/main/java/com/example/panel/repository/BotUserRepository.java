package com.example.panel.repository;

import com.example.panel.entity.BotUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class BotUserRepository {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private final JdbcTemplate jdbcTemplate;

    public BotUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BotUser> findAll() {
        return jdbcTemplate.query("""
                SELECT
                    m.user_id,
                    m.username,
                    (
                        SELECT client_name
                        FROM messages
                        WHERE user_id = m.user_id AND client_name IS NOT NULL AND client_name != ''
                        ORDER BY created_at DESC
                        LIMIT 1
                    ) AS client_name,
                    MIN(m.created_at) AS first_contact,
                    MAX(m.created_at) AS last_contact
                FROM messages m
                GROUP BY m.user_id
                ORDER BY last_contact DESC
                """,
            this::mapRow);
    }

    private BotUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        BotUser user = new BotUser();
        user.setUserId(rs.getLong("user_id"));
        String username = rs.getString("username");
        String clientName = rs.getString("client_name");
        user.setUsername(username);
        user.setFirstName(StringUtils.hasText(clientName) ? clientName : username);
        user.setLastName(null);
        user.setRegisteredAt(parseOffsetDateTime(rs.getString("first_contact")));
        return user;
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                LocalDateTime local = LocalDateTime.parse(value, formatter);
                return local.atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
