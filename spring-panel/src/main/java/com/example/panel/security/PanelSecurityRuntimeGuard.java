package com.example.panel.security;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PanelSecurityRuntimeGuard {

    private static final String DEFAULT_INTERNAL_BOT_API_TOKEN = "iguana-internal-bot-token";
    private static final String DEFAULT_REMEMBER_ME_KEY = "iguana-panel-remember-me";

    private final PanelDatabaseRuntimeMode databaseRuntimeMode;
    private final PanelSecurityProperties securityProperties;
    private final String internalBotApiToken;

    public PanelSecurityRuntimeGuard(
        PanelDatabaseRuntimeMode databaseRuntimeMode,
        PanelSecurityProperties securityProperties,
        @Value("${app.bots.internal-api.token:}") String internalBotApiToken
    ) {
        this.databaseRuntimeMode = databaseRuntimeMode;
        this.securityProperties = securityProperties;
        this.internalBotApiToken = internalBotApiToken;
    }

    @PostConstruct
    public void validate() {
        if (!databaseRuntimeMode.isExternalDatabaseEnabled()) {
            return;
        }

        requireNonDefaultSecret(
            "APP_INTERNAL_BOT_API_TOKEN",
            internalBotApiToken,
            DEFAULT_INTERNAL_BOT_API_TOKEN
        );
        requireNonDefaultSecret(
            "APP_SECURITY_REMEMBER_ME_KEY",
            securityProperties.getRememberMeKey(),
            DEFAULT_REMEMBER_ME_KEY
        );
    }

    private void requireNonDefaultSecret(String envKey, String value, String defaultValue) {
        if (!StringUtils.hasText(value) || defaultValue.equals(value.trim())) {
            throw new IllegalStateException(
                "Во внешнем production-like режиме необходимо явно задать безопасное значение "
                    + envKey + " вместо встроенного дефолта."
            );
        }
    }
}
