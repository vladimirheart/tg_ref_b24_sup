package com.example.panel.service;

import com.example.panel.entity.PanelUser;
import com.example.panel.repository.PanelUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

@Service
public class NavigationService {

    private final PermissionService permissionService;
    private final PanelUserRepository panelUserRepository;
    private final UnblockRequestService unblockRequestService;
    private final PanelUserPhotoService panelUserPhotoService;

    public NavigationService(PermissionService permissionService,
                             PanelUserRepository panelUserRepository,
                             UnblockRequestService unblockRequestService,
                             PanelUserPhotoService panelUserPhotoService) {
        this.permissionService = permissionService;
        this.panelUserRepository = panelUserRepository;
        this.unblockRequestService = unblockRequestService;
        this.panelUserPhotoService = panelUserPhotoService;
    }

    public void enrich(Model model, Authentication authentication) {
        model.addAttribute("canViewAnalytics", permissionService.hasAuthority(authentication, "PAGE_ANALYTICS"));
        model.addAttribute("canEditKnowledgeBase", permissionService.hasAuthority(authentication, "PAGE_KNOWLEDGE_BASE"));
        model.addAttribute("canManageTasks", permissionService.hasAuthority(authentication, "PAGE_TASKS"));
        model.addAttribute("canManageChannels", permissionService.hasAuthority(authentication, "PAGE_CHANNELS"));
        model.addAttribute("canManageUsers", permissionService.hasAuthority(authentication, "PAGE_USERS"));
        model.addAttribute("canManagePasswordResetRequests",
                permissionService.isSuperUser(authentication) || permissionService.hasAuthority(authentication, "ROLE_PORTAL_ADMIN"));
        model.addAttribute("canViewSettings", permissionService.hasAuthority(authentication, "PAGE_SETTINGS"));
        model.addAttribute("canViewPassports", permissionService.hasAuthority(authentication, "PAGE_OBJECT_PASSPORTS"));
        model.addAttribute("canViewClients", permissionService.hasAuthority(authentication, "PAGE_CLIENTS"));
        model.addAttribute("pendingUnblockRequests", loadPendingUnblockCount(authentication));
        enrichUser(model, authentication);
    }

    private long loadPendingUnblockCount(Authentication authentication) {
        if (!permissionService.hasAuthority(authentication, "PAGE_CLIENTS")) {
            return 0;
        }
        return unblockRequestService.countPendingRequests();
    }

    private void enrichUser(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        String username = authentication.getName();
        PanelUser user = panelUserRepository.findByUsernameIgnoreCase(username).orElse(null);
        String displayName = username;
        String resolvedUsername = username;
        String avatar = null;
        if (user != null) {
            if (StringUtils.hasText(user.getFullName())) {
                displayName = user.getFullName();
            }
            if (StringUtils.hasText(user.getUsername())) {
                resolvedUsername = user.getUsername();
            }
            avatar = user.getPhoto();
        }
        String avatarUrl = panelUserPhotoService.resolveUrl(avatar, "/avatar_default.svg");
        model.addAttribute("sidebarUserDisplayName", displayName);
        model.addAttribute("sidebarUserUsername", resolvedUsername);
        model.addAttribute("sidebarUserAvatarUrl", avatarUrl);
        model.addAttribute("sidebarUserHasAvatar", avatarUrl != null && !"/avatar_default.svg".equals(avatarUrl));
    }
}
