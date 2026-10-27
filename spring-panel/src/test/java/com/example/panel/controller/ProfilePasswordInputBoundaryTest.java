package com.example.panel.controller;

import com.example.panel.service.UiPreferenceService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfilePasswordInputBoundaryTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final ProfileApiController controller = new ProfileApiController(jdbc, encoder, mock(UiPreferenceService.class));
    final UsernamePasswordAuthenticationToken actor = new UsernamePasswordAuthenticationToken(" reader ", "fixture", List.of());

    @BeforeEach void setup() {
        when(jdbc.queryForList("SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 1", " reader ")).thenReturn(List.of(Map.of("password", "fixture-stored")));
        when(encoder.matches(anyString(), eq("fixture-stored"))).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("fixture-hash");
    }

    static Stream<String> valid() { return Stream.of("abcdefgh", " next password ", "Привет123", "🔑 пароль", "a".repeat(72), "я".repeat(36)); }
    static Stream<String> invalid() { return Stream.of("a\0b", "a\tb", "a\nb", "a\rb", "a\u007fb", "a\u0085b", "a".repeat(73), "я".repeat(37)); }

    @ParameterizedTest @MethodSource("valid")
    void newPasswordIsHashedExactlyAndCurrentIdentityRemainsExact(String password) {
        var response = controller.updatePassword(actor, Map.of("current_password", "current", "new_password", password, "confirm_password", password));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(encoder).matches("current", "fixture-stored"); verify(encoder).encode(password);
        verify(jdbc).update("UPDATE users SET password = ? WHERE lower(username) = lower(?)", "fixture-hash", " reader ");
    }

    @ParameterizedTest @MethodSource("invalid")
    void invalidNewPasswordCannotReachVerificationOrWrite(String password) {
        var response = controller.updatePassword(actor, Map.of("current_password", "current", "new_password", password, "confirm_password", password));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(encoder); verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void currentPasswordPreservesItsEdgeSpaces() {
        var response = controller.updatePassword(actor, Map.of("current_password", " current ", "new_password", "abcdefgh", "confirm_password", "abcdefgh"));
        assertThat(response.getStatusCode().value()).isEqualTo(200); verify(encoder).matches(" current ", "fixture-stored");
    }

    @Test void confirmationIncludesEdgeSpaces() {
        var response = controller.updatePassword(actor, Map.of("current_password", "current", "new_password", " abcdefgh ", "confirm_password", "abcdefgh"));
        assertThat(response.getStatusCode().value()).isEqualTo(400); verifyNoInteractions(encoder); verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void incorrectCurrentPasswordStillPreventsWrite() {
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        var response = controller.updatePassword(actor, Map.of("current_password", "wrong", "new_password", "abcdefgh", "confirm_password", "abcdefgh"));
        assertThat(response.getStatusCode().value()).isEqualTo(400); verify(encoder, never()).encode(any()); verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void anonymousStillCannotChangePassword() {
        var response = controller.updatePassword(null, Map.of("current_password", "current", "new_password", "abcdefgh", "confirm_password", "abcdefgh"));
        assertThat(response.getStatusCode().value()).isEqualTo(401); verifyNoInteractions(encoder, jdbc);
    }
}
