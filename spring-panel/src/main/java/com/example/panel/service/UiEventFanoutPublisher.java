package com.example.panel.service;

import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.runtime.UiEventFanoutMode;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UiEventFanoutPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UiEventFanoutProperties fanoutProperties;
    private final RuntimeRoleProperties runtimeProperties;

    public UiEventFanoutPublisher(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 UiEventFanoutProperties fanoutProperties,
                                 RuntimeRoleProperties runtimeProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fanoutProperties = fanoutProperties;
        this.runtimeProperties = runtimeProperties;
    }

    /**
     * @return true when the event was accepted by Redis fanout; false means the caller
     * should deliver it process-locally (LOCAL mode or AUTO fallback).
     */
    public boolean publish(String targetUser, String eventName, Map<String, Object> payload) {
        return publish(targetUser, eventName, payload, null);
    }

    public boolean publish(String targetUser, String eventName, Map<String, Object> payload,
                           UiEventTicketScope ticketScope) {
        if (!StringUtils.hasText(eventName)) {
            return true;
        }

        UiEventFanoutMode mode = fanoutProperties.resolvedMode();
        if (mode != UiEventFanoutMode.REDIS) {
            return false;
        }

        UiEventFanoutEnvelope envelope = new UiEventFanoutEnvelope(
            UUID.randomUUID().toString(),
            runtimeProperties.resolvedInstanceId(),
            StringUtils.hasText(targetUser) ? targetUser.toLowerCase(Locale.ROOT) : null,
            eventName.trim(),
            payload,
            ticketScope
        );

        try {
            String serialized = objectMapper.writeValueAsString(envelope);
            redisTemplate.convertAndSend(fanoutProperties.resolvedChannel(), serialized);
            return true;
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new IllegalStateException(
                "Redis UI event fanout failed for event '" + eventName + "'.",
                ex
            );
        }
    }
}
