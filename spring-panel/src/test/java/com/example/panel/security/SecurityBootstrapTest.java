package com.example.panel.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityBootstrapTest {

    @Test
    void externalModeWithoutAdminAndBootstrapCredentialsFailsFast() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        ObjectProvider<JdbcTemplate> jdbcProvider = mock(ObjectProvider.class);
        PanelSecurityProperties properties = new PanelSecurityProperties();

        when(jdbcProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        when(runtimeMode.isSqliteMode()).thenReturn(false);
        when(runtimeMode.modeLabel()).thenReturn("postgresql");
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_authorities", Integer.class)).thenReturn(1);
        when(jdbcTemplate.query(
            eq("SELECT DISTINCT user_id FROM user_authorities WHERE authority = ? ORDER BY user_id LIMIT 1"),
            any(RowMapper.class),
            eq("ROLE_ADMIN")
        )).thenReturn(List.of());

        SecurityBootstrap bootstrap = new SecurityBootstrap(jdbcProvider, passwordEncoder, runtimeMode, properties);

        assertThatThrownBy(bootstrap::ensureDefaultAdmin)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME");

        verify(jdbcTemplate, never()).update(eq("INSERT INTO users(username, password) VALUES(?, ?)"), any(), any());
        verify(jdbcTemplate, never()).update(eq("INSERT INTO users(username, password, enabled) VALUES(?, ?, ?)"), any(), any(), any());
    }

    @Test
    void externalModeUsesExplicitBootstrapCredentials() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        ObjectProvider<JdbcTemplate> jdbcProvider = mock(ObjectProvider.class);
        PanelSecurityProperties properties = new PanelSecurityProperties();

        properties.getBootstrapAdmin().setUsername("ops-admin");
        properties.getBootstrapAdmin().setPassword("super-secret");

        when(jdbcProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        when(runtimeMode.isSqliteMode()).thenReturn(false);
        when(runtimeMode.modeLabel()).thenReturn("postgresql");
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_authorities", Integer.class)).thenReturn(1);
        when(jdbcTemplate.query(
            eq("SELECT DISTINCT user_id FROM user_authorities WHERE authority = ? ORDER BY user_id LIMIT 1"),
            any(RowMapper.class),
            eq("ROLE_ADMIN")
        )).thenReturn(List.of());
        when(jdbcTemplate.query(
            eq("SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 1"),
            any(RowMapper.class),
            eq("ops-admin")
        )).thenReturn(List.of()).thenReturn(List.of(101L));
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(false);
        when(passwordEncoder.encode("super-secret")).thenReturn("encoded-secret");
        when(jdbcTemplate.query(
            eq("SELECT authority FROM user_authorities WHERE user_id = ?"),
            any(RowMapper.class),
            eq(101L)
        )).thenReturn(List.of(
            "ROLE_ADMIN",
            "PAGE_DIALOGS",
            "PAGE_ANALYTICS",
            "PAGE_CLIENTS",
            "PAGE_OBJECT_PASSPORTS",
            "PAGE_CHANNELS",
            "PAGE_USERS",
            "PAGE_SETTINGS",
            "PAGE_TASKS",
            "PAGE_KNOWLEDGE_BASE"
        ));
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM roles WHERE lower(name) = lower(?)"),
            eq(Integer.class),
            anyString()
        )).thenReturn(1);

        SecurityBootstrap bootstrap = new SecurityBootstrap(jdbcProvider, passwordEncoder, runtimeMode, properties);

        assertThatCode(bootstrap::ensureDefaultAdmin).doesNotThrowAnyException();

        verify(jdbcTemplate).update(
            "INSERT INTO users(username, password) VALUES(?, ?)",
            "ops-admin",
            "encoded-secret"
        );
    }
}
