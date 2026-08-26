package com.example.panel.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentWorkbenchCredentialRotationUiContractTest {

    @Test
    void credentialRotationContextIsRenderedAsOperatorFacingBlock() throws Exception {
        ClassPathResource resource = new ClassPathResource("static/js/incidents-workbench.js");
        String source;
        try (InputStream input = resource.getInputStream()) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(source)
            .contains("function credentialRotationSignalContext(incident)")
            .contains("function renderCredentialRotationSignalContext(incident)")
            .contains("incident?.signal_context")
            .contains("Почему появился инцидент")
            .contains("Почему критично")
            .contains("Что сделать")
            .contains("Политика критичности");
    }
}
