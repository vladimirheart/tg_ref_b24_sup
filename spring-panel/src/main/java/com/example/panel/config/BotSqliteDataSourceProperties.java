package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.bot-sqlite")
public class BotSqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(BotSqliteDataSourceProperties.class);

    public BotSqliteDataSourceProperties() {
        super("bot_runtime.db", "app.datasource.bot-sqlite.path", "bot");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
