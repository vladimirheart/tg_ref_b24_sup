package com.example.panel.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserRepositoryUserDetailsServiceTest {

    @Test
    void individualOverridesAreAppliedAfterTheRoleGrants() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.getDataSource()).thenReturn(mock(javax.sql.DataSource.class));
        when(jdbc.queryForList("SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2", "reader"))
                .thenReturn(List.of(Map.of("id", 7L, "username", "reader", "password", "credential")));
        when(jdbc.queryForList("SELECT r.name AS role_name, r.permissions AS permissions " +
                "FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1", 7L))
                .thenReturn(List.of(Map.of("role_name", "reader", "permissions",
                        "{\"pages\":[\"settings\"],\"fields\":{\"edit\":[\"settings.dialogs\"]}}")));
        when(jdbc.query(org.mockito.ArgumentMatchers.eq("SELECT authority FROM user_authorities WHERE user_id = ?"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(List.of("DENY_EDIT_SETTINGS_DIALOGS", "EDIT_SETTINGS_BACKUP", "PAGE_TASKS"));
        var user = new UserRepositoryUserDetailsService(jdbc, new LegacyCompatiblePasswordEncoder()).loadUserByUsername("reader");
        assertThat(user.getAuthorities()).extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .contains("PAGE_SETTINGS", "PAGE_TASKS", "EDIT_SETTINGS_BACKUP")
                .doesNotContain("EDIT_SETTINGS_DIALOGS");
    }

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

    @Test
    void loadUserByUsernameDefaultsToEnabledWhenColumnIsAbsent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LegacyCompatiblePasswordEncoder passwordEncoder = new LegacyCompatiblePasswordEncoder();
        UserRepositoryUserDetailsService service = new UserRepositoryUserDetailsService(jdbcTemplate, passwordEncoder);

        when(jdbcTemplate.getDataSource()).thenReturn(mock(javax.sql.DataSource.class));
        when(jdbcTemplate.queryForList("SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2", "operator"))
                .thenReturn(List.of(Map.of(
                        "id", 42L,
                        "username", "operator",
                        "password", "plain-secret"
                )));

        UserDetails user = service.loadUserByUsername("operator");

        assertThat(user.getPassword()).isEqualTo("plain-secret");
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsernameTreatsBlockedUserAsDisabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LegacyCompatiblePasswordEncoder passwordEncoder = new LegacyCompatiblePasswordEncoder();
        UserRepositoryUserDetailsService service = new UserRepositoryUserDetailsService(jdbcTemplate, passwordEncoder);

        when(jdbcTemplate.getDataSource()).thenReturn(mock(javax.sql.DataSource.class));
        when(jdbcTemplate.queryForList("SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2", "blocked"))
                .thenReturn(List.of(Map.of(
                        "id", 43L,
                        "username", "blocked",
                        "password", "plain-secret",
                        "is_blocked", 1
                )));

        UserDetails user = service.loadUserByUsername("blocked");

        assertThat(user.isEnabled()).isFalse();
    }
}
