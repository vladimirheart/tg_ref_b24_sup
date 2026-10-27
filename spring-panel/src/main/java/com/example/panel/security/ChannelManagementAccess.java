package com.example.panel.security;

import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/** Global channel administration requires an explicit system role and page authority. */
public final class ChannelManagementAccess {
    public static final String REQUIRE = "hasAuthority('ROLE_ADMIN') and hasAnyAuthority('PAGE_CHANNELS', 'PAGE_SETTINGS')";

    private ChannelManagementAccess() {}

    public static boolean isAllowed(Authentication authentication) {
        Set<String> rights = authorities(authentication);
        return rights.contains("ROLE_ADMIN")
            && (rights.contains("PAGE_CHANNELS") || rights.contains("PAGE_SETTINGS"));
    }

    public static boolean hasSettingsPage(Authentication authentication) {
        return authorities(authentication).contains("PAGE_SETTINGS");
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
            ? Set.of() : AuthorityUtils.authorityListToSet(authentication.getAuthorities());
    }
}
