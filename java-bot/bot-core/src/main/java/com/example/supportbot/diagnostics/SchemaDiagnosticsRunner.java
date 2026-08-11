package com.example.supportbot.diagnostics;

import com.example.supportbot.support.JdbcSchemaInspector;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
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
            inspectSchema(connection);
        } catch (Exception ex) {
            log.warn("DB diagnostics failed: {}", ex.getMessage(), ex);
        }
    }

    private void inspectSchema(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>(JdbcSchemaInspector.loadTableNames(connection));
        if (tables.isEmpty()) {
            log.warn("DB diagnostics: no tables found via JDBC metadata.");
            return;
        }
        for (String table : tables) {
            inspectTable(connection, table);
        }
    }

    private void inspectTable(Connection connection, String table) throws Exception {
        boolean anomaly = false;
        List<String> issues = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
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
            log.warn("DB diagnostics: unable to resolve a clean metadata view for table {}", table);
        }
    }
}
