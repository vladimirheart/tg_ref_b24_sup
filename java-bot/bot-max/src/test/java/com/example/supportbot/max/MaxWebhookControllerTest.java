package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.FeedbackService;
import com.example.supportbot.service.MessagingService;
import com.example.supportbot.service.PublicFormConversationLinkService;
import com.example.supportbot.service.SharedConfigService;
import com.example.supportbot.service.TicketService;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        FeedbackService feedbackService = mock(FeedbackService.class);
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
                feedbackService,
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

    @Test
    void handleUpdateStoresFeedbackForPendingMaxRequest() {
        MaxBotProperties properties = new MaxBotProperties();
        properties.setEnabled(true);
        properties.setChannelId(42L);
        properties.setToken("token");

        ChannelService channelService = mock(ChannelService.class);
        TicketService ticketService = mock(TicketService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        MessagingService messagingService = mock(MessagingService.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        PublicFormConversationLinkService linkService = mock(PublicFormConversationLinkService.class);
        BotSettingsService botSettingsService = mock(BotSettingsService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        Channel channel = new Channel();
        channel.setId(42L);
        when(channelService.resolveConfiguredChannel(42L, "token", "MAX", "max")).thenReturn(channel);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setUserId(1001L);
        request.setChannel(channel);
        request.setTicketId("T-100");
        when(feedbackService.findActiveRequest(1001L, channel)).thenReturn(Optional.of(request));

        BotSettingsDto settings = new BotSettingsDto();
        when(botSettingsService.loadFromChannel(channel)).thenReturn(settings);
        when(botSettingsService.ratingAllowedValues(settings)).thenReturn(Set.of("1", "2", "3", "4", "5"));
        when(botSettingsService.ratingResponseFor(settings, 5)).thenReturn(Optional.of("Спасибо за оценку!"));

        MaxWebhookController controller = new MaxWebhookController(
                properties,
                channelService,
                ticketService,
                chatHistoryService,
                messagingService,
                feedbackService,
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
        message.putObject("body").put("text", "5");

        ResponseEntity<Map<String, Object>> response = controller.handleUpdate(update, "");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("feedback_saved", true);
        assertThat(response.getBody()).containsEntry("rating", 5);
        verify(feedbackService).storeFeedback(request, 5);
        verify(messagingService).sendToUser(channel, 1001L, "Спасибо за оценку!");
        verify(ticketService).findActiveTicketForUser(1001L, "max_1001");
    }

    @Test
    void handleUpdateDoesNotTreatNumericPresetAnswerAsFeedbackWhenSessionIsActive() {
        MaxBotProperties properties = new MaxBotProperties();
        properties.setEnabled(true);
        properties.setChannelId(42L);
        properties.setToken("token");

        ChannelService channelService = mock(ChannelService.class);
        TicketService ticketService = mock(TicketService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        MessagingService messagingService = mock(MessagingService.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        PublicFormConversationLinkService linkService = mock(PublicFormConversationLinkService.class);
        BotSettingsService botSettingsService = mock(BotSettingsService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        Channel channel = new Channel();
        channel.setId(42L);
        when(channelService.resolveConfiguredChannel(42L, "token", "MAX", "max")).thenReturn(channel);
        when(ticketService.findActiveTicketForUser(1001L, "max_1001")).thenReturn(Optional.empty());

        BotSettingsDto settings = new BotSettingsDto();
        when(botSettingsService.loadFromChannel(channel)).thenReturn(settings);

        MaxWebhookController controller = new MaxWebhookController(
                properties,
                channelService,
                ticketService,
                chatHistoryService,
                messagingService,
                feedbackService,
                linkService,
                botSettingsService,
                sharedConfigService,
                objectMapper
        );

        ObjectNode startUpdate = objectMapper.createObjectNode();
        startUpdate.put("update_type", "message_created");
        ObjectNode startMessage = startUpdate.putObject("message");
        startMessage.putObject("sender").put("user_id", 1001L);
        startMessage.putObject("recipient").put("chat_id", 2002L);
        startMessage.putObject("body").put("text", "/start");

        ResponseEntity<Map<String, Object>> startResponse = controller.handleUpdate(startUpdate, "");
        assertThat(startResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ObjectNode numericUpdate = objectMapper.createObjectNode();
        numericUpdate.put("update_type", "message_created");
        ObjectNode numericMessage = numericUpdate.putObject("message");
        numericMessage.putObject("sender").put("user_id", 1001L);
        numericMessage.putObject("recipient").put("chat_id", 2002L);
        numericMessage.putObject("body").put("text", "2");

        ResponseEntity<Map<String, Object>> numericResponse = controller.handleUpdate(numericUpdate, "");

        assertThat(numericResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(numericResponse.getBody()).containsEntry("missing_options", true);
        verify(feedbackService, never()).findActiveRequest(1001L, channel);
    }
}
