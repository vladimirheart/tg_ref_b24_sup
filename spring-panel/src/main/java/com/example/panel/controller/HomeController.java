package com.example.panel.controller;

import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.DashboardAnalyticsService;
import com.example.panel.service.DialogLookupReadService;
import com.example.panel.service.IncidentOpsMetricsService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Read-only landing page; no channel discovery, lifecycle commands or new data authority. */
@Controller
@PreAuthorize("isAuthenticated()")
public class HomeController {
    private final NavigationService navigation;
    private final PermissionService permissions;
    private final DialogLookupReadService dialogs;
    private final DashboardAnalyticsService analytics;
    private final IncidentOpsMetricsService incidents;
    private final ChannelRepository channels;
    private final SharedConfigService sharedConfig;

    public HomeController(NavigationService navigation, PermissionService permissions,
                          DialogLookupReadService dialogs, DashboardAnalyticsService analytics,
                          IncidentOpsMetricsService incidents, ChannelRepository channels,
                          SharedConfigService sharedConfig) {
        this.navigation = navigation;
        this.permissions = permissions;
        this.dialogs = dialogs;
        this.analytics = analytics;
        this.incidents = incidents;
        this.channels = channels;
        this.sharedConfig = sharedConfig;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        navigation.enrich(model, authentication);
        model.addAttribute("canViewDialogs", permissions.hasAuthority(authentication, "PAGE_DIALOGS"));
        model.addAttribute("activePage", "home");
        return "home/index";
    }

    @GetMapping("/api/home/pulse")
    @ResponseBody
    public Map<String, Object> pulse(Authentication authentication) {
        LocalDate today = LocalDate.now();
        boolean canViewDialogs = permissions.hasAuthority(authentication, "PAGE_DIALOGS");
        boolean canViewIncidents = canViewDialogs
                || permissions.hasAuthority(authentication, "PAGE_TASKS")
                || permissions.hasAuthority(authentication, "PAGE_OBJECT_PASSPORTS");
        boolean canViewRuntime = permissions.hasAuthority(authentication, "PAGE_CHANNELS")
                || permissions.hasAuthority(authentication, "PAGE_ANALYTICS");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", today.toString());
        payload.put("zone", ZoneId.systemDefault().getId());
        payload.put("generatedAt", Instant.now().toString());
        payload.put("dialogs", section(canViewDialogs, () -> dialogPulse(authentication.getName(), today)));
        payload.put("incidents", section(canViewIncidents, incidents::buildSummary));
        payload.put("runtime", section(canViewRuntime, this::runtimePulse));
        payload.put("success", true);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dialogPulse(String operator, LocalDate today) {
        var rows = dialogs.loadDialogs(operator);
        var data = analytics.buildDashboardPayload(rows,
                new DashboardAnalyticsService.DashboardFilters(today, today, List.of()));
        var stats = (Map<String, Object>) data.get("stats");
        var current = (Map<String, Object>) stats.get("current");
        var charts = (Map<String, Object>) data.get("charts");
        var activity = (Map<String, Object>) data.get("activity_stats");
        var matrix = (List<Map<String, Object>>) activity.get("matrix");
        List<Long> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            long count = 0;
            for (var day : matrix) {
                var values = (List<? extends Number>) day.get("values");
                count += values.get(hour).longValue();
            }
            hourly.add(count);
        }
        return Map.of("createdToday", current.get("total"),
                "active", rows.stream().filter(row -> !List.of("closed", "auto_closed").contains(row.statusKey())).count(),
                "hourly", hourly, "channels", charts.get("channel"));
    }

    private Map<String, Object> runtimePulse() {
        var configured = channels.findAll();
        int active = 0, running = 0, unknown = 0;
        for (var channel : configured) {
            if (!Boolean.TRUE.equals(channel.getActive())) continue;
            active++;
            try {
                var snapshot = sharedConfig.loadRuntimeStatus("runtime/bot-" + channel.getId() + ".json");
                String status = String.valueOf(snapshot.getOrDefault("status", ""));
                if ("running".equalsIgnoreCase(status)) running++;
                else if (!"stopped".equalsIgnoreCase(status)) unknown++;
            } catch (RuntimeException ignored) {
                unknown++;
            }
        }
        return Map.of("totalChannels", configured.size(), "activeChannels", active,
                "runningBots", running, "unknownBots", unknown);
    }

    private Map<String, Object> section(boolean permitted, Supplier<Map<String, Object>> load) {
        if (!permitted) return Map.of("available", false, "reason", "Нет доступа к разделу");
        try {
            Map<String, Object> result = new LinkedHashMap<>(load.get());
            result.put("available", true);
            return result;
        } catch (RuntimeException ignored) {
            return Map.of("available", false, "reason", "Данные временно недоступны");
        }
    }
}
