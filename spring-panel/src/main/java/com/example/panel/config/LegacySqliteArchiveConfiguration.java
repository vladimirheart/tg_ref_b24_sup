package com.example.panel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    SqliteDataSourceProperties.class,
    MonitoringSqliteDataSourceProperties.class,
    BotSqliteDataSourceProperties.class
})
public class LegacySqliteArchiveConfiguration {
}
