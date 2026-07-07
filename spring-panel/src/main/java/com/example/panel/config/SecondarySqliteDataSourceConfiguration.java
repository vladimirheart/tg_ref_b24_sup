package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    @Bean(name = "clientsDataSource")
    public DataSource clientsDataSource(ClientsSqliteDataSourceProperties properties) {
        log.info("Using CLIENTS SQLite database at {}", properties.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(properties);
    }

    @Bean(name = "knowledgeDataSource")
    public DataSource knowledgeDataSource(KnowledgeSqliteDataSourceProperties properties) {
        log.info("Using KNOWLEDGE SQLite database at {}", properties.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(properties);
    }

    @Bean(name = "objectsDataSource")
    public DataSource objectsDataSource(ObjectsSqliteDataSourceProperties properties) {
        log.info("Using OBJECTS SQLite database at {}", properties.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(properties);
    }

    @Bean(name = "settingsDataSource")
    public DataSource settingsDataSource(SettingsSqliteDataSourceProperties properties) {
        log.info("Using SETTINGS SQLite database at {}", properties.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(properties);
    }
}
