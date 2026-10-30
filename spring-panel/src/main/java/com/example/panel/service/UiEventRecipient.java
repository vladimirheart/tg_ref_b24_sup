package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Connection identity is immutable; a routing name never authorizes another account. */
record UiEventRecipient(long userId, String username) {
    static UiEventRecipient from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal account)
                || account.getUserId() <= 0 || account.getUsername() == null || account.getUsername().isBlank()
                || !account.isEnabled() || !account.isAccountNonLocked()
                || !account.isAccountNonExpired() || !account.isCredentialsNonExpired()) return null;
        return new UiEventRecipient(account.getUserId(), account.getUsername());
    }
    String routingKey() { return username.toLowerCase(Locale.ROOT); }
    @Override public String toString() { return "UiEventRecipient"; }
}
