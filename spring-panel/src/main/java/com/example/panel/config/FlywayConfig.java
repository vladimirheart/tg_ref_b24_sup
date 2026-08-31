package com.example.panel.config;

import com.example.panel.runtime.RuntimeRole;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);
    private static final String BASELINE_VERSION = "1";
    private static final String BASELINE_SCRIPT = "V1__baseline_schema.sql";
    private static final String LEGACY_CLIENT_PHONES_VERSION = "6.1";
    private static final String LEGACY_CLIENT_PHONES_SCRIPT = "db.migration.V6_1__fix_client_phones_schema";
    private static final String CLIENT_PHONES_SQLITE_OLD_VERSION = "37";
    private static final String CLIENT_PHONES_SQLITE_NEW_VERSION = "37.1";
    private static final String CLIENT_PHONES_SQLITE_OLD_SCRIPT = "db.migration.sqlite.V37__fix_client_phones_schema";
    private static final String CLIENT_PHONES_SQLITE_NEW_SCRIPT = "db.migration.sqlite.V37_1__fix_client_phones_schema";

    @Bean
    public FlywayConfigurationCustomizer databaseSpecificFlywayLocations(Environment environment) {
        return configuration -> {
            String location = resolveFlywayLocation(environment);
            configuration.locations(location);
            logger.info("Configured Flyway migration location: {}", location);
        };
    }

    @Bean
    public FlywayMigrationStrategy normalizeLegacyHistoryBeforeMigrate(Environment environment) {
        return flyway -> {
            RuntimeRole runtimeRole = RuntimeRole.from(
                environment.getProperty("app.runtime.role", "all")
            );
            if (runtimeRole == RuntimeRole.WEB || runtimeRole == RuntimeRole.WORKER) {
                logger.info(
                    "Skipping Flyway migration for runtime role '{}'; schema ownership belongs to all/db-migrate.",
                    runtimeRole.externalName()
                );
                return;
            }

            // Legacy checksum/history cleanup is performed only by the migration owner.
            // Do not run flyway.repair() unconditionally: it hides real repair events.
            normalizeSchemaHistory(flyway);
            flyway.migrate();
        };
    }

    private String resolveFlywayLocation(Environment environment) {
        DatabaseMode requestedMode = DatabaseMode.from(environment.getProperty("app.datasource.mode"));
        if (requestedMode == DatabaseMode.SQLITE) {
            throw new IllegalStateException(
                "spring-panel Flyway no longer supports SQLite runtime mode. Configure an external PostgreSQL/MySQL datasource."
            );
        }
        if (requestedMode == DatabaseMode.POSTGRESQL) {
            return "classpath:db/migration/postgresql";
        }
        if (requestedMode == DatabaseMode.MYSQL) {
            return "classpath:db/migration/mysql";
        }

        String databaseUrl = environment.getProperty("spring.datasource.url");
        if (!StringUtils.hasText(databaseUrl)) {
            databaseUrl = environment.getProperty("DATABASE_URL");
        }
        if (!StringUtils.hasText(databaseUrl)) {
            throw new IllegalStateException(
                "Unable to resolve Flyway migration location without spring.datasource.url or DATABASE_URL."
            );
        }

        String normalized = databaseUrl.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:postgresql:")
            || normalized.startsWith("postgresql:")
            || normalized.startsWith("postgres:")) {
            return "classpath:db/migration/postgresql";
        }
        if (normalized.startsWith("jdbc:mysql:") || normalized.startsWith("mysql:")) {
            return "classpath:db/migration/mysql";
        }
        throw new IllegalStateException(
            "Unsupported datasource URL for Flyway migration location resolution: " + databaseUrl
        );
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
        String migrateClientPhonesVersionSql =
            "UPDATE " + schemaHistoryTable + " SET version = ?, script = ? " +
                "WHERE version = ? AND script = ? AND success = TRUE";
        String updateBaselineChecksumSql =
            "UPDATE " + schemaHistoryTable + " SET checksum = ? " +
                "WHERE version = ? AND script = ? AND success = TRUE AND (checksum IS NULL OR checksum <> ?)";
        String deleteRedundantDeleteMarkersSql =
            "DELETE FROM " + schemaHistoryTable + " AS deleted " +
                "WHERE deleted.type = 'DELETE' " +
                "AND deleted.version IS NOT NULL " +
                "AND EXISTS (" +
                "    SELECT 1 FROM " + schemaHistoryTable + " AS applied " +
                "    WHERE applied.version = deleted.version " +
                "      AND applied.script = deleted.script " +
                "      AND applied.success = TRUE " +
                "      AND applied.type <> 'DELETE'" +
                ")";

        try (Connection connection = dataSource.getConnection()) {
            if (!schemaHistoryTableExists(connection, schemaHistoryTable)) {
                logger.debug(
                    "Flyway schema history table '{}' does not exist yet; skipping legacy history normalization.",
                    schemaHistoryTable
                );
                return;
            }

            int removedLegacyRows;
            try (PreparedStatement statement = connection.prepareStatement(deleteLegacyClientPhonesSql)) {
                statement.setString(1, LEGACY_CLIENT_PHONES_VERSION);
                statement.setString(2, LEGACY_CLIENT_PHONES_SCRIPT);
                removedLegacyRows = statement.executeUpdate();
            }

            int migratedClientPhonesRows;
            try (PreparedStatement statement = connection.prepareStatement(migrateClientPhonesVersionSql)) {
                statement.setString(1, CLIENT_PHONES_SQLITE_NEW_VERSION);
                statement.setString(2, CLIENT_PHONES_SQLITE_NEW_SCRIPT);
                statement.setString(3, CLIENT_PHONES_SQLITE_OLD_VERSION);
                statement.setString(4, CLIENT_PHONES_SQLITE_OLD_SCRIPT);
                migratedClientPhonesRows = statement.executeUpdate();
            }

            int repairedBaselineChecksums = 0;
            Integer resolvedBaselineChecksum = resolveChecksum(flyway, BASELINE_VERSION, BASELINE_SCRIPT);
            if (resolvedBaselineChecksum != null) {
                try (PreparedStatement statement = connection.prepareStatement(updateBaselineChecksumSql)) {
                    statement.setInt(1, resolvedBaselineChecksum);
                    statement.setString(2, BASELINE_VERSION);
                    statement.setString(3, BASELINE_SCRIPT);
                    statement.setInt(4, resolvedBaselineChecksum);
                    repairedBaselineChecksums = statement.executeUpdate();
                }
            } else {
                logger.warn(
                    "Unable to resolve local Flyway checksum for {} {} before migrate. Skipping legacy checksum normalization.",
                    BASELINE_VERSION,
                    BASELINE_SCRIPT
                );
            }

            int removedDeleteMarkers;
            try (PreparedStatement statement = connection.prepareStatement(deleteRedundantDeleteMarkersSql)) {
                removedDeleteMarkers = statement.executeUpdate();
            }

            if (removedLegacyRows > 0 || migratedClientPhonesRows > 0 || repairedBaselineChecksums > 0 || removedDeleteMarkers > 0) {
                logger.info(
                    "Normalized Flyway schema history: removed {} legacy V6.1 rows, remapped {} SQLite client_phones rows to version 37.1, repaired {} baseline V1 checksums and removed {} redundant DELETE markers.",
                    removedLegacyRows,
                    migratedClientPhonesRows,
                    repairedBaselineChecksums,
                    removedDeleteMarkers
                );
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException ex) {
            logger.warn("Unable to normalize existing Flyway schema history before migrate.", ex);
        }
    }

    private boolean schemaHistoryTableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(tableName);
        candidates.add(tableName.toLowerCase(Locale.ROOT));
        candidates.add(tableName.toUpperCase(Locale.ROOT));

        String catalog = connection.getCatalog();
        String schema = null;
        try {
            schema = connection.getSchema();
        } catch (SQLException | AbstractMethodError ignored) {
            // Some JDBC drivers do not expose a current schema.
        }

        for (String candidate : candidates) {
            if (tableExists(metadata, catalog, schema, candidate)) {
                return true;
            }
            if (schema != null && tableExists(metadata, catalog, null, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean tableExists(DatabaseMetaData metadata,
                                String catalog,
                                String schema,
                                String tableName) throws SQLException {
        try (ResultSet tables = metadata.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
            return tables.next();
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
}
