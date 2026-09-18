package com.example.panel.service;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupDestinationSettingsTest {

    @Test
    void legacyLocalPathDefaultsToLocalFilesystemAndNotDr() {
        BackupDestinationSettings settings = BackupDestinationSettings.fromStored(Map.of(
                BackupDestinationSettings.DESTINATION_KEY, "C:\\1C\\backup"
        ));

        assertThat(settings.type()).isEqualTo(BackupDestinationSettings.TYPE_LOCAL);
        assertThat(settings.destinationPath()).isEqualTo("C:\\1C\\backup");
        assertThat(settings.authMode()).isEqualTo(BackupDestinationSettings.AUTH_NONE);
        assertThat(settings.drClassification(true)).isEqualTo("not_dr");
    }

    @Test
    void legacyUncPathIsParsedIntoSmbFields() {
        BackupDestinationSettings settings = BackupDestinationSettings.fromStored(Map.of(
                BackupDestinationSettings.DESTINATION_KEY,
                "\\\\10.10.1.1\\fileserver\\!COMMON\\ИТ\\back"
        ));

        assertThat(settings.type()).isEqualTo(BackupDestinationSettings.TYPE_SMB);
        assertThat(settings.server()).isEqualTo("10.10.1.1");
        assertThat(settings.share()).isEqualTo("fileserver");
        assertThat(settings.subpath()).isEqualTo("!COMMON\\ИТ\\back");
        assertThat(settings.authMode()).isEqualTo(BackupDestinationSettings.AUTH_CURRENT_IDENTITY);
    }

    @Test
    void smbCredentialReferenceBuildsCanonicalUncWithoutSecret() {
        BackupDestinationSettings settings = BackupDestinationSettings.fromPayload(Map.of(
                "destination_type", "smb-unc",
                "destination_server", "10.10.1.1",
                "destination_share", "fileserver",
                "destination_subpath", "!COMMON/ИТ/back",
                "destination_auth_mode", "credential-ref",
                "destination_username", "DOMAIN\\backup-user",
                "destination_credential_ref", "backup-destination-main"
        ));

        assertThat(settings.destinationPath())
                .isEqualTo("\\\\10.10.1.1\\fileserver\\!COMMON\\ИТ\\back");
        assertThat(settings.credentialsConfigured()).isTrue();
        assertThat(settings.persistedFields().values())
                .doesNotContain("secret", "password");
    }

    @Test
    void explicitLocalTypeRejectsUncPath() {
        assertThatThrownBy(() -> BackupDestinationSettings.fromPayload(Map.of(
                "destination_type", "local-filesystem",
                "destination_path", "\\\\10.10.1.1\\fileserver\\backup"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smb-unc");
    }

    @Test
    void inlineSmbPasswordIsRejected() {
        assertThatThrownBy(() -> BackupDestinationSettings.fromPayload(Map.of(
                "destination_type", "smb-unc",
                "destination_server", "10.10.1.1",
                "destination_share", "fileserver",
                "destination_password", "must-not-be-persisted"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential_ref");
    }
}
