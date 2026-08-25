package com.example.panel.controller;

import com.example.panel.model.AnalyticsClientSummary;
import com.example.panel.model.AnalyticsTicketSummary;
import com.example.panel.service.AnalyticsService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

        List<String> loadWarnings = new ArrayList<>();
        List<AnalyticsTicketSummary> ticketSummary = loadTicketSummary(loadWarnings);
        List<AnalyticsClientSummary> clientSummary = loadClientSummary(loadWarnings);
        model.addAttribute("ticketSummary", ticketSummary);
        model.addAttribute("clientSummary", clientSummary);
        model.addAttribute("analyticsLoadWarnings", loadWarnings);

        Map<String, Object> dialogConfig = loadDialogConfig(loadWarnings);
        model.addAttribute("crossProductOmnichannelDashboardUrl", String.valueOf(dialogConfig.getOrDefault("cross_product_omnichannel_dashboard_url", "")).trim());
        model.addAttribute("crossProductOmnichannelDashboardLabel", String.valueOf(dialogConfig.getOrDefault("cross_product_omnichannel_dashboard_label", "Omni-channel KPI dashboard")).trim());
        model.addAttribute("crossProductFinanceDashboardUrl", String.valueOf(dialogConfig.getOrDefault("cross_product_finance_dashboard_url", "")).trim());
        model.addAttribute("crossProductFinanceDashboardLabel", String.valueOf(dialogConfig.getOrDefault("cross_product_finance_dashboard_label", "Финансовый KPI dashboard")).trim());
        model.addAttribute("crossProductDashboardLinksRequired", Boolean.parseBoolean(
                String.valueOf(dialogConfig.getOrDefault("workspace_rollout_external_kpi_dashboard_links_required", false))));
        model.addAttribute("crossProductOmnichannelReady", Boolean.parseBoolean(
                String.valueOf(dialogConfig.getOrDefault("workspace_rollout_external_kpi_omnichannel_ready", false))));
        model.addAttribute("crossProductFinanceReady", Boolean.parseBoolean(
                String.valueOf(dialogConfig.getOrDefault("workspace_rollout_external_kpi_finance_ready", false))));
        String dependencyTicketUrl = String.valueOf(dialogConfig.getOrDefault(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_url", "")).trim();
        model.addAttribute("crossProductDependencyTicketUrl", dependencyTicketUrl);
        model.addAttribute("crossProductDependencyTicketRequired", Boolean.parseBoolean(
                String.valueOf(dialogConfig.getOrDefault(
                        "workspace_rollout_external_kpi_datamart_dependency_ticket_required", false))));
        model.addAttribute("crossProductDependencyTicketFreshnessRequired", Boolean.parseBoolean(
                String.valueOf(dialogConfig.getOrDefault(
                        "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required", false))));
        model.addAttribute("crossProductDependencyTicketUpdatedAt", String.valueOf(dialogConfig.getOrDefault(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at", "")).trim());
        model.addAttribute("crossProductDependencyTicketTtlHours", String.valueOf(dialogConfig.getOrDefault(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours", "336")).trim());

        log.info("Analytics view requested by {}: {} ticket rows, {} client rows, {} degraded source(s)",
                authentication != null ? authentication.getName() : "unknown",
                ticketSummary.size(),
                clientSummary.size(),
                loadWarnings.size());
        return "analytics/index";
    }

    private List<AnalyticsTicketSummary> loadTicketSummary(List<String> warnings) {
        try {
            return analyticsService.loadTicketSummary();
        } catch (Exception ex) {
            warnings.add("ticket-summary");
            log.error("Analytics ticket summary is temporarily unavailable", ex);
            return List.of();
        }
    }

    private List<AnalyticsClientSummary> loadClientSummary(List<String> warnings) {
        try {
            return analyticsService.loadClientSummary();
        } catch (Exception ex) {
            warnings.add("client-summary");
            log.error("Analytics client summary is temporarily unavailable", ex);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadDialogConfig(List<String> warnings) {
        try {
            Map<String, Object> settings = sharedConfigService.loadSettings();
            return settings.get("dialog_config") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
        } catch (Exception ex) {
            warnings.add("shared-config");
            log.error("Analytics shared configuration is temporarily unavailable", ex);
            return Map.of();
        }
    }

    @GetMapping("/certificates")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String sslCertificatesMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/certificates";
    }

    @GetMapping("/rms-control")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String rmsMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/rms-control";
    }

    @GetMapping("/iiko-api-monitoring")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String iikoApiMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/iiko-api-monitoring";
    }

    @GetMapping("/backup-readiness")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String backupReadinessMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/backup-readiness";
    }

    @GetMapping("/public-ingress")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String publicIngressMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/public-ingress";
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
