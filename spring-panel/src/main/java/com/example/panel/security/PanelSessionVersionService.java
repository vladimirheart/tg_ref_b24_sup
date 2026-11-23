package com.example.panel.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Checks long-lived streams against the same durable revocation state as HTTP requests. */
@Service
public class PanelSessionVersionService {
    private final JdbcTemplate jdbc;

    public PanelSessionVersionService(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isCurrent(long userId, String username, long expectedVersion) {
        if (userId <= 0 || username == null || expectedVersion < 0) return false;
        try {
            Long current = jdbc.queryForObject("SELECT session_version FROM users WHERE id = ? AND username = ?",
                Long.class, userId, username);
            return current != null && current == expectedVersion;
        } catch (DataAccessException unavailable) {
            return false;
        }
    }
}
