package com.example.panel.service.integration;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.integration.transport.mode", havingValue = "rabbitmq")
public class InboundClientMessageListener {

    private final InboundClientMessageIngestionService ingestionService;

    public InboundClientMessageListener(InboundClientMessageIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @RabbitListener(
        queues = "${app.integration.rabbitmq.inbound-queue}",
        containerFactory = "inboundClientMessageListenerContainerFactory"
    )
    public void onInboundClientMessage(InboundClientMessageEvent event,
                                       @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        ingestionService.ingest(event, routingKey);
    }
}
