package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MonitoringDataSourceConfigurationTest {

    private final MonitoringDataSourceConfiguration configuration = new MonitoringDataSourceConfiguration();

    @Test
    void monitoringDatasourceAliasesPrimaryDatasource() {
        DataSource primaryDataSource = mock(DataSource.class);
        assertThat(configuration.monitoringDataSource(primaryDataSource)).isSameAs(primaryDataSource);
    }

    @Test
    void monitoringRuntimeJdbcTemplateUsesPrimaryTemplate() {
        JdbcTemplate primaryJdbcTemplate = mock(JdbcTemplate.class);
        assertThat(configuration.monitoringRuntimeJdbcTemplate(primaryJdbcTemplate))
            .isSameAs(primaryJdbcTemplate);
    }
}
