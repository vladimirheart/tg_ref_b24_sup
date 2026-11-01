package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** No authorization cache and no local-storage fallback, including when the API is unavailable. */
@Service
public class PanelFeedbackPromptDeliveryClient {
    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper mapper;
    private final PanelApiRequestHeadersFactory headers;
    private final HttpClient http;
    public PanelFeedbackPromptDeliveryClient(IntegrationPanelApiProperties properties,ObjectMapper mapper,PanelApiRequestHeadersFactory headers) {
        this.properties=properties;this.mapper=mapper;this.headers=headers;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
    public boolean authorize(OutboundFeedbackPromptEvent event,String routingKey) {
        if(!properties.isConfigured() || event==null || event.eventId()==null) {
            throw new IllegalStateException("Feedback delivery requires a configured internal panel authorization API.");
        }
        try {
            String base=properties.getBaseUrl().replaceAll("/+$","");
            URI uri=URI.create(base+"/internal/api/bot/feedback-prompts/authorize-delivery");
            String body=mapper.writeValueAsString(new DeliveryRequest(event,routingKey));
            var builder=HttpRequest.newBuilder(uri).timeout(properties.getRequestTimeout())
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
            headers.apply(builder::header,"POST",uri,body,null);
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()!=200)throw new IllegalStateException("Feedback delivery authorization API returned status "+response.statusCode());
            var decision=mapper.readTree(response.body());
            if(decision==null || !decision.isObject() || !decision.path("allowed").isBoolean()
                || !decision.path("eventId").isTextual() || !event.eventId().equals(decision.path("eventId").textValue())) {
                throw new IllegalStateException("Feedback delivery authorization response is missing or inconsistent.");
            }
            return decision.path("allowed").booleanValue();
        } catch(IOException | InterruptedException failure) {
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            throw new IllegalStateException("Feedback delivery authorization API is unavailable.",failure);
        }
    }
    private record DeliveryRequest(OutboundFeedbackPromptEvent event,String routingKey) {}
}
