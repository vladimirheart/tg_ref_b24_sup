package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.settings-sqlite")
public class SettingsSqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(SettingsSqliteDataSourceProperties.class);

    public SettingsSqliteDataSourceProperties() {
        super("settings.db", "app.datasource.settings-sqlite.path", "settings");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
