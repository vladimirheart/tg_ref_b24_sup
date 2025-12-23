package com.example.panel.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private static final Logger log = LoggerFactory.getLogger(AuthApiController.class);

    @GetMapping("/whoami")
    public Map<String, Object> whoAmI(Authentication authentication) {
        if (authentication == null) {
            log.warn("whoami called without authentication context");
            return Map.of("authenticated", false);
        }
        Object principal = authentication.getPrincipal();
        String username = principal instanceof UserDetails userDetails ? userDetails.getUsername() : authentication.getName();
        log.info("whoami for user {} with {} authorities", username, authentication.getAuthorities().size());
        return Map.of(
                "authenticated", authentication.isAuthenticated(),
                "username", username,
                "authorities", authentication.getAuthorities().stream()
                        .map(granted -> granted.getAuthority())
                        .toList()
        );
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        log.info("Ping requested, authenticated={}", authenticated);
        return Map.of("success", authenticated);
    }
}
