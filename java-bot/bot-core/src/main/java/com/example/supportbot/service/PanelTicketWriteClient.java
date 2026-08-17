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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PanelTicketWriteClient {

    private static final Logger log = LoggerFactory.getLogger(PanelTicketWriteClient.class);
    private static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";

    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PanelTicketWriteClient(IntegrationPanelApiProperties properties,
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

    public boolean reopenTicket(String ticketId) {
        return reopenTicket(ticketId, null);
    }

    public boolean reopenTicket(String ticketId, String operatorIdentity) {
        if (!isEnabled() || !StringUtils.hasText(ticketId)) {
            return false;
        }
        return sendMutation(
            "/internal/api/bot/tickets/" + encodePath(ticketId.trim()) + "/reopen",
            "POST",
            StringUtils.hasText(operatorIdentity) ? new TicketActivityRequest(operatorIdentity.trim()) : null
        ).map(MutationResponse::updated).orElse(false);
    }

    public boolean registerActivity(String ticketId, String userIdentity) {
        if (!isEnabled() || !StringUtils.hasText(ticketId)) {
            return false;
        }
        return sendMutation(
            "/internal/api/bot/tickets/" + encodePath(ticketId.trim()) + "/activity",
            "PUT",
            new TicketActivityRequest(userIdentity)
        ).map(MutationResponse::updated).orElse(false);
    }

    public boolean clearActivity(String ticketId) {
        if (!isEnabled() || !StringUtils.hasText(ticketId)) {
            return false;
        }
        return sendMutation(
            "/internal/api/bot/tickets/" + encodePath(ticketId.trim()) + "/activity",
            "DELETE",
            null
        ).map(MutationResponse::updated).orElse(false);
    }

    public boolean recordOperatorRelay(String ticketId,
                                       String message,
                                       Long telegramMessageId,
                                       Long replyToTelegramId,
                                       String operatorIdentity) {
        if (!isEnabled() || !StringUtils.hasText(ticketId) || !StringUtils.hasText(message)) {
            return false;
        }
        return sendMutation(
            "/internal/api/bot/tickets/" + encodePath(ticketId.trim()) + "/operator-relay",
            "POST",
            new OperatorRelayRequest(message.trim(), telegramMessageId, replyToTelegramId, operatorIdentity)
        ).map(MutationResponse::updated).orElse(false);
    }

    public boolean markClientMessageEdited(Long channelId,
                                           Long telegramMessageId,
                                           String message) {
        if (!isEnabled() || channelId == null || telegramMessageId == null || !StringUtils.hasText(message)) {
            return false;
        }
        return sendMutation(
            "/internal/api/bot/channels/" + channelId + "/messages/" + telegramMessageId + "/client-edit",
            "PUT",
            new ClientMessageEditRequest(message.trim())
        ).map(MutationResponse::updated).orElse(false);
    }

    public boolean markOperatorMessageEdited(String ticketId,
                                             Long telegramMessageId,
                                             String message,
                                             String operatorIdentity) {
        if (!isEnabled() || !StringUtils.hasText(ticketId) || telegramMessageId == null || !StringUtils.hasText(message)) {
            return false;
        }
        return sendMutation(
            "/internal/api/bot/tickets/" + encodePath(ticketId.trim()) + "/operator-messages/" + telegramMessageId,
            "PUT",
            new OperatorMessageEditRequest(message.trim(), operatorIdentity)
        ).map(MutationResponse::updated).orElse(false);
    }

    public boolean storeFeedback(Long requestId, Integer rating) {
        if (!isEnabled() || requestId == null || rating == null) {
            return false;
        }
        return sendMutation(
            "/internal/api/bot/feedback/pending/" + requestId + "/submit",
            "POST",
            new FeedbackSubmitRequest(rating)
        ).map(MutationResponse::updated).orElse(false);
    }

    private Optional<MutationResponse> sendMutation(String path, String method, Object body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofSeconds(5))
            .header(AUTH_HEADER, properties.getToken());
        try {
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body),
                    StandardCharsets.UTF_8
                ));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Internal panel write API request {} {} failed with status {}", method, path, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(response.body(), new TypeReference<MutationResponse>() {}));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to call internal panel write API {} {}", method, path, ex);
            return Optional.empty();
        }
    }

    private URI resolve(String path) {
        String baseUrl = properties.getBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    private String encodePath(String value) {
        return value.replace(" ", "%20");
    }

    private record MutationResponse(boolean updated,
                                    boolean exists) {
    }

    private record TicketActivityRequest(String userIdentity) {
    }

    private record OperatorRelayRequest(String message,
                                        Long telegramMessageId,
                                        Long replyToTelegramId,
                                        String operatorIdentity) {
    }

    private record ClientMessageEditRequest(String message) {
    }

    private record OperatorMessageEditRequest(String message,
                                              String operatorIdentity) {
    }

    private record FeedbackSubmitRequest(Integer rating) {
    }
}
