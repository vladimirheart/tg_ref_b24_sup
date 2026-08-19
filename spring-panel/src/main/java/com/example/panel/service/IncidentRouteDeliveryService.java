package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IncidentRouteDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(IncidentRouteDeliveryService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final NotificationService notificationService;
    private final NotificationRoutingService notificationRoutingService;
    private final ObjectMapper objectMapper;

    public IncidentRouteDeliveryService(NotificationService notificationService,
                                        NotificationRoutingService notificationRoutingService,
                                        ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.notificationRoutingService = notificationRoutingService;
        this.objectMapper = objectMapper;
    }

    public void deliver(String routeType,
                        String routeTarget,
                        String messageText,
                        String incidentUrl,
                        Map<String, Object> payload) {
        String normalizedType = normalizeRouteType(routeType);
        String normalizedTarget = normalizeTarget(routeTarget);
        String safeText = requiredText(messageText, "Incident route delivery requires message text.");
        switch (normalizedType) {
            case "webhook" -> sendWebhook(normalizedTarget, payload);
            case "user" -> notificationService.notifyUser(normalizedTarget, safeText, incidentUrl);
            case "users" -> {
                Set<String> recipients = splitRecipients(normalizedTarget);
                if (recipients.isEmpty()) {
                    throw new IllegalStateException("Incident route users target is empty.");
                }
                notificationService.notifyUsers(recipients, safeText, incidentUrl);
            }
            case "all_operators" -> notificationService.notifyAllOperators(safeText, incidentUrl, null);
            case "department" -> {
                Set<String> recipients = notificationRoutingService.findDepartmentRecipients(normalizedTarget);
                if (recipients.isEmpty()) {
                    throw new IllegalStateException("Incident route department has no active recipients: " + normalizedTarget);
                }
                notificationService.notifyUsers(recipients, safeText, incidentUrl);
            }
            default -> throw new IllegalStateException("Unsupported incident route type: " + routeType);
        }
    }

    private void sendWebhook(String routeTarget,
                             Map<String, Object> payload) {
        if (!StringUtils.hasText(routeTarget)) {
            throw new IllegalStateException("Incident webhook route target is empty.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(new URI(routeTarget))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Incident webhook responded with status="
                    + response.statusCode() + " body=" + truncate(response.body(), 300));
            }
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid incident webhook URL: " + routeTarget, ex);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize incident route payload.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("I/O failure while delivering incident webhook.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delivering incident webhook.", ex);
        }
    }

    private String normalizeRouteType(String routeType) {
        String normalized = requiredText(routeType, "Incident route type is required.")
            .trim()
            .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "operator", "operators", "all_operators" -> "all_operators";
            case "user" -> "user";
            case "users" -> "users";
            case "department" -> "department";
            case "webhook" -> "webhook";
            default -> normalized;
        };
    }

    private String normalizeTarget(String routeTarget) {
        if (!StringUtils.hasText(routeTarget)) {
            return null;
        }
        return routeTarget.trim();
    }

    private Set<String> splitRecipients(String routeTarget) {
        Set<String> recipients = new LinkedHashSet<>();
        if (!StringUtils.hasText(routeTarget)) {
            return recipients;
        }
        for (String chunk : routeTarget.split("[,;\\n]+")) {
            String value = chunk == null ? null : chunk.trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(value)) {
                recipients.add(value);
            }
        }
        return recipients;
    }

    private String requiredText(String value,
                                String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private String truncate(String value,
                            int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        String truncated = value.substring(0, maxLength);
        log.debug("Incident webhook response truncated to {} chars", maxLength);
        return truncated + "...";
    }
}
