package com.example.panel.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    @GetMapping("/whoami")
    public Map<String, Object> whoAmI(Authentication authentication) {
        if (authentication == null) {
            return Map.of("authenticated", false);
        }
        Object principal = authentication.getPrincipal();
        String username = principal instanceof UserDetails userDetails ? userDetails.getUsername() : authentication.getName();
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
        return Map.of("success", authenticated);
    }
}
