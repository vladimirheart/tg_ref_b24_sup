package com.example.panel.config;

import com.example.panel.service.DatabaseHealthService;
import com.example.panel.service.UiPreferenceService;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final Optional<DatabaseHealthService> databaseHealthService;
    private final Optional<UiPreferenceService> uiPreferenceService;

    public GlobalModelAttributes(ObjectProvider<DatabaseHealthService> databaseHealthService,
                                 ObjectProvider<UiPreferenceService> uiPreferenceService) {
        this.databaseHealthService = Optional.ofNullable(databaseHealthService.getIfAvailable());
        this.uiPreferenceService = Optional.ofNullable(uiPreferenceService.getIfAvailable());
    }

    @ModelAttribute
    public void addDatabaseWarning(Model model) {
        databaseHealthService.ifPresent(service ->
            service.detectProblem().ifPresent(message -> {
                model.addAttribute("dbWarning", message);
                model.addAttribute("dbPath", service.databasePath());
            })
        );
    }

    @ModelAttribute
    public void addUiPreferenceBootstrap(Model model, Authentication authentication) {
        boolean authenticated = authentication != null
            && authentication.isAuthenticated()
            && StringUtils.hasText(authentication.getName())
            && !"anonymousUser".equalsIgnoreCase(authentication.getName());
        model.addAttribute("uiPreferencesSyncEnabled", authenticated);
        if (!authenticated) {
            model.addAttribute("uiPreferencesBootstrap", java.util.Map.of());
            return;
        }
        model.addAttribute(
            "uiPreferencesBootstrap",
            uiPreferenceService.map(service -> service.loadForUser(authentication.getName())).orElse(java.util.Map.of())
        );
    }
}
