package com.example.panel.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.bots.internal-api")
public class InternalBotApiProperties {

    private String token = "iguana-internal-bot-token";
    private String signatureSecret;
    private boolean requireRequestSignature = false;
    private Duration requestTimestampSkew = Duration.ofMinutes(5);
    private Duration idempotencyInflightTtl = Duration.ofMinutes(2);
    private Duration idempotencyTtl = Duration.ofHours(12);

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSignatureSecret() {
        return signatureSecret;
    }

    public void setSignatureSecret(String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    public boolean isRequireRequestSignature() {
        return requireRequestSignature;
    }

    public void setRequireRequestSignature(boolean requireRequestSignature) {
        this.requireRequestSignature = requireRequestSignature;
    }

    public Duration getRequestTimestampSkew() {
        return requestTimestampSkew;
    }

    public void setRequestTimestampSkew(Duration requestTimestampSkew) {
        this.requestTimestampSkew = requestTimestampSkew;
    }

    public Duration getIdempotencyInflightTtl() {
        return idempotencyInflightTtl;
    }

    public void setIdempotencyInflightTtl(Duration idempotencyInflightTtl) {
        this.idempotencyInflightTtl = idempotencyInflightTtl;
    }

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }
}
