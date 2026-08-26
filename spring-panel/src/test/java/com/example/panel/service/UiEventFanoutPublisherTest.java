package com.example.panel.service;

import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventFanoutPublisherTest {

    @Test
    void localModeRequestsProcessLocalFallback() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UiEventFanoutProperties properties = new UiEventFanoutProperties();
        properties.setMode("local");

        UiEventFanoutPublisher publisher = new UiEventFanoutPublisher(
            redis,
            new ObjectMapper(),
            properties,
            new RuntimeRoleProperties()
        );

        assertThat(publisher.publish(null, "dialogs_changed", Map.of("ticketId", "T-1")))
            .isFalse();
        verify(redis, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void redisModePublishesEnvelopeToSharedChannel() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.convertAndSend(anyString(), anyString())).thenReturn(1L);

        UiEventFanoutProperties properties = new UiEventFanoutProperties();
        properties.setMode("redis");
        properties.setChannel("iguana:test:ui-events");

        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setInstanceId("web-2");

        UiEventFanoutPublisher publisher = new UiEventFanoutPublisher(
            redis,
            new ObjectMapper(),
            properties,
            runtime
        );

        assertThat(publisher.publish("Operator@Example.COM", "notifications_changed", Map.of("reason", "test")))
            .isTrue();
        verify(redis).convertAndSend(
            org.mockito.ArgumentMatchers.eq("iguana:test:ui-events"),
            org.mockito.ArgumentMatchers.contains("\"originInstanceId\":\"web-2\"")
        );
    }

    @Test
    void redisModeFailsClosedWhenFanoutBackendIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.convertAndSend(anyString(), anyString()))
            .thenThrow(new IllegalStateException("redis unavailable"));

        UiEventFanoutProperties properties = new UiEventFanoutProperties();
        properties.setMode("redis");

        UiEventFanoutPublisher publisher = new UiEventFanoutPublisher(
            redis,
            new ObjectMapper(),
            properties,
            new RuntimeRoleProperties()
        );

        assertThatThrownBy(() -> publisher.publish(null, "dialogs_changed", Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Redis UI event fanout failed");
    }

    @Test
    void autoModeUsesProcessLocalFallbackWithoutRedisPublication() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        UiEventFanoutProperties properties = new UiEventFanoutProperties();
        properties.setMode("auto");

        UiEventFanoutPublisher publisher = new UiEventFanoutPublisher(
            redis,
            new ObjectMapper(),
            properties,
            new RuntimeRoleProperties()
        );

        assertThat(publisher.publish(null, "dialogs_changed", Map.of()))
            .isFalse();
        verify(redis, never()).convertAndSend(anyString(), anyString());
    }
}
