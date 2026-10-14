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
public class BusinessScopePageController {

    private final NavigationService navigationService;
    private final MultibusinessFeatureFlags flags;

    public BusinessScopePageController(NavigationService navigationService,
                                       MultibusinessFeatureFlags flags) {
        this.navigationService = navigationService;
        this.flags = flags;
    }

    @GetMapping("/business-dialogs")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String page(Authentication authentication, Model model) {
        if (!flags.isFoundationEnabled() || !flags.isUiSelector()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        navigationService.enrich(model, authentication);
        model.addAttribute("activePage", "dialogs");
        return "businesses/dialogs";
    }
}
