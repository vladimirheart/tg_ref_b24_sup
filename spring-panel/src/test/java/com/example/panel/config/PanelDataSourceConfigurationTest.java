package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PanelDataSourceConfigurationTest {

    private final PanelDataSourceConfiguration configuration = new PanelDataSourceConfiguration();

    @Test
    void dataSourceRejectsMissingExternalDatasource() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.datasource.mode", "auto");

        assertThatThrownBy(() -> configuration.dataSource(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("external datasource contract");
    }

    @Test
    void dataSourceBuildsExternalDatasourceForPostgresql() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.datasource.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.local:5432/iguana")
            .withProperty("spring.datasource.username", "iguana")
            .withProperty("spring.datasource.password", "secret");

        DataSource dataSource = configuration.dataSource(environment);

        assertThat(dataSource).isNotNull();
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }
}
