package com.example.panel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitIntegrationTransportConfig {

    @Bean
    public Jackson2JsonMessageConverter integrationRabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public Declarables integrationInboundRabbitTopology(IntegrationRabbitProperties properties) {
        TopicExchange inboundExchange = new TopicExchange(properties.getInboundExchange(), true, false);
        TopicExchange deadLetterExchange = new TopicExchange(properties.getInboundDlx(), true, false);
        Queue inboundQueue = new Queue(
            properties.getInboundQueue(),
            true,
            false,
            false,
            Map.of(
                "x-dead-letter-exchange", properties.getInboundDlx(),
                "x-dead-letter-routing-key", properties.getInboundDlq()
            )
        );
        Queue deadLetterQueue = new Queue(properties.getInboundDlq(), true);
        Binding telegramBinding = BindingBuilder.bind(inboundQueue).to(inboundExchange).with(properties.getRoutingTelegram());
        Binding vkBinding = BindingBuilder.bind(inboundQueue).to(inboundExchange).with(properties.getRoutingVk());
        Binding maxBinding = BindingBuilder.bind(inboundQueue).to(inboundExchange).with(properties.getRoutingMax());
        Binding dlqBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(properties.getInboundDlq());
        return new Declarables(inboundExchange, deadLetterExchange, inboundQueue, deadLetterQueue,
            telegramBinding, vkBinding, maxBinding, dlqBinding);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter integrationRabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(integrationRabbitMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
