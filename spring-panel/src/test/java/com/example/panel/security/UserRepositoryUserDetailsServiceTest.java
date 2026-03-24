package com.example.panel.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryUserDetailsServiceTest {

    @Test
    void extractsPagePermissionsFromRoleJson() {
        List<String> pages = UserRepositoryUserDetailsService.extractPagePermissions(
                "{\"pages\":[\"dialogs\",\"clients\",\"settings\"]}"
        );

        assertThat(pages).containsExactly("dialogs", "clients", "settings");
    }

    @Test
    void mapsWildcardPagePermissionToAllKnownAuthorities() {
        Set<String> authorities = UserRepositoryUserDetailsService.mapPagePermissionsToAuthorities(List.of("*"));

        assertThat(authorities).contains(
                "PAGE_DIALOGS",
                "PAGE_TASKS",
                "PAGE_CLIENTS",
                "PAGE_OBJECT_PASSPORTS",
                "PAGE_KNOWLEDGE_BASE",
                "PAGE_ANALYTICS",
                "PAGE_CHANNELS",
                "PAGE_SETTINGS",
                "PAGE_USERS"
        );
    }

    @Test
    void mapsPagePermissionsToSpringAuthorities() {
        Set<String> authorities = UserRepositoryUserDetailsService.mapPagePermissionsToAuthorities(
                List.of("dialogs", "user_management", "object_passports")
        );

        assertThat(authorities).containsExactlyInAnyOrder(
                "PAGE_DIALOGS",
                "PAGE_USERS",
                "PAGE_OBJECT_PASSPORTS"
        );
    }
}
