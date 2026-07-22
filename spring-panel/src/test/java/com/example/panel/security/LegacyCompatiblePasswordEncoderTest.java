package com.example.panel.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyCompatiblePasswordEncoderTest {

    private final LegacyCompatiblePasswordEncoder passwordEncoder = new LegacyCompatiblePasswordEncoder();

    @Test
    void matchesPlainTextLegacyPassword() {
        assertThat(passwordEncoder.matches("admin", "admin")).isTrue();
    }

    @Test
    void matchesBcryptPassword() {
        String encoded = passwordEncoder.encode("secret");

        assertThat(passwordEncoder.matches("secret", encoded)).isTrue();
        assertThat(passwordEncoder.matches("wrong", encoded)).isFalse();
    }
}
