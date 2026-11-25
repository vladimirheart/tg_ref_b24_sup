package com.example.panel.service;

import com.example.panel.config.SchedulingConfiguration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.support.TestPropertySourceUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UiEventHeartbeatSchedulingTest {
    @Test
    void generalHeartbeatSurvivesBusyDefaultSchedulerAndBlockedUiHeartbeat() throws Exception {
        var ui = mock(UiEventStreamService.class);
        var general = mock(GeneralTicketRealtimeService.class);
        var defaultsOccupied = new CountDownLatch(4);
        var uiOccupied = new CountDownLatch(1);
        var generalDelivered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blockUi = new AtomicBoolean();
        doAnswer(invocation -> {
            if (blockUi.get()) {
                uiOccupied.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return null;
        }).when(ui).sendHeartbeat();
        doAnswer(invocation -> {
            if (defaultsOccupied.getCount() == 0 && uiOccupied.getCount() == 0) {
                generalDelivered.countDown();
            }
            return null;
        }).when(general).sendHeartbeat();
        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "panel.ui-events.heartbeat-ms=25");
            context.register(SchedulingConfiguration.class, SchedulingEnabled.class);
            context.registerBean(UiEventStreamHeartbeatScheduler.class,
                () -> new UiEventStreamHeartbeatScheduler(ui, general));
            context.refresh();
            var shared = context.getBean("taskScheduler", TaskScheduler.class);
            for (int i = 0; i < 4; i++) shared.schedule(() -> {
                defaultsOccupied.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }, Instant.now());
            try {
                assertThat(defaultsOccupied.await(2, TimeUnit.SECONDS)).isTrue();
                blockUi.set(true);
                assertThat(uiOccupied.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(generalDelivered.await(2, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
        }
    }

    @Configuration
    @EnableScheduling
    static class SchedulingEnabled { }
}
