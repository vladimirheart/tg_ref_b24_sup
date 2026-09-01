package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PanelChannelClient {

    private static final Logger log = LoggerFactory.getLogger(PanelChannelClient.class);

    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final PanelApiRequestHeadersFactory requestHeadersFactory;
    private final HttpClient httpClient;

    public PanelChannelClient(IntegrationPanelApiProperties properties,
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

    public Optional<Channel> findById(Long channelId) {
        if (!isEnabled() || channelId == null || channelId <= 0) {
            return Optional.empty();
        }
        return send("/internal/api/bot/channels/" + channelId, "GET", null);
    }

    public Optional<Channel> resolveConfiguredChannel(Long channelId,
                                                      String token,
                                                      String channelName,
                                                      String platform) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return send(
            "/internal/api/bot/channels/resolve",
            "POST",
            new ChannelResolveRequest(channelId, token, channelName, platform)
        );
    }

    public Optional<Channel> updateSupportChatId(Long channelId, String supportChatId) {
        if (!isEnabled() || channelId == null || !StringUtils.hasText(supportChatId)) {
            return Optional.empty();
        }
        return send(
            "/internal/api/bot/channels/" + channelId + "/support-chat",
            "PUT",
            new SupportChatUpdateRequest(supportChatId.trim())
        );
    }

    private Optional<Channel> send(String path, String method, Object body) {
        try {
            URI uri = resolve(path);
            String requestBody = body != null ? objectMapper.writeValueAsString(body) : null;
            String idempotencyKey = !"GET".equalsIgnoreCase(method)
                ? requestHeadersFactory.newIdempotencyKey("panel-channel", path)
                : null;
            int maxAttempts = Math.max(1, properties.getRetryAttempts() + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    HttpResponse<String> response = httpClient.send(
                        buildRequest(uri, method, requestBody, idempotencyKey),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                    );
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        ChannelResponse payload = objectMapper.readValue(response.body(), new TypeReference<ChannelResponse>() {});
                        return Optional.of(payload.toChannel());
                    }
                    if (!isRetryableStatus(response.statusCode()) || attempt == maxAttempts) {
                        log.warn("Internal panel channel API request {} {} failed with status {} after {}/{} attempt(s)",
                            method, path, response.statusCode(), attempt, maxAttempts);
                        return Optional.empty();
                    }
                    log.warn("Internal panel channel API request {} {} returned {}; retrying {}/{}",
                        method, path, response.statusCode(), attempt + 1, maxAttempts);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                } catch (IOException ex) {
                    if (attempt == maxAttempts) {
                        log.warn("Internal panel channel API request {} {} failed after {}/{} attempt(s): {}",
                            method, path, attempt, maxAttempts, ex.getClass().getSimpleName());
                        return Optional.empty();
                    }
                    log.warn("Internal panel channel API request {} {} failed with {}; retrying {}/{}",
                        method, path, ex.getClass().getSimpleName(), attempt + 1, maxAttempts);
                }
                if (!backoff()) {
                    return Optional.empty();
                }
            }
        } catch (IOException ex) {
            log.warn("Unable to serialize internal panel channel API request {}: {}", path, ex.getClass().getSimpleName());
            return Optional.empty();
        }
        return Optional.empty();
    }

    private HttpRequest buildRequest(URI uri, String method, String requestBody, String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(properties.getRequestTimeout());
        requestHeadersFactory.apply(builder::header, method, uri, requestBody, idempotencyKey);
        if (requestBody != null) {
            return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        }
        return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    private boolean backoff() {
        try {
            long delayMillis = Math.max(0, properties.getRetryBackoff().toMillis());
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private URI resolve(String path) {
        String baseUrl = properties.getBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    private record ChannelResolveRequest(Long channelId,
                                         String token,
                                         String channelName,
                                         String platform) {
    }

    private record SupportChatUpdateRequest(String supportChatId) {
    }

    private record ChannelResponse(Long id,
                                   String token,
                                   String channelName,
                                   String questionsCfg,
                                   Integer maxQuestions,
                                   Boolean active,
                                   String botUsername,
                                   String questionTemplateId,
                                   String ratingTemplateId,
                                   String publicId,
                                   String autoActionTemplateId,
                                   String description,
                                   String filters,
                                   String deliverySettings,
                                   String platform,
                                   String platformConfig,
                                   Long credentialId,
                                   String supportChatId) {
        private Channel toChannel() {
            Channel channel = new Channel();
            channel.setId(id);
            channel.setToken(token);
            channel.setChannelName(channelName);
            channel.setQuestionsCfg(questionsCfg);
            channel.setMaxQuestions(maxQuestions);
            channel.setActive(active != null && active);
            channel.setBotUsername(botUsername);
            channel.setQuestionTemplateId(questionTemplateId);
            channel.setRatingTemplateId(ratingTemplateId);
            channel.setPublicId(publicId);
            channel.setAutoActionTemplateId(autoActionTemplateId);
            channel.setDescription(description);
            channel.setFilters(filters);
            channel.setDeliverySettings(deliverySettings);
            channel.setPlatform(platform);
            channel.setPlatformConfig(platformConfig);
            channel.setCredentialId(credentialId);
            channel.setSupportChatId(supportChatId);
            return channel;
        }
    }
}
