package com.example.panel.controller;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.service.DashboardAnalyticsService;
import com.example.panel.service.DialogService;
import com.example.panel.service.ManagerReportService;
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
    private final ManagerReportService managerReportService;

    public DashboardApiController(DialogService dialogService,
                                  DashboardAnalyticsService analyticsService,
                                  ManagerReportService managerReportService) {
        this.dialogService = dialogService;
        this.analyticsService = analyticsService;
        this.managerReportService = managerReportService;
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


    @PostMapping("/manager-report")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public Map<String, Object> managerReport(@RequestBody(required = false) DashboardFilterRequest request,
                                             Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        List<DialogListItem> dialogs = dialogService.loadDialogs(operator);
        DashboardAnalyticsService.DashboardFilters filters = normalize(request, true);
        Map<String, Object> payload = managerReportService.buildManagerReport(dialogs, filters);
        payload.put("success", true);
        return payload;
    }

    @PostMapping("/olap-preview")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public Map<String, Object> olapPreview(@RequestBody(required = false) OlapFilterRequest request, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        List<DialogListItem> dialogs = dialogService.loadDialogs(operator);
        DashboardAnalyticsService.DashboardFilters filters = normalize(request != null ? request.filters() : null);
        ManagerReportService.OlapPreviewRequest olapRequest = (request != null && request.olap() != null)
            ? request.olap()
            : new ManagerReportService.OlapPreviewRequest("location", true, true, true);
        Map<String, Object> payload = managerReportService.buildOlapPreview(dialogs, filters, olapRequest);
        payload.put("success", true);
        return payload;
    }

    private DashboardAnalyticsService.DashboardFilters normalize(DashboardFilterRequest request) {
        return normalize(request, false);
    }

    private DashboardAnalyticsService.DashboardFilters normalize(DashboardFilterRequest request, boolean defaultCurrentMonth) {
        if (request == null) {
            return defaultCurrentMonth
                ? currentMonthFilters(List.of())
                : new DashboardAnalyticsService.DashboardFilters(null, null, List.of());
        }
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        List<String> restaurants = request.restaurants() != null ? request.restaurants() : List.of();
        if (defaultCurrentMonth && startDate == null && endDate == null) {
            return currentMonthFilters(restaurants);
        }
        return new DashboardAnalyticsService.DashboardFilters(startDate, endDate, restaurants);
    }

    private DashboardAnalyticsService.DashboardFilters currentMonthFilters(List<String> restaurants) {
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
        return new DashboardAnalyticsService.DashboardFilters(start, end, restaurants);
    }

    public record DashboardFilterRequest(LocalDate startDate, LocalDate endDate, List<String> restaurants) {}

    public record OlapFilterRequest(DashboardFilterRequest filters, ManagerReportService.OlapPreviewRequest olap) {}
}
