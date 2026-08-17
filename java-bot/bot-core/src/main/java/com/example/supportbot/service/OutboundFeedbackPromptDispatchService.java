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

    public OutboundFeedbackPromptDispatchService(ChannelService channelService,
                                                 MessagingService messagingService) {
        this.channelService = channelService;
        this.messagingService = messagingService;
    }

    public void dispatch(OutboundFeedbackPromptEvent event, String routingKey) {
        if (event == null || event.channelId() == null || event.userId() == null || !StringUtils.hasText(event.prompt())) {
            throw new IllegalArgumentException("Outbound feedback prompt event is missing required fields.");
        }
        Optional<Channel> channel = channelService.findById(event.channelId());
        if (channel.isEmpty()) {
            throw new IllegalStateException("Channel " + event.channelId() + " not found for outbound feedback prompt.");
        }
        boolean delivered = messagingService.sendToUser(channel.get(), event.userId(), event.prompt());
        if (!delivered) {
            throw new IllegalStateException("Failed to deliver outbound feedback prompt for channel " + event.channelId());
        }
        log.info("Delivered outbound feedback prompt event {} via routing {}", event.eventId(), routingKey);
    }
}
