package com.example.panel.service.integration;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.entity.Channel;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboundFeedbackPromptPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboundFeedbackPromptPublisher.class);

    private final OutboundFeedbackPromptPublishOutboxService outboxService;

    public OutboundFeedbackPromptPublisher(OutboundFeedbackPromptPublishOutboxService outboxService,
                                           IntegrationRabbitProperties rabbitProperties) {
        this.outboxService = outboxService;
        this.rabbitProperties = rabbitProperties;
    }

    private final IntegrationRabbitProperties rabbitProperties;

    public void publish(Long requestId,
                        Channel channel,
                        Long userId,
                        String ticketId,
                        String prompt) {
        if (channel == null || channel.getId() == null) {
            throw new IllegalArgumentException("Feedback prompt dispatch requires a resolved channel with id.");
        }
        String eventId = UUID.randomUUID().toString();
        String platform = channel.getPlatform() != null ? channel.getPlatform() : "telegram";
        OutboundFeedbackPromptEvent event = new OutboundFeedbackPromptEvent(
            eventId,
            "feedback.prompt.dispatch",
            eventId,
            platform,
            channel.getId(),
            requestId,
            userId,
            ticketId,
            prompt
        );
        String routingKey = rabbitProperties.outboundFeedbackPromptRoutingKey(platform, channel.getId());
        outboxService.enqueue(event, routingKey);
        log.info("Queued outbound feedback prompt event {} for request {}", eventId, requestId);
    }
}
