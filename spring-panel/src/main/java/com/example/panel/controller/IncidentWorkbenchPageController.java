package com.example.panel.controller;

import com.example.panel.service.NavigationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IncidentWorkbenchPageController {

    private final NavigationService navigationService;

    public IncidentWorkbenchPageController(NavigationService navigationService) {
        this.navigationService = navigationService;
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAnyAuthority('PAGE_DIALOGS','PAGE_TASKS','PAGE_OBJECT_PASSPORTS')")
    public String incidents(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        return "incidents/index";
    }
}
