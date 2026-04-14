package com.example.panel.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class PermissionService {

    public boolean isSuperUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String username = authentication.getName();
        if (username != null && "admin".equalsIgnoreCase(username.trim())) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth) || "ROLE_PORTAL_ADMIN".equals(auth));
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isSuperUser(authentication)) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals(authority));
    }

    public boolean hasAnyRole(Authentication authentication, Set<String> allowedRoles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isSuperUser(authentication)) {
            return true;
        }
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }
        Set<String> normalizedAllowedRoles = allowedRoles.stream()
                .map(this::normalizeRoleName)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (normalizedAllowedRoles.isEmpty()) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::normalizeRoleName)
                .anyMatch(normalizedAllowedRoles::contains);
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null) {
            return "";
        }
        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring("ROLE_".length());
        }
        return normalized;
    }
}
