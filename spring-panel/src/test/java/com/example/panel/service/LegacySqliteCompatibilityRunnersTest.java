package com.example.panel.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.panel.config.LegacySqliteCompatibilitySettings;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class LegacySqliteCompatibilityRunnersTest {

    @Test
    void legacyImportSkipsByDefaultInPostgresqlMode() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        MockEnvironment environment = new MockEnvironment();
        LegacySqliteImportService service = new LegacySqliteImportService(
			dataSource,
			environment,
			postgresMode(),
			new LegacySqliteCompatibilitySettings(environment),
			mock(MonitoringCredentialsCryptoService.class)
		);

        service.run(mock(ApplicationArguments.class));

        verify(dataSource, never()).getConnection();
    }

    @Test
    void criticalRecoverySkipsByDefaultInPostgresqlMode() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        MockEnvironment environment = new MockEnvironment();
        PostgresLegacyCriticalDataRecoveryService service = new PostgresLegacyCriticalDataRecoveryService(
            dataSource,
            environment,
            postgresMode(),
            new LegacySqliteCompatibilitySettings(environment)
        );

        service.run(mock(ApplicationArguments.class));

        verify(dataSource, never()).getConnection();
    }

    @Test
    void reconciliationSkipsMarkerQueryByDefaultInPostgresqlMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MockEnvironment environment = new MockEnvironment();
        PostgresImportedDataReconciliationService service = new PostgresImportedDataReconciliationService(
            jdbcTemplate,
            postgresMode(),
            new LegacySqliteCompatibilitySettings(environment)
        );

        service.run(mock(ApplicationArguments.class));

        verify(jdbcTemplate, never()).queryForObject("SELECT COUNT(*) FROM legacy_sqlite_imports", Integer.class);
    }

    private PanelDatabaseRuntimeMode postgresMode() {
        return new PanelDatabaseRuntimeMode(new MockEnvironment()
            .withProperty("app.datasource.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")
            .withProperty("spring.datasource.username", "iguana")
            .withProperty("spring.datasource.password", "iguana"));
    }
}
