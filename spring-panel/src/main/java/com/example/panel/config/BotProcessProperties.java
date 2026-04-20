package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.bots")
public class BotProcessProperties {

    private String databaseDir = "../bot_databases";
    private String host = "127.0.0.1";
    private int maxBasePort = 18000;
    private String launchMode = "auto";
    private Map<String, String> executableJars = new LinkedHashMap<>();
    private String preferredProductionLauncher = "jar";
    private String recommendedArtifactDirectory = "dist";
    private Duration startupReadinessTimeout = Duration.ofSeconds(45);
    private Duration startupPollInterval = Duration.ofMillis(250);

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

    public Map<String, String> getExecutableJars() {
        return executableJars;
    }

    public void setExecutableJars(Map<String, String> executableJars) {
        this.executableJars = executableJars != null ? new LinkedHashMap<>(executableJars) : new LinkedHashMap<>();
    }

    public Duration getStartupReadinessTimeout() {
        return startupReadinessTimeout;
    }

    public void setStartupReadinessTimeout(Duration startupReadinessTimeout) {
        this.startupReadinessTimeout = startupReadinessTimeout;
    }

    public Duration getStartupPollInterval() {
        return startupPollInterval;
    }

    public void setStartupPollInterval(Duration startupPollInterval) {
        this.startupPollInterval = startupPollInterval;
    }

    public String getPreferredProductionLauncher() {
        return preferredProductionLauncher;
    }

    public void setPreferredProductionLauncher(String preferredProductionLauncher) {
        this.preferredProductionLauncher = preferredProductionLauncher;
    }

    public String getRecommendedArtifactDirectory() {
        return recommendedArtifactDirectory;
    }

    public void setRecommendedArtifactDirectory(String recommendedArtifactDirectory) {
        this.recommendedArtifactDirectory = recommendedArtifactDirectory;
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

    public String resolveExecutableJar(String botModule) {
        if (botModule == null || botModule.isBlank() || executableJars == null) {
            return null;
        }
        String configured = executableJars.get(botModule);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return configured.trim();
    }

    public Duration resolveStartupReadinessTimeout() {
        return startupReadinessTimeout != null && !startupReadinessTimeout.isNegative() && !startupReadinessTimeout.isZero()
            ? startupReadinessTimeout
            : Duration.ofSeconds(45);
    }

    public Duration resolveStartupPollInterval() {
        return startupPollInterval != null && !startupPollInterval.isNegative() && !startupPollInterval.isZero()
            ? startupPollInterval
            : Duration.ofMillis(250);
    }

    public LaunchMode resolvePreferredProductionLauncher() {
        String normalized = preferredProductionLauncher == null ? "" : preferredProductionLauncher.trim().toLowerCase();
        return switch (normalized) {
            case "maven", "spring-boot-run", "spring_boot_run" -> LaunchMode.MAVEN;
            case "jar", "" -> LaunchMode.JAR;
            default -> LaunchMode.JAR;
        };
    }

    public String resolveRecommendedExecutableJar(String botModule) {
        if (botModule == null || botModule.isBlank()) {
            return null;
        }
        String baseDir = (recommendedArtifactDirectory == null || recommendedArtifactDirectory.isBlank())
            ? "dist"
            : recommendedArtifactDirectory.trim();
        return baseDir + "/" + botModule + "-runtime.jar";
    }

    public enum LaunchMode {
        AUTO,
        JAR,
        MAVEN
    }
}
