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
    private static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";

    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PanelChannelClient(IntegrationPanelApiProperties properties,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public boolean isEnabled() {
        return properties.isConfigured();
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofSeconds(5))
            .header(AUTH_HEADER, properties.getToken())
            .header("Content-Type", "application/json");
        try {
            builder.method(method, HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(body),
                StandardCharsets.UTF_8
            ));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Internal panel channel API request {} {} failed with status {}", method, path, response.statusCode());
                return Optional.empty();
            }
            ChannelResponse payload = objectMapper.readValue(response.body(), new TypeReference<ChannelResponse>() {});
            return Optional.of(payload.toChannel());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to call internal panel channel API {} {}", method, path, ex);
            return Optional.empty();
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
