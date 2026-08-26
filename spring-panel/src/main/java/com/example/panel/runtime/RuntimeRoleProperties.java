package com.example.panel.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.runtime")
public class RuntimeRoleProperties {

    private String role = "all";
    private String instanceId = "local";

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public RuntimeRole resolvedRole() {
        return RuntimeRole.from(role);
    }

    public String resolvedInstanceId() {
        return StringUtils.hasText(instanceId) ? instanceId.trim() : "local";
    }
}
