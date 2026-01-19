package com.example.panel.controller;

import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogService;
import com.example.panel.service.NavigationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DialogsController {

    private static final Logger log = LoggerFactory.getLogger(DialogsController.class);

    private final NavigationService navigationService;
    private final DialogService dialogService;

    public DialogsController(NavigationService navigationService, DialogService dialogService) {
        this.navigationService = navigationService;
        this.dialogService = dialogService;
    }

    @GetMapping("/")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String dialogs(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        model.addAttribute("activePage", "dialogs");
        try {
            DialogSummary summary = dialogService.loadSummary();
            model.addAttribute("summary", summary);
            model.addAttribute("dialogs", dialogService.loadDialogs());
            log.info("Loaded dialogs page for user {}", authentication != null ? authentication.getName() : "unknown");
        } catch (Exception ex) {
            log.error("Failed to load dialogs page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "dialogs/index";
    }
}
