package com.example.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Refreshes access before protected requests; never resolves an existing session by username. */
final class SessionAuthorityFilter extends OncePerRequestFilter {
    private final UserRepositoryUserDetailsService users;
    private final boolean databaseBacked;
    private final RequestMatcher publicRequests;
    private final SecurityContextRepository contexts;
    private final String rememberMeKey;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookies = new CookieClearingLogoutHandler("JSESSIONID", "remember-me");

    SessionAuthorityFilter(UserRepositoryUserDetailsService users, RequestMatcher publicRequests,
                           SecurityContextRepository contexts, String rememberMeKey) {
        this.users = users;
        this.databaseBacked = users.getDataSource() != null;
        this.publicRequests = publicRequests;
        this.contexts = contexts;
        this.rememberMeKey = rememberMeKey;
        logout.setSecurityContextRepository(contexts);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !databaseBacked || publicRequests.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        if (!(current.getPrincipal() instanceof PanelUserPrincipal principal)
                || !(current instanceof UsernamePasswordAuthenticationToken || current instanceof RememberMeAuthenticationToken)) {
            revoke(request, response, current);
            chain.doFilter(request, response);
            return;
        }
        PanelUserPrincipal fresh;
        try {
            fresh = users.loadCurrentUser(principal.getUserId());
        } catch (UsernameNotFoundException ex) {
            revoke(request, response, current);
            chain.doFilter(request, response);
            return;
        } catch (DataAccessException ex) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (fresh.getUserId() != principal.getUserId() || !fresh.getUsername().equals(principal.getUsername())
                || !fresh.isEnabled() || !fresh.isAccountNonLocked() || !fresh.isAccountNonExpired()
                || !fresh.isCredentialsNonExpired()) {
            revoke(request, response, current);
            chain.doFilter(request, response);
            return;
        }
        fresh.eraseCredentials();
        if (!AuthorityUtils.authorityListToSet(current.getAuthorities()).equals(AuthorityUtils.authorityListToSet(fresh.getAuthorities()))) {
            var refreshed = current instanceof RememberMeAuthenticationToken
                ? new RememberMeAuthenticationToken(rememberMeKey, fresh, fresh.getAuthorities())
                : UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities());
            refreshed.setDetails(current.getDetails());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(refreshed);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
        }
        chain.doFilter(request, response);
    }

    private void revoke(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        cookies.logout(request, response, authentication);
        logout.logout(request, response, authentication);
    }
}
