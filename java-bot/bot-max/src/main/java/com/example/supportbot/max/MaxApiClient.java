package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"" + escape(text) + "\"}", StandardCharsets.UTF_8))
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

    private String escape(String source) {
        if (source == null) {
            return "";
        }
        return source.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
