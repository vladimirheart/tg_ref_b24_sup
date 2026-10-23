package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.DashboardAnalyticsService;
import com.example.panel.service.DialogLookupReadService;
import com.example.panel.service.IncidentOpsMetricsService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HomePulseTest {
    final DialogLookupReadService dialogs = mock(DialogLookupReadService.class);
    final DashboardAnalyticsService analytics = mock(DashboardAnalyticsService.class);
    final IncidentOpsMetricsService incidents = mock(IncidentOpsMetricsService.class);
    final ChannelRepository channels = mock(ChannelRepository.class);
    final SharedConfigService sharedConfig = mock(SharedConfigService.class);
    final HomeController controller = new HomeController(mock(NavigationService.class), new PermissionService(),
            dialogs, analytics, incidents, channels, sharedConfig);

    @Test void unauthorizedSectionsNeverQueryTheirDataSources() {
        var result = controller.pulse(new TestingAuthenticationToken("reader", "", "PAGE_CLIENTS"));
        for (String section : List.of("dialogs", "incidents", "runtime")) {
            assertThat(((Map<?, ?>) result.get(section)).get("available")).isEqualTo(false);
        }
        verifyNoInteractions(dialogs, analytics, incidents, channels, sharedConfig);
    }

    @Test void taskReaderGetsOnlyIncidentStatistics() {
        when(incidents.buildSummary()).thenReturn(Map.of("active_count", 2));
        var result = controller.pulse(new TestingAuthenticationToken("reader", "", "PAGE_TASKS"));
        assertThat(((Map<?, ?>) result.get("incidents")).get("active_count")).isEqualTo(2);
        verifyNoInteractions(dialogs, analytics, channels, sharedConfig);
    }

    @Test void todayUsesExistingAnalyticsFiltersAndARealZeroHourlySeries() {
        when(dialogs.loadDialogs("operator")).thenReturn(List.of());
        when(analytics.buildDashboardPayload(eq(List.of()), any())).thenReturn(Map.of(
                "stats", Map.of("current", Map.of("total", 0)), "charts", Map.of("channel", Map.of()),
                "activity_stats", Map.of("matrix", List.of(Map.of("values", Collections.nCopies(24, 0))))));
        when(incidents.buildSummary()).thenReturn(Map.of());
        var result = controller.pulse(new TestingAuthenticationToken("operator", "", "PAGE_DIALOGS"));
        var stats = (Map<?, ?>) result.get("dialogs");
        assertThat(stats.get("available")).isEqualTo(true);
        assertThat(stats.get("createdToday")).isEqualTo(0);
        assertThat((List<?>) stats.get("hourly")).hasSize(24).allMatch(value -> Long.valueOf(0).equals(value));
        LocalDate today = LocalDate.now();
        verify(analytics).buildDashboardPayload(List.of(), new DashboardAnalyticsService.DashboardFilters(today, today, List.of()));
    }

    @Test void runtimeUsesOnlySavedSnapshotsAndKeepsUnknownDistinctFromStopped() {
        when(channels.findAll()).thenReturn(List.of(channel(1L, true), channel(2L, true), channel(3L, false)));
        when(sharedConfig.loadRuntimeStatus("runtime/bot-1.json")).thenReturn(Map.of("status", "running"));
        when(sharedConfig.loadRuntimeStatus("runtime/bot-2.json")).thenReturn(Map.of());
        var result = controller.pulse(new TestingAuthenticationToken("reader", "", "PAGE_CHANNELS"));
        var runtime = (Map<?, ?>) result.get("runtime");
        assertThat(runtime.get("totalChannels")).isEqualTo(3);
        assertThat(runtime.get("activeChannels")).isEqualTo(2);
        assertThat(runtime.get("runningBots")).isEqualTo(1);
        assertThat(runtime.get("unknownBots")).isEqualTo(1);
        verifyNoInteractions(dialogs, analytics, incidents);
    }

    @Test void failureIsUnavailableNotAFabricatedZeroAndOtherSectionsStillWork() {
        when(dialogs.loadDialogs("operator")).thenThrow(new IllegalStateException("private detail"));
        when(incidents.buildSummary()).thenReturn(Map.of("active_count", 3));
        var result = controller.pulse(new TestingAuthenticationToken("operator", "", "PAGE_DIALOGS"));
        var stats = (Map<?, ?>) result.get("dialogs");
        assertThat(stats.get("available")).isEqualTo(false);
        assertThat(stats.containsKey("createdToday")).isFalse();
        assertThat(stats.toString()).doesNotContain("private detail");
        assertThat(((Map<?, ?>) result.get("incidents")).get("active_count")).isEqualTo(3);
    }

    private Channel channel(long id, boolean active) {
        Channel channel = new Channel(); channel.setId(id); channel.setActive(active); return channel;
    }
}
