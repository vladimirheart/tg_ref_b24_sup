package com.example.panel.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettingsPageControllerSourceContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void settingsPageUsesFeatureFirstControllerOwnership() throws IOException {
        String management = read("src/main/java/com/example/panel/controller/ManagementController.java");
        String settings = read("src/main/java/com/example/panel/settings/SettingsPageController.java");

        assertThat(settings)
                .contains("@GetMapping(\"/settings\")")
                .contains("return \"settings/index\";")
                .contains("AppSettingRepository")
                .contains("SettingsParameterRepository")
                .contains("SharedConfigService")
                .contains("SettingsCatalogService")
                .contains("IikoDepartmentLocationCatalogService")
                .contains("AutoCloseConfigNormalizer")
                .contains("BotSettingsPayloadNormalizer")
                .contains("buildDialogLegacyQuestionTemplateAudit")
                .contains("canPublishDialogMacros");

        assertThat(management)
                .contains("@GetMapping(\"/tasks\")")
                .contains("@GetMapping(\"/channels\")")
                .contains("@GetMapping(\"/users\")")
                .doesNotContain("@GetMapping(\"/settings\")")
                .doesNotContain("AppSettingRepository")
                .doesNotContain("SettingsParameterRepository")
                .doesNotContain("SharedConfigService")
                .doesNotContain("SettingsCatalogService")
                .doesNotContain("AutoCloseConfigNormalizer")
                .doesNotContain("BotSettingsPayloadNormalizer");
    }
}
