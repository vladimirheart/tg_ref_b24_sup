package com.example.panel.controller;

import com.example.panel.service.NavigationService;
import com.example.panel.service.UnblockRequestService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UnblockRequestsController {

    private final NavigationService navigationService;
    private final UnblockRequestService unblockRequestService;

    public UnblockRequestsController(NavigationService navigationService,
                                     UnblockRequestService unblockRequestService) {
        this.navigationService = navigationService;
        this.unblockRequestService = unblockRequestService;
    }

    @GetMapping("/unblock-requests")
    @PreAuthorize("hasAuthority('PAGE_CLIENTS')")
    public String list(@RequestParam(name = "status", required = false) String status,
                       Model model,
                       Authentication authentication) {
        navigationService.enrich(model, authentication);
        model.addAttribute("statusFilter", status);
        model.addAttribute("requests", unblockRequestService.loadRequests(status));
        model.addAttribute("availableStatuses", List.of("pending", "approved", "rejected", "expired"));
        return "clients/unblock_requests";
    }
}
