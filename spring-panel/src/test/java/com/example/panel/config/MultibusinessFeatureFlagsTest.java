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
            assertThat(flags.isGeneralChannelResolution()).isFalse();
            assertThat(flags.isGeneralTicketQueue()).isFalse();
            assertThat(flags.isUiSelector()).isFalse();
            assertThat(flags.isManualTicketCreation()).isFalse();
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
                        "multibusiness.general-channel-resolution=true",
                        "multibusiness.general-ticket-queue=true",
                        "multibusiness.ui-selector=true",
                        "multibusiness.manual-ticket-creation=true"
                )
                .run(context -> {
                    MultibusinessFeatureFlags flags = context.getBean(MultibusinessFeatureFlags.class);

                    assertThat(flags.isFoundationEnabled()).isTrue();
                    assertThat(flags.isTicketWriteRequiresBusiness()).isTrue();
                    assertThat(flags.isStrictReadScope()).isTrue();
                    assertThat(flags.isSharedChannelResolution()).isTrue();
                    assertThat(flags.isGeneralChannelResolution()).isTrue();
                    assertThat(flags.isGeneralTicketQueue()).isTrue();
                    assertThat(flags.isUiSelector()).isTrue();
                    assertThat(flags.isManualTicketCreation()).isTrue();
                });
    }

    private ApplicationContextRunner fromApplicationYaml(java.util.Map<String,Object> inputs) {
        return contextRunner.withInitializer(context -> {
            context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.SystemEnvironmentPropertySource("persistent-rollout-inputs",inputs));
            try {
                for(var source:new org.springframework.boot.env.YamlPropertySourceLoader().load("actual-application",
                        new org.springframework.core.io.ClassPathResource("application.yml")))
                    context.getEnvironment().getPropertySources().addLast(source);
            } catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
        });
    }

    @Test void actualApplicationDefaultsStayDisabledWithoutAnExplicitRollout() {
        fromApplicationYaml(java.util.Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            var flags=context.getBean(MultibusinessFeatureFlags.class);
            assertThat(flags.isFoundationEnabled()).isFalse();
            assertThat(flags.isSharedChannelResolution()).isFalse();
            assertThat(flags.isTicketWriteRequiresBusiness()).isFalse();
        });
    }

    @Test void persistentAppEnvironmentBindsCanonicalNestedFoundationAndAllFlags() {
        var inputs=new java.util.LinkedHashMap<String,Object>();
        for(String key:java.util.List.of("FOUNDATION_ENABLED","TICKET_WRITE_REQUIRES_BUSINESS","STRICT_READ_SCOPE",
                "SHARED_CHANNEL_RESOLUTION","GENERAL_CHANNEL_RESOLUTION","GENERAL_TICKET_QUEUE","UI_SELECTOR","MANUAL_TICKET_CREATION"))
            inputs.put("APP_MULTIBUSINESS_"+key,"true");
        fromApplicationYaml(inputs).run(context -> {
            assertThat(context).hasNotFailed();
            var flags=context.getBean(MultibusinessFeatureFlags.class);
            assertThat(flags.getFoundation().isEnabled()).isTrue();
            assertThat(flags.isTicketWriteRequiresBusiness()).isTrue();
            assertThat(flags.isStrictReadScope()).isTrue();
            assertThat(flags.isSharedChannelResolution()).isTrue();
            assertThat(flags.isGeneralChannelResolution()).isTrue();
            assertThat(flags.isGeneralTicketQueue()).isTrue();
            assertThat(flags.isUiSelector()).isTrue();
            assertThat(flags.isManualTicketCreation()).isTrue();
        });
    }

    @Test void canonicalRuntimeOverrideRetainsPrecedenceOverPersistentDefaults() {
        fromApplicationYaml(java.util.Map.of("APP_MULTIBUSINESS_FOUNDATION_ENABLED","false"))
            .withPropertyValues("multibusiness.foundation.enabled=true","multibusiness.shared-channel-resolution=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(MultibusinessFeatureFlags.class).isFoundationEnabled()).isTrue();
                assertThat(context.getBean(MultibusinessFeatureFlags.class).isSharedChannelResolution()).isTrue();
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MultibusinessFeatureFlags.class)
    static class FeatureFlagsConfiguration {
    }
}
