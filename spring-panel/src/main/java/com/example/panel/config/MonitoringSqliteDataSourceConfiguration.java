package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(MonitoringSqliteDataSourceProperties.class)
public class MonitoringSqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MonitoringSqliteDataSourceConfiguration.class);

    @Bean(name = "monitoringDataSource")
    public DataSource monitoringDataSource(MonitoringSqliteDataSourceProperties props,
                                           @Qualifier("dataSource") DataSource primaryDataSource,
                                           PanelDatabaseRuntimeMode databaseRuntimeMode) {
        if (databaseRuntimeMode.isExternalDatabaseEnabled()) {
            log.info("Using primary external {} datasource as MONITORING datasource", databaseRuntimeMode.modeLabel());
            return primaryDataSource;
        }
        log.info("Using MONITORING SQLite database at {}", props.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(props);
    }

    @Bean(name = "monitoringJdbcTemplate")
    public JdbcTemplate monitoringJdbcTemplate(@Qualifier("monitoringDataSource") DataSource monitoringDataSource) {
        return new JdbcTemplate(monitoringDataSource);
    }

    @Bean(name = "monitoringRuntimeJdbcTemplate")
    public JdbcTemplate monitoringRuntimeJdbcTemplate(JdbcTemplate primaryJdbcTemplate,
                                                      @Qualifier("monitoringJdbcTemplate") JdbcTemplate monitoringJdbcTemplate,
                                                      PanelDatabaseRuntimeMode databaseRuntimeMode) {
        if (databaseRuntimeMode.isExternalDatabaseEnabled()) {
            return primaryJdbcTemplate;
        }
        return monitoringJdbcTemplate;
    }

    @Bean(name = "monitoringTransactionManager")
    public DataSourceTransactionManager monitoringTransactionManager(@Qualifier("monitoringDataSource") DataSource monitoringDataSource) {
        return new DataSourceTransactionManager(monitoringDataSource);
    }
}
