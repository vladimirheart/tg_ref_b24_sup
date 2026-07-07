package com.example.panel.service;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

@Component
public class SqliteSchemaBootstrapSupport {

    public void initializeSchema(DataSource dataSource, List<String> statements, String schemaName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("""
                CREATE TABLE IF NOT EXISTS schema_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    applied_at TEXT NOT NULL
                )
                """);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize SQLite schema for " + schemaName, ex);
        }
    }
}
