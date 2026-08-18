package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.mock.env.MockEnvironment;

class FlywayConfigTest {

    private final FlywayConfig flywayConfig = new FlywayConfig();

    @Test
    void usesPostgresqlMigrationsForPostgresqlMode() {
        assertLocation("postgresql", null, "classpath:db/migration/postgresql");
    }

    @Test
    void usesMysqlMigrationsForMysqlMode() {
        assertLocation("mysql", null, "classpath:db/migration/mysql");
    }

    @Test
    void usesSqliteMigrationsForSqliteMode() {
        assertLocation("sqlite", null, "classpath:db/migration/sqlite");
    }

    @Test
    void detectsPostgresqlLocationInAutoMode() {
        assertLocation("auto", "jdbc:postgresql://localhost:5432/iguana", "classpath:db/migration/postgresql");
    }

    private void assertLocation(String mode, String datasourceUrl, String expectedLocation) {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.datasource.mode", mode);

        if (datasourceUrl != null) {
            environment.withProperty("spring.datasource.url", datasourceUrl);
        }

        FluentConfiguration configuration = Flyway.configure();
        FlywayConfigurationCustomizer customizer = flywayConfig.databaseSpecificFlywayLocations(environment);
        customizer.customize(configuration);

        assertThat(configuration.getLocations())
            .hasSize(1);
        assertThat(configuration.getLocations()[0].getDescriptor())
            .isEqualTo(expectedLocation);
    }
}
