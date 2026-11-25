package com.example.panel.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AccessRecoveryTestDatabase {
    public static JdbcTemplate fresh() {
        JdbcTemplate jdbc = base();
        migrate(jdbc);
        return jdbc;
    }
    public static JdbcTemplate base() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("access_recovery");
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY, name TEXT, permissions TEXT)");
        jdbc.execute("CREATE TABLE users(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, username VARCHAR(255) UNIQUE NOT NULL, password TEXT NOT NULL, enabled BOOLEAN DEFAULT TRUE, is_blocked BOOLEAN DEFAULT FALSE, session_version BIGINT DEFAULT 0, role_id BIGINT REFERENCES roles(id))");
        jdbc.execute("CREATE TABLE user_authorities(user_id BIGINT REFERENCES users(id), authority TEXT)");
        jdbc.execute("CREATE TABLE client_unblock_requests(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, status TEXT NOT NULL, user_id TEXT, channel_id BIGINT, reason TEXT, created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, decided_at TIMESTAMPTZ, decided_by TEXT, decision_comment TEXT)");
        jdbc.execute("""
                CREATE TABLE password_reset_requests(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, username_snapshot VARCHAR(255) NOT NULL,
                    requested_by_username VARCHAR(255), requested_by_ip VARCHAR(255), requested_user_agent TEXT,
                    requested_note TEXT, status TEXT NOT NULL DEFAULT 'PENDING', resolution_note TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, resolved_at TIMESTAMPTZ,
                    resolved_by_username VARCHAR(255))
                """);
        return jdbc;
    }
    public static void migrate(JdbcTemplate jdbc) {
        try {
            String sql = Files.readString(Path.of("src/main/resources/db/migration/postgresql/V104__access_recovery_attention.sql"));
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private AccessRecoveryTestDatabase() {}
}
