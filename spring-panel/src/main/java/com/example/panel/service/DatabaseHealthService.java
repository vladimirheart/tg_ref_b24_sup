package com.example.panel.service;

import com.example.panel.config.SqliteDataSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class DatabaseHealthService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final String sqlitePath;

    public DatabaseHealthService(JdbcTemplate jdbcTemplate, SqliteDataSourceProperties sqliteProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlitePath = sqliteProperties.getNormalizedPath().toString();
        log.info("Spring panel is using SQLite database at: {}", this.sqlitePath);
    }

    public String databasePath() {
        return sqlitePath;
    }

    public Optional<String> detectProblem() {
        Path path = Path.of(sqlitePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return Optional.of("Файл базы данных не найден по пути " + path);
        }

        try {
            jdbcTemplate.queryForObject("SELECT name FROM sqlite_master WHERE type='table' LIMIT 1", String.class);
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_authorities", Integer.class);
        } catch (DataAccessException ex) {
            return Optional.of("Не удалось прочитать таблицы пользователей в базе " + path
                    + " (" + ex.getClass().getSimpleName() + ")");
        }

        return Optional.empty();
    }
}
