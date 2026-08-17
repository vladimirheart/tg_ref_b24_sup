package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LegacyBusinessFallbackIsolationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("app.integration.transport.mode=rabbitmq")
            .withUserConfiguration(TestConfig.class);

    @Test
    void rabbitMqModeDoesNotExposeLegacyTaskFallbackBeans() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(TaskService.class);
            assertThat(context).doesNotHaveBean(AutoCloseFollowUpTaskService.class);
            assertThat(context).hasSingleBean(AutoCloseFollowUpTaskSupport.class);
            assertThat(context).hasSingleBean(NoOpAutoCloseFollowUpTaskSupport.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            TaskService.class,
            AutoCloseFollowUpTaskService.class,
            NoOpAutoCloseFollowUpTaskSupport.class
    })
    static class TestConfig {
    }
}
