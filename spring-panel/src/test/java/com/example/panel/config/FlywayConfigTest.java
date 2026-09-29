package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.mock.env.MockEnvironment;

class FlywayConfigTest {

    private final FlywayConfig flywayConfig = new FlywayConfig();

    @Test
    void alwaysUsesPostgresqlMigrationChainForCanonicalMode() {
        assertLocation(new MockEnvironment().withProperty("app.datasource.mode", "postgresql"));
    }

    @Test
    void defaultModeAlsoUsesPostgresqlMigrationChain() {
        assertLocation(new MockEnvironment());
    }

    @Test
    void rejectsLegacyModes() {
        for (String mode : new String[]{"sqlite", "mysql", "auto", "worker"}) {
            MockEnvironment environment = new MockEnvironment().withProperty("app.datasource.mode", mode);
            FluentConfiguration configuration = Flyway.configure();
            FlywayConfigurationCustomizer customizer = flywayConfig.databaseSpecificFlywayLocations(environment);
            assertThatThrownBy(() -> customizer.customize(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only postgresql");
        }
    }

    @Test
    void rejectsNonPostgresqlDatasourceUrl() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.datasource.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/iguana");
        FluentConfiguration configuration = Flyway.configure();
        FlywayConfigurationCustomizer customizer = flywayConfig.databaseSpecificFlywayLocations(environment);
        assertThatThrownBy(() -> customizer.customize(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only PostgreSQL");
    }

    private void assertLocation(MockEnvironment environment) {
        FluentConfiguration configuration = Flyway.configure();
        flywayConfig.databaseSpecificFlywayLocations(environment).customize(configuration);
        assertThat(configuration.getLocations()).hasSize(1);
        assertThat(configuration.getLocations()[0].getDescriptor())
            .isEqualTo("classpath:db/migration/postgresql");
    }
}
