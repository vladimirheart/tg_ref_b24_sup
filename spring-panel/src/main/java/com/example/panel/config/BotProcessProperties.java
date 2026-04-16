package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "app.bots")
public class BotProcessProperties {

    private String databaseDir = "../bot_databases";
    private String host = "127.0.0.1";
    private int maxBasePort = 18000;
    private String launchMode = "auto";

    public String getDatabaseDir() {
        return databaseDir;
    }

    public void setDatabaseDir(String databaseDir) {
        this.databaseDir = databaseDir;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getMaxBasePort() {
        return maxBasePort;
    }

    public void setMaxBasePort(int maxBasePort) {
        this.maxBasePort = maxBasePort;
    }

    public String getLaunchMode() {
        return launchMode;
    }

    public void setLaunchMode(String launchMode) {
        this.launchMode = launchMode;
    }

    public Path resolveDatabaseDir() {
        return Paths.get(databaseDir).toAbsolutePath().normalize();
    }

    public int resolveMaxPort(Long channelId) {
        if (channelId == null || channelId <= 0) {
            return maxBasePort;
        }
        long candidate = (long) maxBasePort + channelId;
        if (candidate > 65535L) {
            throw new IllegalArgumentException("MAX bot port is out of range for channel " + channelId);
        }
        return (int) candidate;
    }

    public LaunchMode resolveLaunchMode() {
        String normalized = launchMode == null ? "" : launchMode.trim().toLowerCase();
        return switch (normalized) {
            case "jar" -> LaunchMode.JAR;
            case "maven", "spring-boot-run", "spring_boot_run" -> LaunchMode.MAVEN;
            default -> LaunchMode.AUTO;
        };
    }

    public enum LaunchMode {
        AUTO,
        JAR,
        MAVEN
    }
}
