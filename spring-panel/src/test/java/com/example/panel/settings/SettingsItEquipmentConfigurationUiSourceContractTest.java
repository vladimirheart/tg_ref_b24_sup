package com.example.panel.settings;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettingsItEquipmentConfigurationUiSourceContractTest {

    @Test
    void settingsEquipmentExposesTypedAttributesAndReusableProfilesWorkspace() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/settings/fragments/it-equipment.html"), UTF_8);
        String index = Files.readString(Path.of("src/main/resources/templates/settings/index.html"), UTF_8);
        String runtime = Files.readString(Path.of("src/main/resources/static/js/settings-it-equipment-configuration-runtime.js"), UTF_8);

        assertThat(template)
                .contains("itEquipmentConfigurationWorkspace")
                .contains("itEquipmentConfigurationType")
                .contains("itEquipmentConfigurationAttributes")
                .contains("itEquipmentConfigurationProfiles")
                .contains("Профили комплектации");
        assertThat(index).contains("settings-it-equipment-configuration-runtime.js?v=20261005-01-280-r12");
        assertThat(runtime)
                .contains("/api/settings/it-equipment/attributes")
                .contains("/api/settings/it-equipment/profiles")
                .contains("data-config-key")
                .contains("catalog_id")
                .contains("profile_name")
                .contains("value_type")
                .contains("integer")
                .contains("decimal")
                .contains("boolean")
                .contains("enum");
    }
}
