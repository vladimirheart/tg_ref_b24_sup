package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.support.PostgresqlIntegrationTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SettingsItEquipmentConfigurationServiceIntegrationTest extends PostgresqlIntegrationTestSupport {

    private static final String KIOSK = "R11 Self-service kiosk";
    private static final String POS = "R11 POS monoblock";

    @Autowired
    private SettingsItEquipmentConfigurationService configurationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanConfigurationRows() {
        jdbcTemplate.update("DELETE FROM it_equipment_configuration_profiles WHERE equipment_type IN (?, ?)", KIOSK, POS);
        jdbcTemplate.update("DELETE FROM it_equipment_attribute_definitions WHERE equipment_type IN (?, ?)", KIOSK, POS);
        jdbcTemplate.update("DELETE FROM it_equipment_catalog WHERE equipment_type IN (?, ?)", KIOSK, POS);
    }

    @Test
    void createsReadsAndUpdatesTypedAttributesAndCatalogLinkedProfile() {
        long cpuId = createAttribute(KIOSK, "cpu_cores", "CPU cores", "integer", List.of());
        long mountingId = createAttribute(KIOSK, "mounting", "Mounting", "enum", List.of("standing", "wall"));
        long catalogId = insertCatalog(KIOSK, "R11 Vendor", "Kiosk 100");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cpu_cores", 8);
        values.put("mounting", "standing");
        Map<String, Object> created = configurationService.createProfile(Map.of(
                "equipment_type", KIOSK,
                "catalog_id", catalogId,
                "profile_name", "Standard kiosk",
                "description", "R11 standard",
                "values", values,
                "active", true));

        assertThat(created).containsEntry("success", true);
        long profileId = itemId(created);
        assertThat(profileId).isPositive();

        Map<String, Object> listed = configurationService.listProfiles(KIOSK, catalogId);
        List<Map<String, Object>> items = items(listed);
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("catalog_id", catalogId);
        assertThat(values(items.get(0))).containsEntry("cpu_cores", 8).containsEntry("mounting", "standing");

        Map<String, Object> updatedValues = new LinkedHashMap<>();
        updatedValues.put("cpu_cores", 12);
        updatedValues.put("mounting", "wall");
        Map<String, Object> updated = configurationService.updateProfile(profileId, Map.of(
                "equipment_type", KIOSK,
                "catalog_id", catalogId,
                "profile_name", "Standard kiosk",
                "description", "R11 updated",
                "values", updatedValues,
                "active", true));

        assertThat(updated).containsEntry("success", true);
        assertThat(values(item(updated))).containsEntry("cpu_cores", 12).containsEntry("mounting", "wall");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM it_equipment_configuration_profiles WHERE id = ? AND values ->> 'mounting' = 'wall'",
                Integer.class, profileId)).isEqualTo(1);

        assertThat(configurationService.deleteProfile(profileId)).containsEntry("success", true);
        assertThat(configurationService.deleteAttribute(cpuId)).containsEntry("success", true);
        assertThat(configurationService.deleteAttribute(mountingId)).containsEntry("success", true);
    }

    @Test
    void rejectsInvalidTypedValuesUnknownKeysAndCatalogFromAnotherEquipmentType() {
        createAttribute(KIOSK, "mounting", "Mounting", "enum", List.of("standing", "wall"));
        createAttribute(KIOSK, "second_screen", "Second screen", "boolean", List.of());
        long foreignCatalogId = insertCatalog(POS, "R11 Vendor", "POS 200");

        assertFailure(configurationService.createProfile(profilePayload(
                KIOSK, "Bad enum", Map.of("mounting", "ceiling"))), "enum");
        assertFailure(configurationService.createProfile(profilePayload(
                KIOSK, "Bad boolean", Map.of("second_screen", "true"))), "true/false");
        assertFailure(configurationService.createProfile(profilePayload(
                KIOSK, "Unknown key", Map.of("ram_gb", 16))), "Неизвестная");

        Map<String, Object> wrongCatalog = new LinkedHashMap<>(profilePayload(
                KIOSK, "Wrong catalog", Map.of("mounting", "wall")));
        wrongCatalog.put("catalog_id", foreignCatalogId);
        assertFailure(configurationService.createProfile(wrongCatalog), "не принадлежит");

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM it_equipment_configuration_profiles WHERE equipment_type = ?",
                Integer.class, KIOSK);
        assertThat(profileCount).isZero();
    }

    @Test
    void protectsReferencedAttributeIdentityAndRemovesDeletedAttributeFromExistingProfileJsonb() {
        long cpuId = createAttribute(KIOSK, "cpu_cores", "CPU cores", "integer", List.of());
        createAttribute(KIOSK, "mounting", "Mounting", "enum", List.of("standing", "wall"));

        Map<String, Object> profile = configurationService.createProfile(profilePayload(
                KIOSK, "Mutable profile", Map.of("cpu_cores", 8, "mounting", "wall")));
        assertThat(profile).containsEntry("success", true);
        long profileId = itemId(profile);

        Map<String, Object> identityChange = configurationService.updateAttribute(cpuId, Map.of(
                "equipment_type", KIOSK,
                "attribute_key", "cpu_threads",
                "label", "CPU threads",
                "value_type", "integer",
                "required", false,
                "options", List.of(),
                "sort_order", 10,
                "active", true));
        assertFailure(identityChange, "используется профилями");

        assertThat(configurationService.deleteAttribute(cpuId)).containsEntry("success", true);
        Map<String, Object> listed = configurationService.listProfiles(KIOSK, null);
        Map<String, Object> persistedValues = values(items(listed).get(0));
        assertThat(persistedValues).doesNotContainKey("cpu_cores").containsEntry("mounting", "wall");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT jsonb_exists(values, 'cpu_cores') FROM it_equipment_configuration_profiles WHERE id = ?",
                Boolean.class, profileId)).isFalse();
    }

    private long createAttribute(String equipmentType, String key, String label, String valueType, List<String> options) {
        Map<String, Object> result = configurationService.createAttribute(Map.of(
                "equipment_type", equipmentType,
                "attribute_key", key,
                "label", label,
                "value_type", valueType,
                "section_name", "R11",
                "required", false,
                "options", options,
                "sort_order", 10,
                "active", true));
        assertThat(result).containsEntry("success", true);
        return itemId(result);
    }

    private long insertCatalog(String equipmentType, String vendor, String model) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO it_equipment_catalog(
                    equipment_type, equipment_vendor, equipment_model, photo_url,
                    serial_number, accessories, created_at, updated_at
                ) VALUES (?, ?, ?, '', '', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, equipmentType, vendor, model);
        assertThat(id).isNotNull().isPositive();
        return id;
    }

    private Map<String, Object> profilePayload(String equipmentType, String name, Map<String, Object> values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("equipment_type", equipmentType);
        payload.put("profile_name", name);
        payload.put("values", values);
        payload.put("active", true);
        return payload;
    }

    private void assertFailure(Map<String, Object> result, String messagePart) {
        assertThat(result).containsEntry("success", false);
        assertThat(String.valueOf(result.get("error"))).contains(messagePart);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> result) {
        assertThat(result).containsEntry("success", true);
        return (List<Map<String, Object>>) result.get("items");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> item(Map<String, Object> result) {
        return (Map<String, Object>) result.get("item");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> values(Map<String, Object> item) {
        return (Map<String, Object>) item.get("values");
    }

    private long itemId(Map<String, Object> result) {
        Object raw = item(result).get("id");
        assertThat(raw).isInstanceOf(Number.class);
        return ((Number) raw).longValue();
    }
}
