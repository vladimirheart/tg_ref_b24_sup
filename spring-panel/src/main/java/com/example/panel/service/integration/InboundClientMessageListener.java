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
    id = "inbound-client-message-listener",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.BROKER_COMPETING_CONSUMER
)@Component
@ConditionalOnProperty(name = "app.integration.transport.mode", havingValue = "rabbitmq")
public class InboundClientMessageListener {

    private final InboundClientMessageIngestionService ingestionService;
    private final com.example.panel.security.ChildBotInboundPrincipalService principals;

    public InboundClientMessageListener(InboundClientMessageIngestionService ingestionService,
            com.example.panel.security.ChildBotInboundPrincipalService principals) {
        this.ingestionService = ingestionService;
        this.principals = principals;
    }

    @RabbitListener(
        queues = "${app.integration.rabbitmq.inbound-queue}",
        containerFactory = "inboundClientMessageListenerContainerFactory"
    )
    @org.springframework.transaction.annotation.Transactional
    public void onInboundClientMessage(InboundClientMessageEvent event,
                                       @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                       @Header(name=AmqpHeaders.RECEIVED_USER_ID, required=false) String publisher,
                                       org.springframework.amqp.core.Message wire) {
        principals.authorize(publisher, event != null ? event.channelId() : null, event != null ? event.platform() : null,
            event != null ? event.userId() : null, event != null ? event.ticketId() : null, false,
            wire!=null?wire.getBody():null,wire!=null && wire.getMessageProperties().getHeaders().get("X-Iguana-Child-Event-Signature") instanceof String signature?signature:null);
        ingestionService.ingest(event, routingKey);
    }
}
