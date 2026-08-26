package com.example.panel.runtime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    RuntimeRoleProperties.class,
    UiEventFanoutProperties.class
})
public class RuntimeRoleConfiguration {
}
