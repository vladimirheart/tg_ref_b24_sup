package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SqliteDataSourceConfigurationTest {

    private final SqliteDataSourceConfiguration configuration = new SqliteDataSourceConfiguration();

    @Test
    void dataSourceRejectsRetiredSqliteRuntime() {
        SqliteDataSourceProperties properties = new SqliteDataSourceProperties();
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.datasource.mode", "sqlite");

        assertThatThrownBy(() -> configuration.dataSource(properties, environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("external datasource contract")
            .hasMessageContaining("SQLite runtime mode");
    }

    @Test
    void dataSourceBuildsExternalDatasourceForPostgresql() {
        SqliteDataSourceProperties properties = new SqliteDataSourceProperties();
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.datasource.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.local:5432/iguana")
            .withProperty("spring.datasource.username", "iguana")
            .withProperty("spring.datasource.password", "secret");

        DataSource dataSource = configuration.dataSource(properties, environment);

        assertThat(dataSource).isNotNull();
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }
}
