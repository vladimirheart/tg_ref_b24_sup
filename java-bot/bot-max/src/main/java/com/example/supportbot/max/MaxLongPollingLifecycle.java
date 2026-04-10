package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class MaxLongPollingLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MaxLongPollingLifecycle.class);

    private final MaxBotProperties properties;
    private final MaxApiClient apiClient;
    private final MaxWebhookController updateProcessor;

    private volatile boolean running = false;
    private volatile Thread worker;
    private volatile String marker = "";

    public MaxLongPollingLifecycle(MaxBotProperties properties,
                                   MaxApiClient apiClient,
                                   MaxWebhookController updateProcessor) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.updateProcessor = updateProcessor;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (!properties.isEnabled()) {
            log.info("MAX long polling is disabled by config");
            return;
        }
        running = true;
        Thread thread = new Thread(this::pollLoop, "max-long-polling");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
        log.info("MAX long polling started");
    }

    private void pollLoop() {
        while (running) {
            try {
                MaxApiClient.PollBatch batch = apiClient.fetchUpdates(marker, 100, 25);
                if (batch != null && batch.marker() != null && !batch.marker().isBlank()) {
                    marker = batch.marker();
                }
                List<JsonNode> updates = batch != null ? batch.updates() : List.of();
                if (!updates.isEmpty()) {
                    String expectedSecret = properties.getWebhookSecret();
                    for (JsonNode update : updates) {
                        try {
                            updateProcessor.handleUpdate(update, expectedSecret);
                        } catch (Exception ex) {
                            log.warn("Failed to process MAX update: {}", ex.getMessage());
                        }
                    }
                    continue;
                }
                sleepSilently(300);
            } catch (Exception ex) {
                log.warn("MAX long polling iteration failed: {}", ex.getMessage());
                sleepSilently(1500);
            }
        }
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
        Thread thread = worker;
        worker = null;
        if (thread != null) {
            thread.interrupt();
        }
        log.info("MAX long polling stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }
}
