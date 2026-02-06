package com.example.panel.controller;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.service.DashboardAnalyticsService;
import com.example.panel.service.DialogService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Validated
public class DashboardApiController {

    private static final Logger log = LoggerFactory.getLogger(DashboardApiController.class);

    private final DialogService dialogService;
    private final DashboardAnalyticsService analyticsService;

    public DashboardApiController(DialogService dialogService, DashboardAnalyticsService analyticsService) {
        this.dialogService = dialogService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/data")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public Map<String, Object> data(@RequestBody(required = false) DashboardFilterRequest request,
                                    Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        List<DialogListItem> dialogs = dialogService.loadDialogs(operator);
        DashboardAnalyticsService.DashboardFilters filters = normalize(request);
        Map<String, Object> payload = analyticsService.buildDashboardPayload(dialogs, filters);
        payload.put("success", true);
        log.info("Dashboard API payload built for operator {} with {} dialogs", operator, dialogs.size());
        return payload;
    }

    private DashboardAnalyticsService.DashboardFilters normalize(DashboardFilterRequest request) {
        if (request == null) {
            return new DashboardAnalyticsService.DashboardFilters(null, null, List.of());
        }
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        List<String> restaurants = request.restaurants() != null ? request.restaurants() : List.of();
        return new DashboardAnalyticsService.DashboardFilters(startDate, endDate, restaurants);
    }

    public record DashboardFilterRequest(LocalDate startDate, LocalDate endDate, List<String> restaurants) {}
}
