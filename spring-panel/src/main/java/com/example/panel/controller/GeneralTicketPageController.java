package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralTicketAccessService;
import com.example.panel.service.NavigationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class GeneralTicketPageController {
    private final MultibusinessFeatureFlags flags;
    private final GeneralTicketAccessService access;
    private final NavigationService navigation;

    public GeneralTicketPageController(MultibusinessFeatureFlags flags, GeneralTicketAccessService access,
                                       NavigationService navigation) {
        this.flags = flags;
        this.access = access;
        this.navigation = navigation;
    }

    @GetMapping("/general-dialogs")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String queue(Authentication authentication, Model model) {
        requireEnabled();
        long userId=access.requireAuthenticatedUserId(authentication.getName());
        access.requireRead(userId);
        model.addAttribute("canCreateGeneral",flags.isManualTicketCreation()&&access.canOperate(userId));
        navigation.enrich(model, authentication);
        model.addAttribute("activePage", "dialogs");
        return "general/dialogs";
    }

    @GetMapping("/general-dialogs/access")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
    public String access(Authentication authentication, Model model) {
        requireEnabled();
        navigation.enrich(model, authentication);
        model.addAttribute("activePage", "settings");
        return "general/access";
    }

    private void requireEnabled() {
        if (!flags.isFoundationEnabled() || !flags.isGeneralTicketQueue()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
