package com.example.panel.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"$2a$", "$2b$", "$2y$"})
    void storedBcryptHashIsNeverAcceptedAsThePassword(String prefix) {
        String encoded = passwordEncoder.encode("fixture original password");
        String stored = prefix + encoded.substring(4);

        assertThat(passwordEncoder.matches(stored, stored)).isFalse();
        assertThat(passwordEncoder.matches("fixture original password", stored)).isTrue();
    }

    @Test
    void bcryptLookingUserPasswordStillWorksWhenEncoded() {
        String password = passwordEncoder.encode("fixture other password");
        String encoded = passwordEncoder.encode(password);

        assertThat(passwordEncoder.matches(password, encoded)).isTrue();
        assertThat(passwordEncoder.matches(encoded, encoded)).isFalse();
    }
}
