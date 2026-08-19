package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class MonitoringSqliteDataSourceConfigurationTest {

    private final MonitoringSqliteDataSourceConfiguration configuration = new MonitoringSqliteDataSourceConfiguration();

    @Test
    void monitoringRuntimeJdbcTemplateUsesPrimaryTemplateInExternalMode() {
        JdbcTemplate primaryJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate monitoringJdbcTemplate = mock(JdbcTemplate.class);

        JdbcTemplate runtimeJdbcTemplate = configuration.monitoringRuntimeJdbcTemplate(
            primaryJdbcTemplate,
            monitoringJdbcTemplate,
            new PanelDatabaseRuntimeMode(new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")
                .withProperty("spring.datasource.username", "iguana")
                .withProperty("spring.datasource.password", "iguana"))
        );

        assertThat(runtimeJdbcTemplate).isSameAs(primaryJdbcTemplate);
    }

    @Test
    void monitoringRuntimeJdbcTemplateUsesDedicatedMonitoringTemplateInSqliteMode() {
        JdbcTemplate primaryJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate monitoringJdbcTemplate = mock(JdbcTemplate.class);

        JdbcTemplate runtimeJdbcTemplate = configuration.monitoringRuntimeJdbcTemplate(
            primaryJdbcTemplate,
            monitoringJdbcTemplate,
            new PanelDatabaseRuntimeMode(new MockEnvironment())
        );

        assertThat(runtimeJdbcTemplate).isSameAs(monitoringJdbcTemplate);
    }
}
