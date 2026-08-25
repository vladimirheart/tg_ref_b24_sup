package com.example.panel.security;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityBootstrap {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;
    private final PanelSecurityProperties securityProperties;

    public SecurityBootstrap(
        @org.springframework.beans.factory.annotation.Qualifier("usersJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplate,
        PasswordEncoder passwordEncoder,
        PanelDatabaseRuntimeMode databaseRuntimeMode,
        PanelSecurityProperties securityProperties
    ) {
        this.jdbcTemplate = jdbcTemplate.getIfAvailable();
        this.passwordEncoder = passwordEncoder;
        this.databaseRuntimeMode = databaseRuntimeMode;
        this.securityProperties = securityProperties;
    }

    public void ensureDefaultAdmin() {
        if (jdbcTemplate == null) {
            return;
        }

        ensureAuthoritiesTable();

        Long adminId = findExistingAdminUserId();
        if (adminId == null) {
            adminId = ensureBootstrapAdminUser();
        }

        ensureAdminAuthorities(adminId);
        ensurePortalAdminRole();
    }

    private void ensureAuthoritiesTable() {
        if (!databaseRuntimeMode.isSqliteMode()) {
            try {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_authorities", Integer.class);
                return;
            } catch (DataAccessException ex) {
                throw new IllegalStateException(
                    "user_authorities must be created by Flyway before spring-panel starts in external "
                        + databaseRuntimeMode.modeLabel() + " mode",
                    ex
                );
            }
        }

        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_authorities", Integer.class);
            return;
        } catch (DataAccessException ignored) {
            // SQLite compatibility mode may bootstrap this table on first run.
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_authorities (
                user_id BIGINT NOT NULL,
                authority TEXT NOT NULL,
                PRIMARY KEY(user_id, authority),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_user_authorities_user_id
            ON user_authorities(user_id);
        """);
    }

    private Long findExistingAdminUserId() {
        List<Long> ids = jdbcTemplate.query(
            "SELECT DISTINCT user_id FROM user_authorities WHERE authority = ? ORDER BY user_id LIMIT 1",
            (rs, rowNum) -> rs.getLong("user_id"),
            "ROLE_ADMIN"
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long ensureBootstrapAdminUser() {
        BootstrapAdminCredentials credentials = resolveBootstrapAdminCredentials();
        Long existingId = loadUserIdByUsername(credentials.username());
        if (existingId != null) {
            return existingId;
        }

        String encoded = passwordEncoder.encode(credentials.password());
        if (hasUsersColumn("enabled")) {
            jdbcTemplate.update(
                "INSERT INTO users(username, password, enabled) VALUES(?, ?, ?)",
                credentials.username(), encoded, true
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO users(username, password) VALUES(?, ?)",
                credentials.username(), encoded
            );
        }

        Long id = loadUserIdByUsername(credentials.username());
        if (id == null) {
            throw new IllegalStateException("Failed to create or load bootstrap admin user");
        }

        return id;
    }

    private Long loadUserIdByUsername(String username) {
        List<Long> ids = jdbcTemplate.query(
            "SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 1",
            (rs, rowNum) -> rs.getLong("id"),
            username
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private BootstrapAdminCredentials resolveBootstrapAdminCredentials() {
        PanelSecurityProperties.BootstrapAdmin bootstrapAdmin = securityProperties.getBootstrapAdmin();
        String configuredUsername = trimToNull(bootstrapAdmin.getUsername());
        String configuredPassword = trimToNull(bootstrapAdmin.getPassword());

        if (StringUtils.hasText(configuredUsername) ^ StringUtils.hasText(configuredPassword)) {
            throw new IllegalStateException(
                "Для bootstrap admin необходимо задать одновременно APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME "
                    + "и APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD."
            );
        }

        if (StringUtils.hasText(configuredUsername) && StringUtils.hasText(configuredPassword)) {
            if (!databaseRuntimeMode.isSqliteMode()
                && "admin".equalsIgnoreCase(configuredUsername)
                && "admin".equals(configuredPassword)) {
                throw new IllegalStateException(
                    "Во внешнем production-like режиме bootstrap admin не может использовать пару admin/admin."
                );
            }
            return new BootstrapAdminCredentials(configuredUsername, configuredPassword);
        }

        if (databaseRuntimeMode.isSqliteMode() && bootstrapAdmin.isAllowDefaultCredentialsInSqlite()) {
            return new BootstrapAdminCredentials("admin", "admin");
        }

        throw new IllegalStateException(
            "Не найден ROLE_ADMIN пользователь, а bootstrap credentials не заданы. "
                + "Укажите APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME и APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD "
                + "перед запуском внешнего production-like контура."
        );
    }

    private void ensureAdminAuthorities(long userId) {
        List<String> required = List.of(
            "ROLE_ADMIN",
            "PAGE_DIALOGS",
            "PAGE_ANALYTICS",
            "PAGE_CLIENTS",
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
        } catch (DataAccessException ex) {
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
                    "INSERT INTO user_authorities(user_id, authority) VALUES(?, ?)",
                    userId, auth
                );
            }
        }
    }

    private void ensurePortalAdminRole() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE lower(name) = lower(?)",
                Integer.class,
                "Администратор портала"
            );
            if (count != null && count > 0) {
                return;
            }
            String permissionsJson = """
                {"pages":["*"],"fields":{"edit":["*"],"view":["*"]}}
                """.trim();
            jdbcTemplate.update(
                "INSERT INTO roles(name, description, permissions) VALUES (?, ?, ?)",
                "Администратор портала",
                "Полный доступ к панели и заявкам на восстановление пароля",
                permissionsJson
            );
        } catch (DataAccessException ignored) {
            // roles table may be absent in bootstrap edge cases
        }
    }

    private boolean hasUsersColumn(String columnName) {
        try {
            return jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
                try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, null, null)) {
                    while (resultSet.next()) {
                        String tableName = resultSet.getString("TABLE_NAME");
                        String foundColumn = resultSet.getString("COLUMN_NAME");
                        if ("users".equalsIgnoreCase(tableName) && columnName.equalsIgnoreCase(foundColumn)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record BootstrapAdminCredentials(String username, String password) {
    }
}
