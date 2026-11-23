package com.example.panel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/** A revoked remember-me token cannot silently create another valid session. */
final class VersionedRememberMeServices extends TokenBasedRememberMeServices {
    private final UserRepositoryUserDetailsService users;

    VersionedRememberMeServices(String key, UserRepositoryUserDetailsService users) {
        super(key, username -> signingPrincipal((PanelUserPrincipal) users.loadUserByUsername(username)));
        this.users = users;
        setParameter("remember-me");
        setTokenValiditySeconds(14 * 24 * 60 * 60);
    }

    private static PanelUserPrincipal signingPrincipal(PanelUserPrincipal account) {
        String password = account.getPassword();
        // Preserve previously issued cookies only until the first explicit revocation.
        if (account.getSessionVersion() != 0) password += "\niguana-session-version-v1:" + account.getSessionVersion();
        return new PanelUserPrincipal(account.getUserId(), account.getUsername(), password, account.isEnabled(),
            account.getAuthorities(), account.getSessionVersion());
    }

    @Override public void onLoginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication success) {
        if (!(success.getPrincipal() instanceof PanelUserPrincipal snapshot)) {
            cancelCookie(request, response);
            return;
        }
        PanelUserPrincipal current = users.loadCurrentUser(snapshot.getUserId());
        if (!current.isEnabled() || !current.getUsername().equals(snapshot.getUsername())
                || current.getSessionVersion() != snapshot.getSessionVersion()) {
            cancelCookie(request, response);
            return;
        }
        var signing = signingPrincipal(current);
        try {
            super.onLoginSuccess(request, response,
                UsernamePasswordAuthenticationToken.authenticated(signing, null, signing.getAuthorities()));
        } finally {
            signing.eraseCredentials();
            current.eraseCredentials();
        }
    }

    @Override protected UserDetails processAutoLoginCookie(String[] tokens, HttpServletRequest request,
                                                           HttpServletResponse response) {
        try {
            UserDetails account = super.processAutoLoginCookie(tokens, request, response);
            if (account instanceof org.springframework.security.core.CredentialsContainer credentials) credentials.eraseCredentials();
            return account;
        } catch (RememberMeAuthenticationException invalid) {
            // Spring's signature mismatch message contains token material; never retain it in logs.
            throw new InvalidCookieException("Remember-me token is invalid or revoked");
        }
    }
}
