package com.example.panel.service;

import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.runtime.UiEventFanoutMode;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UiEventFanoutPublisher {

    private static final Logger log = LoggerFactory.getLogger(UiEventFanoutPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UiEventFanoutProperties fanoutProperties;
    private final RuntimeRoleProperties runtimeProperties;
    private final AtomicBoolean autoFallbackWarningLogged = new AtomicBoolean(false);

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
        if (!StringUtils.hasText(eventName)) {
            return true;
        }

        UiEventFanoutMode mode = fanoutProperties.resolvedMode();
        if (mode == UiEventFanoutMode.LOCAL) {
            return false;
        }

        UiEventFanoutEnvelope envelope = new UiEventFanoutEnvelope(
            UUID.randomUUID().toString(),
            runtimeProperties.resolvedInstanceId(),
            StringUtils.hasText(targetUser) ? targetUser.trim().toLowerCase() : null,
            eventName.trim(),
            payload
        );

        try {
            String serialized = objectMapper.writeValueAsString(envelope);
            redisTemplate.convertAndSend(fanoutProperties.resolvedChannel(), serialized);
            autoFallbackWarningLogged.set(false);
            return true;
        } catch (JsonProcessingException | RuntimeException ex) {
            if (mode == UiEventFanoutMode.REDIS) {
                throw new IllegalStateException(
                    "Redis UI event fanout failed for event '" + eventName + "'.",
                    ex
                );
            }

            if (autoFallbackWarningLogged.compareAndSet(false, true)) {
                log.warn(
                    "Redis UI event fanout is unavailable in AUTO mode; falling back to process-local SSE delivery: {}",
                    ex.getMessage()
                );
            }
            return false;
        }
    }
}
