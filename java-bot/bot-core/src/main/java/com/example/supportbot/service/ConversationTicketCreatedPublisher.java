package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationRabbitProperties;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationTicketCreatedPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConversationTicketCreatedPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final IntegrationRabbitProperties rabbitProperties;

    public ConversationTicketCreatedPublisher(RabbitTemplate rabbitTemplate,
                                              IntegrationRabbitProperties rabbitProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
    }

    public void publish(ConversationTicketCreationCommand command,
                        String ticketId,
                        String business,
                        String locationType,
                        String city,
                        String locationName,
                        String problem) {
        if (command == null || command.channel() == null) {
            throw new IllegalArgumentException("Conversation ticket creation requires a resolved channel.");
        }
        String eventId = UUID.randomUUID().toString();
        String platform = command.channel().getPlatform() != null ? command.channel().getPlatform() : "telegram";
        ConversationTicketCreatedEvent event = new ConversationTicketCreatedEvent(
            eventId,
            "ticket.created.initial_contact",
            platform,
            command.channel().getId(),
            ticketId,
            command.userId(),
            command.userIdentity(),
            command.username(),
            command.clientName(),
            business,
            locationType,
            city,
            locationName,
            problem,
            command.startedAt(),
            mapAttributes(command.attributes()),
            command.historyEntries()
        );
        rabbitTemplate.convertAndSend(
            rabbitProperties.getInboundExchange(),
            rabbitProperties.getRoutingTicketCreated(),
            event,
            message -> {
                message.getMessageProperties().setMessageId(eventId);
                message.getMessageProperties().setCorrelationId(eventId);
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
        log.info("Published conversation ticket creation event {} for ticket {}", eventId, ticketId);
    }

    private List<ConversationTicketCreatedEvent.TicketAttributePayload> mapAttributes(
        List<TicketService.TicketAttributeInput> attributes
    ) {
        if (attributes == null) {
            return List.of();
        }
        return attributes.stream()
            .filter(attribute -> attribute != null)
            .map(attribute -> new ConversationTicketCreatedEvent.TicketAttributePayload(
                attribute.questionId(),
                attribute.attributeKey(),
                attribute.questionText(),
                attribute.inputType(),
                attribute.valueId(),
                attribute.valueLabel(),
                attribute.valueText(),
                attribute.includeInDashboard()
            ))
            .toList();
    }
}
