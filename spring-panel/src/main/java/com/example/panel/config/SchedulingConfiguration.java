package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfiguration.class);

    @Bean
    public TaskScheduler taskScheduler(@Value("${panel.scheduling.pool-size:4}") int configuredPoolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(2, configuredPoolSize));
        scheduler.setThreadNamePrefix("panel-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setErrorHandler(throwable ->
                log.error("Scheduled task execution failed: {}", throwable.getMessage(), throwable));
        return scheduler;
    }
}
