package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialRotationExternalMetadataImportServiceTest {

    @Test
    void loadsLinkedMetadataFromHttpJsonBackend() {
        CredentialRotationExternalMetadataImportService service =
            new CredentialRotationExternalMetadataImportService(
                new ObjectMapper(),
                backend -> """
                    {
                      "items": [
                        {
                          "secret_ref": "network/project/proxy/password",
                          "expires_at": "2026-12-31T00:00:00Z",
                          "rotated_at": "2026-08-01T00:00:00Z",
                          "rotation_interval_days": 180,
                          "owner_name": "ops.network",
                          "note": "Imported from vault metadata"
                        }
                      ]
                    }
                    """
            );

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("credential_rotation_external_backends", List.of(
            Map.of(
                "id", "vault-main",
                "type", "http_json",
                "enabled", true,
                "metadata_url", "https://vault.example.test/api/metadata",
                "auth_type", "bearer",
                "auth_token", "secret-token"
            )
        ));
        settings.put("credential_rotation_external_links", List.of(
            Map.of(
                "entry_key", "network.project.proxy.password",
                "backend_id", "vault-main",
                "secret_ref", "network/project/proxy/password"
            )
        ));

        Map<String, CredentialRotationExternalMetadataImportService.ImportedMetadata> imported =
            service.loadImportedMetadata(settings);

        assertThat(imported).containsKey("network.project.proxy.password");
        CredentialRotationExternalMetadataImportService.ImportedMetadata metadata =
            imported.get("network.project.proxy.password");
        assertThat(metadata.backendId()).isEqualTo("vault-main");
        assertThat(metadata.secretRef()).isEqualTo("network/project/proxy/password");
        assertThat(metadata.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-12-31T00:00:00Z"));
        assertThat(metadata.rotatedAt()).isEqualTo(OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        assertThat(metadata.rotationIntervalDays()).isEqualTo(180);
        assertThat(metadata.ownerName()).isEqualTo("ops.network");
        assertThat(metadata.note()).isEqualTo("Imported from vault metadata");
        assertThat(metadata.overrideManualMetadata()).isFalse();
    }

    @Test
    void returnsEmptyMapWhenBackendFetchFails() {
        CredentialRotationExternalMetadataImportService service =
            new CredentialRotationExternalMetadataImportService(
                new ObjectMapper(),
                backend -> {
                    throw new IOException("backend unavailable");
                }
            );

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("credential_rotation_external_backends", List.of(
            Map.of(
                "id", "vault-main",
                "type", "http_json",
                "enabled", true,
                "metadata_url", "https://vault.example.test/api/metadata"
            )
        ));
        settings.put("credential_rotation_external_links", List.of(
            Map.of(
                "entry_key", "network.project.proxy.password",
                "backend_id", "vault-main",
                "secret_ref", "network/project/proxy/password"
            )
        ));

        assertThat(service.loadImportedMetadata(settings)).isEmpty();
    }
}
