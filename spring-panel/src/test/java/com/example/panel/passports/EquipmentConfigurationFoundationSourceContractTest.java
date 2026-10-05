package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EquipmentConfigurationFoundationSourceContractTest {

    @Test
    void migrationDefinesTypedAttributesAndReusableProfiles() throws Exception {
        String migration = read("src/main/resources/db/migration/postgresql/V47__equipment_configuration_foundation.sql");

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS it_equipment_attribute_definitions")
            .contains("UNIQUE(equipment_type, attribute_key)")
            .contains("value_type IN ('text', 'integer', 'decimal', 'boolean', 'enum')")
            .contains("options JSONB NOT NULL DEFAULT '[]'::jsonb")
            .contains("CREATE TABLE IF NOT EXISTS it_equipment_configuration_profiles")
            .contains("catalog_id BIGINT REFERENCES it_equipment_catalog(id) ON DELETE SET NULL")
            .contains("values JSONB NOT NULL DEFAULT '{}'::jsonb")
            .contains("UNIQUE(equipment_type, profile_name)");
    }

    @Test
    void passportEditorsUsePersistentInstanceIdentityWithoutReplacingCatalogIdentity() throws Exception {
        String shared = read("src/main/resources/static/js/passport-editor-equipment-runtime.js");
        String detail = read("src/main/resources/static/js/passport-detail-editor-runtime.js");

        assertThat(shared)
            .contains("item.instance_id")
            .contains("createEquipmentInstanceId")
            .contains("return instanceId;");

        assertThat(detail)
            .contains("function ensureEquipmentInstanceId(item)")
            .contains("instance_id: createEquipmentInstanceId()")
            .contains("const withIdentity = ensureEquipmentInstanceId(item)")
            .contains("catalog_id: match.id");
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
