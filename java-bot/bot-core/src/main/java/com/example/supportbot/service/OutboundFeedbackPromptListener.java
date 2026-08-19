package com.example.supportbot.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.integration.transport.mode", havingValue = "rabbitmq")
public class OutboundFeedbackPromptListener {

    private final OutboundFeedbackPromptDispatchService dispatchService;

    public OutboundFeedbackPromptListener(OutboundFeedbackPromptDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @RabbitListener(
        queues = "${app.integration.rabbitmq.outbound-queue}",
        containerFactory = "outboundFeedbackPromptListenerContainerFactory"
    )
    public void onFeedbackPrompt(OutboundFeedbackPromptEvent event,
                                 @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        dispatchService.dispatch(event, routingKey);
    }
}
