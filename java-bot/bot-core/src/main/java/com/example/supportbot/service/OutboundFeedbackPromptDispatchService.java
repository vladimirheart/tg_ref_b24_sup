package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final PanelFeedbackPromptDeliveryClient deliveryAuthorization;

    public OutboundFeedbackPromptDispatchService(ChannelService channelService,
                                                 MessagingService messagingService,
                                                 OutboundTransportDeliveryLedgerService deliveryLedgerService,
                                                 PanelFeedbackPromptDeliveryClient deliveryAuthorization) {
        this.channelService = channelService;
        this.messagingService = messagingService;
        this.deliveryLedgerService = deliveryLedgerService;
        this.deliveryAuthorization = deliveryAuthorization;
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
        AtomicBoolean handoffStarted = new AtomicBoolean();
        try {
            Optional<Channel> channel = channelService.findById(event.channelId());
            if (channel.isEmpty()) throw new IllegalStateException("Channel " + event.channelId() + " not found for outbound feedback prompt.");
            requireCurrentChannel(channel.get(), event);
            // Do not authorize before ledger/channel work, cache an earlier allow, or fall back to local business data.
            if (!deliveryAuthorization.authorize(event, routingKey)) {
                throw new IllegalStateException("Outbound feedback prompt recipient context is no longer authorized.");
            }
            // Authorization performs I/O; do not route a channel object changed while awaiting that decision.
            requireCurrentChannel(channel.get(), event);
            boolean delivered = messagingService.sendToUser(channel.get(), event.userId(), event.prompt(), () -> {
                deliveryLedgerService.markSending(event.eventId());
                handoffStarted.set(true);
            });
            if (!delivered || !handoffStarted.get()) throw new IllegalStateException(
                "Outbound feedback prompt delivery did not complete for channel " + event.channelId());
            deliveryLedgerService.markDelivered(event.eventId());
        } catch (RuntimeException exception) {
            try {
                if (handoffStarted.get()) deliveryLedgerService.markUncertain(event.eventId(), exception);
                else deliveryLedgerService.markFailed(event.eventId(), exception);
            } catch (RuntimeException persistenceFailure) { exception.addSuppressed(persistenceFailure); }
            throw exception;
        }
        log.info("Delivered outbound feedback prompt event {} via routing {}", event.eventId(), routingKey);
    }

    private void requireCurrentChannel(Channel channel, OutboundFeedbackPromptEvent event) {
        if (channel == null || !channel.isActive() || event.channelId() == null || event.channelId() <= 0
                || !java.util.Objects.equals(event.channelId(), channel.getId())
                || !StringUtils.hasText(event.platform())
                || !java.util.Objects.equals(event.platform(), channel.getPlatform())) {
            throw new IllegalStateException("Outbound feedback prompt channel identity, activity or platform has changed.");
        }
    }
}
