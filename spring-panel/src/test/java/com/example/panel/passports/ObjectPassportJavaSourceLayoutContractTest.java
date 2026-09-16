package com.example.panel.passports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectPassportJavaSourceLayoutContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void passportCoreServiceUsesFeatureOwnedPackage() throws IOException {
        Path oldService = Path.of("src/main/java/com/example/panel/service/ObjectPassportService.java");
        Path newService = Path.of("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String api = read("src/main/java/com/example/panel/controller/ObjectPassportApiController.java");
        Path oldPageController = Path.of("src/main/java/com/example/panel/passports/ObjectPassportPageController.java");
        Path newPageController = Path.of("src/main/java/com/example/panel/passports/api/ObjectPassportPageController.java");
        String page = read("src/main/java/com/example/panel/passports/api/ObjectPassportPageController.java");
        String settingsEquipment = read("src/main/java/com/example/panel/service/SettingsItEquipmentService.java");
        String netBoxSync = read("src/main/java/com/example/panel/passports/infrastructure/NetBoxObjectPassportSyncService.java");

        assertThat(Files.exists(oldService)).isFalse();
        assertThat(Files.exists(newService)).isTrue();
        assertThat(Files.exists(oldPageController)).isFalse();
        assertThat(Files.exists(newPageController)).isTrue();
        assertThat(service)
                .contains("package com.example.panel.passports;")
                .contains("public class ObjectPassportService")
                .contains("createPassport(")
                .contains("updatePassport(")
                .contains("uploadPhoto(");
        assertThat(api)
                .contains("import com.example.panel.passports.ObjectPassportService;")
                .doesNotContain("import com.example.panel.service.ObjectPassportService;");
        assertThat(page)
                .contains("package com.example.panel.passports.api;")
                .contains("import com.example.panel.passports.ObjectPassportService;")
                .contains("public class ObjectPassportPageController")
                .doesNotContain("package com.example.panel.passports;")
                .doesNotContain("import com.example.panel.service.ObjectPassportService;");
        assertThat(settingsEquipment).contains("import com.example.panel.passports.ObjectPassportService;");
        assertThat(netBoxSync).contains("import com.example.panel.passports.ObjectPassportService;");
    }
    @Test
    void passportAppealReadModelUsesDedicatedQueryOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String query = read("src/main/java/com/example/panel/passports/ObjectPassportAppealQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportAppealQuery appealQuery;")
                .contains("this.appealQuery = new ObjectPassportAppealQuery(jdbcTemplate);")
                .contains("appealQuery.loadAppealCountsByLocation()")
                .contains("appealQuery.resolveAppealCount(appealsCountByLocation, normalized)")
                .contains("appealQuery.loadCases(normalized)")
                .doesNotContain("private Map<String, Long> loadAppealCountsByLocation()")
                .doesNotContain("private List<Map<String, Object>> queryCases(");
        assertThat(query)
                .contains("final class ObjectPassportAppealQuery")
                .contains("Map<String, Long> loadAppealCountsByLocation()")
                .contains("long resolveAppealCount(")
                .contains("List<Map<String, Object>> loadCases(")
                .contains("SELECT DISTINCT ticket_id")
                .contains("SELECT ticket_id, business, city, problem, created_at");
    }

    @Test
    void passportEquipmentCatalogueUsesDedicatedQueryOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String query = read("src/main/java/com/example/panel/passports/ObjectPassportEquipmentCatalogQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportEquipmentCatalogQuery equipmentCatalogQuery;")
                .contains("this.equipmentCatalogQuery = new ObjectPassportEquipmentCatalogQuery();")
                .contains("return equipmentCatalogQuery.listCandidates(passportPayloads);")
                .doesNotContain("private boolean isArchivedEquipment(Map<?, ?> item)")
                .doesNotContain("private Long positiveLongValue(Object raw)");
        assertThat(query)
                .contains("final class ObjectPassportEquipmentCatalogQuery")
                .contains("List<Map<String, Object>> listCandidates(")
                .contains("value.put(\"usage_count\", 0)")
                .contains("value.put(\"object_count\", 0)")
                .contains("value.put(\"source\", \"passports\")")
                .doesNotContain("openConnection()")
                .doesNotContain("loadAllStoredPassports(");
    }

    @Test
    void passportPhotoRulesUseDedicatedModelOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String model = read("src/main/java/com/example/panel/passports/ObjectPassportPhotoModel.java");

        assertThat(service)
                .contains("private final ObjectPassportPhotoModel photoModel;")
                .contains("this.photoModel = new ObjectPassportPhotoModel(photoStorageService::buildPhotoUrl);")
                .contains("photoModel.normalizePhotos(")
                .contains("photoModel.mutablePhotoList(")
                .contains("photoModel.enforceSingleTitlePhoto(")
                .contains("photoModel.findTitlePhotoUrl(")
                .doesNotContain("private List<Map<String, Object>> normalizePhotos(Object value)")
                .doesNotContain("private String findTitlePhotoUrl(");
        assertThat(model)
                .contains("final class ObjectPassportPhotoModel")
                .contains("List<Map<String, Object>> normalizePhotos(Object value)")
                .contains("List<Map<String, Object>> enforceSingleTitlePhoto(")
                .contains("String normalizePhotoCategory(Object raw)")
                .contains("String findTitlePhotoUrl(")
                .contains("photoUrlBuilder.apply(storedName)")
                .doesNotContain("openConnection()")
                .doesNotContain("updatePassportRow(");
    }

    @Test
    void passportNetBoxSyncUsesFeatureInfrastructurePackage() throws IOException {
        Path oldSync = Path.of("src/main/java/com/example/panel/service", "NetBoxObjectPassportSyncService.java");
        Path newSync = Path.of("src/main/java/com/example/panel/passports/infrastructure/NetBoxObjectPassportSyncService.java");
        String sync = read("src/main/java/com/example/panel/passports/infrastructure/NetBoxObjectPassportSyncService.java");
        String settingsPageData = read("src/main/java/com/example/panel/service/SettingsPageDataService.java");
        String settingsController = read("src/main/java/com/example/panel/controller/SettingsNetBoxSyncController.java");
        Path oldScheduler = Path.of("src/main/java/com/example/panel/service", "NetBoxObjectPassportSyncScheduler.java");
        Path newScheduler = Path.of("src/main/java/com/example/panel/passports/infrastructure/NetBoxObjectPassportSyncScheduler.java");
        String scheduler = read("src/main/java/com/example/panel/passports/infrastructure/NetBoxObjectPassportSyncScheduler.java");
        String dispatcher = read("src/main/java/com/example/panel/service/BackendOpsCommandDispatcher.java");

        assertThat(Files.exists(oldSync)).isFalse();
        assertThat(Files.exists(newSync)).isTrue();
        assertThat(Files.exists(oldScheduler)).isFalse();
        assertThat(Files.exists(newScheduler)).isTrue();
        assertThat(sync)
                .contains("package com.example.panel.passports.infrastructure;")
                .contains("import com.example.panel.passports.ObjectPassportService;")
                .contains("public class NetBoxObjectPassportSyncService");
        assertThat(scheduler)
                .contains("package com.example.panel.passports.infrastructure;")
                .contains("import com.example.panel.service.RuntimeCoordinationService;")
                .contains("public class NetBoxObjectPassportSyncScheduler")
                .contains("private final NetBoxObjectPassportSyncService syncService;")
                .contains("RuntimeRole.WORKER")
                .contains("RuntimeReplicaPolicy.LEASED")
                .doesNotContain("import com.example.panel.passports.infrastructure.NetBoxObjectPassportSyncService;");
        for (String consumer : new String[] {settingsPageData, settingsController, dispatcher}) {
            assertThat(consumer)
                    .contains("import com.example.panel.passports.infrastructure.NetBoxObjectPassportSyncService;")
                    .doesNotContain("import com.example.panel.service.NetBoxObjectPassportSyncService;");
        }
    }

    @Test
    void passportPageModelAssemblyUsesDedicatedOwner() throws IOException {
        String controller = read("src/main/java/com/example/panel/passports/api/ObjectPassportPageController.java");
        String assembler = read("src/main/java/com/example/panel/passports/api/ObjectPassportPageModelAssembler.java");

        assertThat(controller)
                .contains("private final ObjectPassportPageModelAssembler pageModelAssembler;")
                .contains("pageModelAssembler.populatePassportEditor(model, true);")
                .contains("pageModelAssembler.populatePassportEditor(model, false);")
                .doesNotContain("private void populatePassportEditor(")
                .doesNotContain("ItEquipmentCatalogRepository")
                .doesNotContain("SettingsParameterService");
        assertThat(assembler)
                .contains("public class ObjectPassportPageModelAssembler")
                .contains("void populatePassportEditor(Model model, boolean isNew)")
                .contains("private Map<String, Object> loadPassportLocationsPayload()")
                .contains("model.addAttribute(\"parameterValuesPayload\"")
                .contains("catalogItem.put(\"equipment_model\", item.getEquipmentModel())")
                .doesNotContain("@GetMapping(")
                .doesNotContain("ObjectPassportService");
    }

    @Test
    void passportPayloadRulesUseDedicatedModelOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String model = read("src/main/java/com/example/panel/passports/ObjectPassportPayloadModel.java");

        assertThat(service)
                .contains("private final ObjectPassportPayloadModel payloadModel;")
                .contains("this.payloadModel = new ObjectPassportPayloadModel(photoModel);")
                .contains("payloadModel.normalizePayload(")
                .contains("payloadModel.validatePayload(")
                .doesNotContain("payloadModel.buildObjectName(")
                .doesNotContain("payloadModel.buildPassportNumber(")
                .contains("payloadModel.isDeletedStatus(")
                .doesNotContain("private Map<String, Object> normalizePayload(")
                .doesNotContain("private void validatePayload(")
                .doesNotContain("private String buildObjectName(")
                .doesNotContain("private String buildPassportNumber(")
                .doesNotContain("private boolean isDeletedStatus(");
        assertThat(model)
                .contains("final class ObjectPassportPayloadModel")
                .contains("Map<String, Object> normalizePayload(")
                .contains("void validatePayload(")
                .contains("String buildObjectName(")
                .contains("String buildPassportNumber(")
                .contains("boolean isDeletedStatus(")
                .contains("photoModel.normalizePhotos(")
                .doesNotContain("openConnection()")
                .doesNotContain("PreparedStatement")
                .doesNotContain("ObjectPassportPhotoStorageService");
    }

    @Test
    void passportPersistenceUsesDedicatedOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String persistence = read("src/main/java/com/example/panel/passports/ObjectPassportPersistence.java");

        assertThat(service)
                .contains("private final ObjectPassportPersistence persistence;")
                .contains("this.persistence = new ObjectPassportPersistence(objectMapper, payloadModel);")
                .contains("persistence.insertObject(")
                .contains("persistence.insertPassport(")
                .contains("persistence.updatePassportRow(")
                .contains("persistence.loadStoredPassport(")
                .contains("persistence.loadAllStoredPassports(")
                .contains("persistence.readPayload(")
                .doesNotContain("private long insertObject(")
                .doesNotContain("private long insertPassport(")
                .doesNotContain("private void updatePassportRow(")
                .doesNotContain("private StoredPassportRecord loadStoredPassport(")
                .doesNotContain("private Map<String, Object> readJson(")
                .doesNotContain("TIMESTAMP_FORMATTER");
        assertThat(persistence)
                .contains("final class ObjectPassportPersistence")
                .contains("long insertObject(Connection connection")
                .contains("long insertPassport(Connection connection")
                .contains("void updatePassportRow(Connection connection")
                .contains("StoredPassportRecord loadStoredPassport(Connection connection")
                .contains("List<StoredPassportRecord> loadAllStoredPassports(Connection connection")
                .contains("Map<String, Object> readPayload(String raw)")
                .contains("INSERT INTO object_passports")
                .contains("UPDATE object_passports SET object_id")
                .contains("payloadModel.buildObjectName(")
                .contains("payloadModel.buildPassportNumber(")
                .doesNotContain("DataSource")
                .doesNotContain("SqliteConnectionConfigSupport")
                .doesNotContain("setAutoCommit")
                .doesNotContain("connection.commit()")
                .doesNotContain("connection.rollback()");
    }

}
