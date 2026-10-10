package com.example.panel.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MultibusinessFeatureFlagsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FeatureFlagsConfiguration.class);

    @Test
    void defaultsEveryMultibusinessFlagToDisabled() {
        contextRunner.run(context -> {
            MultibusinessFeatureFlags flags = context.getBean(MultibusinessFeatureFlags.class);

            assertThat(flags.isFoundationEnabled()).isFalse();
            assertThat(flags.isTicketWriteRequiresBusiness()).isFalse();
            assertThat(flags.isStrictReadScope()).isFalse();
            assertThat(flags.isSharedChannelResolution()).isFalse();
            assertThat(flags.isUiSelector()).isFalse();
        });
    }

    @Test
    void bindsEachFlagOnlyWhenExplicitlyConfigured() {
        contextRunner
                .withPropertyValues(
                        "multibusiness.foundation.enabled=true",
                        "multibusiness.ticket-write-requires-business=true",
                        "multibusiness.strict-read-scope=true",
                        "multibusiness.shared-channel-resolution=true",
                        "multibusiness.ui-selector=true"
                )
                .run(context -> {
                    MultibusinessFeatureFlags flags = context.getBean(MultibusinessFeatureFlags.class);

                    assertThat(flags.isFoundationEnabled()).isTrue();
                    assertThat(flags.isTicketWriteRequiresBusiness()).isTrue();
                    assertThat(flags.isStrictReadScope()).isTrue();
                    assertThat(flags.isSharedChannelResolution()).isTrue();
                    assertThat(flags.isUiSelector()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MultibusinessFeatureFlags.class)
    static class FeatureFlagsConfiguration {
    }
}
