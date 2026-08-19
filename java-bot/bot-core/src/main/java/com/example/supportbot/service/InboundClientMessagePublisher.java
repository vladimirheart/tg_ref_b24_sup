package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationRabbitProperties;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InboundClientMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(InboundClientMessagePublisher.class);

    private final IntegrationRabbitProperties rabbitProperties;
    private final IntegrationTransportOutboxService integrationTransportOutboxService;

    public InboundClientMessagePublisher(IntegrationTransportOutboxService integrationTransportOutboxService,
                                         IntegrationRabbitProperties rabbitProperties) {
        this.integrationTransportOutboxService = integrationTransportOutboxService;
        this.rabbitProperties = rabbitProperties;
    }

    public void publish(ActiveInboundClientMessageCommand command) {
        if (command == null || command.channel() == null) {
            throw new IllegalArgumentException("Active inbound client message requires a resolved channel.");
        }
        String eventId = UUID.randomUUID().toString();
        OffsetDateTime occurredAt = command.occurredAt() != null ? command.occurredAt() : OffsetDateTime.now();
        String platform = command.channel().getPlatform() != null ? command.channel().getPlatform() : "telegram";
        InboundClientMessageEvent event = new InboundClientMessageEvent(
            eventId,
            "client_message.active_ticket",
            platform,
            command.channel().getId(),
            command.ticketId(),
            command.userId(),
            command.userIdentity(),
            command.username(),
            command.clientName(),
            command.text(),
            command.messageType(),
            command.attachmentPath(),
            command.attachmentName(),
            stringify(command.providerMessageId()),
            stringify(command.replyToProviderMessageId()),
            command.forwardedFrom(),
            occurredAt
        );
        String routingKey = rabbitProperties.routingKeyForPlatform(platform);
        integrationTransportOutboxService.enqueueInboundClientMessage(event, routingKey, rabbitProperties);
        log.info("Queued inbound client message event {} for ticket {} via routing key {}",
            eventId, command.ticketId(), routingKey);
    }

    private String stringify(Long value) {
        return value == null ? null : value.toString();
    }
}
