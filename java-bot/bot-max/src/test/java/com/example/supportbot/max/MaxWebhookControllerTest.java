package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.service.BlacklistService;
import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.BotIngressCoordinationService;
import com.example.supportbot.service.BotSessionStoreService;
import com.example.supportbot.service.BotWebhookDeliveryGuardService;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.FeedbackService;
import com.example.supportbot.service.MessagingService;
import com.example.supportbot.service.RuntimeConfigService;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

class MaxWebhookControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChannelService channelService;
    private TicketService ticketService;
    private ChatHistoryService chatHistoryService;
    private MessagingService messagingService;
    private FeedbackService feedbackService;
    private BotSettingsService botSettingsService;
    private BotIngressCoordinationService ingressCoordinationService;
    private BotWebhookDeliveryGuardService webhookDeliveryGuardService;
    private MaxWebhookController controller;

    @BeforeEach
    void setUp() {
        MaxBotProperties properties = new MaxBotProperties();
        properties.setEnabled(true);
        properties.setChannelId(55L);
        properties.setWebhookSecret("secret");

        BlacklistService blacklistService = mock(BlacklistService.class);
        channelService = mock(ChannelService.class);
        ticketService = mock(TicketService.class);
        chatHistoryService = mock(ChatHistoryService.class);
        messagingService = mock(MessagingService.class);
        feedbackService = mock(FeedbackService.class);
        botSettingsService = mock(BotSettingsService.class);
        ingressCoordinationService = mock(BotIngressCoordinationService.class);
        webhookDeliveryGuardService = mock(BotWebhookDeliveryGuardService.class);
        BotSessionStoreService sessionStoreService = mock(BotSessionStoreService.class);
        RuntimeConfigService runtimeConfigService = mock(RuntimeConfigService.class);

        controller = new MaxWebhookController(
                properties,
                blacklistService,
                channelService,
                ticketService,
                chatHistoryService,
                messagingService,
                feedbackService,
                botSettingsService,
                ingressCoordinationService,
                webhookDeliveryGuardService,
                sessionStoreService,
                runtimeConfigService,
                mock(AttachmentService.class),
                mock(MaxApiClient.class),
                objectMapper
        );
    }

    @Test
    void returnsServiceUnavailableWhenCurrentInstanceIsNotOwner() throws Exception {
        when(ingressCoordinationService.tryAcquireOrRenew("max", 55L)).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.handleUpdate(messageCreatedUpdate(), "secret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "not-owner");
        verifyNoInteractions(webhookDeliveryGuardService);
    }

    @Test
    void acknowledgesDuplicateDeliveryWithoutReprocessing() throws Exception {
        when(ingressCoordinationService.tryAcquireOrRenew("max", 55L)).thenReturn(true);
        when(webhookDeliveryGuardService.tryClaim(eq("max"), eq(55L), anyString()))
                .thenReturn(new BotWebhookDeliveryGuardService.DeliveryClaim(
                        "max:55:update-1",
                        "token",
                        BotWebhookDeliveryGuardService.ClaimStatus.ALREADY_PROCESSED
                ));

        ResponseEntity<Map<String, Object>> response = controller.handleUpdate(messageCreatedUpdate(), "secret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("ok", true).containsEntry("duplicate", true);
        verifyNoInteractions(channelService, ticketService, messagingService, feedbackService, botSettingsService);
    }

    private JsonNode messageCreatedUpdate() throws Exception {
        return objectMapper.readTree("""
            {
              "update_type": "message_created",
              "update_id": "update-1",
              "message": {
                "sender": { "user_id": 1001 },
                "recipient": { "chat_id": 2002 },
                "body": { "text": "hello" }
              }
            }
            """);
    }
}
