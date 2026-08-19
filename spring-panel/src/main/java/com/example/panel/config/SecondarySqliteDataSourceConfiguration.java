package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties({
    ClientsSqliteDataSourceProperties.class,
    KnowledgeSqliteDataSourceProperties.class,
    ObjectsSqliteDataSourceProperties.class,
    SettingsSqliteDataSourceProperties.class
})
public class SecondarySqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecondarySqliteDataSourceConfiguration.class);

    @Bean(name = "objectsDataSource")
    public DataSource objectsDataSource(ObjectsSqliteDataSourceProperties properties,
                                        @Qualifier("dataSource") DataSource primaryDataSource,
                                        PanelDatabaseRuntimeMode databaseRuntimeMode) {
        if (databaseRuntimeMode.isExternalDatabaseEnabled()) {
            log.info("Using primary external {} datasource as OBJECTS datasource", databaseRuntimeMode.modeLabel());
            return primaryDataSource;
        }
        log.info("Using OBJECTS SQLite database at {}", properties.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(properties);
    }
}
