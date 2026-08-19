package com.example.supportbot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.coordination")
public class BotIngressCoordinationProperties {

    private String mode = "direct";
    private String leaseNamespace = "iguana";
    private Duration ingressLeaseTtl = Duration.ofSeconds(45);
    private Duration ingressLeaseRenewInterval = Duration.ofSeconds(15);
    private Duration ingressFollowerBackoff = Duration.ofSeconds(5);

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

    public Duration getIngressLeaseTtl() {
        return ingressLeaseTtl;
    }

    public void setIngressLeaseTtl(Duration ingressLeaseTtl) {
        this.ingressLeaseTtl = ingressLeaseTtl;
    }

    public Duration getIngressLeaseRenewInterval() {
        return ingressLeaseRenewInterval;
    }

    public void setIngressLeaseRenewInterval(Duration ingressLeaseRenewInterval) {
        this.ingressLeaseRenewInterval = ingressLeaseRenewInterval;
    }

    public Duration getIngressFollowerBackoff() {
        return ingressFollowerBackoff;
    }

    public void setIngressFollowerBackoff(Duration ingressFollowerBackoff) {
        this.ingressFollowerBackoff = ingressFollowerBackoff;
    }

    public boolean isRedisMode() {
        return "redis".equalsIgnoreCase(mode);
    }
}
