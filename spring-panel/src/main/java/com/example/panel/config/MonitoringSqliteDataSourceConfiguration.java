package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(MonitoringSqliteDataSourceProperties.class)
public class MonitoringSqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MonitoringSqliteDataSourceConfiguration.class);

    @Bean(name = "monitoringDataSource")
    public DataSource monitoringDataSource(MonitoringSqliteDataSourceProperties props) {
        var url = props.buildJdbcUrl();
        log.info("Using MONITORING SQLite database at {}", props.getNormalizedPath());

        var config = SqliteConnectionConfigSupport.buildConfig(
            props.getJournalMode(),
            props.getBusyTimeoutMs()
        );

        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl(url);
        return ds;
    }

    @Bean(name = "monitoringJdbcTemplate")
    public JdbcTemplate monitoringJdbcTemplate(DataSource monitoringDataSource) {
        return new JdbcTemplate(monitoringDataSource);
    }

    @Bean(name = "monitoringTransactionManager")
    public DataSourceTransactionManager monitoringTransactionManager(DataSource monitoringDataSource) {
        return new DataSourceTransactionManager(monitoringDataSource);
    }
}
