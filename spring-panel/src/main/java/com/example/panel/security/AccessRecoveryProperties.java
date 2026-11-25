package com.example.panel.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.access-recovery")
public class AccessRecoveryProperties {
    private Duration minimumResponseTime = Duration.ofMillis(350);
    private Duration rateWindow = Duration.ofMinutes(15);
    private int requestsPerIp = 20;
    private int requestsPerLogin = 5;
    private int loginAttemptsPerIp = 100;
    private int loginAttemptsPerLogin = 20;
    private int metadataRetentionDays = 90;
    private List<String> trustedProxies = List.of();

    public Duration getMinimumResponseTime() { return minimumResponseTime; }
    public void setMinimumResponseTime(Duration value) { minimumResponseTime = value; }
    public Duration getRateWindow() { return rateWindow; }
    public void setRateWindow(Duration value) { rateWindow = value; }
    public int getRequestsPerIp() { return requestsPerIp; }
    public void setRequestsPerIp(int value) { requestsPerIp = value; }
    public int getRequestsPerLogin() { return requestsPerLogin; }
    public void setRequestsPerLogin(int value) { requestsPerLogin = value; }
    public int getLoginAttemptsPerIp() { return loginAttemptsPerIp; }
    public void setLoginAttemptsPerIp(int value) { loginAttemptsPerIp = value; }
    public int getLoginAttemptsPerLogin() { return loginAttemptsPerLogin; }
    public void setLoginAttemptsPerLogin(int value) { loginAttemptsPerLogin = value; }
    public int getMetadataRetentionDays() { return metadataRetentionDays; }
    public void setMetadataRetentionDays(int value) { metadataRetentionDays = value; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> value) { trustedProxies = List.copyOf(value); }
}
