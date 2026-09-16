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
        String casesQuery = read("src/main/java/com/example/panel/passports/ObjectPassportCasesQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportAppealQuery appealQuery;")
                .contains("this.appealQuery = new ObjectPassportAppealQuery(jdbcTemplate);")
                .contains("appealQuery.loadAppealCountsByLocation()")
                .doesNotContain("appealQuery.resolveAppealCount(appealsCountByLocation, normalized)")
                .contains("private final ObjectPassportCasesQuery casesQuery;")
                .contains("this.casesQuery = new ObjectPassportCasesQuery(persistence, payloadModel, appealQuery);")
                .contains("return casesQuery.load(connection, passportId);")
                .doesNotContain("List<Map<String, Object>> items = appealQuery.loadCases(normalized);")
                .doesNotContain("private Map<String, Long> loadAppealCountsByLocation()")
                .doesNotContain("private List<Map<String, Object>> queryCases(");
        assertThat(query)
                .contains("final class ObjectPassportAppealQuery")
                .contains("Map<String, Long> loadAppealCountsByLocation()")
                .contains("long resolveAppealCount(")
                .contains("List<Map<String, Object>> loadCases(")
                .contains("SELECT DISTINCT ticket_id")
                .contains("SELECT ticket_id, business, city, problem, created_at");
        assertThat(casesQuery)
                .contains("final class ObjectPassportCasesQuery")
                .contains("private final ObjectPassportPersistence persistence;")
                .contains("private final ObjectPassportPayloadModel payloadModel;")
                .contains("private final ObjectPassportAppealQuery appealQuery;")
                .contains("Map<String, Object> load(Connection connection, long passportId) throws SQLException")
                .contains("persistence.loadStoredPassport(connection, passportId)")
                .contains("payloadModel.normalizePayload(Map.of(), existing.payload(), passportId)")
                .contains("appealQuery.loadCases(normalized)")
                .contains("\"total_count\", items.size()")
                .doesNotContain("DataSource")
                .doesNotContain("openConnection()")
                .doesNotContain("setAutoCommit")
                .doesNotContain("connection.commit()")
                .doesNotContain("connection.rollback()");
    }

    @Test
    void passportDetailsProjectionUsesDedicatedQueryOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String query = read("src/main/java/com/example/panel/passports/ObjectPassportDetailsQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportDetailsQuery detailsQuery;")
                .contains("this.detailsQuery = new ObjectPassportDetailsQuery(persistence, payloadModel);")
                .contains("return detailsQuery.load(connection, passportId);")
                .contains("private Connection openConnection() throws SQLException")
                .contains("private DataSource runtimeObjectsDataSource()")
                .doesNotContain("\"passport\", payloadModel.normalizePayload(Map.of(), existing.payload(), passportId));");
        assertThat(query)
                .contains("final class ObjectPassportDetailsQuery")
                .contains("private final ObjectPassportPersistence persistence;")
                .contains("private final ObjectPassportPayloadModel payloadModel;")
                .contains("ObjectPassportDetailsQuery(ObjectPassportPersistence persistence,")
                .contains("Map<String, Object> load(Connection connection, long passportId) throws SQLException")
                .contains("persistence.loadStoredPassport(connection, passportId)")
                .contains("payloadModel.normalizePayload(Map.of(), existing.payload(), passportId)")
                .contains("return Map.of(")
                .doesNotContain("DataSource")
                .doesNotContain("openConnection()")
                .doesNotContain("setAutoCommit")
                .doesNotContain("connection.commit()")
                .doesNotContain("connection.rollback()");
    }

    @Test
    void passportListProjectionUsesDedicatedQueryOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String query = read("src/main/java/com/example/panel/passports/ObjectPassportListQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportListQuery listQuery;")
                .contains("this.listQuery = new ObjectPassportListQuery(persistence, payloadModel, photoModel, appealQuery);")
                .contains("appealQuery.loadAppealCountsByLocation()")
                .contains("return listQuery.list(connection, appealsCountByLocation);")
                .contains("private Connection openConnection() throws SQLException")
                .contains("private DataSource runtimeObjectsDataSource()")
                .doesNotContain("SELECT p.id, p.object_id, p.passport_number, p.details")
                .doesNotContain("persistence.readPayload(rs.getString(\"details\"))")
                .doesNotContain("payloadModel.isDeletedStatus(status)")
                .doesNotContain("photoModel.findTitlePhotoUrl(photos)")
                .doesNotContain("appealQuery.resolveAppealCount(appealsCountByLocation, normalized)")
                .doesNotContain("private String firstNonBlank(Object... values)");
        assertThat(query)
                .contains("final class ObjectPassportListQuery")
                .contains("List<Map<String, Object>> list(Connection connection,")
                .contains("SELECT p.id, p.object_id, p.passport_number, p.details")
                .contains("ORDER BY p.id DESC")
                .contains("persistence.readPayload(rs.getString(\"details\"))")
                .contains("payloadModel.normalizePayload(Map.of(), payload, passportId)")
                .contains("payloadModel.isDeletedStatus(status)")
                .contains("photoModel.findTitlePhotoUrl(photos)")
                .contains("appealQuery.resolveAppealCount(appealsCountByLocation, normalized)")
                .contains("item.put(\"location_address\"")
                .contains("item.put(\"passport_number\"")
                .contains("item.put(\"object_name\"")
                .contains("item.put(\"appeals_count\"")
                .contains("item.put(\"photos\", photos)")
                .doesNotContain("DataSource")
                .doesNotContain("openConnection()")
                .doesNotContain("loadAppealCountsByLocation()")
                .doesNotContain("setAutoCommit")
                .doesNotContain("connection.commit()")
                .doesNotContain("connection.rollback()");
    }

    @Test
    void passportEquipmentCatalogueUsesDedicatedQueryOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String query = read("src/main/java/com/example/panel/passports/ObjectPassportEquipmentCatalogQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportEquipmentCatalogQuery equipmentCatalogQuery;")
                .contains("this.equipmentCatalogQuery = new ObjectPassportEquipmentCatalogQuery(persistence);")
                .contains("return equipmentCatalogQuery.listCandidates(connection);")
                .contains("private Connection openConnection() throws SQLException")
                .contains("private DataSource runtimeObjectsDataSource()")
                .doesNotContain("new ObjectPassportEquipmentCatalogQuery();")
                .doesNotContain("List<Map<String, Object>> passportPayloads = new ArrayList<>();")
                .doesNotContain("return equipmentCatalogQuery.listCandidates(passportPayloads);");
        assertThat(query)
                .contains("final class ObjectPassportEquipmentCatalogQuery")
                .contains("private final ObjectPassportPersistence persistence;")
                .contains("ObjectPassportEquipmentCatalogQuery(ObjectPassportPersistence persistence)")
                .contains("List<Map<String, Object>> listCandidates(Connection connection) throws SQLException")
                .contains("persistence.loadAllStoredPassports(connection)")
                .contains("passportPayloads.add(record.payload())")
                .contains("return aggregateCandidates(passportPayloads)")
                .contains("value.put(\"usage_count\", 0)")
                .contains("value.put(\"object_count\", 0)")
                .contains("value.put(\"source\", \"passports\")")
                .doesNotContain("DataSource")
                .doesNotContain("openConnection()")
                .doesNotContain("setAutoCommit")
                .doesNotContain("connection.commit()")
                .doesNotContain("connection.rollback()");
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
                .contains("photoModel.updatePhoto(")
                .contains("photoModel.deletePhoto(")
                .doesNotContain("photoModel.findTitlePhotoUrl(")
                .doesNotContain("boolean updated = false;")
                .doesNotContain("List<Map<String, Object>> remaining = new ArrayList<>();")
                .doesNotContain("throw new ResponseStatusException(HttpStatus.NOT_FOUND, \"Фото паспорта не найдено\")")
                .doesNotContain("import java.util.ArrayList;")
                .doesNotContain("import org.springframework.http.HttpStatus;")
                .doesNotContain("import org.springframework.web.server.ResponseStatusException;")
                .doesNotContain("private List<Map<String, Object>> normalizePhotos(Object value)")
                .doesNotContain("private String findTitlePhotoUrl(");
        assertThat(model)
                .contains("final class ObjectPassportPhotoModel")
                .contains("List<Map<String, Object>> normalizePhotos(Object value)")
                .contains("List<Map<String, Object>> enforceSingleTitlePhoto(")
                .contains("String normalizePhotoCategory(Object raw)")
                .contains("String findTitlePhotoUrl(")
                .contains("List<Map<String, Object>> updatePhoto(Object value,")
                .contains("PhotoDeleteResult deletePhoto(Object value, String photoId)")
                .contains("record PhotoDeleteResult(List<Map<String, Object>> photos, String storedName)")
                .contains("new ResponseStatusException(HttpStatus.NOT_FOUND, \"Фото паспорта не найдено\")")
                .contains("photoUrlBuilder.apply(storedName)")
                .doesNotContain("openConnection()")
                .doesNotContain("updatePassportRow(")
                .doesNotContain("ObjectPassportPhotoStorageService");
    }

    @Test
    void passportPhotoLookupUsesDedicatedQueryOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String query = read("src/main/java/com/example/panel/passports/ObjectPassportPhotoQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportPhotoQuery photoQuery;")
                .contains("this.photoQuery = new ObjectPassportPhotoQuery(persistence, photoModel);")
                .contains("photoQuery.findStoredPassportByPhotoId(connection, photoId)")
                .contains("public Map<String, Object> uploadPhoto(")
                .contains("public Map<String, Object> updatePhoto(")
                .contains("public Map<String, Object> deletePhoto(")
                .contains("photoStorageService.store(file)")
                .contains("photoStorageService.deleteQuietly(")
                .contains("return photoStorageService.download(storedName);")
                .contains("connection.setAutoCommit(false);")
                .contains("connection.commit();")
                .contains("connection.rollback();")
                .contains("private Connection openConnection() throws SQLException")
                .contains("private DataSource runtimeObjectsDataSource()")
                .doesNotContain("SELECT id FROM object_passports")
                .doesNotContain("private ObjectPassportPersistence.StoredPassportRecord findStoredPassportByPhotoId(")
                .doesNotContain("import java.sql.ResultSet;");
        assertThat(query)
                .contains("final class ObjectPassportPhotoQuery")
                .contains("findStoredPassportByPhotoId(Connection connection,")
                .contains("SELECT id FROM object_passports")
                .contains("persistence.loadStoredPassport(connection, passportId)")
                .contains("photoModel.normalizePhotos(record.payload().get(\"photos\"))")
                .contains("new ObjectPassportPersistence.StoredPassportRecord(")
                .contains("HttpStatus.NOT_FOUND")
                .doesNotContain("DataSource")
                .doesNotContain("openConnection()")
                .doesNotContain("ObjectPassportPhotoStorageService")
                .doesNotContain("setAutoCommit")
                .doesNotContain("connection.commit()")
                .doesNotContain("connection.rollback()");
    }

    @Test
    void passportNetBoxLookupUsesDedicatedQueryOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String query = read("src/main/java/com/example/panel/passports/ObjectPassportNetBoxQuery.java");

        assertThat(service)
                .contains("private final ObjectPassportNetBoxQuery netBoxQuery;")
                .contains("this.netBoxQuery = new ObjectPassportNetBoxQuery(persistence, payloadModel);")
                .contains("String normalizedSiteId = netBoxQuery.normalizeSiteId(siteId);")
                .contains("if (!StringUtils.hasText(normalizedSiteId)) {")
                .contains("return netBoxQuery.findBySiteId(connection, normalizedSiteId);")
                .contains("Map<String, Object> existing = findPassportByNetBoxSiteId(siteId);")
                .contains("private Connection openConnection() throws SQLException")
                .contains("private DataSource runtimeObjectsDataSource()")
                .doesNotContain("record.payload().get(\"netbox_site_id\")");
        assertThat(query)
                .contains("final class ObjectPassportNetBoxQuery")
                .contains("String normalizeSiteId(Object siteId)")
                .contains("Map<String, Object> findBySiteId(Connection connection,")
                .contains("persistence.loadAllStoredPassports(connection)")
                .contains("record.payload().get(\"netbox_site_id\")")
                .contains("payloadModel.normalizePayload(Map.of(), record.payload(), record.passportId())")
                .doesNotContain("DataSource")
                .doesNotContain("openConnection()")
                .doesNotContain("setAutoCommit")
                .doesNotContain("connection.commit()")
                .doesNotContain("connection.rollback()")
                .doesNotContain("ObjectPassportPhotoStorageService");
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
    void passportManualOverridesUseDedicatedModelOwner() throws IOException {
        String service = read("src/main/java/com/example/panel/passports/ObjectPassportService.java");
        String model = read("src/main/java/com/example/panel/passports/ObjectPassportManualOverrideModel.java");

        assertThat(service)
                .contains("private final ObjectPassportManualOverrideModel manualOverrideModel;")
                .contains("this.manualOverrideModel = new ObjectPassportManualOverrideModel();")
                .contains("manualOverrideModel.markManualOverrides(existing.payload(), payload)")
                .contains("connection.setAutoCommit(false);")
                .contains("connection.commit();")
                .contains("connection.rollback();")
                .contains("private Connection openConnection() throws SQLException")
                .contains("private DataSource runtimeObjectsDataSource()")
                .doesNotContain("static Map<String, Object> markManualOverrides(")
                .doesNotContain("java.util.Objects.deepEquals(previous, entry.getValue())");
        assertThat(model)
                .contains("final class ObjectPassportManualOverrideModel")
                .contains("Map<String, Object> markManualOverrides(")
                .contains("existing.get(\"_manual_overrides\")")
                .contains("key.startsWith(\"_\")")
                .contains("\"id\".equals(key)")
                .contains("\"is_new\".equals(key)")
                .contains("Objects.deepEquals(previous, entry.getValue())")
                .contains("result.put(\"_manual_overrides\", List.copyOf(overrides))")
                .doesNotContain("Connection")
                .doesNotContain("DataSource")
                .doesNotContain("ObjectPassportPersistence")
                .doesNotContain("ObjectPassportPhotoStorageService");
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
                .doesNotContain("payloadModel.isDeletedStatus(")
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
                .doesNotContain("persistence.readPayload(")
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
