package com.example.panel.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);
    private static final String LEGACY_CLIENT_PHONES_VERSION = "6.1";
    private static final String LEGACY_CLIENT_PHONES_SCRIPT = "db.migration.V6_1__fix_client_phones_schema";

    @Bean
    public FlywayMigrationStrategy normalizeLegacyHistoryBeforeMigrate() {
        return flyway -> {
            normalizeSchemaHistory(flyway);
            flyway.migrate();
        };
    }

    private void normalizeSchemaHistory(Flyway flyway) {
        Configuration configuration = flyway.getConfiguration();
        DataSource dataSource = configuration.getDataSource();
        if (dataSource == null) {
            return;
        }

        String schemaHistoryTable = configuration.getTable();
        String deleteLegacyClientPhonesSql =
            "DELETE FROM " + schemaHistoryTable + " WHERE version = ? AND script = ?";
        String deleteRedundantDeleteMarkersSql =
            "DELETE FROM " + schemaHistoryTable + " AS deleted " +
                "WHERE deleted.type = 'DELETE' " +
                "AND deleted.version IS NOT NULL " +
                "AND EXISTS (" +
                "    SELECT 1 FROM " + schemaHistoryTable + " AS applied " +
                "    WHERE applied.version = deleted.version " +
                "      AND applied.script = deleted.script " +
                "      AND applied.success = 1 " +
                "      AND applied.type <> 'DELETE'" +
                ")";

        try (Connection connection = dataSource.getConnection()) {
            int removedLegacyRows;
            try (PreparedStatement statement = connection.prepareStatement(deleteLegacyClientPhonesSql)) {
                statement.setString(1, LEGACY_CLIENT_PHONES_VERSION);
                statement.setString(2, LEGACY_CLIENT_PHONES_SCRIPT);
                removedLegacyRows = statement.executeUpdate();
            }

            int removedDeleteMarkers;
            try (PreparedStatement statement = connection.prepareStatement(deleteRedundantDeleteMarkersSql)) {
                removedDeleteMarkers = statement.executeUpdate();
            }

            if (removedLegacyRows > 0 || removedDeleteMarkers > 0) {
                logger.warn(
                    "Normalized Flyway schema history before migrate: removed {} legacy V6.1 rows and {} redundant DELETE markers.",
                    removedLegacyRows,
                    removedDeleteMarkers
                );
            }
        } catch (SQLException ex) {
            logger.warn("Unable to normalize Flyway schema history before migrate.", ex);
        }
    }
}
