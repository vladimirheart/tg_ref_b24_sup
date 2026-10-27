package com.example.panel.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;

class PermissionIdentityBoundaryTest {
    private final PermissionService permissions = new PermissionService();

    static Stream<String> otherAccounts() {
        return Stream.of(" admin", "admin ", " admin ", "\tadmin", "\nadmin", "\radmin",
            "AdMiN\t", "admin\r\n", "\u2003admin\u2003", "administrator", "admin-user", "9007199254740993");
    }

    private Authentication authenticated(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "unused",
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @ParameterizedTest @MethodSource("otherAccounts")
    void anotherLiteralAccountCannotInheritAdminPrivileges(String username) {
        var actor = authenticated(username, "PAGE_DIALOGS", "ROLE_SUPPORT");
        assertFalse(permissions.isSuperUser(actor));
        assertFalse(permissions.hasAuthority(actor, "PAGE_SETTINGS"));
        assertFalse(permissions.hasAnyRole(actor, Set.of("ADMIN")));
        assertTrue(permissions.hasAuthority(actor, "PAGE_DIALOGS"));
        assertTrue(permissions.hasAnyRole(actor, Set.of("SUPPORT")));
        assertEquals(username, actor.getName());
    }

    @ParameterizedTest @ValueSource(strings = {"admin", "ADMIN", "AdMiN"})
    void establishedUnpaddedAdminCompatibilityIsPreserved(String username) {
        var actor = authenticated(username);
        assertTrue(permissions.isSuperUser(actor));
        assertTrue(permissions.hasAuthority(actor, "PAGE_SETTINGS"));
        assertTrue(permissions.hasAnyRole(actor, Set.of("SUPERVISOR")));
    }

    static Stream<Arguments> administrators() {
        return Stream.of("ROLE_ADMIN", "ROLE_PORTAL_ADMIN").flatMap(role ->
            Stream.of("operator", " admin ", "9007199254740993").map(name -> Arguments.of(name, role)));
    }

    @ParameterizedTest @MethodSource("administrators")
    void explicitAdministrativeRoleStillGrantsPrivileges(String username, String role) {
        var actor = authenticated(username, role);
        assertTrue(permissions.isSuperUser(actor));
        assertTrue(permissions.hasAuthority(actor, "PAGE_SETTINGS"));
        assertTrue(permissions.hasAnyRole(actor, Set.of("SUPERVISOR")));
    }

    @ParameterizedTest @ValueSource(strings = {"BUSINESS_ADMIN", "ALL_BUSINESSES", "ROLE_SUPPORT", "ROLE_ADMINISTRATOR"})
    void otherRolesAndBusinessTokensDoNotBecomeSystemAdministration(String authority) {
        var actor = authenticated(" admin ", authority);
        assertFalse(permissions.isSuperUser(actor));
        assertFalse(permissions.hasAuthority(actor, "PAGE_SETTINGS"));
        assertFalse(permissions.hasAnyRole(actor, Set.of("PORTAL_ADMIN")));
    }

    @Test void missingAndUnauthenticatedActorsHaveNoPrivilegesEvenWithAdminName() {
        for (Authentication actor : Arrays.asList(null, new UsernamePasswordAuthenticationToken("admin", "unused"))) {
            assertFalse(permissions.isSuperUser(actor));
            assertFalse(permissions.hasAuthority(actor, "PAGE_SETTINGS"));
            assertFalse(permissions.hasAnyRole(actor, Set.of()));
        }
    }

    @Test void existingRoleVocabularyAndEmptyAllowListArePreservedForLiteralAccount() {
        var actor = authenticated(" admin ", "ROLE_SUPPORT");
        assertTrue(permissions.hasAnyRole(actor, Set.of(" role_support ")));
        assertTrue(permissions.hasAnyRole(actor, Set.of()));
        assertTrue(permissions.hasAnyRole(actor, null));
        assertFalse(permissions.hasAnyRole(actor, Set.of("SUPERVISOR")));
    }
}
