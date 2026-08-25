package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PanelApiRequestHeadersFactory {

    public static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";
    public static final String TIMESTAMP_HEADER = "X-Iguana-Request-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Iguana-Request-Signature";
    public static final String IDEMPOTENCY_HEADER = "X-Iguana-Idempotency-Key";

    private final IntegrationPanelApiProperties properties;

    public PanelApiRequestHeadersFactory(IntegrationPanelApiProperties properties) {
        this.properties = properties;
    }

    public void apply(HttpRequestBuilderAdapter adapter,
                      String method,
                      URI uri,
                      String requestBody,
                      String idempotencyKey) {
        adapter.header(AUTH_HEADER, properties.getToken());
        if (StringUtils.hasText(idempotencyKey)) {
            adapter.header(IDEMPOTENCY_HEADER, idempotencyKey.trim());
        }
        if (!properties.isRequestSigningEnabled()) {
            return;
        }
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
        String signature = sign(
            method,
            canonicalPath(uri),
            timestamp,
            requestBody
        );
        adapter.header(TIMESTAMP_HEADER, timestamp);
        adapter.header(SIGNATURE_HEADER, signature);
    }

    public String newIdempotencyKey(String scope, String target) {
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toLowerCase() : "panel-api";
        String normalizedTarget = StringUtils.hasText(target) ? target.trim().toLowerCase() : "generic";
        return normalizedScope + ":" + normalizedTarget + ":" + UUID.randomUUID();
    }

    public String canonicalPath(URI uri) {
        if (uri == null) {
            return "/";
        }
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
        String query = uri.getRawQuery();
        return StringUtils.hasText(query) ? path + "?" + query : path;
    }

    private String sign(String method, String canonicalPath, String timestamp, String requestBody) {
        String canonical = normalizeMethod(method)
            + "\n"
            + (StringUtils.hasText(canonicalPath) ? canonicalPath.trim() : "/")
            + "\n"
            + timestamp.trim()
            + "\n"
            + sha256Hex(StringUtils.hasText(requestBody) ? requestBody : "");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.resolveSignatureSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign internal panel API request.", ex);
        }
    }

    private String normalizeMethod(String method) {
        return StringUtils.hasText(method) ? method.trim().toUpperCase() : "GET";
    }

    private String sha256Hex(String value) {
        try {
            byte[] input = value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to calculate request body digest for internal panel API.", ex);
        }
    }

    @FunctionalInterface
    public interface HttpRequestBuilderAdapter {
        void header(String name, String value);
    }
}
