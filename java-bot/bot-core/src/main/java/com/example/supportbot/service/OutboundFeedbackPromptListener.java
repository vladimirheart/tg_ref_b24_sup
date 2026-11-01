package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationRabbitProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.integration.transport.mode", havingValue = "rabbitmq")
public class OutboundFeedbackPromptListener {

    private final OutboundFeedbackPromptDispatchService dispatchService;
    private final String childRoutingKey;
    private final String childQueue;

    public OutboundFeedbackPromptListener(OutboundFeedbackPromptDispatchService dispatchService,
                                         IntegrationRabbitProperties properties) {
        this.dispatchService = dispatchService;
        // Pin launcher inputs for this process; a mutable properties bean must not rebind a running child.
        this.childRoutingKey = properties.getOutboundRoutingKey();
        this.childQueue = properties.getOutboundQueue();
    }

    @RabbitListener(
        queues = "${app.integration.rabbitmq.outbound-queue}",
        containerFactory = "outboundFeedbackPromptListenerContainerFactory"
    )
    public void onFeedbackPrompt(OutboundFeedbackPromptEvent event,
                                 @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        requireChildRoute(event, routingKey);
        dispatchService.dispatch(event, routingKey);
    }

    private void requireChildRoute(OutboundFeedbackPromptEvent event, String receivedRoutingKey) {
        if (event == null || event.channelId() == null || event.channelId() <= 0
                || !("telegram".equals(event.platform()) || "vk".equals(event.platform()) || "max".equals(event.platform()))) {
            throw invalidChildRoute();
        }
        String destination = event.platform() + ".channel." + event.channelId();
        String expectedRoutingKey = "integration.outbound.feedback.prompt." + destination;
        String expectedQueue = "iguana.integration.outbound.feedback-prompt." + destination + ".bot";
        // Exact canonical equality, not a topic match or a channel inferred from the received message.
        if (!expectedRoutingKey.equals(childRoutingKey) || !expectedRoutingKey.equals(receivedRoutingKey)
                || !expectedQueue.equals(childQueue)) {
            throw invalidChildRoute();
        }
    }

    private IllegalArgumentException invalidChildRoute() {
        return new IllegalArgumentException("Outbound feedback prompt does not match this child runtime route.");
    }
}
