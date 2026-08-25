package com.example.panel.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import org.junit.jupiter.api.Test;

class PanelSecurityRuntimeGuardTest {

    @Test
    void externalModeRejectsDefaultInternalBotApiToken() {
        PanelSecurityProperties properties = new PanelSecurityProperties();
        properties.setRememberMeKey("custom-remember-key");

        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        when(runtimeMode.isExternalDatabaseEnabled()).thenReturn(true);

        PanelSecurityRuntimeGuard guard = new PanelSecurityRuntimeGuard(
            runtimeMode,
            properties,
            "iguana-internal-bot-token"
        );

        assertThatThrownBy(guard::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_INTERNAL_BOT_API_TOKEN");
    }

    @Test
    void externalModeRejectsDefaultRememberMeKey() {
        PanelSecurityProperties properties = new PanelSecurityProperties();

        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        when(runtimeMode.isExternalDatabaseEnabled()).thenReturn(true);

        PanelSecurityRuntimeGuard guard = new PanelSecurityRuntimeGuard(
            runtimeMode,
            properties,
            "custom-internal-token"
        );

        assertThatThrownBy(guard::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_SECURITY_REMEMBER_ME_KEY");
    }

    @Test
    void sqliteCompatibilityModeAllowsDevDefaults() {
        PanelSecurityProperties properties = new PanelSecurityProperties();

        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        when(runtimeMode.isExternalDatabaseEnabled()).thenReturn(false);

        PanelSecurityRuntimeGuard guard = new PanelSecurityRuntimeGuard(
            runtimeMode,
            properties,
            "iguana-internal-bot-token"
        );

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }
}
