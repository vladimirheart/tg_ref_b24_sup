package com.example.supportbot.vk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.VkBotProperties;
import com.example.supportbot.service.BotIngressCoordinationService;
import com.example.supportbot.service.BotWebhookDeliveryGuardService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class VkCallbackControllerTest {

    private VkSupportBot vkSupportBot;
    private BotIngressCoordinationService ingressCoordinationService;
    private BotWebhookDeliveryGuardService webhookDeliveryGuardService;
    private VkCallbackController controller;

    @BeforeEach
    void setUp() {
        vkSupportBot = mock(VkSupportBot.class);
        ingressCoordinationService = mock(BotIngressCoordinationService.class);
        webhookDeliveryGuardService = mock(BotWebhookDeliveryGuardService.class);

        VkBotProperties properties = new VkBotProperties();
        properties.setWebhookEnabled(true);
        properties.setGroupId(101);
        properties.setChannelId(77L);

        controller = new VkCallbackController(
                vkSupportBot,
                properties,
                ingressCoordinationService,
                webhookDeliveryGuardService
        );
    }

    @Test
    void returnsServiceUnavailableWhenCurrentInstanceIsNotOwner() {
        when(ingressCoordinationService.tryAcquireOrRenew("vk", 77L)).thenReturn(false);

        ResponseEntity<String> response = controller.handle(101, messageNewPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo("not-owner");
        verifyNoInteractions(webhookDeliveryGuardService, vkSupportBot);
    }

    @Test
    void acknowledgesDuplicateWebhookWithoutReprocessing() {
        when(ingressCoordinationService.tryAcquireOrRenew("vk", 77L)).thenReturn(true);
        when(webhookDeliveryGuardService.tryClaim(eq("vk"), eq(77L), anyString()))
                .thenReturn(new BotWebhookDeliveryGuardService.DeliveryClaim(
                        "vk:77:event-1",
                        "token",
                        BotWebhookDeliveryGuardService.ClaimStatus.ALREADY_PROCESSED
                ));

        ResponseEntity<String> response = controller.handle(101, messageNewPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("ok");
        verifyNoInteractions(vkSupportBot);
    }

    private Map<String, Object> messageNewPayload() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", 55);
        message.put("conversation_message_id", 56);
        message.put("peer_id", 999L);
        message.put("from_id", 999L);
        message.put("date", 123456789L);
        message.put("text", "hello");

        Map<String, Object> object = new LinkedHashMap<>();
        object.put("message", message);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message_new");
        payload.put("event_id", "event-1");
        payload.put("object", object);
        return payload;
    }
}
