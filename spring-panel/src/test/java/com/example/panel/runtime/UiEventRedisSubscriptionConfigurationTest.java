package com.example.panel.runtime;

import com.example.panel.service.UiEventRedisSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UiEventRedisSubscriptionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(FixtureConfiguration.class, UiEventRedisSubscriptionConfiguration.class);

    @Test
    void autoModeDoesNotCreateRedisListenerContainer() {
        contextRunner
            .withPropertyValues("app.ui-events.fanout.mode=auto")
            .run(context -> assertThat(context).doesNotHaveBean("uiEventRedisMessageListenerContainer"));
    }

    @Configuration(proxyBeanMethods = false)
    static class FixtureConfiguration {

        @Bean
        UiEventFanoutProperties uiEventFanoutProperties() {
            return new UiEventFanoutProperties();
        }

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        UiEventRedisSubscriber uiEventRedisSubscriber() {
            return mock(UiEventRedisSubscriber.class);
        }
    }
}
