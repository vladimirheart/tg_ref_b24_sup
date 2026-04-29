package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.MessagingService;
import com.example.supportbot.service.PublicFormConversationLinkService;
import com.example.supportbot.service.SharedConfigService;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MaxWebhookControllerTest {

    @Test
    void handleUpdateTreatsRussianCancelAsCancellationCommand() {
        MaxBotProperties properties = new MaxBotProperties();
        properties.setEnabled(true);
        properties.setChannelId(42L);
        properties.setToken("token");

        ChannelService channelService = mock(ChannelService.class);
        TicketService ticketService = mock(TicketService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        MessagingService messagingService = mock(MessagingService.class);
        PublicFormConversationLinkService linkService = mock(PublicFormConversationLinkService.class);
        BotSettingsService botSettingsService = mock(BotSettingsService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        Channel channel = new Channel();
        when(channelService.resolveConfiguredChannel(42L, "token", "MAX", "max")).thenReturn(channel);

        MaxWebhookController controller = new MaxWebhookController(
                properties,
                channelService,
                ticketService,
                chatHistoryService,
                messagingService,
                linkService,
                botSettingsService,
                sharedConfigService,
                objectMapper
        );

        ObjectNode update = objectMapper.createObjectNode();
        update.put("update_type", "message_created");
        ObjectNode message = update.putObject("message");
        message.putObject("sender").put("user_id", 1001L);
        message.putObject("recipient").put("chat_id", 2002L);
        message.putObject("body").put("text", "отмена");

        ResponseEntity<Map<String, Object>> response = controller.handleUpdate(update, "");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("cancelled", true);
        verify(messagingService).sendToUser(channel, 1001L, "Текущая заявка отменена.");
    }
}
