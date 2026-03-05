package com.example.panel.controller;

import com.example.panel.model.AnalyticsClientSummary;
import com.example.panel.model.AnalyticsTicketSummary;
import com.example.panel.service.AnalyticsService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final NavigationService navigationService;
    private final SharedConfigService sharedConfigService;

    public AnalyticsController(AnalyticsService analyticsService,
                               NavigationService navigationService,
                               SharedConfigService sharedConfigService) {
        this.analyticsService = analyticsService;
        this.navigationService = navigationService;
        this.sharedConfigService = sharedConfigService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String view(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        try {
            List<AnalyticsTicketSummary> ticketSummary = analyticsService.loadTicketSummary();
            List<AnalyticsClientSummary> clientSummary = analyticsService.loadClientSummary();
            model.addAttribute("ticketSummary", ticketSummary);
            model.addAttribute("clientSummary", clientSummary);
            Map<String, Object> settings = sharedConfigService.loadSettings();
            Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            model.addAttribute("crossProductOmnichannelDashboardUrl", String.valueOf(dialogConfig.getOrDefault("cross_product_omnichannel_dashboard_url", "")).trim());
            model.addAttribute("crossProductOmnichannelDashboardLabel", String.valueOf(dialogConfig.getOrDefault("cross_product_omnichannel_dashboard_label", "Omni-channel KPI dashboard")).trim());
            model.addAttribute("crossProductFinanceDashboardUrl", String.valueOf(dialogConfig.getOrDefault("cross_product_finance_dashboard_url", "")).trim());
            model.addAttribute("crossProductFinanceDashboardLabel", String.valueOf(dialogConfig.getOrDefault("cross_product_finance_dashboard_label", "Финансовый KPI dashboard")).trim());

            log.info("Analytics view requested by {}: {} ticket rows, {} client rows",
                    authentication.getName(), ticketSummary.size(), clientSummary.size());
        } catch (Exception ex) {
            log.error("Failed to load analytics page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "analytics/index";
    }

    @PostMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<StreamingResponseBody> export(@RequestBody(required = false) Map<String, Object> request) {
        StreamingResponseBody body = outputStream -> {
            try (PrintWriter writer = new PrintWriter(outputStream)) {
                writer.println("business,city,status,total");
                analyticsService.loadTicketSummary().forEach(row -> writer.printf("%s,%s,%s,%d%n",
                        row.business(), row.city(), row.status(), row.total()));
            }
        };
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=analytics.csv")
                .body(body);
    }
}
