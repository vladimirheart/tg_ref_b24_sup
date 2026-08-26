package com.example.panel.runtime;

import com.example.panel.service.UiEventRedisSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@RuntimeWorkload(
    id = "ui-event-redis-subscription",
    roles = {RuntimeRole.WEB},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)
@ConditionalOnProperty(prefix = "app.ui-events.fanout", name = "mode", havingValue = "redis")
public class UiEventRedisSubscriptionConfiguration {

    @Bean
    RedisMessageListenerContainer uiEventRedisMessageListenerContainer(
        RedisConnectionFactory connectionFactory,
        UiEventRedisSubscriber subscriber,
        UiEventFanoutProperties properties
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(properties.resolvedChannel()));
        return container;
    }
}
