package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(UsersSqliteDataSourceProperties.class)
public class UsersSqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UsersSqliteDataSourceConfiguration.class);

    @Bean(name = "usersDataSource")
    public DataSource usersDataSource(UsersSqliteDataSourceProperties props,
                                      @Qualifier("dataSource") DataSource primaryDataSource,
                                      PanelDatabaseRuntimeMode databaseRuntimeMode) {
        if (databaseRuntimeMode.isExternalDatabaseEnabled()) {
            log.info("Using primary external {} datasource as USERS datasource", databaseRuntimeMode.modeLabel());
            return primaryDataSource;
        }
        log.info("Using USERS SQLite database at {}", props.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(props);
    }

    @Bean(name = "usersJdbcTemplate")
    public JdbcTemplate usersJdbcTemplate(@Qualifier("usersDataSource") DataSource usersDataSource) {
        return new JdbcTemplate(usersDataSource);
    }
}
