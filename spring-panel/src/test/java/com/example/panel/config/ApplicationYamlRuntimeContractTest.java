package com.example.panel.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationYamlRuntimeContractTest {

    @Test
    void applicationYamlParsesAndContainsRuntimeSplitContracts() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
            "application-runtime-contract",
            new ClassPathResource("application.yml")
        );

        assertThat(sources).isNotEmpty();

        PropertySource<?> source = sources.get(0);
        assertThat(source.getProperty("app.runtime.role")).isNotNull();
        assertThat(source.getProperty("app.runtime.instance-id")).isNotNull();
        assertThat(source.getProperty("app.runtime.exit-after-migration")).isNotNull();
        assertThat(source.getProperty("app.ui-events.fanout.mode")).isNotNull();
        assertThat(source.getProperty("app.ui-events.fanout.channel")).isNotNull();
        assertThat(source.getProperty("management.metrics.tags.runtime_role")).isNotNull();
        assertThat(source.getProperty("management.metrics.distribution.percentiles-histogram.http.server.requests"))
            .isEqualTo(true);
    }
}
