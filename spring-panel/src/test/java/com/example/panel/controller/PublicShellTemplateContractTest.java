package com.example.panel.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PublicShellTemplateContractTest {

    private static final Path TEMPLATE_ROOT = Path.of("src", "main", "resources", "templates");

    @Test
    void loginTemplateIncludesUiHeadBootstrapAndExplicitPublicPreset() throws Exception {
        assertTemplateHasPublicPreset("auth/login.html");
    }

    @Test
    void error403TemplateIncludesUiHeadBootstrapAndExplicitPublicPreset() throws Exception {
        assertTemplateHasPublicPreset("error/403.html");
    }

    @Test
    void error404TemplateIncludesUiHeadBootstrapAndExplicitPublicPreset() throws Exception {
        assertTemplateHasPublicPreset("error/404.html");
    }

    @Test
    void error500TemplateIncludesUiHeadBootstrapAndExplicitPublicPreset() throws Exception {
        assertTemplateHasPublicPreset("error/500.html");
    }

    private void assertTemplateHasPublicPreset(String relativePath) throws Exception {
        String template = Files.readString(TEMPLATE_ROOT.resolve(relativePath));

        assertThat(template).contains("fragments/ui-head");
        assertThat(template).contains("data-ui-page=\"public\"");
    }
}
