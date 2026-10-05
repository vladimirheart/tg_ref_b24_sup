package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectPassportEquipmentConfigurationEditorUiSourceContractTest {

    @Test
    void sharedConfigurationRuntimeUsesTypedMetadataProfilesAndOverrideStorage() throws Exception {
        String source = read("src/main/resources/static/js/equipment-configuration-editor-runtime.js");

        assertThat(source)
                .contains("/api/settings/it-equipment/attributes")
                .contains("/api/settings/it-equipment/profiles")
                .contains("?equipmentType=")
                .contains("configuration_profile_id")
                .contains("configuration")
                .contains("override")
                .contains("value_type")
                .contains("boolean")
                .contains("integer")
                .contains("decimal")
                .contains("enum")
                .doesNotContain("localStorage");
    }

    @Test
    void bothPassportEditorsPersistProfileAndInstanceOverrides() throws Exception {
        String shared = read("src/main/resources/static/js/passport-editor-equipment-runtime.js");
        String detail = read("src/main/resources/static/js/passport-detail-editor-runtime.js");

        assertThat(shared)
                .contains("window.EquipmentConfigurationEditorRuntime")
                .contains("data-equipment-instance-configuration")
                .contains("configuration_profile_id")
                .contains("configuration:")
                .contains("instance_id:");

        assertThat(detail)
                .contains("window.EquipmentConfigurationEditorRuntime")
                .contains("data-equipment-instance-configuration")
                .contains("configuration_profile_id")
                .contains("resolvedEquipmentDraft()")
                .contains("instance_id: createEquipmentInstanceId()");
    }

    @Test
    void readOnlyEquipmentDetailResolvesAssignedProfileNameWithoutDenormalizingPassportJson() throws Exception {
        String detail = read("src/main/resources/static/js/passport-detail-equipment-runtime.js");

        assertThat(detail)
                .contains("/api/settings/it-equipment/profiles?equipmentType=")
                .contains("configuration_profile_id")
                .contains("data-equipment-profile-name")
                .contains("Профиль комплектации")
                .doesNotContain("configuration_profile_name");
    }

    @Test
    void configurationRuntimeLoadsBeforeEachPassportEquipmentEditor() throws Exception {
        String newPage = read("src/main/resources/templates/passports/new.html");
        String detailPage = read("src/main/resources/templates/passports/detail.html");
        String configurationRuntime = "/js/equipment-configuration-editor-runtime.js";
        String sharedEditor = "/js/passport-editor-equipment-runtime.js";
        String detailEditor = "/js/passport-detail-editor-runtime.js";

        assertThat(newPage.indexOf(configurationRuntime)).isGreaterThanOrEqualTo(0);
        assertThat(newPage.indexOf(configurationRuntime)).isLessThan(newPage.indexOf(sharedEditor));
        assertThat(detailPage.indexOf(configurationRuntime)).isGreaterThanOrEqualTo(0);
        assertThat(detailPage.indexOf(configurationRuntime)).isLessThan(detailPage.indexOf(detailEditor));
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
