package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final OutboundDeliveryReceiptService receipts;

    public OutboundFeedbackPromptDispatchService(ChannelService channelService,
                                                 MessagingService messagingService,
                                                 OutboundTransportDeliveryLedgerService deliveryLedgerService,
                                                 PanelFeedbackPromptDeliveryClient deliveryAuthorization) {
        this(channelService,messagingService,deliveryLedgerService,deliveryAuthorization,null);
    }

    /** Runtime always uses the migration-gated receipt service; the old constructor is source compatibility only. */
    @Autowired
    public OutboundFeedbackPromptDispatchService(ChannelService channelService,MessagingService messagingService,
            OutboundTransportDeliveryLedgerService deliveryLedgerService,PanelFeedbackPromptDeliveryClient deliveryAuthorization,
            OutboundDeliveryReceiptService receipts) {
        this.channelService = channelService;
        this.messagingService = messagingService;
        this.deliveryLedgerService = deliveryLedgerService;
        this.deliveryAuthorization = deliveryAuthorization;
        this.receipts = receipts;
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
            if(receipts!=null)receipts.reconcile(event.eventId());
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
            Runnable handoff = () -> {
                deliveryLedgerService.markSending(event.eventId());
                handoffStarted.set(true);
            };
            AtomicReference<String> attempt=new AtomicReference<>();
            boolean delivered = receipts==null
                ? messagingService.sendToUser(channel.get(),event.userId(),event.prompt(),handoff)
                : messagingService.sendToUserObserved(channel.get(),event.userId(),event.prompt(),new OutboundDeliveryObserver(){
                    public void beforeProvider(OutboundSenderIdentity sender){
                        handoff.run();attempt.set(receipts.prepare(event.eventId(),sender,event.prompt()));
                    }
                    public void accepted(OutboundSenderIdentity sender,Long userId,ProviderSendResult result){
                        receipts.record(event.eventId(),attempt.get(),sender,userId,result);
                    }
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
