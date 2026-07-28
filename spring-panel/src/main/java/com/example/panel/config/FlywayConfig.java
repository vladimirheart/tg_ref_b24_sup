package com.example.panel.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);
    private static final String BASELINE_VERSION = "1";
    private static final String BASELINE_SCRIPT = "V1__baseline_schema.sql";
    private static final int BASELINE_FALLBACK_CHECKSUM = 823443258;
    private static final String LEGACY_CLIENT_PHONES_VERSION = "6.1";
    private static final String LEGACY_CLIENT_PHONES_SCRIPT = "db.migration.V6_1__fix_client_phones_schema";

    @Bean
    public FlywayMigrationStrategy normalizeLegacyHistoryBeforeMigrate() {
        return flyway -> {
            normalizeSchemaHistory(flyway);
            repairBaselineChecksumMismatchIfNeeded(flyway);
            flyway.migrate();
        };
    }

    private void normalizeSchemaHistory(Flyway flyway) {
        org.flywaydb.core.api.configuration.Configuration configuration = flyway.getConfiguration();
        DataSource dataSource = configuration.getDataSource();
        if (dataSource == null) {
            return;
        }

        String schemaHistoryTable = configuration.getTable();
        String deleteLegacyClientPhonesSql =
            "DELETE FROM " + schemaHistoryTable + " WHERE version = ? AND script = ?";
        String updateBaselineChecksumSql =
            "UPDATE " + schemaHistoryTable + " SET checksum = ? " +
                "WHERE version = ? AND script = ? AND success = 1 AND (checksum IS NULL OR checksum <> ?)";
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

            int repairedBaselineChecksums = 0;
            Integer resolvedBaselineChecksum = resolveChecksum(flyway, BASELINE_VERSION, BASELINE_SCRIPT);
            if (resolvedBaselineChecksum == null) {
                resolvedBaselineChecksum = BASELINE_FALLBACK_CHECKSUM;
            }
            if (resolvedBaselineChecksum != null) {
                try (PreparedStatement statement = connection.prepareStatement(updateBaselineChecksumSql)) {
                    statement.setInt(1, resolvedBaselineChecksum);
                    statement.setString(2, BASELINE_VERSION);
                    statement.setString(3, BASELINE_SCRIPT);
                    statement.setInt(4, resolvedBaselineChecksum);
                    repairedBaselineChecksums = statement.executeUpdate();
                }
            }

            int removedDeleteMarkers;
            try (PreparedStatement statement = connection.prepareStatement(deleteRedundantDeleteMarkersSql)) {
                removedDeleteMarkers = statement.executeUpdate();
            }

            if (removedLegacyRows > 0 || repairedBaselineChecksums > 0 || removedDeleteMarkers > 0) {
                logger.warn(
                    "Normalized Flyway schema history before migrate: removed {} legacy V6.1 rows, repaired {} baseline V1 checksums and removed {} redundant DELETE markers.",
                    removedLegacyRows,
                    repairedBaselineChecksums,
                    removedDeleteMarkers
                );
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException ex) {
            logger.warn("Unable to normalize Flyway schema history before migrate.", ex);
        }
    }

    private Integer resolveChecksum(Flyway flyway, String version, String script) {
        for (MigrationInfo migrationInfo : flyway.info().all()) {
            if (migrationInfo == null || migrationInfo.getVersion() == null) {
                continue;
            }
            if (!version.equals(migrationInfo.getVersion().getVersion())) {
                continue;
            }
            if (!script.equals(migrationInfo.getScript())) {
                continue;
            }
            return migrationInfo.getChecksum();
        }
        return null;
    }

    private void repairBaselineChecksumMismatchIfNeeded(Flyway flyway) {
        org.flywaydb.core.api.configuration.Configuration configuration = flyway.getConfiguration();
        DataSource dataSource = configuration.getDataSource();
        if (dataSource == null) {
            return;
        }

        Integer resolvedBaselineChecksum = resolveChecksum(flyway, BASELINE_VERSION, BASELINE_SCRIPT);
        if (resolvedBaselineChecksum == null) {
            resolvedBaselineChecksum = BASELINE_FALLBACK_CHECKSUM;
        }

        String schemaHistoryTable = configuration.getTable();
        String mismatchCountSql =
            "SELECT COUNT(*) FROM " + schemaHistoryTable +
                " WHERE version = ? AND script = ? AND success = 1 AND (checksum IS NULL OR checksum <> ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(mismatchCountSql)) {
            statement.setString(1, BASELINE_VERSION);
            statement.setString(2, BASELINE_SCRIPT);
            statement.setInt(3, resolvedBaselineChecksum);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    logger.warn(
                        "Detected legacy Flyway baseline checksum mismatch for {} {}. Running Flyway repair before migrate.",
                        BASELINE_VERSION,
                        BASELINE_SCRIPT
                    );
                    flyway.repair();
                }
            }
        } catch (SQLException ex) {
            logger.warn("Unable to check legacy Flyway baseline checksum mismatch before migrate.", ex);
        }
    }
}
