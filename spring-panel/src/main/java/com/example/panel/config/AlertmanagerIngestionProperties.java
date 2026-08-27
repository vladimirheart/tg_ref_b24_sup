package com.example.panel.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.alertmanager.ingestion")
public class AlertmanagerIngestionProperties {

    private boolean enabled = false;
    private Duration leaseTtl = Duration.ofSeconds(30);
    private String tokenFile = "/run/secrets/iguana-alertmanager-ingestion.token";
    private String routeType = "all_operators";
    private String routeTarget = "all_operators";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getLeaseTtl() {
        return leaseTtl;
    }

    public void setLeaseTtl(Duration leaseTtl) {
        this.leaseTtl = leaseTtl;
    }

    public String getTokenFile() {
        return tokenFile;
    }

    public void setTokenFile(String tokenFile) {
        this.tokenFile = tokenFile;
    }

    public String getRouteType() {
        return routeType;
    }

    public void setRouteType(String routeType) {
        this.routeType = routeType;
    }

    public String getRouteTarget() {
        return routeTarget;
    }

    public void setRouteTarget(String routeTarget) {
        this.routeTarget = routeTarget;
    }
}
