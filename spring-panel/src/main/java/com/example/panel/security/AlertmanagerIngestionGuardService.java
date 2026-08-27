package com.example.panel.security;

import com.example.panel.config.AlertmanagerIngestionProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertmanagerIngestionGuardService {

    public static final String TOKEN_FILE = "alertmanager-ingestion.token";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_TOKEN_LENGTH = 32;

    private final AlertmanagerIngestionProperties properties;

    public AlertmanagerIngestionGuardService(AlertmanagerIngestionProperties properties) {
        this.properties = properties;
    }

    public void authorize(String authorizationHeader) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alertmanager ingestion is disabled.");
        }

        String expected = readExpectedToken();
        String presented = extractBearerToken(authorizationHeader);
        if (!StringUtils.hasText(presented)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Alertmanager Bearer token is required.");
        }

        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, presentedBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Alertmanager Bearer token is invalid.");
        }
    }

    private String readExpectedToken() {
        if (!StringUtils.hasText(properties.getTokenFile())) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Alertmanager ingestion token file path is not configured."
            );
        }
        Path tokenPath = Path.of(properties.getTokenFile().trim()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(tokenPath)) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Alertmanager ingestion token file is missing."
            );
        }

        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).trim();
            if (token.length() < MIN_TOKEN_LENGTH) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Alertmanager ingestion token file is invalid."
                );
            }
            return token;
        } catch (IOException ex) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Alertmanager ingestion token file cannot be read.",
                ex
            );
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.length() <= BEARER_PREFIX.length()
                || !value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }
}
