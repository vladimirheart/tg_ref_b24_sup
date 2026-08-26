package com.example.panel.service;

import com.example.panel.runtime.RuntimeRoleProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MonitoringCredentialsCryptoRuntimeRoleTest {

    @Test
    void splitRoleRequiresSharedMasterKey() {
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("web");

        MonitoringCredentialsCryptoService service = new MonitoringCredentialsCryptoService(
            mock(SharedConfigService.class),
            "",
            "unused.key",
            runtime
        );

        assertThatThrownBy(service::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MONITORING_CREDENTIALS_MASTER_KEY");
    }

    @Test
    void splitRoleCanReuseLegacyKeyFileMaterialViaBase64Prefix() throws Exception {
        String encodedKey = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
        java.nio.file.Path legacyKeyFile = java.nio.file.Files.createTempFile("monitoring-credentials", ".key");
        try {
            java.nio.file.Files.writeString(legacyKeyFile, encodedKey);
            SharedConfigService legacySharedConfig = mock(SharedConfigService.class);
            org.mockito.Mockito.when(legacySharedConfig.resolvePath("monitoring-credentials.key"))
                .thenReturn(legacyKeyFile);

            MonitoringCredentialsCryptoService legacyService = new MonitoringCredentialsCryptoService(
                legacySharedConfig,
                "",
                "monitoring-credentials.key"
            );
            legacyService.init();
            String encrypted = legacyService.encryptIfNeeded("legacy-secret");

            RuntimeRoleProperties runtime = new RuntimeRoleProperties();
            runtime.setRole("worker");
            MonitoringCredentialsCryptoService splitService = new MonitoringCredentialsCryptoService(
                mock(SharedConfigService.class),
                "base64:" + encodedKey,
                "unused.key",
                runtime
            );
            splitService.init();

            assertThat(splitService.decryptIfNeeded(encrypted)).isEqualTo("legacy-secret");
        } finally {
            java.nio.file.Files.deleteIfExists(legacyKeyFile);
        }
    }

    @Test
    void splitRoleUsesConfiguredMasterKeyWithoutLocalKeyFile() {
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("worker");

        MonitoringCredentialsCryptoService service = new MonitoringCredentialsCryptoService(
            mock(SharedConfigService.class),
            "shared-production-master-key",
            "unused.key",
            runtime
        );

        service.init();
        String encrypted = service.encryptIfNeeded("secret-value");

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(service.decryptIfNeeded(encrypted)).isEqualTo("secret-value");
    }
}
