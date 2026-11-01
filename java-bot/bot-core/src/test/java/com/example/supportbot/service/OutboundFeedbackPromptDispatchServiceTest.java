package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.entity.Channel;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboundFeedbackPromptDispatchServiceTest {
    private final PanelFeedbackPromptDeliveryClient authorization = mock(PanelFeedbackPromptDeliveryClient.class);

    @Test
    void dispatchUsesMessagingServiceForResolvedChannel() {
        when(authorization.authorize(any(), anyString())).thenReturn(true);
        ChannelService channelService = mock(ChannelService.class);
        MessagingService messagingService = mock(MessagingService.class);
        OutboundTransportDeliveryLedgerService deliveryLedgerService = mock(OutboundTransportDeliveryLedgerService.class);
        Channel channel = new Channel();
        channel.setId(12L);
        channel.setPlatform("telegram");
        when(channelService.findById(12L)).thenReturn(Optional.of(channel));
        when(messagingService.sendToUser(eq(channel), eq(77L), eq("Prompt"), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable)call.getArgument(3)).run(); return true;
        });
        when(deliveryLedgerService.beginDelivery("evt-1", "feedback.prompt.dispatch", "integration.outbound.feedback.prompt.telegram.channel.12", 12L, 77L, "T-901", 901L))
                .thenReturn(true);

        OutboundFeedbackPromptDispatchService service =
                new OutboundFeedbackPromptDispatchService(channelService, messagingService, deliveryLedgerService, authorization);

        service.dispatch(
            new OutboundFeedbackPromptEvent("evt-1", "feedback.prompt.dispatch", "evt-1", "telegram", 12L, 901L, 77L, "T-901", "Prompt"),
            "integration.outbound.feedback.prompt.telegram.channel.12"
        );

        verify(messagingService).sendToUser(eq(channel), eq(77L), eq("Prompt"), any(Runnable.class));
        verify(deliveryLedgerService).markSending("evt-1");
        verify(deliveryLedgerService).markDelivered("evt-1");
    }

    @Test
    void dispatchFailsWhenDeliveryFails() {
        when(authorization.authorize(any(), anyString())).thenReturn(true);
        ChannelService channelService = mock(ChannelService.class);
        MessagingService messagingService = mock(MessagingService.class);
        OutboundTransportDeliveryLedgerService deliveryLedgerService = mock(OutboundTransportDeliveryLedgerService.class);
        Channel channel = new Channel();
        channel.setId(12L);
        when(channelService.findById(12L)).thenReturn(Optional.of(channel));
        when(messagingService.sendToUser(eq(channel), eq(77L), eq("Prompt"), any(Runnable.class))).thenReturn(false);
        when(deliveryLedgerService.beginDelivery("evt-1", "feedback.prompt.dispatch", "integration.outbound.feedback.prompt.telegram.channel.12", 12L, 77L, "T-901", 901L))
                .thenReturn(true);

        OutboundFeedbackPromptDispatchService service =
                new OutboundFeedbackPromptDispatchService(channelService, messagingService, deliveryLedgerService, authorization);

        assertThatThrownBy(() -> service.dispatch(
            new OutboundFeedbackPromptEvent("evt-1", "feedback.prompt.dispatch", "evt-1", "telegram", 12L, 901L, 77L, "T-901", "Prompt"),
            "integration.outbound.feedback.prompt.telegram.channel.12"
        )).isInstanceOf(IllegalStateException.class);

        verify(deliveryLedgerService).markFailed(org.mockito.ArgumentMatchers.eq("evt-1"), org.mockito.ArgumentMatchers.any(IllegalStateException.class));
    }

    @Test
    void dispatchSkipsAlreadyDeliveredEvent() {
        ChannelService channelService = mock(ChannelService.class);
        MessagingService messagingService = mock(MessagingService.class);
        OutboundTransportDeliveryLedgerService deliveryLedgerService = mock(OutboundTransportDeliveryLedgerService.class);
        when(deliveryLedgerService.beginDelivery("evt-1", "feedback.prompt.dispatch", "integration.outbound.feedback.prompt.telegram.channel.12", 12L, 77L, "T-901", 901L))
                .thenReturn(false);

        OutboundFeedbackPromptDispatchService service =
                new OutboundFeedbackPromptDispatchService(channelService, messagingService, deliveryLedgerService, authorization);

        service.dispatch(
                new OutboundFeedbackPromptEvent("evt-1", "feedback.prompt.dispatch", "evt-1", "telegram", 12L, 901L, 77L, "T-901", "Prompt"),
                "integration.outbound.feedback.prompt.telegram.channel.12"
        );

        verify(channelService, never()).findById(12L);
        verify(messagingService, never()).sendToUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), any(Runnable.class));
    }
}
