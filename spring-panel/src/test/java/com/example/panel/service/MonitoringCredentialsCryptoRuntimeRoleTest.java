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
