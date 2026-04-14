package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MaxApiClient {

    private static final Logger log = LoggerFactory.getLogger(MaxApiClient.class);
    private static final String API_BASE = "https://platform-api.max.ru";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MaxBotProperties properties;

    public MaxApiClient(MaxBotProperties properties) {
        this.properties = properties;
    }

    public boolean sendMessageToUser(Long userId, String text) {
        if (userId == null) {
            return false;
        }
        return send("user_id=" + userId, text);
    }

    public boolean sendMessageToChat(String chatId, String text) {
        if (chatId == null || chatId.isBlank()) {
            return false;
        }
        return send("chat_id=" + chatId, text);
    }

    public PollBatch fetchUpdates(String marker, int limit, int timeoutSeconds) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            log.warn("MAX token is not configured");
            return PollBatch.empty(marker);
        }
        try {
            StringBuilder query = new StringBuilder();
            query.append("limit=").append(Math.max(1, Math.min(limit, 1000)));
            query.append("&timeout=").append(Math.max(1, Math.min(timeoutSeconds, 120)));
            if (marker != null && !marker.isBlank()) {
                query.append("&marker=").append(URLEncoder.encode(marker, StandardCharsets.UTF_8));
            }
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/updates?" + query))
                .header("Authorization", token)
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds + 5)))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("MAX updates returned status {} body={}", response.statusCode(), response.body());
                return PollBatch.empty(marker);
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode updatesNode = root.path("updates");
            List<JsonNode> updates = new ArrayList<>();
            if (updatesNode.isArray()) {
                updatesNode.forEach(updates::add);
            }
            String nextMarker = root.path("marker").asText("");
            if (nextMarker.isBlank()) {
                nextMarker = marker == null ? "" : marker;
            }
            return new PollBatch(updates, nextMarker);
        } catch (Exception ex) {
            log.warn("Failed to fetch MAX updates: {}", ex.getMessage());
            return PollBatch.empty(marker);
        }
    }

    private boolean send(String query, String text) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            log.warn("MAX token is not configured");
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/messages?" + query))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("text", text == null ? "" : text)),
                    StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            log.warn("MAX API returned status {} body={}", response.statusCode(), response.body());
            return false;
        } catch (Exception ex) {
            log.error("Failed to send MAX message", ex);
            return false;
        }
    }

    public record PollBatch(List<JsonNode> updates, String marker) {
        public static PollBatch empty(String marker) {
            return new PollBatch(List.of(), marker == null ? "" : marker);
        }
    }
}
