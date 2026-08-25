package com.example.supportbot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.integration.panel-api")
public class IntegrationPanelApiProperties {

    private String baseUrl = "http://127.0.0.1:8080";
    private String token = "iguana-internal-bot-token";
    private boolean requestSigningEnabled = true;
    private String signatureSecret;
    private Duration requestTimeout = Duration.ofSeconds(5);
    private int retryAttempts = 2;
    private Duration retryBackoff = Duration.ofMillis(250);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isRequestSigningEnabled() {
        return requestSigningEnabled;
    }

    public void setRequestSigningEnabled(boolean requestSigningEnabled) {
        this.requestSigningEnabled = requestSigningEnabled;
    }

    public String getSignatureSecret() {
        return signatureSecret;
    }

    public void setSignatureSecret(String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(token);
    }

    public String resolveSignatureSecret() {
        return StringUtils.hasText(signatureSecret) ? signatureSecret.trim() : token;
    }
}
