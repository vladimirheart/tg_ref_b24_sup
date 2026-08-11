package com.example.panel.service;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.config.UsersSqliteDataSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class DatabaseHealthService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthService.class);

    private final JdbcTemplate sqliteJdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;
    private final String sqlitePath;
    private final String usersDbPath;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;
    private final String runtimeDescription;

    public DatabaseHealthService(JdbcTemplate jdbcTemplate,
                                 @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                 SqliteDataSourceProperties sqliteProperties,
                                 UsersSqliteDataSourceProperties usersProperties,
                                 PanelDatabaseRuntimeMode databaseRuntimeMode) {
        this.sqliteJdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.sqlitePath = sqliteProperties.getNormalizedPath().toString();
        this.usersDbPath = usersProperties.getNormalizedPath().toString();
        this.databaseRuntimeMode = databaseRuntimeMode;
        this.runtimeDescription = databaseRuntimeMode.externalSettings()
            .map(settings -> settings.jdbcUrl())
            .orElse(this.sqlitePath);
        if (databaseRuntimeMode.isSqliteMode()) {
            log.info("Spring panel is using SQLite database at: {} (tickets) and {} (users)",
                this.sqlitePath, this.usersDbPath);
        } else {
            log.info("Spring panel is using external {} database at {}", databaseRuntimeMode.modeLabel(), runtimeDescription);
        }
    }

    public String databasePath() {
        return runtimeDescription;
    }

    public Optional<String> detectProblem() {
        if (!databaseRuntimeMode.isSqliteMode()) {
            if (!canReadTicketsDb(null)) {
                return Optional.of("Не удалось прочитать основные таблицы панели во внешней БД " + runtimeDescription);
            }
            if (!canReadUsersDb(null)) {
                return Optional.of("Не удалось прочитать users/auth таблицы во внешней БД " + runtimeDescription);
            }
            return Optional.empty();
        }

        Path path = Path.of(sqlitePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return Optional.of("Файл базы данных не найден по пути " + path);
        }

        if (!canReadTicketsDb(path)) {
            return Optional.of("Не удалось прочитать таблицы тикетов в базе " + path);
        }

        Path usersPath = Path.of(usersDbPath).toAbsolutePath().normalize();
        if (!Files.exists(usersPath)) {
            return Optional.of("Файл базы пользователей не найден по пути " + usersPath);
        }

        if (!canReadUsersDb(usersPath)) {
            return Optional.of("Не удалось прочитать таблицы пользователей в базе " + usersPath);
        }

        return Optional.empty();
    }

    private boolean canReadTicketsDb(Path path) {
        try {
            sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class);
            sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages", Integer.class);
            return true;
        } catch (DataAccessException ex) {
            log.warn("Ticket database at {} is not readable: {}", path != null ? path : runtimeDescription, ex.getMessage());
            return false;
        }
    }

    private boolean canReadUsersDb(Path usersPath) {
        try {
            usersJdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            usersJdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_authorities", Integer.class);
            return true;
        } catch (DataAccessException ex) {
            log.warn("Users database at {} is not readable: {}", usersPath != null ? usersPath : runtimeDescription, ex.getMessage());
            return false;
        }
    }
}
