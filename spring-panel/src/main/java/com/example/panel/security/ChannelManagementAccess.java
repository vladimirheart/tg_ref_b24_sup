package com.example.panel.security;

import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/** Channel administration is owned by the Settings channels block. */
public final class ChannelManagementAccess {
    public static final String REQUIRE = "T(com.example.panel.security.PanelAccessPolicy).canEdit(authentication, 'channels')";

    private ChannelManagementAccess() {}

    public static boolean isAllowed(Authentication authentication) {
        return PanelAccessPolicy.canEdit(authentication, "channels");
    }

    public static boolean hasSettingsPage(Authentication authentication) {
        return authorities(authentication).contains("PAGE_SETTINGS");
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
            ? Set.of() : AuthorityUtils.authorityListToSet(authentication.getAuthorities());
    }
}
