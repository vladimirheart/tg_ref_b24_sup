package com.example.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "same-origin");
        boolean documentPreview = "true".equals(request.getParameter("preview"))
                && SettingsWritePermissionFilter.requestPath(request).matches(
                        "/api/object_passports/[1-9][0-9]*/documents/[0-9a-fA-F-]{36}/revisions/[1-9][0-9]*/file");
        // ResponseEntity adds headers. Replace the initial DENY only when this typed file response explicitly opts in.
        filterChain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override public void addHeader(String name, String value) {
                if (documentPreview && "X-Frame-Options".equalsIgnoreCase(name) && "SAMEORIGIN".equals(value))
                    super.setHeader(name, value);
                else super.addHeader(name, value);
            }
        });
    }
}
