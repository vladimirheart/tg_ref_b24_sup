package com.example.panel.service.integration;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@RuntimeWorkload(
    id = "conversation-ticket-created-listener",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.BROKER_COMPETING_CONSUMER
)@Component
@ConditionalOnProperty(name = "app.integration.transport.mode", havingValue = "rabbitmq")
public class ConversationTicketCreatedListener {

    private final ConversationTicketCreationIngestionService ingestionService;

    public ConversationTicketCreatedListener(ConversationTicketCreationIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @RabbitListener(
        queues = "${app.integration.rabbitmq.ticket-created-queue}",
        containerFactory = "conversationTicketCreatedListenerContainerFactory"
    )
    public void onConversationTicketCreated(ConversationTicketCreatedEvent event,
                                            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        ingestionService.ingest(event, routingKey);
    }
}
