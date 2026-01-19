package com.example.panel.controller;

import com.example.panel.model.dialog.DialogChannelStat;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class DashboardApiController {

    private final DialogService dialogService;

    public DashboardApiController(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @PostMapping("/api/dashboard/data")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public DashboardResponse loadDashboard(@RequestBody(required = false) DashboardFilter filter) {
        DialogSummary summary = dialogService.loadSummary();
        DashboardStatsPeriod current = new DashboardStatsPeriod(
                summary.totalTickets(),
                summary.resolvedTickets(),
                summary.pendingTickets()
        );
        DashboardStatsPeriod previous = new DashboardStatsPeriod(0, 0, 0);
        DashboardStats stats = new DashboardStats(current, previous);
        DashboardTimeStats timeStats = new DashboardTimeStats("0 ч", "0 мин", summary.resolvedTickets());
        DashboardCharts charts = new DashboardCharts(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
        return new DashboardResponse(stats, timeStats, List.of(), charts);
    }

    @GetMapping("/stats_data")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public Map<String, Object> loadChannelStats() {
        DialogSummary summary = dialogService.loadSummary();
        List<Map<String, Object>> byChannel = summary.channelStats().stream()
                .map(this::toChannelMap)
                .toList();
        return Map.of("by_channel", byChannel);
    }

    private Map<String, Object> toChannelMap(DialogChannelStat stat) {
        return Map.of(
                "name", stat.name(),
                "total", stat.total()
        );
    }

    public record DashboardFilter(String startDate, String endDate, List<String> restaurants) {
    }

    public record DashboardResponse(DashboardStats stats,
                                    @JsonProperty("time_stats") DashboardTimeStats timeStats,
                                    @JsonProperty("staff_time_stats") List<StaffTimeStat> staffTimeStats,
                                    DashboardCharts charts) {
    }

    public record DashboardStats(DashboardStatsPeriod current, DashboardStatsPeriod previous) {
    }

    public record DashboardStatsPeriod(long total, long resolved, long pending) {
    }

    public record DashboardTimeStats(@JsonProperty("formatted_total") String formattedTotal,
                                     @JsonProperty("formatted_avg") String formattedAvg,
                                     @JsonProperty("resolved_count") long resolvedCount) {
    }

    public record StaffTimeStat(String name,
                                @JsonProperty("formatted_total") String formattedTotal,
                                @JsonProperty("resolved_count") long resolvedCount,
                                @JsonProperty("formatted_avg") String formattedAvg,
                                @JsonProperty("formatted_avg_response") String formattedAvgResponse,
                                @JsonProperty("total_minutes") long totalMinutes) {
    }

    public record DashboardCharts(Map<String, Long> business,
                                  Map<String, Long> network,
                                  Map<String, Long> category,
                                  Map<String, Long> city,
                                  Map<String, Long> restaurant) {
    }
}
