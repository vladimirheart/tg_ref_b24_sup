package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class BotIngressCoordinationServiceTest {

    @Test
    void directModeAlwaysTreatsCurrentInstanceAsOwner() {
        BotIngressCoordinationProperties properties = new BotIngressCoordinationProperties();
        properties.setMode("direct");
        BotIngressCoordinationService service = new BotIngressCoordinationService(properties, emptyProvider());

        assertThat(service.tryAcquireOrRenew("telegram", 17L)).isTrue();
        assertThat(service.isCurrentOwner("telegram", 17L)).isTrue();

        service.release("telegram", 17L);

        assertThat(service.isCurrentOwner("telegram", 17L)).isTrue();
    }

    @Test
    void redisModeRequiresRedisTemplate() {
        BotIngressCoordinationProperties properties = new BotIngressCoordinationProperties();
        properties.setMode("redis");
        BotIngressCoordinationService service = new BotIngressCoordinationService(properties, emptyProvider());

        assertThatThrownBy(() -> service.tryAcquireOrRenew("vk", 42L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("StringRedisTemplate");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> emptyProvider() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
