package com.example.panel.service;

import com.example.panel.config.MonitoringSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.RmsLicenseMonitor;
import com.example.panel.entity.SslCertificateMonitor;
import com.example.panel.repository.IikoApiMonitorRepository;
import com.example.panel.repository.RmsLicenseMonitorRepository;
import com.example.panel.repository.SslCertificateMonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Order(120)
public class MonitoringDatabaseBootstrapService implements ApplicationRunner {
    // SQLite-only monitoring bootstrap/migration layer kept for local/dev compatibility.

    private static final Logger log = LoggerFactory.getLogger(MonitoringDatabaseBootstrapService.class);
    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate monitoringJdbcTemplate;
    private final IikoApiMonitorRepository iikoApiMonitorRepository;
    private final SslCertificateMonitorRepository sslRepository;
    private final RmsLicenseMonitorRepository rmsRepository;
    private final MonitoringCredentialsCryptoService credentialsCryptoService;
    private final SqliteDataSourceProperties primaryProperties;
    private final MonitoringSqliteDataSourceProperties monitoringProperties;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;

    public MonitoringDatabaseBootstrapService(JdbcTemplate primaryJdbcTemplate,
                                              @Qualifier("monitoringJdbcTemplate") JdbcTemplate monitoringJdbcTemplate,
                                              IikoApiMonitorRepository iikoApiMonitorRepository,
                                              SslCertificateMonitorRepository sslRepository,
                                              RmsLicenseMonitorRepository rmsRepository,
                                              MonitoringCredentialsCryptoService credentialsCryptoService,
                                              SqliteDataSourceProperties primaryProperties,
                                              MonitoringSqliteDataSourceProperties monitoringProperties,
                                              PanelDatabaseRuntimeMode databaseRuntimeMode) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.monitoringJdbcTemplate = monitoringJdbcTemplate;
        this.iikoApiMonitorRepository = iikoApiMonitorRepository;
        this.sslRepository = sslRepository;
        this.rmsRepository = rmsRepository;
        this.credentialsCryptoService = credentialsCryptoService;
        this.primaryProperties = primaryProperties;
        this.monitoringProperties = monitoringProperties;
        this.databaseRuntimeMode = databaseRuntimeMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!databaseRuntimeMode.isSqliteMode()) {
            log.info("Skipping SQLite monitoring bootstrap because spring-panel runs in external {} mode", databaseRuntimeMode.modeLabel());
            return;
        }
        ensureSchema();
        migrateFromPrimaryDatabase();
        migrateEncryptedRmsCredentials();
    }

    private void ensureSchema() {
        monitoringJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ssl_certificate_monitors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                site_name TEXT NOT NULL,
                endpoint_url TEXT NOT NULL,
                host TEXT NOT NULL,
                port INTEGER NOT NULL DEFAULT 443,
                enabled INTEGER NOT NULL DEFAULT 1,
                monitor_status TEXT,
                error_message TEXT,
                days_left INTEGER,
                expires_at TEXT,
                last_checked_at TEXT,
                last_notified_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """);
        monitoringJdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_ssl_certificate_monitors_endpoint
            ON ssl_certificate_monitors(endpoint_url)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_ssl_certificate_monitors_enabled
            ON ssl_certificate_monitors(enabled)
            """);

        monitoringJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS rms_license_monitors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rms_address TEXT NOT NULL,
                scheme TEXT NOT NULL DEFAULT 'https',
                host TEXT NOT NULL,
                port INTEGER NOT NULL DEFAULT 443,
                auth_login TEXT NOT NULL,
                auth_password TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                license_monitoring_enabled INTEGER NOT NULL DEFAULT 1,
                network_monitoring_enabled INTEGER NOT NULL DEFAULT 1,
                server_name TEXT,
                server_type TEXT,
                server_version TEXT,
                license_status TEXT,
                license_error_message TEXT,
                license_details_json TEXT,
                license_expires_at TEXT,
                license_days_left INTEGER,
                license_last_checked_at TEXT,
                license_last_notified_at TEXT,
                rms_status TEXT,
                rms_status_message TEXT,
                ping_output TEXT,
                traceroute_summary TEXT,
                traceroute_report TEXT,
                traceroute_checked_at TEXT,
                rms_last_checked_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                deleted_at TEXT
            )
            """);
        monitoringJdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_rms_license_monitors_address
            ON rms_license_monitors(rms_address)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_rms_license_monitors_enabled
            ON rms_license_monitors(enabled)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS rms_refresh_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                queue_kind TEXT NOT NULL,
                monitor_id INTEGER,
                with_notifications INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'queued',
                requested_at TEXT NOT NULL
            )
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_rms_refresh_queue_kind_status
            ON rms_refresh_queue(queue_kind, status, requested_at, id)
            """);
        ensureColumn(
            "rms_license_monitors",
            "license_monitoring_enabled",
            "ALTER TABLE rms_license_monitors ADD COLUMN license_monitoring_enabled INTEGER NOT NULL DEFAULT 1"
        );
        ensureColumn(
            "rms_license_monitors",
            "license_details_json",
            "ALTER TABLE rms_license_monitors ADD COLUMN license_details_json TEXT"
        );
        ensureColumn(
            "rms_license_monitors",
            "license_debug_excerpt",
            "ALTER TABLE rms_license_monitors ADD COLUMN license_debug_excerpt TEXT"
        );
        ensureColumn(
            "rms_license_monitors",
            "network_monitoring_enabled",
            "ALTER TABLE rms_license_monitors ADD COLUMN network_monitoring_enabled INTEGER NOT NULL DEFAULT 1"
        );
        ensureColumn(
            "rms_license_monitors",
            "is_deleted",
            "ALTER TABLE rms_license_monitors ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0"
        );
        ensureColumn(
            "rms_license_monitors",
            "deleted_at",
            "ALTER TABLE rms_license_monitors ADD COLUMN deleted_at TEXT"
        );

        monitoringJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS iiko_api_monitors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                monitor_name TEXT NOT NULL,
                base_url TEXT NOT NULL,
                api_login TEXT NOT NULL,
                request_type TEXT NOT NULL,
                request_config_json TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                locations_sync_enabled INTEGER NOT NULL DEFAULT 0,
                last_status TEXT,
                last_http_status INTEGER,
                last_error_message TEXT,
                last_duration_ms INTEGER,
                last_checked_at TEXT,
                last_token_checked_at TEXT,
                last_response_excerpt TEXT,
                last_response_summary_json TEXT,
                consecutive_failures INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_iiko_api_monitors_enabled
            ON iiko_api_monitors(enabled)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_iiko_api_monitors_request_type
            ON iiko_api_monitors(request_type)
            """);
        ensureColumn(
            "iiko_api_monitors",
            "request_config_json",
            "ALTER TABLE iiko_api_monitors ADD COLUMN request_config_json TEXT"
        );
        ensureColumn(
            "iiko_api_monitors",
            "last_response_summary_json",
            "ALTER TABLE iiko_api_monitors ADD COLUMN last_response_summary_json TEXT"
        );
        ensureColumn(
            "iiko_api_monitors",
            "locations_sync_enabled",
            "ALTER TABLE iiko_api_monitors ADD COLUMN locations_sync_enabled INTEGER NOT NULL DEFAULT 0"
        );

        monitoringJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS backup_readiness_monitors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                monitor_name TEXT NOT NULL,
                backup_kind TEXT NOT NULL DEFAULT 'generic',
                path_pattern TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                freshness_threshold_hours INTEGER NOT NULL DEFAULT 24,
                restore_threshold_days INTEGER NOT NULL DEFAULT 14,
                last_status TEXT,
                last_summary TEXT,
                last_error_message TEXT,
                last_backup_at TEXT,
                last_backup_size_bytes INTEGER,
                last_backup_path TEXT,
                last_restore_verified_at TEXT,
                last_restore_note TEXT,
                last_checked_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """);
        monitoringJdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_backup_readiness_monitors_name
            ON backup_readiness_monitors(monitor_name)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_backup_readiness_monitors_enabled
            ON backup_readiness_monitors(enabled)
            """);

        monitoringJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS public_ingress_monitors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                monitor_name TEXT NOT NULL,
                endpoint_url TEXT NOT NULL,
                scheme TEXT NOT NULL,
                host TEXT NOT NULL,
                port INTEGER NOT NULL,
                expected_http_status INTEGER,
                enabled INTEGER NOT NULL DEFAULT 1,
                last_status TEXT,
                last_summary TEXT,
                last_error_message TEXT,
                last_dns_resolved_at TEXT,
                last_dns_addresses TEXT,
                last_http_status INTEGER,
                last_http_duration_ms INTEGER,
                last_http_checked_at TEXT,
                last_tls_checked_at TEXT,
                last_tls_expires_at TEXT,
                last_tls_days_left INTEGER,
                last_checked_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """);
        monitoringJdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_public_ingress_monitors_name
            ON public_ingress_monitors(monitor_name)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_public_ingress_monitors_endpoint
            ON public_ingress_monitors(endpoint_url)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_public_ingress_monitors_enabled
            ON public_ingress_monitors(enabled)
            """);

        monitoringJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS monitoring_check_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                monitor_kind TEXT NOT NULL,
                monitor_id INTEGER NOT NULL,
                check_kind TEXT NOT NULL,
                status TEXT,
                summary TEXT,
                details_excerpt TEXT,
                http_status INTEGER,
                duration_ms INTEGER,
                created_at TEXT NOT NULL
            )
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_monitoring_check_history_monitor
            ON monitoring_check_history(monitor_kind, monitor_id, created_at DESC, id DESC)
            """);
        monitoringJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_monitoring_check_history_created_at
            ON monitoring_check_history(julianday(created_at))
            """);
    }

    private void migrateFromPrimaryDatabase() {
        if (sameDatabase()) {
            log.info("Primary DB and monitoring DB point to the same file, migration skipped");
            return;
        }
        migrateSslRecords();
        migrateRmsRecords();
        migrateCheckHistoryRecords();
    }

    private void migrateSslRecords() {
        if (!tableReadable(primaryJdbcTemplate, "ssl_certificate_monitors")) {
            return;
        }

        List<SslCertificateMonitor> items = primaryJdbcTemplate.query(
            "SELECT * FROM ssl_certificate_monitors ORDER BY id ASC",
            (rs, rowNum) -> {
                SslCertificateMonitor item = new SslCertificateMonitor();
                item.setSiteName(rs.getString("site_name"));
                item.setEndpointUrl(rs.getString("endpoint_url"));
                item.setHost(rs.getString("host"));
                item.setPort(rs.getInt("port"));
                item.setEnabled(rs.getInt("enabled") != 0);
                item.setMonitorStatus(rs.getString("monitor_status"));
                item.setErrorMessage(rs.getString("error_message"));
                Object daysLeft = rs.getObject("days_left");
                item.setDaysLeft(daysLeft == null ? null : rs.getInt("days_left"));
                item.setExpiresAt(parseOffsetDateTime(rs.getString("expires_at")));
                item.setLastCheckedAt(parseOffsetDateTime(rs.getString("last_checked_at")));
                item.setLastNotifiedAt(parseOffsetDateTime(rs.getString("last_notified_at")));
                item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
                item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
                return item;
            }
        );

        int migrated = 0;
        for (SslCertificateMonitor item : items) {
            if (item.getEndpointUrl() == null || item.getEndpointUrl().isBlank()) {
                continue;
            }
            sslRepository.findByEndpointUrl(item.getEndpointUrl()).ifPresent(existing -> item.setId(existing.getId()));
            sslRepository.save(item);
            migrated++;
        }
        if (migrated > 0) {
            log.info("Migrated {} SSL monitor records into monitoring.db", migrated);
        }
    }

    private void migrateRmsRecords() {
        if (!tableReadable(primaryJdbcTemplate, "rms_license_monitors")) {
            return;
        }

        List<RmsLicenseMonitor> items = primaryJdbcTemplate.query(
            "SELECT * FROM rms_license_monitors ORDER BY id ASC",
            (rs, rowNum) -> {
                RmsLicenseMonitor item = new RmsLicenseMonitor();
                item.setRmsAddress(rs.getString("rms_address"));
                item.setScheme(rs.getString("scheme"));
                item.setHost(rs.getString("host"));
                item.setPort(rs.getInt("port"));
                item.setAuthLogin(rs.getString("auth_login"));
                item.setAuthPassword(rs.getString("auth_password"));
                item.setEnabled(rs.getInt("enabled") != 0);
                item.setLicenseMonitoringEnabled(readBooleanColumn(rs, "license_monitoring_enabled", true));
                item.setNetworkMonitoringEnabled(readBooleanColumn(rs, "network_monitoring_enabled", true));
                item.setDeleted(readBooleanColumn(rs, "is_deleted", false));
                item.setServerName(rs.getString("server_name"));
                item.setServerType(rs.getString("server_type"));
                item.setServerVersion(rs.getString("server_version"));
                item.setLicenseStatus(rs.getString("license_status"));
                item.setLicenseErrorMessage(rs.getString("license_error_message"));
                item.setLicenseDetailsJson(readStringColumn(rs, "license_details_json"));
                item.setLicenseDebugExcerpt(readStringColumn(rs, "license_debug_excerpt"));
                item.setLicenseExpiresAt(parseOffsetDateTime(rs.getString("license_expires_at")));
                Object licenseDaysLeft = rs.getObject("license_days_left");
                item.setLicenseDaysLeft(licenseDaysLeft == null ? null : rs.getInt("license_days_left"));
                item.setLicenseLastCheckedAt(parseOffsetDateTime(rs.getString("license_last_checked_at")));
                item.setLicenseLastNotifiedAt(parseOffsetDateTime(rs.getString("license_last_notified_at")));
                item.setRmsStatus(rs.getString("rms_status"));
                item.setRmsStatusMessage(rs.getString("rms_status_message"));
                item.setPingOutput(rs.getString("ping_output"));
                item.setTracerouteSummary(rs.getString("traceroute_summary"));
                item.setTracerouteReport(rs.getString("traceroute_report"));
                item.setTracerouteCheckedAt(parseOffsetDateTime(rs.getString("traceroute_checked_at")));
                item.setRmsLastCheckedAt(parseOffsetDateTime(rs.getString("rms_last_checked_at")));
                item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
                item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
                item.setDeletedAt(parseOffsetDateTime(readStringColumn(rs, "deleted_at")));
                return item;
            }
        );

        int migrated = 0;
        for (RmsLicenseMonitor item : items) {
            if (item.getRmsAddress() == null || item.getRmsAddress().isBlank()) {
                continue;
            }
            findRmsMonitorIdByAddress(item.getRmsAddress()).ifPresent(item::setId);
            rmsRepository.save(item);
            migrated++;
        }
        if (migrated > 0) {
            log.info("Migrated {} RMS monitor records into monitoring.db", migrated);
        }
    }

    private void migrateCheckHistoryRecords() {
        if (!tableReadable(primaryJdbcTemplate, "monitoring_check_history")) {
            return;
        }
        try {
            OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
            List<LegacyMonitoringHistoryRow> items = primaryJdbcTemplate.query(
                """
                SELECT monitor_kind, monitor_id, check_kind, status, summary,
                       details_excerpt, http_status, duration_ms, created_at
                  FROM monitoring_check_history
                 ORDER BY id ASC
                """,
                (rs, rowNum) -> new LegacyMonitoringHistoryRow(
                    rs.getString("monitor_kind"),
                    readLongColumn(rs, "monitor_id"),
                    rs.getString("check_kind"),
                    rs.getString("status"),
                    rs.getString("summary"),
                    rs.getString("details_excerpt"),
                    readIntegerColumn(rs, "http_status"),
                    readLongColumn(rs, "duration_ms"),
                    readStringColumn(rs, "created_at")
                )
            );
            if (items.isEmpty()) {
                return;
            }

            int migrated = 0;
            int expired = 0;
            for (LegacyMonitoringHistoryRow item : items) {
                OffsetDateTime createdAt = parseOffsetDateTime(item.createdAt());
                if (createdAt != null && createdAt.isBefore(cutoff)) {
                    expired++;
                    continue;
                }
                if (item.monitorKind() == null || item.monitorId() == null || item.checkKind() == null
                        || item.createdAt() == null || item.createdAt().isBlank()) {
                    throw new IllegalStateException(
                        "Legacy monitoring history contains a row with missing required fields; source cleanup refused"
                    );
                }
                migrated += monitoringJdbcTemplate.update(
                    """
                    INSERT INTO monitoring_check_history (
                        monitor_kind, monitor_id, check_kind, status, summary,
                        details_excerpt, http_status, duration_ms, created_at
                    )
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?
                    WHERE NOT EXISTS (
                        SELECT 1
                          FROM monitoring_check_history
                         WHERE monitor_kind = ?
                           AND monitor_id = ?
                           AND check_kind = ?
                           AND status IS ?
                           AND summary IS ?
                           AND details_excerpt IS ?
                           AND http_status IS ?
                           AND duration_ms IS ?
                           AND created_at = ?
                    )
                    """,
                    item.monitorKind(), item.monitorId(), item.checkKind(), item.status(), item.summary(),
                    item.detailsExcerpt(), item.httpStatus(), item.durationMs(), item.createdAt(),
                    item.monitorKind(), item.monitorId(), item.checkKind(), item.status(), item.summary(),
                    item.detailsExcerpt(), item.httpStatus(), item.durationMs(), item.createdAt()
                );
            }

            int removed = primaryJdbcTemplate.update("DELETE FROM monitoring_check_history");
            log.info(
                "Migrated legacy monitoring history into monitoring.db: copied={}, expiredDiscarded={}, legacyRowsRemoved={}, cutoff={}",
                migrated,
                expired,
                removed,
                cutoff
            );
            if (removed > 0) {
                vacuumPrimaryDatabase();
            }
        } catch (Exception ex) {
            log.warn(
                "Failed to migrate legacy monitoring_check_history into monitoring.db; legacy source rows were preserved when migration did not complete",
                ex
            );
        }
    }

    private void vacuumPrimaryDatabase() {
        try {
            primaryJdbcTemplate.execute("VACUUM");
            log.info("VACUUM completed for legacy primary SQLite after monitoring history cleanup");
        } catch (Exception ex) {
            log.warn(
                "Legacy monitoring history rows were removed, but SQLite VACUUM failed; run VACUUM later to reclaim file size",
                ex
            );
        }
    }

    private boolean tableReadable(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            return count != null && count >= 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean sameDatabase() {
        try {
            return primaryProperties.getNormalizedPath().equals(monitoringProperties.getNormalizedPath());
        } catch (Exception ex) {
            return false;
        }
    }

    private java.util.Optional<Long> findRmsMonitorIdByAddress(String rmsAddress) {
        try {
            return monitoringJdbcTemplate.query(
                "SELECT id FROM rms_license_monitors WHERE rms_address = ? LIMIT 1",
                rs -> rs.next() ? java.util.Optional.of(rs.getLong("id")) : java.util.Optional.empty(),
                rmsAddress
            );
        } catch (Exception ex) {
            log.warn("Failed to resolve RMS monitor id for address {} during bootstrap migration", rmsAddress, ex);
            return java.util.Optional.empty();
        }
    }

    private void migrateEncryptedRmsCredentials() {
        try {
            List<Long> ids = monitoringJdbcTemplate.query(
                "SELECT id FROM rms_license_monitors WHERE auth_password IS NOT NULL AND auth_password <> ''",
                (rs, rowNum) -> rs.getLong("id")
            );
            int updated = 0;
            for (Long id : ids) {
                String stored = monitoringJdbcTemplate.queryForObject(
                    "SELECT auth_password FROM rms_license_monitors WHERE id = ?",
                    String.class,
                    id
                );
                if (stored == null || stored.isBlank() || credentialsCryptoService.isEncrypted(stored)) {
                    continue;
                }
                String encrypted = credentialsCryptoService.encryptIfNeeded(stored);
                monitoringJdbcTemplate.update(
                    "UPDATE rms_license_monitors SET auth_password = ? WHERE id = ?",
                    encrypted,
                    id
                );
                updated++;
            }
            if (updated > 0) {
                log.info("Encrypted {} plaintext RMS credential(s) in monitoring.db", updated);
            }
        } catch (Exception ex) {
            log.warn("Failed to migrate RMS credentials to encrypted storage", ex);
        }
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }

    private void ensureColumn(String tableName, String columnName, String sql) {
        try {
            boolean exists = monitoringJdbcTemplate.query(
                "PRAGMA table_info(" + tableName + ")",
                (ResultSet rs) -> {
                    while (rs.next()) {
                        if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                            return true;
                        }
                    }
                    return false;
                }
            );
            if (!exists) {
                monitoringJdbcTemplate.execute(sql);
            }
        } catch (Exception ex) {
            log.warn("Failed to ensure column {}.{} in monitoring DB", tableName, columnName, ex);
        }
    }

    private boolean readBooleanColumn(ResultSet rs, String columnName, boolean defaultValue) {
        try {
            return rs.getInt(columnName) != 0;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Long readLongColumn(ResultSet rs, String columnName) {
        try {
            Object value = rs.getObject(columnName);
            return value instanceof Number number ? number.longValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer readIntegerColumn(ResultSet rs, String columnName) {
        try {
            Object value = rs.getObject(columnName);
            return value instanceof Number number ? number.intValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readStringColumn(ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record LegacyMonitoringHistoryRow(String monitorKind,
                                              Long monitorId,
                                              String checkKind,
                                              String status,
                                              String summary,
                                              String detailsExcerpt,
                                              Integer httpStatus,
                                              Long durationMs,
                                              String createdAt) {
    }
}
