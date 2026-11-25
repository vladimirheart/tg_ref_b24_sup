package com.example.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

final class LoginRequestGuardFilter extends OncePerRequestFilter {
    static final String START = LoginRequestGuardFilter.class.getName() + ".start";
    private final PublicAuthenticationGuard guard;
    private final RequestClientAddressResolver addresses;
    LoginRequestGuardFilter(PublicAuthenticationGuard guard, RequestClientAddressResolver addresses) {
        this.guard = guard; this.addresses = addresses;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/login".equals(request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        request.setAttribute(START, started);
        response.setHeader("Cache-Control", "no-store");
        try {
            if (!guard.allow("login", addresses.resolve(request), String.valueOf(request.getParameter("username")))) {
                guard.recordFailure("login_limited", addresses.resolve(request), request.getParameter("username"), request.getHeader("User-Agent"));
                guard.finish(started);
                response.sendRedirect(request.getContextPath() + "/login?error");
                return;
            }
        } catch (DataAccessException unavailable) {
            guard.finish(started);
            response.sendError(503);
            return;
        }
        chain.doFilter(request, response);
    }
}
