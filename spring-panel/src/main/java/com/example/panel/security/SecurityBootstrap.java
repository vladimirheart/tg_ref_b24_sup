package com.example.panel.security;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityBootstrap {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public SecurityBootstrap(
            @org.springframework.beans.factory.annotation.Qualifier("usersJdbcTemplate") JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public void ensureDefaultAdmin() {
        // 1) гарантируем таблицу authorities (важно для "старых" users.db, например от Flask)
        ensureAuthoritiesTable();

        // 2) гарантируем наличие admin
        long adminId = ensureAdminUser();

        // 3) гарантируем набор прав admin
        ensureAdminAuthorities(adminId);
    }

    /**
     * Создаёт таблицу user_authorities, если её ещё нет.
     * Это защищает "чистый развёртывание" и сценарий, когда users.db пришёл от Flask
     * (где есть users, но нет user_authorities).
     */
    private void ensureAuthoritiesTable() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='user_authorities'",
                    Integer.class
            );
            if (exists != null && exists > 0) {
                return;
            }
        } catch (DataAccessException ignored) {
            // если sqlite_master недоступен/другой диалект — всё равно попробуем CREATE IF NOT EXISTS ниже
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_authorities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                authority TEXT NOT NULL,
                UNIQUE(user_id, authority),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_user_authorities_user_id
            ON user_authorities(user_id);
        """);
    }

    private long ensureAdminUser() {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM users WHERE lower(username) = 'admin' LIMIT 1",
                (rs, rowNum) -> rs.getLong("id")
        );

        if (!ids.isEmpty()) {
            return ids.get(0);
        }

        // если админа нет — создаём
        String encoded = passwordEncoder.encode("admin");
        jdbcTemplate.update(
                "INSERT INTO users(username, password, enabled) VALUES(?, ?, 1)",
                "admin", encoded
        );

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE lower(username) = 'admin' LIMIT 1",
                Long.class
        );

        if (id == null) {
            throw new IllegalStateException("Failed to create or load default admin user");
        }

        return id;
    }

    private void ensureAdminAuthorities(long userId) {
        List<String> required = List.of(
                "ROLE_ADMIN",
                "PAGE_DIALOGS",
                "PAGE_ANALYTICS",
                "PAGE_OBJECT_PASSPORTS",
                "PAGE_CHANNELS",
                "PAGE_USERS",
                "PAGE_SETTINGS",
                "PAGE_TASKS",
                "PAGE_KNOWLEDGE_BASE"
        );

        Set<String> existing;
        try {
            existing = jdbcTemplate.query(
                    "SELECT authority FROM user_authorities WHERE user_id = ?",
                    (rs, rowNum) -> rs.getString("authority"),
                    userId
            ).stream().collect(Collectors.toSet());
        } catch (DataAccessException e) {
            // например: "no such table: user_authorities"
            ensureAuthoritiesTable();
            existing = jdbcTemplate.query(
                    "SELECT authority FROM user_authorities WHERE user_id = ?",
                    (rs, rowNum) -> rs.getString("authority"),
                    userId
            ).stream().collect(Collectors.toSet());
        }

        for (String auth : required) {
            if (!existing.contains(auth)) {
                jdbcTemplate.update(
                        "INSERT OR IGNORE INTO user_authorities(user_id, authority) VALUES(?, ?)",
                        userId, auth
                );
            }
        }
    }
}
