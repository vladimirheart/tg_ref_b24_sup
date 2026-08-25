package com.example.panel.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class PanelSecurityProperties {

    private String rememberMeKey = "iguana-panel-remember-me";
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    public String getRememberMeKey() {
        return rememberMeKey;
    }

    public void setRememberMeKey(String rememberMeKey) {
        this.rememberMeKey = rememberMeKey;
    }

    public BootstrapAdmin getBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public static class BootstrapAdmin {

        private String username = "";
        private String password = "";
        private boolean allowDefaultCredentialsInSqlite = true;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isAllowDefaultCredentialsInSqlite() {
            return allowDefaultCredentialsInSqlite;
        }

        public void setAllowDefaultCredentialsInSqlite(boolean allowDefaultCredentialsInSqlite) {
            this.allowDefaultCredentialsInSqlite = allowDefaultCredentialsInSqlite;
        }
    }
}
