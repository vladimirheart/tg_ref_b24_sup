package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.NavigationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BusinessAdministrationPageController {

    private final NavigationService navigationService;
    private final MultibusinessFeatureFlags flags;

    public BusinessAdministrationPageController(NavigationService navigationService,
                                                MultibusinessFeatureFlags flags) {
        this.navigationService = navigationService;
        this.flags = flags;
    }

    @GetMapping("/businesses/manage")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
    public String page(Authentication authentication, Model model) {
        if (!flags.isFoundationEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        navigationService.enrich(model, authentication);
        model.addAttribute("activePage", "settings");
        return "businesses/manage";
    }
}
