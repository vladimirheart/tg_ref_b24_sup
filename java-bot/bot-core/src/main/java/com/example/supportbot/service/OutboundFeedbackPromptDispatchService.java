package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OutboundFeedbackPromptDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OutboundFeedbackPromptDispatchService.class);

    private final ChannelService channelService;
    private final MessagingService messagingService;
    private final OutboundTransportDeliveryLedgerService deliveryLedgerService;

    public OutboundFeedbackPromptDispatchService(ChannelService channelService,
                                                 MessagingService messagingService,
                                                 OutboundTransportDeliveryLedgerService deliveryLedgerService) {
        this.channelService = channelService;
        this.messagingService = messagingService;
        this.deliveryLedgerService = deliveryLedgerService;
    }

    public void dispatch(OutboundFeedbackPromptEvent event, String routingKey) {
        if (event == null || event.channelId() == null || event.userId() == null || !StringUtils.hasText(event.prompt())) {
            throw new IllegalArgumentException("Outbound feedback prompt event is missing required fields.");
        }
        if (!deliveryLedgerService.beginDelivery(
                event.eventId(),
                event.eventType(),
                routingKey,
                event.channelId(),
                event.userId(),
                event.ticketId(),
                event.requestId())) {
            log.info("Skipping already processed outbound feedback prompt event {} via routing {}", event.eventId(), routingKey);
            return;
        }
        Optional<Channel> channel = channelService.findById(event.channelId());
        if (channel.isEmpty()) {
            IllegalStateException exception = new IllegalStateException(
                    "Channel " + event.channelId() + " not found for outbound feedback prompt.");
            deliveryLedgerService.markFailed(event.eventId(), exception);
            throw exception;
        }
        boolean delivered = messagingService.sendToUser(channel.get(), event.userId(), event.prompt());
        if (!delivered) {
            IllegalStateException exception = new IllegalStateException(
                    "Failed to deliver outbound feedback prompt for channel " + event.channelId());
            deliveryLedgerService.markFailed(event.eventId(), exception);
            throw exception;
        }
        deliveryLedgerService.markDelivered(event.eventId());
        log.info("Delivered outbound feedback prompt event {} via routing {}", event.eventId(), routingKey);
    }
}
