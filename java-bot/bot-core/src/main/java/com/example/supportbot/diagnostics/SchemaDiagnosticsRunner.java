package com.example.supportbot.diagnostics;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "support-bot.db-diagnostics.enabled", havingValue = "true")
public class SchemaDiagnosticsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaDiagnosticsRunner.class);

    private final DataSource dataSource;

    public SchemaDiagnosticsRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            String product = connection.getMetaData().getDatabaseProductName();
            log.info("DB diagnostics enabled. JDBC URL: {}. Product: {}", jdbcUrl, product);
            if (jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlite:")) {
                inspectSqliteSchema(connection);
            } else {
                log.info("DB diagnostics skipped: unsupported JDBC URL {}", jdbcUrl);
            }
        } catch (Exception ex) {
            log.warn("DB diagnostics failed: {}", ex.getMessage(), ex);
        }
    }

    private void inspectSqliteSchema(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                String table = rs.getString("name");
                if (table != null && !table.isBlank()) {
                    tables.add(table);
                }
            }
        }
        if (tables.isEmpty()) {
            log.warn("DB diagnostics: no tables found in sqlite_master.");
            return;
        }
        for (String table : tables) {
            inspectSqliteTable(connection, table);
        }
    }

    private void inspectSqliteTable(Connection connection, String table) throws Exception {
        boolean anomaly = false;
        List<String> issues = new ArrayList<>();
        String sql = "PRAGMA table_info('" + table.replace("'", "''") + "')";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                if (name == null || name.isBlank()) {
                    anomaly = true;
                    issues.add("column with empty name");
                }
                if (type == null || type.isBlank()) {
                    anomaly = true;
                    issues.add("column " + (name == null ? "<null>" : name) + " has empty type");
                }
            }
        }
        if (anomaly) {
            log.warn("DB diagnostics: anomalies in table {}: {}", table, String.join("; ", issues));
            log.warn("DB diagnostics: schema for {} -> {}", table, loadSqliteTableDefinition(connection, table));
        }
    }

    private String loadSqliteTableDefinition(Connection connection, String table) {
        String sql = "SELECT sql FROM sqlite_master WHERE type='table' AND name='" + table.replace("'", "''") + "'";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString("sql");
            }
        } catch (Exception ex) {
            return "failed to read table definition: " + ex.getMessage();
        }
        return "<not found>";
    }
}
