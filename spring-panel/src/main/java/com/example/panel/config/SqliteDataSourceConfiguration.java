package com.example.panel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

import org.sqlite.SQLiteDataSource;

@Configuration
@Profile("sqlite")
@EnableConfigurationProperties(SqliteDataSourceProperties.class)
public class SqliteDataSourceConfiguration {

    @Bean
    @Primary
    public DataSource sqliteDataSource(SqliteDataSourceProperties properties) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(properties.buildJdbcUrl());
        return dataSource;
    }
}