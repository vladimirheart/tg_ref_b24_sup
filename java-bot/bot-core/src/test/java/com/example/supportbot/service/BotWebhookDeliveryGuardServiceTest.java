package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class BotWebhookDeliveryGuardServiceTest {

    @Test
    void directModeTracksInflightAndProcessedDeliveries() {
        BotIngressCoordinationProperties properties = new BotIngressCoordinationProperties();
        properties.setMode("direct");
        BotWebhookDeliveryGuardService service = new BotWebhookDeliveryGuardService(properties, emptyProvider());

        BotWebhookDeliveryGuardService.DeliveryClaim first = service.tryClaim("vk", 17L, "message_new|event-1");
        assertThat(first.acquired()).isTrue();

        BotWebhookDeliveryGuardService.DeliveryClaim inflight = service.tryClaim("vk", 17L, "message_new|event-1");
        assertThat(inflight.inFlight()).isTrue();

        service.markProcessed(first);

        BotWebhookDeliveryGuardService.DeliveryClaim duplicate = service.tryClaim("vk", 17L, "message_new|event-1");
        assertThat(duplicate.alreadyProcessed()).isTrue();
    }

    @Test
    void releasingInflightClaimAllowsRetry() {
        BotIngressCoordinationProperties properties = new BotIngressCoordinationProperties();
        properties.setMode("direct");
        BotWebhookDeliveryGuardService service = new BotWebhookDeliveryGuardService(properties, emptyProvider());

        BotWebhookDeliveryGuardService.DeliveryClaim first = service.tryClaim("max", 42L, "message_created|u1");
        assertThat(first.acquired()).isTrue();

        service.release(first);

        BotWebhookDeliveryGuardService.DeliveryClaim retry = service.tryClaim("max", 42L, "message_created|u1");
        assertThat(retry.acquired()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> emptyProvider() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
