package com.example.panel.service;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceTest {

    private final PermissionService permissionService = new PermissionService();

    @Test
    void hasAnyRoleReturnsTrueWhenAllowListIsEmpty() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_SUPPORT"))
        );

        assertTrue(permissionService.hasAnyRole(auth, Set.of()));
    }

    @Test
    void hasAnyRoleMatchesWithOrWithoutRolePrefix() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "lead",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
        );

        assertTrue(permissionService.hasAnyRole(auth, Set.of("SUPERVISOR")));
        assertTrue(permissionService.hasAnyRole(auth, Set.of("ROLE_SUPERVISOR")));
        assertFalse(permissionService.hasAnyRole(auth, Set.of("ADMIN")));
    }
}
