package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.entity.ClientUnblockRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PanelBlacklistClient {

    private static final Logger log = LoggerFactory.getLogger(PanelBlacklistClient.class);

    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final PanelApiRequestHeadersFactory requestHeadersFactory;
    private final HttpClient httpClient;

    public PanelBlacklistClient(IntegrationPanelApiProperties properties,
                                ObjectMapper objectMapper,
                                PanelApiRequestHeadersFactory requestHeadersFactory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.requestHeadersFactory = requestHeadersFactory;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public boolean isEnabled() {
        return properties.isConfigured();
    }

    public Optional<ResolvedBlacklistStatus> resolveStatus(long userId, List<String> aliases) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        StringBuilder path = new StringBuilder("/internal/api/bot/blacklist/status?userId=").append(userId);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    path.append("&alias=").append(encode(alias.trim()));
                }
            }
        }
        return send(path.toString(), "GET", null, new TypeReference<ResolvedBlacklistStatusResponse>() {})
            .map(ResolvedBlacklistStatusResponse::toLookup);
    }

    public Optional<UnblockRequestDecision> requestUnblock(long userId,
                                                           String reason,
                                                           Long channelId,
                                                           Duration cooldown) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return send(
            "/internal/api/bot/blacklist/unblock-requests",
            "POST",
            new UnblockRequestCreateRequest(
                userId,
                reason,
                channelId,
                cooldown != null ? Math.max(0L, cooldown.getSeconds()) : 0L
            ),
            new TypeReference<UnblockRequestDecisionResponse>() {}
        ).map(UnblockRequestDecisionResponse::toLookup);
    }

    public Optional<PendingUnblockSummary> pendingSummary(int limit) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return send(
            "/internal/api/bot/unblock-requests/pending-summary?limit=" + Math.max(0, limit),
            "GET",
            null,
            new TypeReference<PendingUnblockSummaryResponse>() {}
        ).map(PendingUnblockSummaryResponse::toLookup);
    }

    private <T> Optional<T> send(String path,
                                 String method,
                                 Object body,
                                 TypeReference<T> typeReference) {
        try {
            URI uri = resolve(path);
            String requestBody = body != null ? objectMapper.writeValueAsString(body) : null;
            String idempotencyKey = !"GET".equalsIgnoreCase(method)
                ? requestHeadersFactory.newIdempotencyKey("panel-blacklist", path)
                : null;
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout());
            requestHeadersFactory.apply(builder::header, method, uri, requestBody, idempotencyKey);
            if (requestBody != null) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Internal panel blacklist API request {} {} failed with status {}", method, path, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(response.body(), typeReference));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to call internal panel blacklist API {} {}", method, path, ex);
            return Optional.empty();
        }
    }

    private URI resolve(String path) {
        String baseUrl = properties.getBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record ResolvedBlacklistStatus(String matchedUserId,
                                          boolean blacklisted,
                                          boolean unblockRequested) {
    }

    public record UnblockRequestDecision(ClientUnblockRequest request,
                                         boolean created,
                                         Duration retryAfter) {
    }

    public record PendingUnblockSummary(long pendingCount,
                                        List<ClientUnblockRequest> recentRequests) {
    }

    private record UnblockRequestCreateRequest(Long userId,
                                               String reason,
                                               Long channelId,
                                               Long cooldownSeconds) {
    }

    private record ResolvedBlacklistStatusResponse(String matchedUserId,
                                                   boolean blacklisted,
                                                   boolean unblockRequested) {
        private ResolvedBlacklistStatus toLookup() {
            return new ResolvedBlacklistStatus(matchedUserId, blacklisted, unblockRequested);
        }
    }

    private record UnblockRequestDecisionResponse(ClientUnblockRequestResponse request,
                                                  boolean created,
                                                  Duration retryAfter) {
        private UnblockRequestDecision toLookup() {
            return new UnblockRequestDecision(
                request != null ? request.toEntity() : null,
                created,
                retryAfter != null ? retryAfter : Duration.ZERO
            );
        }
    }

    private record PendingUnblockSummaryResponse(long pendingCount,
                                                 List<ClientUnblockRequestResponse> recentRequests) {
        private PendingUnblockSummary toLookup() {
            List<ClientUnblockRequest> mapped = new ArrayList<>();
            if (recentRequests != null) {
                for (ClientUnblockRequestResponse request : recentRequests) {
                    if (request != null) {
                        mapped.add(request.toEntity());
                    }
                }
            }
            return new PendingUnblockSummary(pendingCount, mapped);
        }
    }

    private record ClientUnblockRequestResponse(Long id,
                                                String userId,
                                                Long channelId,
                                                String reason,
                                                OffsetDateTime createdAt,
                                                String status) {
        private ClientUnblockRequest toEntity() {
            ClientUnblockRequest entity = new ClientUnblockRequest();
            entity.setId(id);
            entity.setUserId(userId);
            entity.setChannelId(channelId);
            entity.setReason(reason);
            entity.setCreatedAt(createdAt);
            entity.setStatus(status);
            return entity;
        }
    }
}
