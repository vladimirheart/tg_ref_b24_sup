package com.example.panel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    ClientsSqliteDataSourceProperties.class,
    KnowledgeSqliteDataSourceProperties.class,
    ObjectsSqliteDataSourceProperties.class,
    SettingsSqliteDataSourceProperties.class
})
public class SecondarySqliteDataSourceConfiguration {
}
