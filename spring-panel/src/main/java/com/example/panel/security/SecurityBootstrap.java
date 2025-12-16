package com.example.panel.security;

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

    public SecurityBootstrap(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    // Вызови это из твоего init-места (как было раньше)
    public void ensureDefaultAdmin() {
        long adminId = ensureAdminUser();
        ensureAdminAuthorities(adminId);
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

        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE lower(username) = 'admin' LIMIT 1",
                Long.class
        );
    }

    private void ensureAdminAuthorities(long userId) {
        // полный набор прав, чтобы все страницы открывались
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

        Set<String> existing = jdbcTemplate.query(
                "SELECT authority FROM user_authorities WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("authority"),
                userId
        ).stream().collect(Collectors.toSet());

        for (String auth : required) {
            if (!existing.contains(auth)) {
                jdbcTemplate.update(
                        "INSERT INTO user_authorities(user_id, authority) VALUES(?, ?)",
                        userId, auth
                );
            }
        }
    }
}
