package com.example.panel.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecurityBootstrap {

    private final UserRepositoryUserDetailsService userDetailsService;
    private final String adminUsername;
    private final String adminPassword;

    public SecurityBootstrap(UserRepositoryUserDetailsService userDetailsService,
                              @Value("${app.security.bootstrap.username:admin}") String adminUsername,
                              @Value("${app.security.bootstrap.password:admin}") String adminPassword) {
        this.userDetailsService = userDetailsService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    public void init() {
        userDetailsService.ensureDefaultAdmin(adminUsername, adminPassword);
    }
}