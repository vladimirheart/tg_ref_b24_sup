package com.example.panel.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration(proxyBeanMethods = false)
public class MonitoringDataSourceConfiguration {

    @Bean(name = "monitoringDataSource")
    public DataSource monitoringDataSource(@Qualifier("dataSource") DataSource primaryDataSource) {
        return primaryDataSource;
    }

    @Bean(name = "monitoringJdbcTemplate")
    public JdbcTemplate monitoringJdbcTemplate(@Qualifier("monitoringDataSource") DataSource monitoringDataSource) {
        return new JdbcTemplate(monitoringDataSource);
    }

    @Bean(name = "monitoringRuntimeJdbcTemplate")
    public JdbcTemplate monitoringRuntimeJdbcTemplate(JdbcTemplate primaryJdbcTemplate) {
        return primaryJdbcTemplate;
    }

    @Bean(name = "monitoringTransactionManager")
    public DataSourceTransactionManager monitoringTransactionManager(
        @Qualifier("monitoringDataSource") DataSource monitoringDataSource
    ) {
        return new DataSourceTransactionManager(monitoringDataSource);
    }
}
