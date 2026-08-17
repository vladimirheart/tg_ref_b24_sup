package com.example.supportbot.config;

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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter integrationRabbitMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(integrationRabbitMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public Declarables integrationInboundExchange(IntegrationRabbitProperties properties) {
        TopicExchange inboundExchange = new TopicExchange(properties.getInboundExchange(), true, false);
        TopicExchange inboundDlx = new TopicExchange(properties.getInboundDlx(), true, false);
        TopicExchange outboundExchange = new TopicExchange(properties.getOutboundExchange(), true, false);
        TopicExchange outboundDlx = new TopicExchange(properties.getOutboundDlx(), true, false);
        Queue outboundQueue = new Queue(
            properties.getOutboundQueue(),
            true,
            false,
            false,
            Map.of(
                "x-dead-letter-exchange", properties.getOutboundDlx(),
                "x-dead-letter-routing-key", properties.getOutboundDlq()
            )
        );
        Queue outboundDlq = new Queue(properties.getOutboundDlq(), true);
        Binding outboundBinding = BindingBuilder.bind(outboundQueue)
            .to(outboundExchange)
            .with(properties.getOutboundRoutingKey());
        Binding outboundDlqBinding = BindingBuilder.bind(outboundDlq)
            .to(outboundDlx)
            .with(properties.getOutboundDlq());
        return new Declarables(
            inboundExchange,
            inboundDlx,
            outboundExchange,
            outboundDlx,
            outboundQueue,
            outboundDlq,
            outboundBinding,
            outboundDlqBinding
        );
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
