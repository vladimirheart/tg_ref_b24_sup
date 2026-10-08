package com.example.panel.runtime;

import com.example.panel.support.JdbcSchemaInspector;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UsersProfileSchemaParityMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/postgresql/V49__users_profile_schema_parity.sql"
    );

    @Test
    void migrationDefinesOnlyActiveProfileParityFields() throws Exception {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");

        assertThat(sql)
                .contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS registration_date TIMESTAMP WITH TIME ZONE;")
                .contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date DATE;")
                .contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT;")
                .contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS department TEXT;")
                .contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS phones TEXT;")
                .contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name TEXT;")
                .contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN NOT NULL DEFAULT FALSE;")
                .contains("SET registration_date = created_at")
                .doesNotContain("ALTER TABLE panel_users")
                .doesNotContain("ADD COLUMN IF NOT EXISTS password_hash")
                .doesNotContain("ADD COLUMN IF NOT EXISTS role TEXT")
                .doesNotContain("DROP COLUMN");
    }

    @Test
    void migrationAppliesIdempotentlyOnProductionShapedPostgresqlUsersTable() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("users_profile_v49");
        jdbc.execute("""
                CREATE TABLE users (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    username VARCHAR(255) NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                    role_id BIGINT,
                    last_portal_activity_at TIMESTAMP WITH TIME ZONE,
                    photo TEXT
                )
                """);
        jdbc.update("""
                INSERT INTO users(username, password, enabled, created_at, role_id, photo)
                VALUES (?, ?, TRUE, CAST(? AS TIMESTAMPTZ), ?, ?)
                """, "krukov", "{noop}secret", "2026-01-02T03:04:05Z", 7L, "/avatars/k.png");

        applyMigration(jdbc);
        applyMigration(jdbc);

        Set<String> columns = JdbcSchemaInspector.loadColumnNames(jdbc, "users");
        assertThat(columns).contains(
                "id", "username", "password", "enabled", "created_at", "role_id",
                "last_portal_activity_at", "photo", "registration_date", "birth_date",
                "email", "department", "phones", "full_name", "is_blocked"
        );

        OffsetDateTime createdAt = jdbc.queryForObject(
                "SELECT created_at FROM users WHERE username = ?", OffsetDateTime.class, "krukov"
        );
        OffsetDateTime registrationDate = jdbc.queryForObject(
                "SELECT registration_date FROM users WHERE username = ?", OffsetDateTime.class, "krukov"
        );
        Boolean blocked = jdbc.queryForObject(
                "SELECT is_blocked FROM users WHERE username = ?", Boolean.class, "krukov"
        );
        String password = jdbc.queryForObject(
                "SELECT password FROM users WHERE username = ?", String.class, "krukov"
        );
        Long roleId = jdbc.queryForObject(
                "SELECT role_id FROM users WHERE username = ?", Long.class, "krukov"
        );
        String photo = jdbc.queryForObject(
                "SELECT photo FROM users WHERE username = ?", String.class, "krukov"
        );

        assertThat(registrationDate).isEqualTo(createdAt);
        assertThat(blocked).isFalse();
        assertThat(password).isEqualTo("{noop}secret");
        assertThat(roleId).isEqualTo(7L);
        assertThat(photo).isEqualTo("/avatars/k.png");
    }

    private void applyMigration(JdbcTemplate jdbc) throws Exception {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbc.execute(trimmed);
            }
        }
    }
}
