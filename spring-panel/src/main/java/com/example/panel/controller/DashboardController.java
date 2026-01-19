package com.example.panel.controller;

import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final PermissionService permissionService;
    private final DialogService dialogService;
    private final NavigationService navigationService;

    public DashboardController(PermissionService permissionService,
                              DialogService dialogService,
                              NavigationService navigationService) {
        this.permissionService = permissionService;
        this.dialogService = dialogService;
        this.navigationService = navigationService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String dashboard(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        model.addAttribute("activePage", "dashboard");
        return renderDashboard(authentication, model);
    }

    private String renderDashboard(Authentication authentication, Model model) {
        try {
            DialogSummary summary = dialogService.loadSummary();
            var dialogs = dialogService.loadDialogs();
            model.addAttribute("summary", summary);
            model.addAttribute("dialogs", dialogs);
            model.addAttribute("restaurants", List.of());
            log.info("Rendering dashboard for user {} with {} dialogs", authentication != null ? authentication.getName() : "unknown", dialogs.size());
        } catch (Exception ex) {
            log.error("Failed to render dashboard for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "dashboard/index";
    }
}
