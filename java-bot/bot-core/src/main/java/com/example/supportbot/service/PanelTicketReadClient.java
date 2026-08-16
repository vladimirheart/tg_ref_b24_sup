package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.entity.TicketMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PanelTicketReadClient {

    private static final Logger log = LoggerFactory.getLogger(PanelTicketReadClient.class);
    private static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";

    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PanelTicketReadClient(IntegrationPanelApiProperties properties,
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

    public Optional<TicketActive> findActiveTicket(Long userId, String username, Long channelId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        StringBuilder path = new StringBuilder("/internal/api/bot/tickets/active?");
        boolean appended = false;
        if (userId != null) {
            path.append("userId=").append(userId);
            appended = true;
        }
        if (StringUtils.hasText(username)) {
            if (appended) {
                path.append('&');
            }
            path.append("username=").append(encode(username.trim()));
            appended = true;
        }
        if (channelId != null) {
            if (appended) {
                path.append('&');
            }
            path.append("channelId=").append(channelId);
        }
        return send(path.toString(), new TypeReference<ActiveTicketResponse>() {})
            .map(response -> {
                TicketActive active = new TicketActive();
                active.setTicketId(response.ticketId());
                active.setUser(response.userIdentity());
                active.setLastSeen(response.lastSeen());
                return active;
            });
    }

    public Optional<TicketService.TicketWithUser> findTicket(String ticketId) {
        if (!isEnabled() || !StringUtils.hasText(ticketId)) {
            return Optional.empty();
        }
        return send("/internal/api/bot/tickets/" + encodePath(ticketId.trim()), new TypeReference<TicketLookupResponse>() {})
            .map(response -> new TicketService.TicketWithUser(response.userId(), response.ticketId(), response.status()));
    }

    public Optional<String> resolveRequestNumber(String ticketId) {
        if (!isEnabled() || !StringUtils.hasText(ticketId)) {
            return Optional.empty();
        }
        return send(
            "/internal/api/bot/tickets/" + encodePath(ticketId.trim()) + "/request-number",
            new TypeReference<RequestNumberResponse>() {}
        ).map(RequestNumberResponse::requestNumber);
    }

    public Optional<TicketMessage> findLastTicketContext(Long userId) {
        if (!isEnabled() || userId == null) {
            return Optional.empty();
        }
        return send(
            "/internal/api/bot/users/" + userId + "/last-ticket-context",
            new TypeReference<LastTicketContextResponse>() {}
        ).map(response -> {
            TicketMessage message = new TicketMessage();
            message.setTicketId(response.ticketId());
            message.setBusiness(response.business());
            message.setLocationType(response.locationType());
            message.setCity(response.city());
            message.setLocationName(response.locationName());
            message.setProblem(response.problem());
            message.setCreatedAt(response.createdAt());
            message.setCreatedDate(response.createdDate());
            message.setId(response.messageId());
            return message;
        });
    }

    public List<TicketService.TicketSummary> findRecentTickets(Long userId, int limit) {
        if (!isEnabled() || userId == null || limit <= 0) {
            return List.of();
        }
        return sendList(
            "/internal/api/bot/users/" + userId + "/tickets/recent?limit=" + limit,
            new TypeReference<List<TicketSummaryResponse>>() {}
        ).stream()
            .map(item -> new TicketService.TicketSummary(
                item.ticketId(),
                item.requestNumber(),
                item.problem(),
                item.business(),
                item.locationType(),
                item.city(),
                item.locationName(),
                item.rating(),
                item.createdAt()
            ))
            .toList();
    }

    private <T> Optional<T> send(String path, TypeReference<T> typeReference) {
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofSeconds(5))
            .header(AUTH_HEADER, properties.getToken())
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Internal panel API request {} failed with status {}", path, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(response.body(), typeReference));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to call internal panel API path {}", path, ex);
            return Optional.empty();
        }
    }

    private <T> List<T> sendList(String path, TypeReference<List<T>> typeReference) {
        return send(path, typeReference).orElse(List.of());
    }

    private URI resolve(String path) {
        String baseUrl = properties.getBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return value.replace(" ", "%20");
    }

    private record ActiveTicketResponse(String ticketId,
                                        String userIdentity,
                                        OffsetDateTime lastSeen) {
    }

    private record TicketLookupResponse(Long userId,
                                        String ticketId,
                                        String status) {
    }

    private record RequestNumberResponse(String ticketId,
                                         String requestNumber) {
    }

    private record LastTicketContextResponse(String ticketId,
                                             String business,
                                             String locationType,
                                             String city,
                                             String locationName,
                                             String problem,
                                             OffsetDateTime createdAt,
                                             LocalDate createdDate,
                                             Long messageId) {
    }

    private record TicketSummaryResponse(String ticketId,
                                         String requestNumber,
                                         String problem,
                                         String business,
                                         String locationType,
                                         String city,
                                         String locationName,
                                         Integer rating,
                                         OffsetDateTime createdAt) {
    }
}
