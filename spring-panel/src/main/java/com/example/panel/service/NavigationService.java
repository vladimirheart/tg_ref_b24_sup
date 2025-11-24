package com.example.panel.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
public class NavigationService {

    private final PermissionService permissionService;

    public NavigationService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void enrich(Model model, Authentication authentication) {
        model.addAttribute("canViewAnalytics", permissionService.hasAuthority(authentication, "PAGE_ANALYTICS"));
        model.addAttribute("canEditKnowledgeBase", permissionService.hasAuthority(authentication, "PAGE_KNOWLEDGE_BASE"));
        model.addAttribute("canManageTasks", permissionService.hasAuthority(authentication, "PAGE_TASKS"));
        model.addAttribute("canManageChannels", permissionService.hasAuthority(authentication, "PAGE_CHANNELS"));
        model.addAttribute("canManageUsers", permissionService.hasAuthority(authentication, "PAGE_USERS"));
        model.addAttribute("canViewSettings", permissionService.hasAuthority(authentication, "PAGE_SETTINGS"));
        model.addAttribute("canViewPassports", permissionService.hasAuthority(authentication, "PAGE_OBJECT_PASSPORTS"));
    }
}
