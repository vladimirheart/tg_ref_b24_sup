package com.example.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DataSourceConfigTest {

    @Test
    void buildsPostgresqlDatasourceAndForcesExternalRuntimeProperties() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("support-bot.database.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.local:5432/iguana")
            .withProperty("spring.datasource.username", "iguana")
            .withProperty("spring.datasource.password", "secret");

        DataSource dataSource = new DataSourceConfig().dataSource(environment);

        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getJdbcUrl())
            .isEqualTo("jdbc:postgresql://db.example.local:5432/iguana");
        assertThat(environment.getProperty("spring.jpa.database-platform"))
            .isEqualTo("org.hibernate.dialect.PostgreSQLDialect");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void missingPostgresqlDatasourceFailsClosed() {
        assertThatThrownBy(() -> new DataSourceConfig().dataSource(
            new MockEnvironment().withProperty("support-bot.database.mode", "postgresql")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires PostgreSQL");
    }

    @Test
    void legacyDatabaseModesAreRejected() {
        assertThatThrownBy(() -> new DataSourceConfig().dataSource(
            new MockEnvironment().withProperty("support-bot.database.mode", "sqlite")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Only postgresql");

        assertThatThrownBy(() -> new DataSourceConfig().dataSource(
            new MockEnvironment().withProperty("support-bot.database.mode", "worker")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Only postgresql");
    }
}
