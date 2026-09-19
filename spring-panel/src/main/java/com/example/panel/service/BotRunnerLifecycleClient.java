package com.example.panel.service;

import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BotRunnerLifecycleClient {

    private final ObjectMapper objectMapper;
    private final InternalBotApiProperties internalApiProperties;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final HttpClient httpClient;

    @Autowired
    public BotRunnerLifecycleClient(ObjectMapper objectMapper,
                                    InternalBotApiProperties internalApiProperties,
                                    @Value("${app.bots.runner-api.base-url:http://bot-runner:8080}") String baseUrl,
                                    @Value("${app.bots.runner-api.request-timeout:110s}") Duration requestTimeout) {
        this(
            objectMapper,
            internalApiProperties,
            baseUrl,
            requestTimeout,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        );
    }

    BotRunnerLifecycleClient(ObjectMapper objectMapper,
                             InternalBotApiProperties internalApiProperties,
                             String baseUrl,
                             Duration requestTimeout,
                             HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.internalApiProperties = internalApiProperties;
        String normalizedBaseUrl = Objects.toString(baseUrl, "").trim().replaceAll("/+$", "");
        if (!StringUtils.hasText(normalizedBaseUrl)) {
            throw new IllegalArgumentException("bot-runner internal API base URL is empty");
        }
        this.baseUri = URI.create(normalizedBaseUrl);
        this.requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
            ? Duration.ofSeconds(110)
            : requestTimeout;
        this.httpClient = httpClient;
    }

    public BotLifecycleCommandResult start(Long channelId) {
        return execute(channelId, "start");
    }

    public BotLifecycleCommandResult stop(Long channelId) {
        return execute(channelId, "stop");
    }

    private BotLifecycleCommandResult execute(Long channelId, String action) {
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("channelId must be positive");
        }
        String commandId = "bot-lifecycle:" + action + ":" + channelId + ":" + UUID.randomUUID();
        String path = "/internal/api/bot/runtime/" + channelId + "/" + action;
        URI uri = baseUri.resolve(path);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header(InternalBotApiRequestGuardService.AUTH_HEADER, internalApiProperties.getToken())
            .header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, commandId)
            .POST(HttpRequest.BodyPublishers.noBody());

        if (internalApiProperties.isRequireRequestSignature()) {
            String timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
            request.header(InternalBotApiRequestGuardService.TIMESTAMP_HEADER, timestamp);
            request.header(
                InternalBotApiRequestGuardService.SIGNATURE_HEADER,
                sign("POST", canonicalPath(uri), timestamp)
            );
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bot-runner lifecycle request was interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("bot-runner lifecycle request failed", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("bot-runner lifecycle request returned HTTP " + response.statusCode());
        }

        BotLifecycleCommandResult result;
        try {
            result = objectMapper.readValue(response.body(), BotLifecycleCommandResult.class);
        } catch (IOException ex) {
            throw new IllegalStateException("bot-runner lifecycle acknowledgement is invalid", ex);
        }

        if (result == null
                || !Objects.equals(commandId, result.commandId())
                || !Objects.equals(channelId, result.channelId())
                || !Objects.equals(action, result.action())
                || !StringUtils.hasText(result.runnerInstanceId())) {
            throw new IllegalStateException("bot-runner lifecycle acknowledgement does not match the command");
        }
        return result;
    }

    private String canonicalPath(URI uri) {
        String path = uri != null && StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
        String query = uri != null ? uri.getRawQuery() : null;
        return StringUtils.hasText(query) ? path + "?" + query : path;
    }

    private String sign(String method, String canonicalPath, String timestamp) {
        String canonical = method.trim().toUpperCase()
            + "\n"
            + canonicalPath
            + "\n"
            + timestamp
            + "\n"
            + sha256Hex(new byte[0]);
        String secret = StringUtils.hasText(internalApiProperties.getSignatureSecret())
            ? internalApiProperties.getSignatureSecret().trim()
            : Objects.toString(internalApiProperties.getToken(), "");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign bot-runner lifecycle request", ex);
        }
    }

    private String sha256Hex(byte[] value) {
        try {
            byte[] payload = value != null ? value : new byte[0];
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash bot-runner lifecycle request body", ex);
        }
    }
}
