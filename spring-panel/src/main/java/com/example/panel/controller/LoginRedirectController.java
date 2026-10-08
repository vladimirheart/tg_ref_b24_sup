package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import com.example.panel.service.WorkforceService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginRedirectController {

    private static final String[] PAGE_REDIRECTS = {
            "PAGE_DIALOGS:/",
            "PAGE_CLIENTS:/clients",
            "PAGE_TASKS:/tasks",
            "PAGE_ANALYTICS:/analytics",
            "PAGE_SETTINGS:/settings",
            "PAGE_USERS:/users",
            "PAGE_CHANNELS:/channels",
            "PAGE_KNOWLEDGE_BASE:/knowledge-base",
            "PAGE_OBJECT_PASSPORTS:/passports"
    };

    private final PermissionService permissionService;
    private final WorkforceService workforceService;

    public LoginRedirectController(PermissionService permissionService,
                                   WorkforceService workforceService) {
        this.permissionService = permissionService;
        this.workforceService = workforceService;
    }

    @GetMapping("/post-login")
    public String postLogin(Authentication authentication) {
        if (authentication != null && workforceService.requiresCheckIn(authentication.getName())) {
            return "redirect:/shift-check-in";
        }
        for (String mapping : PAGE_REDIRECTS) {
            String[] parts = mapping.split(":", 2);
            if (permissionService.hasAuthority(authentication, parts[0])) {
                return "redirect:" + parts[1];
            }
        }
        return "redirect:/error/403";
    }
}
