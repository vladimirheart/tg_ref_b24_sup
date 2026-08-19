package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.coordination")
public class RuntimeCoordinationProperties {

    private String mode = "redis";
    private String leaseNamespace = "iguana";
    private boolean requiredForPostgresql = true;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getLeaseNamespace() {
        return leaseNamespace;
    }

    public void setLeaseNamespace(String leaseNamespace) {
        this.leaseNamespace = leaseNamespace;
    }

    public boolean isRequiredForPostgresql() {
        return requiredForPostgresql;
    }

    public void setRequiredForPostgresql(boolean requiredForPostgresql) {
        this.requiredForPostgresql = requiredForPostgresql;
    }

    public boolean isRedisMode() {
        return mode != null && "redis".equalsIgnoreCase(mode.trim());
    }
}
