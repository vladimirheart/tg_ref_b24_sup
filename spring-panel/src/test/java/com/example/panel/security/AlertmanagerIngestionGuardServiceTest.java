package com.example.panel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.panel.config.AlertmanagerIngestionProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AlertmanagerIngestionGuardServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsBearerTokenFromConfiguredSecretFile() throws Exception {
        AlertmanagerIngestionProperties properties = new AlertmanagerIngestionProperties();
        properties.setEnabled(true);
        Path tokenPath = tempDir.resolve(AlertmanagerIngestionGuardService.TOKEN_FILE);
        properties.setTokenFile(tokenPath.toString());
        Files.writeString(
            tokenPath,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n"
        );
        AlertmanagerIngestionGuardService guard =
            new AlertmanagerIngestionGuardService(properties);

        assertThatCode(() -> guard.authorize(
            "Bearer 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongBearerToken() throws Exception {
        AlertmanagerIngestionProperties properties = new AlertmanagerIngestionProperties();
        properties.setEnabled(true);
        Path tokenPath = tempDir.resolve(AlertmanagerIngestionGuardService.TOKEN_FILE);
        properties.setTokenFile(tokenPath.toString());
        Files.writeString(
            tokenPath,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n"
        );
        AlertmanagerIngestionGuardService guard =
            new AlertmanagerIngestionGuardService(properties);

        assertThatThrownBy(() -> guard.authorize("Bearer wrong-token"))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
    }

    @Test
    void missingTokenFileFailsClosed() {
        AlertmanagerIngestionProperties properties = new AlertmanagerIngestionProperties();
        properties.setEnabled(true);
        properties.setTokenFile(tempDir.resolve(AlertmanagerIngestionGuardService.TOKEN_FILE).toString());
        AlertmanagerIngestionGuardService guard =
            new AlertmanagerIngestionGuardService(properties);

        assertThatThrownBy(() -> guard.authorize("Bearer any-value"))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            );
    }

    @Test
    void disabledIngestionIsNotExposed() {
        AlertmanagerIngestionProperties properties = new AlertmanagerIngestionProperties();
        properties.setEnabled(false);
        properties.setTokenFile(tempDir.resolve(AlertmanagerIngestionGuardService.TOKEN_FILE).toString());
        AlertmanagerIngestionGuardService guard =
            new AlertmanagerIngestionGuardService(properties);

        assertThatThrownBy(() -> guard.authorize("Bearer any-value"))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }
}
