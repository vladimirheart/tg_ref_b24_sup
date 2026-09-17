package com.example.panel.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PanelSecurityRuntimeGuardTest {

    @Test
    void externalRuntimeRejectsDefaultInternalBotApiToken() {
        PanelSecurityProperties properties = new PanelSecurityProperties();
        properties.setRememberMeKey("custom-remember-key");

        PanelSecurityRuntimeGuard guard = new PanelSecurityRuntimeGuard(
            properties,
            "iguana-internal-bot-token"
        );

        assertThatThrownBy(guard::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_INTERNAL_BOT_API_TOKEN");
    }

    @Test
    void externalRuntimeRejectsDefaultRememberMeKey() {
        PanelSecurityProperties properties = new PanelSecurityProperties();

        PanelSecurityRuntimeGuard guard = new PanelSecurityRuntimeGuard(
            properties,
            "custom-internal-token"
        );

        assertThatThrownBy(guard::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_SECURITY_REMEMBER_ME_KEY");
    }

    @Test
    void externalRuntimeAcceptsExplicitSecrets() {
        PanelSecurityProperties properties = new PanelSecurityProperties();
        properties.setRememberMeKey("custom-remember-key");

        PanelSecurityRuntimeGuard guard = new PanelSecurityRuntimeGuard(
            properties,
            "custom-internal-token"
        );

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }
}
