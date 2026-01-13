package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "app.bots")
public class BotProcessProperties {

    private String databaseDir = "../bot_databases";

    public String getDatabaseDir() {
        return databaseDir;
    }

    public void setDatabaseDir(String databaseDir) {
        this.databaseDir = databaseDir;
    }

    public Path resolveDatabaseDir() {
        return Paths.get(databaseDir).toAbsolutePath().normalize();
    }
}
