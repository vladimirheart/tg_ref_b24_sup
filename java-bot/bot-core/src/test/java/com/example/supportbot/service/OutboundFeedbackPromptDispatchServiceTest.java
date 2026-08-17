package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.entity.Channel;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboundFeedbackPromptDispatchServiceTest {

    @Test
    void dispatchUsesMessagingServiceForResolvedChannel() {
        ChannelService channelService = mock(ChannelService.class);
        MessagingService messagingService = mock(MessagingService.class);
        Channel channel = new Channel();
        channel.setId(12L);
        channel.setPlatform("telegram");
        when(channelService.findById(12L)).thenReturn(Optional.of(channel));
        when(messagingService.sendToUser(channel, 77L, "Prompt")).thenReturn(true);

        OutboundFeedbackPromptDispatchService service = new OutboundFeedbackPromptDispatchService(channelService, messagingService);

        service.dispatch(
            new OutboundFeedbackPromptEvent("evt-1", "feedback.prompt.dispatch", "evt-1", "telegram", 12L, 901L, 77L, "T-901", "Prompt"),
            "integration.outbound.feedback.prompt.telegram.channel.12"
        );

        verify(messagingService).sendToUser(channel, 77L, "Prompt");
    }

    @Test
    void dispatchFailsWhenDeliveryFails() {
        ChannelService channelService = mock(ChannelService.class);
        MessagingService messagingService = mock(MessagingService.class);
        Channel channel = new Channel();
        channel.setId(12L);
        when(channelService.findById(12L)).thenReturn(Optional.of(channel));
        when(messagingService.sendToUser(channel, 77L, "Prompt")).thenReturn(false);

        OutboundFeedbackPromptDispatchService service = new OutboundFeedbackPromptDispatchService(channelService, messagingService);

        assertThatThrownBy(() -> service.dispatch(
            new OutboundFeedbackPromptEvent("evt-1", "feedback.prompt.dispatch", "evt-1", "telegram", 12L, 901L, 77L, "T-901", "Prompt"),
            "integration.outbound.feedback.prompt.telegram.channel.12"
        )).isInstanceOf(IllegalStateException.class);
    }
}
