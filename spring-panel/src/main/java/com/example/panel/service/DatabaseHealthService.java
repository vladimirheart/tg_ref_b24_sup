package com.example.panel.service;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DatabaseHealthService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthService.class);

    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;
    private final String runtimeDescription;

    public DatabaseHealthService(JdbcTemplate jdbcTemplate,
                                 @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                 PanelDatabaseRuntimeMode databaseRuntimeMode) {
        this.primaryJdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.runtimeDescription = databaseRuntimeMode.externalSettings()
            .map(settings -> settings.jdbcUrl())
            .orElseThrow(() -> new IllegalStateException("spring-panel database health requires external datasource settings"));
        log.info("Spring panel is using external {} database at {}", databaseRuntimeMode.modeLabel(), runtimeDescription);
    }

    public String databasePath() {
        return runtimeDescription;
    }

    public Optional<String> detectProblem() {
        if (!canReadRuntimeTables()) {
            return Optional.of("Не удалось прочитать основные таблицы панели во внешней БД " + runtimeDescription);
        }
        if (!canReadIdentityTables()) {
            return Optional.of("Не удалось прочитать users/auth таблицы во внешней БД " + runtimeDescription);
        }
        return Optional.empty();
    }

    private boolean canReadRuntimeTables() {
        try {
            primaryJdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class);
            primaryJdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages", Integer.class);
            return true;
        } catch (DataAccessException ex) {
            log.warn("Runtime database at {} is not readable: {}", runtimeDescription, ex.getMessage());
            return false;
        }
    }

    private boolean canReadIdentityTables() {
        try {
            usersJdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            usersJdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_authorities", Integer.class);
            return true;
        } catch (DataAccessException ex) {
            log.warn("Identity tables in database at {} are not readable: {}", runtimeDescription, ex.getMessage());
            return false;
        }
    }
}
