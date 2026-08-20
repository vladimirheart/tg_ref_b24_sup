package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class BotSessionStoreServiceTest {

    @Test
    void directModePersistsAndLoadsSessionPayloads() {
        BotIngressCoordinationProperties properties = new BotIngressCoordinationProperties();
        properties.setMode("direct");
        properties.setBotSessionTtl(Duration.ofHours(6));
        BotSessionStoreService service = new BotSessionStoreService(
                properties,
                new ObjectMapper(),
                emptyProvider()
        );

        DemoSessionState payload = new DemoSessionState("draft", 2);
        service.save("vk", 17L, 101L, payload);

        BotSessionStoreService.StoredBotSession<DemoSessionState> stored = service
                .load("vk", 17L, 101L, DemoSessionState.class)
                .orElseThrow();

        assertThat(stored.userId()).isEqualTo(101L);
        assertThat(stored.payload()).isEqualTo(payload);
        assertThat(service.loadAll("vk", 17L, DemoSessionState.class))
                .extracting(BotSessionStoreService.StoredBotSession::payload)
                .containsExactly(payload);
    }

    @Test
    void deleteIfUnchangedRemovesOnlyMatchingPayload() {
        BotIngressCoordinationProperties properties = new BotIngressCoordinationProperties();
        properties.setMode("direct");
        BotSessionStoreService service = new BotSessionStoreService(
                properties,
                new ObjectMapper(),
                emptyProvider()
        );

        service.save("max", 9L, 202L, new DemoSessionState("initial", 1));
        String initialRawPayload = service.load("max", 9L, 202L, DemoSessionState.class)
                .orElseThrow()
                .rawPayload();

        service.save("max", 9L, 202L, new DemoSessionState("updated", 2));

        assertThat(service.deleteIfUnchanged("max", 9L, 202L, initialRawPayload)).isFalse();
        assertThat(service.load("max", 9L, 202L, DemoSessionState.class))
                .map(BotSessionStoreService.StoredBotSession::payload)
                .contains(new DemoSessionState("updated", 2));

        String updatedRawPayload = service.load("max", 9L, 202L, DemoSessionState.class)
                .orElseThrow()
                .rawPayload();
        assertThat(service.deleteIfUnchanged("max", 9L, 202L, updatedRawPayload)).isTrue();
        assertThat(service.loadAll("max", 9L, DemoSessionState.class)).isEqualTo(List.of());
    }

    @Test
    void saveIfUnchangedSupportsOptimisticCreateAndReplace() {
        BotIngressCoordinationProperties properties = new BotIngressCoordinationProperties();
        properties.setMode("direct");
        BotSessionStoreService service = new BotSessionStoreService(
            properties,
            new ObjectMapper(),
            emptyProvider()
        );

        assertThat(service.saveIfUnchanged("vk", 17L, 501L, null, new DemoSessionState("created", 1)))
            .isPresent();
        assertThat(service.saveIfUnchanged("vk", 17L, 501L, null, new DemoSessionState("duplicate", 2)))
            .isEmpty();

        String currentRawPayload = service.load("vk", 17L, 501L, DemoSessionState.class)
            .orElseThrow()
            .rawPayload();

        assertThat(service.saveIfUnchanged("vk", 17L, 501L, "{\"stale\":true}", new DemoSessionState("stale", 2)))
            .isEmpty();
        assertThat(service.saveIfUnchanged("vk", 17L, 501L, currentRawPayload, new DemoSessionState("updated", 2)))
            .isPresent();
        assertThat(service.load("vk", 17L, 501L, DemoSessionState.class))
            .map(BotSessionStoreService.StoredBotSession::payload)
            .contains(new DemoSessionState("updated", 2));
    }

    private record DemoSessionState(String status, int step) {
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> emptyProvider() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
