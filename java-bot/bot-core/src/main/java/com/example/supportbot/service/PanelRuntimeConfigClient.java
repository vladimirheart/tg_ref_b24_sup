package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PanelRuntimeConfigClient {

    private static final Logger log = LoggerFactory.getLogger(PanelRuntimeConfigClient.class);
    private static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<Long, CachedRuntimeConfig> cache = new ConcurrentHashMap<>();

    public PanelRuntimeConfigClient(IntegrationPanelApiProperties properties,
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

    public Optional<RuntimeConfigSnapshot> findByChannelId(Long channelId) {
        if (!isEnabled() || channelId == null || channelId <= 0) {
            return Optional.empty();
        }
        CachedRuntimeConfig cached = cache.get(channelId);
        if (cached != null && cached.isFresh()) {
            return Optional.of(cached.snapshot());
        }
        return fetch(channelId).map(snapshot -> {
            cache.put(channelId, new CachedRuntimeConfig(snapshot, Instant.now()));
            return snapshot;
        });
    }

    private Optional<RuntimeConfigSnapshot> fetch(Long channelId) {
        String path = "/internal/api/bot/channels/" + channelId + "/runtime-config";
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofSeconds(5))
            .header(AUTH_HEADER, properties.getToken())
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Internal panel runtime config request {} failed with status {}", path, response.statusCode());
                return Optional.empty();
            }
            RuntimeConfigResponse payload = objectMapper.readValue(response.body(), new TypeReference<RuntimeConfigResponse>() {});
            return Optional.of(payload.toSnapshot());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to call internal panel runtime config API {}", path, ex);
            return Optional.empty();
        }
    }

    private URI resolve(String path) {
        String baseUrl = properties.getBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    public record RuntimeConfigSnapshot(Long channelId,
                                        Map<String, Object> botSettings,
                                        Map<String, Object> locationTree,
                                        Map<String, Object> presetDefinitions) {
    }

    private record CachedRuntimeConfig(RuntimeConfigSnapshot snapshot, Instant loadedAt) {
        private boolean isFresh() {
            return loadedAt != null && loadedAt.plus(CACHE_TTL).isAfter(Instant.now());
        }
    }

    private record RuntimeConfigResponse(Long channelId,
                                         Map<String, Object> botSettings,
                                         Map<String, Object> locationTree,
                                         Map<String, Object> presetDefinitions) {
        private RuntimeConfigSnapshot toSnapshot() {
            return new RuntimeConfigSnapshot(
                channelId,
                copyMap(botSettings),
                copyMap(locationTree),
                copyMap(presetDefinitions)
            );
        }

        private static Map<String, Object> copyMap(Map<String, Object> source) {
            return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
        }
    }
}
