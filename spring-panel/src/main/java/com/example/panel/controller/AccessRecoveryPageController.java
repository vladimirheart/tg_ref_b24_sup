package com.example.panel.controller;

import com.example.panel.service.AccessRecoveryService;
import com.example.panel.service.NavigationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessRecoveryPageController {
    private final AccessRecoveryService recovery;
    private final NavigationService navigation;
    public AccessRecoveryPageController(AccessRecoveryService recovery, NavigationService navigation) {
        this.recovery = recovery; this.navigation = navigation;
    }
    @GetMapping("/access-recovery-requests")
    public String page(Model model, Authentication actor) {
        recovery.requireRecovery(actor);
        navigation.enrich(model, actor);
        return "auth/access-recovery-requests";
    }
}
