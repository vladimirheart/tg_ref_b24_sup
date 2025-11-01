package com.example.panel.controller;

import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogService;
import com.example.panel.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final PermissionService permissionService;
    private final DialogService dialogService;

    public DashboardController(PermissionService permissionService, DialogService dialogService) {
        this.permissionService = permissionService;
        this.dialogService = dialogService;
    }

    @GetMapping("/")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String index(Authentication authentication, Model model) {
        model.addAttribute("canViewAnalytics", permissionService.hasAuthority(authentication, "PAGE_ANALYTICS"));
        model.addAttribute("canEditKnowledgeBase", permissionService.hasAuthority(authentication, "PAGE_KNOWLEDGE_BASE"));
        DialogSummary summary = dialogService.loadSummary();
        model.addAttribute("summary", summary);
        model.addAttribute("dialogs", dialogService.loadDialogs());
        return "dashboard/index";
    }
}
