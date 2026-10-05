package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettingsItEquipmentConfigurationSourceContractTest {

    @Test
    void controllerExposesAttributeAndProfileCrudNextToEquipmentSettings() throws Exception {
        String source = read("src/main/java/com/example/panel/controller/SettingsItEquipmentConfigurationController.java");

        assertThat(source)
            .contains("@RequestMapping(\"/api/settings/it-equipment\")")
            .contains("@GetMapping(\"/attributes\")")
            .contains("@PostMapping(\"/attributes\")")
            .contains("@PutMapping(\"/attributes/{attributeId}\")")
            .contains("@DeleteMapping(\"/attributes/{attributeId}\")")
            .contains("@GetMapping(\"/profiles\")")
            .contains("@PostMapping(\"/profiles\")")
            .contains("@PutMapping(\"/profiles/{profileId}\")")
            .contains("@DeleteMapping(\"/profiles/{profileId}\")");
    }

    @Test
    void serviceKeepsTypedJsonbContractAndModelTypeGuard() throws Exception {
        String source = read("src/main/java/com/example/panel/service/SettingsItEquipmentConfigurationService.java");

        assertThat(source)
            .contains("CAST(? AS jsonb)")
            .contains("VALUE_TYPES = Set.of(\"text\", \"integer\", \"decimal\", \"boolean\", \"enum\")")
            .contains("validateProfileValues")
            .contains("validateTypedValue")
            .contains("jsonb_exists(values, ?)")
            .contains("SELECT COUNT(*) FROM it_equipment_catalog WHERE id = ? AND equipment_type = ?")
            .contains("Выбранная модель не принадлежит указанному типу оборудования");
    }

    @Test
    void foundationMigrationStillDefinesOnlyTypeScopedAttributesAndProfiles() throws Exception {
        String migration = read("src/main/resources/db/migration/postgresql/V47__equipment_configuration_foundation.sql");

        assertThat(migration)
            .contains("UNIQUE(equipment_type, attribute_key)")
            .contains("UNIQUE(equipment_type, profile_name)")
            .contains("catalog_id BIGINT REFERENCES it_equipment_catalog(id) ON DELETE SET NULL")
            .contains("values JSONB NOT NULL DEFAULT '{}'::jsonb");
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
